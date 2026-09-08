package com.naveenans.olabatteryalert;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.*;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int PICK = 700, BIND = 701, CONFIG = 702;
    private BatteryWidgetHost host;
    private LinearLayout widgetBox;
    private TextView batteryBig, status, limitText;
    private SeekBar limitBar;
    private int pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private AppWidgetProviderInfo pendingInfo;
    private final Handler live = new Handler(Looper.getMainLooper());
    private final Runnable liveScan = new Runnable() {
        @Override public void run() {
            WidgetRefresh.ping(MainActivity.this);
            live.postDelayed(() -> {
                scanDisplayedWidget(false);
                refreshStatus();
            }, 450);
            live.postDelayed(this, WidgetRefresh.DEFAULT_MS);
        }
    };
    private final BroadcastReceiver batteryRx = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { refreshStatus(); }
    };
    private final SharedPreferences.OnSharedPreferenceChangeListener prefListen = (prefs, key) -> {
        if (key == null) return;
        if (key.equals("last_pct") || key.equals("last_charging") || key.equals("charging_known") || key.equals("last_update") || key.equals("last_source") || key.equals("monitor")) {
            runOnUiThread(MainActivity.this::refreshStatus);
        }
    };

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        AlertEngine.ensureChannels(this);
        requestNotifications();
        host = HostHolder.host(this);
        getSharedPreferences("prefs", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(prefListen);
        buildUi();
        refreshStatus();
    }

    @Override protected void onDestroy() {
        try { getSharedPreferences("prefs", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(prefListen); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override protected void onStart() {
        super.onStart();
        try { HostHolder.host(this); } catch (Exception ignored) {}
        if (widgetBox == null || widgetBox.getChildCount() == 0) showBoundWidget();
        else {
            View v = widgetBox.getChildAt(0);
            if (v instanceof BatteryWidgetHostView) HostHolder.setLiveView((BatteryWidgetHostView) v);
        }
        refreshStatus();
        WidgetRefresh.ensureMonitor(this);
        live.removeCallbacks(liveScan);
        live.post(liveScan);
        requestOverlayQuiet();
        requestUnrestrictedBattery();
        try {
            IntentFilter f = new IntentFilter(WidgetRefresh.ACTION_UPDATED);
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(batteryRx, f, Context.RECEIVER_NOT_EXPORTED);
            else registerReceiver(batteryRx, f);
        } catch (Exception ignored) {}
    }

    @Override protected void onStop() {
        live.removeCallbacks(liveScan);
        try { unregisterReceiver(batteryRx); } catch (Exception ignored) {}
        HostHolder.setLiveView(null);
        super.onStop();
    }

    private void requestOverlayQuiet() {
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return;
    }

    private void requestOverlay() {
        try {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, android.net.Uri.parse("package:" + getPackageName()));
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Open Settings → Display over other apps", Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }
    private TextView text(String s,int sp){ TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(Color.WHITE); v.setPadding(dp(2),dp(6),dp(2),dp(6)); return v; }
    private GradientDrawable bg(int c,float r){ GradientDrawable g=new GradientDrawable(); g.setColor(c); g.setCornerRadius(dp((int)r)); return g; }
    private Button button(String s,int color){ Button b=new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setAllCaps(false); b.setBackground(bg(color,18)); b.setPadding(dp(12),dp(9),dp(12),dp(9)); return b; }
    private LinearLayout.LayoutParams buttonLp(){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50)); p.setMargins(0,dp(6),0,dp(6)); return p; }

    private void buildUi(){
        ScrollView sv=new ScrollView(this);
        sv.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, themeBg()));
        sv.setFillViewport(true);
        sv.setSmoothScrollingEnabled(true);
        sv.setOverScrollMode(View.OVER_SCROLL_ALWAYS);
        sv.setClipToPadding(false);
        if (Build.VERSION.SDK_INT >= 21) sv.setNestedScrollingEnabled(true);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(24),dp(18),dp(30)); sv.addView(root);

        TextView title=text("⚡ OLA Battery Alert",28); title.setTypeface(null,1); root.addView(title);
        TextView sub=text("Always-on monitor · live charge state · spatial OCR fallback",14); sub.setTextColor(Color.rgb(202,220,245)); root.addView(sub);

        LinearLayout hero=new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setPadding(dp(18),dp(16),dp(18),dp(16)); hero.setBackground(bg(Color.argb(205,11,18,34),24)); LinearLayout.LayoutParams card=new LinearLayout.LayoutParams(-1,-2); card.setMargins(0,dp(14),0,dp(10)); root.addView(hero,card);
        TextView small=text("LIVE BATTERY",12); small.setTextColor(accent()); hero.addView(small);
        batteryBig=text("—",46); batteryBig.setTypeface(null,1); hero.addView(batteryBig);
        status=text("Select the OLA widget to begin.",13); status.setTextColor(Color.rgb(210,220,235)); hero.addView(status);

        LinearLayout limitCard=new LinearLayout(this); limitCard.setOrientation(LinearLayout.VERTICAL); limitCard.setPadding(dp(16),dp(12),dp(16),dp(12)); limitCard.setBackground(bg(Color.argb(190,43,35,90),22)); root.addView(limitCard,card);
        limitText=text("",19); limitText.setTypeface(null,1); limitCard.addView(limitText);
        limitBar=new SeekBar(this); limitBar.setMax(40); int saved=getSharedPreferences("prefs",MODE_PRIVATE).getInt("limit",80); limitBar.setProgress(saved-60); limitText.setText("Charge alarm at "+saved+"%"); limitCard.addView(limitBar);
        limitBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar s,int p,boolean f){ int x=60+p; limitText.setText("Charge alarm at "+x+"%"); getSharedPreferences("prefs",MODE_PRIVATE).edit().putInt("limit",x).apply(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} });

        LinearLayout refreshCard=new LinearLayout(this); refreshCard.setOrientation(LinearLayout.VERTICAL); refreshCard.setPadding(dp(16),dp(12),dp(16),dp(12)); refreshCard.setBackground(bg(Color.argb(190,11,18,34),22)); root.addView(refreshCard,card);
        TextView refreshTitle=text("Background fetch · every 5 minutes",19); refreshTitle.setTypeface(null,1); refreshCard.addView(refreshTitle);
        TextView refreshHint=text("Always running. Exact alarm + foreground service keep reading the Ola widget even when this screen is closed.",13); refreshHint.setTextColor(Color.rgb(186,202,220)); refreshCard.addView(refreshHint);

        LinearLayout themeCard=new LinearLayout(this); themeCard.setOrientation(LinearLayout.VERTICAL); themeCard.setPadding(dp(16),dp(12),dp(16),dp(12)); themeCard.setBackground(bg(Color.argb(190,11,18,34),22)); root.addView(themeCard,card);
        TextView themeTitle=text("Theme",19); themeTitle.setTypeface(null,1); themeCard.addView(themeTitle);
        LinearLayout trow=new LinearLayout(this); trow.setOrientation(LinearLayout.HORIZONTAL);
        String cur=WidgetRefresh.theme(this);
        String[] themes=new String[]{"neon","volt","ice"};
        for(String name: themes){
            Button b=button(name, name.equals(cur)? accent() : Color.rgb(40,52,70));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,dp(44),1);
            lp.setMargins(dp(2),dp(6),dp(2),0);
            final String pick=name;
            b.setOnClickListener(v->{
                getSharedPreferences("prefs",MODE_PRIVATE).edit().putString("theme", pick).apply();
                buildUi();
                showBoundWidget();
                refreshStatus();
            });
            trow.addView(b, lp);
        }
        themeCard.addView(trow);

        TextView wt=text("OLA widget preview",16); wt.setTypeface(null,1); root.addView(wt);
        widgetBox=new LinearLayout(this); widgetBox.setOrientation(LinearLayout.VERTICAL); widgetBox.setPadding(0,dp(8),0,dp(8)); root.addView(widgetBox);

        Button nativePick=button("📱 Select Widget — Android Picker",Color.rgb(72,72,220)); nativePick.setOnClickListener(v->openNativePicker()); root.addView(nativePick,buttonLp());
        Button advanced=button("☰ Advanced Widget List",Color.rgb(88,63,171)); advanced.setOnClickListener(v->chooseProviderList()); root.addView(advanced,buttonLp());
        Button reset=button("↻ Reset Widget Connection",Color.rgb(0,128,166)); reset.setOnClickListener(v->resetWidget()); root.addView(reset,buttonLp());
        Button scan=button("🔍 Scan Battery Now",Color.rgb(0,156,132)); scan.setOnClickListener(v->scanDisplayedWidget()); root.addView(scan,buttonLp());
        Button access=button("🔔 Enable OLA Notification Fallback",Color.rgb(151,82,187)); access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))); root.addView(access,buttonLp());
        Button overlay=button("⧉ Allow overlay (needed for background OCR)",Color.rgb(40,90,150)); overlay.setOnClickListener(v->requestOverlay()); root.addView(overlay,buttonLp());
        Button test=button("🚨 Test Burglar Alarm",Color.rgb(220,94,37)); test.setOnClickListener(v->{ int l=getSharedPreferences("prefs",MODE_PRIVATE).getInt("limit",80); AlertEngine.sendLimitAlert(this,l,l,"test"); }); root.addView(test,buttonLp());

        TextView note=text("v2.1: OCR finds a number beside the % symbol and reads the nearby green lightning icon. Battery is green while charging and blue otherwise.",12); note.setTextColor(Color.rgb(192,207,229)); note.setPadding(0,dp(12),0,0); root.addView(note);
        setContentView(sv);
    }

    private int[] themeBg(){
        String t=WidgetRefresh.theme(this);
        if("volt".equals(t)) return new int[]{Color.rgb(8,22,10),Color.rgb(36,62,8),Color.rgb(10,40,28)};
        if("ice".equals(t)) return new int[]{Color.rgb(8,14,24),Color.rgb(16,36,64),Color.rgb(0,48,72)};
        return new int[]{Color.rgb(13,35,73),Color.rgb(72,27,117),Color.rgb(0,91,102)};
    }
    private int accent(){
        String t=WidgetRefresh.theme(this);
        if("volt".equals(t)) return Color.rgb(198,245,74);
        if("ice".equals(t)) return Color.rgb(168,216,255);
        return Color.rgb(46,230,255);
    }

    private void requestNotifications(){ if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},900); }

    private void openNativePicker(){
        cleanupPending();
        pendingWidgetId=host.allocateAppWidgetId();
        Intent i=new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pendingWidgetId);
        try { startActivityForResult(i,PICK); }
        catch(Exception e){ Toast.makeText(this,"Android widget picker unavailable: "+e.getMessage(),Toast.LENGTH_LONG).show(); cleanupPending(); }
    }

    private void chooseProviderList(){
        List<AppWidgetProviderInfo> providers=AppWidgetManager.getInstance(this).getInstalledProviders();
        if(providers==null||providers.isEmpty()){ Toast.makeText(this,"Android reported no widget providers",Toast.LENGTH_LONG).show(); return; }
        final List<AppWidgetProviderInfo> choices=new ArrayList<>(providers);
        PackageManager pm=getPackageManager();
        Collections.sort(choices,(a,b)->{
            int oa=isOla(a,pm)?1:0, ob=isOla(b,pm)?1:0;
            if(oa!=ob)return Integer.compare(ob,oa);
            return providerLabel(a,pm).compareToIgnoreCase(providerLabel(b,pm));
        });
        String[] labels=new String[choices.size()];
        for(int x=0;x<choices.size();x++){
            AppWidgetProviderInfo p=choices.get(x);
            labels[x]=(isOla(p,pm)?"★ OLA  ":"")+providerLabel(p,pm)+"\n"+(p.provider==null?"":p.provider.flattenToShortString());
        }
        new AlertDialog.Builder(this).setTitle("Widgets reported by Android: "+choices.size()).setItems(labels,(d,w)->beginManualBind(choices.get(w))).setNegativeButton("Cancel",null).show();
    }

    private String providerLabel(AppWidgetProviderInfo info,PackageManager pm){ try{ CharSequence c=info.loadLabel(pm); if(c!=null&&c.length()>0)return c.toString(); }catch(Exception ignored){} return info.provider==null?"Widget":info.provider.getClassName(); }
    private boolean isOla(AppWidgetProviderInfo i,PackageManager pm){ String s=((i.provider==null?"":i.provider.flattenToShortString())+" "+providerLabel(i,pm)).toLowerCase(Locale.US); return s.contains("ola")||s.contains("electric"); }

    private void beginManualBind(AppWidgetProviderInfo info){
        cleanupPending(); pendingInfo=info; pendingWidgetId=host.allocateAppWidgetId();
        AppWidgetManager mgr=AppWidgetManager.getInstance(this); Bundle opts=widgetOptions();
        boolean ok=false; try{ ok=mgr.bindAppWidgetIdIfAllowed(pendingWidgetId,info.provider,opts); }catch(Exception ignored){}
        if(ok){ configureOrFinish(info); return; }
        Intent bind=new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
        bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pendingWidgetId);
        bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,info.provider);
        bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS,opts);
        try{ startActivityForResult(bind,BIND); }catch(Exception e){ Toast.makeText(this,"Widget permission screen failed",Toast.LENGTH_LONG).show(); cleanupPending(); }
    }

    private Bundle widgetOptions(){
        DisplayMetrics dm=getResources().getDisplayMetrics();
        int screenDp=(int)(dm.widthPixels/dm.density);
        Bundle o=new Bundle();
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY,AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,Math.max(250,screenDp-48));
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,Math.max(280,screenDp-24));
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,140);
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,320);
        return o;
    }

    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);
        if(req==PICK){
            if(res!=RESULT_OK){ cleanupPending(); return; }
            int id=data!=null?data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pendingWidgetId):pendingWidgetId;
            pendingWidgetId=id;
            pendingInfo=AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
            if(pendingInfo==null){ Toast.makeText(this,"Widget was selected but Android did not return provider info",Toast.LENGTH_LONG).show(); cleanupPending(); return; }
            configureOrFinish(pendingInfo);
        } else if(req==BIND){
            if(res==RESULT_OK&&pendingInfo!=null) configureOrFinish(pendingInfo); else cleanupPending();
        } else if(req==CONFIG){
            if(res==RESULT_OK) finishWidget(pendingWidgetId,pendingInfo); else cleanupPending();
        }
    }

    private void configureOrFinish(AppWidgetProviderInfo info){
        if(info!=null&&info.configure!=null){
            Intent c=new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            c.setComponent(info.configure); c.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pendingWidgetId);
            try{ startActivityForResult(c,CONFIG); return; }catch(Exception ignored){}
        }
        finishWidget(pendingWidgetId,info);
    }

    private void finishWidget(int id,AppWidgetProviderInfo info){
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID||info==null){ cleanupPending(); return; }
        try{ AppWidgetManager.getInstance(this).updateAppWidgetOptions(id,widgetOptions()); }catch(Exception ignored){}
        String pkg=info.provider==null?"":info.provider.getPackageName();
        getSharedPreferences("prefs",MODE_PRIVATE).edit().putInt("widget_id",id).putString("widget_pkg",pkg).putBoolean("monitor",true).apply();
        pendingWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID; pendingInfo=null;
        showBoundWidget(); WidgetRefresh.ensureMonitor(this); refreshStatus();
        Toast.makeText(this,"Widget connected: "+pkg,Toast.LENGTH_SHORT).show();
    }

    private void resetWidget(){
        int old=getSharedPreferences("prefs",MODE_PRIVATE).getInt("widget_id",AppWidgetManager.INVALID_APPWIDGET_ID);
        if(old!=AppWidgetManager.INVALID_APPWIDGET_ID)try{host.deleteAppWidgetId(old);}catch(Exception ignored){}
        getSharedPreferences("prefs",MODE_PRIVATE).edit().remove("widget_id").remove("widget_pkg").remove("last_pct").remove("last_charging").remove("charging_known").remove("last_update").remove("last_source").apply();
        showBoundWidget(); refreshStatus(); Toast.makeText(this,"Widget connection reset",Toast.LENGTH_SHORT).show();
    }

    private void cleanupPending(){ if(pendingWidgetId!=AppWidgetManager.INVALID_APPWIDGET_ID)try{host.deleteAppWidgetId(pendingWidgetId);}catch(Exception ignored){} pendingWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID; pendingInfo=null; }

    private void showBoundWidget(){
        if(widgetBox==null)return; widgetBox.removeAllViews();
        int id=getSharedPreferences("prefs",MODE_PRIVATE).getInt("widget_id",AppWidgetManager.INVALID_APPWIDGET_ID);
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID){ widgetBox.addView(text("No widget connected. Use Android Picker above.",14)); return; }
        AppWidgetManager mgr=AppWidgetManager.getInstance(this); AppWidgetProviderInfo info=mgr.getAppWidgetInfo(id);
        if(info==null){ widgetBox.addView(text("Stored widget instance is no longer bound. Tap Reset Widget Connection and select it again.",14)); return; }
        try{ mgr.updateAppWidgetOptions(id,widgetOptions()); }catch(Exception ignored){}
        try{
            BatteryWidgetHostView v=(BatteryWidgetHostView)host.createView(this,id,info);
            v.setListener(p->{ if(p!=null){ AlertEngine.process(this,p,"widget-text"); refreshStatus(); } });
            HostHolder.setLiveView(v);
            int w=getResources().getDisplayMetrics().widthPixels-dp(36);
            int h=dp(210);
            v.setMinimumWidth(w); v.setMinimumHeight(h);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,h); lp.setMargins(0,dp(6),0,dp(6)); widgetBox.addView(v,lp);
            v.post(() -> { v.requestLayout(); v.invalidate(); scanWidgetView(v); });
            v.postDelayed(()->scanWidgetView(v),1500);
            v.postDelayed(()->scanWidgetView(v),4000);
        }catch(Exception e){ widgetBox.addView(text("Widget host error: "+e.getClass().getSimpleName()+": "+e.getMessage(),13)); }
    }

    private void scanDisplayedWidget(){ scanDisplayedWidget(true); }
    private void scanDisplayedWidget(boolean toast){
        if(widgetBox==null || widgetBox.getChildCount()==0){ if(toast) Toast.makeText(this,"No rendered widget to scan",Toast.LENGTH_SHORT).show(); return; }
        View v=widgetBox.getChildAt(0);
        if(v instanceof BatteryWidgetHostView) {
            try { v.requestLayout(); v.invalidate(); } catch (Exception ignored) {}
            scanWidgetView((BatteryWidgetHostView)v);
        }
        else if(toast) Toast.makeText(this,"No rendered widget to scan",Toast.LENGTH_SHORT).show();
        SharedPreferences p=getSharedPreferences("prefs",MODE_PRIVATE);
        long t=p.getLong("last_update",0);
        int id=p.getInt("widget_id", AppWidgetManager.INVALID_APPWIDGET_ID);
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID && t > 0 && System.currentTimeMillis() - t > WidgetRefresh.intervalMs(this) * 3L) {
            showBoundWidget();
        }
    }
    private void scanWidgetView(BatteryWidgetHostView v){
        HostHolder.setLiveView(v);
        ScanEngine.scan(v, new ScanEngine.Callback() {
            @Override public void onHit(int pct, Boolean charging, String source, float confidence, String raw) {
                AlertEngine.process(MainActivity.this, pct, charging, source + " · " + Math.round(confidence * 100) + "%");
                refreshStatus();
            }
            @Override public void onMiss(String reason) { refreshStatus(); }
        });
    }

    private void requestUnrestrictedBattery(){
        if (Build.VERSION.SDK_INT < 23) return;
        SharedPreferences p = getSharedPreferences("prefs", MODE_PRIVATE);
        if (p.getBoolean("asked_battery", false)) return;
        try {
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm != null && pm.isIgnoringBatteryOptimizations(getPackageName())) return;
            p.edit().putBoolean("asked_battery", true).apply();
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, android.net.Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {}
    }

    private void refreshStatus(){
        SharedPreferences p=getSharedPreferences("prefs",MODE_PRIVATE);
        int pct=p.getInt("last_pct",-1);
        long t=p.getLong("last_update",0);
        String src=p.getString("last_source","none");
        boolean mon=p.getBoolean("monitor",false);
        boolean chargingKnown=p.getBoolean("charging_known",false);
        boolean charging=p.getBoolean("last_charging",false);
        String when;
        if (t==0) when="waiting for first scan";
        else {
            long ago = Math.max(0, (System.currentTimeMillis()-t)/1000);
            when = ago < 5 ? "just now" : ago + "s ago";
        }
        if(batteryBig!=null){
            batteryBig.setText(pct<0?"—":(charging?"⚡ ":"")+pct+"%");
            batteryBig.setTextColor(chargingKnown && charging ? Color.rgb(34,197,94) : Color.rgb(46,139,255));
        }
        String chargeLabel = !chargingKnown ? "detecting charge state" : charging ? "CHARGING" : "NOT CHARGING";
        if(status!=null)status.setText("Live "+(pct<0?"—":pct+"%")+"  ·  "+chargeLabel+"\nMonitor: ALWAYS ON  ·  "+when+"\nSource: "+src);
    }
}
