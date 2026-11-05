package dev.fusion.widget;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

public final class BridgeClient {
    public static Bundle call(Context c, String method, String argument) throws Exception {
        Bundle b;
        boolean compatibility = DeviceStore.compatibilityMode(c);
        Bundle extras = new Bundle(); extras.putBoolean(Protocol.COMPATIBILITY_MODE, compatibility);
        try { b = c.getContentResolver().call(Uri.parse(Protocol.URI + "/method"), method, argument, extras); }
        catch (SecurityException e) { throw new Exception("模块未连接：请在 LSPosed 启用并勾选「互联互通服务」，然后重启手机"); }
        catch (RuntimeException e) { throw new Exception("无法连接 MiLink，请确认系统组件正常运行"); }
        if (b == null || !b.containsKey("ok")) throw new Exception("模块未加载：请启用 LSPosed 模块并重启手机");
        if (!b.getBoolean("ok")) throw new Exception(b.getString("error", "系统接口暂不可用"));
        if (Protocol.STATUS.equals(method)) {
            if(b.getInt("bridge",0)<Protocol.BRIDGE_VERSION)throw new Exception("MiLink Hook 尚未更新，请重新加载互联互通进程或重启手机");
            if (compatibility && !b.containsKey("compatibility_mode"))
                throw new Exception("兼容模式尚未生效，请重新加载设备互联进程或重启手机");
            if (!b.getBoolean("compatible"))
                throw new Exception("当前 MiLink " + b.getString("milink", "未知版本") + " 尚未适配，可在「关于」中开启 MiLink 兼容模式");
        }
        return b;
    }
    private BridgeClient() {}
}
