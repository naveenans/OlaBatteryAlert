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
        String collected = BatteryParser.collectText(view);
        BatteryParser.Hit textHit = BatteryParser.best(collected);
        // Always OCR. Cached TextViews keep the first percent if we return early.
        WidgetOcrReader.scan(view, (ocrHit, raw) -> {
            if (ocrHit != null && ocrHit.confidence >= 0.50f) {
                if (textHit != null && textHit.pct == ocrHit.pct) {
                    cb.onHit(textHit.pct, ocrHit.charging, "widget-text+ocr", Math.max(textHit.confidence, ocrHit.confidence), collected);
                } else {
                    cb.onHit(ocrHit.pct, ocrHit.charging, "widget-ocr-spatial", ocrHit.confidence, raw);
                }
                return;
            }
            if (textHit != null) {
                cb.onHit(textHit.pct, null, "widget-text", textHit.confidence, collected);
                return;
            }
            cb.onMiss(raw == null || raw.isEmpty() ? "no-digits" : raw);
        });
    }
}
