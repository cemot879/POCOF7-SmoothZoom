package com.ashitu.pocof7smoothzoom;

import android.app.Activity;
import android.os.Bundle;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Simple settings screen for the LSPosed module. */
public final class MainActivity extends Activity {
    private SharedPreferences prefs;
    private TextView alphaValue;
    private TextView maxValue;
    private TextView durationValue;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("POCO F7 Smooth Zoom");
        title.setTextSize(24f);
        title.setTextColor(Color.BLACK);
        root.addView(title, lp(-1, -2));

        TextView note = new TextView(this);
        note.setText("Pengaturan berlaku untuk Xiaomi Camera. Aktifkan modul dan scope com.android.camera di LSPosed.");
        note.setTextSize(14f);
        note.setPadding(0, dp(8), 0, dp(18));
        root.addView(note, lp(-1, -2));

        alphaValue = new TextView(this);
        root.addView(alphaValue, lp(-1, -2));
        SeekBar alpha = new SeekBar(this);
        alpha.setMax(35); // 0.15 .. 0.50
        float a = prefs.getFloat("alpha", 0.22f);
        alpha.setProgress(Math.round((a - 0.15f) * 100f));
        alpha.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                float v = 0.15f + p / 100f;
                v = Math.max(0.15f, Math.min(0.50f, v));
                alphaValue.setText("Smoothness: " + String.format(java.util.Locale.US, "%.2f", v) + "  (lebih tinggi = lebih responsif)");
                prefs.edit().putFloat("alpha", v).apply();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(alpha, lp(-1, -2));

        maxValue = new TextView(this);
        root.addView(maxValue, lp(-1, -2));
        SeekBar max = new SeekBar(this);
        max.setMax(98); // 2 .. 100
        float mz = prefs.getFloat("max_zoom", 20f);
        max.setProgress(Math.round(mz - 2f));
        max.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                float v = 2f + p;
                maxValue.setText("Max Zoom: " + format(v));
                prefs.edit().putFloat("max_zoom", v).apply();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(max, lp(-1, -2));

        durationValue = new TextView(this);
        root.addView(durationValue, lp(-1, -2));
        SeekBar duration = new SeekBar(this);
        duration.setMax(138); // 120 .. 1500 in 10ms steps
        long d = prefs.getLong("duration", 450L);
        duration.setProgress((int)Math.max(0, Math.min(138, (d - 120L) / 10L)));
        duration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                long v = 120L + p * 10L;
                durationValue.setText("Durasi animasi: " + v + " ms");
                prefs.edit().putLong("duration", v).apply();
            }
            public void onStartTrackingTouch(SeekBar s) {}
            public void onStopTrackingTouch(SeekBar s) {}
        });
        root.addView(duration, lp(-1, -2));

        TextView presets = new TextView(this);
        presets.setText("Tombol kamera: 0.6x  •  1x  •  2x  •  5x  •  10x\nTap sekali untuk zoom smooth. Max Zoom membatasi target tombol.");
        presets.setPadding(0, dp(18), 0, dp(18));
        root.addView(presets, lp(-1, -2));

        Button save = new Button(this);
        save.setText("Simpan");
        save.setOnClickListener(v -> finish());
        root.addView(save, lp(-1, -2));

        setContentView(root);
        // Refresh labels from the current values.
        alphaValue.setText("Smoothness: " + String.format(java.util.Locale.US, "%.2f", a) + "  (lebih tinggi = lebih responsif)");
        maxValue.setText("Max Zoom: " + format(mz));
        durationValue.setText("Durasi animasi: " + d + " ms");
    }

    private LinearLayout.LayoutParams lp(int w, int h) {
        return new LinearLayout.LayoutParams(w, h);
    }
    private int dp(int x) { return Math.round(x * getResources().getDisplayMetrics().density); }
    private String format(float z) { return z == (int)z ? ((int)z) + "x" : String.format(java.util.Locale.US, "%.1fx", z); }
}
