package com.droidev.brcwallet;

import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters;
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.SecureRandom;


public final class TxBuilder {

    public static final int CHAIN_ID = 0xc01dfeed;
    public static final long COIN = 100_000_000L;
    public static final long MAX_MONEY = 21_000_000L * COIN;
    public static final long MIN_FEE = 152L;
    public static final int TX_SIZE = 152;
    public static final int PREIMAGE_SIZE = 88;

    private TxBuilder() {
    }

    public static KeyPair generateKeyPair() {
        Ed25519PrivateKeyParameters priv = new Ed25519PrivateKeyParameters(new SecureRandom());
        Ed25519PublicKeyParameters pub = priv.generatePublicKey();
        return new KeyPair(priv.getEncoded(), pub.getEncoded());
    }

    public static byte[] publicKeyFromPrivate(byte[] privKey) {
        return new Ed25519PrivateKeyParameters(privKey, 0).generatePublicKey().getEncoded();
    }

    public static byte[] buildSignedTransfer(byte[] privKey, byte[] to,
                                             long amountWei, long feeWei, long nonce) {
        if (to == null || to.length != 32)
            throw new IllegalArgumentException("'to' deve ter 32 bytes");
        if (amountWei < 0 || feeWei < 0) throw new IllegalArgumentException("amount/fee negativos");
        if (amountWei == 0 && feeWei == 0) throw new IllegalArgumentException("tx sem valor");
        if (amountWei + feeWei > MAX_MONEY) throw new IllegalArgumentException("excede MAX_MONEY");
        if (feeWei < MIN_FEE)
            throw new IllegalArgumentException("fee abaixo do mínimo (" + MIN_FEE + " wei)");
        if (nonce < 0 || nonce > 0xFFFFFFFFL)
            throw new IllegalArgumentException("nonce fora de u32");

        byte[] from = publicKeyFromPrivate(privKey);

        ByteBuffer buf = ByteBuffer.allocate(PREIMAGE_SIZE).order(ByteOrder.BIG_ENDIAN);
        buf.putInt(CHAIN_ID);
        buf.put(from);
        buf.put(to);
        buf.putLong(amountWei);
        buf.putLong(feeWei);
        buf.putInt((int) nonce);
        byte[] preimage = buf.array();

        Ed25519Signer signer = new Ed25519Signer();
        signer.init(true, new Ed25519PrivateKeyParameters(privKey, 0));
        signer.update(preimage, 0, preimage.length);
        byte[] signature = signer.generateSignature();

        byte[] tx = new byte[TX_SIZE];
        System.arraycopy(preimage, 0, tx, 0, PREIMAGE_SIZE);
        System.arraycopy(signature, 0, tx, PREIMAGE_SIZE, 64);
        return tx;
    }

    public static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b)
            sb.append(Character.forDigit((x >> 4) & 0xF, 16))
                    .append(Character.forDigit(x & 0xF, 16));
        return sb.toString();
    }

    public static byte[] fromHex(String s) {
        s = s.trim();
        if ((s.length() & 1) != 0) throw new IllegalArgumentException("hex com tamanho ímpar");
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        }
        return out;
    }

    public static long brcToWei(String brc) {
        brc = brc.trim();
        if (brc.isEmpty()) throw new IllegalArgumentException("valor vazio");
        java.math.BigDecimal d = new java.math.BigDecimal(brc);
        return d.movePointRight(8).longValueExact();
    }

    public static String weiToBrc(long wei) {
        long whole = wei / COIN, frac = wei % COIN;
        String f = String.format(java.util.Locale.US, "%08d", frac);
        f = f.replaceAll("0+$", "");
        return whole + (f.isEmpty() ? "" : "." + f) + " BRC";
    }

    public static final class KeyPair {
        public final byte[] privateKey;
        public final byte[] publicKey;

        KeyPair(byte[] priv, byte[] pub) {
            this.privateKey = priv;
            this.publicKey = pub;
        }
    }
}
