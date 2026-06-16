package it.davidecascella.githubresumer.service;

import it.davidecascella.githubresumer.config.EncryptionProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Cifratura/decifratura AES-256-GCM per i token GitHub a riposo nel DB.
 *
 * Formato in DB: Base64(IV[12] || ciphertext || tag[16])
 * L'IV è casuale per ogni cifratura, così due cifrature dello stesso valore
 * producono testi cifrati diversi (IND-CPA). Il tag GCM garantisce integrità.
 */
@Component
public class AesTokenEncryptor {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec keySpec;
    private final SecureRandom secureRandom = new SecureRandom();

    public AesTokenEncryptor(EncryptionProperties properties) {
        String encoded = properties.secretKey();
        if (encoded == null || encoded.isBlank()) {
            throw new IllegalStateException(
                    "app.encryption.secret-key non configurata. " +
                    "Genera una chiave con: openssl rand -base64 32");
        }
        byte[] keyBytes = Base64.getDecoder().decode(encoded);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                    "app.encryption.secret-key deve essere esattamente 32 byte (AES-256). " +
                    "Lunghezza attuale: " + keyBytes.length + " byte.");
        }
        this.keySpec = new SecretKeySpec(keyBytes, "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes());

            byte[] combined = new byte[IV_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, IV_BYTES);
            System.arraycopy(ciphertext, 0, combined, IV_BYTES, ciphertext.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new IllegalStateException("Cifratura token fallita", e);
        }
    }

    public String decrypt(String encrypted) {
        try {
            byte[] combined = Base64.getDecoder().decode(encrypted);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, IV_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, new GCMParameterSpec(TAG_BITS, iv));

            return new String(cipher.doFinal(ciphertext));
        } catch (Exception e) {
            throw new IllegalStateException("Decifratura token fallita", e);
        }
    }
}
