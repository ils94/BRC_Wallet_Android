package com.droidev.brcwallet;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONArray;

import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

public final class WalletStore {

    private static final String PREFS = "wallet_prefs";
    private static final String KEY_PRIV_ENC = "priv_enc";
    private static final String KEY_PRIV_SALT = "priv_salt";
    private static final String KEY_PRIV_IV = "priv_iv";
    private static final String KEY_PUB = "pub_key";
    private static final String KEY_HEIGHT = "sync_height";
    private static final String KEY_BALANCE = "balance_wei";
    private static final String KEY_NONCE = "nonce";
    private static final String KEY_API = "api_base";
    private static final String KEY_HAS_PASSWORD = "has_password";
    private static final String KEY_HISTORY = "tx_history";

    private static final int PBKDF2_ITERATIONS = 100_000;
    private static final int SALT_SIZE_BYTES = 16;
    private static final int MAX_HISTORY = 500;

    private final SharedPreferences prefs;

    public WalletStore(Context ctx) {
        prefs = ctx.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public boolean hasWallet() {
        return prefs.contains(KEY_PRIV_ENC);
    }

    public void savePrivateKey(byte[] privKey, String password) {
        try {
            byte[] salt = generateRandomBytes();
            SecretKey key = deriveKey(password, salt);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] iv = cipher.getIV();
            byte[] enc = cipher.doFinal(privKey);

            byte[] pubKey = TxBuilder.publicKeyFromPrivate(privKey);

            prefs.edit()
                    .putString(KEY_PRIV_ENC, Base64.encodeToString(enc, Base64.NO_WRAP))
                    .putString(KEY_PRIV_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                    .putString(KEY_PRIV_IV, Base64.encodeToString(iv, Base64.NO_WRAP))
                    .putString(KEY_PUB, Base64.encodeToString(pubKey, Base64.NO_WRAP))
                    .putBoolean(KEY_HAS_PASSWORD, true)
                    .apply();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] loadPrivateKey(String password) {
        try {
            byte[] enc = Base64.decode(prefs.getString(KEY_PRIV_ENC, ""), Base64.NO_WRAP);
            byte[] salt = Base64.decode(prefs.getString(KEY_PRIV_SALT, ""), Base64.NO_WRAP);
            byte[] iv = Base64.decode(prefs.getString(KEY_PRIV_IV, ""), Base64.NO_WRAP);

            SecretKey key = deriveKey(password, salt);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return cipher.doFinal(enc);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public byte[] loadPublicKey() {
        String base64 = prefs.getString(KEY_PUB, null);
        if (base64 == null) return null;
        return Base64.decode(base64, Base64.NO_WRAP);
    }

    public long getSyncHeight() {
        return prefs.getLong(KEY_HEIGHT, -1);
    }

    public long getBalanceWei() {
        return prefs.getLong(KEY_BALANCE, 0);
    }

    public long getNonce() {
        return prefs.getLong(KEY_NONCE, 0);
    }

    public void saveSyncState(long height, long balanceWei, long nonce) {
        prefs.edit().putLong(KEY_HEIGHT, height)
                .putLong(KEY_BALANCE, balanceWei)
                .putLong(KEY_NONCE, nonce)
                .apply();
    }

    public void setSyncHeight(long height) {
        prefs.edit().putLong(KEY_HEIGHT, height)
                .putLong(KEY_BALANCE, 0L)
                .putLong(KEY_NONCE, 0L)
                .apply();
    }

    public void saveHistory(List<TxRecord> history) {
        if (history == null || history.isEmpty()) return;
        try {
            List<TxRecord> existing = loadHistory();
            Set<String> seen = new HashSet<>();
            JSONArray arr = new JSONArray();
            for (TxRecord r : existing) {
                String key = r.toJson().toString();
                if (seen.add(key)) arr.put(r.toJson());
            }
            for (TxRecord r : history) {
                String key = r.toJson().toString();
                if (seen.add(key)) arr.put(r.toJson());
            }
            while (arr.length() > MAX_HISTORY) {
                arr.remove(0);
            }
            prefs.edit().putString(KEY_HISTORY, arr.toString()).apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<TxRecord> loadHistory() {
        List<TxRecord> list = new ArrayList<>();
        String json = prefs.getString(KEY_HISTORY, null);
        if (json == null) return list;
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                list.add(TxRecord.fromJson(arr.getJSONObject(i)));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    public String getApiBase() {
        return prefs.getString(KEY_API, "https://api1.browsercoin.org,https://api2.browsercoin.org,https://api1.taitech.eu,https://brc-api.solodragonsden.fun");
    }

    public void setApiBase(String url) {
        prefs.edit().putString(KEY_API, url).apply();
    }

    private SecretKey deriveKey(String password, byte[] salt) throws Exception {
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256);
        SecretKey tmp = factory.generateSecret(spec);
        return new SecretKeySpec(tmp.getEncoded(), "AES");
    }

    private static byte[] generateRandomBytes() {
        byte[] b = new byte[WalletStore.SALT_SIZE_BYTES];
        new SecureRandom().nextBytes(b);
        return b;
    }

    public void clearHistory() {
        prefs.edit().remove(KEY_HISTORY).apply();
    }
}