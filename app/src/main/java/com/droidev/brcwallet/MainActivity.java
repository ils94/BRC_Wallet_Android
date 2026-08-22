package com.droidev.brcwallet;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
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

public class MainActivity extends AppCompatActivity implements DialogManager.WalletActionCallback, DialogManager.ScannerCallback {

    private WalletStore store;
    private BRCApi api;
    private WalletOperations operations;
    private DialogManager dialogs;

    private TextView txtAddress, txtBalance, txtStatus;
    private ImageView imgQrCode;
    private Button btnCopy, btnShare;

    private EditText tempEdtTo;

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
        operations = new WalletOperations(this, store, api);
        dialogs = new DialogManager(this, store, this);
        dialogs.setScannerCallback(this);

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
            dialogs.showNewWalletDialog();
            return true;
        } else if (id == R.id.action_import) {
            dialogs.showImportDialog();
            return true;
        } else if (id == R.id.action_export) {
            dialogs.showExportDialog();
            return true;
        } else if (id == R.id.action_send) {
            dialogs.showSendDialog();
            return true;
        } else if (id == R.id.action_change_api) {
            dialogs.showChangeServerDialog();
            return true;
        } else if (id == R.id.action_set_height) {
            dialogs.showSetHeightDialog();
            return true;
        } else if (id == R.id.action_history) {
            startActivity(new Intent(this, HistoryActivity.class));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onWalletCreated() {
        toast(getString(R.string.toast_wallet_created));
        updateUi();
    }

    @Override
    public void onWalletImported() {
        toast(getString(R.string.toast_wallet_imported));
        updateUi();
    }

    @Override
    public void onWalletExported(byte[] privKey) {
        Intent intent = new Intent(this, ExportActivity.class);
        intent.putExtra(ExportActivity.EXTRA_PRIVATE_KEY, TxBuilder.toHex(privKey));
        startActivity(intent);
    }

    @Override
    public void onServerChanged(String newUrl) {
        api.setBaseUrl(newUrl);
        toast(getString(R.string.toast_server_updated));
    }

    @Override
    public void onHeightSet(long height) {
        toast(getString(R.string.toast_height_set, height));
        updateUi();
    }

    @Override
    public void onSendRequested(byte[] to, long amountWei, long feeWei, String password) {
        setStatus(getString(R.string.status_sending));
        operations.sendTransaction(to, amountWei, feeWei, password, (success, message) -> {
            if (success) {
                toast(message);
                updateUi();
                setStatus(getString(R.string.status_tx_sent_wait));
            } else {
                setStatus(message);
            }
        });
    }

    private final ActivityResultLauncher<ScanOptions> scanLauncher =
            registerForActivityResult(new ScanContract(), result -> {
                if (result.getContents() != null && tempEdtTo != null) {
                    tempEdtTo.setText(result.getContents());
                    tempEdtTo = null;
                }
            });

    @Override
    public void startScan(EditText targetField) {
        tempEdtTo = targetField;
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
        operations.refreshBalance(
                this::setStatus,
                (success, message) -> {
                    if (success) {
                        updateUi();
                        setStatus(message);
                    } else {
                        setStatus(message);
                    }
                }
        );
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

    private void setStatus(String message) {
        runOnUiThread(() -> txtStatus.setText(message));
    }

    private void toast(String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
    }
}