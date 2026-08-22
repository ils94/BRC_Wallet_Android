package com.droidev.brcwallet;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class MainActivity extends AppCompatActivity {

    private WalletStore store;
    private BRCApi api;
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private TextView txtAddress, txtBalance, txtStatus;
    private ImageView imgQrCode;
    private Button btnCopy;
    private Button btnShare;

    private EditText tempEdtTo;

    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9a-fA-F]{64}$");

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        setContentView(R.layout.activity_main);

        getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE
        );

        View rootView = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(rootView, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        store = new WalletStore(this);
        api = new BRCApi(store.getApiBase());

        txtAddress = findViewById(R.id.txtAddress);
        txtBalance = findViewById(R.id.txtBalance);
        txtStatus = findViewById(R.id.txtStatus);
        imgQrCode = findViewById(R.id.imgQrCode);
        Button btnRefresh = findViewById(R.id.btnRefresh);
        btnCopy = findViewById(R.id.btnCopy);
        btnShare = findViewById(R.id.btnShare);

        btnRefresh.setOnClickListener(v -> refreshBalance());
        btnCopy.setOnClickListener(v -> copyAddress());
        btnShare.setOnClickListener(v -> shareAddress());

        updateUi();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_new_wallet) {
            confirmNewWallet();
            return true;
        } else if (id == R.id.action_import) {
            showImportDialog();
            return true;
        } else if (id == R.id.action_export) {
            showExportDialog();
            return true;
        } else if (id == R.id.action_send) {
            showSendDialog();
            return true;
        } else if (id == R.id.action_change_api) {
            showChangeApiDialog();
            return true;
        } else if (id == R.id.action_set_height) {
            showSetHeightDialog();
            return true;
        } else if (id == R.id.action_history) {
            startActivity(new Intent(this, HistoryActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void confirmNewWallet() {
        confirm(getString(R.string.dialog_new_wallet_title),
                getString(R.string.dialog_new_wallet_message),
                this::showCreateWalletPasswordDialog);
    }

    private void showCreateWalletPasswordDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 16);

        EditText edtPassword = new EditText(this);
        edtPassword.setHint(getString(R.string.hint_password));
        edtPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(edtPassword);

        EditText edtConfirm = new EditText(this);
        edtConfirm.setHint(getString(R.string.hint_confirm_password));
        edtConfirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(edtConfirm);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_set_password_title))
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.button_create), (d, w) -> {
                    String pwd = edtPassword.getText().toString();
                    String confirmPwd = edtConfirm.getText().toString();
                    if (pwd.isEmpty()) {
                        toast(getString(R.string.toast_password_empty));
                        return;
                    }
                    if (!pwd.equals(confirmPwd)) {
                        toast(getString(R.string.toast_passwords_do_not_match));
                        return;
                    }
                    createWallet(pwd);
                })
                .setNegativeButton(getString(R.string.button_cancel), null)
                .show();
    }

    private void createWallet(String password) {
        TxBuilder.KeyPair kp = TxBuilder.generateKeyPair();
        store.savePrivateKey(kp.privateKey, password);
        store.saveSyncState(-1, 0, 0);
        toast(getString(R.string.toast_wallet_created));
        updateUi();
    }

    private void showImportDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 16);

        EditText edtInput = new EditText(this);
        edtInput.setHint(getString(R.string.hint_import_input));
        edtInput.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(edtInput);

        EditText edtPassword = new EditText(this);
        edtPassword.setHint(getString(R.string.hint_set_wallet_password));
        edtPassword.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(edtPassword);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_import_title))
                .setView(layout)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.button_import), (d, w) -> {
                    String inputText = edtInput.getText().toString().trim();
                    String pwd = edtPassword.getText().toString();

                    if (pwd.isEmpty()) {
                        toast(getString(R.string.toast_set_password_required));
                        return;
                    }

                    byte[] priv = null;
                    try {
                        if (isHexPrivateKey(inputText)) {
                            priv = TxBuilder.fromHex(inputText);
                            if (priv.length != 32) {
                                throw new IllegalArgumentException(getString(R.string.error_private_key_length));
                            }
                        } else {
                            // Interpreta como seed phrase
                            priv = mnemonicToEntropy(inputText);
                            if (priv.length != 32) {
                                throw new IllegalArgumentException(getString(R.string.error_private_key_length));
                            }
                        }
                        store.savePrivateKey(priv, pwd);
                        store.saveSyncState(-1, 0, 0);
                        toast(getString(R.string.toast_wallet_imported));
                        updateUi();
                    } catch (Exception e) {
                        toast(getString(R.string.toast_invalid_key, e.getMessage()));
                    }
                })
                .setNegativeButton(getString(R.string.button_cancel), null)
                .show();
    }

    private void showExportDialog() {
        if (!store.hasWallet()) {
            toast(getString(R.string.toast_no_wallet));
            return;
        }

        EditText input = new EditText(this);
        input.setHint(getString(R.string.hint_wallet_password));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_enter_password_title))
                .setView(input)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.button_ok), (d, w) -> {
                    try {
                        byte[] priv = store.loadPrivateKey(input.getText().toString());
                        showExportFormatDialog(priv);
                    } catch (Exception e) {
                        toast(getString(R.string.toast_wrong_password));
                    }
                })
                .setNegativeButton(getString(R.string.button_cancel), null)
                .show();
    }

    private void showExportFormatDialog(byte[] priv) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dialog_export_format_title))
                .setCancelable(false)
                .setPositiveButton(getString(R.string.button_private_key), (d, w) -> {
                    showPrivateKeyDialog(priv);
                })
                .setNegativeButton(getString(R.string.button_mnemonic), (d, w) -> {
                    String mnemonic = entropyToMnemonic(priv);
                    showMnemonicDialog(mnemonic);
                })
                .setNeutralButton(getString(R.string.button_cancel), null)
                .show();
    }

    private void showMnemonicDialog(String mnemonic) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_mnemonic_title))
                .setMessage(mnemonic)
                .setPositiveButton(getString(R.string.button_ok), null)
                .setCancelable(false)
                .show();
    }

    private void showPrivateKeyDialog(byte[] priv) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_private_key_title))
                .setMessage(TxBuilder.toHex(priv))
                .setPositiveButton(getString(R.string.button_ok), null)
                .setCancelable(false)
                .show();
    }

    private void showChangeApiDialog() {
        EditText input = new EditText(this);
        input.setText(store.getApiBase());
        input.setHint(getString(R.string.hint_server_url));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_change_server_title))
                .setView(input)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.button_save), (d, w) -> {
                    String url = input.getText().toString().trim();
                    store.setApiBase(url);
                    api.setBaseUrl(url);
                    toast(getString(R.string.toast_server_updated));
                })
                .setNegativeButton(getString(R.string.button_cancel), null)
                .show();
    }

    private void showSendDialog() {
        if (!store.hasWallet()) {
            toast(getString(R.string.toast_no_wallet));
            return;
        }

        View view = LayoutInflater.from(this)
                .inflate(R.layout.dialog_send, null);

        EditText edtTo = view.findViewById(R.id.edtTo);
        EditText edtAmount = view.findViewById(R.id.edtAmount);
        EditText edtPassword = view.findViewById(R.id.edtPassword);
        Button btnScan = view.findViewById(R.id.btnScan);
        Button btnSend = view.findViewById(R.id.btnSend);
        TextView btnCancel = view.findViewById(R.id.btnCancel);

        Dialog dialog = new Dialog(this);
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
            tempEdtTo = edtTo;
            scanQrCode();
        });

        btnSend.setOnClickListener(v -> {
            try {
                byte[] to = TxBuilder.fromHex(edtTo.getText().toString());

                if (to.length != 32)
                    throw new IllegalArgumentException(
                            getString(R.string.error_address_length));

                long amountWei =
                        TxBuilder.brcToWei(edtAmount.getText().toString());

                String password = edtPassword.getText().toString();

                if (password.isEmpty()) {
                    toast(getString(R.string.toast_enter_password_to_send));
                    return;
                }

                performSend(to, amountWei, password);
                dialog.dismiss();

            } catch (Exception e) {
                toast(getString(R.string.toast_invalid_data, e.getMessage()));
            }
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        dialog.show();
    }

    private final ActivityResultLauncher<ScanOptions> scanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null && tempEdtTo != null) {
                    tempEdtTo.setText(result.getContents());
                    tempEdtTo = null;
                }
            });

    private void scanQrCode() {
        ScanOptions options = new ScanOptions();
        options.setPrompt(getString(R.string.scan_prompt));
        options.setBeepEnabled(true);
        options.setOrientationLocked(true);
        options.setBarcodeImageEnabled(false);
        scanLauncher.launch(options);
    }

    private void refreshBalance() {
        if (!store.hasWallet()) {
            toast(getString(R.string.toast_no_wallet));
            return;
        }
        byte[] pub = store.loadPublicKey();
        if (pub == null) {
            toast(getString(R.string.toast_no_wallet));
            return;
        }

        setStatus(getString(R.string.status_syncing));
        ChainSync.AccountState state = new ChainSync.AccountState();
        state.height = store.getSyncHeight();
        state.balanceWei = store.getBalanceWei();
        state.nonce = store.getNonce();

        io.execute(() -> {
            try {
                List<TxRecord> history = new ArrayList<>();
                ChainSync.sync(api, pub, state,
                        (h, tip) -> setStatus(getString(R.string.status_sync_progress, h, tip)),
                        history);
                store.saveHistory(history);
                store.saveSyncState(state.height, state.balanceWei, state.nonce);
                runOnUiThread(() -> {
                    updateUi();
                    setStatus(getString(R.string.status_synced_to, state.height));
                });
            } catch (Exception e) {
                setStatus(getString(R.string.status_sync_error, e.getMessage()));
            }
        });
    }

    private void performSend(byte[] to, long amountWei, String password) {
        final long fee = TxBuilder.MIN_FEE;
        final byte[] priv;
        try {
            priv = store.loadPrivateKey(password);
        } catch (Exception e) {
            toast(getString(R.string.toast_wrong_password));
            return;
        }

        setStatus(getString(R.string.status_sending));
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
                    setStatus(getString(R.string.status_insufficient_balance,
                            TxBuilder.weiToBrc(state.balanceWei)));
                    return;
                }

                byte[] tx = TxBuilder.buildSignedTransfer(priv, to, amountWei, fee, state.nonce);
                BRCApi.SubmitResult res = api.submitTxs(
                        Collections.singletonList(TxBuilder.toHex(tx)));

                runOnUiThread(() -> {
                    if (res.ok()) {
                        store.saveSyncState(state.height, state.balanceWei, state.nonce + 1);
                        toast(getString(R.string.status_tx_admitted));
                        updateUi();
                        setStatus(getString(R.string.status_tx_sent_wait));
                    } else {
                        setStatus(getString(R.string.status_tx_rejected, res.errors));
                    }
                });
            } catch (Exception e) {
                setStatus(getString(R.string.status_send_error, e.getMessage()));
            }
        });
    }

    private void updateUi() {
        if (store.hasWallet()) {
            byte[] pub = store.loadPublicKey();
            if (pub != null) {
                String address = TxBuilder.toHex(pub);
                txtAddress.setText(address);
                txtBalance.setText(getString(R.string.label_balance,
                        TxBuilder.weiToBrc(store.getBalanceWei())));
                generateQrCode(address);
                btnCopy.setEnabled(true);
                btnShare.setEnabled(true);
            } else {
                txtAddress.setText(getString(R.string.label_address_unavailable));
                imgQrCode.setImageBitmap(null);
                btnCopy.setEnabled(false);
                btnShare.setEnabled(false);
            }
        } else {
            txtAddress.setText(getString(R.string.label_no_wallet));
            txtBalance.setText(getString(R.string.label_balance_empty));
            imgQrCode.setImageBitmap(null);
            btnCopy.setEnabled(false);
            btnShare.setEnabled(false);
        }
    }

    private void generateQrCode(String content) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, 512, 512);
            Bitmap bitmap = Bitmap.createBitmap(512, 512, Bitmap.Config.RGB_565);
            for (int x = 0; x < 512; x++) {
                for (int y = 0; y < 512; y++) {
                    bitmap.setPixel(x, y, matrix.get(x, y) ? Color.BLACK : Color.WHITE);
                }
            }
            imgQrCode.setImageBitmap(bitmap);
        } catch (WriterException e) {
            e.printStackTrace();
        }
    }

    private void copyAddress() {
        if (!store.hasWallet()) return;
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(
                getString(R.string.clipboard_label_address), txtAddress.getText());
        clipboard.setPrimaryClip(clip);
        toast(getString(R.string.toast_address_copied));
    }

    private void shareAddress() {
        if (!store.hasWallet()) return;
        Intent sendIntent = new Intent();
        sendIntent.setAction(Intent.ACTION_SEND);
        sendIntent.putExtra(Intent.EXTRA_TEXT, txtAddress.getText());
        sendIntent.setType("text/plain");
        startActivity(Intent.createChooser(sendIntent,
                getString(R.string.chooser_share_address)));
    }

    private void setStatus(String s) {
        runOnUiThread(() -> txtStatus.setText(s));
    }

    private void toast(String s) {
        runOnUiThread(() -> Toast.makeText(this, s, Toast.LENGTH_LONG).show());
    }

    private void confirm(String title, String msg, Runnable onYes) {
        new AlertDialog.Builder(this)
                .setTitle(title).setMessage(msg)
                .setPositiveButton(getString(R.string.dialog_new_wallet_title), (d, w) -> onYes.run())
                .setNegativeButton(getString(R.string.button_cancel), null)
                .setCancelable(false)
                .show();
    }

    private void showSetHeightDialog() {
        EditText input = new EditText(this);
        input.setHint(getString(R.string.hint_block_height));
        input.setInputType(InputType.TYPE_CLASS_NUMBER);

        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.dialog_set_height_title))
                .setMessage(getString(R.string.dialog_set_height_message))
                .setView(input)
                .setCancelable(false)
                .setPositiveButton(getString(R.string.button_set), (d, w) -> {
                    try {
                        long height = Long.parseLong(input.getText().toString());
                        if (height < 0) throw new NumberFormatException();
                        store.setSyncHeight(height);
                        toast(getString(R.string.toast_height_set, height));
                        updateUi();
                    } catch (NumberFormatException e) {
                        toast(getString(R.string.toast_invalid_height));
                    }
                })
                .setNegativeButton(getString(R.string.button_cancel), null)
                .show();
    }

    private boolean isHexPrivateKey(String input) {
        return HEX_PATTERN.matcher(input).matches();
    }

    private byte[] mnemonicToEntropy(String mnemonic) throws Exception {
        try {
            List<String> words = Arrays.asList(mnemonic.trim().split("\\s+"));
            return MnemonicCode.INSTANCE.toEntropy(words);
        } catch (MnemonicException e) {
            throw new Exception(getString(R.string.toast_invalid_mnemonic));
        }
    }

    private String entropyToMnemonic(byte[] entropy) {
        try {
            List<String> words = MnemonicCode.INSTANCE.toMnemonic(entropy);
            return String.join(" ", words);
        } catch (MnemonicException e) {
            return "";
        }
    }
}