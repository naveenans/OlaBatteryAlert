package com.naveenans.olabatteryalert;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if (c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getBoolean("monitor", false)) {
            try { c.startForegroundService(new Intent(c, WidgetMonitorService.class)); } catch (Exception ignored) {}
        }
    }
}
