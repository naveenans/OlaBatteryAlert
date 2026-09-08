package com.naveenans.olabatteryalert;

import android.app.Notification;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

public class OlaNotificationListener extends NotificationListenerService {
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null) return;
        String pkg = sbn.getPackageName() == null ? "" : sbn.getPackageName().toLowerCase();
        if (!pkg.contains("ola") && !pkg.contains("electric")) return;
        Notification n = sbn.getNotification();
        if (n == null) return;
        Bundle e = n.extras;
        StringBuilder all = new StringBuilder();
        append(all, e.getCharSequence(Notification.EXTRA_TITLE, ""));
        append(all, e.getCharSequence(Notification.EXTRA_TEXT, ""));
        append(all, e.getCharSequence(Notification.EXTRA_SUB_TEXT, ""));
        append(all, e.getCharSequence(Notification.EXTRA_BIG_TEXT, ""));
        append(all, e.getCharSequence(Notification.EXTRA_INFO_TEXT, ""));
        CharSequence[] lines = e.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) for (CharSequence line : lines) append(all, line);
        BatteryParser.Hit hit = BatteryParser.best(all.toString());
        if (hit != null) AlertEngine.process(this, hit.pct, "ola-notification");
    }

    private static void append(StringBuilder b, CharSequence cs) {
        if (cs != null && cs.length() > 0) b.append(cs).append(' ');
    }
}
