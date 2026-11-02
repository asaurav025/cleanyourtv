package dev.asaurav.myhome;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The room and the market strip, on one background thread so the ESP boards see one request at a time (the strips
 * are BLE behind an ESP32-C3: every colour is a radio write). Rules:
 * - it never switches a light on by itself, except the opening-bell flash, which ESPHome returns to the old state;
 * - a light the family switched off stays off: sync, film and night only touch lights that are on.
 */
final class Room {
    private static final long FOCUS_SETTLE_MS = 400;
    private static final long MIN_STRIP_GAP_MS = 700;
    private static final long STATE_TTL_MS = 30_000;
    private static final long QUOTES_TTL_MS = 60_000;

    private static Room instance;

    private final Context ctx;
    private final Handler bg;
    private Config cfg = Config.defaults();
    private long cfgStamp = -2;

    private int pendingColor = -1;
    private int sentColor = -1;
    private long lastStripWrite;
    private boolean film;
    /** The fairy lights' brightness (0-255) before a film dimmed them, by entity; restored on Home. */
    private final Map<String, Integer> savedDim = new HashMap<>();
    private final Map<String, Boolean> onCache = new HashMap<>();
    private final Map<String, Long> onCacheAt = new HashMap<>();

    private volatile String line = "";
    private long quotesAt;

    static synchronized Room get(Context c) {
        if (instance == null) instance = new Room(c.getApplicationContext());
        return instance;
    }

    private Room(Context ctx) {
        this.ctx = ctx;
        HandlerThread t = new HandlerThread("room");
        t.start();
        bg = new Handler(t.getLooper());
    }

    // ---- events from Home (HomeProvider), any thread ----------------------------------------------------

    void focus(int rgb) {
        bg.post(() -> {
            pendingColor = rgb;
            bg.removeCallbacks(applyFocus);
            bg.postDelayed(applyFocus, FOCUS_SETTLE_MS);
        });
    }

    void launched(String pkg) {
        bg.post(() -> {
            reload();
            if (pkg == null || !cfg.movieApps.contains(pkg) || film) return;
            film = true;
            bg.removeCallbacks(applyFocus);
            for (Config.Light l : cfg.dimLights()) {
                JSONObject s = Esp.get(l.host, "light", l.entity);
                if (s == null || !"ON".equalsIgnoreCase(s.optString("state"))) continue;
                savedDim.put(l.entity, s.optInt("brightness", 255));
                Esp.post(l.host, "light", l.entity, "turn_on", "brightness=" + Scene.brightness(cfg.movieDim) + "&transition=2");
            }
            for (Config.Light l : cfg.rgbLights()) {
                if (stripOn(l)) Esp.post(l.host, "light", l.entity, "turn_on",
                        Esp.rgbQuery(cfg.movieStripColor, Scene.brightness(cfg.movieStrip)));
            }
            sentColor = -1;
        });
    }

    void home() {
        bg.post(() -> {
            reload();
            Bell.schedule(ctx, cfg);
            ZonedDateTime now = ZonedDateTime.now(Market.IST);
            if (film) {
                film = false;
                for (Config.Light l : cfg.dimLights()) {
                    Integer b = savedDim.remove(l.entity);
                    if (b != null) Esp.post(l.host, "light", l.entity, "turn_on", "brightness=" + b + "&transition=2");
                }
            }
            if (night(now)) {
                for (Config.Light l : cfg.dimLights()) {
                    if (Esp.isOn(l.host, "light", l.entity)) Esp.post(l.host, "light", l.entity, "turn_on",
                            "brightness=" + Scene.brightness(cfg.nightDim) + "&transition=3");
                }
                for (Config.Light l : cfg.rgbLights()) {
                    if (stripOn(l)) Esp.post(l.host, "light", l.entity, "turn_on",
                            Esp.rgbQuery(cfg.nightColor, Scene.brightness(cfg.nightBrightness)));
                }
                sentColor = -1;
            } else if (diwaliEvening(now)) {
                for (Config.Light l : cfg.dimLights()) {
                    if (!l.effects.contains(cfg.diwaliEffect)) continue;
                    JSONObject s = Esp.get(l.host, "light", l.entity);
                    if (s != null && "ON".equalsIgnoreCase(s.optString("state"))
                            && !cfg.diwaliEffect.equals(s.optString("effect"))) {
                        Esp.post(l.host, "light", l.entity, "turn_on", "effect=" + enc(cfg.diwaliEffect));
                    }
                }
            }
            refreshLine(true);
        });
    }

    /** The cached line for Home; refreshes it in the background when it is stale. Never blocks. */
    String line() {
        bg.post(() -> refreshLine(false));
        return line;
    }

    String greeting() {
        Config c = cfg;
        return night(ZonedDateTime.now(Market.IST)) ? c.nightGreeting : "";
    }

    /** The opening bell or flat-by: one flash of the fairy lights; ESPHome puts them back as they were. */
    void pulse() {
        bg.post(() -> {
            reload();
            if (!cfg.bellPulse) return;
            for (Config.Light l : cfg.dimLights()) {
                Esp.post(l.host, "light", l.entity, "turn_on", "brightness=255&flash=2");
            }
        });
    }

    // ---- on the room thread ---------------------------------------------------------------------------

    private final Runnable applyFocus = new Runnable() {
        @Override
        public void run() {
            reload();
            if (!cfg.sync || film || night(ZonedDateTime.now(Market.IST)) || pendingColor < 0) return;
            int led = Scene.ledColor(pendingColor);
            if (led == sentColor) return;
            long wait = MIN_STRIP_GAP_MS - (SystemClock.uptimeMillis() - lastStripWrite);
            if (wait > 0) {
                bg.postDelayed(this, wait);
                return;
            }
            for (Config.Light l : cfg.rgbLights()) {
                if (stripOn(l)) Esp.post(l.host, "light", l.entity, "turn_on", Esp.rgbQuery(led, Scene.brightness(cfg.syncBrightness)));
            }
            sentColor = led;
            lastStripWrite = SystemClock.uptimeMillis();
        }
    };

    private void refreshLine(boolean force) {
        ZonedDateTime now = ZonedDateTime.now(Market.IST);
        Market.Phase phase = Market.phase(now, cfg.flatBy);
        if (!cfg.market || phase == Market.Phase.CLOSED) {
            line = "";
            return;
        }
        long t = SystemClock.uptimeMillis();
        if (!force && t - quotesAt < QUOTES_TTL_MS && !line.isEmpty()) return;
        quotesAt = t;
        List<Market.Quote> q = phase == Market.Phase.PRE ? java.util.Collections.emptyList() : Quotes.fetch(cfg.symbols);
        line = Market.line(q, phase, cfg.flatBy);
    }

    /** A strip counts as on when its light is on and, if it has one, its power switch too. Cached briefly. */
    private boolean stripOn(Config.Light l) {
        long t = SystemClock.uptimeMillis();
        Long at = onCacheAt.get(l.entity);
        if (at != null && t - at < STATE_TTL_MS) return Boolean.TRUE.equals(onCache.get(l.entity));
        boolean on = l.power.isEmpty() ? Esp.isOn(l.host, "light", l.entity) : Esp.isOn(l.host, "switch", l.power);
        onCache.put(l.entity, on);
        onCacheAt.put(l.entity, t);
        return on;
    }

    void forgetStates() {
        bg.post(() -> {
            onCache.clear();
            onCacheAt.clear();
        });
    }

    private boolean night(ZonedDateTime now) {
        return Scene.inWindow(now.toLocalTime(), cfg.nightFrom, cfg.nightTo);
    }

    private boolean diwaliEvening(ZonedDateTime now) {
        LocalDate d = now.toLocalDate();
        return !cfg.diwaliEffect.isEmpty() && Scene.inRange(d, cfg.diwaliFrom, cfg.diwaliTo)
                && !now.toLocalTime().isBefore(cfg.diwaliEvening);
    }

    private void reload() {
        File f = new File(ctx.getExternalFilesDir(null), Config.FILE_NAME);
        long stamp = f.isFile() ? f.lastModified() ^ f.length() : -1;
        if (stamp == cfgStamp) return;
        cfgStamp = stamp;
        String text = null;
        try {
            if (stamp != -1) text = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            // an unreadable file is the same as none
        }
        cfg = Config.parse(text);
        onCache.clear();
        onCacheAt.clear();
    }

    /** myhome.json as it is on disk now, for screens that need it before the room thread has read it. */
    static Config readConfig(Context ctx) {
        File f = new File(ctx.getExternalFilesDir(null), Config.FILE_NAME);
        try {
            return Config.parse(f.isFile() ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8) : null);
        } catch (Exception e) {
            return Config.defaults();
        }
    }

    private static String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (Exception e) {
            return s;
        }
    }
}
