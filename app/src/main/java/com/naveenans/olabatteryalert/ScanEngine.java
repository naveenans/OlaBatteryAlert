package com.naveenans.olabatteryalert;

import android.view.View;

public final class ScanEngine {
    public interface Callback {
        void onHit(int pct, Boolean charging, String source, float confidence, String raw);
        void onMiss(String reason);
    }

    private ScanEngine() {}

    public static void scan(View view, Callback cb) {
        if (cb == null) return;
        if (view == null) { cb.onMiss("no-view"); return; }
        try {
            view.requestLayout();
            view.invalidate();
        } catch (Throwable ignored) {}
        // OCR-only. Never use TextView/content-description values because a widget
        // host can retain an old number there after its visible frame changes.
        WidgetOcrReader.scan(view, (ocrHit, raw) -> {
            if (ocrHit != null && ocrHit.confidence >= 0.50f) {
                cb.onHit(ocrHit.pct, ocrHit.charging, "widget-number-ocr", ocrHit.confidence, raw);
                return;
            }
            cb.onMiss(raw == null || raw.isEmpty() ? "no-upper-right-battery-number" : raw);
        });
    }
}
