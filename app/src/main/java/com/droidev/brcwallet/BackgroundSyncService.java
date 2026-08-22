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

public class BackgroundSyncService extends Service {

    private static final String CHANNEL_ID = "brc_wallet_sync";
    private static final int NOTIFICATION_ID = 1001;

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

        createNotificationChannel();

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

                    scheduleNextSync(60_000);
                }
        );
    }

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                "Automatic synchronization",
                NotificationManager.IMPORTANCE_LOW
        );
        channel.setDescription("Keeps balance and history up to date");
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);
    }

    private Notification buildNotification(String text) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("BRC Wallet")
                .setContentText(text)
                .setContentIntent(pendingIntent)
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