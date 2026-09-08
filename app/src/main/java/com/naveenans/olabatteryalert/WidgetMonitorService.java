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
    private static final int HOST_ID = 41041;
    private static final long SCAN_MS = 1500L;
    private static final long SCAN_CHARGING_MS = 800L;
    private BatteryWidgetHost host;
    private BatteryWidgetHostView hostedView;
    private WindowManager windowManager;
    private boolean overlayAttached;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean scanBusy = new AtomicBoolean(false);
    private int lastPct = -1;

    private final Runnable scanner = new Runnable() {
        @Override public void run() {
            scanNow();
            long delay = lastPct >= 0 && getSharedPreferences("prefs", MODE_PRIVATE).getInt("last_pct", -1) >= getSharedPreferences("prefs", MODE_PRIVATE).getInt("limit", 80) - 8
                    ? SCAN_CHARGING_MS : SCAN_MS;
            handler.postDelayed(this, delay);
        }
    };

    @Override public void onCreate() {
        super.onCreate();
        AlertEngine.ensureChannels(this);
        startForeground(4104, monitorNotification("Realtime widget scan running"));
        attachWidget();
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

    private void attachWidget() {
        int id = getSharedPreferences("prefs", MODE_PRIVATE).getInt("widget_id", AppWidgetManager.INVALID_APPWIDGET_ID);
        if (id == AppWidgetManager.INVALID_APPWIDGET_ID) return;
        if (host == null) {
            host = new BatteryWidgetHost(this, HOST_ID);
            try { host.startListening(); } catch (Exception ignored) {}
        }
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(id);
        if (info == null) return;
        try { AppWidgetManager.getInstance(this).updateAppWidgetOptions(id, widgetOptions()); } catch (Exception ignored) {}
        if (hostedView == null) {
            hostedView = (BatteryWidgetHostView) host.createView(this, id, info);
            hostedView.setListener(pct -> {
                if (pct != null) AlertEngine.process(this, pct, "widget-text");
                else scanFusion();
            });
        }
        int density = getResources().getDisplayMetrics().densityDpi;
        int w = Math.max(720, info.minWidth * density / 160);
        int h = Math.max(300, info.minHeight * density / 160);
        hostedView.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY));
        hostedView.layout(0, 0, w, h);
        attachOverlay(w, h);
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

    private void attachOverlay(int w, int h) {
        if (overlayAttached || hostedView == null) return;
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
            lp.alpha = 0.02f;
            windowManager.addView(hostedView, lp);
            overlayAttached = true;
        } catch (Throwable ignored) {}
    }

    private void scanNow() {
        if (hostedView == null) {
            attachWidget();
            return;
        }
        scanFusion();
    }

    private void scanFusion() {
        if (hostedView == null || !scanBusy.compareAndSet(false, true)) return;
        ScanEngine.scan(hostedView, new ScanEngine.Callback() {
            @Override public void onHit(int pct, String source, float confidence, String raw) {
                scanBusy.set(false);
                lastPct = pct;
                AlertEngine.process(WidgetMonitorService.this, pct, source + " · " + Math.round(confidence * 100) + "%");
                try {
                    NotificationManager nm = getSystemService(NotificationManager.class);
                    nm.notify(4104, monitorNotification(pct + "% via " + source));
                } catch (Throwable ignored) {}
            }
            @Override public void onMiss(String reason) {
                scanBusy.set(false);
            }
        });
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (hostedView == null) attachWidget();
        return START_STICKY;
    }

    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (overlayAttached && windowManager != null && hostedView != null) {
            try { windowManager.removeView(hostedView); } catch (Throwable ignored) {}
            overlayAttached = false;
        }
        if (host != null) try { host.stopListening(); } catch (Exception ignored) {}
        hostedView = null;
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
