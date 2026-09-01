package com.naveenans.olabatteryalert;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.DateFormat;
import java.util.*;

public class MainActivity extends Activity {
    private static final int HOST_ID = 41041, BIND = 701, CONFIG = 702;
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
        showBoundWidget();
        refreshStatus();
        if (getSharedPreferences("prefs",MODE_PRIVATE).getBoolean("monitor",false)) startMonitor(false);
    }
    @Override protected void onStart(){ super.onStart(); host.startListening(); showBoundWidget(); refreshStatus(); }
    @Override protected void onStop(){ host.stopListening(); super.onStop(); }

    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }
    private TextView text(String s,int sp){ TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(Color.WHITE); v.setPadding(dp(2),dp(6),dp(2),dp(6)); return v; }
    private GradientDrawable bg(int c,float r){ GradientDrawable g=new GradientDrawable(); g.setColor(c); g.setCornerRadius(dp((int)r)); return g; }
    private Button button(String s,int color){ Button b=new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setAllCaps(false); b.setBackground(bg(color,18)); b.setPadding(dp(12),dp(9),dp(12),dp(9)); return b; }

    private void buildUi(){
        ScrollView sv=new ScrollView(this);
        GradientDrawable page=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(16,38,74),Color.rgb(78,26,115),Color.rgb(0,93,102)});
        sv.setBackground(page);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(24),dp(18),dp(30)); sv.addView(root);

        TextView title=text("⚡ OLA Battery Alert",28); title.setTypeface(null,1); root.addView(title);
        TextView sub=text("Full widget list • Background monitoring • OCR fallback • Alarm alert",14); sub.setTextColor(Color.rgb(202,220,245)); root.addView(sub);

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

        Button add=button("🔗 Select Widget (Full List)",Color.rgb(81,73,220)); add.setOnClickListener(v->chooseProvider()); root.addView(add,buttonLp());
        Button reload=button("↻ Reload Widget",Color.rgb(0,132,168)); reload.setOnClickListener(v->{ showBoundWidget(); scanDisplayedWidget(); }); root.addView(reload,buttonLp());
        Button scan=button("🔍 Scan Battery Now",Color.rgb(0,156,132)); scan.setOnClickListener(v->scanDisplayedWidget()); root.addView(scan,buttonLp());
        Button monitor=button("▶ Start Background Monitor",Color.rgb(20,132,76)); monitor.setOnClickListener(v->startMonitor(true)); root.addView(monitor,buttonLp());
        Button stop=button("■ Stop Background Monitor",Color.rgb(163,58,78)); stop.setOnClickListener(v->{ getSharedPreferences("prefs",MODE_PRIVATE).edit().putBoolean("monitor",false).apply(); stopService(new Intent(this,WidgetMonitorService.class)); Toast.makeText(this,"Background monitor stopped",Toast.LENGTH_SHORT).show(); refreshStatus(); }); root.addView(stop,buttonLp());
        Button access=button("🔔 Enable OLA Notification Fallback",Color.rgb(151,82,187)); access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))); root.addView(access,buttonLp());
        Button test=button("🚨 Test Burglar Alarm",Color.rgb(220,94,37)); test.setOnClickListener(v->{ int l=getSharedPreferences("prefs",MODE_PRIVATE).getInt("limit",80); AlertEngine.sendLimitAlert(this,l,l,"test"); }); root.addView(test,buttonLp());

        TextView note=text("v1.3 fix: the selector now shows every widget provider Android reports for the current phone/profile. OLA entries are placed first and clearly marked, but nothing is hidden. The total widget count is shown in the picker title.",12); note.setTextColor(Color.rgb(192,207,229)); note.setPadding(0,dp(12),0,0); root.addView(note);
        setContentView(sv);
    }

    private LinearLayout.LayoutParams buttonLp(){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50)); p.setMargins(0,dp(6),0,dp(6)); return p; }
    private void requestNotifications(){ if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},900); }

    private void chooseProvider(){
        List<AppWidgetProviderInfo> installed=AppWidgetManager.getInstance(this).getInstalledProviders();
        if(installed==null || installed.isEmpty()){
            Toast.makeText(this,"Android reported no widget providers",Toast.LENGTH_LONG).show();
            return;
        }

        PackageManager pm=getPackageManager();
        final List<AppWidgetProviderInfo> choices=new ArrayList<>(installed);
        Collections.sort(choices,(a,b)->{
            int sa=score(a), sb=score(b);
            if(sa!=sb) return Integer.compare(sb,sa);
            String la=providerLabel(a,pm).toLowerCase(Locale.US);
            String lb=providerLabel(b,pm).toLowerCase(Locale.US);
            return la.compareTo(lb);
        });

        final List<String> labels=new ArrayList<>();
        int olaCount=0;
        for(AppWidgetProviderInfo info: choices){
            String pkg=info.provider!=null?info.provider.getPackageName():"unknown.package";
            String name=providerLabel(info,pm);
            boolean ola=isOla(info,pm);
            if(ola) olaCount++;
            labels.add((ola?"★ OLA  ":"")+name+"\n"+pkg);
        }

        final int detectedOla=olaCount;
        AlertDialog dlg=new AlertDialog.Builder(this)
                .setTitle("All widgets: "+choices.size()+"  •  OLA detected: "+detectedOla)
                .setItems(labels.toArray(new String[0]),(d,which)->beginBind(choices.get(which)))
                .setNeutralButton("System Widget Picker",(d,w)->openSystemWidgetPicker())
                .setNegativeButton("Cancel",null)
                .create();
        dlg.setOnShowListener(x->Toast.makeText(this,"Showing full widget list: "+choices.size(),Toast.LENGTH_SHORT).show());
        dlg.show();
    }

    private String providerLabel(AppWidgetProviderInfo info,PackageManager pm){
        try{
            CharSequence label=info.loadLabel(pm);
            if(label!=null && label.length()>0) return label.toString();
        }catch(Exception ignored){}
        if(info.provider!=null) return info.provider.getClassName();
        return "Widget";
    }

    private boolean isOla(AppWidgetProviderInfo info,PackageManager pm){
        String pkg=info.provider==null?"":info.provider.getPackageName();
        String label=providerLabel(info,pm);
        String s=(pkg+" "+label).toLowerCase(Locale.US);
        return s.contains("ola") || s.contains("electric");
    }

    private void openSystemWidgetPicker(){
        pendingWidgetId=host.allocateAppWidgetId();
        Intent i=new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pendingWidgetId);
        try{
            startActivityForResult(i,BIND);
        }catch(Exception e){
            Toast.makeText(this,"System widget picker is not available on this phone",Toast.LENGTH_LONG).show();
            cleanupPending();
        }
    }

    private int score(AppWidgetProviderInfo i){
        try{return isOla(i,getPackageManager())?100:0;}catch(Exception e){return 0;}
    }

    private void beginBind(AppWidgetProviderInfo info){
        pendingInfo=info;
        pendingWidgetId=host.allocateAppWidgetId();
        AppWidgetManager mgr=AppWidgetManager.getInstance(this);
        Bundle options=widgetOptions(info);
        boolean bound=false;
        try { bound=mgr.bindAppWidgetIdIfAllowed(pendingWidgetId,info.provider,options); } catch(Exception ignored){}
        if(bound){ configureOrFinish(info); return; }
        Intent bind=new Intent(AppWidgetManager.ACTION_APPWIDGET_BIND);
        bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pendingWidgetId);
        bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER,info.provider);
        bind.putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS,options);
        try { startActivityForResult(bind,BIND); }
        catch(Exception e){ Toast.makeText(this,"Android could not open widget permission: "+e.getMessage(),Toast.LENGTH_LONG).show(); cleanupPending(); }
    }

    private Bundle widgetOptions(AppWidgetProviderInfo info){
        Bundle o=new Bundle();
        int minW=Math.max(320,info.minWidth), minH=Math.max(160,info.minHeight);
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,minW);
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,minH);
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,720);
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,420);
        return o;
    }

    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);
        if(req==BIND){
            if(res==RESULT_OK){
                if(pendingInfo!=null){ configureOrFinish(pendingInfo); return; }
                int id=data!=null?data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pendingWidgetId):pendingWidgetId;
                AppWidgetProviderInfo picked=AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
                pendingWidgetId=id;
                pendingInfo=picked;
                if(picked!=null) configureOrFinish(picked); else cleanupPending();
            } else cleanupPending();
        } else if(req==CONFIG){
            if(res==RESULT_OK) finishWidget(pendingWidgetId,pendingInfo); else cleanupPending();
        }
    }

    private void configureOrFinish(AppWidgetProviderInfo info){
        if(info!=null && info.configure!=null){
            Intent c=new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
            c.setComponent(info.configure); c.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pendingWidgetId);
            try { startActivityForResult(c,CONFIG); } catch(Exception e){ finishWidget(pendingWidgetId,info); }
        } else finishWidget(pendingWidgetId,info);
    }

    private void finishWidget(int id,AppWidgetProviderInfo info){
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID || info==null){ cleanupPending(); return; }
        AppWidgetManager.getInstance(this).updateAppWidgetOptions(id,widgetOptions(info));
        String pkg=info.provider!=null?info.provider.getPackageName():"";
        getSharedPreferences("prefs",MODE_PRIVATE).edit().putInt("widget_id",id).putString("widget_pkg",pkg).putBoolean("monitor",true).apply();
        pendingWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID; pendingInfo=null;
        showBoundWidget(); startMonitor(false); refreshStatus();
        Toast.makeText(this,"Widget connected: "+pkg,Toast.LENGTH_SHORT).show();
    }

    private void cleanupPending(){
        if(pendingWidgetId!=AppWidgetManager.INVALID_APPWIDGET_ID){ try{host.deleteAppWidgetId(pendingWidgetId);}catch(Exception ignored){} }
        pendingWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID; pendingInfo=null;
    }

    private void showBoundWidget(){
        if(widgetBox==null) return;
        widgetBox.removeAllViews();
        int id=getSharedPreferences("prefs",MODE_PRIVATE).getInt("widget_id",AppWidgetManager.INVALID_APPWIDGET_ID);
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID){ widgetBox.addView(text("No OLA widget selected yet.",14)); return; }
        AppWidgetManager mgr=AppWidgetManager.getInstance(this);
        AppWidgetProviderInfo info=mgr.getAppWidgetInfo(id);
        if(info==null){
            getSharedPreferences("prefs",MODE_PRIVATE).edit().remove("widget_id").apply();
            widgetBox.addView(text("Saved widget binding expired. Tap Select Widget again.",14)); return;
        }
        mgr.updateAppWidgetOptions(id,widgetOptions(info));
        try{
            BatteryWidgetHostView v=(BatteryWidgetHostView)host.createView(this,id,info);
            v.setPadding(0,0,0,0);
            v.setListener(p->{ if(p!=null){ AlertEngine.process(this,p,"OLA widget text"); refreshStatus(); } else scanOcr(v); });
            int targetH=Math.max(dp(190),dp(Math.max(info.minHeight,180)));
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,targetH); lp.setMargins(0,dp(6),0,dp(6)); widgetBox.addView(v,lp);
            v.postDelayed(()->{ v.requestLayout(); scanWidgetView(v); },1800);
        }catch(Exception e){
            widgetBox.addView(text("Widget could not render: "+e.getMessage()+"\nTap Reload Widget or select it again.",13));
        }
    }

    private void scanDisplayedWidget(){ if(widgetBox.getChildCount()==0){ Toast.makeText(this,"Select OLA widget first",Toast.LENGTH_SHORT).show(); return; } View v=widgetBox.getChildAt(0); if(v instanceof BatteryWidgetHostView) scanWidgetView((BatteryWidgetHostView)v); else Toast.makeText(this,"Select OLA widget first",Toast.LENGTH_SHORT).show(); }
    private void scanWidgetView(BatteryWidgetHostView v){ Integer p=BatteryParser.fromView(v); if(p!=null){ AlertEngine.process(this,p,"OLA widget text"); refreshStatus(); Toast.makeText(this,"Battery found: "+p+"%",Toast.LENGTH_SHORT).show(); } else scanOcr(v); }
    private void scanOcr(BatteryWidgetHostView v){ WidgetOcrReader.scan(v,(pct,raw)->{ if(pct!=null){ AlertEngine.process(this,pct,"OLA widget OCR"); Toast.makeText(this,"OCR battery found: "+pct+"%",Toast.LENGTH_SHORT).show(); } else Toast.makeText(this,"Percentage not detected yet",Toast.LENGTH_SHORT).show(); refreshStatus(); }); }

    private void startMonitor(boolean toast){ getSharedPreferences("prefs",MODE_PRIVATE).edit().putBoolean("monitor",true).apply(); try{ Intent i=new Intent(this,WidgetMonitorService.class); if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i); if(toast) Toast.makeText(this,"Background monitor running",Toast.LENGTH_SHORT).show(); }catch(Exception e){ if(toast) Toast.makeText(this,"Could not start monitor: "+e.getMessage(),Toast.LENGTH_LONG).show(); } refreshStatus(); }

    private void refreshStatus(){ SharedPreferences p=getSharedPreferences("prefs",MODE_PRIVATE); int pct=p.getInt("last_pct",-1); long t=p.getLong("last_update",0); String src=p.getString("last_source","none"); boolean mon=p.getBoolean("monitor",false); String when=t==0?"Never":DateFormat.getDateTimeInstance().format(new Date(t)); if(batteryBig!=null)batteryBig.setText(pct<0?"—":pct+"%"); if(status!=null)status.setText("Monitor: "+(mon?"RUNNING":"STOPPED")+"\nLast update: "+when+"\nSource: "+src); }
}
