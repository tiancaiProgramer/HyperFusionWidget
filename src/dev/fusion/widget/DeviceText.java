package dev.fusion.widget;

public final class DeviceText {
    public static String state(String type, int bits) {
        if ("mijia_iot_control".equals(type)) return "场景 · 在系统中操作";
        if (TelevisionPolicy.accepts("nearby", type) && (bits & TelevisionPolicy.WAITING_STATE) != 0) return "待刷新";
        if ((bits & 1) == 0) return "离线或暂不可用";
        int[] masks = {16, 1024, 4, 2, 512, 32, 8, 4096, 2048, 128, 256, 64};
        String[] labels = {"通话协同中", "拍照中", "相机协同中", "屏幕协同中", "应用接续中", "键鼠共享中", "镜像中", "通信共享中", "视频投送中", "音频投送中", "音乐播放中", "已开启"};
        for (int i = 0; i < masks.length; i++) if ((bits & masks[i]) != 0) return labels[i];
        return "在线";
    }
    public static boolean isScene(String type) { return "mijia_iot_control".equals(type); }
    private DeviceText() {}
}
