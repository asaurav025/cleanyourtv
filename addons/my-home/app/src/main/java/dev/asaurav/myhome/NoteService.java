package dev.asaurav.myhome;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

/**
 * Keeps this process awake for the phone's page. Android freezes a cached app, and a frozen NoteServer still accepts
 * connections but never answers, so without this a note can only be sent in the moments Home is calling the provider.
 * The server itself starts with the process (HomeProvider); this service only keeps the process from being frozen.
 */
public final class NoteService extends Service {
    private static final String CHANNEL = "note";
    private static final int ID = 1;

    /** A start from the background needs the exemption install.sh sets up; without it the note is only best effort. */
    static void start(Context ctx) {
        try {
            ctx.startForegroundService(new Intent(ctx, NoteService.class));
        } catch (RuntimeException e) {
            Log.w("MyHome", "note service did not start", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Note", NotificationManager.IMPORTANCE_MIN));
        Notification n = new Notification.Builder(this, CHANNEL)
                .setSmallIcon(R.drawable.ic_foreground)
                .setContentTitle("Binge Box note")
                .setOngoing(true)
                .build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(ID, n);
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
