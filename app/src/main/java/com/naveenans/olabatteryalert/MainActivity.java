package com.naveenans.olabatteryalert;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.DateFormat;
import java.util.Date;

public class MainActivity extends Activity {
    private static final int HOST_ID = 41041, PICK = 701, BIND = 702;
    private BatteryWidgetHost host;
    private LinearLayout widgetBox;
    private TextView status;
    private SeekBar limitBar;
    private TextView limitText;
    private int pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b); AlertEngine.ensureChannels(this); requestNotifications();
        host = new BatteryWidgetHost(this, HOST_ID); buildUi(); showBoundWidget(); refreshStatus();
    }
    @Override protected void onStart(){ super.onStart(); host.startListening(); }
    @Override protected void onStop(){ host.stopListening(); super.onStop(); }

    private TextView text(String s, int sp) { TextView v=new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(Color.WHITE); v.setPadding(0,10,0,10); return v; }
    private Button button(String s){ Button b=new Button(this); b.setText(s); return b; }
    private void buildUi(){
        ScrollView sv=new ScrollView(this); sv.setBackgroundColor(Color.rgb(16,20,24)); LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(36,42,36,48); sv.addView(root);
        TextView title=text("Ola Battery Alert",28); title.setTypeface(null,1); root.addView(title); root.addView(text("Reads the battery percentage shown by a user-selected Ola Electric widget and alerts when your charging limit is reached.",15));
        limitText=text("",20); root.addView(limitText); limitBar=new SeekBar(this); limitBar.setMax(40); int saved=getSharedPreferences("prefs",MODE_PRIVATE).getInt("limit",80); limitBar.setProgress(saved-60); limitText.setText("Charge alert: "+saved+"%"); root.addView(limitBar);
        limitBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){ public void onProgressChanged(SeekBar s,int p,boolean f){ int x=60+p; limitText.setText("Charge alert: "+x+"%"); getSharedPreferences("prefs",MODE_PRIVATE).edit().putInt("limit",x).apply(); } public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){} });
        widgetBox=new LinearLayout(this); widgetBox.setOrientation(LinearLayout.VERTICAL); widgetBox.setPadding(0,16,0,16); root.addView(widgetBox);
        Button add=button("Select / Change Ola Widget"); add.setOnClickListener(v->pickWidget()); root.addView(add);
        Button access=button("Enable Ola Notification Fallback"); access.setOnClickListener(v->startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))); root.addView(access);
        Button monitor=button("Start Background Monitor"); monitor.setOnClickListener(v->startMonitor()); root.addView(monitor);
        Button test=button("Test Limit Alert"); test.setOnClickListener(v->{ int l=getSharedPreferences("prefs",MODE_PRIVATE).getInt("limit",80); AlertEngine.sendLimitAlert(this,l,l,"test"); }); root.addView(test);
        status=text("",14); root.addView(status); setContentView(sv);
    }
    private void requestNotifications(){ if(Build.VERSION.SDK_INT>=33 && checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"},900); }
    private void pickWidget(){ pendingWidgetId=host.allocateAppWidgetId(); Intent i=new Intent(AppWidgetManager.ACTION_APPWIDGET_PICK); i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pendingWidgetId); startActivityForResult(i,PICK); }
    @Override protected void onActivityResult(int req,int res,Intent data){ super.onActivityResult(req,res,data); if(res!=RESULT_OK){ if(pendingWidgetId!=AppWidgetManager.INVALID_APPWIDGET_ID) host.deleteAppWidgetId(pendingWidgetId); pendingWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID; return; } int id=data!=null?data.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,pendingWidgetId):pendingWidgetId; if(req==PICK) configureOrBind(id); else if(req==BIND) finishWidget(id); }
    private void configureOrBind(int id){ AppWidgetManager m=AppWidgetManager.getInstance(this); AppWidgetProviderInfo info=m.getAppWidgetInfo(id); if(info==null){ finishWidget(id); return; } if(info.configure!=null){ pendingWidgetId=id; Intent c=new Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE); c.setComponent(info.configure); c.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id); startActivityForResult(c,BIND); } else finishWidget(id); }
    private void finishWidget(int id){ if(id==AppWidgetManager.INVALID_APPWIDGET_ID) return; AppWidgetProviderInfo info=AppWidgetManager.getInstance(this).getAppWidgetInfo(id); String pkg=info!=null&&info.provider!=null?info.provider.getPackageName():""; getSharedPreferences("prefs",MODE_PRIVATE).edit().putInt("widget_id",id).putString("widget_pkg",pkg).apply(); pendingWidgetId=AppWidgetManager.INVALID_APPWIDGET_ID; showBoundWidget(); refreshStatus(); }
    private void showBoundWidget(){ widgetBox.removeAllViews(); int id=getSharedPreferences("prefs",MODE_PRIVATE).getInt("widget_id",AppWidgetManager.INVALID_APPWIDGET_ID); if(id==AppWidgetManager.INVALID_APPWIDGET_ID){ widgetBox.addView(text("No Ola widget selected yet.",16)); return; } AppWidgetProviderInfo info=AppWidgetManager.getInstance(this).getAppWidgetInfo(id); if(info==null){ widgetBox.addView(text("Saved widget is no longer available. Select it again.",16)); return; } BatteryWidgetHostView v=(BatteryWidgetHostView)host.createView(this,id,info); v.setListener(p->{ if(p!=null){ AlertEngine.process(this,p,"Ola widget"); refreshStatus(); }}); widgetBox.addView(v,new LinearLayout.LayoutParams(-1,Math.max(180,info.minHeight))); v.postDelayed(()->{ Integer p=BatteryParser.fromView(v); if(p!=null){ AlertEngine.process(this,p,"Ola widget"); refreshStatus(); }},800); }
    private void startMonitor(){ getSharedPreferences("prefs",MODE_PRIVATE).edit().putBoolean("monitor",true).apply(); try{ startForegroundService(new Intent(this,WidgetMonitorService.class)); Toast.makeText(this,"Background monitor started",Toast.LENGTH_SHORT).show(); }catch(Exception e){ Toast.makeText(this,"Could not start monitor: "+e.getMessage(),Toast.LENGTH_LONG).show(); } }
    private void refreshStatus(){ android.content.SharedPreferences p=getSharedPreferences("prefs",MODE_PRIVATE); int pct=p.getInt("last_pct",-1); long t=p.getLong("last_update",0); String src=p.getString("last_source","none"); String pkg=p.getString("widget_pkg","not selected"); String when=t==0?"Never":DateFormat.getDateTimeInstance().format(new Date(t)); status.setText("Last battery: "+(pct<0?"—":pct+"%")+"\nLast update: "+when+"\nSource: "+src+"\nWidget package: "+pkg+"\n\nTip: choose the Ola Electric widget that visibly shows the scooter battery %. Keep the background monitor enabled while charging."); }
}
