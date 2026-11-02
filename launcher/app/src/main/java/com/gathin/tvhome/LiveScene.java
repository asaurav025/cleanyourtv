package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.DisplayMetrics;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * A living scene behind Home (theme key live_scene), after Lumen's: the wallpaper re-lit for dawn, day, dusk and night
 * and for the weather, with weather and life animated over it (SceneView, Ambient and Fireflies, adapted from Lumen,
 * MIT, launcher/LUMEN-LICENSE-MIT.txt). The scene, "dusk/rain", comes from the paired app (paired_app), which has
 * the sunrise, sunset and the weather; without one the clock picks the phase and the weather is clear.
 * The light is a colour filter on the wallpaper view, so nothing is copied or re-decoded when it changes; the
 * animation redraws at about 30 fps only while Home is on screen.
 */
final class LiveScene {
    private final Context ctx;
    private final FrameLayout root;
    private final ImageView wallpaper;
    private final View shade;
    /** A small software copy of the wallpaper: Fireflies reads its pixels to stay over the dark forest. */
    private final Bitmap sample;
    private View stack;
    private String key = "";

    /** Null when the theme shows no wallpaper picture: there is nothing to light. */
    static LiveScene create(Context ctx, FrameLayout root, ImageView wallpaper, Config cfg) {
        if (wallpaper == null || wallpaper.getParent() != root) return null;
        File file = cfg.wallpaper != null ? cfg.wallpaper : cfg.wallpapers.isEmpty() ? null : cfg.wallpapers.get(0);
        return new LiveScene(ctx, root, wallpaper, file);
    }

    private LiveScene(Context ctx, FrameLayout root, ImageView wallpaper, File file) {
        this.ctx = ctx;
        this.root = root;
        this.wallpaper = wallpaper;
        Bitmap s = null;
        if (file != null) {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inSampleSize = 8;
            o.inPreferredConfig = Bitmap.Config.RGB_565;
            try {
                s = BitmapFactory.decodeFile(file.getPath(), o);
            } catch (Throwable ignored) {
                s = null;
            }
        }
        sample = s;
        shade = new View(ctx);
        shade.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        shade.setFocusable(false);
        root.addView(shade, root.indexOfChild(wallpaper) + 1, new FrameLayout.LayoutParams(-1, -1));
    }

    /** "phase/weather" from the paired app, or empty for the clock's phase in clear weather. */
    void set(String scene) {
        if (scene == null || scene.indexOf('/') < 1) scene = phaseAt(LocalTime.now()) + "/clear";
        if (scene.equals(key)) return;
        key = scene;
        String phase = scene.substring(0, scene.indexOf('/'));
        String weather = scene.substring(scene.indexOf('/') + 1);
        wallpaper.setColorFilter(new ColorMatrixColorFilter(light(phase, weather)));
        shade.setBackground(shade(phase));
        if (stack != null) root.removeView(stack);
        stack = new SceneView.Stack(ctx, layersFor(phase, weather, season(LocalDate.now())), sample);
        stack.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        root.addView(stack, root.indexOfChild(shade) + 1, new FrameLayout.LayoutParams(-1, -1));
    }

    /** Home is back on screen: the animation stopped with the last frame drawn, so start it again. */
    void resume() {
        if (stack != null) stack.invalidate();
    }

    /** Lumen's clock fallback: 45 minutes either side of 06:30 and 18:00 are dawn and dusk. */
    static String phaseAt(LocalTime t) {
        int now = t.getHour() * 60 + t.getMinute(), rise = 6 * 60 + 30, set = 18 * 60;
        if (Math.abs(now - rise) <= 45) return "dawn";
        if (Math.abs(now - set) <= 45) return "dusk";
        return now > rise && now < set ? "day" : "night";
    }

    static String season(LocalDate d) {
        int m = d.getMonthValue();
        if (m >= 3 && m <= 5) return "spring";
        if (m >= 6 && m <= 8) return "summer";
        if (m >= 9 && m <= 11) return "autumn";
        return "winter";
    }

    /** Lumen's layers: weather first, then life and season over it. */
    static List<Integer> layersFor(String phase, String weather, String season) {
        List<Integer> l = new ArrayList<>();
        boolean night = phase.equals("night"), day = phase.equals("day");
        boolean calm = weather.equals("clear") || weather.equals("partly");
        switch (weather) {
            case "snow": l.add(SceneView.SNOW); break;
            case "rain": l.add(SceneView.RAIN); break;
            case "storm": l.add(SceneView.STORM); break;
            case "fog": l.add(SceneView.FOG); if (night) l.add(SceneView.FIREFLIES); break;
            case "partly": case "cloudy": l.add(SceneView.CLOUDS); break;
            default: break;
        }
        if (calm) {
            if (night) {
                if (!season.equals("winter")) l.add(SceneView.FIREFLIES);
                l.add(SceneView.STARS);
            } else {
                if (day && weather.equals("clear")) l.add(SceneView.SUNBEAMS);
                l.add(SceneView.MOTES);
                l.add(SceneView.BIRDS);
                if (season.equals("autumn")) l.add(SceneView.LEAVES);
                if (season.equals("spring")) l.add(SceneView.PETALS);
            }
        }
        return l;
    }

    /** Lumen's grading: saturation, then a scale and lift per channel, for the hour and then the weather. */
    static ColorMatrix light(String phase, String weather) {
        float sat;
        float[] m;
        switch (phase) {
            case "dawn": sat = 0.5f; m = new float[] {0.86f, 0.78f, 0.84f, 14, 6, 14}; break;
            case "dusk": sat = 0.45f; m = new float[] {0.74f, 0.60f, 0.72f, 12, 4, 20}; break;
            case "night": sat = 0.30f; m = new float[] {0.24f, 0.30f, 0.46f, 2, 6, 18}; break;
            default: sat = 0.70f; m = new float[] {0.78f, 0.84f, 0.90f, 0, 2, 8}; break;
        }
        switch (weather) {
            case "snow":
                sat = 0.15f;
                m = phase.equals("night") ? new float[] {0.36f, 0.34f, 0.46f, 4, 4, 14}
                        : new float[] {0.64f, 0.62f, 0.68f, 8, 6, 16};
                break;
            case "cloudy":
            case "partly": {
                boolean cloudy = weather.equals("cloudy");
                sat *= cloudy ? 0.55f : 0.8f;
                for (int i = 0; i < 3; i++) m[i] *= cloudy ? 0.86f : 0.94f;
                break;
            }
            case "rain":
                sat *= 0.45f;
                for (int i = 0; i < 3; i++) m[i] *= 0.74f;
                m[5] += 10;
                break;
            case "storm":
                sat *= 0.35f;
                for (int i = 0; i < 3; i++) m[i] *= 0.56f;
                m[5] += 12;
                break;
            case "fog":
                sat *= 0.4f;
                for (int i = 0; i < 3; i++) {
                    m[i] *= 0.7f;
                    m[i + 3] += phase.equals("night") ? 18 : 60;
                }
                break;
            default:
                break;
        }
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(sat);
        cm.postConcat(new ColorMatrix(new float[] {
                m[0], 0, 0, 0, m[3],
                0, m[1], 0, 0, m[4],
                0, 0, m[2], 0, m[5],
                0, 0, 0, 1, 0}));
        return cm;
    }

    /** A warm glow high in the sky at dawn and dusk, a darker top for the clock and a darker bottom for the apps. */
    private Drawable shade(String phase) {
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int w = dm.widthPixels, h = dm.heightPixels;
        List<Drawable> layers = new ArrayList<>();
        if (phase.equals("dawn") || phase.equals("dusk")) {
            int glow = phase.equals("dawn") ? 0x40FFB89A : 0x40FF9A6C;
            GradientDrawable g = new GradientDrawable();
            g.setGradientType(GradientDrawable.RADIAL_GRADIENT);
            g.setColors(new int[] {glow, glow & 0x00FFFFFF});
            g.setGradientCenter(0.72f, 0.18f);
            g.setGradientRadius(w * 0.55f);
            layers.add(g);
        }
        GradientDrawable top = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0x66000000, 0});
        GradientDrawable bottom = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0, 0x8C000000});
        layers.add(top);
        layers.add(bottom);
        LayerDrawable ld = new LayerDrawable(layers.toArray(new Drawable[0]));
        int n = layers.size();
        ld.setLayerInset(n - 2, 0, 0, 0, Math.round(h * 0.55f));
        ld.setLayerInset(n - 1, 0, Math.round(h * 0.55f), 0, 0);
        return ld;
    }
}
