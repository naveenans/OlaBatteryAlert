package com.naveenans.olabatteryalert;

import android.view.View;

public final class ScanEngine {
    public interface Callback {
        void onHit(int pct, String source, float confidence, String raw);
        void onMiss(String reason);
    }

    private ScanEngine() {}

    public static void scan(View view, Callback cb) {
        if (cb == null) return;
        if (view == null) { cb.onMiss("no-view"); return; }
        String collected = BatteryParser.collectText(view);
        BatteryParser.Hit textHit = BatteryParser.best(collected);
        if (textHit != null && textHit.confidence >= 0.62f) {
            cb.onHit(textHit.pct, "widget-text", textHit.confidence, collected);
            return;
        }
        WidgetOcrReader.scan(view, (ocrHit, raw) -> {
            if (ocrHit != null) {
                cb.onHit(ocrHit.pct, "widget-ocr", ocrHit.confidence, raw);
            } else if (textHit != null) {
                cb.onHit(textHit.pct, "widget-text", textHit.confidence, collected);
            } else {
                cb.onMiss(raw == null || raw.isEmpty() ? "no-digits" : raw);
            }
        });
    }
}
