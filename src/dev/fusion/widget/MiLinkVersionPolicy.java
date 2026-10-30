package dev.fusion.widget;

/** Each request carries its compatibility setting; caller verification always runs first. */
public final class MiLinkVersionPolicy {
    public static boolean isBaseline(String version) { return Protocol.BASELINE.equals(version); }
    public static boolean allows(String version, boolean compatibilityMode) {
        return isBaseline(version) || (compatibilityMode && version != null && !version.trim().isEmpty());
    }
    private MiLinkVersionPolicy() {}
}
