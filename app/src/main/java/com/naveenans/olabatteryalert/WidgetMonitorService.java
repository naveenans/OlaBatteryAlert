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
            WidgetRefresh.ping(WidgetMonitorService.this);
            handler.postDelayed(() -> scanNow(), 450);
            int base = WidgetRefresh.intervalMs(WidgetMonitorService.this);
            handler.postDelayed(this, base);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        AlertEngine.ensureChannels(this);
        HostHolder.host(this);
        startForeground(4104, monitorNotification("Live widget refresh running"));
        handler.post(scanner);
    }

    private Notification monitorNotification(String text) {
        PendingIntent pi = PendingIntent.getActivity(this, 4,
                new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, AlertEngine.CHANNEL_MONITOR)
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setContentTitle("Battery Alert live")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    private BatteryWidgetHostView targetView() {
        BatteryWidgetHostView live = HostHolder.liveView();
        if (live != null) return live;
        return overlayView;
    }

    private void ensureOverlay() {
        if (HostHolder.liveView() != null) {
            detachOverlay();
            return;
        }
        if (overlayView != null && overlayAttached) return;
        int id = getSharedPreferences("prefs", MODE_PRIVATE).getInt("widget_id", AppWidgetManager.INVALID_APPWIDGET_ID);
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return;
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
        if (info == null) return;
        BatteryWidgetHost host = HostHolder.host(this);
        try { AppWidgetManager.getInstance(this).updateAppWidgetOptions(id, widgetOptions()); } catch (Exception ignored) {}
        if (overlayView == null) {
            overlayView = (BatteryWidgetHostView) host.createView(this, id, info);
            overlayView.setListener(pct -> {
                if (pct != null) AlertEngine.process(this, pct, "widget-text");
            });
        }
        int density = getResources().getDisplayMetrics().densityDpi;
        int w = Math.max(720, info.minWidth * density / 160);
        int h = Math.max(300, info.minHeight * density / 160);
        overlayView.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        overlayView.layout(0, 0, w, h);
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) return;
        if (overlayAttached) return;
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
            lp.alpha = 0.04f;
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
        if (!overlayAttached) return;
        if (windowManager != null && overlayView != null) {
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
        try {
            view.requestLayout();
            view.invalidate();
        } catch (Throwable ignored) {}
        scanFusion(view);
    }

    private void scanFusion(BatteryWidgetHostView view) {
        if (view == null || !scanBusy.compareAndSet(false, true)) return;
        handler.postDelayed(() -> scanBusy.set(false), Math.max(2500, WidgetRefresh.intervalMs(this)));
        ScanEngine.scan(view, new ScanEngine.Callback() {
            @Override public void onHit(int pct, String source, float confidence, String raw) {
                scanBusy.set(false);
                AlertEngine.process(WidgetMonitorService.this, pct, source + " · " + Math.round(confidence * 100) + "%");
                try {
                    getSystemService(NotificationManager.class)
                            .notify(4104, monitorNotification(pct + "% · " + source));
                } catch (Throwable ignored) {}
            }
            @Override public void onMiss(String reason) {
                scanBusy.set(false);
            }
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        detachOverlay();
        overlayView = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
