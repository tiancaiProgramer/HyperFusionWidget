package dev.fusion.widget;

import android.content.Context;
import org.json.*;
import java.util.*;

/** A single JSON setting makes each layout change atomic across App and widget processes. */
final class DisplayPreferences {
    private static final Object LOCK = new Object();
    private static final String KEY = "layout_v3";
    private final Context context;
    DisplayPreferences(Context context) { this.context = context.getApplicationContext(); }

    DevicePresentation layout(JSONArray snapshot) {
        synchronized (LOCK) { return DeviceStore.transaction(context, () -> read(snapshot)); }
    }
    private DevicePresentation read(JSONArray snapshot) {
        String saved = DeviceStore.preference(context, KEY, "");
        DevicePresentation result = new DevicePresentation();
        List<String> ids = ids(snapshot);
        if (saved.isEmpty()) {
            int limit = 2;
            try { limit = Integer.parseInt(DeviceStore.preference(context, "resident", "2")); } catch (NumberFormatException ignored) {}
            result = DevicePresentation.migrate(ids, new HashSet<>(array(DeviceStore.preference(context, "hidden", "[]"))), limit);
            if (!ids.isEmpty()) save(result);
            return result;
        }
        try {
            JSONObject data = new JSONObject(saved);
            result.pinned.addAll(array(data.optJSONArray("pinned")));
            result.hidden.addAll(array(data.optJSONArray("hidden"))); result.hidden.remove(DevicePresentation.HIDDEN);
            result.allOrder.addAll(array(data.optJSONArray("allOrder")));
            result.hiddenOrder.addAll(array(data.optJSONArray("hiddenOrder")));
            if (result.discover(ids)) save(result);
        } catch (JSONException ignored) {
            result = DevicePresentation.migrate(ids, Collections.emptySet(), 2);
            if (!ids.isEmpty()) save(result);
        }
        return result;
    }
    void pin(String id, boolean selected) {
        synchronized (LOCK) { DeviceStore.transaction(context, () -> { DevicePresentation model = read(DeviceStore.devices(context)); model.pin(id, selected); save(model); return null; }); }
    }
    void hide(String id, boolean hidden) {
        synchronized (LOCK) { DeviceStore.transaction(context, () -> { DevicePresentation model = read(DeviceStore.devices(context)); model.hide(id, hidden); save(model); return null; }); }
    }
    void move(String scope, String source, String target) {
        synchronized (LOCK) { DeviceStore.transaction(context, () -> { DevicePresentation model = read(DeviceStore.devices(context)); if (model.move(scope, source, target)) save(model); return null; }); }
    }
    List<JSONObject> selected(JSONArray snapshot) {
        DevicePresentation model = layout(snapshot); Map<String,JSONObject> byId = new LinkedHashMap<>();
        for (int i = 0; i < snapshot.length(); i++) { JSONObject d = snapshot.optJSONObject(i); if (d != null) byId.put(d.optString("id"), d); }
        List<JSONObject> result = new ArrayList<>();
        for (String id : model.home(ids(snapshot))) {
            if (DevicePresentation.HIDDEN.equals(id)) {
                JSONObject group = new JSONObject();
                try { group.put("id", id); group.put("title", "隐藏设备"); group.put("deviceType", "fusion_hidden"); group.put("subtitle", model.concealed(ids(snapshot)).size()+" 台 · 点击查看"); } catch (JSONException ignored) {}
                result.add(group);
            } else if (byId.containsKey(id)) result.add(byId.get(id));
        }
        return result;
    }
    private void save(DevicePresentation model) {
        JSONObject data = new JSONObject();
        try {
            data.put("pinned", new JSONArray(model.pinned)); data.put("hidden", new JSONArray(model.hidden));
            data.put("allOrder", new JSONArray(model.allOrder)); data.put("hiddenOrder", new JSONArray(model.hiddenOrder));
            DeviceStore.putPreference(context, KEY, data.toString());
        } catch (JSONException e) { throw new IllegalStateException(e); }
    }
    private static List<String> ids(JSONArray snapshot) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i < snapshot.length(); i++) { JSONObject d = snapshot.optJSONObject(i); if (d != null && !d.optString("id").isEmpty() && !DevicePresentation.HIDDEN.equals(d.optString("id"))) result.add(d.optString("id")); }
        return result;
    }
    private static List<String> array(String json) { try { return array(new JSONArray(json)); } catch (JSONException e) { return new ArrayList<>(); } }
    private static List<String> array(JSONArray list) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (list != null) for (int i = 0; i < list.length(); i++) { String id = list.optString(i, ""); if (!id.isEmpty()) result.add(id); }
        return new ArrayList<>(result);
    }
    boolean moreOpen() { return "1".equals(DeviceStore.preference(context, "more_open", "0")); }
    void moreOpen(boolean value) { DeviceStore.putPreference(context, "more_open", value ? "1" : "0"); }
    boolean guideOpen() { return "1".equals(DeviceStore.preference(context, "guide_open", "0")); }
    void guideOpen(boolean value) { DeviceStore.putPreference(context, "guide_open", value ? "1" : "0"); }
}
