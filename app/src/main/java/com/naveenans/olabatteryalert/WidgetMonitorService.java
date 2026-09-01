package com.naveenans.olabatteryalert;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.IBinder;

public class WidgetMonitorService extends Service {
    private static final int HOST_ID = 41041;
    private BatteryWidgetHost host;
    private BatteryWidgetHostView hostedView;
    @Override public void onCreate() {
        super.onCreate();
        AlertEngine.ensureChannels(this);
        Notification n = new Notification.Builder(this, AlertEngine.CHANNEL_MONITOR)
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging).setContentTitle("Ola Battery Alert active")
                .setContentText("Monitoring the selected Ola widget for battery updates").setOngoing(true).build();
        startForeground(4104, n);
        int id = getSharedPreferences("prefs", MODE_PRIVATE).getInt("widget_id", AppWidgetManager.INVALID_APPWIDGET_ID);
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) {
            host = new BatteryWidgetHost(this, HOST_ID);
            host.startListening();
            AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
            if (info != null) {
                hostedView = (BatteryWidgetHostView) host.createView(this, id, info);
                hostedView.setListener(pct -> { if (pct != null) AlertEngine.process(this, pct, "Ola widget"); });
                hostedView.postDelayed(() -> { Integer pct = BatteryParser.fromView(hostedView); if (pct != null) AlertEngine.process(this, pct, "Ola widget"); }, 1200);
            }
        }
    }
    @Override public void onDestroy() { if (host != null) host.stopListening(); hostedView = null; super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
