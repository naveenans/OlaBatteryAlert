package com.naveenans.olabatteryalert;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            List<Bitmap> passes = new ArrayList<>();
            passes.add(src);
            Bitmap hi = upsample(src, 2);
            if (hi != null) passes.add(hi);
            Bitmap gray = contrast(src, false);
            if (gray != null) passes.add(gray);
            Bitmap inv = contrast(src, true);
            if (inv != null) passes.add(inv);
            Bitmap bin = adaptive(gray != null ? gray : src);
            if (bin != null) passes.add(bin);
            Bitmap right = crop(src, 0.42f, 0f, 1f, 1f);
            if (right != null) {
                passes.add(right);
                Bitmap r2 = contrast(right, true);
                if (r2 != null) passes.add(r2);
            }
            Bitmap tr = crop(src, 0.45f, 0f, 1f, 0.62f);
            if (tr != null) passes.add(tr);
            runPass(passes, 0, new ArrayList<>(), new StringBuilder(), callback);
        } catch (Throwable t) {
            callback.onResult(null, "");
        }
    }

    private static void runPass(List<Bitmap> passes, int i, List<BatteryParser.Hit> hits, StringBuilder raw, Callback cb) {
        if (i >= passes.size()) {
            recycleAll(passes);
            cb.onResult(vote(hits), raw.toString());
            return;
        }
        Bitmap bmp = passes.get(i);
        try {
            RECOGNIZER.process(InputImage.fromBitmap(bmp, 0))
                    .addOnSuccessListener(result -> {
                        String text = result == null ? "" : result.getText();
                        if (text.length() > 0) {
                            if (raw.length() > 0) raw.append(" | ");
                            raw.append(text);
                        }
                        BatteryParser.Hit hit = pick(result, text, bmp);
                        if (hit != null) hits.add(hit);
                        runPass(passes, i + 1, hits, raw, cb);
                    })
                    .addOnFailureListener(e -> runPass(passes, i + 1, hits, raw, cb));
        } catch (Throwable t) {
            runPass(passes, i + 1, hits, raw, cb);
        }
    }

    private static BatteryParser.Hit vote(List<BatteryParser.Hit> hits) {
        if (hits == null || hits.isEmpty()) return null;
        Map<Integer, Integer> n = new HashMap<>();
        Map<Integer, Float> conf = new HashMap<>();
        Map<Integer, String> raw = new HashMap<>();
        Map<Integer, Integer> charging = new HashMap<>();
        Map<Integer, Integer> notCharging = new HashMap<>();
        for (BatteryParser.Hit h : hits) {
            n.put(h.pct, n.getOrDefault(h.pct, 0) + 1);
            conf.put(h.pct, Math.max(conf.getOrDefault(h.pct, 0f), h.confidence));
            raw.put(h.pct, h.raw);
            if (Boolean.TRUE.equals(h.charging)) charging.put(h.pct, charging.getOrDefault(h.pct, 0) + 1);
            else if (Boolean.FALSE.equals(h.charging)) notCharging.put(h.pct, notCharging.getOrDefault(h.pct, 0) + 1);
        }
        int bestPct = -1;
        int bestN = 0;
        float bestC = 0;
        for (Map.Entry<Integer, Integer> e : n.entrySet()) {
            float c = conf.getOrDefault(e.getKey(), 0f);
            if (e.getValue() > bestN || (e.getValue() == bestN && c > bestC)) {
                bestN = e.getValue();
                bestC = c;
                bestPct = e.getKey();
            }
        }
        if (bestPct < 0) return null;
        float score = Math.min(0.99f, bestC + (bestN >= 3 ? 0.12f : bestN >= 2 ? 0.06f : 0f));
        Boolean state = null;
        int yes = charging.getOrDefault(bestPct, 0);
        int no = notCharging.getOrDefault(bestPct, 0);
        if (yes > 0) state = true;
        else if (no > 0) state = false;
        return new BatteryParser.Hit(bestPct, score, raw.get(bestPct), state);
    }

    private static BatteryParser.Hit pick(Text result, String full, Bitmap bitmap) {
        BatteryParser.Hit best = BatteryParser.best(full);
        if (result == null) return best;
        try {
            for (Text.TextBlock block : result.getTextBlocks()) {
                BatteryParser.Hit h = BatteryParser.best(block.getText());
                if (h != null && (best == null || h.confidence > best.confidence)) best = boost(h, block);
                for (Text.Line line : block.getLines()) {
                    BatteryParser.Hit lh = BatteryParser.best(line.getText());
                    if (lh != null && (best == null || lh.confidence > best.confidence)) best = boost(lh, block);
                    BatteryParser.Hit spatial = spatialPercent(line, bitmap);
                    if (spatial != null && (best == null || spatial.confidence > best.confidence)) best = spatial;
                }
            }
        } catch (Throwable ignored) {}
        return best;
    }

    // Strong fallback for the OLA widget: accept a number only when a percent glyph is
    // in the same OCR element or immediately to its right on the same visual line.
    private static BatteryParser.Hit spatialPercent(Text.Line line, Bitmap bitmap) {
        if (line == null) return null;
        List<Text.Element> elements = line.getElements();
        for (int i = 0; i < elements.size(); i++) {
            Text.Element number = elements.get(i);
            String nt = BatteryParser.normalize(number.getText());
            BatteryParser.Hit same = BatteryParser.best(nt);
            Rect nb = number.getBoundingBox();
            if (same != null && nt.contains("%") && nb != null) {
                return new BatteryParser.Hit(same.pct, 0.99f, line.getText(), detectCharging(bitmap, nb));
            }
            if (!nt.matches("^(100|[0-9]{1,2})$") || nb == null) continue;
            for (int j = i + 1; j < Math.min(elements.size(), i + 3); j++) {
                Text.Element percent = elements.get(j);
                String pt = BatteryParser.normalize(percent.getText()).toLowerCase(java.util.Locale.US);
                Rect pb = percent.getBoundingBox();
                if (pb == null || !(pt.contains("%") || pt.contains("percent") || pt.equals("pct"))) continue;
                int height = Math.max(nb.height(), pb.height());
                int gap = pb.left - nb.right;
                int vertical = Math.abs(pb.centerY() - nb.centerY());
                if (gap < -height / 2 || gap > height * 2 || vertical > height) continue;
                int pct;
                try { pct = Integer.parseInt(nt); } catch (Exception ignored) { continue; }
                Rect pair = new Rect(Math.min(nb.left, pb.left), Math.min(nb.top, pb.top),
                        Math.max(nb.right, pb.right), Math.max(nb.bottom, pb.bottom));
                return new BatteryParser.Hit(pct, 0.99f, line.getText(), detectCharging(bitmap, pair));
            }
        }
        return null;
    }

    // The charging bolt in the supplied OLA widget is a saturated green shape directly
    // left of the percentage. A ratio threshold rejects the scooter glow further below.
    private static Boolean detectCharging(Bitmap bitmap, Rect percentBox) {
        if (bitmap == null || percentBox == null) return null;
        int h = Math.max(12, percentBox.height());
        int left = Math.max(0, percentBox.left - Math.round(h * 2.4f));
        int right = Math.max(left + 1, Math.min(bitmap.getWidth(), percentBox.left + h / 5));
        int top = Math.max(0, percentBox.top - h / 2);
        int bottom = Math.min(bitmap.getHeight(), percentBox.bottom + h / 2);
        int green = 0, sampled = 0;
        for (int y = top; y < bottom; y += 2) {
            for (int x = left; x < right; x += 2) {
                int c = bitmap.getPixel(x, y);
                int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
                sampled++;
                if (g >= 85 && g > r * 1.35f && g > b * 1.12f && g - Math.max(r, b) >= 28) green++;
            }
        }
        if (sampled == 0) return null;
        return green >= Math.max(8, sampled / 120);
    }

    private static BatteryParser.Hit boost(BatteryParser.Hit hit, Text.TextBlock block) {
        if (hit == null || block == null || block.getBoundingBox() == null) return hit;
        int h = block.getBoundingBox().height();
        float extra = h >= 56 ? 0.10f : h >= 32 ? 0.05f : 0f;
        return extra == 0f ? hit : new BatteryParser.Hit(hit.pct, Math.min(0.99f, hit.confidence + extra), hit.raw, hit.charging);
    }

    private static Bitmap capture(View view) {
        int width = view.getWidth();
        int height = view.getHeight();
        if (width < 80) width = Math.max(view.getMeasuredWidth(), 720);
        if (height < 60) height = Math.max(view.getMeasuredHeight(), 280);
        width = Math.max(200, Math.min(width, 1200));
        height = Math.max(120, Math.min(height, 640));
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY));
        view.layout(0, 0, width, height);
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        view.draw(canvas);
        return bitmap;
    }

    private static Bitmap upsample(Bitmap src, int factor) {
        if (src == null) return null;
        try {
            return Bitmap.createScaledBitmap(src, src.getWidth() * factor, src.getHeight() * factor, true);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap contrast(Bitmap src, boolean invert) {
        if (src == null) return null;
        try {
            Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(out);
            ColorMatrix gray = new ColorMatrix();
            gray.setSaturation(0f);
            float contrast = 1.85f;
            float translate = (-0.5f * contrast + 0.5f) * 255f;
            ColorMatrix scale = new ColorMatrix(new float[]{
                    contrast, 0, 0, 0, translate,
                    0, contrast, 0, 0, translate,
                    0, 0, contrast, 0, translate,
                    0, 0, 0, 1, 0
            });
            gray.postConcat(scale);
            if (invert) {
                gray.postConcat(new ColorMatrix(new float[]{
                        -1, 0, 0, 0, 255,
                        0, -1, 0, 0, 255,
                        0, 0, -1, 0, 255,
                        0, 0, 0, 1, 0
                }));
            }
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            p.setColorFilter(new ColorMatrixColorFilter(gray));
            c.drawBitmap(src, 0, 0, p);
            return out;
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap crop(Bitmap src, float x0, float y0, float x1, float y1) {
        if (src == null) return null;
        try {
            int x = Math.max(0, Math.round(src.getWidth() * x0));
            int y = Math.max(0, Math.round(src.getHeight() * y0));
            int w = Math.min(src.getWidth() - x, Math.round(src.getWidth() * (x1 - x0)));
            int h = Math.min(src.getHeight() - y, Math.round(src.getHeight() * (y1 - y0)));
            if (w < 40 || h < 40) return null;
            return Bitmap.createBitmap(src, x, y, w, h);
        } catch (Throwable t) {
            return null;
        }
    }

    private static Bitmap adaptive(Bitmap src) {
        if (src == null) return null;
        try {
            int w = src.getWidth(), h = src.getHeight();
            int[] px = new int[w * h];
            src.getPixels(px, 0, w, 0, 0, w, h);
            int block = 18;
            int[] out = new int[w * h];
            for (int y = 0; y < h; y++) {
                int y0 = Math.max(0, y - block);
                int y1 = Math.min(h - 1, y + block);
                for (int x = 0; x < w; x++) {
                    int x0 = Math.max(0, x - block);
                    int x1 = Math.min(w - 1, x + block);
                    long sum = 0;
                    int n = 0;
                    for (int yy = y0; yy <= y1; yy += 3) {
                        int row = yy * w;
                        for (int xx = x0; xx <= x1; xx += 3) {
                            int c = px[row + xx];
                            sum += ((c >> 16) & 0xff);
                            n++;
                        }
                    }
                    int mean = n == 0 ? 128 : (int) (sum / n);
                    int lum = (px[y * w + x] >> 16) & 0xff;
                    int v = lum < mean - 10 ? 0 : 255;
                    out[y * w + x] = 0xff000000 | (v << 16) | (v << 8) | v;
                }
            }
            Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            bmp.setPixels(out, 0, w, 0, 0, w, h);
            return bmp;
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
