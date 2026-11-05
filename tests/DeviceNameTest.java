package dev.fusion.widget;
public final class DeviceNameTest {
    static void check(boolean yes){if(!yes)throw new AssertionError("Device name retention");}
    public static void main(String[] args){check("客厅的小米电视".equals(DeviceNamePolicy.resolve("电视","客厅的小米电视",false)));check("客厅的小米电视".equals(DeviceNamePolicy.resolve("","客厅的小米电视",false)));check("卧室电视".equals(DeviceNamePolicy.resolve("卧室电视","客厅的小米电视",true)));check("电视".equals(DeviceNamePolicy.resolve("电视","客厅的小米电视",true)));check("客厅的小米电视".equals(DeviceNamePolicy.resolve("已固定设备","客厅的小米电视",false)));check("".equals(DeviceNamePolicy.resolve(null,null,false)));System.out.println("6 device-name synchronization checks passed");}
}
