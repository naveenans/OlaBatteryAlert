package com.naveenans.olabatteryalert;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BatteryParser {
    private static final Pattern PERCENT = Pattern.compile("(?<!\\d)(100|[1-9]?\\d)\\s*%");
    private BatteryParser() {}

    public static Integer fromView(View view) {
        if (view == null) return null;
        if (view instanceof TextView) {
            CharSequence cs = ((TextView) view).getText();
            Integer v = fromText(cs == null ? null : cs.toString());
            if (v != null) return v;
        }
        CharSequence desc = view.getContentDescription();
        Integer fromDesc = fromText(desc == null ? null : desc.toString());
        if (fromDesc != null) return fromDesc;
        if (view instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) view;
            for (int i = 0; i < g.getChildCount(); i++) {
                Integer v = fromView(g.getChildAt(i));
                if (v != null) return v;
            }
        }
        return null;
    }

    public static Integer fromText(String text) {
        if (text == null) return null;
        Matcher m = PERCENT.matcher(text);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)); } catch (Exception ignored) {}
        }
        return null;
    }
}
