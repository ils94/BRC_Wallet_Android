package com.droidev.brcwallet;

import org.bitcoinj.crypto.MnemonicCode;
import org.bitcoinj.crypto.MnemonicException;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Utilitários para conversão entre chave privada e frase mnemônica (BIP39).
 */
public final class Bip39Helper {

    private static final Pattern HEX_PATTERN = Pattern.compile("^[0-9a-fA-F]{64}$");

    private Bip39Helper() {}

    /** Verifica se a string é uma chave privada hexadecimal de 32 bytes. */
    public static boolean isHexPrivateKey(String input) {
        return HEX_PATTERN.matcher(input).matches();
    }

    /**
     * Converte uma frase mnemônica BIP39 para a entropia (32 bytes = chave privada).
     * @throws Exception se a frase for inválida ou não gerar 32 bytes.
     */
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

    /** Converte uma chave privada (32 bytes) para frase mnemônica BIP39. */
    public static String entropyToMnemonic(byte[] entropy) {
        try {
            List<String> words = MnemonicCode.INSTANCE.toMnemonic(entropy);
            return String.join(" ", words);
        } catch (MnemonicException e) {
            // Não deve ocorrer para 32 bytes
            return "";
        }
    }
}