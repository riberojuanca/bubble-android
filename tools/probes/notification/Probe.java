package org.bubble.notificationprobe;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.os.Bundle;
import android.os.IBinder;
import android.service.notification.StatusBarNotification;

/** Synthetic posting and privileged query probe. Intended only for the Android emulator. */
public final class Probe extends Instrumentation {
    private Bundle arguments;

    @Override public void onCreate(Bundle args) {
        super.onCreate(args);
        arguments = args == null ? new Bundle() : args;
        start();
    }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            String mode = arguments.getString("mode", "post");
            String marker = arguments.getString("marker", "bubble-test-default");
            if (!marker.startsWith("bubble-test-")) throw new IllegalArgumentException("Synthetic marker required");
            NotificationManager manager = getTargetContext().getSystemService(NotificationManager.class);
            if (mode.equals("post")) {
                manager.createNotificationChannel(new NotificationChannel("probe", "Privacy probe", NotificationManager.IMPORTANCE_DEFAULT));
                manager.notify(marker, 1, new Notification.Builder(getTargetContext(), "probe")
                        .setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(marker)
                        .setContentText("Synthetic privacy test; no personal data").build());
                result.putString("stream", "POSTED " + marker + "\n");
            } else if (mode.equals("cancel")) {
                manager.cancel(marker, 1);
                result.putString("stream", "CANCELED " + marker + "\n");
            } else if (mode.equals("query")) {
                IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                        .getMethod("getService", String.class).invoke(null, "notification");
                Object service = Class.forName("android.app.INotificationManager$Stub")
                        .getMethod("asInterface", IBinder.class).invoke(null, binder);
                Class<?> api = Class.forName("android.app.INotificationManager");
                StatusBarNotification[] active = (StatusBarNotification[]) api
                        .getMethod("getActiveNotificationsWithAttribution", String.class, String.class)
                        .invoke(service, getTargetContext().getPackageName(), null);
                StatusBarNotification[] history = (StatusBarNotification[]) api
                        .getMethod("getHistoricalNotificationsWithAttribution", String.class, String.class, int.class, boolean.class)
                        .invoke(service, getTargetContext().getPackageName(), null, 100, true);
                result.putString("stream", "ACTIVE_SYNTHETIC=" + count(active, marker)
                        + " HISTORY_SYNTHETIC=" + count(history, marker) + "\n");
            } else throw new IllegalArgumentException("Unknown mode");
            finish(Activity.RESULT_OK, result);
        } catch (Throwable failure) {
            result.putString("stream", "PROBE_FAILED " + android.util.Log.getStackTraceString(failure));
            finish(Activity.RESULT_CANCELED, result);
        }
    }

    private int count(StatusBarNotification[] notifications, String marker) {
        int count = 0;
        if (notifications != null) for (StatusBarNotification item : notifications) {
            if (getTargetContext().getPackageName().equals(item.getPackageName()) && marker.equals(item.getTag())) count++;
        }
        return count;
    }
}
