package com.naveenans.olabatteryalert;

import android.app.*;
import android.content.*;
import android.media.*;
import android.os.*;

public final class AlertEngine {
    public static final String CHANNEL_ALERT = "charge_limit_v2";
    public static final String CHANNEL_MONITOR = "monitor";
    private AlertEngine() {}

    public static void ensureChannels(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            NotificationChannel alert = new NotificationChannel(CHANNEL_ALERT, "Charge limit alarm", NotificationManager.IMPORTANCE_HIGH);
            alert.enableVibration(true);
            alert.setVibrationPattern(new long[]{0,350,120,350,120,700,180,350});
            alert.setDescription("Urgent alarm when the selected Ola charging limit is reached.");
            nm.createNotificationChannel(alert);
            NotificationChannel monitor = new NotificationChannel(CHANNEL_MONITOR, "Background battery monitor", NotificationManager.IMPORTANCE_LOW);
            monitor.setDescription("Keeps lightweight Ola widget monitoring active in the background.");
            nm.createNotificationChannel(monitor);
        }
    }

    public static void process(Context c, int pct, String source) {
        if (pct < 0 || pct > 100) return;
        SharedPreferences p = c.getSharedPreferences("prefs", Context.MODE_PRIVATE);
        int limit = p.getInt("limit", 80);
        int last = p.getInt("last_pct", -1);
        p.edit().putInt("last_pct", pct).putLong("last_update", System.currentTimeMillis()).putString("last_source", source).apply();
        if (pct >= limit && last < limit) sendLimitAlert(c, pct, limit, source);
    }

    public static void sendLimitAlert(Context c, int pct, int limit, String source) {
        ensureChannels(c);
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 11, open, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification n = new Notification.Builder(c, CHANNEL_ALERT)
                .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
                .setContentTitle("CHARGE LIMIT REACHED • " + pct + "%")
                .setContentText("Your " + limit + "% Ola battery target is reached. Unplug charger if required.")
                .setCategory(Notification.CATEGORY_ALARM)
                .setPriority(Notification.PRIORITY_MAX)
                .setContentIntent(pi)
                .setAutoCancel(true)
                .build();
        c.getSystemService(NotificationManager.class).notify(8080, n);
        vibrate(c);
        playBurglarAlarm();
    }

    private static void vibrate(Context c) {
        try {
            Vibrator v = (Vibrator)c.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                long[] p = {0,350,120,350,120,700,180,350,120,350};
                if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createWaveform(p, -1));
                else v.vibrate(p, -1);
            }
        } catch (Throwable ignored) {}
    }

    private static void playBurglarAlarm() {
        new Thread(() -> {
            final int rate = 16000;
            final double seconds = 4.2;
            final int count = (int)(rate * seconds);
            short[] pcm = new short[count];
            for (int i=0;i<count;i++) {
                double t = i / (double)rate;
                int phase = (int)(t / 0.18) % 2;
                double freq = phase == 0 ? 880.0 : 1180.0;
                double pulse = ((int)(t / 0.09) % 2 == 0) ? 1.0 : 0.75;
                pcm[i] = (short)(Math.sin(2.0 * Math.PI * freq * t) * 19000.0 * pulse);
            }
            AudioTrack track = null;
            try {
                AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
                AudioFormat fmt = new AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build();
                track = new AudioTrack.Builder().setAudioAttributes(attrs).setAudioFormat(fmt).setBufferSizeInBytes(pcm.length * 2).setTransferMode(AudioTrack.MODE_STATIC).build();
                track.write(pcm, 0, pcm.length);
                track.play();
                Thread.sleep((long)(seconds * 1000) + 250);
            } catch (Throwable ignored) {
            } finally {
                if (track != null) { try { track.stop(); } catch (Throwable ignored) {} try { track.release(); } catch (Throwable ignored) {} }
            }
        }, "charge-alarm").start();
    }
}
