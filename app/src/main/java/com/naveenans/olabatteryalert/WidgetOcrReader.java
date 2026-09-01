package com.naveenans.olabatteryalert;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import com.google.android.gms.tasks.Task;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public final class WidgetOcrReader {
    public interface Callback { void onResult(Integer percent, String rawText); }
    private static final TextRecognizer RECOGNIZER = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
    private WidgetOcrReader() {}

    public static void scan(View view, Callback callback) {
        if (view == null) { callback.onResult(null, ""); return; }
        try {
            int width = view.getWidth();
            int height = view.getHeight();
            if (width < 100) width = Math.max(600, view.getMeasuredWidth());
            if (height < 80) height = Math.max(260, view.getMeasuredHeight());
            width = Math.min(width, 1200);
            height = Math.min(height, 700);
            int wSpec = View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY);
            int hSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY);
            view.measure(wSpec, hSpec);
            view.layout(0, 0, width, height);

            Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            view.draw(canvas);
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            Task<Text> task = RECOGNIZER.process(image);
            task.addOnSuccessListener(result -> {
                String text = result == null ? "" : result.getText();
                callback.onResult(BatteryParser.fromText(text), text);
                bitmap.recycle();
            }).addOnFailureListener(e -> {
                callback.onResult(null, "");
                bitmap.recycle();
            });
        } catch (Throwable t) {
            callback.onResult(null, "");
        }
    }
}
