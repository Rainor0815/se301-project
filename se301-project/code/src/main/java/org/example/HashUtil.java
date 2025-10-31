package org.example;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.Provider;
import java.security.Security;

/**
 * Small helper to compute sha256 hex (lower-case).
 */
public final class HashUtil {
    private HashUtil() {}

    private static final boolean CORRETTO_AVAILABLE;
    static {
        boolean ok = false;
        String[] candidates = new String[] {
                "software.amazon.cryptools.AmazonCorrettoCryptoProvider",
                "com.amazon.corretto.crypto.provider.AmazonCorrettoCryptoProvider"
        };
        for (String cls : candidates) {
            try {
                Class<?> providerClass = Class.forName(cls);
                Object inst = providerClass.getDeclaredConstructor().newInstance();
                if (inst instanceof Provider) {
                    Security.insertProviderAt((Provider) inst, 1);
                    System.out.println("[INFO] Registered crypto provider: " + ((Provider) inst).getName());
                    ok = true;
                    break;
                }
            } catch (ClassNotFoundException ignored) {
                // try next candidate
            } catch (Throwable t) {
                System.err.println("[WARN] Failed to register provider " + cls + ": " + t);
            }
        }
        CORRETTO_AVAILABLE = ok;
    }

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

    /** Return true if a Corretto-like provider was registered at startup. */
    public static boolean isCorrettoAvailable() {
        return CORRETTO_AVAILABLE;
    }
}
