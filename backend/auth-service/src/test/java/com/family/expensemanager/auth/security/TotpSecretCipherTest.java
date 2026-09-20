package com.family.expensemanager.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;

import org.junit.jupiter.api.Test;

class TotpSecretCipherTest {

    private static final String KEY = "ZGV2LW9ubHktdG90cC1lbmNyeXB0aW9uLWtleS0zMmI=";

    private final TotpSecretCipher cipher = new TotpSecretCipher(KEY);

    @Test
    void encrypt_thenDecrypt_roundTrips_withPrefixedNonDeterministicOutput() {
        String first = cipher.encrypt("JBSWY3DPEHPK3PXP");
        String second = cipher.encrypt("JBSWY3DPEHPK3PXP");

        assertThat(first).startsWith("enc:v1:").doesNotContain("JBSWY3DPEHPK3PXP");
        assertThat(first).isNotEqualTo(second);
        assertThat(cipher.decrypt(first)).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(cipher.decrypt(second)).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void decrypt_returnsLegacyPlaintextUnchanged_whenNoPrefix() {
        assertThat(cipher.isEncrypted("JBSWY3DPEHPK3PXP")).isFalse();
        assertThat(cipher.decrypt("JBSWY3DPEHPK3PXP")).isEqualTo("JBSWY3DPEHPK3PXP");
    }

    @Test
    void nullStaysNull() {
        assertThat(cipher.encrypt(null)).isNull();
        assertThat(cipher.decrypt(null)).isNull();
        assertThat(cipher.isEncrypted(null)).isFalse();
    }

    @Test
    void decrypt_fails_whenCiphertextTamperedOrKeyDiffers() {
        String encrypted = cipher.encrypt("JBSWY3DPEHPK3PXP");
        byte[] payload = Base64.getDecoder().decode(encrypted.substring("enc:v1:".length()));
        payload[payload.length - 1] ^= 1;
        String tampered = "enc:v1:" + Base64.getEncoder().encodeToString(payload);
        TotpSecretCipher otherKey = new TotpSecretCipher(Base64.getEncoder().encodeToString(new byte[32]));

        assertThatThrownBy(() -> cipher.decrypt(tampered)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> otherKey.decrypt(encrypted)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decrypt_fallsBackToPreviousKey_afterRotation() {
        String newKey = Base64.getEncoder().encodeToString(new byte[32]);
        String encryptedWithOldKey = cipher.encrypt("JBSWY3DPEHPK3PXP");
        TotpSecretCipher rotated = new TotpSecretCipher(newKey, KEY);

        assertThat(rotated.decrypt(encryptedWithOldKey)).isEqualTo("JBSWY3DPEHPK3PXP");
        assertThat(new TotpSecretCipher(newKey).isEncrypted(encryptedWithOldKey)).isTrue();
        assertThatThrownBy(() -> new TotpSecretCipher(newKey).decrypt(encryptedWithOldKey))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void reencryptIfNeeded_movesOldKeyAndPlaintextToCurrentKey_andLeavesCurrentAlone() {
        String newKey = Base64.getEncoder().encodeToString(new byte[32]);
        TotpSecretCipher rotated = new TotpSecretCipher(newKey, KEY);
        String encryptedWithOldKey = cipher.encrypt("JBSWY3DPEHPK3PXP");

        String moved = rotated.reencryptIfNeeded(encryptedWithOldKey);
        assertThat(moved).isNotNull().isNotEqualTo(encryptedWithOldKey);
        assertThat(new TotpSecretCipher(newKey).decrypt(moved)).isEqualTo("JBSWY3DPEHPK3PXP");

        assertThat(rotated.reencryptIfNeeded(moved)).isNull();

        String fromPlaintext = rotated.reencryptIfNeeded("JBSWY3DPEHPK3PXP");
        assertThat(fromPlaintext).startsWith("enc:v1:");
        assertThat(rotated.decrypt(fromPlaintext)).isEqualTo("JBSWY3DPEHPK3PXP");

        assertThat(rotated.reencryptIfNeeded(null)).isNull();
    }

    @Test
    void constructor_rejectsKeyThatIsNot32Bytes() {
        String shortKey = Base64.getEncoder().encodeToString(new byte[16]);

        assertThatThrownBy(() -> new TotpSecretCipher(shortKey)).isInstanceOf(IllegalStateException.class);
    }
}
