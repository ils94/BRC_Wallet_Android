package com.droidev.brcwallet;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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
        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.dialog_new_wallet_title))
                .setMessage(context.getString(R.string.dialog_new_wallet_message))
                .setPositiveButton(context.getString(R.string.dialog_new_wallet_title), (d, w) -> showCreateWalletPasswordDialog())
                .setNegativeButton(context.getString(R.string.button_cancel), null)
                .setCancelable(false)
                .show();
    }

    private void showCreateWalletPasswordDialog() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 16);

        EditText edtPassword = new EditText(context);
        edtPassword.setHint(context.getString(R.string.hint_password));
        edtPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(edtPassword);

        EditText edtConfirm = new EditText(context);
        edtConfirm.setHint(context.getString(R.string.hint_confirm_password));
        edtConfirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(edtConfirm);

        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.dialog_set_password_title))
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton(context.getString(R.string.button_create), (d, w) -> {
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
                    store.savePrivateKey(kp.privateKey, pwd);
                    store.saveSyncState(-1, 0, 0);
                    callback.onWalletCreated();
                })
                .setNegativeButton(context.getString(R.string.button_cancel), null)
                .show();
    }

    public void showImportDialog() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 16);

        EditText edtInput = new EditText(context);
        edtInput.setHint(context.getString(R.string.hint_import_input));
        edtInput.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(edtInput);

        EditText edtPassword = new EditText(context);
        edtPassword.setHint(context.getString(R.string.hint_set_wallet_password));
        edtPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(edtPassword);

        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.dialog_import_title))
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton(context.getString(R.string.button_import), (d, w) -> {
                    String inputText = edtInput.getText().toString().trim();
                    String pwd = edtPassword.getText().toString();

                    if (pwd.isEmpty()) {
                        toast(context.getString(R.string.toast_set_password_required));
                        return;
                    }

                    byte[] priv;
                    try {
                        if (Bip39Helper.isHexPrivateKey(inputText)) {
                            priv = TxBuilder.fromHex(inputText);
                            if (priv.length != 32) {
                                throw new IllegalArgumentException(context.getString(R.string.error_private_key_length));
                            }
                        } else {
                            priv = Bip39Helper.mnemonicToEntropy(inputText);
                            if (priv.length != 32) {
                                throw new IllegalArgumentException(context.getString(R.string.error_private_key_length));
                            }
                        }
                        store.savePrivateKey(priv, pwd);
                        store.saveSyncState(-1, 0, 0);
                        callback.onWalletImported();
                    } catch (Exception e) {
                        toast(context.getString(R.string.toast_invalid_key, e.getMessage()));
                    }
                })
                .setNegativeButton(context.getString(R.string.button_cancel), null)
                .show();
    }

    public void showExportDialog() {
        if (!store.hasWallet()) {
            toast(context.getString(R.string.toast_no_wallet));
            return;
        }

        EditText input = new EditText(context);
        input.setHint(context.getString(R.string.hint_wallet_password));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.dialog_enter_password_title))
                .setView(input)
                .setCancelable(false)
                .setPositiveButton(context.getString(R.string.button_ok), (d, w) -> {
                    try {
                        byte[] priv = store.loadPrivateKey(input.getText().toString());
                        callback.onWalletExported(priv);
                    } catch (Exception e) {
                        toast(context.getString(R.string.toast_wrong_password));
                    }
                })
                .setNegativeButton(context.getString(R.string.button_cancel), null)
                .show();
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

                // Taxa opcional
                long feeWei = TxBuilder.MIN_FEE; // padrão
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
        EditText input = new EditText(context);
        input.setText(store.getApiBase());
        input.setHint(context.getString(R.string.hint_server_url));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);

        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.dialog_change_server_title))
                .setView(input)
                .setCancelable(false)
                .setPositiveButton(context.getString(R.string.button_save), (d, w) -> {
                    String url = input.getText().toString().trim();
                    store.setApiBase(url);
                    callback.onServerChanged(url);
                })
                .setNegativeButton(context.getString(R.string.button_cancel), null)
                .show();
    }

    public void showSetHeightDialog() {
        EditText input = new EditText(context);
        input.setHint(context.getString(R.string.hint_block_height));
        input.setInputType(InputType.TYPE_CLASS_NUMBER);

        new AlertDialog.Builder(context)
                .setTitle(context.getString(R.string.dialog_set_height_title))
                .setMessage(context.getString(R.string.dialog_set_height_message))
                .setView(input)
                .setCancelable(false)
                .setPositiveButton(context.getString(R.string.button_set), (d, w) -> {
                    try {
                        long height = Long.parseLong(input.getText().toString());
                        if (height < 0) throw new NumberFormatException();
                        store.setSyncHeight(height);
                        callback.onHeightSet(height);
                    } catch (NumberFormatException e) {
                        toast(context.getString(R.string.toast_invalid_height));
                    }
                })
                .setNegativeButton(context.getString(R.string.button_cancel), null)
                .show();
    }

    private void toast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show();
    }
}