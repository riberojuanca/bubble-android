package org.bubble.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.IBinder;

/**
 * Runs once after boot: reconnects over wireless debugging, re-applies the firewall (if on) and
 * relaunches the shell-side guard, which also keeps Google services out of the bubble. Short-lived;
 * stops itself when done.
 */
public final class GuardService extends Service {

    private static final String CHANNEL = "bubble-guard";

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(1, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        new Thread(() -> {
            try {
                new Controller(this).restore();
            } catch (Exception ignored) {
                // Next boot, or a manual open of the app, retries.
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE);
                stopSelf(startId);
            }
        }, "bubble-guard").start();
        return START_NOT_STICKY;
    }

    private Notification notification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Bubble",
                NotificationManager.IMPORTANCE_LOW));
        return new Notification.Builder(this, CHANNEL)
                .setContentTitle("Bubble")
                .setContentText(getString(R.string.guard_restoring))
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .build();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
