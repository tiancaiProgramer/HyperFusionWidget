package dev.fusion.widget;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.os.SystemClock;
import android.util.Log;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Iterator;
import org.json.JSONObject;

/** Runs only inside MiLink; native identifiers and objects never leave that process. */
final class NativeTelevisions {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Object connection, framework;
    private static Method disconnect;
    private static boolean warned;
    private static final Runnable STOP = NativeTelevisions::stopDiscovery;
    private static final LinkedHashMap<String, Television> RECENT = new LinkedHashMap<>(16, .75f, true);

    static final class Television {
        final String nativeId, id, title, type;
        final long seen, updateTime;
        final boolean live;
        Television(String nativeId, String title, String type, long seen, long updateTime, boolean live) {
            this.nativeId = nativeId; this.id = TelevisionPolicy.publicId(nativeId); this.title = title; this.type = type;
            this.seen = seen; this.updateTime = updateTime; this.live = live;
        }
        JSONObject json() throws Exception {
            return new JSONObject().put("id", id).put("category", "nearby").put("deviceType", type)
                    .put("title", title).put("subtitle", "").put("state", Integer.toString(live ? 1 : TelevisionPolicy.WAITING_STATE)).put("battery", "")
                    .put("icon", "").put("updateTime", Long.toString(updateTime));
        }
        Parcelable panel(ClassLoader loader) throws Exception {
            Class<?> builderClass = loader.loadClass("com.miui.circulate.device.api.DeviceInfo$Builder");
            Object builder = builderClass.getConstructor().newInstance();
            builderClass.getMethod("setId", String.class).invoke(builder, nativeId);
            builderClass.getMethod("setCategory", String.class).invoke(builder, "nearby");
            builderClass.getMethod("setDeviceType", String.class).invoke(builder, type);
            builderClass.getMethod("setTitle", String.class).invoke(builder, title);
            builderClass.getMethod("setState", int.class).invoke(builder, 1);
            return (Parcelable) builderClass.getMethod("build").invoke(builder);
        }
    }

    static synchronized List<Television> read(ClassLoader loader) {
        long now = SystemClock.elapsedRealtime(); Set<String> live = new HashSet<>();
        try {
            Class<?> managerClass = loader.loadClass("com.miui.circulate.api.service.o");
            Object manager = managerClass.getMethod("h").invoke(null);
            Object values = managerClass.getMethod("i").invoke(manager);
            Collection<?> entries = values instanceof Collection ? new ArrayList<>((Collection<?>) values) : Collections.emptyList();
            for (Object item : entries) {
                Class<?> type = item.getClass();
                String category = (String) type.getField("deviceCategory").get(item);
                String deviceType = (String) type.getField("devicesType").get(item);
                if (!TelevisionPolicy.accepts(category, deviceType)) continue;
                String nativeId = (String) type.getField("id").get(item);
                String title = (String) type.getField("devicesName").get(item);
                if (nativeId == null || nativeId.isEmpty() || title == null || title.isEmpty()) continue;
                live.add(nativeId);
                RECENT.put(nativeId, new Television(nativeId, title, deviceType, now, System.currentTimeMillis(), true));
                if (RECENT.size() > 128) RECENT.remove(RECENT.keySet().iterator().next());
                if (live.size() == 128) break;
            }
        } catch (ReflectiveOperationException | LinkageError | ClassCastException e) { warn(e); }
        List<Television> result = new ArrayList<>();
        Iterator<Television> iterator = RECENT.values().iterator();
        while (iterator.hasNext()) {
            Television tv = iterator.next();
            if (!TelevisionPolicy.isRecent(tv.seen, now)) { iterator.remove(); continue; }
            result.add(new Television(tv.nativeId, tv.title, tv.type, tv.seen, tv.updateTime, live.contains(tv.nativeId)));
        }
        return result;
    }

    static Parcelable findPanel(ClassLoader loader, String id) throws Exception {
        for (Television tv : read(loader)) if (tv.live && tv.id.equals(id)) return tv.panel(loader);
        return null;
    }

    static void startDiscovery(Context context, ClassLoader loader) {
        stopDiscovery();
        try {
            Class<?> consent = loader.loadClass("com.miui.circulate.world.service.p");
            if (!Boolean.TRUE.equals(consent.getMethod("a", Context.class).invoke(null, context))) return;
            Class<?> optionsClass = loader.loadClass("p8.b");
            Class<?> builderClass = loader.loadClass("p8.b$b");
            Class<?> callbackClass = loader.loadClass("p8.a");
            Object builder = builderClass.getConstructor().newInstance();
            builderClass.getMethod("c", String.class).invoke(builder, "FusionWidgetTV");
            builderClass.getMethod("b", List.class).invoke(builder, Arrays.asList(65536, 262144, 327681));
            Object options = builderClass.getMethod("a").invoke(builder);
            Class<?> frameworkClass = loader.loadClass("com.miui.circulate.world.service.h");
            framework = frameworkClass.getMethod("k", Context.class).invoke(null, context.getApplicationContext());
            disconnect = frameworkClass.getMethod("a", callbackClass);
            connection = Proxy.newProxyInstance(loader, new Class<?>[]{callbackClass}, (proxy, method, args) -> {
                switch (method.getName()) {
                    case "hashCode": return System.identityHashCode(proxy);
                    case "equals": return args != null && args.length == 1 && proxy == args[0];
                    case "toString": return "FusionWidgetTV";
                    case "onError": Log.w("FusionWidget", "Native TV discovery unavailable"); break;
                    default: break;
                }
                return null;
            });
            frameworkClass.getMethod("b", optionsClass, callbackClass).invoke(framework, options, connection);
            MAIN.postDelayed(STOP, 12000);
        } catch (ReflectiveOperationException | LinkageError e) { warn(e); stopDiscovery(); }
    }

    static void stopDiscovery() {
        MAIN.removeCallbacks(STOP);
        Object previous = connection, owner = framework; Method release = disconnect;
        connection = null; framework = null; disconnect = null;
        if (previous != null && owner != null && release != null) {
            try { release.invoke(owner, previous); }
            catch (ReflectiveOperationException e) { warn(e); }
        }
    }

    private static synchronized void warn(Throwable e) {
        if (!warned) { warned = true; Log.w("FusionWidget", "Native TV interface unavailable: " + e.getClass().getSimpleName()); }
    }
    private NativeTelevisions() {}
}
