package com.naveenans.olabatteryalert;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.os.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class WidgetMonitorService extends Service {
    private static final int HOST_ID = 41041;
    private static final long SCAN_MS = 15000L;
    private BatteryWidgetHost host;
    private BatteryWidgetHostView hostedView;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean ocrBusy = new AtomicBoolean(false);

    private final Runnable scanner = new Runnable() {
        @Override public void run() {
            scanNow();
            handler.postDelayed(this, SCAN_MS);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        AlertEngine.ensureChannels(this);
        Notification n = new Notification.Builder(this, AlertEngine.CHANNEL_MONITOR)
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setContentTitle("Ola Battery Alert is running")
                .setContentText("Background battery scan every 15 seconds")
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
        startForeground(4104, n);
        attachWidget();
        handler.post(scanner);
    }

    private void attachWidget() {
        int id = getSharedPreferences("prefs", MODE_PRIVATE).getInt("widget_id", AppWidgetManager.INVALID_APPWIDGET_ID);
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return;
        host = new BatteryWidgetHost(this, HOST_ID);
        host.startListening();
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
        if (info == null) return;
        hostedView = (BatteryWidgetHostView) host.createView(this, id, info);
        int density = getResources().getDisplayMetrics().densityDpi;
        int w = Math.max(650, info.minWidth * density / 160);
        int h = Math.max(280, info.minHeight * density / 160);
        hostedView.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        hostedView.layout(0, 0, w, h);
        hostedView.setListener(pct -> {
            if (pct != null) AlertEngine.process(this, pct, "Ola widget text");
            else scanOcr();
        });
    }

    private void scanNow() {
        if (hostedView == null) {
            attachWidget();
            return;
        }
        Integer pct = BatteryParser.fromView(hostedView);
        if (pct != null) AlertEngine.process(this, pct, "Ola widget text");
        else scanOcr();
    }

    private void scanOcr() {
        if (hostedView == null || !ocrBusy.compareAndSet(false, true)) return;
        WidgetOcrReader.scan(hostedView, (pct, raw) -> {
            ocrBusy.set(false);
            if (pct != null) AlertEngine.process(this, pct, "Ola widget OCR");
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (host != null) host.stopListening();
        hostedView = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
