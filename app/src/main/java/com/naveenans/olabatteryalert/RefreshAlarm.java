package com.naveenans.olabatteryalert;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class RefreshAlarm extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        WidgetRefresh.ensureMonitor(c);
    }
}
