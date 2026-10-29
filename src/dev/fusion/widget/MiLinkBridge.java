package dev.fusion.widget;

import android.app.KeyguardManager;
import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import org.json.*;
import java.util.*;

/** Runs inside MiLink :ui. Only authenticated, versioned, constrained operations. */
public final class MiLinkBridge {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final Map<String, NativePanel> PANELS = new HashMap<>();
    private static boolean searching;
    private static Runnable stopSearch;
    private static long lastRefresh;

    static Bundle dispatch(ContentProvider provider, String method, String arg, Bundle extras, ClassLoader loader) throws Exception {
        Context ctx = provider.getContext();
        if (ctx == null || !trusted(ctx)) return fail("调用身份验证失败");
        String installed = ctx.getPackageManager().getPackageInfo(Protocol.TARGET, 0).versionName;
        Bundle result = ok(); result.putString("milink", installed); result.putInt("bridge", 1);
        if (Protocol.STATUS.equals(method)) {
            result.putBoolean("compatible", Protocol.BASELINE.equals(installed));
            return result;
        }
        if (!Protocol.BASELINE.equals(installed)) return fail("MiLink 版本尚未适配，请勿强制启用");
        final int caller = Binder.getCallingUid();
        long identity = Binder.clearCallingIdentity();
        try {
            if (Protocol.SNAPSHOT.equals(method)) return snapshot(provider);
            if (Protocol.REFRESH.equals(method)) return refresh(provider);
            if (Protocol.OPEN.equals(method)) {
                if (locked(ctx)) return fail("请解锁手机后打开设备面板");
                if (arg == null || arg.isEmpty() || arg.length() > 256) return fail("设备标识无效");
                Parcelable device = null;
                try (Cursor c = query(provider, Uri.parse(Protocol.URI + "/export"))) {
                    while (c != null && c.moveToNext()) {
                        if (!arg.equals(str(c, "id"))) continue;
                        // Scenes can execute immediately rather than show a panel. Exclude them.
                        if ("mijia_iot_control".equals(str(c, "deviceType"))) return fail("此项目是直接执行的场景，请在系统融合设备中心操作");
                        device = (Parcelable) loader.loadClass("com.miui.circulate.device.api.b").getMethod("b", Cursor.class).invoke(null, c);
                        break;
                    }
                }
                if (device == null) return fail("设备已不在当前列表，请先刷新");
                String ticket = UUID.randomUUID().toString();
                NativePanel panel = new NativePanel(ctx, device, caller, ticket);
                synchronized (PANELS) {
                    for (NativePanel old : PANELS.values()) if (old.active) return fail("已有设备面板打开，请先关闭");
                    PANELS.entrySet().removeIf(e -> SystemClock.elapsedRealtime() - e.getValue().created > 600000);
                    PANELS.put(ticket, panel);
                }
                MAIN.post(panel::open);
                result.putString("ticket", ticket); return result;
            }
            if (Protocol.PANEL.equals(method)) {
                NativePanel p; synchronized (PANELS) { p = PANELS.get(arg); }
                if (p == null || p.owner != caller) return fail("面板请求已失效");
                result.putString("state", p.state); result.putString("detail", p.detail); return result;
            }
            return fail("不支持的模块请求");
        } finally { Binder.restoreCallingIdentity(identity); }
    }

    private static boolean trusted(Context ctx) {
        try {
            int uid = Binder.getCallingUid();
            PackageManager pm = ctx.getPackageManager();
            String[] packages = pm.getPackagesForUid(uid);
            PackageInfo info = pm.getPackageInfo(Protocol.APP, PackageManager.GET_SIGNING_CERTIFICATES);
            android.content.pm.Signature[] certs = info.signingInfo.getApkContentsSigners();
            return certs != null && certs.length == 1 && CallerPolicy.allows(uid, info.applicationInfo.uid,
                    packages != null && packages.length == 1 && Protocol.APP.equals(packages[0]), certs[0].toByteArray());
        } catch (Exception e) { return false; }
    }
    private static Cursor query(ContentProvider p, Uri uri) { return p.query(uri, null, null, null, null); }
    private static String str(Cursor c, String key) {
        int index = c.getColumnIndex(key); return index < 0 || c.isNull(index) ? "" : c.getString(index);
    }
    private static Bundle snapshot(ContentProvider p) throws Exception {
        JSONArray list = new JSONArray();
        try (Cursor c = query(p, Uri.parse(Protocol.URI + "/export"))) {
            if (c == null) return fail("系统设备列表暂不可用");
            while (c.moveToNext() && list.length() < 12) {
                JSONObject item = new JSONObject();
                for (String key : new String[]{"id", "category", "deviceType", "title", "subtitle", "state", "battery", "icon", "updateTime"})
                    item.put(key, str(c, key));
                list.put(item);
            }
        }
        Bundle b = ok(); b.putString("devices", list.toString()); return b;
    }
    private static Bundle refresh(ContentProvider p) {
        synchronized (MiLinkBridge.class) {
            if (SystemClock.elapsedRealtime() - lastRefresh < 12000) return ok();
            lastRefresh = SystemClock.elapsedRealtime();
            Bundle args = searchArgs();
            Bundle response = p.call("start_search", null, args);
            if (response == null || response.getInt("result", -1) != 1) return fail("系统未接受设备发现请求");
            searching = true;
            if (stopSearch != null) MAIN.removeCallbacks(stopSearch);
            stopSearch = () -> {
                synchronized (MiLinkBridge.class) {
                    if (!searching) return;
                    searching = false;
                    try { p.call("stop_search", null, searchArgs()); } catch (Throwable ignored) {}
                }
            };
            MAIN.postDelayed(stopSearch, 12000);
            return ok();
        }
    }
    private static Bundle searchArgs() {
        Bundle args = new Bundle();
        args.putParcelableArray("search_uri_list", new Uri[]{Uri.parse(Protocol.URI + "/export")});
        args.putString("from", "FusionWidget"); return args;
    }
    private static boolean locked(Context c) {
        KeyguardManager keyguard = c.getSystemService(KeyguardManager.class);
        return keyguard == null || keyguard.isDeviceLocked();
    }
    private static Bundle ok() { Bundle b = new Bundle(); b.putBoolean("ok", true); return b; }
    private static Bundle fail(String text) { Bundle b = new Bundle(); b.putBoolean("ok", false); b.putString("error", text); return b; }

    private static final class NativePanel implements ServiceConnection {
        final Context context; final Parcelable device; final int owner; final String ticket;
        final long created = SystemClock.elapsedRealtime();
        volatile boolean active = true;
        volatile String state = "pending", detail = "正在连接系统面板";
        Messenger server; boolean bound; int cardId;
        final Messenger client;
        final Runnable timeout = () -> close("error", "面板响应超时");
        NativePanel(Context c, Parcelable d, int o, String t) {
            context = c; device = d; owner = o; ticket = t;
            client = new Messenger(new Handler(Looper.getMainLooper(), message -> {
                switch (message.what) {
                    case 5: cardId = message.getData().getInt("mlCardId"); state = "created"; detail = "系统面板已创建"; break;
                    case 6: state = "shown"; detail = "系统面板已显示"; MAIN.removeCallbacks(timeout); MAIN.postDelayed(timeout, 600000); break;
                    case 4: close("hidden", "面板已关闭"); break;
                    case 9: close("error", "系统设备面板加载失败"); break;
                }
                return true;
            }));
        }
        void open() {
            if (locked(context)) { close("error", "请先解锁手机"); return; }
            try {
                Intent intent = new Intent("com.miLink.card.frame.show");
                intent.setComponent(new ComponentName(Protocol.TARGET, "com.miui.circulate.world.MLCardViewHostService"));
                bound = context.bindService(intent, this, Context.BIND_AUTO_CREATE);
                if (!bound) { close("error", "系统面板服务连接失败"); return; }
                MAIN.postDelayed(timeout, 12000);
            } catch (Throwable e) { close("error", "系统面板调用失败（" + e.getClass().getSimpleName() + "）"); }
        }
        @Override public void onServiceConnected(ComponentName name, IBinder binder) {
            if (!active || locked(context)) { close("error", "面板请求已取消或手机已锁定"); return; }
            try {
                server = new Messenger(binder);
                Message request = Message.obtain(null, 1);
                Bundle b = new Bundle();
                b.putLong("keyCardFrameVersion", 20250417L);
                b.putParcelable("deviceInfo", device); b.putString("keyDeviceToSpecifyPanel", "");
                request.setData(b); request.replyTo = client; server.send(request);
            } catch (Throwable e) { close("error", "系统面板请求失败"); }
        }
        @Override public void onServiceDisconnected(ComponentName name) { close("error", "系统面板服务已断开"); }
        @Override public void onNullBinding(ComponentName name) { close("error", "系统面板接口不可用"); }
        @Override public void onBindingDied(ComponentName name) { close("error", "系统面板连接已失效"); }
        void close(String next, String text) {
            if (!active) return;
            active = false; state = next; detail = text; MAIN.removeCallbacks(timeout);
            if (server != null && !"hidden".equals(next)) {
                try { Message m = Message.obtain(null, 2); Bundle b = new Bundle(); b.putInt("mlCardId", cardId); b.putLong("keyCardFrameVersion", 20250417L); m.setData(b); m.replyTo = client; server.send(m); } catch (Exception ignored) {}
            }
            if (bound) { bound = false; try { context.unbindService(this); } catch (Exception ignored) {} }
            server = null;
        }
    }
}
