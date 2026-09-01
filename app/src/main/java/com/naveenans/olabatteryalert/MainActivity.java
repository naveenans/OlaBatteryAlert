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
import java.util.Date;

public class MainActivity extends Activity {
    private static final int HOST_ID = 41041, PICK = 701, CONFIG = 702;
    private BatteryWidgetHost host;
    private LinearLayout widgetBox;
    private TextView batteryBig, status, limitText;
    private SeekBar limitBar;
    private int pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

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
    @Override protected void onStart(){ super.onStart(); host.startListening(); refreshStatus(); }
    @Override protected void onStop(){ host.stopListening(); super.onStop(); }

    private int dp(int v){ return Math.round(v * getResources().getDisplayMetrics().density); }
    private TextView text(String s,int sp){ TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(Color.WHITE); v.setPadding(dp(2),dp(6),dp(2),dp(6)); return v; }
    private GradientDrawable bg(int c,float r){ GradientDrawable g=new GradientDrawable(); g.setColor(c); g.setCornerRadius(dp((int)r)); return g; }
    private Button button(String s,int color){ Button b=new Button(this); b.setText(s); b.setTextColor(Color.WHITE); b.setTextSize(14); b.setAllCaps(false); b.setBackground(bg(color,18)); b.setPadding(dp(12),dp(9),dp(12),dp(9)); return b; }

    private void buildUi(){
        ScrollView sv=new ScrollView(this);
        GradientDrawable page=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{Color.rgb(18,28,52),Color.rgb(46,18,66),Color.rgb(10,58,66)});
        sv.setBackground(page);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(dp(18),dp(24),dp(18),dp(30)); sv.addView(root);

        TextView title=text("⚡ Ola Battery Alert",28); title.setTypeface(null,1); root.addView(title);
        TextView sub=text("Lightweight background monitor • Widget OCR fallback • Alarm alert",14); sub.setTextColor(Color.rgb(190,210,235)); root.addView(sub);

        LinearLayout hero=new LinearLayout(this); hero.setOrientation(LinearLayout.VERTICAL); hero.setPadding(dp(18),dp(16),dp(18),dp(16)); hero.setBackground(bg(Color.argb(190,14,20,35),24)); LinearLayout.LayoutParams card=new LinearLayout.LayoutParams(-1,-2); card.setMargins(0,dp(14),0,dp(10)); root.addView(hero,card);
        TextView small=text("LATEST BATTERY",12); small.setTextColor(Color.rgb(125,230,210)); hero.addView(small);
        batteryBig=text("—",46); batteryBig.setTypeface(null,1); hero.addView(batteryBig);
        status=text("Waiting for Ola widget data…",13); status.setTextColor(Color.rgb(205,215,230)); hero.addView(status);

        LinearLayout limitCard=new LinearLayout(this); limitCard.setOrientation(LinearLayout.VERTICAL); limitCard.setPadding(dp(16),dp(12),dp(16),dp(12)); limitCard.setBackground(bg(Color.argb(185,37,36,82),22)); root.addView(limitCard,card);
        limitText=text("",19); limitText.setTypeface(null,1); limitCard.addView(limitText);
        limitBar=new SeekBar(this); limitBar.setMax(40); int saved=getSharedPreferences("prefs",MODE_PRIVATE).getInt("limit",80); limitBar.setProgress(saved-60); limitText.setText("Charge alarm at "+saved+"%"); limitCard.addView(limitBar);
        limitBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar s,int p,boolean f){ int x=60+p; limitText.setText("Charge alarm at "+x+"%"); getSharedPreferences("prefs",MODE_PRIVATE).edit().putInt("limit",x).apply(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} });

        TextView wt=text("Ola widget source",16); wt.setTypeface(null,1); root.addView(wt);
        widgetBox=new LinearLayout(this); widgetBox.setOrientation(LinearLayout.VERTICAL); widgetBox.setPadding(0,dp(8),0,dp(8)); root.addView(widgetBox);

        Button add=button("🔗 Select / Change Ola Widget",Color.rgb(77,76,214)); add.setOnClickListener(v->pickWidget()); root.addView(add,buttonLp());
        Button scan=button("🔍 Scan Battery Now",Color.rgb(0,145,132)); scan.setOnClickListener(v->scanDisplayedWidget()); root.addView(scan,buttonLp());
        Button monitor=button("▶ Start Background Monitor",Color.rgb(18,116,73)); monitor.setOnClickListener(v->startMonitor(true)); root.addView(monitor,buttonLp());
        Button stop=button("■ Stop Background Monitor",Color.rgb(155,60,72)); stop.setOnClickListener(v->{ getSharedPreferences("prefs",MODE_PRIVATE).edit().putBoolean("monitor",false).apply(); stopService(new Intent(this,WidgetMonitorService.class)); Toast.makeText(this,"Background monitor stopped",Toast.LENGTH_SHORT).show(); refreshStatus(); }); root.addView(stop,buttonLp());
        Button access=button("🔔 Enable Ola Notification Fallback",Color.rgb(145,82,180)); access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))); root.addView(access,buttonLp());
        Button test=button("🚨 Test Burglar Alarm",Color.rgb(210,92,38)); test.setOnClickListener(v->{ int l=getSharedPreferences("prefs",MODE_PRIVATE).getInt("limit",80); AlertEngine.sendLimitAlert(this,l,l,"test"); }); root.addView(test,buttonLp());

        TextView note=text("Tip: the Ola widget shown on your phone can render the percentage as an image instead of normal text. v1.1 first reads widget text, then uses lightweight Play Services OCR on the rendered widget image. Keep the foreground monitor enabled while charging.",12); note.setTextColor(Color.rgb(185,200,220)); note.setPadding(0,dp(12),0,0); root.addView(note);
        setContentView(sv);
    }

    private LinearLayout.LayoutParams buttonLp(){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(50)); p.setMargins(0,dp(6),0,dp(6)); return p; }

    private void requestNotifications(){ if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},900); }
    private void pickWidget(){ pendingWidgetId=host.allocateAppWidgetId(); Intent i=new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK); i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pendingWidgetId); startActivityForResult(i,PICK); }

    @Override protected void onActivityResult(int req,int res,Intent data){
        super.onActivityResult(req,res,data);
        int id=data!=null?data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pendingWidgetId):pendingWidgetId;
        if(res!=RESULT_OK){ if(id!=AppWidgetManager.INVALID_APPWIDGET_ID) host.deleteAppWidgetId(id); pendingWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID; return; }
        if(req==PICK) configureOrFinish(id); else if(req==CONFIG) finishWidget(id);
    }

    private void configureOrFinish(int id){ AppWidgetProviderInfo info=AppWidgetManager.getInstance(this).getAppWidgetInfo(id); if(info!=null&&info.configure!=null){ pendingWidgetId=id; Intent c=new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE); c.setComponent(info.configure); c.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id); startActivityForResult(c,CONFIG); } else finishWidget(id); }
    private void finishWidget(int id){ if(id==AppWidgetManager.INVALID_APPWIDGET_ID)return; AppWidgetProviderInfo info=AppWidgetManager.getInstance(this).getAppWidgetInfo(id); String pkg=info!=null&&info.provider!=null?info.provider.getPackageName():""; getSharedPreferences("prefs",MODE_PRIVATE).edit().putInt("widget_id",id).putString("widget_pkg",pkg).putBoolean("monitor",true).apply(); pendingWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID; showBoundWidget(); startMonitor(false); refreshStatus(); }

    private void showBoundWidget(){
        widgetBox.removeAllViews();
        int id=getSharedPreferences("prefs",MODE_PRIVATE).getInt("widget_id",AppWidgetManager.INVALID_APPWIDGET_ID);
        if(id==AppWidgetManager.INVALID_APPWIDGET_ID){ widgetBox.addView(text("No Ola widget selected yet.",14)); return; }
        AppWidgetProviderInfo info=AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
        if(info==null){ widgetBox.addView(text("Saved widget is unavailable. Select it again.",14)); return; }
        BatteryWidgetHostView v=(BatteryWidgetHostView)host.createView(this,id,info);
        v.setListener(p->{ if(p!=null){ AlertEngine.process(this,p,"Ola widget text"); refreshStatus(); } else scanOcr(v); });
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,Math.max(dp(170),dp(info.minHeight))); lp.setMargins(0,dp(6),0,dp(6)); widgetBox.addView(v,lp);
        v.postDelayed(()->scanWidgetView(v),1000);
    }

    private void scanDisplayedWidget(){ if(widgetBox.getChildCount()==0){ Toast.makeText(this,"Select Ola widget first",Toast.LENGTH_SHORT).show(); return; } View v=widgetBox.getChildAt(0); if(v instanceof BatteryWidgetHostView) scanWidgetView((BatteryWidgetHostView)v); else Toast.makeText(this,"Select Ola widget first",Toast.LENGTH_SHORT).show(); }
    private void scanWidgetView(BatteryWidgetHostView v){ Integer p=BatteryParser.fromView(v); if(p!=null){ AlertEngine.process(this,p,"Ola widget text"); refreshStatus(); Toast.makeText(this,"Battery found: "+p+"%",Toast.LENGTH_SHORT).show(); } else scanOcr(v); }
    private void scanOcr(BatteryWidgetHostView v){ WidgetOcrReader.scan(v,(pct,raw)->{ if(pct!=null){ AlertEngine.process(this,pct,"Ola widget OCR"); Toast.makeText(this,"OCR battery found: "+pct+"%",Toast.LENGTH_SHORT).show(); } else Toast.makeText(this,"Percentage not detected yet",Toast.LENGTH_SHORT).show(); refreshStatus(); }); }

    private void startMonitor(boolean toast){ getSharedPreferences("prefs",MODE_PRIVATE).edit().putBoolean("monitor",true).apply(); try{ Intent i=new Intent(this,WidgetMonitorService.class); if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i); if(toast) Toast.makeText(this,"Background monitor running",Toast.LENGTH_SHORT).show(); }catch(Exception e){ if(toast) Toast.makeText(this,"Could not start monitor: "+e.getMessage(),Toast.LENGTH_LONG).show(); } refreshStatus(); }

    private void refreshStatus(){ SharedPreferences p=getSharedPreferences("prefs",MODE_PRIVATE); int pct=p.getInt("last_pct",-1); long t=p.getLong("last_update",0); String src=p.getString("last_source","none"); boolean mon=p.getBoolean("monitor",false); String when=t==0?"Never":DateFormat.getDateTimeInstance().format(new Date(t)); batteryBig.setText(pct<0?"—":pct+"%"); status.setText("Monitor: "+(mon?"RUNNING":"STOPPED")+"\nLast update: "+when+"\nSource: "+src); }
}
