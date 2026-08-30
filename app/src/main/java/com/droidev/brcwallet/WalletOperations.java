package com.droidev.brcwallet;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WalletOperations {

    public interface ProgressCallback {
        void onProgress(String message);
    }

    public interface CompletionCallback {
        void onComplete(boolean success, String message);
    }

    public interface HistoryCallback {
        void onNewHistory(List<TxRecord> newTxs, boolean isFirstSync);
    }

    public interface SendCallback {
        void onComplete(boolean success, String message, String txid);
    }

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final WalletStore store;
    private final BRCApi api;
    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public WalletOperations(Context context, WalletStore store, BRCApi api) {
        this.context = context;
        this.store = store;
        this.api = api;
    }

    public void refreshBalance(ProgressCallback progressCallback, CompletionCallback doneCallback) {
        refreshBalance(progressCallback, doneCallback, null);
    }

    public void refreshBalance(ProgressCallback progressCallback,
                               CompletionCallback doneCallback,
                               HistoryCallback historyCallback) {
        if (!store.hasWallet()) {
            postCompletion(doneCallback, false, context.getString(R.string.toast_no_wallet));
            return;
        }

        byte[] pub = store.loadPublicKey();
        if (pub == null) {
            postCompletion(doneCallback, false, context.getString(R.string.toast_no_wallet));
            return;
        }

        ChainSync.AccountState state = new ChainSync.AccountState();
        state.height = store.getSyncHeight();
        state.balanceWei = store.getBalanceWei();
        state.nonce = store.getNonce();

        final boolean firstSync = (state.height < 0);

        io.execute(() -> {
            List<TxRecord> history = new ArrayList<>();
            List<TxRecord> newHistory = new ArrayList<>();

            try {
                postProgress(progressCallback, context.getString(R.string.status_syncing));

                try (HistoryDatabase historyDatabase = new HistoryDatabase(context.getApplicationContext())) {
                    List<TxRecord> existingHistory = historyDatabase.loadAll();

                    java.util.HashSet<String> existingKeys =
                            new java.util.HashSet<>();

                    for (TxRecord tx : existingHistory) {
                        existingKeys.add(HistoryDatabase.dedupKey(tx));
                    }

                    ChainSync.sync(
                            api,
                            pub,
                            state,
                            (h, tip) -> postProgress(
                                    progressCallback,
                                    context.getString(
                                            R.string.status_sync_progress,
                                            h,
                                            tip
                                    )
                            ),
                            history
                    );

                    for (TxRecord tx : history) {
                        String key = HistoryDatabase.dedupKey(tx);

                        if (!existingKeys.contains(key)) {
                            newHistory.add(tx);
                            existingKeys.add(key);
                        }
                    }

                }

                store.saveHistory(history);
                store.saveSyncState(
                        state.height,
                        state.balanceWei,
                        state.nonce
                );

                if (historyCallback != null) {
                    mainHandler.post(() ->
                            historyCallback.onNewHistory(
                                    newHistory,
                                    firstSync
                            )
                    );
                }

                postCompletion(
                        doneCallback,
                        true,
                        context.getString(
                                R.string.status_synced_to,
                                state.height
                        )
                );

            } catch (Exception e) {
                store.saveHistory(history);
                store.saveSyncState(
                        state.height,
                        state.balanceWei,
                        state.nonce
                );

                postCompletion(
                        doneCallback,
                        false,
                        context.getString(
                                R.string.status_sync_error,
                                e.getMessage()
                        )
                );
            }
        });
    }

    public void sendTransaction(byte[] to, long amountWei, long feeWei, String password,
                                SendCallback doneCallback) {
        final byte[] priv;
        try {
            priv = store.loadPrivateKey(password);
        } catch (Exception e) {
            postSend(doneCallback, false, context.getString(R.string.toast_wrong_password), null);
            return;
        }

        io.execute(() -> {
            try {
                byte[] pub = store.loadPublicKey();
                ChainSync.AccountState state = new ChainSync.AccountState();
                state.height = store.getSyncHeight();
                state.balanceWei = store.getBalanceWei();
                state.nonce = store.getNonce();

                ChainSync.sync(api, pub, state, null, null);
                store.saveSyncState(state.height, state.balanceWei, state.nonce);

                if (state.balanceWei < amountWei + feeWei) {
                    postSend(doneCallback, false,
                            context.getString(R.string.status_insufficient_balance,
                                    TxBuilder.weiToBrc(state.balanceWei)), null);
                    return;
                }

                byte[] tx = TxBuilder.buildSignedTransfer(priv, to, amountWei, feeWei, state.nonce);
                String txid = TxBuilder.sha256Hex(tx);

                BRCApi.SubmitResult res = api.submitTxs(
                        Collections.singletonList(TxBuilder.toHex(tx)));

                if (res.ok()) {
                    store.saveSyncState(state.height, state.balanceWei, state.nonce + 1);
                    postSend(doneCallback, true,
                            context.getString(R.string.status_tx_admitted), txid);
                } else {
                    postSend(doneCallback, false,
                            context.getString(R.string.status_tx_rejected, res.errors), null);
                }
            } catch (Exception e) {
                postSend(doneCallback, false,
                        context.getString(R.string.status_send_error, e.getMessage()), null);
            }
        });
    }

    private void postSend(SendCallback callback, boolean success, String message, String txid) {
        if (callback != null) {
            mainHandler.post(() -> callback.onComplete(success, message, txid));
        }
    }

    private void postProgress(ProgressCallback callback, String message) {
        if (callback != null) {
            mainHandler.post(() -> callback.onProgress(message));
        }
    }

    private void postCompletion(CompletionCallback callback, boolean success, String message) {
        if (callback != null) {
            mainHandler.post(() -> callback.onComplete(success, message));
        }
    }
}