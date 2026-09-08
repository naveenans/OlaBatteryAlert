package com.naveenans.olabatteryalert;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.view.View;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.ArrayList;
import java.util.List;

public final class WidgetOcrReader {
    public interface Callback { void onResult(BatteryParser.Hit hit, String rawText); }

    private static final TextRecognizer RECOGNIZER = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private WidgetOcrReader() {}

    public static void scan(View view, Callback callback) {
        if (callback == null) return;
        if (view == null) { callback.onResult(null, ""); return; }
        try {
            Bitmap src = capture(view);
            if (src == null) { callback.onResult(null, ""); return; }
            List<Bitmap> passes = new ArrayList<>(2);
            passes.add(src);
            Bitmap invert = enhance(src, true);
            if (invert != null) passes.add(invert);
            runPass(passes, 0, null, "", callback);
        } catch (Throwable t) {
            callback.onResult(null, "");
        }
    }

    private static void runPass(List<Bitmap> passes, int i, BatteryParser.Hit best, String bestRaw, Callback cb) {
        if (i >= passes.size()) {
            recycleAll(passes);
            cb.onResult(best, bestRaw == null ? "" : bestRaw);
            return;
        }
        Bitmap bmp = passes.get(i);
        try {
            InputImage image = InputImage.fromBitmap(bmp, 0);
            Task<Text> task = RECOGNIZER.process(image);
            task.addOnSuccessListener(result -> {
                String text = result == null ? "" : result.getText();
                BatteryParser.Hit hit = pick(result, text);
                BatteryParser.Hit next = best;
                String raw = bestRaw;
                if (hit != null && (next == null || hit.confidence > next.confidence)) {
                    next = hit;
                    raw = text;
                }
                if (next != null && next.confidence >= 0.80f) {
                    recycleAll(passes);
                    cb.onResult(next, raw);
                    return;
                }
                runPass(passes, i + 1, next, raw, cb);
            }).addOnFailureListener(e -> runPass(passes, i + 1, best, bestRaw, cb));
        } catch (Throwable t) {
            runPass(passes, i + 1, best, bestRaw, cb);
        }
    }

    private static BatteryParser.Hit pick(Text result, String full) {
        BatteryParser.Hit best = BatteryParser.best(full);
        if (result == null) return best;
        try {
            for (Text.TextBlock block : result.getTextBlocks()) {
                BatteryParser.Hit h = BatteryParser.best(block.getText());
                if (h != null && (best == null || h.confidence > best.confidence)) best = boostBySize(h, block);
                for (Text.Line line : block.getLines()) {
                    BatteryParser.Hit lh = BatteryParser.best(line.getText());
                    if (lh != null && (best == null || lh.confidence > best.confidence)) best = boostBySize(lh, line.getBoundingBox() == null ? block : block);
                }
            }
        } catch (Throwable ignored) {}
        return best;
    }

    private static BatteryParser.Hit boostBySize(BatteryParser.Hit hit, Text.TextBlock block) {
        if (hit == null || block == null || block.getBoundingBox() == null) return hit;
        int h = block.getBoundingBox().height();
        float extra = h >= 48 ? 0.08f : h >= 28 ? 0.04f : 0f;
        return extra == 0f ? hit : new BatteryParser.Hit(hit.pct, Math.min(0.99f, hit.confidence + extra), hit.raw);
    }

    private static Bitmap capture(View view) {
        int width = view.getWidth();
        int height = view.getHeight();
        if (width < 80) width = Math.max(view.getMeasuredWidth(), 640);
        if (height < 60) height = Math.max(view.getMeasuredHeight(), 240);
        width = Math.max(160, Math.min(width, 1400));
        height = Math.max(100, Math.min(height, 800));
        int wSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
        int hSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
        view.measure(wSpec, hSpec);
        view.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        if (width < 400 || height < 160) {
            Bitmap scaled = Bitmap.createScaledBitmap(bitmap, width * 2, height * 2, true);
            if (scaled != bitmap) bitmap.recycle();
            return scaled;
        }
        return bitmap;
    }

    private static Bitmap enhance(Bitmap src, boolean invert) {
        if (src == null) return null;
        try {
            Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            ColorMatrix gray = new ColorMatrix();
            gray.setSaturation(0f);
            float contrast = 1.55f;
            float translate = (-0.5f * contrast + 0.5f) * 255f;
            ColorMatrix scale = new ColorMatrix(new float[]{
                    contrast, 0, 0, 0, translate,
                    0, contrast, 0, 0, translate,
                    0, 0, contrast, 0, translate,
                    0, 0, 0, 1, 0
            });
            gray.postConcat(scale);
            if (invert) {
                ColorMatrix inv = new ColorMatrix(new float[]{
                        -1, 0, 0, 0, 255,
                        0, -1, 0, 0, 255,
                        0, 0, -1, 0, 255,
                        0, 0, 0, 1, 0
                });
                gray.postConcat(inv);
            }
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            p.setColorFilter(new ColorMatrixColorFilter(gray));
            c.drawBitmap(src, 0, 0, p);
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap cropRight(Bitmap src) {
        if (src == null || src.getWidth() < 120) return null;
        try {
            int x = src.getWidth() / 3;
            return Bitmap.createBitmap(src, x, 0, src.getWidth() - x, src.getHeight());
        } catch (Throwable t) {
            return null;
        }
    }

    private static void recycleAll(List<Bitmap> passes) {
        if (passes == null) return;
        for (Bitmap b : passes) {
            try { if (b != null && !b.isRecycled()) b.recycle(); } catch (Throwable ignored) {}
        }
        passes.clear();
    }
}
