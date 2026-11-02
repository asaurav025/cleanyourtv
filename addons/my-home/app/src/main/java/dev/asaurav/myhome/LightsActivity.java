package dev.asaurav.myhome;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The Lights card: one row per light and four scenes, worked with the remote.
 * Scenes across the top; below them OK switches a light, ◀ ▶ dims it by 10 %, Menu cycles its effects.
 */
public final class LightsActivity extends Activity {
    private final Handler main = new Handler(Looper.getMainLooper());
    private Handler bg;
    private HandlerThread thread;
    private Config cfg;
    private final List<Row> rows = new ArrayList<>();
    private TextView status;

    private final class Row {
        final Config.Light light;   // null for a scene
        final String scene;
        final TextView view;
        boolean on;
        int pct;
        String effect = "";

        Row(Config.Light light, String scene, TextView view) {
            this.light = light;
            this.scene = scene;
            this.view = view;
        }

        void render() {
            if (light == null) {
                view.setText(scene);
                return;
            }
            String state = on ? "On  ·  " + pct + "%" + (effect.isEmpty() || "None".equals(effect) ? "" : "  ·  " + effect) : "Off";
            view.setText(light.name + "\n" + state);
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        thread = new HandlerThread("lights");
        thread.start();
        bg = new Handler(thread.getLooper());
        cfg = Room.readConfig(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B0E14);
        int pad = dp(56);
        root.setPadding(pad, dp(40), pad, dp(32));

        TextView title = text("Binge Box  ·  Lights", 34, Color.WHITE, Typeface.create("sans-serif-light", Typeface.NORMAL));
        root.addView(title);
        status = text("OK switches  ·  ◀ ▶ dims  ·  Menu changes the effect", 15, 0xFF8B95A5, Typeface.DEFAULT);
        status.setPadding(0, dp(4), 0, dp(24));
        root.addView(status);

        LinearLayout scenesRow = new LinearLayout(this);
        scenesRow.setOrientation(LinearLayout.HORIZONTAL);
        root.addView(scenesRow);
        LinearLayout lightsCol = column();
        lightsCol.setPadding(0, dp(20), 0, 0);
        root.addView(lightsCol);

        if (cfg.lights.isEmpty()) {
            status.setText("No lights yet: push myhome.json with addons/my-home/install.sh");
        }
        for (String s : new String[] {"Film", "Night", "Bright", "All off"}) rows.add(addRow(scenesRow, null, s));
        for (Config.Light l : cfg.lights) rows.add(addRow(lightsCol, l, null));
        setContentView(root);
        if (rows.size() > 4) rows.get(4).view.requestFocus();
        else if (!rows.isEmpty()) rows.get(0).view.requestFocus();
        refreshAll();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        thread.quitSafely();
        Room.get(this).forgetStates();
    }

    private Row addRow(LinearLayout col, Config.Light l, String scene) {
        TextView v = text("", 22, Color.WHITE, Typeface.create("sans-serif-medium", Typeface.NORMAL));
        v.setFocusable(true);
        v.setPadding(dp(22), dp(14), dp(22), dp(14));
        v.setLineSpacing(dp(2), 1f);
        GradientDrawable bgd = new GradientDrawable();
        bgd.setCornerRadius(dp(16));
        bgd.setColor(0x14FFFFFF);
        v.setBackground(bgd);
        Row r = new Row(l, scene, v);
        v.setOnFocusChangeListener((view, focused) -> {
            bgd.setColor(focused ? 0x40FFFFFF : 0x14FFFFFF);
            bgd.setStroke(focused ? dp(2) : 0, 0x80FFFFFF);
            view.animate().scaleX(focused ? 1.03f : 1f).scaleY(focused ? 1.03f : 1f).setDuration(150).start();
        });
        v.setOnClickListener(view -> ok(r));
        v.setOnKeyListener((view, code, e) -> {
            if (e.getAction() != KeyEvent.ACTION_DOWN || r.light == null) return false;
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) { dim(r, -10); return true; }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { dim(r, +10); return true; }
            if (code == KeyEvent.KEYCODE_MENU) { nextEffect(r); return true; }
            return false;
        });
        boolean isScene = l == null;
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(isScene ? -2 : -1, -2);
        if (isScene) lp.rightMargin = dp(14);
        else lp.bottomMargin = dp(12);
        col.addView(v, lp);
        r.render();
        return r;
    }

    private void ok(Row r) {
        if (r.light == null) {
            scene(r.scene);
            return;
        }
        Config.Light l = r.light;
        bg.post(() -> {
            if (!l.power.isEmpty()) Esp.post(l.host, "switch", l.power, "toggle", null);
            else Esp.post(l.host, "light", l.entity, "toggle", null);
            read(r);
        });
    }

    private void dim(Row r, int delta) {
        Config.Light l = r.light;
        int target = Math.max(5, Math.min(100, r.pct + delta));
        r.pct = target;
        r.render();
        bg.removeCallbacksAndMessages(r);
        bg.postAtTime(() -> {
            Esp.post(l.host, "light", l.entity, "turn_on", "brightness=" + Scene.brightness(target) + "&transition=0");
            read(r);
        }, r, android.os.SystemClock.uptimeMillis() + 250);
    }

    private void nextEffect(Row r) {
        Config.Light l = r.light;
        if (l.effects.isEmpty()) return;
        List<String> cycle = new ArrayList<>();
        cycle.add("None");
        cycle.addAll(l.effects);
        int i = cycle.indexOf(r.effect.isEmpty() ? "None" : r.effect);
        String next = cycle.get((i + 1) % cycle.size());
        bg.post(() -> {
            Esp.post(l.host, "light", l.entity, "turn_on", "effect=" + next.replace(" ", "%20"));
            read(r);
        });
    }

    private void scene(String name) {
        status.setText(name + "…");
        bg.post(() -> {
            for (Config.Light l : cfg.lights) {
                switch (name) {
                    case "All off":
                        if (!l.power.isEmpty()) Esp.post(l.host, "switch", l.power, "turn_off", null);
                        else Esp.post(l.host, "light", l.entity, "turn_off", "transition=2");
                        break;
                    case "Bright":
                        if (!l.power.isEmpty()) Esp.post(l.host, "switch", l.power, "turn_on", null);
                        Esp.post(l.host, "light", l.entity, "turn_on", l.rgb()
                                ? Esp.rgbQuery(Scene.WARM_WHITE, 255) : "brightness=255&effect=None&transition=1");
                        break;
                    case "Night":
                        if (!l.power.isEmpty()) Esp.post(l.host, "switch", l.power, "turn_on", null);
                        Esp.post(l.host, "light", l.entity, "turn_on", l.rgb()
                                ? Esp.rgbQuery(cfg.nightColor, Scene.brightness(cfg.nightBrightness))
                                : "brightness=" + Scene.brightness(cfg.nightDim) + "&effect=None&transition=3");
                        break;
                    default: // Film
                        if (!l.power.isEmpty()) Esp.post(l.host, "switch", l.power, "turn_on", null);
                        Esp.post(l.host, "light", l.entity, "turn_on", l.rgb()
                                ? Esp.rgbQuery(cfg.movieStripColor, Scene.brightness(cfg.movieStrip))
                                : "brightness=" + Scene.brightness(cfg.movieDim) + "&effect=None&transition=2");
                }
            }
            Room.get(this).forgetStates();
            main.post(() -> status.setText(name + " is on"));
            for (Row r : rows) if (r.light != null) read(r);
        });
    }

    private void refreshAll() {
        bg.post(() -> {
            for (Row r : rows) if (r.light != null) read(r);
        });
    }

    /** On the background thread: read one light's state and show it. */
    private void read(Row r) {
        Config.Light l = r.light;
        JSONObject s = Esp.get(l.host, "light", l.entity);
        boolean on = s != null && "ON".equalsIgnoreCase(s.optString("state"));
        if (!l.power.isEmpty()) on = Esp.isOn(l.host, "switch", l.power);
        int pct = s == null ? r.pct : Math.round(s.optInt("brightness", 255) / 2.55f);
        String effect = s == null ? "" : s.optString("effect", "");
        boolean reachable = s != null;
        boolean finalOn = on;
        main.post(() -> {
            r.on = finalOn;
            r.pct = pct;
            r.effect = effect;
            r.render();
            if (!reachable) r.view.setText(l.name + "\nNot answering at " + l.host);
        });
    }

    private LinearLayout column() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        return c;
    }

    private TextView text(String s, int sp, int color, Typeface tf) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(tf);
        t.setGravity(Gravity.START);
        return t;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
