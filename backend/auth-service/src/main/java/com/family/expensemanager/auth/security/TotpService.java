package com.family.expensemanager.auth.security;

import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Locale;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * RFC 6238 TOTP (the "Google Authenticator" algorithm) — no external library needed,
 * it's just HMAC-SHA1 over a 30s time step, which the JDK already provides. Backs
 * README "9. Không có 2FA".
 */
@Component
public class TotpService {

    private static final String ISSUER = "FamilyExpenseManager";
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final int SECRET_BYTES = 20; // 160 bits, matches RFC 4226's recommended key length
    private static final int CODE_DIGITS = 6;
    private static final int TIME_STEP_SECONDS = 30;
    /** How many steps before/after "now" still count as valid, to tolerate clock drift between server and phone. */
    private static final int ALLOWED_STEP_DRIFT = 1;
    private static final char[] BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateSecret() {
        byte[] bytes = new byte[SECRET_BYTES];
        secureRandom.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public String buildOtpAuthUri(String secret, String accountEmail) {
        String label = ISSUER + ":" + accountEmail;
        return "otpauth://totp/" + urlEncode(label)
                + "?secret=" + secret
                + "&issuer=" + urlEncode(ISSUER)
                + "&algorithm=SHA1&digits=" + CODE_DIGITS + "&period=" + TIME_STEP_SECONDS;
    }

    public boolean verifyCode(String secret, String code) {
        return matchStep(secret, code).isPresent();
    }

    /** The time step the code was generated for (within the allowed drift), so callers can reject a replay of the same step. */
    public Optional<Long> matchStep(String secret, String code) {
        if (code == null || !code.matches("\\d{" + CODE_DIGITS + "}")) {
            return Optional.empty();
        }
        long currentStep = System.currentTimeMillis() / 1000 / TIME_STEP_SECONDS;
        byte[] key = base32Decode(secret);
        for (int drift = -ALLOWED_STEP_DRIFT; drift <= ALLOWED_STEP_DRIFT; drift++) {
            long step = currentStep + drift;
            if (code.equals(generateCode(key, step))) {
                return Optional.of(step);
            }
        }
        return Optional.empty();
    }

    private String generateCode(byte[] key, long step) {
        try {
            byte[] stepBytes = ByteBuffer.allocate(8).putLong(step).array();
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            byte[] hash = mac.doFinal(stepBytes);

            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            int otp = binary % (int) Math.pow(10, CODE_DIGITS);
            return String.format(Locale.ROOT, "%0" + CODE_DIGITS + "d", otp);
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được mã TOTP", e);
        }
    }

    private String base32Encode(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = 0;
        int bitsLeft = 0;
        for (byte b : data) {
            buffer = (buffer << 8) | (b & 0xFF);
            bitsLeft += 8;
            while (bitsLeft >= 5) {
                int index = (buffer >> (bitsLeft - 5)) & 0x1F;
                result.append(BASE32_ALPHABET[index]);
                bitsLeft -= 5;
            }
        }
        if (bitsLeft > 0) {
            int index = (buffer << (5 - bitsLeft)) & 0x1F;
            result.append(BASE32_ALPHABET[index]);
        }
        return result.toString();
    }

    private byte[] base32Decode(String encoded) {
        String clean = encoded.trim().toUpperCase(Locale.ROOT);
        int bitsLeft = 0;
        int buffer = 0;
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (char c : clean.toCharArray()) {
            int index = new String(BASE32_ALPHABET).indexOf(c);
            if (index < 0) {
                continue;
            }
            buffer = (buffer << 5) | index;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    private String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
    }
}
