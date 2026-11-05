package dev.fusion.widget;

import android.app.*;
import android.os.*;
import android.widget.Toast;

/** A visible launch boundary keeps widget clicks user-initiated. */
public final class DeviceActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean cancelled;
    private String id;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km == null || km.isDeviceLocked()) { fail("请先解锁手机"); return; }
        id = getIntent().getStringExtra("id");
        if (id == null || id.isEmpty()) { fail("设备请求无效"); return; }
        new Thread(() -> {
            try {
                DeviceStore.operation(this,id,"正在刷新…");
                FusionWidget.fetch(this);
                if (!available()) {
                    BridgeClient.call(this,Protocol.REFRESH,null);
                    for(int retry=0;retry<12&&!cancelled&&!available();retry++) {Thread.sleep(1000);FusionWidget.fetch(this);}
                }
                if(cancelled)return;
                if(!available())throw new Exception("设备暂时无法操作，请确认设备在线后重试");
                DeviceStore.operation(this,id,"正在打开…");
                Bundle open = BridgeClient.call(this, Protocol.OPEN, id);
                String ticket = open.getString("ticket");
                if (ticket == null) throw new Exception("系统未返回面板请求");
                for (int i = 0; i < 24 && !cancelled; i++) {
                    Thread.sleep(350);
                    Bundle b = BridgeClient.call(this, Protocol.PANEL, ticket);
                    String s = b.getString("state", "pending");
                    if ("shown".equals(s) || "hidden".equals(s)) { main.post(()->{DeviceStore.operation(this,id,"");finish();}); return; }
                    if ("error".equals(s)) throw new Exception(b.getString("detail", "系统面板打开失败"));
                }
                if (!cancelled) throw new Exception("面板未确认显示，请从「更多」打开系统融合设备中心");
            } catch (Exception e) { if (!cancelled) main.post(() -> fail(e.getMessage())); }
        }, "FusionPanelLaunch").start();
    }
    private boolean available() {
        org.json.JSONArray list=DeviceStore.devices(this);
        for(int i=0;i<list.length();i++){org.json.JSONObject d=list.optJSONObject(i);if(d!=null&&id.equals(d.optString("id")))return !d.optBoolean("stale")&&(d.optInt("state")&1)!=0;}
        return false;
    }
    private void fail(String message) {
        if(id!=null)DeviceStore.operation(this,id,"暂时无法操作");
        Toast.makeText(this,message==null?"设备暂时无法操作":message,Toast.LENGTH_LONG).show();finish();
    }
    @Override protected void onDestroy() {
        cancelled=true;
        if(id!=null&&DeviceStore.operation(this,id).startsWith("正在"))DeviceStore.operation(this,id,"");
        super.onDestroy();
    }
}
