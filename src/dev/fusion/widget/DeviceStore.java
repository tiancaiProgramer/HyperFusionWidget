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
    public static JSONArray devices(Context c) {
        try { return new JSONArray(value(c, "list", "[]")); } catch (Exception e) { return new JSONArray(); }
    }
    public static void save(Context c, String json) throws JSONException {
        new JSONArray(json);
        ContentValues row = new ContentValues(); row.put("list", json); row.put("time", System.currentTimeMillis()); row.put("error", "");
        db(c).update("snapshot", row, "id=1", null);
    }
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
