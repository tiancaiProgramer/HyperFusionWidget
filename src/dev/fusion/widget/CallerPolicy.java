package dev.fusion.widget;

import java.security.MessageDigest;

/** Pure policy shared with the host-side tests. No global signature exemption. */
public final class CallerPolicy {
    public static boolean allows(int caller, int appUid, boolean solePackage, byte[] certificate) {
        if (caller != appUid || !solePackage || certificate == null) return false;
        try {
            byte[] expected = hex(BuildIdentity.CERT_SHA256);
            return MessageDigest.isEqual(expected, MessageDigest.getInstance("SHA-256").digest(certificate));
        } catch (Exception e) { return false; }
    }
    private static byte[] hex(String s) {
        byte[] b = new byte[s.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(s.substring(i*2, i*2+2), 16);
        return b;
    }
    private CallerPolicy() {}
}
