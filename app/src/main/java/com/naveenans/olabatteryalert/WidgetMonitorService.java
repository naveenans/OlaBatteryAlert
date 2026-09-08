package com.naveenans.olabatteryalert;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.graphics.PixelFormat;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class WidgetMonitorService extends Service {
    private BatteryWidgetHostView overlayView;
    private WindowManager windowManager;
    private boolean overlayAttached;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean scanBusy = new AtomicBoolean(false);

    private final Runnable scanner = new Runnable() {
        @Override public void run() {
            cycle();
            handler.postDelayed(this, WidgetRefresh.DEFAULT_MS);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        AlertEngine.ensureChannels(this);
        HostHolder.host(this);
        startForeground(4104, monitorNotification("Live widget monitor active"));
        WidgetRefresh.schedule(this);
        handler.post(scanner);
    }

    private Notification monitorNotification(String text) {
        PendingIntent pi = PendingIntent.getActivity(this, 4,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, AlertEngine.CHANNEL_MONITOR)
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setContentTitle("Battery Alert")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private void cycle() {
        // Keep the same host view. Recreating it here makes AppWidgetHost immediately
        // replay its cached RemoteViews (the source of the permanently stale value).
        BatteryWidgetHostView live = HostHolder.liveView();
        if (live != null && overlayView != null) {
            detachOverlay();
            overlayView = null;
        }
        if (targetView() == null) ensureOverlay();
        WidgetRefresh.ping(this);
        handler.postDelayed(this::scanNow, 1800);
        handler.postDelayed(this::scanNow, 5000);
        handler.postDelayed(this::scanNow, 10000);
        WidgetRefresh.schedule(this);
    }

    private BatteryWidgetHostView targetView() {
        BatteryWidgetHostView live = HostHolder.liveView();
        if (live != null) return live;
        return overlayView;
    }

    private void rebuildOverlay() {
        if (HostHolder.liveView() != null) {
            detachOverlay();
            overlayView = null;
            return;
        }
        detachOverlay();
        overlayView = null;
        ensureOverlay();
    }

    private void ensureOverlay() {
        int id = getSharedPreferences("prefs", MODE_PRIVATE).getInt("widget_id", AppWidgetManager.INVALID_APPWIDGET_ID);
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return;
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
        if (info == null) return;
        BatteryWidgetHost host = HostHolder.host(this);
        try { AppWidgetManager.getInstance(this).updateAppWidgetOptions(id, widgetOptions()); } catch (Exception ignored) {}
        overlayView = (BatteryWidgetHostView) host.createView(this, id, info);
        overlayView.setListener(pct -> {
            if (pct != null) AlertEngine.process(this, pct, "widget-text");
        });
        int density = getResources().getDisplayMetrics().densityDpi;
        int w = Math.max(720, info.minWidth * density / 160);
        int h = Math.max(300, info.minHeight * density / 160);
        overlayView.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        overlayView.layout(0, 0, w, h);
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return;
        try {
            windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    w, h,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.START;
            lp.x = 0;
            lp.y = 0;
            lp.alpha = 0.05f;
            windowManager.addView(overlayView, lp);
            overlayAttached = true;
        } catch (Throwable ignored) {}
    }

    private android.os.Bundle widgetOptions() {
        android.os.Bundle o = new android.os.Bundle();
        int screenDp = (int) (getResources().getDisplayMetrics().widthPixels / getResources().getDisplayMetrics().density);
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_HOST_CATEGORY, AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN);
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, Math.max(250, screenDp - 48));
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, Math.max(280, screenDp - 24));
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 140);
        o.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 320);
        return o;
    }

    private void detachOverlay() {
        if (overlayAttached && windowManager != null && overlayView != null) {
            try { windowManager.removeView(overlayView); } catch (Throwable ignored) {}
        }
        overlayAttached = false;
    }

    private void scanNow() {
        BatteryWidgetHostView view = targetView();
        if (view == null) {
            ensureOverlay();
            view = targetView();
        }
        if (view == null) return;
        try { view.requestLayout(); view.invalidate(); } catch (Throwable ignored) {}
        if (!scanBusy.compareAndSet(false, true)) return;
        handler.postDelayed(() -> scanBusy.set(false), 20000);
        ScanEngine.scan(view, new ScanEngine.Callback() {
            @Override public void onHit(int pct, Boolean charging, String source, float confidence, String raw) {
                scanBusy.set(false);
                AlertEngine.process(WidgetMonitorService.this, pct, charging, source + " · " + Math.round(confidence * 100) + "%");
                try {
                    getSystemService(NotificationManager.class)
                            .notify(4104, monitorNotification(pct + "% · " + (charging == null ? "charge state unknown" : charging ? "charging" : "not charging") + " · live refresh active"));
                } catch (Throwable ignored) {}
            }
            @Override public void onMiss(String reason) { scanBusy.set(false); }
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        handler.removeCallbacks(scanner);
        handler.post(scanner);
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        detachOverlay();
        overlayView = null;
        WidgetRefresh.schedule(this);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
