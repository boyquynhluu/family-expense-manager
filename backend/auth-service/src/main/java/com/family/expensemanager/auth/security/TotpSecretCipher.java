package com.family.expensemanager.auth.security;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AES-256-GCM encryption of USERS.totp_secret at rest, stored as {@code enc:v1:} + base64(iv || ciphertext).
 * A value without the prefix is a legacy plaintext secret and is returned as-is by {@link #decrypt}.
 * An optional previous key lets secrets written before a key rotation still be read until
 * {@link TotpKeyRotation} re-encrypts them with the current key.
 */
@Component
public class TotpSecretCipher {

    static final String PREFIX = "enc:v1:";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int KEY_BYTES = 32;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecretKeySpec previousKey;
    private final SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public TotpSecretCipher(
            @Value("${auth.totp-encryption-key}") String base64Key,
            @Value("${auth.totp-encryption-key-previous:}") String previousBase64Key) {
        this.key = toKey(base64Key, "auth.totp-encryption-key");
        this.previousKey = previousBase64Key == null || previousBase64Key.isBlank()
                ? null
                : toKey(previousBase64Key, "auth.totp-encryption-key-previous");
    }

    public TotpSecretCipher(String base64Key) {
        this(base64Key, "");
    }

    private static SecretKeySpec toKey(String base64Key, String property) {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key.trim());
        if (keyBytes.length != KEY_BYTES) {
            throw new IllegalStateException(property + " phải là base64 của đúng 32 byte");
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    public boolean isEncrypted(String stored) {
        return stored != null && stored.startsWith(PREFIX);
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] payload = ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
            return PREFIX + Base64.getEncoder().encodeToString(payload);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Không mã hoá được TOTP secret", e);
        }
    }

    public String decrypt(String stored) {
        if (stored == null || !isEncrypted(stored)) {
            return stored;
        }
        try {
            return decryptWith(key, stored);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            if (previousKey != null) {
                try {
                    return decryptWith(previousKey, stored);
                } catch (GeneralSecurityException | IllegalArgumentException ignored) {
                    // falls through to the error below
                }
            }
            throw new IllegalStateException("Không giải mã được TOTP secret (sai khoá auth.totp-encryption-key?)", e);
        }
    }

    /**
     * Returns the value re-encrypted with the current key, or null when the stored value already
     * uses the current key and nothing needs to change.
     */
    public String reencryptIfNeeded(String stored) {
        if (stored == null) {
            return null;
        }
        if (!isEncrypted(stored)) {
            return encrypt(stored);
        }
        try {
            decryptWith(key, stored);
            return null;
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return encrypt(decrypt(stored));
        }
    }

    private String decryptWith(SecretKeySpec decryptionKey, String stored) throws GeneralSecurityException {
        byte[] payload = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, decryptionKey, new GCMParameterSpec(TAG_BITS, payload, 0, IV_BYTES));
        byte[] plaintext = cipher.doFinal(payload, IV_BYTES, payload.length - IV_BYTES);
        return new String(plaintext, StandardCharsets.UTF_8);
    }
}
