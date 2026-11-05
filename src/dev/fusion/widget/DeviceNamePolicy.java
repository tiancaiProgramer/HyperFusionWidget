package dev.fusion.widget;
/** A disconnected placeholder must retain the last real device name. */
final class DeviceNamePolicy {
    static String resolve(String incoming,String remembered,boolean live){
        String name=incoming==null?"":incoming.trim(),saved=remembered==null?"":remembered.trim();
        if(name.isEmpty())return saved;
        if(!live&&(name.equals("电视")||name.equals("已固定设备"))&&!saved.isEmpty())return saved;
        return name;
    }
    private DeviceNamePolicy(){}
}
