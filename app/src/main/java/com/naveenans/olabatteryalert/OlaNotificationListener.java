package com.naveenans.olabatteryalert;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.os.Bundle;

public class OlaNotificationListener extends NotificationListenerService {
    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName() == null ? "" : sbn.getPackageName().toLowerCase();
        if (!pkg.contains("ola")) return;
        Notification n = sbn.getNotification();
        Bundle e = n.extras;
        String title = String.valueOf(e.getCharSequence(Notification.EXTRA_TITLE, ""));
        String text = String.valueOf(e.getCharSequence(Notification.EXTRA_TEXT, ""));
        String big = String.valueOf(e.getCharSequence(Notification.EXTRA_BIG_TEXT, ""));
        Integer pct = BatteryParser.fromText(title + " " + text + " " + big);
        if (pct != null) AlertEngine.process(this, pct, "Ola notification");
    }
}
