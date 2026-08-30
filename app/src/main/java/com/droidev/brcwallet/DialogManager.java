package com.droidev.brcwallet;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.SwitchCompat;

public class DialogManager {

    public interface WalletActionCallback {
        void onWalletCreated();

        void onWalletImported();

        void onWalletExported(byte[] privKey);

        void onServerChanged(String newUrl);

        void onHeightSet(long height);

        void onHistoryRescanRequested(long height);

        void onSendRequested(byte[] to, long amountWei, long feeWei, String password);
    }

    public interface ContactActionCallback {
        void onContactAdded(String name, String address);

        void onContactEdited(int position, String name, String address);

        void onContactDeleted(int position);
    }

    public interface ScannerCallback {
        void startScan(EditText targetField);
    }

    public interface CameraEntropyCallback {
        void requestCameraEntropy(CameraEntropyResult result);
    }

    public interface CameraEntropyResult {
        void onEntropy(byte[] imageBytes);
    }

    private CameraEntropyCallback cameraEntropyCallback;

    private final Context context;
    private final WalletStore store;
    private final WalletActionCallback callback;
    private ContactActionCallback contactCallback;
    private ScannerCallback scanCallback;
    private final ContactsStore contactsStore;

    public DialogManager(Context context, WalletStore store, WalletActionCallback callback) {
        this.context = context;
        this.store = store;
        this.callback = callback;
        this.contactsStore = new ContactsStore(context);
    }

    public void setContactCallback(ContactActionCallback contactCallback) {
        this.contactCallback = contactCallback;
    }

    public void setScannerCallback(ScannerCallback callback) {
        this.scanCallback = callback;
    }

    public void setCameraEntropyCallback(CameraEntropyCallback cb) {
        this.cameraEntropyCallback = cb;
    }

    public void showNewWalletDialog() {
        if (!store.hasWallet()) {
            showCreateWalletPasswordDialog();
            return;
        }

        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_new_wallet, null);
        Button btnCreate = view.findViewById(R.id.btnCreate);
        TextView btnCancel = view.findViewById(R.id.btnCancel);

        Dialog dialog = createStyledDialog(view);

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
        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_create_wallet, null);
        EditText edtPassword = view.findViewById(R.id.edtPassword);
        EditText edtConfirm = view.findViewById(R.id.edtConfirm);
        CheckBox chkSensor = view.findViewById(R.id.chkSensorEntropy);
        CheckBox chkCamera = view.findViewById(R.id.chkCameraEntropy);
        Button btnCreate = view.findViewById(R.id.btnCreate);
        TextView btnCancel = view.findViewById(R.id.btnCancel);

        Dialog dialog = createStyledDialog(view);

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

            boolean useSensors = chkSensor.isChecked();
            boolean useCamera = chkCamera.isChecked();

            btnCreate.setEnabled(false);
            toast(context.getString(R.string.status_collecting_entropy));

            if (useCamera) {
                if (cameraEntropyCallback == null) {
                    toast(context.getString(R.string.toast_camera_unavailable));
                    btnCreate.setEnabled(true);
                    return;
                }
                dialog.dismiss();
                cameraEntropyCallback.requestCameraEntropy(imageBytes ->
                        finishWalletCreation(pwd, useSensors, imageBytes));
            } else {
                dialog.dismiss();
                finishWalletCreation(pwd, useSensors, null);
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private void finishWalletCreation(String pwd, boolean useSensors, byte[] photoBytes) {
        if (useSensors) {
            EntropyCollector.collectSensors(context, 400, sensorHash -> {
                byte[] extra = EntropyCollector.merge(sensorHash, photoBytes);
                createWalletWithEntropy(pwd, extra);
            });
        } else {
            byte[] extra = EntropyCollector.merge(photoBytes);
            createWalletWithEntropy(pwd, extra);
        }
    }

    private void createWalletWithEntropy(String pwd, byte[] extraEntropy) {
        TxBuilder.KeyPair kp = TxBuilder.generateKeyPair(extraEntropy);
        store.clearHistory();
        store.savePrivateKey(kp.privateKey, pwd);
        store.saveSyncState(-1, 0, 0);
        callback.onWalletCreated();
    }

    public void showImportDialog() {
        if (!store.hasWallet()) {
            showImportInputDialog();
            return;
        }

        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_import_warning, null);
        Button btnImport = view.findViewById(R.id.btnImport);
        TextView btnCancel = view.findViewById(R.id.btnCancel);

        Dialog dialog = createStyledDialog(view);

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
        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_import, null);
        EditText edtInput = view.findViewById(R.id.edtInput);
        EditText edtPassword = view.findViewById(R.id.edtPassword);
        EditText edtConfirm = view.findViewById(R.id.edtConfirm);
        Button btnImport = view.findViewById(R.id.btnImport);
        TextView btnCancel = view.findViewById(R.id.btnCancel);

        Dialog dialog = createStyledDialog(view);

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

            try {
                byte[] priv;
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

        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_export_password, null);
        EditText edtPassword = view.findViewById(R.id.edtPassword);
        Button btnConfirm = view.findViewById(R.id.btnConfirm);
        TextView btnCancel = view.findViewById(R.id.btnCancel);

        Dialog dialog = createStyledDialog(view);

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

    private void showPasswordConfirmDialog(byte[] to, long amountWei, long feeWei) {
        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_password_confirm, null);
        EditText edtPasswordConfirm = view.findViewById(R.id.edtPasswordConfirm);
        Button btnConfirmPassword = view.findViewById(R.id.btnConfirmPassword);
        TextView btnCancelPassword = view.findViewById(R.id.btnCancelPassword);

        Dialog dialog = createStyledDialog(view);

        btnConfirmPassword.setOnClickListener(v -> {
            String password = edtPasswordConfirm.getText().toString();
            if (password.isEmpty()) {
                toast(context.getString(R.string.toast_enter_password_to_send));
                return;
            }

            try {
                store.loadPrivateKey(password);
                dialog.dismiss();
                callback.onSendRequested(to, amountWei, feeWei, password);
            } catch (Exception e) {
                toast(context.getString(R.string.toast_wrong_password));
            }
        });

        btnCancelPassword.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showConfirmSendDialog(byte[] to, long amountWei, long feeWei, String contactName) {
        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_confirm_send, null);
        TextView txtContactName = view.findViewById(R.id.txtContactName);
        TextView txtRecipient = view.findViewById(R.id.txtRecipient);
        TextView txtAmount = view.findViewById(R.id.txtAmount);
        TextView txtFee = view.findViewById(R.id.txtFee);
        SwitchCompat switchConfirm = view.findViewById(R.id.switchConfirm);
        SeekBar seekBarConfirm = view.findViewById(R.id.seekBarConfirm);
        TextView txtSlideHint = view.findViewById(R.id.txtSlideHint);
        TextView btnCancel = view.findViewById(R.id.btnCancel);

        if (contactName != null && !contactName.isEmpty()) {
            txtContactName.setText(contactName);
            txtContactName.setVisibility(View.VISIBLE);
        } else {
            txtContactName.setVisibility(View.GONE);
        }

        txtRecipient.setText(TxBuilder.toHex(to));
        txtAmount.setText(TxBuilder.weiToBrc(amountWei));
        txtFee.setText(TxBuilder.weiToBrc(feeWei));

        Dialog dialog = createStyledDialog(view);

        seekBarConfirm.setEnabled(false);
        seekBarConfirm.setAlpha(0.5f);
        txtSlideHint.setText(R.string.slide_to_confirm_disabled);

        switchConfirm.setOnCheckedChangeListener((buttonView, isChecked) -> {
            seekBarConfirm.setEnabled(isChecked);
            seekBarConfirm.setAlpha(isChecked ? 1.0f : 0.5f);
            if (!isChecked) {
                seekBarConfirm.setProgress(0);
                txtSlideHint.setText(R.string.slide_to_confirm_disabled);
            } else {
                txtSlideHint.setText(R.string.slide_to_confirm);
            }
        });

        seekBarConfirm.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                if (!switchConfirm.isChecked()) {
                    seekBar.setProgress(0);
                }
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                if (switchConfirm.isChecked() && seekBar.getProgress() >= 100) {
                    dialog.dismiss();
                    showPasswordConfirmDialog(to, amountWei, feeWei);
                } else if (!switchConfirm.isChecked()) {
                    seekBar.setProgress(0);
                    txtSlideHint.setText(R.string.slide_to_confirm_disabled);
                } else {
                    seekBar.setProgress(0);
                    txtSlideHint.setText(R.string.slide_to_confirm);
                }
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    public void showSendDialog() {
        showSendDialogInternal(null);
    }

    public void showSendDialog(String prefillAddress) {
        showSendDialogInternal(prefillAddress);
    }

    private void showSendDialogInternal(String prefillAddress) {
        if (!store.hasWallet()) {
            toast(context.getString(R.string.toast_no_wallet));
            return;
        }

        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_send, null);
        TextView txtContactName = view.findViewById(R.id.txtContactName);
        EditText edtTo = view.findViewById(R.id.edtTo);
        EditText edtAmount = view.findViewById(R.id.edtAmount);
        EditText edtFee = view.findViewById(R.id.edtFee);
        Button btnScan = view.findViewById(R.id.btnScan);
        Button btnSend = view.findViewById(R.id.btnSend);
        TextView btnCancel = view.findViewById(R.id.btnCancel);

        if (prefillAddress != null && !prefillAddress.isEmpty()) {
            edtTo.setText(prefillAddress);
            String contactName = null;
            for (Contact c : contactsStore.loadContacts()) {
                if (c.address.equalsIgnoreCase(prefillAddress)) {
                    contactName = c.name;
                    break;
                }
            }
            if (contactName != null) {
                txtContactName.setText(contactName);
                txtContactName.setVisibility(View.VISIBLE);
            } else {
                txtContactName.setVisibility(View.GONE);
            }
        }

        Dialog dialog = createStyledDialog(view);

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

                long feeWei = TxBuilder.MIN_FEE;
                String feeText = edtFee.getText().toString().trim();
                if (!feeText.isEmpty()) {
                    feeWei = TxBuilder.brcToWei(feeText);
                    if (feeWei < TxBuilder.MIN_FEE) {
                        throw new IllegalArgumentException(context.getString(R.string.toast_invalid_fee));
                    }
                }

                String contactName = null;
                for (Contact c : contactsStore.loadContacts()) {
                    if (c.address.equalsIgnoreCase(TxBuilder.toHex(to))) {
                        contactName = c.name;
                        break;
                    }
                }

                dialog.dismiss();
                showConfirmSendDialog(to, amountWei, feeWei, contactName);
            } catch (Exception e) {
                toast(context.getString(R.string.toast_invalid_data, e.getMessage()));
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    public void showChangeServerDialog() {
        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_change_server, null);
        EditText edtServer = view.findViewById(R.id.edtServer);
        Button btnSave = view.findViewById(R.id.btnSave);
        TextView btnCancel = view.findViewById(R.id.btnCancel);

        edtServer.setText(store.getApiBase());
        edtServer.setSelection(edtServer.length());

        Dialog dialog = createStyledDialog(view);

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
        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(context)
                .inflate(R.layout.dialog_set_height, null);

        EditText edtHeight = view.findViewById(R.id.edtHeight);
        Button btnSet = view.findViewById(R.id.btnSet);
        TextView btnCancel = view.findViewById(R.id.btnCancel);

        androidx.appcompat.widget.AppCompatRadioButton radioFullSync =
                view.findViewById(R.id.radioFullSync);

        androidx.appcompat.widget.AppCompatRadioButton radioSearchPayments =
                view.findViewById(R.id.radioSearchPayments);

        radioFullSync.setChecked(true);

        Dialog dialog = createStyledDialog(view);

        btnSet.setOnClickListener(v -> {
            String text = edtHeight.getText().toString().trim();

            try {
                long height = Long.parseLong(text);

                if (height < 0) {
                    throw new NumberFormatException();
                }

                if (radioFullSync.isChecked()) {
                    store.setSyncHeight(height);
                    callback.onHeightSet(height);
                } else if (radioSearchPayments.isChecked()) {
                    callback.onHistoryRescanRequested(height);
                }

                dialog.dismiss();

            } catch (NumberFormatException e) {
                toast(context.getString(R.string.toast_invalid_height));
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    public void showAddContactDialog() {
        if (contactCallback == null) return;

        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_add_contact, null);
        EditText edtName = view.findViewById(R.id.edtName);
        EditText edtAddress = view.findViewById(R.id.edtAddress);
        Button btnScan = view.findViewById(R.id.btnScan);
        Button btnSave = view.findViewById(R.id.btnSave);
        TextView btnCancel = view.findViewById(R.id.btnCancel);

        Dialog dialog = createStyledDialog(view);

        btnScan.setOnClickListener(v -> {
            if (scanCallback != null) {
                scanCallback.startScan(edtAddress);
            }
        });

        btnSave.setOnClickListener(v -> {
            String name = edtName.getText().toString().trim();
            String address = edtAddress.getText().toString().trim();

            if (name.isEmpty() || address.isEmpty()) {
                toast(context.getString(R.string.toast_contact_empty_fields));
                return;
            }
            if (!Bip39Helper.isHexPrivateKey(address) || address.length() != 64) {
                toast(context.getString(R.string.toast_contact_invalid_address));
                return;
            }

            dialog.dismiss();
            contactCallback.onContactAdded(name, address);
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    public void showEditContactDialog(Contact contact, int position) {
        if (contactCallback == null) return;

        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_edit_contact, null);
        EditText edtName = view.findViewById(R.id.edtName);
        EditText edtAddress = view.findViewById(R.id.edtAddress);
        Button btnScan = view.findViewById(R.id.btnScan);
        Button btnSaveEdit = view.findViewById(R.id.btnSaveEdit);
        TextView btnCancel = view.findViewById(R.id.btnCancel);

        edtName.setText(contact.name);
        edtAddress.setText(contact.address);

        Dialog dialog = createStyledDialog(view);

        btnScan.setOnClickListener(v -> {
            if (scanCallback != null) {
                scanCallback.startScan(edtAddress);
            }
        });

        btnSaveEdit.setOnClickListener(v -> {
            String newName = edtName.getText().toString().trim();
            String newAddress = edtAddress.getText().toString().trim();

            if (newName.isEmpty() || newAddress.isEmpty()) {
                toast(context.getString(R.string.toast_contact_empty_fields));
                return;
            }
            if (!Bip39Helper.isHexPrivateKey(newAddress) || newAddress.length() != 64) {
                toast(context.getString(R.string.toast_contact_invalid_address));
                return;
            }

            dialog.dismiss();
            contactCallback.onContactEdited(position, newName, newAddress);
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    public void showDeleteContactDialog(Contact contact, int position) {
        if (contactCallback == null) return;

        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_delete_contact, null);
        TextView txtDeleteMessage = view.findViewById(R.id.txtDeleteMessage);
        Button btnConfirmDelete = view.findViewById(R.id.btnConfirmDelete);
        Button btnCancelDelete = view.findViewById(R.id.btnCancelDelete);

        txtDeleteMessage.setText(context.getString(R.string.dialog_delete_contact_message, contact.name));

        Dialog dialog = createStyledDialog(view);

        btnConfirmDelete.setOnClickListener(v -> {
            dialog.dismiss();
            contactCallback.onContactDeleted(position);
        });

        btnCancelDelete.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    public void showImportContactsDialog(Runnable onSelectFile) {
        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_import_contacts, null);

        Button btnSelectFile = view.findViewById(R.id.btnSelectFile);
        TextView btnCancel = view.findViewById(R.id.btnCancel);

        Dialog dialog = createStyledDialog(view);

        btnSelectFile.setOnClickListener(v -> {
            dialog.dismiss();
            if (onSelectFile != null) {
                onSelectFile.run();
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }

    private Dialog createStyledDialog(View view) {
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
        return dialog;
    }

    private void toast(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    public void showTxidDialog(String txid) {
        if (txid == null || txid.isEmpty()) return;

        @SuppressLint("InflateParams")
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_tx_sent, null);
        TextView txtTxid = view.findViewById(R.id.txtTxid);
        Button btnCopy = view.findViewById(R.id.btnCopyTxid);

        txtTxid.setText(txid);

        Dialog dialog = createStyledDialog(view);
        dialog.setCancelable(false);
        dialog.setCanceledOnTouchOutside(false);

        btnCopy.setOnClickListener(v -> {
            ClipboardManager clipboard =
                    (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("txid", txid));
            toast(context.getString(R.string.toast_txid_copied));
            dialog.dismiss();
        });

        dialog.show();
    }
}