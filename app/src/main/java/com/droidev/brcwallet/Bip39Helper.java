package com.droidev.brcwallet;

import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public final class Bip39Helper {

    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9a-fA-F]{64}$");

    private Bip39Helper() {
    }

    public static boolean isHexPrivateKey(String input) {
        return HEX_PATTERN.matcher(input).matches();
    }

    public static byte[] mnemonicToEntropy(String mnemonic) throws Exception {
        try {
            List<String> words = Arrays.asList(mnemonic.trim().split("\\s+"));
            byte[] entropy = MnemonicCode.INSTANCE.toEntropy(words);
            if (entropy.length != 32) {
                throw new Exception("Mnemonic does not produce 32 bytes");
            }
            return entropy;
        } catch (MnemonicException e) {
            throw new Exception("Invalid mnemonic phrase", e);
        }
    }

    public static String entropyToMnemonic(byte[] entropy) {
        try {
            List<String> words = MnemonicCode.INSTANCE.toMnemonic(entropy);
            return String.join(" ", words);
        } catch (MnemonicException e) {
            return "";
        }
    }
}