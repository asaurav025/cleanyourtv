package dev.asaurav.myhome;

import java.time.LocalDate;
import java.time.LocalTime;

/** Pure helpers for the room: LED colours from app colours, time windows and date ranges. Unit tested. */
final class Scene {
    /** Warm white (about 2700 K on these strips) for grey or black app colours. */
    static final int WARM_WHITE = 0xFFB46B;

    private Scene() {}

    /**
     * A strip colour from an app's ambient colour (RGB, alpha ignored). The launcher's glow colours are dark and muted
     * on purpose; on an LED that reads as brown, so keep the hue, push saturation up and value to full. Greys and
     * near-blacks have no hue to keep and become warm white.
     */
    static int ledColor(int rgb) {
        float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float s = max == 0 ? 0 : delta / max;
        if (s < 0.15f || max < 0.06f) return WARM_WHITE;
        float h;
        if (max == r) h = ((g - b) / delta) % 6f;
        else if (max == g) h = (b - r) / delta + 2f;
        else h = (r - g) / delta + 4f;
        h *= 60f;
        if (h < 0) h += 360f;
        return hsv(h, Math.max(s, 0.8f), 1f);
    }

    static int hsv(float h, float s, float v) {
        float c = v * s, x = c * (1 - Math.abs((h / 60f) % 2 - 1)), m = v - c;
        float r, g, b;
        if (h < 60) { r = c; g = x; b = 0; }
        else if (h < 120) { r = x; g = c; b = 0; }
        else if (h < 180) { r = 0; g = c; b = x; }
        else if (h < 240) { r = 0; g = x; b = c; }
        else if (h < 300) { r = x; g = 0; b = c; }
        else { r = c; g = 0; b = x; }
        return (Math.round((r + m) * 255) << 16) | (Math.round((g + m) * 255) << 8) | Math.round((b + m) * 255);
    }

    /** "#RRGGBB" or "#AARRGGBB" to 0xRRGGBB; -1 when it is not a colour. */
    static int parseColor(String s) {
        if (s == null) return -1;
        s = s.trim();
        if (!s.startsWith("#") || (s.length() != 7 && s.length() != 9)) return -1;
        try {
            return (int) (Long.parseLong(s.substring(1), 16) & 0xFFFFFF);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Whether t is inside [from, to), where a window like 23:00 to 06:00 wraps past midnight. */
    static boolean inWindow(LocalTime t, LocalTime from, LocalTime to) {
        if (from == null || to == null || from.equals(to)) return false;
        if (from.isBefore(to)) return !t.isBefore(from) && t.isBefore(to);
        return !t.isBefore(from) || t.isBefore(to);
    }

    /** Inclusive date range; false when either end is missing. */
    static boolean inRange(LocalDate d, LocalDate from, LocalDate to) {
        return from != null && to != null && !d.isBefore(from) && !d.isAfter(to);
    }

    /** 0-100 percent to ESPHome's 0-255 brightness. */
    static int brightness(int percent) {
        return Math.round(Math.max(0, Math.min(100, percent)) * 2.55f);
    }
}
