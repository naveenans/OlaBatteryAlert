package com.naveenans.olabatteryalert;

import android.appwidget.AppWidgetManager;
import android.content.*;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        int id = c.getSharedPreferences("prefs", Context.MODE_PRIVATE).getInt("widget_id", AppWidgetManager.INVALID_APPWIDGET_ID);
        if (id != AppWidgetManager.INVALID_APPWIDGET_ID) WidgetRefresh.ensureMonitor(c);
    }
}
