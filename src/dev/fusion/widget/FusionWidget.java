package dev.fusion.widget;

import android.app.PendingIntent;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import android.view.View;
import android.widget.RemoteViews;
import org.json.*;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class FusionWidget extends AppWidgetProvider {
    static final String REFRESH_ACTION = Protocol.APP + ".REFRESH";
    private static final ExecutorService WORK = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean BUSY = new AtomicBoolean();
    private static volatile long lastSnapshot = -12000, lastDiscovery = -12000;

    @Override public void onReceive(Context context, Intent intent) {
        String a = intent.getAction();
        boolean standard = AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(a);
        if (!standard && !REFRESH_ACTION.equals(a) && !"miui.appwidget.action.APPWIDGET_UPDATE".equals(a)) {
            super.onReceive(context, intent); return;
        }
        updateAll(context);
        RefreshJob.schedule(context);
        PendingResult pending = goAsync();
        if (!request(context, !standard, pending::finish)) pending.finish();
    }
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) manager.updateAppWidget(id, render(context, manager.getAppWidgetOptions(id)));
        RefreshJob.schedule(context);
    }
    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, Bundle options) {
        manager.updateAppWidget(id, render(context, options));
    }
    @Override public void onDisabled(Context context) { RefreshJob.cancel(context); }

    static synchronized boolean request(Context context, boolean discover, Runnable done) {
        Context app = context.getApplicationContext();
        long now = SystemClock.elapsedRealtime();
        if ((discover ? now - lastDiscovery < 12000 : now - lastSnapshot < 1000)
                || !BUSY.compareAndSet(false, true)) return false;
        lastSnapshot = now;
        if (discover) lastDiscovery = now;
        WORK.execute(() -> {
            try {
                fetch(app);
                updateAll(app);
                if (discover) {
                    BridgeClient.call(app, Protocol.REFRESH, null);
                    // A bounded refresh catches early discoveries without keeping a permanent service.
                    Thread.sleep(1800); fetch(app); updateAll(app);
                    Thread.sleep(2500); fetch(app);
                }
            } catch (Exception e) { DeviceStore.error(app, e.getMessage() == null ? "设备列表更新失败" : e.getMessage()); }
            finally { updateAll(app); BUSY.set(false); done.run(); }
        });
        return true;
    }
    static void fetch(Context context) throws Exception {
        BridgeClient.call(context, Protocol.STATUS, null);
        Bundle data = BridgeClient.call(context, Protocol.SNAPSHOT, null);
        String json = data.getString("devices");
        if (json == null) throw new Exception("系统返回了空的设备数据");
        DeviceStore.save(context, json);
    }
    static void updateAll(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        for (int id : m.getAppWidgetIds(new ComponentName(c, FusionWidget.class))) {
            try { m.updateAppWidget(id, render(c, m.getAppWidgetOptions(id))); } catch (RuntimeException ignored) {}
        }
        MainActivity.notifyChanged();
    }
    static PendingIntent activity(Context c, String action, String id) {
        if ("more".equals(action)) {
            Intent expanded = new Intent(c, MainActivity.class).setAction("expand")
                    .setData(android.net.Uri.parse("fusionwidget://expand"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            return PendingIntent.getActivity(c, 2, expanded,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
        Intent i = new Intent(c, "device".equals(action) ? DeviceActivity.class : MainActivity.class);
        i.setAction(action); i.setData(android.net.Uri.parse("fusionwidget://" + action + "/" + android.net.Uri.encode(id == null ? "" : id)));
        if (id != null) i.putExtra("id", id);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    static RemoteViews render(Context c, Bundle options) {
        RemoteViews root = new RemoteViews(c.getPackageName(), R.layout.widget);
        root.setOnClickPendingIntent(R.id.header, activity(c, "main", null));
        root.setOnClickPendingIntent(R.id.message, activity(c, "main", null));
        root.setOnClickPendingIntent(R.id.more, activity(c, "more", null));
        Intent refresh = new Intent(c, FusionWidget.class).setAction(REFRESH_ACTION);
        root.setOnClickPendingIntent(R.id.refresh, PendingIntent.getBroadcast(c, 0, refresh, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        DisplayPreferences preferences = new DisplayPreferences(c);
        List<JSONObject> devices = preferences.selected(DeviceStore.devices(c));
        String error = DeviceStore.error(c);
        int count = devices.size();
        int height = options == null ? 160 : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 160);
        int rows = height >= 270 ? 3 : height >= 190 ? 2 : 1;
        int limit = Math.min(count, rows * 2);
        if (count == 0) {
            root.setTextViewText(R.id.message, error.isEmpty() ? "首页暂无常驻模块，点击打开展示设置" : error);
            root.setViewVisibility(R.id.rows, View.GONE); root.setViewVisibility(R.id.message, View.VISIBLE);
        } else {
            root.setViewVisibility(R.id.rows, View.VISIBLE); root.setViewVisibility(R.id.message, View.GONE);
            root.removeAllViews(R.id.rows);
            for (int i = 0; i < limit; i += 2) {
                RemoteViews row = new RemoteViews(c.getPackageName(), R.layout.row);
                for (int j = i; j < Math.min(i+2, limit); j++) {
                    JSONObject d = devices.get(j);
                    RemoteViews tile = new RemoteViews(c.getPackageName(), R.layout.tile);
                    tile.setTextViewText(R.id.name, d.optString("title", "设备"));
                    boolean hiddenGroup = "fusion_hidden".equals(d.optString("deviceType"));
                    String state = hiddenGroup ? d.optString("subtitle") : DeviceText.state(d.optString("deviceType"), d.optInt("state"));
                    String battery = battery(d.optString("battery"));
                    tile.setTextViewText(R.id.state, state + (battery.isEmpty() ? "" : " · " + battery));
                    tile.setImageViewBitmap(R.id.icon, DeviceIcons.make(c, d));
                    tile.setContentDescription(R.id.tile, d.optString("title") + "，" + state + "，打开系统面板");
                    tile.setOnClickPendingIntent(R.id.tile, hiddenGroup ? activity(c, "hidden", null) : activity(c, "device", d.optString("id")));
                    row.addView(R.id.row, tile);
                }
                root.addView(R.id.rows, row);
            }
        }
        long time = DeviceStore.time(c);
        String footer;
        if (!error.isEmpty()) footer = count > 0 ? "缓存 · 更新失败，点标题查看原因" : "点标题查看启用步骤";
        else if (time == 0) footer = "请先启用 LSPosed 模块";
        else footer = (System.currentTimeMillis() - time > 300000 ? "缓存 " : "读取于 ") + new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date(time)) + " · 常驻 " + count + " 个模块";
        root.setTextViewText(R.id.footer, footer);
        return root;
    }
    static String battery(String value) {
        try {
            JSONArray a = new JSONArray(value);
            for (int i = 0; i < a.length(); i++) { double v = a.optDouble(i, -1); if (v >= 0 && v <= 100) return Math.round(v <= 1 ? v*100 : v) + "%"; }
        } catch (Exception ignored) {}
        return "";
    }
}
