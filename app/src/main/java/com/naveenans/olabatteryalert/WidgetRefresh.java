package com.naveenans.olabatteryalert;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public final class WidgetRefresh {
    public static final String ACTION_UPDATED = "com.naveenans.olabatteryalert.BATTERY_UPDATED";
    public static final int DEFAULT_MS = 2000;
    private WidgetRefresh() {}

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("prefs", Context.MODE_PRIVATE);
    }

    public static int intervalMs(Context c) {
        int ms = prefs(c).getInt("refresh_ms", DEFAULT_MS);
        if (ms < 1000) ms = 1000;
        if (ms > 30000) ms = 30000;
        return ms;
    }

    public static String theme(Context c) {
        String t = prefs(c).getString("theme", "neon");
        if ("volt".equals(t) || "ice".equals(t) || "neon".equals(t)) return t;
        return "neon";
    }

    public static void ping(Context c) {
        int id = prefs(c).getInt("widget_id", AppWidgetManager.INVALID_APPWIDGET_ID);
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return;
        AppWidgetManager mgr = AppWidgetManager.getInstance(c);
        AppWidgetProviderInfo info = mgr.getAppWidgetInfo(id);
        if (info == null || info.provider == null) return;
        Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        i.setComponent(info.provider);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{id});
        try { c.sendBroadcast(i); } catch (Throwable ignored) {}
        try { mgr.notifyAppWidgetViewDataChanged(id, android.R.id.list); } catch (Throwable ignored) {}
    }

    public static void notifyUi(Context c) {
        try { c.sendBroadcast(new Intent(ACTION_UPDATED).setPackage(c.getPackageName())); } catch (Throwable ignored) {}
    }
}
