package com.naveenans.olabatteryalert;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BatteryParser {
    public static final class Hit {
        public final int pct;
        public final float confidence;
        public final String raw;
        public Hit(int pct, float confidence, String raw) {
            this.pct = pct;
            this.confidence = confidence;
            this.raw = raw;
        }
    }

    private static final Pattern PERCENT = Pattern.compile("(?<!\\d)(100|[1-9]?\\d)\\s*(?:%|percent|pct)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LABELED = Pattern.compile("(?i)(?:soc|battery|charge|charging|bms)\\s*[:\\-–]?\\s*(100|[1-9]?\\d)");
    private static final Pattern BARE = Pattern.compile("(?<!\\d)(100|[1-9]?\\d)(?!\\d)");
    private static final Pattern REJECT = Pattern.compile("(?i)(\\d+)\\s*(km|kw|kwh|wh|v\\b|volt|amp|°c|deg|range|km/h|kmph)");

    private BatteryParser() {}

    public static Integer fromView(View view) {
        Hit hit = bestFromView(view);
        return hit == null ? null : hit.pct;
    }

    public static Integer fromText(String text) {
        Hit hit = best(text);
        return hit == null ? null : hit.pct;
    }

    public static Hit bestFromView(View view) {
        return best(collectText(view));
    }

    public static String collectText(View view) {
        StringBuilder out = new StringBuilder(256);
        collect(view, out, 0);
        return out.toString();
    }

    private static void collect(View view, StringBuilder out, int depth) {
        if (view == null || depth > 48 || out.length() > 8000) return;
        if (view instanceof TextView) {
            CharSequence cs = ((TextView) view).getText();
            if (cs != null && cs.length() > 0) out.append(cs).append(' ');
        }
        CharSequence desc = view.getContentDescription();
        if (desc != null && desc.length() > 0) out.append(desc).append(' ');
        CharSequence hint = view.getTooltipText();
        if (hint != null && hint.length() > 0) out.append(hint).append(' ');
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) collect(g.getChildAt(i), out, depth + 1);
        }
    }

    public static String normalize(String text) {
        if (text == null) return "";
        String t = text.replace('\n', ' ').replace('\r', ' ');
        t = t.replace('％', '%').replace('℅', '%');
        t = t.replaceAll("\\s+", " ").trim();
        StringBuilder out = new StringBuilder(t.length());
        for (String tok : t.split(" ")) {
            if (out.length() > 0) out.append(' ');
            if (tok.length() <= 4 && tok.matches(".*[0-9OIlSBZGo%％].*")) {
                out.append(tok.replace('O','0').replace('o','0').replace('I','1').replace('l','1')
                    .replace('|','1').replace('S','5').replace('B','8').replace('Z','2').replace('G','6'));
            } else out.append(tok);
        }
        return out.toString();
    }

    public static Hit best(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        String raw = text;
        String t = normalize(text);
        List<Hit> hits = new ArrayList<>();

        Matcher m = PERCENT.matcher(t);
        while (m.find()) add(hits, m.group(1), 0.86f, raw, t, m.start());

        m = LABELED.matcher(t);
        while (m.find()) add(hits, m.group(1), 0.72f, raw, t, m.start());

        m = BARE.matcher(t);
        while (m.find()) add(hits, m.group(1), 0.40f, raw, t, m.start());

        Hit best = null;
        for (Hit h : hits) {
            if (best == null || h.confidence > best.confidence) best = h;
        }
        return best != null && best.confidence >= 0.38f ? best : null;
    }

    private static void add(List<Hit> hits, String num, float base, String raw, String t, int at) {
        int pct;
        try { pct = Integer.parseInt(num); } catch (Exception e) { return; }
        if (pct < 0 || pct > 100) return;
        String window = slice(t, Math.max(0, at - 10), Math.min(t.length(), at + num.length() + 12));
        if (REJECT.matcher(window).find()) return;
        float score = base;
        if (window.contains("%") || window.toLowerCase(Locale.US).contains("percent")) score += 0.08f;
        if (pct >= 12 && pct <= 96) score += 0.04f;
        if (pct == 0 || pct == 100) score -= 0.04f;
        hits.add(new Hit(pct, Math.min(0.99f, score), raw));
    }

    private static String slice(String s, int a, int b) {
        if (s == null || s.isEmpty()) return "";
        return s.substring(Math.max(0, a), Math.min(s.length(), b)).toLowerCase(Locale.US);
    }
}
