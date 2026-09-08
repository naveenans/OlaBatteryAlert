package com.naveenans.olabatteryalert;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;

public final class WidgetRefresh {
    public static final String ACTION_UPDATED = "com.naveenans.olabatteryalert.BATTERY_UPDATED";
    public static final int DEFAULT_MS = 5 * 60 * 1000;
    private WidgetRefresh() {}

    public static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences("prefs", Context.MODE_PRIVATE);
    }

    public static int intervalMs(Context c) {
        return DEFAULT_MS;
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
        try {
            Bundle opts = mgr.getAppWidgetOptions(id);
            if (opts == null) opts = new Bundle();
            boolean flip = prefs(c).getBoolean("ping_flip", false);
            int minW = opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 250);
            if (minW < 180) minW = 250;
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, minW + (flip ? 1 : -1));
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, Math.max(280, minW + 40));
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, Math.max(120, opts.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 140)));
            opts.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 320);
            opts.putLong("refresh_at", System.currentTimeMillis());
            mgr.updateAppWidgetOptions(id, opts);
            prefs(c).edit().putBoolean("ping_flip", !flip).apply();
        } catch (Throwable ignored) {}
        Intent i = new Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        i.setComponent(info.provider);
        i.setPackage(info.provider.getPackageName());
        i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
        i.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, new int[]{id});
        try { c.sendBroadcast(i); } catch (Throwable ignored) {}
        try { mgr.notifyAppWidgetViewDataChanged(id, android.R.id.list); } catch (Throwable ignored) {}
    }

    public static void notifyUi(Context c) {
        try { c.sendBroadcast(new Intent(ACTION_UPDATED).setPackage(c.getPackageName())); } catch (Throwable ignored) {}
    }

    public static void ensureMonitor(Context c) {
        prefs(c).edit().putBoolean("monitor", true).apply();
        try {
            Intent i = new Intent(c, WidgetMonitorService.class);
            if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i);
            else c.startService(i);
        } catch (Throwable ignored) {}
        schedule(c);
    }

    public static void schedule(Context c) {
        try {
            AlarmManager am = (AlarmManager) c.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(c, RefreshAlarm.class);
            PendingIntent pi = PendingIntent.getBroadcast(c, 41, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            long at = System.currentTimeMillis() + DEFAULT_MS;
            if (Build.VERSION.SDK_INT >= 23) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
            else am.setExact(AlarmManager.RTC_WAKEUP, at, pi);
        } catch (Throwable ignored) {}
    }
}
