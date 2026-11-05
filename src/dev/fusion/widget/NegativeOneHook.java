package dev.fusion.widget;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.content.pm.*;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.*;
import android.widget.Toast;
import io.github.libxposed.api.XposedModule;
import java.lang.reflect.*;

/** Handles only an authenticated one-shot request from our app; normal picker UI is untouched. */
final class NegativeOneHook {
    private static final String PICKER = "com.miui.personalassistant.picker.business.home.pages.PickerHomeActivity";
    private static final String BASELINE = "25.31.06-07091932";
    static void install(XposedModule module, ClassLoader loader) throws Exception {
        Class<?> picker = loader.loadClass(PICKER);
        Class<?> item = loader.loadClass("com.miui.personalassistant.widget.entity.ItemInfo");
        Class<?> widget = loader.loadClass("com.miui.personalassistant.widget.iteminfo.AppWidgetItemInfo");
        Method add = picker.getMethod("addWidget", View.class, int.class, item, int.class, boolean.class);
        Method span = loader.loadClass("com.miui.personalassistant.picker.util.f").getMethod("a", item, int.class);
        Constructor<?> constructor = widget.getConstructor(AppWidgetProviderInfo.class);
        class AddFlow {
            void request(Activity activity, Intent intent) {
                if (intent == null || !intent.getBooleanExtra("fusionWidgetAdd", false)) return;
                PendingIntent proof = intent.getParcelableExtra("fusionWidgetCaller");
                intent.removeExtra("fusionWidgetAdd"); intent.removeExtra("fusionWidgetCaller");
                if (!trusted(activity, proof)) return;
                boolean bound=intent.getBooleanExtra("fusionWidgetBound",false);intent.removeExtra("fusionWidgetBound");
                android.os.ResultReceiver reply=intent.getParcelableExtra("fusionWidgetResult");intent.removeExtra("fusionWidgetResult");
                activity.getIntent().putExtra("fusionWidgetResult",reply);
                try {
                    if (!BASELINE.equals(activity.getPackageManager().getPackageInfo("com.miui.personalassistant", 0).versionName)) {
                        finish(activity, "当前智能助理版本尚未适配"); return;
                    }
                    // This invocation is an app command, not a visible picker session.
                    Window window = activity.getWindow();
                    window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
                    window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                    window.setBackgroundBlurRadius(0); window.setWindowAnimations(0);
                    View root = window.getDecorView(); root.setAlpha(0f);
                    root.post(() -> {
                        try {
                            if(bound){module.log(android.util.Log.INFO,"FusionWidget","MiLink-only add request: existing widget binding");finish(activity,"负一屏已添加融合设备卡片");return;}
                            Class<?> overlay = loader.loadClass("com.miui.personalassistant.core.overlay.AssistantOverlayWindow");
                            Object nativeWindow = overlay.getMethod("R").invoke(null);
                            if (nativeWindow == null) { finish(activity, "请先在桌面打开一次负一屏，再回来添加"); return; }
                            Field content = overlay.getDeclaredField("t"); content.setAccessible(true);
                            Object host = content.get(nativeWindow);
                            if (host == null) { finish(activity, "负一屏正在加载，请稍后再试"); return; }
                            Object controller = host.getClass().getMethod("getWidgetController").invoke(host);
                            Object all = controller.getClass().getMethod("getAllWidgets").invoke(controller);
                            if (all instanceof Iterable) for (Object v : (Iterable<?>) all) {
                                Object existing = v.getClass().getMethod("getItemInfo").invoke(v);
                                if (Protocol.APP.equals(item.getField("appPackageName").get(existing))) {
                                    module.log(android.util.Log.INFO, "FusionWidget", "App add request: already present");
                                    finish(activity, "负一屏已添加融合设备卡片"); return;
                                }
                            }
                            AppWidgetProviderInfo selected = null;
                            for (AppWidgetProviderInfo provider : AppWidgetManager.getInstance(activity).getInstalledProviders()) {
                                if (new ComponentName(Protocol.APP, Protocol.APP + ".FusionWidget").equals(provider.provider)) { selected = provider; break; }
                            }
                            if (selected == null) { finish(activity, "未找到融合设备卡片，请重新安装应用"); return; }
                            Object info = constructor.newInstance(selected);
                            span.invoke(null, info, 2);
                            item.getField("title").set(info, "融合设备中心");
                            item.getField("appName").set(info, "融合设备卡片");
                            item.getField("appPackageName").set(info, Protocol.APP);
                            item.getField("status").setInt(info, 1);
                            item.getField("appVersionCode").setInt(info, activity.getPackageManager().getPackageInfo(Protocol.APP, 0).versionCode);
                            item.getField("addWay").setInt(info, 1002);
                            item.getField("implUniqueCode").set(info, "fusion_widget_local_v1");
                            item.getMethod("createExpend").invoke(info);
                            item.getField("bitmap").set(info, Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888));
                            // Preserve native count checks, host binding and placement; no add animation.
                            add.invoke(activity, root, 1, info, 286261248, false);
                            module.log(android.util.Log.INFO, "FusionWidget", "App add request: native pipeline invoked");
                            // Native rejection (e.g. count limit) need not leave an invisible picker open.
                            root.postDelayed(() -> { finish(activity, "已提交负一屏添加，请返回负一屏查看卡片。"); }, 2000);
                        } catch (Throwable e) { failed(activity, e); }
                    });
                } catch (Throwable e) { failed(activity, e); }
            }
            void failed(Activity activity, Throwable e) {
                Throwable cause = e instanceof InvocationTargetException ? ((InvocationTargetException)e).getTargetException() : e;
                module.log(android.util.Log.ERROR, "FusionWidget", "App add failed: " + android.util.Log.getStackTraceString(cause));
                finish(activity, "添加失败（" + cause.getClass().getSimpleName() + "）");
            }
        }
        AddFlow flow = new AddFlow();
        module.hook(picker.getDeclaredMethod("onResume")).intercept(chain -> {
            Object result = chain.proceed(); Activity activity = (Activity)chain.getThisObject();
            flow.request(activity, activity.getIntent()); return result;
        });
        module.hook(picker.getDeclaredMethod("onNewIntent", Intent.class)).intercept(chain -> {
            Object result = chain.proceed();
            flow.request((Activity)chain.getThisObject(), (Intent)chain.getArg(0)); return result;
        });
        module.log(android.util.Log.INFO, "FusionWidget", "MiLink-only negative-one bridge v"+Protocol.BRIDGE_VERSION+" registered; picker UI unchanged");
    }
    private static boolean trusted(Context context, PendingIntent proof) {
        if (proof == null || !proof.isImmutable() || !Protocol.APP.equals(proof.getCreatorPackage())) return false;
        try {
            int uid = proof.getCreatorUid(); PackageManager pm = context.getPackageManager();
            String[] packages = pm.getPackagesForUid(uid);
            PackageInfo info = pm.getPackageInfo(Protocol.APP, PackageManager.GET_SIGNING_CERTIFICATES);
            android.content.pm.Signature[] certs = info.signingInfo.getApkContentsSigners();
            return certs != null && certs.length == 1 && CallerPolicy.allows(uid, info.applicationInfo.uid,
                    packages != null && packages.length == 1 && Protocol.APP.equals(packages[0]), certs[0].toByteArray());
        } catch (Exception e) { return false; }
    }
    private static void finish(Activity activity, String message) {
        android.os.ResultReceiver reply=activity.getIntent().getParcelableExtra("fusionWidgetResult");
        if(reply!=null){android.os.Bundle data=new android.os.Bundle();data.putString("message",message);reply.send(0,data);activity.getIntent().removeExtra("fusionWidgetResult");}
        Toast.makeText(activity, message, Toast.LENGTH_LONG).show(); activity.finish(); activity.overridePendingTransition(0, 0);
    }
}
