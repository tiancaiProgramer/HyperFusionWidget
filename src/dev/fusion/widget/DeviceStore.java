package dev.fusion.widget;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import org.json.*;

/** Transactional cache shared by the foreground and dedicated widget processes. */
public final class DeviceStore {
    private static Cache database;
    private static synchronized SQLiteDatabase db(Context c) {
        if (database == null) database = new Cache(c.getApplicationContext());
        return database.getWritableDatabase();
    }
    private static String value(Context c, String column, String fallback) {
        try (Cursor cursor = db(c).query("snapshot", new String[]{column}, "id=1", null, null, null, null)) {
            return cursor.moveToFirst() && !cursor.isNull(0) ? cursor.getString(0) : fallback;
        }
    }
    static void separateCloud(Context c){
        if("1".equals(preference(c,"cloud_separated_v1","0")))return;
        boolean migrated="1".equals(preference(c,"haier_migration_complete","0"));
        transaction(c,()->{try{
            JSONObject layout=new JSONObject(preference(c,"layout_v3","{}"));for(String key:new String[]{"pinned","hidden","allOrder","hiddenOrder"}){JSONArray a=layout.optJSONArray(key);if(a==null)continue;JSONArray keep=new JSONArray();for(int i=0;i<a.length();i++)if(!a.optString(i).startsWith("haier:"))keep.put(a.get(i));layout.put(key,keep);}putPreference(c,"layout_v3",layout.toString());
            for(String key:new String[]{"remembered_devices","known_device_names"}){JSONObject data=new JSONObject(preference(c,key,"{}"));java.util.List<String> remove=new java.util.ArrayList<>();for(java.util.Iterator<String> i=data.keys();i.hasNext();){String id=i.next();if(id.startsWith("haier:"))remove.add(id);}for(String id:remove)data.remove(id);putPreference(c,key,data.toString());}
            db(c).delete("presentation","key LIKE ? OR key LIKE ?",new String[]{"haier_%","operation:haier:%"});putPreference(c,"cloud_separated_v1","1");return null;
        }catch(JSONException e){throw new IllegalStateException(e);}});
        if(migrated){for(String file:new String[]{"haier-session.enc","haier-session.enc.bak","haier-session.enc.new","haier.lock"})new java.io.File(c.getNoBackupFilesDir(),file).delete();try{java.security.KeyStore keys=java.security.KeyStore.getInstance("AndroidKeyStore");keys.load(null);keys.deleteEntry("fusion.haier.session.v1");}catch(Exception ignored){}}
    }
    public static JSONArray devices(Context c) {
        separateCloud(c);
        try {
            JSONArray live = new JSONArray(value(c, "list", "[]"));
            JSONObject names=new JSONObject(preference(c,"known_device_names","{}"));
            for(int i=0;i<live.length();i++){JSONObject d=live.getJSONObject(i);String title=DeviceNamePolicy.resolve(d.optString("title"),names.optString(d.optString("id")),!d.optBoolean("stale")&&(d.optInt("state")&1)!=0);if(!title.isEmpty())d.put("title",title);}
            java.util.Set<String> seen = new java.util.HashSet<>();
            for (int i=0;i<live.length();i++) seen.add(live.getJSONObject(i).optString("id"));
            JSONObject saved = new JSONObject(preference(c,"remembered_devices","{}"));
            for (String id : retained(c)) if (!seen.contains(id) && !DevicePresentation.HIDDEN.equals(id)) {
                JSONObject item = saved.optJSONObject(id);
                if (item == null) item = new JSONObject().put("id",id).put("title",TelevisionPolicy.isPublicId(id)?"电视":"已固定设备").put("deviceType",TelevisionPolicy.isPublicId(id)?"tv":"unknown");
                else item = new JSONObject(item.toString());
                String title=DeviceNamePolicy.resolve(item.optString("title"),names.optString(id),false);if(!title.isEmpty())item.put("title",title);
                item.put("stale",true).put("state",0);live.put(item);
            }
            return live;
        } catch (Exception e) { return new JSONArray(); }
    }
    public static void save(Context c, String json) throws JSONException {
        JSONArray incoming = new JSONArray(json);
        transaction(c, () -> {
        rememberNames(c,incoming);
        remember(c, incoming);
        ContentValues row = new ContentValues(); row.put("list", incoming.toString()); row.put("time", System.currentTimeMillis()); row.put("error", "");
        db(c).update("snapshot", row, "id=1", null);
        return null; });
    }
    private static void rememberNames(Context c,JSONArray incoming){
        try{JSONObject names=new JSONObject(preference(c,"known_device_names","{}"));for(JSONArray source:new JSONArray[]{new JSONArray(value(c,"list","[]")),incoming})for(int i=0;i<source.length();i++){JSONObject d=source.getJSONObject(i);String id=d.optString("id"),name=DeviceNamePolicy.resolve(d.optString("title"),names.optString(id),!d.optBoolean("stale")&&(d.optInt("state")&1)!=0);if(!id.isEmpty()&&!name.isEmpty()){names.put(id,name);d.put("title",name);}}putPreference(c,"known_device_names",names.toString());}catch(JSONException e){throw new IllegalStateException(e);}
    }
    private static java.util.Set<String> retained(Context c) throws JSONException {
        JSONObject layout = new JSONObject(preference(c,"layout_v3","{}"));
        java.util.Set<String> ids = new java.util.LinkedHashSet<>();
        for(String key:new String[]{"pinned","hidden"}) {JSONArray a=layout.optJSONArray(key);if(a!=null)for(int i=0;i<a.length();i++)ids.add(a.getString(i));}
        return ids;
    }
    static void remember(Context c, JSONArray incoming) {
        try {
            java.util.Set<String> retain=retained(c);JSONObject saved=new JSONObject(preference(c,"remembered_devices","{}")),next=new JSONObject();
            for(String id:retain)if(saved.has(id))next.put(id,saved.get(id));
            for(JSONArray source:new JSONArray[]{new JSONArray(value(c,"list","[]")),incoming})for(int i=0;i<source.length();i++) {
                JSONObject d=source.optJSONObject(i);if(d==null||!retain.contains(d.optString("id")))continue;
                JSONObject item=new JSONObject();for(String key:new String[]{"id","title","deviceType","category","icon","battery","subtitle","updateTime"})item.put(key,d.optString(key));
                next.put(d.optString("id"),item);
            }
            putPreference(c,"remembered_devices",next.toString());
        }catch(JSONException e){throw new IllegalStateException(e);}
    }
    static String operation(Context c,String id) {return preference(c,"operation:"+id,"");}
    static void operation(Context c,String id,String message) {putPreference(c,"operation:"+id,message);FusionWidget.updateAll(c);}
    public static long time(Context c) { return Long.parseLong(value(c, "time", "0")); }
    public static String error(Context c) { return value(c, "error", ""); }
    static boolean compatibilityMode(Context c) { return "1".equals(preference(c, Protocol.COMPATIBILITY_MODE, "1")); }
    static void compatibilityMode(Context c, boolean enabled) { putPreference(c, Protocol.COMPATIBILITY_MODE, enabled ? "1" : "0"); }
    public static void error(Context c, String message) {
        ContentValues row = new ContentValues(); row.put("error", message == null ? "设备列表更新失败" : message); db(c).update("snapshot", row, "id=1", null);
    }
    static String preference(Context c, String key, String fallback) {
        try (Cursor cursor = db(c).query("presentation", new String[]{"value"}, "key=?", new String[]{key}, null, null, null)) {
            return cursor.moveToFirst() ? cursor.getString(0) : fallback;
        }
    }
    static void putPreference(Context c, String key, String value) {
        ContentValues row = new ContentValues(); row.put("key", key); row.put("value", value);
        db(c).insertWithOnConflict("presentation", null, row, SQLiteDatabase.CONFLICT_REPLACE);
    }
    static <T> T transaction(Context c, java.util.function.Supplier<T> operation) {
        SQLiteDatabase database = db(c);
        database.beginTransaction();
        try { T result = operation.get(); database.setTransactionSuccessful(); return result; }
        finally { database.endTransaction(); }
    }
    private static final class Cache extends SQLiteOpenHelper {
        private final Context context;
        Cache(Context c) { super(c, "devices.db", null, 2); context = c; }
        @Override public void onCreate(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE snapshot(id INTEGER PRIMARY KEY CHECK(id=1), list TEXT NOT NULL, time INTEGER NOT NULL, error TEXT NOT NULL)");
            SharedPreferences old = context.getSharedPreferences("devices", Context.MODE_PRIVATE);
            ContentValues row = new ContentValues(); row.put("id", 1); row.put("list", old.getString("list", "[]"));
            row.put("time", old.getLong("time", 0)); row.put("error", old.getString("error", "")); database.insertOrThrow("snapshot", null, row);
            createPresentation(database);
        }
        private void createPresentation(SQLiteDatabase database) { database.execSQL("CREATE TABLE IF NOT EXISTS presentation(key TEXT PRIMARY KEY, value TEXT NOT NULL)"); }
        @Override public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
            if (oldVersion == 1 && newVersion == 2) createPresentation(database);
            else throw new IllegalStateException("Unsupported cache version");
        }
    }
    private DeviceStore() {}
}
