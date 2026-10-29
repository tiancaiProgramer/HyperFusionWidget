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
    public static void error(Context c, String message) {
        ContentValues row = new ContentValues(); row.put("error", message); db(c).update("snapshot", row, "id=1", null);
    }
    private static final class Cache extends SQLiteOpenHelper {
        private final Context context;
        Cache(Context c) { super(c, "devices.db", null, 1); context = c; }
        @Override public void onCreate(SQLiteDatabase database) {
            database.execSQL("CREATE TABLE snapshot(id INTEGER PRIMARY KEY CHECK(id=1), list TEXT NOT NULL, time INTEGER NOT NULL, error TEXT NOT NULL)");
            SharedPreferences old = context.getSharedPreferences("devices", Context.MODE_PRIVATE);
            ContentValues row = new ContentValues(); row.put("id", 1); row.put("list", old.getString("list", "[]"));
            row.put("time", old.getLong("time", 0)); row.put("error", old.getString("error", "")); database.insertOrThrow("snapshot", null, row);
        }
        @Override public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) { throw new IllegalStateException("Unsupported cache version"); }
    }
    private DeviceStore() {}
}
