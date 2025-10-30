package org.example;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Small helper to compute sha256 hex (lower-case).
 */
public final class HashUtil {
    private HashUtil() {}

    private static final ThreadLocal<MessageDigest> SHA256_TL =
            ThreadLocal.withInitial(() -> {
                try { return MessageDigest.getInstance("SHA-256"); }
                catch (NoSuchAlgorithmException e) { throw new RuntimeException(e); }
            });

    // fast hex table
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    public static String sha256Hex(String s) {
        MessageDigest md = SHA256_TL.get();
        md.reset();
        byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
        md.update(bytes);
        byte[] out = md.digest();

        char[] dst = new char[out.length << 1];
        for (int i = 0, j = 0; i < out.length; i++) {
            int v = out[i] & 0xFF;
            dst[j++] = HEX[v >>> 4];
            dst[j++] = HEX[v & 0x0F];
        }
        return new String(dst);
    }
}
