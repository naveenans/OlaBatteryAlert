package com.naveenans.olabatteryalert;

import android.appwidget.AppWidgetHost;
import android.appwidget.AppWidgetHostView;
import android.content.Context;

public class BatteryWidgetHost extends AppWidgetHost {
    public BatteryWidgetHost(Context c, int hostId) { super(c, hostId); }
    @Override protected AppWidgetHostView onCreateView(Context context, int appWidgetId, android.appwidget.AppWidgetProviderInfo info) {
        return new BatteryWidgetHostView(context);
    }
}
