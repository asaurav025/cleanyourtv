package dev.asaurav.myhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.time.LocalTime;
import java.time.ZonedDateTime;

/**
 * The opening bell (09:15 IST) and flat-by (15:10 by default) on trading days: the fairy lights flash once. Inexact
 * alarms (a minute's window), no exact-alarm permission, and not RTC_WAKEUP: a TV in standby has nobody to tell.
 * Rescheduled each time Home comes back, so a reboot needs no boot receiver.
 */
public final class Bell extends BroadcastReceiver {
    private static final long WINDOW_MS = 60_000;

    static void schedule(Context ctx, Config cfg) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        ZonedDateTime now = ZonedDateTime.now(Market.IST);
        set(am, ctx, 1, Market.next(now, Market.OPEN), cfg.market && cfg.bellPulse);
        set(am, ctx, 2, Market.next(now, cfg.flatBy), cfg.market && cfg.bellPulse);
    }

    private static void set(AlarmManager am, Context ctx, int code, ZonedDateTime at, boolean on) {
        PendingIntent pi = PendingIntent.getBroadcast(ctx, code, new Intent(ctx, Bell.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        if (on) am.setWindow(AlarmManager.RTC, at.toInstant().toEpochMilli(), WINDOW_MS, pi);
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        // A late alarm (the TV was asleep) is not a bell. Android stretches an inexact window to 10 minutes,
        // so allow up to 12 after 09:15 or flat-by, and nothing before.
        Room room = Room.get(ctx);
        Config cfg = Room.readConfig(ctx);
        LocalTime t = ZonedDateTime.now(Market.IST).toLocalTime();
        boolean near = justAfter(t, Market.OPEN) || justAfter(t, cfg.flatBy);
        if (near && Market.tradingDay(ZonedDateTime.now(Market.IST).toLocalDate())) room.pulse();
        schedule(ctx, cfg);
    }

    static boolean justAfter(LocalTime t, LocalTime at) {
        int d = t.toSecondOfDay() - at.toSecondOfDay();
        return d >= -60 && d <= 12 * 60;
    }
}
