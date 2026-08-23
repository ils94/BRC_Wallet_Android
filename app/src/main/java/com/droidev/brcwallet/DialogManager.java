package com.droidev.brcwallet;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class DialogManager {

    public interface WalletActionCallback {
        void onWalletCreated();

        void onWalletImported();

        void onWalletExported(byte[] privKey);

        void onServerChanged(String newUrl);

        void onHeightSet(long height);

        void onSendRequested(byte[] to, long amountWei, long feeWei, String password);
    }

    private final Context context;
    private final WalletStore store;
    private final WalletActionCallback callback;

    public DialogManager(Context context, WalletStore store, WalletActionCallback callback) {
        this.context = context;
        this.store = store;
        this.callback = callback;
    }

    public void showNewWalletDialog() {
        if (!store.hasWallet()) {
            showCreateWalletPasswordDialog();
            return;
        }

        @SuppressLint("InflateParams") View view = LayoutInflater.from(context).inflate(R.layout.dialog_new_wallet, null);
        Button btnCreate = view.findViewById(R.id.btnCreate);
        TextView btnCancel = view.findViewById(R.id.btnCancel);

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        dialog.setCancelable(false);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }

        Handler handler = new Handler(Looper.getMainLooper());
        Runnable timerRunnable = new Runnable() {
            int countdown = 10;

            @Override
            public void run() {
                if (countdown > 0) {
                    btnCreate.setText(context.getString(R.string.button_create_countdown, countdown));
                    countdown--;
                    handler.postDelayed(this, 1000);
                } else {
                    btnCreate.setText(R.string.button_create);
                    btnCreate.setEnabled(true);
                }
            }
        };
        handler.post(timerRunnable);

        btnCreate.setOnClickListener(v -> {
            handler.removeCallbacks(timerRunnable);
            dialog.dismiss();
            showCreateWalletPasswordDialog();
        });

        btnCancel.setOnClickListener(v -> {
            handler.removeCallbacks(timerRunnable);
            dialog.dismiss();
        });

        dialog.setOnDismissListener(d -> handler.removeCallbacks(timerRunnable));

        dialog.show();
    }

    private void showCreateWalletPasswordDialog() {
        @SuppressLint("InflateParams") View view = LayoutInflater.from(context).inflate(R.layout.dialog_create_wallet, null);
        EditText edtPassword = view.findViewById(R.id.edtPassword);
        EditText edtConfirm = view.findViewById(R.id.edtConfirm);
        Button btnCreate = view.findViewById(R.id.btnCreate);
        TextView btnCancel = view.findViewById(R.id.btnCancel);

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        dialog.setCancelable(false);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }

        btnCreate.setOnClickListener(v -> {
            String pwd = edtPassword.getText().toString();
            String confirmPwd = edtConfirm.getText().toString();

            if (pwd.isEmpty()) {
                toast(context.getString(R.string.toast_password_empty));
                return;
            }
            if (!pwd.equals(confirmPwd)) {
                toast(context.getString(R.string.toast_passwords_do_not_match));
                return;
            }

            TxBuilder.KeyPair kp = TxBuilder.generateKeyPair();

            store.clearHistory();

            store.savePrivateKey(kp.privateKey, pwd);
            store.saveSyncState(-1, 0, 0);
            callback.onWalletCreated();
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    public void showImportDialog() {

        if (!store.hasWallet()) {
            showImportInputDialog();
            return;
        }

        @SuppressLint("InflateParams") View view = LayoutInflater.from(context).inflate(R.layout.dialog_import_warning, null);
        Button btnImport = view.findViewById(R.id.btnImport);
        TextView btnCancel = view.findViewById(R.id.btnCancel);

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        dialog.setCancelable(false);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }

        Handler handler = new Handler(Looper.getMainLooper());
        Runnable timerRunnable = new Runnable() {
            int countdown = 10;

            @Override
            public void run() {
                if (countdown > 0) {
                    btnImport.setText(context.getString(R.string.button_import_countdown, countdown));
                    countdown--;
                    handler.postDelayed(this, 1000);
                } else {
                    btnImport.setText(R.string.button_import);
                    btnImport.setEnabled(true);
                }
            }
        };
        handler.post(timerRunnable);

        btnImport.setOnClickListener(v -> {
            handler.removeCallbacks(timerRunnable);
            dialog.dismiss();
            showImportInputDialog();
        });

        btnCancel.setOnClickListener(v -> {
            handler.removeCallbacks(timerRunnable);
            dialog.dismiss();
        });

        dialog.setOnDismissListener(d -> handler.removeCallbacks(timerRunnable));

        dialog.show();
    }

    private void showImportInputDialog() {
        @SuppressLint("InflateParams") View view = LayoutInflater.from(context).inflate(R.layout.dialog_import, null);
        EditText edtInput = view.findViewById(R.id.edtInput);
        EditText edtPassword = view.findViewById(R.id.edtPassword);
        EditText edtConfirm = view.findViewById(R.id.edtConfirm);
        Button btnImport = view.findViewById(R.id.btnImport);
        TextView btnCancel = view.findViewById(R.id.btnCancel);

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        dialog.setCancelable(false);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }

        btnImport.setOnClickListener(v -> {
            String inputText = edtInput.getText().toString().trim();
            String pwd = edtPassword.getText().toString();
            String confirmPwd = edtConfirm.getText().toString();

            if (pwd.isEmpty()) {
                toast(context.getString(R.string.toast_set_password_required));
                return;
            }
            if (!pwd.equals(confirmPwd)) {
                toast(context.getString(R.string.toast_passwords_do_not_match));
                return;
            }

            byte[] priv;
            try {
                if (Bip39Helper.isHexPrivateKey(inputText)) {
                    priv = TxBuilder.fromHex(inputText);
                } else {
                    priv = Bip39Helper.mnemonicToEntropy(inputText);
                }
                if (priv.length != 32) {
                    throw new IllegalArgumentException(context.getString(R.string.error_private_key_length));
                }
                store.clearHistory();
                store.savePrivateKey(priv, pwd);
                store.saveSyncState(-1, 0, 0);
                callback.onWalletImported();
                dialog.dismiss();
            } catch (Exception e) {
                toast(context.getString(R.string.toast_invalid_key, e.getMessage()));
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    public void showExportDialog() {
        if (!store.hasWallet()) {
            toast(context.getString(R.string.toast_no_wallet));
            return;
        }

        @SuppressLint("InflateParams") View view = LayoutInflater.from(context).inflate(R.layout.dialog_export_password, null);
        EditText edtPassword = view.findViewById(R.id.edtPassword);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);
        TextView btnCancel = view.findViewById(R.id.btnCancel);

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        dialog.setCancelable(false);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }

        btnConfirm.setOnClickListener(v -> {
            try {
                byte[] priv = store.loadPrivateKey(edtPassword.getText().toString());
                callback.onWalletExported(priv);
                dialog.dismiss();
            } catch (Exception e) {
                toast(context.getString(R.string.toast_wrong_password));
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    public void showSendDialog() {
        if (!store.hasWallet()) {
            toast(context.getString(R.string.toast_no_wallet));
            return;
        }

        @SuppressLint("InflateParams") View view = LayoutInflater.from(context).inflate(R.layout.dialog_send, null);
        EditText edtTo = view.findViewById(R.id.edtTo);
        EditText edtAmount = view.findViewById(R.id.edtAmount);
        EditText edtFee = view.findViewById(R.id.edtFee);
        EditText edtPassword = view.findViewById(R.id.edtPassword);
        Button btnScan = view.findViewById(R.id.btnScan);
        Button btnSend = view.findViewById(R.id.btnSend);
        TextView btnCancel = view.findViewById(R.id.btnCancel);

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        dialog.setCancelable(false);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }

        btnScan.setOnClickListener(v -> {
            if (scanCallback != null) {
                scanCallback.startScan(edtTo);
            }
        });

        btnSend.setOnClickListener(v -> {
            try {
                byte[] to = TxBuilder.fromHex(edtTo.getText().toString());
                if (to.length != 32) {
                    throw new IllegalArgumentException(context.getString(R.string.error_address_length));
                }
                long amountWei = TxBuilder.brcToWei(edtAmount.getText().toString());
                String password = edtPassword.getText().toString();
                if (password.isEmpty()) {
                    toast(context.getString(R.string.toast_enter_password_to_send));
                    return;
                }

                long feeWei = TxBuilder.MIN_FEE;
                String feeText = edtFee.getText().toString().trim();
                if (!feeText.isEmpty()) {
                    feeWei = TxBuilder.brcToWei(feeText);
                    if (feeWei < TxBuilder.MIN_FEE) {
                        throw new IllegalArgumentException(context.getString(R.string.toast_invalid_fee));
                    }
                }

                dialog.dismiss();
                callback.onSendRequested(to, amountWei, feeWei, password);
            } catch (Exception e) {
                toast(context.getString(R.string.toast_invalid_data, e.getMessage()));
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    public interface ScannerCallback {
        void startScan(EditText targetField);
    }

    private ScannerCallback scanCallback;

    public void setScannerCallback(ScannerCallback callback) {
        this.scanCallback = callback;
    }

    public void showChangeServerDialog() {
        @SuppressLint("InflateParams") View view = LayoutInflater.from(context).inflate(R.layout.dialog_change_server, null);
        EditText edtServer = view.findViewById(R.id.edtServer);
        Button btnSave = view.findViewById(R.id.btnSave);
        TextView btnCancel = view.findViewById(R.id.btnCancel);

        edtServer.setText(store.getApiBase());
        edtServer.setSelection(edtServer.length());

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        dialog.setCancelable(false);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }

        btnSave.setOnClickListener(v -> {
            String url = edtServer.getText().toString().trim();
            store.setApiBase(url);
            callback.onServerChanged(url);
            dialog.dismiss();
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    public void showSetHeightDialog() {
        @SuppressLint("InflateParams") View view = LayoutInflater.from(context).inflate(R.layout.dialog_set_height, null);
        EditText edtHeight = view.findViewById(R.id.edtHeight);
        Button btnSet = view.findViewById(R.id.btnSet);
        TextView btnCancel = view.findViewById(R.id.btnCancel);

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(view);
        dialog.setCancelable(false);

        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }

        btnSet.setOnClickListener(v -> {
            try {
                long height = Long.parseLong(edtHeight.getText().toString());
                if (height < 0) throw new NumberFormatException();
                store.setSyncHeight(height);
                callback.onHeightSet(height);
                dialog.dismiss();
            } catch (NumberFormatException e) {
                toast(context.getString(R.string.toast_invalid_height));
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private void toast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}