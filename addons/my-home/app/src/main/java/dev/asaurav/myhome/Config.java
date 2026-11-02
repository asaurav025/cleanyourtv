package dev.asaurav.myhome;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * myhome.json, pushed by install.sh into the app's external files directory. Every key is optional; a broken file is
 * the same as an empty one, so a typo never breaks Home, it just leaves the room alone. Pure Java, unit tested.
 */
final class Config {
    static final String FILE_NAME = "myhome.json";

    /** One ESPHome light. kind "rgb" follows the screen; kind "dim" (the fairy lights) dims for films and pulses. */
    static final class Light {
        final String name, host, entity, kind, power;
        final List<String> effects;

        Light(String name, String host, String entity, String kind, String power, List<String> effects) {
            this.name = name;
            this.host = host;
            this.entity = entity;
            this.kind = kind;
            this.power = power;
            this.effects = effects;
        }

        boolean rgb() {
            return "rgb".equals(kind);
        }
    }

    static final class Symbol {
        final String yahoo, label;

        Symbol(String yahoo, String label) {
            this.yahoo = yahoo;
            this.label = label;
        }
    }

    final List<Light> lights = new ArrayList<>();

    boolean sync = true;
    int syncBrightness = 60;

    final Set<String> movieApps = new HashSet<>();
    int movieDim = 10;
    int movieStrip = 15;
    int movieStripColor = 0xFF8A3D;

    LocalTime nightFrom = LocalTime.of(23, 0), nightTo = LocalTime.of(6, 0);
    int nightColor = 0xFFA757;
    int nightBrightness = 20;
    int nightDim = 15;
    String nightGreeting = "";

    boolean market = true;
    final List<Symbol> symbols = new ArrayList<>();
    LocalTime flatBy = LocalTime.of(15, 10);
    boolean bellPulse = true;

    LocalDate diwaliFrom, diwaliTo;
    LocalTime diwaliEvening = LocalTime.of(17, 45);
    String diwaliEffect = "";

    static Config defaults() {
        Config c = new Config();
        Collections.addAll(c.movieApps, "com.netflix.ninja", "com.amazon.amazonvideo.livingroom",
                "com.google.android.youtube.tv", "in.startv.hotstar", "com.jio.media.ondemand", "com.disney.disneyplus");
        c.symbols.add(new Symbol("^NSEI", "NIFTY"));
        c.symbols.add(new Symbol("^NSEBANK", "BANKNIFTY"));
        return c;
    }

    static Config parse(String text) {
        Config c = defaults();
        if (text == null || text.trim().isEmpty()) return c;
        JSONObject o;
        try {
            o = new JSONObject(text);
        } catch (Exception e) {
            return c;
        }
        JSONArray lights = o.optJSONArray("lights");
        if (lights != null) {
            for (int i = 0; i < lights.length(); i++) {
                JSONObject l = lights.optJSONObject(i);
                if (l == null || l.optString("host").isEmpty() || l.optString("entity").isEmpty()) continue;
                List<String> effects = new ArrayList<>();
                JSONArray e = l.optJSONArray("effects");
                if (e != null) for (int j = 0; j < e.length(); j++) effects.add(e.optString(j));
                c.lights.add(new Light(l.optString("name", l.optString("entity")), l.optString("host"),
                        l.optString("entity"), l.optString("kind", "dim"), l.optString("power", ""), effects));
            }
        }
        JSONObject sync = o.optJSONObject("sync");
        if (sync != null) {
            c.sync = sync.optBoolean("enabled", c.sync);
            c.syncBrightness = pct(sync, "brightness", c.syncBrightness);
        }
        JSONObject movie = o.optJSONObject("movie");
        if (movie != null) {
            JSONArray apps = movie.optJSONArray("apps");
            if (apps != null) {
                c.movieApps.clear();
                for (int i = 0; i < apps.length(); i++) c.movieApps.add(apps.optString(i));
            }
            c.movieDim = pct(movie, "fairy", c.movieDim);
            c.movieStrip = pct(movie, "strip_brightness", c.movieStrip);
            c.movieStripColor = color(movie, "strip_color", c.movieStripColor);
        }
        JSONObject night = o.optJSONObject("night");
        if (night != null) {
            c.nightFrom = time(night, "from", c.nightFrom);
            c.nightTo = time(night, "to", c.nightTo);
            c.nightColor = color(night, "color", c.nightColor);
            c.nightBrightness = pct(night, "strip_brightness", c.nightBrightness);
            c.nightDim = pct(night, "fairy", c.nightDim);
            c.nightGreeting = night.optString("greeting", c.nightGreeting).trim();
        }
        JSONObject market = o.optJSONObject("market");
        if (market != null) {
            c.market = market.optBoolean("enabled", c.market);
            c.flatBy = time(market, "flat_by", c.flatBy);
            c.bellPulse = market.optBoolean("bell_pulse", c.bellPulse);
            JSONArray syms = market.optJSONArray("symbols");
            if (syms != null) {
                c.symbols.clear();
                for (int i = 0; i < syms.length() && c.symbols.size() < 5; i++) {
                    JSONObject s = syms.optJSONObject(i);
                    if (s != null && !s.optString("yahoo").isEmpty()) {
                        c.symbols.add(new Symbol(s.optString("yahoo"), s.optString("label", s.optString("yahoo"))));
                    } else if (!syms.optString(i).isEmpty() && s == null) {
                        String y = syms.optString(i);
                        c.symbols.add(new Symbol(y, y.replace(".NS", "")));
                    }
                }
            }
        }
        JSONObject diwali = o.optJSONObject("diwali");
        if (diwali != null) {
            c.diwaliFrom = date(diwali, "from");
            c.diwaliTo = date(diwali, "to");
            c.diwaliEvening = time(diwali, "evening", c.diwaliEvening);
            c.diwaliEffect = diwali.optString("effect", "").trim();
        }
        return c;
    }

    List<Light> rgbLights() {
        List<Light> out = new ArrayList<>();
        for (Light l : lights) if (l.rgb()) out.add(l);
        return out;
    }

    List<Light> dimLights() {
        List<Light> out = new ArrayList<>();
        for (Light l : lights) if (!l.rgb()) out.add(l);
        return out;
    }

    private static int pct(JSONObject o, String key, int fallback) {
        int v = o.optInt(key, fallback);
        return v < 0 || v > 100 ? fallback : v;
    }

    private static int color(JSONObject o, String key, int fallback) {
        int v = Scene.parseColor(o.optString(key, null));
        return v < 0 ? fallback : v;
    }

    private static LocalTime time(JSONObject o, String key, LocalTime fallback) {
        try {
            return o.has(key) ? LocalTime.parse(o.getString(key)) : fallback;
        } catch (Exception e) {
            return fallback;
        }
    }

    private static LocalDate date(JSONObject o, String key) {
        try {
            return o.has(key) ? LocalDate.parse(o.getString(key)) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
