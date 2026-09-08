package com.naveenans.olabatteryalert;

import android.appwidget.AppWidgetHostView;
import android.content.Context;
import android.widget.RemoteViews;

public class BatteryWidgetHostView extends AppWidgetHostView {
    public interface Listener { void onBattery(Integer pct); }
    private Listener listener;
    private volatile long lastRemoteUpdateAt;
    public BatteryWidgetHostView(Context c) { super(c); }
    public void setListener(Listener l) { listener = l; }
    public long lastRemoteUpdateAt() { return lastRemoteUpdateAt; }

    @Override public void updateAppWidget(RemoteViews remoteViews) {
        super.updateAppWidget(remoteViews);
        lastRemoteUpdateAt = android.os.SystemClock.elapsedRealtime();
        postDelayed(() -> {
            if (listener == null) return;
            ScanEngine.scan(this, new ScanEngine.Callback() {
                @Override public void onHit(int pct, Boolean charging, String source, float confidence, String raw) {
                    listener.onBattery(pct);
                }
                @Override public void onMiss(String reason) {
                    listener.onBattery(null);
                }
            });
        }, 180);
    }
}
