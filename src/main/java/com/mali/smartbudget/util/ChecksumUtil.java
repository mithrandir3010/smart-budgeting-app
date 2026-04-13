package com.mali.smartbudget.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * PDF ham byte'larından SHA-256 checksum üretir.
 *
 * <p>Aynı içerikli iki PDF dosyası her zaman aynı hash'i üretir —
 * bu özellik mükerrer yükleme tespitinin temelini oluşturur.
 *
 * <p>SHA-256 seçilme sebebi: MD5/SHA-1'e göre çarpışma direnci çok daha yüksek
 * ve standart Java SE içinde geliyor (ek bağımlılık gerektirmez).
 */
public final class ChecksumUtil {

    private ChecksumUtil() {}

    /**
     * Verilen byte dizisinin SHA-256 özetini 64 karakterlik hex string olarak döner.
     *
     * @param bytes SHA-256 hesaplanacak veri (genellikle PDF raw bytes)
     * @return 64 karakterlik lowercase hex string (örn: "a3f1c2d4...")
     * @throws IllegalStateException SHA-256 algoritması JVM tarafından desteklenmiyorsa
     */
    public static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder(64);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256, Java SE spesifikasyonu tarafından garantilendiği için bu branch hiç çalışmaz.
            throw new IllegalStateException("SHA-256 algoritması bulunamadı", e);
        }
    }
}
