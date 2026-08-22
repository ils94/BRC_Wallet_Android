package com.droidev.brcwallet;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import androidx.core.app.NotificationCompat;

import java.util.List;

public class BackgroundSyncService extends Service {

    private static final String CHANNEL_ID = "brc_wallet_sync";
    private static final String CHANNEL_ID_TX = "brc_wallet_tx";
    private static final int NOTIFICATION_ID = 1001;
    private static final int TX_NOTIFICATION_BASE_ID = 2000;

    private static final long SYNC_INTERVAL_MS = 60_000;
    private static final long RETRY_DELAY_MS = 5_000;

    private Handler handler;
    private WalletOperations operations;
    private WalletStore store;
    private boolean isSyncing = false;

    @Override
    public void onCreate() {
        super.onCreate();

        store = new WalletStore(this);
        BRCApi api = new BRCApi(store.getApiBase());
        operations = new WalletOperations(this, store, api);
        handler = new Handler(Looper.getMainLooper());

        createNotificationChannels();

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, buildNotification("Automatic synchronization active"),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(NOTIFICATION_ID, buildNotification("Automatic synchronization active"));
        }

        scheduleNextSync(0);
    }

    private void scheduleNextSync(long delayMillis) {
        handler.postDelayed(this::syncWallet, delayMillis);
    }

    private void syncWallet() {
        if (!store.hasWallet()) {
            stopSelf();
            return;
        }
        if (isSyncing) return;

        isSyncing = true;
        updateNotification("Synchronizing...");

        operations.refreshBalance(
                this::updateNotification,
                (success, message) -> {
                    isSyncing = false;
                    updateNotification(message);

                    if (success) {
                        scheduleNextSync(SYNC_INTERVAL_MS);
                    } else {
                        scheduleNextSync(RETRY_DELAY_MS);
                    }
                },
                this::handleNewTransactions
        );
    }

    private void handleNewTransactions(List<TxRecord> txs, boolean isFirstSync) {
        if (isFirstSync || txs == null || txs.isEmpty()) return;

        for (TxRecord tx : txs) {
            if (tx.type == TxRecord.Type.RECEIVE && tx.amountWei > 0) {
                showIncomingTxNotification(tx);
            }
        }
    }

    private void showIncomingTxNotification(TxRecord tx) {
        String amount = TxBuilder.weiToBrc(tx.amountWei);
        String details = "Block: " + tx.blockHeight + "\n"
                + "From: " + (tx.from.isEmpty() ? "unknown" : tx.from) + "\n"
                + "Amount: " + amount;

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID_TX)
                .setSmallIcon(R.mipmap.ic_launcher_round_monochrome)
                .setContentTitle("Incoming BRC")
                .setContentText("You received " + amount)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(details))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(getMainActivityPendingIntent())
                .build();

        NotificationManager manager = getSystemService(NotificationManager.class);
        int notificationId = TX_NOTIFICATION_BASE_ID + (int) tx.blockHeight;
        manager.notify(notificationId, notification);
    }

    private PendingIntent getMainActivityPendingIntent() {
        Intent intent = new Intent(this, MainActivity.class);
        return PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void createNotificationChannels() {
        NotificationChannel syncChannel = new NotificationChannel(
                CHANNEL_ID,
                "Automatic synchronization",
                NotificationManager.IMPORTANCE_LOW
        );
        syncChannel.setDescription("Keeps balance and history up to date");

        NotificationChannel txChannel = new NotificationChannel(
                CHANNEL_ID_TX,
                "Incoming transactions",
                NotificationManager.IMPORTANCE_HIGH
        );
        txChannel.setDescription("Notifications for received BRC");

        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(syncChannel);
        manager.createNotificationChannel(txChannel);
    }

    private Notification buildNotification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher_round_monochrome)
                .setContentTitle("BRC Wallet")
                .setContentText(text)
                .setContentIntent(getMainActivityPendingIntent())
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void updateNotification(String text) {
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.notify(NOTIFICATION_ID, buildNotification(text));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}