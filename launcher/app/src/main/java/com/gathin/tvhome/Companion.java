package com.gathin.tvhome;

import android.content.Context;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;

import java.util.Locale;

/**
 * Optional companion app, named by a theme's {@code paired_app} (off when empty, the default). Home tells it
 * three things, the focused tile's glow colour, an app opened from Home and Home coming back, and shows the one line
 * it answers under the clock. Everything is a call() to the companion's ContentProvider ({@code <package>.home}):
 * the launcher itself still has no network access; the companion does whatever it does with these.
 * The provider must belong to the named package, so another app cannot pose as the companion.
 */
final class Companion {
    private static final int UP = 0xFF5BD68A;
    private static final int DOWN = 0xFFFF6B6B;
    private static final long FOCUS_COALESCE_MS = 120;
    private static final Object FOCUS_TOKEN = new Object();

    private static volatile String pkg = "";
    private static Handler bg;
    private static final Handler main = new Handler(Looper.getMainLooper());
    private static volatile int focusColor = 0;
    private static volatile String line = "";
    private static volatile String greeting = "";
    private static volatile String scene = "";

    private Companion() {}

    static synchronized void configure(String companionPackage) {
        pkg = companionPackage == null ? "" : companionPackage.trim();
        if (!pkg.isEmpty() && bg == null) {
            HandlerThread t = new HandlerThread("companion");
            t.start();
            bg = new Handler(t.getLooper());
        }
        if (pkg.isEmpty()) {
            line = "";
            greeting = "";
        }
    }

    static boolean enabled() {
        return !pkg.isEmpty();
    }

    /** Coalesced: a fast run of focus moves sends only the colour it settles on. */
    static void focus(Context ctx, int argb) {
        if (!enabled()) return;
        Context app = ctx.getApplicationContext();
        focusColor = argb;
        bg.removeCallbacksAndMessages(FOCUS_TOKEN);
        bg.postAtTime(() -> call(app, "focus", String.format(Locale.ROOT, "#%06X", focusColor & 0xFFFFFF)),
                FOCUS_TOKEN, android.os.SystemClock.uptimeMillis() + FOCUS_COALESCE_MS);
    }

    static void launched(Context ctx, String app) {
        if (!enabled() || app == null) return;
        Context c = ctx.getApplicationContext();
        bg.post(() -> call(c, "launch", app));
    }

    static void home(Context ctx) {
        if (!enabled()) return;
        Context c = ctx.getApplicationContext();
        bg.post(() -> call(c, "home", null));
    }

    /** Asks for the line and the greeting in the background, then runs {@code done} on the main thread. */
    static void refresh(Context ctx, Runnable done) {
        if (!enabled()) return;
        Context c = ctx.getApplicationContext();
        bg.post(() -> {
            Bundle b = call(c, "state", null);
            String l = b == null ? "" : b.getString("line", "");
            String g = b == null ? "" : b.getString("greeting", "");
            String s = b == null ? "" : b.getString("scene", "");
            main.post(() -> {
                line = l == null ? "" : l;
                greeting = g == null ? "" : g;
                scene = s == null ? "" : s;
                done.run();
            });
        });
    }

    static boolean hasLine() {
        return enabled() && !line.isEmpty();
    }

    /** "phase/weather" from the paired app, e.g. "dusk/rain", or empty. */
    static String scene() {
        return enabled() ? scene : "";
    }

    static String greeting() {
        return enabled() ? greeting : "";
    }

    /** The companion's line, with ▲ figures in green and ▼ in red, after {@code stats} when there is one. */
    static CharSequence styled(CharSequence stats) {
        String l = enabled() ? line : "";
        if (l.isEmpty()) return stats == null ? "" : stats;
        String head = stats == null || stats.length() == 0 ? "" : stats + "  ·  ";
        SpannableString s = new SpannableString(head + l);
        for (int i = 0; i < l.length(); i++) {
            char ch = l.charAt(i);
            if (ch != '▲' && ch != '▼') continue;
            int end = i + 1;
            while (end < l.length() && l.charAt(end) != ' ') end++;
            s.setSpan(new ForegroundColorSpan(ch == '▲' ? UP : DOWN), head.length() + i, head.length() + end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return s;
    }

    private static Bundle call(Context ctx, String method, String arg) {
        String p = pkg;
        if (p.isEmpty()) return null;
        String authority = p + ".home";
        try {
            ProviderInfo info = ctx.getPackageManager().resolveContentProvider(authority, 0);
            if (info == null || !p.equals(info.packageName)) return null;
            return ctx.getContentResolver().call(Uri.parse("content://" + authority), method, arg, null);
        } catch (Exception e) {
            return null;   // not installed, not running yet, or it threw: Home carries on without it
        }
    }
}
