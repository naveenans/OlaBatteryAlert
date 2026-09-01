package com.naveenans.olabatteryalert;

import android.accessibilityservice.AccessibilityService;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.Locale;

/**
 * User-enabled fallback that reads only visible accessibility text from the OLA app
 * or common launcher surfaces. It does not capture passwords, credentials or hidden app data.
 */
public class OlaAccessibilityReader extends AccessibilityService {
    private long lastScanAt = 0L;

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;
        CharSequence pkgCs = event.getPackageName();
        String pkg = pkgCs == null ? "" : pkgCs.toString().toLowerCase(Locale.US);
        if (!isAllowedSurface(pkg)) return;

        long now = SystemClock.elapsedRealtime();
        if (now - lastScanAt < 800L) return;
        lastScanAt = now;

        StringBuilder eventText = new StringBuilder();
        if (event.getText() != null) {
            for (CharSequence cs : event.getText()) if (cs != null) eventText.append(cs).append(' ');
        }
        CharSequence desc = event.getContentDescription();
        if (desc != null) eventText.append(desc).append(' ');
        Integer pct = BatteryParser.fromText(eventText.toString());
        if (pct != null) {
            AlertEngine.process(this, pct, pkg.contains("launcher") ? "OLA widget accessibility" : "OLA app accessibility");
            return;
        }

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        StringBuilder all = new StringBuilder();
        collect(root, all, 0);
        try { root.recycle(); } catch (Exception ignored) {}
        pct = BatteryParser.fromText(all.toString());
        if (pct != null) {
            AlertEngine.process(this, pct, pkg.contains("launcher") ? "OLA widget accessibility" : "OLA app accessibility");
        }
    }

    private boolean isAllowedSurface(String pkg) {
        if (pkg.contains("ola")) return true;
        return pkg.contains("launcher") || pkg.contains("oplus") || pkg.contains("coloros") ||
                pkg.contains("oneplus") || pkg.contains("nexuslauncher") || pkg.contains("systemui");
    }

    private void collect(AccessibilityNodeInfo n, StringBuilder out, int depth) {
        if (n == null || depth > 40 || out.length() > 12000) return;
        CharSequence t = n.getText();
        if (t != null) out.append(t).append(' ');
        CharSequence d = n.getContentDescription();
        if (d != null) out.append(d).append(' ');
        for (int i = 0; i < n.getChildCount(); i++) {
            AccessibilityNodeInfo c = n.getChild(i);
            if (c != null) {
                collect(c, out, depth + 1);
                try { c.recycle(); } catch (Exception ignored) {}
            }
        }
    }

    @Override public void onInterrupt() { }
}
