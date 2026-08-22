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
        if (!store.hasWallet()) {
            // Chamado a partir da main thread (botão)
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

        io.execute(() -> {
            try {
                List<TxRecord> history = new ArrayList<>();
                postProgress(progressCallback, context.getString(R.string.status_syncing));

                ChainSync.sync(api, pub, state,
                        (h, tip) -> postProgress(progressCallback,
                                context.getString(R.string.status_sync_progress, h, tip)),
                        history);

                store.saveHistory(history);
                store.saveSyncState(state.height, state.balanceWei, state.nonce);

                postCompletion(doneCallback, true,
                        context.getString(R.string.status_synced_to, state.height));
            } catch (Exception e) {
                postCompletion(doneCallback, false,
                        context.getString(R.string.status_sync_error, e.getMessage()));
            }
        });
    }

    public void sendTransaction(byte[] to, long amountWei, String password, CompletionCallback doneCallback) {
        final long fee = TxBuilder.MIN_FEE;
        final byte[] priv;
        try {
            priv = store.loadPrivateKey(password);
        } catch (Exception e) {
            postCompletion(doneCallback, false, context.getString(R.string.toast_wrong_password));
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

                if (state.balanceWei < amountWei + fee) {
                    postCompletion(doneCallback, false,
                            context.getString(R.string.status_insufficient_balance,
                                    TxBuilder.weiToBrc(state.balanceWei)));
                    return;
                }

                byte[] tx = TxBuilder.buildSignedTransfer(priv, to, amountWei, fee, state.nonce);
                BRCApi.SubmitResult res = api.submitTxs(
                        Collections.singletonList(TxBuilder.toHex(tx)));

                if (res.ok()) {
                    store.saveSyncState(state.height, state.balanceWei, state.nonce + 1);
                    postCompletion(doneCallback, true,
                            context.getString(R.string.status_tx_admitted));
                } else {
                    postCompletion(doneCallback, false,
                            context.getString(R.string.status_tx_rejected, res.errors));
                }
            } catch (Exception e) {
                postCompletion(doneCallback, false,
                        context.getString(R.string.status_send_error, e.getMessage()));
            }
        });
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