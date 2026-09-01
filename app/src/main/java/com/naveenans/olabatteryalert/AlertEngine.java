package com.naveenans.olabatteryalert;

import android.app.*;
import android.content.*;
import android.os.Build;

public final class AlertEngine {
    public static final String CHANNEL_ALERT = "charge_limit";
    public static final String CHANNEL_MONITOR = "monitor";
    private AlertEngine() {}

    public static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            NotificationChannel alert = new NotificationChannel(CHANNEL_ALERT, "Charge limit alerts", NotificationManager.IMPORTANCE_HIGH);
            alert.enableVibration(true); alert.setDescription("Alerts when Ola scooter battery reaches your selected charging limit.");
            nm.createNotificationChannel(alert);
            NotificationChannel monitor = new NotificationChannel(CHANNEL_MONITOR, "Widget monitor", NotificationManager.IMPORTANCE_LOW);
            monitor.setDescription("Keeps the Ola widget monitor active while enabled.");
            nm.createNotificationChannel(monitor);
        }
    }

    public static void process(Context c, int pct, String source) {
        SharedPreferences p = c.getSharedPreferences("prefs", Context.MODE_PRIVATE);
        int limit = p.getInt("limit", 80);
        int last = p.getInt("last_pct", -1);
        p.edit().putInt("last_pct", pct).putLong("last_update", System.currentTimeMillis()).putString("last_source", source).apply();
        if (pct >= limit && last < limit) sendLimitAlert(c, pct, limit, source);
    }

    public static void sendLimitAlert(Context c, int pct, int limit, String source) {
        ensureChannels(c);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 11, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CHANNEL_ALERT)
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setContentTitle("Ola charge limit reached: " + pct + "%")
                .setContentText("Your " + limit + "% charging alert was reached. Source: " + source)
                .setCategory(Notification.CATEGORY_ALARM).setPriority(Notification.PRIORITY_MAX)
                .setContentIntent(pi).setAutoCancel(true).setDefaults(Notification.DEFAULT_ALL).build();
        c.getSystemService(NotificationManager.class).notify(8080, n);
    }
}
