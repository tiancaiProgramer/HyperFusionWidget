package dev.fusion.widget;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.*;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.*;

public final class MainActivity extends Activity {
    private static WeakReference<MainActivity> visible=new WeakReference<>(null);
    private final Handler main=new Handler(Looper.getMainLooper());
    private AppUi ui;
    private DisplayPreferences preferences;
    private LinearLayout content;
    private ScrollView scroll;
    private boolean settingsPage,allPage,aboutPage,hiddenExpanded,settingsReturnAll,foreground,refreshing,seeded,dragging,compatibilityChecking;
    private String settingsTab=DevicePresentation.HOME;
    private DevicePresentation model;
    private PendingIntent addProof;
    private final Set<String> knownIds=new HashSet<>(),newIds=new HashSet<>();
    private String newNotice="",feedback="";
    private LinkedHashMap<String,JSONObject> devices=new LinkedHashMap<>();
    private final Runnable poll=new Runnable() {
        @Override public void run() {
            if(!foreground)return;
            FusionWidget.request(MainActivity.this,false,()->{});
            main.postDelayed(this,15000);
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ui=new AppUi(this);preferences=new DisplayPreferences(this);
        applyIntent(getIntent());
        if(state!=null) {
            allPage=state.getBoolean("all",allPage);settingsPage=state.getBoolean("settings");aboutPage=state.getBoolean("about",aboutPage);hiddenExpanded=state.getBoolean("hidden");
            settingsReturnAll=state.getBoolean("settingsReturnAll");settingsTab=state.getString("tab",DevicePresentation.HOME);
        }
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);getWindow().setNavigationBarColor(Color.TRANSPARENT);
        getWindow().getDecorView();
        WindowInsetsController controller=getWindow().getInsetsController();
        if(controller!=null) {
            int mask=WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            boolean night=(getResources().getConfiguration().uiMode&Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;
            controller.setSystemBarsAppearance(night?0:mask,mask);
        }
        LinearLayout page=ui.column();page.setBackgroundColor(ui.background);
        page.setOnApplyWindowInsetsListener((v,insets)->{
            android.graphics.Insets bars=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());
            v.setPadding(bars.left,bars.top,bars.right,bars.bottom);return insets;
        });
        scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setClipToPadding(false);
        content=ui.column();content.setPadding(ui.dp(20),ui.dp(22),ui.dp(20),ui.dp(24));
        scroll.addView(content);page.addView(scroll,new LinearLayout.LayoutParams(-1,-1));setContentView(page);page.requestApplyInsets();
        if(Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,()->{
            navigateBack();
        });
        render();
    }
    private void applyIntent(Intent intent) {
        String action=intent.getAction();
        aboutPage="about".equals(action);
        allPage="expand".equals(action)||"more".equals(action)||"hidden".equals(action);
        hiddenExpanded="hidden".equals(action);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);setIntent(intent);settingsPage=false;applyIntent(intent);render();scrollToTop();
    }
    @Override public void onSaveInstanceState(Bundle out) {
        out.putBoolean("all",allPage);out.putBoolean("settings",settingsPage);out.putBoolean("about",aboutPage);out.putBoolean("hidden",hiddenExpanded);
        out.putBoolean("settingsReturnAll",settingsReturnAll);out.putString("tab",settingsTab);super.onSaveInstanceState(out);
    }
    @Override public void onBackPressed() { navigateBack(); }
    private void navigateBack() {
        if(aboutPage) {aboutPage=false;render();scrollToTop();}
        else if(settingsPage)leaveSettings();
        else if(allPage) {allPage=false;hiddenExpanded=false;render();scrollToTop();}
        else finish();
    }
    private void openSettings(String tab) {
        settingsReturnAll=allPage;settingsTab=tab;settingsPage=true;render();scrollToTop();
    }
    private void showAll() {allPage=true;hiddenExpanded=false;newNotice="";render();scrollToTop();}
    private void openAbout() {aboutPage=true;settingsPage=false;render();scrollToTop();}

    void render() {
        if(content==null||isFinishing()||isDestroyed())return;
        if(dragging)return;
        int position=scroll.getScrollY();Object focus=getCurrentFocus()==null?null:getCurrentFocus().getTag();
        JSONArray snapshot=DeviceStore.devices(this);devices=new LinkedHashMap<>();
        for(int i=0;i<snapshot.length();i++) {
            JSONObject d=snapshot.optJSONObject(i);if(d!=null&&!d.optString("id").isEmpty())devices.put(d.optString("id"),d);
        }
        if(!seeded&&!devices.isEmpty()) { knownIds.addAll(devices.keySet());seeded=true; }
        else if(seeded) for(String id:devices.keySet())if(knownIds.add(id)) {newIds.add(id);newNotice=devices.get(id).optString("title","新设备");}
        model=preferences.layout(snapshot);
        content.removeAllViews();
        if(aboutPage)renderAbout();else if(settingsPage)renderSettings();else if(allPage)renderAll();else renderHome();
        main.post(()->{
            if(isDestroyed())return;
            if(focus!=null) { View next=content.findViewWithTag(focus);if(next!=null)next.requestFocus(); }
            scroll.scrollTo(0,position);
        });
    }
    private List<String> deviceIds() { return new ArrayList<>(devices.keySet()); }
    private String title(String id) { return DevicePresentation.HIDDEN.equals(id)?"隐藏设备":devices.containsKey(id)?devices.get(id).optString("title","设备"):"设备"; }
    private void renderHome() {
        content.addView(ui.text("融合设备卡片",27,ui.text,true));ui.space(content,8);
        content.addView(ui.text("把常用设备放到负一屏和桌面",13,ui.muted,false));ui.space(content,14);
        String error=DeviceStore.error(this);long time=DeviceStore.time(this);
        LinearLayout status=ui.row();status.addView(dot(error.isEmpty()?ui.accent:ui.muted,6));
        String statusText=error.isEmpty()?(time==0?"等待模块连接":"模块已连接 · 已读取 "+devices.size()+" 台设备"):(devices.isEmpty()?error:"暂时无法同步 · 显示上次设备");
        TextView state=ui.text(statusText,12,ui.muted,false);LinearLayout.LayoutParams sp=new LinearLayout.LayoutParams(0,-2,1);sp.leftMargin=ui.dp(6);status.addView(state,sp);content.addView(status);ui.space(content,25);
        LinearLayout section=ui.row();section.addView(ui.text("融合设备中心",17,ui.text,true),new LinearLayout.LayoutParams(0,-2,1));
        section.addView(smallAction("展示设置",AppUi.SETTINGS,()->openSettings(DevicePresentation.HOME)));content.addView(section);ui.space(content,10);
        if(!newNotice.isEmpty()) {
            LinearLayout notice=ui.row();notice.setPadding(ui.dp(13),ui.dp(5),ui.dp(7),ui.dp(5));notice.setBackground(ui.shape(ui.pale,16));
            notice.addView(ui.text("发现新设备："+newNotice,12,ui.text,false),new LinearLayout.LayoutParams(0,-2,1));
            notice.addView(smallAction("查看",-1,this::showAll));content.addView(notice);ui.space(content,12);
        }
        List<String> shown=model.home(deviceIds());
        if(shown.isEmpty())empty(content,devices.isEmpty()?"还没有发现设备\n确认模块已启用后，点击下方刷新。":"首页尚未选择模块\n在展示设置中勾选常用设备，或点击更多设备。由你决定展示数量。");
        else renderGrid(content,shown);
        LinearLayout allLink=smallAction("更多设备 · 全部 "+devices.size()+" 台",AppUi.DOWN,this::showAll);
        allLink.setContentDescription("更多设备");allLink.setGravity(Gravity.CENTER);allLink.setTag("all-devices");content.addView(allLink,new LinearLayout.LayoutParams(-1,ui.dp(44)));
        LinearLayout meta=ui.row();
        String stamp=time==0?"尚未读取设备":(System.currentTimeMillis()-time>300000?"缓存 ":"读取于 ")+new SimpleDateFormat(DateUtils.isToday(time)?"HH:mm":"MM-dd HH:mm",Locale.getDefault()).format(new Date(time));
        meta.addView(ui.text(stamp,11,ui.muted,false),new LinearLayout.LayoutParams(0,-2,1));
        meta.addView(ui.text("首页常驻 "+shown.size()+" 个模块",11,ui.muted,false));content.addView(meta);ui.space(content,18);
        LinearLayout refresh=actionRow(refreshing?"正在刷新…":"刷新设备",AppUi.REFRESH,-1,this::refreshDevices);refresh.setTag("refresh");refresh.setEnabled(!refreshing);content.addView(refresh);
        ui.space(content,10);boolean more=preferences.moreOpen();
        LinearLayout moreRow=actionRow("更多操作",AppUi.MORE,more?AppUi.UP:AppUi.DOWN,()->{preferences.moreOpen(!preferences.moreOpen());render();});moreRow.setTag("more-operations");content.addView(moreRow);
        if(more) {
            LinearLayout group=ui.column();group.setPadding(ui.dp(14),ui.dp(3),ui.dp(14),ui.dp(3));group.setBackground(ui.shape(ui.surface,22));
            group.addView(option("添加到负一屏",AppUi.NEGATIVE,AppUi.RIGHT,this::addNegative));divider(group);
            group.addView(option("添加桌面小组件",AppUi.DESKTOP,AppUi.RIGHT,this::addDesktop));divider(group);
            group.addView(option("打开系统设备中心",AppUi.DEVICES,AppUi.OUT,()->{try{openCenter();}catch(Exception e){toast("系统设备中心暂不可用");}}));
            LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,-2);gp.topMargin=ui.dp(8);content.addView(group,gp);
        }
        ui.space(content,12);LinearLayout about=actionRow("关于",AppUi.HELP,AppUi.RIGHT,this::openAbout);about.setTag("about");content.addView(about);
        if(!feedback.isEmpty()) {ui.space(content,10);content.addView(ui.text(feedback,12,ui.accent,false));}
        ui.space(content,24);TextView footer=ui.text("HyperFusionWidget · "+BuildIdentity.VERSION_NAME,11,ui.muted,false);footer.setGravity(Gravity.CENTER);content.addView(footer);
    }
    private void renderAbout() {
        content.addView(smallAction("返回首页",AppUi.BACK,()->{aboutPage=false;render();scrollToTop();}));ui.space(content,18);
        content.addView(ui.text("关于",27,ui.text,true));ui.space(content,10);
        content.addView(ui.text("融合设备卡片 · "+BuildIdentity.VERSION_NAME,15,ui.muted,false));ui.space(content,24);
        content.addView(compatibilityCard());ui.space(content,14);
        boolean guide=preferences.guideOpen();
        LinearLayout guideRow=actionRow("启用与添加",AppUi.HELP,guide?AppUi.UP:AppUi.DOWN,()->{preferences.guideOpen(!preferences.guideOpen());render();});guideRow.setTag("guide");content.addView(guideRow);
        if(guide) {
            TextView help=ui.text("1. 在 LSPosed 启用「融合设备卡片」，勾选「设备互联 / com.milink.service」与「智能助理 / com.miui.personalassistant」。\n\n2. 更新模块后重新加载目标应用，再返回这里确认连接。\n\n3. 先打开一次负一屏，再通过首页「更多操作」添加卡片。已有卡片不会重复添加。\n\n设备状态由系统提供，App 可见时自动同步。负一屏按卡片高度展示已选择的设备。",12,ui.muted,false);
            help.setLineSpacing(ui.dp(4),1);help.setPadding(ui.dp(18),ui.dp(16),ui.dp(18),ui.dp(16));help.setBackground(ui.shape(ui.surface,22));
            LinearLayout.LayoutParams hp=new LinearLayout.LayoutParams(-1,-2);hp.topMargin=ui.dp(8);content.addView(help,hp);
        }
        ui.space(content,18);LinearLayout tested=ui.column();tested.setPadding(ui.dp(18),ui.dp(16),ui.dp(18),ui.dp(16));tested.setBackground(ui.shape(ui.surface,22));
        tested.addView(ui.text("测试版本说明",16,ui.text,true));ui.space(tested,10);
        TextView versions=ui.text("MiLink 标准模式：\n"+Protocol.BASELINE+"\n\nMiLink 兼容模式：\n17.2.5.0.2607311944\n\n负一屏自动添加：\n智能助理 25.31.06-07091932\n\n其他版本可尝试兼容模式，部分功能可能不可用。此开关仅作用于 MiLink。",12,ui.muted,false);versions.setLineSpacing(ui.dp(4),1);tested.addView(versions);content.addView(tested);
    }
    private View compatibilityCard() {
        LinearLayout card=ui.column();card.setPadding(ui.dp(18),ui.dp(14),ui.dp(18),ui.dp(16));card.setBackground(ui.shape(ui.surface,22));
        LinearLayout row=ui.row();row.setMinimumHeight(ui.dp(44));row.addView(ui.text("MiLink 兼容模式",16,ui.text,true),new LinearLayout.LayoutParams(0,-2,1));
        Switch toggle=new Switch(this);toggle.setContentDescription("MiLink兼容模式");toggle.setTag("milink-compatibility");toggle.setChecked(DeviceStore.compatibilityMode(this));row.addView(toggle);row.setOnClickListener(v->toggle.setChecked(!toggle.isChecked()));card.addView(row);
        ui.space(card,6);String installed;
        try { installed=getPackageManager().getPackageInfo(Protocol.TARGET,0).versionName; } catch(Exception e) { installed="未检测到"; }
        TextView version=ui.text("当前 MiLink："+installed,12,ui.muted,false);card.addView(version);ui.space(card,10);
        TextView hint=ui.text("默认开启。允许其他 MiLink 版本尝试连接；系统接口不同仍可能无法使用。关闭后恢复标准版本检查。",12,ui.muted,false);hint.setLineSpacing(ui.dp(4),1);card.addView(hint);ui.space(card,10);
        String error=DeviceStore.error(this);String state=compatibilityChecking?"正在检查 MiLink 连接…":error.isEmpty()?(DeviceStore.time(this)==0?"等待连接":"模块已连接 · 已读取 "+devices.size()+" 台设备"):error;
        TextView status=ui.text(state,12,error.isEmpty()?ui.accent:ui.muted,false);status.setLineSpacing(ui.dp(4),1);card.addView(status);
        toggle.setOnCheckedChangeListener((button,enabled)->{
            DeviceStore.compatibilityMode(this,enabled);compatibilityChecking=true;render();
            FusionWidget.recheck(this,()->main.post(()->{if(!isDestroyed()){compatibilityChecking=false;render();}}));
        });return card;
    }
    private View deviceCard(JSONObject device,boolean fresh) {
        LinearLayout card=ui.column();card.setTag("device:"+device.optString("id"));card.setPadding(ui.dp(16),ui.dp(16),ui.dp(16),ui.dp(16));card.setMinimumHeight(ui.dp(128));card.setBackground(ui.ripple(ui.surface,22));card.setClickable(true);card.setFocusable(true);
        LinearLayout top=ui.row();FrameLayout badge=new FrameLayout(this);badge.setBackground(ui.shape(ui.pale,13));
        ImageView image=new ImageView(this);image.setImageBitmap(DeviceIcons.make(this,device,ui.accent));image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);image.setScaleType(ImageView.ScaleType.FIT_CENTER);
        badge.addView(image,new FrameLayout.LayoutParams(ui.dp(31),ui.dp(31),Gravity.CENTER));top.addView(badge,new LinearLayout.LayoutParams(ui.dp(39),ui.dp(39)));
        if(fresh) {TextView flag=ui.text("新设备",11,ui.accent,false);flag.setGravity(Gravity.END);top.addView(flag,new LinearLayout.LayoutParams(0,-2,1));}
        card.addView(top);ui.space(card,12);
        TextView name=ui.text(device.optString("title","设备"),15,ui.text,true);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);card.addView(name);
        ui.space(card,4);String state=DeviceText.state(device.optString("deviceType"),device.optInt("state"));String battery=FusionWidget.battery(device.optString("battery"));
        LinearLayout stateRow=ui.row();stateRow.addView(dot((device.optInt("state")&1)!=0?ui.accent:ui.muted,4));
        TextView stateText=ui.text(state+(battery.isEmpty()?"":" · "+battery),12,ui.muted,false);stateText.setMaxLines(1);stateText.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams st=new LinearLayout.LayoutParams(0,-2,1);st.leftMargin=ui.dp(5);stateRow.addView(stateText,st);card.addView(stateRow);
        card.setContentDescription(device.optString("title")+"，"+state+"，打开系统面板");
        card.setOnLongClickListener(v->{manage(device.optString("id"));return true;});
        card.setOnClickListener(v->{
            if(DeviceText.isScene(device.optString("deviceType"))) {try{openCenter();}catch(Exception e){toast("请在系统设备中心操作此场景");}}
            else startActivity(new Intent(this,DeviceActivity.class).putExtra("id",device.optString("id")));
        });return card;
    }
    private void renderAll() {
        LinearLayout bar=ui.row();bar.addView(smallAction("返回首页",AppUi.BACK,()->{allPage=false;hiddenExpanded=false;render();scrollToTop();}),new LinearLayout.LayoutParams(0,ui.dp(44),1));
        bar.addView(smallAction("展示设置",AppUi.SETTINGS,()->openSettings(DevicePresentation.ALL)));content.addView(bar);ui.space(content,18);
        content.addView(ui.text("全部设备",24,ui.text,true));ui.space(content,8);
        TextView hint=ui.text("包含首页常驻设备。长按设备可设置首页展示或隐藏；隐藏设备集中收纳在折叠栏目中。",12,ui.muted,false);hint.setLineSpacing(ui.dp(4),1);content.addView(hint);ui.space(content,20);
        renderGrid(content,model.all(deviceIds()));ui.space(content,16);
        content.addView(ui.text("共 "+devices.size()+" 台设备 · 隐藏 "+model.concealed(deviceIds()).size()+" 台",11,ui.muted,false));
    }
    private void renderGrid(LinearLayout parent,List<String> ids) {
        for(int i=0;i<ids.size();i+=2) {
            LinearLayout row=ui.row();row.setGravity(Gravity.TOP);
            for(int column=0;column<2;column++) {
                int index=i+column;View card;
                if(index>=ids.size())card=new View(this);
                else {String id=ids.get(index);card=DevicePresentation.HIDDEN.equals(id)?hiddenCard():deviceCard(devices.get(id),newIds.contains(id));}
                // An empty View with wrap-content height expands under ScrollView's unbounded measurement.
                LinearLayout.LayoutParams cp=new LinearLayout.LayoutParams(0,index>=ids.size()?0:-2,1);if(column==0)cp.rightMargin=ui.dp(6);else cp.leftMargin=ui.dp(6);row.addView(card,cp);
            }
            LinearLayout.LayoutParams rp=new LinearLayout.LayoutParams(-1,-2);if(i>0)rp.topMargin=ui.dp(12);parent.addView(row,rp);
            if(hiddenExpanded&&(DevicePresentation.HIDDEN.equals(ids.get(i))||(i+1<ids.size()&&DevicePresentation.HIDDEN.equals(ids.get(i+1))))) {
                LinearLayout group=ui.column();group.setPadding(ui.dp(12),ui.dp(14),ui.dp(12),ui.dp(12));group.setBackground(ui.shape(ui.pale,22));
                group.addView(ui.text("隐藏设备",14,ui.text,true));ui.space(group,12);List<String> concealed=model.concealed(deviceIds());
                if(concealed.isEmpty())group.addView(ui.text("暂无隐藏设备。长按设备卡片或在展示设置中管理。",12,ui.muted,false));
                else renderGrid(group,concealed);
                LinearLayout.LayoutParams gp=new LinearLayout.LayoutParams(-1,-2);gp.topMargin=ui.dp(12);parent.addView(group,gp);
            }
        }
    }
    private View hiddenCard() {
        LinearLayout card=ui.column();card.setTag("hidden-module");card.setPadding(ui.dp(16),ui.dp(16),ui.dp(16),ui.dp(16));card.setMinimumHeight(ui.dp(128));card.setBackground(ui.ripple(ui.surface,22));card.setClickable(true);card.setFocusable(true);card.setContentDescription("隐藏设备");
        LinearLayout top=ui.row();top.addView(ui.badge(AppUi.HIDDEN),new LinearLayout.LayoutParams(ui.dp(39),ui.dp(39)));View spacer=new View(this);top.addView(spacer,new LinearLayout.LayoutParams(0,1,1));top.addView(ui.icon(hiddenExpanded?AppUi.UP:AppUi.DOWN,ui.muted,18));card.addView(top);ui.space(card,12);
        card.addView(ui.text("隐藏设备",15,ui.text,true));ui.space(card,4);card.addView(ui.text(model.concealed(deviceIds()).size()+" 台 · "+(hiddenExpanded?"点击收起":"点击展开"),12,ui.muted,false));
        card.setOnClickListener(v->{hiddenExpanded=!hiddenExpanded;render();});card.setOnLongClickListener(v->{manage(DevicePresentation.HIDDEN);return true;});return card;
    }
    private void manage(String id) {
        boolean group=DevicePresentation.HIDDEN.equals(id),hidden=model.hidden.contains(id),pinned=model.pinned.contains(id);
        List<String> choices=new ArrayList<>();List<Runnable> actions=new ArrayList<>();
        if(!hidden) {choices.add(pinned?"取消首页常驻":"在首页展示");actions.add(()->{preferences.pin(id,!pinned);changed();});}
        if(!group) {choices.add(hidden?"取消隐藏":"隐藏设备");actions.add(()->{preferences.hide(id,!hidden);changed();});}
        String sortScope=hidden?DevicePresentation.CONCEALED:settingsPage?settingsTab:allPage?DevicePresentation.ALL:DevicePresentation.HOME;
        choices.add("调整排序");actions.add(()->openSettings(sortScope));
        new AlertDialog.Builder(this).setTitle(title(id)).setItems(choices.toArray(new String[0]),(dialog,which)->actions.get(which).run()).setNegativeButton("取消",null).show();
    }
    private void renderSettings() {
        LinearLayout toolbar=ui.row();toolbar.addView(smallAction("返回",AppUi.BACK,this::leaveSettings),new LinearLayout.LayoutParams(0,ui.dp(44),1));
        TextView title=ui.text("展示设置",17,ui.text,true);title.setGravity(Gravity.CENTER);toolbar.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout done=smallAction("完成",-1,this::leaveSettings);done.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);toolbar.addView(done,new LinearLayout.LayoutParams(0,ui.dp(44),1));content.addView(toolbar);ui.space(content,18);
        LinearLayout tabs=ui.row();String[] keys={DevicePresentation.HOME,DevicePresentation.ALL,DevicePresentation.CONCEALED};String[] labels={"首页展示","全部排序","隐藏设备"};
        for(int i=0;i<keys.length;i++) {
            final String key=keys[i];boolean selected=key.equals(settingsTab);TextView tab=ui.text(labels[i],13,selected?ui.background:ui.text,true);tab.setGravity(Gravity.CENTER);tab.setBackground(ui.ripple(selected?ui.accent:ui.surface,14));tab.setClickable(true);tab.setFocusable(true);tab.setSelected(selected);tab.setTag("tab:"+key);
            tab.setOnClickListener(v->{settingsTab=key;render();scrollToTop();});LinearLayout.LayoutParams tp=new LinearLayout.LayoutParams(0,ui.dp(44),1);if(i>0)tp.leftMargin=ui.dp(8);tabs.addView(tab,tp);
        }
        content.addView(tabs);ui.space(content,18);
        if(DevicePresentation.HOME.equals(settingsTab)) {
            List<String> home=model.home(deviceIds());
            content.addView(ui.text("首页常驻 · "+home.size()+" 个模块",15,ui.text,true));ui.space(content,8);
            note("勾选哪些模块，首页就直接展示哪些模块。数量不限；长按右侧拖动柄排序，也可使用上下箭头。");ui.space(content,12);
            if(home.isEmpty())empty(content,"尚未选择首页模块，请在下方勾选。");else sortList(home,DevicePresentation.HOME,true,true);
            ui.space(content,22);content.addView(ui.text("添加到首页",14,ui.text,true));ui.space(content,12);
            List<String> remaining=model.all(deviceIds());remaining.removeAll(home);
            if(remaining.isEmpty())note("当前所有可展示模块均已加入首页。");else sortList(remaining,DevicePresentation.HOME,true,false);
            ui.space(content,16);note("新设备进入全部设备列表，首页常驻由你勾选。隐藏设备可在右侧页签中管理；隐藏栏目本身也可常驻首页。");
        } else if(DevicePresentation.ALL.equals(settingsTab)) {
            content.addView(ui.text("全部设备顺序",15,ui.text,true));ui.space(content,8);note("此顺序仅影响更多设备列表。长按拖动柄或使用上下箭头排序，点击设备右侧的更多按钮可隐藏设备。");ui.space(content,12);
            sortList(model.all(deviceIds()),DevicePresentation.ALL,false,true);
        } else {
            List<String> hidden=model.concealed(deviceIds());
            content.addView(ui.text("隐藏设备 · "+hidden.size()+" 台",15,ui.text,true));ui.space(content,8);note("隐藏设备默认收纳在折叠栏目中。这里可独立排序；点击右侧更多按钮可取消隐藏，恢复后可重新选择首页常驻。");ui.space(content,12);
            if(hidden.isEmpty())empty(content,"暂无隐藏设备。可在全部排序中点击设备右侧的更多按钮，或长按设备卡片进行隐藏。");else sortList(hidden,DevicePresentation.CONCEALED,false,true);
        }
        ui.space(content,22);note("选择与顺序会自动保存。负一屏沿用首页模块顺序，实际显示数量受卡片高度限制。");
    }
    private void sortList(List<String> ids,String scope,boolean selectable,boolean sortable) {
        LinearLayout list=ui.column();list.setPadding(ui.dp(10),ui.dp(5),ui.dp(10),ui.dp(5));list.setBackground(ui.shape(ui.surface,22));
        for(int index=0;index<ids.size();index++) {
            final String id=ids.get(index);final int position=index;if(index>0)divider(list);
            LinearLayout row=ui.row();row.setPadding(ui.dp(3),ui.dp(6),ui.dp(3),ui.dp(6));row.setMinimumHeight(ui.dp(66));row.setTag("setting:"+scope+":"+id);
            if(selectable) {
                CheckBox check=new CheckBox(this);check.setChecked(model.pinned.contains(id));check.setContentDescription("首页展示"+title(id));check.setTag("pin:"+id);check.setButtonTintList(new ColorStateList(new int[][]{new int[]{android.R.attr.state_checked},new int[]{}},new int[]{ui.accent,ui.muted}));row.addView(check,new LinearLayout.LayoutParams(ui.dp(34),ui.dp(44)));
                check.setOnCheckedChangeListener((button,checked)->{preferences.pin(id,checked);changed();});row.setOnClickListener(v->check.setChecked(!check.isChecked()));
            }
            if(!selectable||!sortable) {
                FrameLayout badge=new FrameLayout(this);badge.setBackground(ui.shape(ui.pale,9));
                if(DevicePresentation.HIDDEN.equals(id))badge.addView(new AppUi.Icon(this,AppUi.HIDDEN,ui.accent),new FrameLayout.LayoutParams(ui.dp(21),ui.dp(21),Gravity.CENTER));
                else {ImageView image=new ImageView(this);image.setImageBitmap(DeviceIcons.make(this,devices.get(id),ui.accent));image.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);badge.addView(image,new FrameLayout.LayoutParams(ui.dp(24),ui.dp(24),Gravity.CENTER));}
                row.addView(badge,new LinearLayout.LayoutParams(ui.dp(29),ui.dp(29)));
            }
            TextView name=ui.text(title(id),13,ui.text,false);name.setMaxLines(2);name.setEllipsize(TextUtils.TruncateAt.END);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-2,1);np.leftMargin=ui.dp(8);row.addView(name,np);
            if(!selectable)row.addView(sortButton("管理"+title(id),AppUi.MORE,()->manage(id)));
            if(sortable) {
                View up=sortButton("上移"+title(id),AppUi.UP,()->{if(position>0)move(scope,id,ids.get(position-1));});up.setEnabled(index>0);up.setAlpha(index>0?1f:0.25f);row.addView(up);
                View down=sortButton("下移"+title(id),AppUi.DOWN,()->{if(position<ids.size()-1)move(scope,id,ids.get(position+1));});down.setEnabled(index<ids.size()-1);down.setAlpha(index<ids.size()-1?1f:0.25f);row.addView(down);
                View handle=sortButton("拖动排序"+title(id),AppUi.DRAG,()->toast("长按拖动柄，上下拖动调整顺序"));
                handle.setOnLongClickListener(v->{SortDrag item=new SortDrag(scope,id);boolean started=row.startDragAndDrop(ClipData.newPlainText("module-sort",""),new View.DragShadowBuilder(row),item,0);if(started)dragging=true;return started;});row.addView(handle);
                row.setOnDragListener((v,event)->{
                    Object local=event.getLocalState();if(!(local instanceof SortDrag)||!scope.equals(((SortDrag)local).scope))return false;
                    SortDrag item=(SortDrag)local;
                    switch(event.getAction()) {
                        case DragEvent.ACTION_DRAG_STARTED:return true;
                        case DragEvent.ACTION_DRAG_ENTERED:row.setBackground(ui.shape(ui.pale,12));return true;
                        case DragEvent.ACTION_DRAG_LOCATION:
                            int[] origin=new int[2],viewport=new int[2];row.getLocationOnScreen(origin);scroll.getLocationOnScreen(viewport);
                            float y=origin[1]+event.getY()-viewport[1];
                            if(y<ui.dp(80))scroll.smoothScrollBy(0,-ui.dp(18));else if(y>scroll.getHeight()-ui.dp(80))scroll.smoothScrollBy(0,ui.dp(18));return true;
                        case DragEvent.ACTION_DRAG_EXITED:row.setBackgroundColor(Color.TRANSPARENT);return true;
                        case DragEvent.ACTION_DROP:preferences.move(scope,item.id,id);return true;
                        case DragEvent.ACTION_DRAG_ENDED:main.post(this::finishSort);return true;
                        default:return true;
                    }
                });
            }
            list.addView(row,new LinearLayout.LayoutParams(-1,-2));
        }
        content.addView(list);
    }
    private static final class SortDrag {final String scope,id;SortDrag(String scope,String id) {this.scope=scope;this.id=id;}}
    private View sortButton(String label,int icon,Runnable click) {
        FrameLayout button=new FrameLayout(this);button.setBackground(ui.ripple(Color.TRANSPARENT,10));button.setContentDescription(label);button.setClickable(true);button.setFocusable(true);button.addView(new AppUi.Icon(this,icon,ui.accent),new FrameLayout.LayoutParams(ui.dp(17),ui.dp(17),Gravity.CENTER));button.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(32),ui.dp(44)));button.setOnClickListener(v->click.run());return button;
    }
    private void finishSort() {if(!dragging)return;dragging=false;changed();}
    private void move(String scope,String source,String target) {preferences.move(scope,source,target);changed();}
    private void changed() {render();FusionWidget.updateAll(this);}
    private void note(String value) {TextView text=ui.text(value,12,ui.muted,false);text.setLineSpacing(ui.dp(4),1);content.addView(text);}
    private void empty(LinearLayout parent,String value) {TextView text=ui.text(value,13,ui.muted,false);text.setPadding(ui.dp(18),ui.dp(20),ui.dp(18),ui.dp(20));text.setLineSpacing(ui.dp(5),1);text.setBackground(ui.shape(ui.surface,22));parent.addView(text);}
    private LinearLayout smallAction(String label,int icon,Runnable click) {
        LinearLayout row=ui.row();row.setPadding(ui.dp(4),ui.dp(7),ui.dp(4),ui.dp(7));row.setMinimumHeight(ui.dp(40));row.setBackground(ui.ripple(Color.TRANSPARENT,12));row.setClickable(true);row.setFocusable(true);row.setContentDescription(label);
        if(icon>=0) {row.addView(ui.icon(icon,ui.accent,17));View space=new View(this);row.addView(space,new LinearLayout.LayoutParams(ui.dp(5),1));}
        row.addView(ui.text(label,12,ui.accent,true));row.setOnClickListener(v->click.run());return row;
    }
    private LinearLayout actionRow(String label,int icon,int end,Runnable click) {
        LinearLayout row=ui.row();row.setMinimumHeight(ui.dp(54));row.setPadding(ui.dp(15),ui.dp(10),ui.dp(15),ui.dp(10));row.setBackground(ui.ripple(ui.surface,22));row.setClickable(true);row.setFocusable(true);row.setContentDescription(label);
        row.addView(ui.badge(icon),new LinearLayout.LayoutParams(ui.dp(30),ui.dp(30)));TextView name=ui.text(label,14,ui.text,true);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-2,1);np.leftMargin=ui.dp(11);row.addView(name,np);if(end>=0)row.addView(ui.icon(end,ui.muted,18));row.setOnClickListener(v->click.run());return row;
    }
    private LinearLayout option(String label,int icon,int end,Runnable click) {
        LinearLayout row=ui.row();row.setMinimumHeight(ui.dp(50));row.setBackground(ui.ripple(ui.surface,10));row.setClickable(true);row.setFocusable(true);row.setContentDescription(label);row.addView(ui.icon(icon,ui.accent,18));TextView name=ui.text(label,13,ui.text,false);LinearLayout.LayoutParams np=new LinearLayout.LayoutParams(0,-2,1);np.leftMargin=ui.dp(10);row.addView(name,np);row.addView(ui.icon(end,ui.muted,17));row.setOnClickListener(v->click.run());return row;
    }
    private View dot(int color,int size) {View dot=new View(this);dot.setBackground(ui.shape(color,99));dot.setLayoutParams(new LinearLayout.LayoutParams(ui.dp(size),ui.dp(size)));return dot;}
    private void divider(LinearLayout parent) {View line=new View(this);line.setBackgroundColor(ui.line);parent.addView(line,new LinearLayout.LayoutParams(-1,ui.dp(1)));}
    private void scrollToTop() { main.post(()->scroll.scrollTo(0,0)); }
    private void leaveSettings() { settingsPage=false;allPage=settingsReturnAll;hiddenExpanded=false;render();scrollToTop(); }
    private void refreshDevices() {
        if(refreshing)return;
        boolean accepted=FusionWidget.request(this,true,()->main.post(()->{refreshing=false;feedback=DeviceStore.error(this).isEmpty()?"已同步系统设备列表":"同步暂未完成，请在「关于」中查看连接提示。";render();}));
        if(accepted) {refreshing=true;feedback="";render();} else toast("正在同步或刚刚刷新过，请稍后再试");
    }
    private void addNegative() {
        try {
            addProof=PendingIntent.getActivity(this,3,new Intent(this,MainActivity.class).setAction(Protocol.APP+".ADD_REQUEST"),PendingIntent.FLAG_CANCEL_CURRENT|PendingIntent.FLAG_ONE_SHOT|PendingIntent.FLAG_IMMUTABLE);
            startActivity(new Intent().setComponent(new ComponentName("com.miui.personalassistant","com.miui.personalassistant.picker.business.home.pages.PickerHomeActivity")).putExtra("openSource",1).putExtra("fusionWidgetAdd",true).putExtra("fusionWidgetCaller",addProof));overridePendingTransition(0,0);
        } catch(Exception e) {android.util.Log.e("FusionWidget","Picker launch failed",e);toast("请检查智能助理作用域和版本是否适配");}
    }
    private void addDesktop() {
        AppWidgetManager manager=AppWidgetManager.getInstance(this);
        if(manager.isRequestPinAppWidgetSupported()) {if(!manager.requestPinAppWidget(new ComponentName(this,FusionWidget.class),null,null))toast("请在桌面编辑模式中添加小组件");}
        else toast("请长按桌面 → 添加小组件 → 安卓小组件 → 融合设备卡片");
    }
    private void openCenter() {startActivity(new Intent().setComponent(new ComponentName(Protocol.TARGET,"com.miui.circulate.world.CirculateWorldActivity")));}
    private void toast(String value) {Toast.makeText(this,value,Toast.LENGTH_LONG).show();}
    static void notifyChanged() {MainActivity a=visible.get();if(a!=null)a.main.post(a::render);}
    @Override protected void onResume() {
        super.onResume();foreground=true;visible=new WeakReference<>(this);
        if(addProof!=null) {addProof.cancel();addProof=null;}
        render();FusionWidget.request(this,false,()->{});main.removeCallbacks(poll);main.postDelayed(poll,15000);RefreshJob.schedule(this);
    }
    @Override protected void onPause() {foreground=false;dragging=false;main.removeCallbacks(poll);if(visible.get()==this)visible.clear();super.onPause();}
    @Override protected void onDestroy() {main.removeCallbacksAndMessages(null);super.onDestroy();}
}
