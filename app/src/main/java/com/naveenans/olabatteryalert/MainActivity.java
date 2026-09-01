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
    private static final int HOST_ID = 41041;
    private static final int PICK = 700, BIND = 701, CONFIG = 702;
    private BatteryWidgetHost host;
    private LinearLayout widgetBox;
    private TextView batteryBig, status, limitText;
    private SeekBar limitBar;
    private int pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private AppWidgetProviderInfo pendingInfo;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        AlertEngine.ensureChannels(this);
        requestNotifications();
        host = new BatteryWidgetHost(this, HOST_ID);
        buildUi();
        refreshStatus();
    }

    @Override protected void onStart() {
        super.onStart();
        try { host.startListening(); } catch (Exception ignored) {}
        showBoundWidget();
        refreshStatus();
        if (getSharedPreferences("prefs", MODE_PRIVATE).getBoolean("monitor", false)) startMonitor(false);
    }

    @Override protected void onStop() {
        try { host.stopListening(); } catch (Exception ignored) {}
        super.onStop();
    }

    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }
    private TextView text(String s,int sp){ TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(Color.WHITE); v.setPadding(dp(2),dp(6),dp(2),dp(6)); return v; }
    private GradientDrawable bg(int c,float r){ GradientDrawable g=new GradientDrawable(); g.setColor(c); g.setCornerRadius(dp((int)r)); return g; }
    private Button button(String s,int color){ Button b=new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setAllCaps(false); b.setBackground(bg(color,18)); b.setPadding(dp(12),dp(9),dp(12),dp(9)); return b; }
    private LinearLayout.LayoutParams buttonLp(){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50)); p.setMargins(0,dp(6),0,dp(6)); return p; }

    private void buildUi(){
        ScrollView sv=new ScrollView(this);
        sv.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(13,35,73),Color.rgb(72,27,117),Color.rgb(0,91,102)}));
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(24),dp(18),dp(30)); sv.addView(root);

        TextView title=text("⚡ OLA Battery Alert",28); title.setTypeface(null,1); root.addView(title);
        TextView sub=text("Native Android widget picker • Reliable host binding • OCR fallback",14); sub.setTextColor(Color.rgb(202,220,245)); root.addView(sub);

        LinearLayout hero=new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setPadding(dp(18),dp(16),dp(18),dp(16)); hero.setBackground(bg(Color.argb(205,11,18,34),24)); LinearLayout.LayoutParams card=new LinearLayout.LayoutParams(-1,-2); card.setMargins(0,dp(14),0,dp(10)); root.addView(hero,card);
        TextView small=text("LIVE BATTERY",12); small.setTextColor(Color.rgb(78,235,181)); hero.addView(small);
        batteryBig=text("—",46); batteryBig.setTypeface(null,1); hero.addView(batteryBig);
        status=text("Select the OLA widget to begin.",13); status.setTextColor(Color.rgb(210,220,235)); hero.addView(status);

        LinearLayout limitCard=new LinearLayout(this); limitCard.setOrientation(LinearLayout.VERTICAL); limitCard.setPadding(dp(16),dp(12),dp(16),dp(12)); limitCard.setBackground(bg(Color.argb(190,43,35,90),22)); root.addView(limitCard,card);
        limitText=text("",19); limitText.setTypeface(null,1); limitCard.addView(limitText);
        limitBar=new SeekBar(this); limitBar.setMax(40); int saved=getSharedPreferences("prefs",MODE_PRIVATE).getInt("limit",80); limitBar.setProgress(saved-60); limitText.setText("Charge alarm at "+saved+"%"); limitCard.addView(limitBar);
        limitBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar s,int p,boolean f){ int x=60+p; limitText.setText("Charge alarm at "+x+"%"); getSharedPreferences("prefs",MODE_PRIVATE).edit().putInt("limit",x).apply(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} });

        TextView wt=text("OLA widget preview",16); wt.setTypeface(null,1); root.addView(wt);
        widgetBox=new LinearLayout(this); widgetBox.setOrientation(LinearLayout.VERTICAL); widgetBox.setPadding(0,dp(8),0,dp(8)); root.addView(widgetBox);

        Button nativePick=button("📱 Select Widget — Android Picker",Color.rgb(72,72,220)); nativePick.setOnClickListener(v->openNativePicker()); root.addView(nativePick,buttonLp());
        Button advanced=button("☰ Advanced Widget List",Color.rgb(88,63,171)); advanced.setOnClickListener(v->chooseProviderList()); root.addView(advanced,buttonLp());
        Button reset=button("↻ Reset Widget Connection",Color.rgb(0,128,166)); reset.setOnClickListener(v->resetWidget()); root.addView(reset,buttonLp());
        Button scan=button("🔍 Scan Battery Now",Color.rgb(0,156,132)); scan.setOnClickListener(v->scanDisplayedWidget()); root.addView(scan,buttonLp());
        Button monitor=button("▶ Start Background Monitor",Color.rgb(20,132,76)); monitor.setOnClickListener(v->startMonitor(true)); root.addView(monitor,buttonLp());
        Button stop=button("■ Stop Background Monitor",Color.rgb(163,58,78)); stop.setOnClickListener(v->{ getSharedPreferences("prefs",MODE_PRIVATE).edit().putBoolean("monitor",false).apply(); stopService(new Intent(this,WidgetMonitorService.class)); refreshStatus(); }); root.addView(stop,buttonLp());
        Button access=button("🔔 Enable OLA Notification Fallback",Color.rgb(151,82,187)); access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))); root.addView(access,buttonLp());
        Button test=button("🚨 Test Burglar Alarm",Color.rgb(220,94,37)); test.setOnClickListener(v->{ int l=getSharedPreferences("prefs",MODE_PRIVATE).getInt("limit",80); AlertEngine.sendLimitAlert(this,l,l,"test"); }); root.addView(test,buttonLp());

        TextView note=text("v1.4: the primary flow now uses Android's own widget picker. This lets Android bind the exact widget instance first, then the app hosts that already-bound instance. Home-screen host category and screen-aware size options are also supplied before rendering.",12); note.setTextColor(Color.rgb(192,207,229)); note.setPadding(0,dp(12),0,0); root.addView(note);
        setContentView(sv);
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
        showBoundWidget(); startMonitor(false); refreshStatus();
        Toast.makeText(this,"Widget connected: "+pkg,Toast.LENGTH_SHORT).show();
    }

    private void resetWidget(){
        int old=getSharedPreferences("prefs",MODE_PRIVATE).getInt("widget_id",AppWidgetManager.INVALID_APPWIDGET_ID);
        if(old!=AppWidgetManager.INVALID_APPWIDGET_ID)try{host.deleteAppWidgetId(old);}catch(Exception ignored){}
        getSharedPreferences("prefs",MODE_PRIVATE).edit().remove("widget_id").remove("widget_pkg").remove("last_pct").remove("last_update").remove("last_source").apply();
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
            v.setListener(p->{ if(p!=null){ AlertEngine.process(this,p,"OLA widget text"); refreshStatus(); } });
            int w=getResources().getDisplayMetrics().widthPixels-dp(36);
            int h=dp(210);
            v.setMinimumWidth(w); v.setMinimumHeight(h);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,h); lp.setMargins(0,dp(6),0,dp(6)); widgetBox.addView(v,lp);
            v.postDelayed(()->{ v.requestLayout(); v.invalidate(); scanWidgetView(v); },2500);
            v.postDelayed(()->scanWidgetView(v),6000);
        }catch(Exception e){ widgetBox.addView(text("Widget host error: "+e.getClass().getSimpleName()+": "+e.getMessage(),13)); }
    }

    private void scanDisplayedWidget(){ if(widgetBox.getChildCount()==0)return; View v=widgetBox.getChildAt(0); if(v instanceof BatteryWidgetHostView)scanWidgetView((BatteryWidgetHostView)v); else Toast.makeText(this,"No rendered widget to scan",Toast.LENGTH_SHORT).show(); }
    private void scanWidgetView(BatteryWidgetHostView v){ Integer p=BatteryParser.fromView(v); if(p!=null){ AlertEngine.process(this,p,"OLA widget text"); refreshStatus(); } else scanOcr(v); }
    private void scanOcr(BatteryWidgetHostView v){ WidgetOcrReader.scan(v,(pct,raw)->{ if(pct!=null)AlertEngine.process(this,pct,"OLA widget OCR"); refreshStatus(); }); }

    private void startMonitor(boolean toast){ getSharedPreferences("prefs",MODE_PRIVATE).edit().putBoolean("monitor",true).apply(); try{ Intent i=new Intent(this,WidgetMonitorService.class); if(Build.VERSION.SDK_INT>=26)startForegroundService(i); else startService(i); if(toast)Toast.makeText(this,"Background monitor running",Toast.LENGTH_SHORT).show(); }catch(Exception e){ if(toast)Toast.makeText(this,"Could not start monitor: "+e.getMessage(),Toast.LENGTH_LONG).show(); } refreshStatus(); }

    private void refreshStatus(){ SharedPreferences p=getSharedPreferences("prefs",MODE_PRIVATE); int pct=p.getInt("last_pct",-1); long t=p.getLong("last_update",0); String src=p.getString("last_source","none"); boolean mon=p.getBoolean("monitor",false); String when=t==0?"Never":DateFormat.getDateTimeInstance().format(new Date(t)); if(batteryBig!=null)batteryBig.setText(pct<0?"—":pct+"%"); if(status!=null)status.setText("Monitor: "+(mon?"RUNNING":"STOPPED")+"\nLast update: "+when+"\nSource: "+src); }
}
