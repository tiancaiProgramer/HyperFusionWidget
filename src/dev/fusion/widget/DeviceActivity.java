package dev.fusion.widget;

import android.app.*;
import android.os.*;
import android.widget.Toast;

/** A visible launch boundary keeps widget clicks user-initiated. */
public final class DeviceActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km == null || km.isDeviceLocked()) { fail("请先解锁手机"); return; }
        String id = getIntent().getStringExtra("id");
        if (id == null || id.isEmpty()) { fail("设备请求无效"); return; }
        new Thread(() -> {
            try {
                Bundle open = BridgeClient.call(this, Protocol.OPEN, id);
                String ticket = open.getString("ticket");
                if (ticket == null) throw new Exception("系统未返回面板请求");
                for (int i = 0; i < 24 && !cancelled; i++) {
                    Thread.sleep(350);
                    Bundle b = BridgeClient.call(this, Protocol.PANEL, ticket);
                    String s = b.getString("state", "pending");
                    if ("shown".equals(s) || "hidden".equals(s)) { main.post(this::finish); return; }
                    if ("error".equals(s)) throw new Exception(b.getString("detail", "系统面板打开失败"));
                }
                if (!cancelled) throw new Exception("面板未确认显示，请从「更多」打开系统融合设备中心");
            } catch (Exception e) { if (!cancelled) main.post(() -> fail(e.getMessage())); }
        }, "FusionPanelLaunch").start();
    }
    private void fail(String message) { Toast.makeText(this, message, Toast.LENGTH_LONG).show(); finish(); }
    @Override protected void onDestroy() { cancelled = true; super.onDestroy(); }
}
