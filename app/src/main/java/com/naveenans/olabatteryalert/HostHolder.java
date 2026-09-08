package com.naveenans.olabatteryalert;

import android.content.Context;

/** One AppWidgetHost for the whole process. Two hosts with the same id steal updates. */
public final class HostHolder {
    public static final int HOST_ID = 41041;
    private static BatteryWidgetHost host;
    private static BatteryWidgetHostView liveView;

    private HostHolder() {}

    public static synchronized BatteryWidgetHost host(Context c) {
        if (host == null) {
            host = new BatteryWidgetHost(c.getApplicationContext(), HOST_ID);
            try { host.startListening(); } catch (Exception ignored) {}
        }
        return host;
    }

    public static synchronized void setLiveView(BatteryWidgetHostView v) {
        liveView = v;
    }

    public static synchronized BatteryWidgetHostView liveView() {
        BatteryWidgetHostView v = liveView;
        if (v != null && v.getWindowToken() == null && v.getParent() == null) return null;
        return v;
    }
}
