package dev.fusion.widget;

import java.nio.file.*;

public final class PolicyTest {
    private static void check(boolean condition, String detail) { if (!condition) throw new AssertionError(detail); }
    public static void main(String[] args) throws Exception {
        byte[] cert = Files.readAllBytes(Paths.get(args[0]));
        check(CallerPolicy.allows(10234, 10234, true, cert), "installed signer and exact UID accepted");
        check(!CallerPolicy.allows(0, 10234, true, cert), "root UID does not impersonate app");
        check(!CallerPolicy.allows(1000, 10234, true, cert), "system UID does not impersonate app");
        check(!CallerPolicy.allows(1010234, 10234, true, cert), "other user rejected");
        check(!CallerPolicy.allows(10234, 10234, false, cert), "shared UID rejected");
        check(!CallerPolicy.allows(10234, 10234, true, new byte[]{1,2,3}), "re-signed app rejected");
        check(!CallerPolicy.allows(10234, 10234, true, null), "missing certificate rejected");
        check("屏幕协同中".equals(DeviceText.state("AndroidPad", 3)), "state is bitmask");
        check("离线或暂不可用".equals(DeviceText.state("AndroidPad", 2)), "online bit required");
        check(DeviceText.isScene("mijia_iot_control"), "scene recognized");
        check(!DeviceText.isScene("mijia_iot"), "ordinary Mi Home card is not a scene");
        System.out.println("11 authorization/state checks passed");
    }
}
