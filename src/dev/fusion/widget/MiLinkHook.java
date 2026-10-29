package dev.fusion.widget;

import android.content.ContentProvider;
import android.os.Bundle;
import android.util.Log;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface;
import java.lang.reflect.Method;

public final class MiLinkHook extends XposedModule {
    private String process;
    private boolean registered;
    @Override public void onModuleLoaded(XposedModuleInterface.ModuleLoadedParam p) {
        process = p.getProcessName();
    }
    @Override public void onPackageReady(XposedModuleInterface.PackageReadyParam p) {
        if (registered) return;
        if ("com.miui.personalassistant".equals(p.getPackageName()) && "com.miui.personalassistant".equals(process)) {
            try { NegativeOneHook.install(this, p.getClassLoader()); registered = true; }
            catch (Throwable e) { log(Log.ERROR, "FusionWidget", "Negative-one entry unavailable: " + e.getClass().getSimpleName()); }
            return;
        }
        if (!Protocol.TARGET.equals(p.getPackageName()) || !(Protocol.TARGET + ":ui").equals(process)) return;
        try {
            ClassLoader loader = p.getClassLoader();
            Class<?> provider = loader.loadClass("com.miui.circulate.device.service.DeviceControlProvider");
            Method call = provider.getDeclaredMethod("call", String.class, String.class, Bundle.class);
            hook(call).intercept(chain -> {
                String method = (String) chain.getArg(0);
                if (method == null || !method.startsWith(Protocol.PREFIX)) return chain.proceed();
                try {
                    return MiLinkBridge.dispatch((ContentProvider) chain.getThisObject(), method,
                            (String) chain.getArg(1), (Bundle) chain.getArg(2), loader);
                } catch (Throwable e) {
                    Bundle b = new Bundle(); b.putBoolean("ok", false);
                    b.putString("error", "系统接口暂不可用（" + e.getClass().getSimpleName() + "）");
                    return b;
                }
            });
            registered = true;
            log(Log.INFO, "FusionWidget", "MiLink UI bridge registered (API 102, scoped calls only)");
        } catch (Throwable e) {
            log(Log.ERROR, "FusionWidget", "Unsupported provider: " + e.getClass().getSimpleName());
        }
    }
}
