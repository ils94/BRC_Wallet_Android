package com.droidev.brcwallet;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ExportActivity extends AppCompatActivity {

    public static final String EXTRA_PRIVATE_KEY = "extra_private_key";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);

        setContentView(R.layout.activity_export);

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

        TextView txtPrivateKey = findViewById(R.id.txtPrivateKey);
        TextView txtMnemonic = findViewById(R.id.txtMnemonic);
        Button btnCopyPrivate = findViewById(R.id.btnCopyPrivate);
        Button btnCopyMnemonic = findViewById(R.id.btnCopyMnemonic);

        String privateKeyHex = getIntent().getStringExtra(EXTRA_PRIVATE_KEY);
        if (privateKeyHex == null || privateKeyHex.isEmpty()) {
            Toast.makeText(this, R.string.toast_invalid_data, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        byte[] privateKeyBytes;
        try {
            privateKeyBytes = TxBuilder.fromHex(privateKeyHex);
        } catch (Exception e) {
            Toast.makeText(this, R.string.toast_invalid_key, Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        txtPrivateKey.setText(privateKeyHex);

        String mnemonic = Bip39Helper.entropyToMnemonic(privateKeyBytes);
        txtMnemonic.setText(mnemonic);

        btnCopyPrivate.setOnClickListener(v -> copyToClipboard(
                getString(R.string.clipboard_label_private_key), privateKeyHex));

        btnCopyMnemonic.setOnClickListener(v -> copyToClipboard(
                getString(R.string.clipboard_label_mnemonic), mnemonic));
    }

    private void copyToClipboard(String label, String content) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(label, content);
        clipboard.setPrimaryClip(clip);
        Toast.makeText(this, R.string.toast_copied, Toast.LENGTH_SHORT).show();
    }
}