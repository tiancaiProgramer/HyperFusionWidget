package dev.fusion.widget;

public final class CompatibilityTest {
    private static void check(boolean condition, String detail) { if (!condition) throw new AssertionError(detail); }
    public static void main(String[] args) {
        check(MiLinkVersionPolicy.allows(Protocol.BASELINE, false), "baseline works with compatibility disabled");
        check(MiLinkVersionPolicy.allows(Protocol.BASELINE, true), "baseline also works with compatibility enabled");
        check(!MiLinkVersionPolicy.allows("16.0.0", false), "older version is blocked with compatibility disabled");
        check(MiLinkVersionPolicy.allows("16.0.0", true), "older version can opt in");
        check(!MiLinkVersionPolicy.allows("17.2.5.0.2607311944", false), "new connected version is blocked with compatibility disabled");
        check(MiLinkVersionPolicy.allows("17.2.5.0.2607311944", true), "new connected version can opt in");
        check(!MiLinkVersionPolicy.allows(null, true), "missing version is not permission to bypass checks");
        check(!MiLinkVersionPolicy.allows("", true), "empty version is rejected");
        check(!MiLinkVersionPolicy.allows("  ", true), "blank version is rejected");
        check(MiLinkVersionPolicy.isBaseline(Protocol.BASELINE), "baseline label is accurate");
        check(!MiLinkVersionPolicy.isBaseline("17.2.5.0.2607311944"), "opt-in does not label another version as baseline");
        System.out.println("11 MiLink version-policy checks passed");
    }
}
