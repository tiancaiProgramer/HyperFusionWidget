package dev.fusion.widget;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.lang.ref.WeakReference;

public final class MainActivity extends Activity {
    private static WeakReference<MainActivity> visible = new WeakReference<>(null);
    private LinearLayout content;
    private TextView status;
    private FrameLayout preview;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean started;
    private PendingIntent addProof;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        if ("more".equals(getIntent().getAction())) {
            try { openCenter(); finish(); return; } catch (Exception ignored) {}
        }
        ScrollView scroll = new ScrollView(this); scroll.setFillViewport(true);
        content = new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(dp(22), dp(26), dp(22), dp(28));
        scroll.addView(content); setContentView(scroll);
        text("融合设备卡片", 28, true);
        text("把常用设备放到负一屏和桌面", 15, false);
        status = text("正在检查模块连接…", 14, false);
        preview = new FrameLayout(this); LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, dp(225)); pp.topMargin = dp(22); pp.bottomMargin = dp(14); content.addView(preview, pp);
        button("刷新设备", () -> {
            status.setText("正在刷新设备…");
            if (!FusionWidget.request(this, true, () -> main.post(this::render))) render();
        });
        button("添加到负一屏", () -> {
            try {
                addProof = PendingIntent.getActivity(this, 3,
                        new Intent(this, MainActivity.class).setAction(Protocol.APP + ".ADD_REQUEST"),
                        PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE);
                startActivity(new Intent().setComponent(new ComponentName("com.miui.personalassistant", "com.miui.personalassistant.picker.business.home.pages.PickerHomeActivity"))
                        .putExtra("openSource", 1).putExtra("fusionWidgetAdd", true).putExtra("fusionWidgetCaller", addProof));
                overridePendingTransition(0, 0);
            } catch (Exception e) { android.util.Log.e("FusionWidget", "Picker launch failed", e); Toast.makeText(this, "请在 LSPosed 勾选智能助理，重启智能助理后再试", Toast.LENGTH_LONG).show(); }
        });
        button("添加桌面小组件", () -> {
            AppWidgetManager m = AppWidgetManager.getInstance(this);
            if (m.isRequestPinAppWidgetSupported()) {
                boolean accepted = m.requestPinAppWidget(new ComponentName(this, FusionWidget.class), null, null);
                if (!accepted) Toast.makeText(this, "请在桌面编辑模式中添加小组件", Toast.LENGTH_LONG).show();
            } else Toast.makeText(this, "请长按桌面 → 添加小组件 → 安卓小组件 → 融合设备卡片", Toast.LENGTH_LONG).show();
        });
        button("打开系统融合设备中心", () -> { try { openCenter(); } catch (Exception e) { Toast.makeText(this, "系统入口不可用", Toast.LENGTH_LONG).show(); } });
        text("启用与添加", 19, true);
        text("1. 在 LSPosed 中启用「融合设备卡片」，勾选「设备互联 / com.milink.service」和「智能助理 / com.miui.personalassistant」。\n\n2. 重启目标应用或手机后，回到这里检查连接。\n\n3. 在本 App 点击「添加到负一屏」即可添加，已存在的卡片不会重复添加。首次使用请先在桌面打开一次负一屏。桌面小组件可通过上方按钮或桌面编辑模式添加。", 14, false);
        text("版本 · " + BuildIdentity.VERSION_NAME, 12, false);
        text("适配 MIX Fold 3 / HyperOS 3 的 MiLink " + Protocol.BASELINE + "。设备状态可能来自系统缓存，可点刷新重新发现。不适配的系统组件版本会停止调用。", 12, false);
    }
    private void openCenter() { startActivity(new Intent().setComponent(new ComponentName(Protocol.TARGET, "com.miui.circulate.world.CirculateWorldActivity"))); }
    private TextView text(String s, int sp, boolean bold) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(sp); t.setTextColor(getColor(bold ? R.color.text : R.color.secondary));
        if (bold) t.setTypeface(null, 1);
        t.setLineSpacing(dp(4), 1); LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.topMargin = dp(12); content.addView(t, lp); return t;
    }
    private void button(String s, Runnable click) {
        Button b = new Button(this); b.setText(s); b.setAllCaps(false); b.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(52)); lp.topMargin = dp(5); content.addView(b, lp);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    void render() {
        if (isFinishing() || preview == null) return;
        preview.removeAllViews(); Bundle options = new Bundle(); options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 225);
        View view = FusionWidget.render(this, options).apply(this, preview);
        preview.addView(view, new FrameLayout.LayoutParams(-1, -1));
        String e = DeviceStore.error(this);
        status.setText(e.isEmpty() ? (DeviceStore.time(this) == 0 ? "尚未读取设备，启用模块后点击刷新" : "模块已连接 · 已读取 " + DeviceStore.devices(this).length() + " 台设备") : e);
    }
    static void notifyChanged() { MainActivity a = visible.get(); if (a != null) a.main.post(a::render); }
    @Override protected void onResume() {
        super.onResume();
        if (addProof != null) { addProof.cancel(); addProof = null; }
        visible = new WeakReference<>(this); render();
        if (!started && preview != null) { started = true; FusionWidget.request(this, false, () -> main.post(this::render)); }
        RefreshJob.schedule(this);
    }
    @Override protected void onPause() { if (visible.get() == this) visible.clear(); super.onPause(); }
}
