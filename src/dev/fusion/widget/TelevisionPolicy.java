package dev.fusion.widget;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

public final class TelevisionPolicy {
    public static final long RECENT_MILLIS = 600000;
    public static final int WAITING_STATE = 1 << 29;
    private static final String PREFIX = "native_tv:";
    public static boolean accepts(String category, String type) {
        return "nearby".equals(category) && ("TV".equals(type) || "ThirdTV".equals(type) || "third_TV".equals(type));
    }
    public static String publicId(String nativeId) {
        if (nativeId == null || nativeId.isEmpty()) return "";
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(nativeId.getBytes(StandardCharsets.UTF_8));
            StringBuilder id = new StringBuilder(PREFIX);
            for (byte value : hash) id.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
            return id.toString();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static boolean isPublicId(String id) { return id != null && id.startsWith(PREFIX) && id.length() == PREFIX.length() + 64; }
    public static boolean isRecent(long seen, long now) { return now >= seen && now - seen < RECENT_MILLIS; }
    private TelevisionPolicy() {}
}
