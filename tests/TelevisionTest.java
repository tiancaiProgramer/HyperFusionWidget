package dev.fusion.widget;

public final class TelevisionTest {
    private static void check(boolean condition, String detail) { if (!condition) throw new AssertionError(detail); }
    public static void main(String[] args) {
        check(TelevisionPolicy.accepts("nearby", "TV"), "native nearby TV included");
        check(TelevisionPolicy.accepts("nearby", "ThirdTV"), "third-party TV included");
        check(!TelevisionPolicy.accepts("nearby", "composeTV"), "virtual TV group is not a physical TV");
        check(!TelevisionPolicy.accepts("mijia", "mijia_iot_control"), "direct-execution scene excluded");
        check(!TelevisionPolicy.accepts("nearby", "AndroidPhone"), "already supported phone is not duplicated as a TV");
        String raw = "AA:BB:CC:11:22:33", id = TelevisionPolicy.publicId(raw);
        check(!id.contains(raw) && TelevisionPolicy.isPublicId(id), "public ID hides native MAC-like identifier");
        check(id.equals(TelevisionPolicy.publicId(raw)), "TV identity remains stable across snapshots");
        check(!id.equals(TelevisionPolicy.publicId("AA:BB:CC:11:22:34")), "different TVs remain separate");
        check(TelevisionPolicy.publicId(null).isEmpty(), "missing native identity is rejected");
        check(TelevisionPolicy.publicId("").isEmpty(), "empty native identity is rejected");
        check(TelevisionPolicy.isRecent(1000, 1100), "recently discovered TV survives scan teardown");
        check(!TelevisionPolicy.isRecent(1000, 1000 + TelevisionPolicy.RECENT_MILLIS), "old discovery expires at the retention limit");
        check(!TelevisionPolicy.isRecent(1100, 1000), "invalid future discovery time is not retained");
        check("待刷新".equals(DeviceText.state("TV", TelevisionPolicy.WAITING_STATE)), "cached TV is not labelled live online");
        check("离线或暂不可用".equals(DeviceText.state("AndroidPhone", TelevisionPolicy.WAITING_STATE)), "TV cache label does not affect phones");
        System.out.println("15 native-television checks passed");
    }
}
