package org.bubble.notificationprobe;

import android.os.IBinder;
import android.service.notification.StatusBarNotification;

/** Emulator-only app_process probe. Run as UID 1000 to exercise the privileged query path. */
public final class QueryProbe {
    public static void main(String[] args) throws Exception {
        if (args.length < 2 || !args[1].startsWith("bubble-test-")) {
            throw new IllegalArgumentException("Expected caller package and synthetic marker");
        }
        IBinder binder = (IBinder) Class.forName("android.os.ServiceManager")
                .getMethod("getService", String.class).invoke(null, "notification");
        if (args.length == 3 && args[2].equals("dump")) {
            android.os.ParcelFileDescriptor[] pipe = android.os.ParcelFileDescriptor.createPipe();
            Thread writer = new Thread(() -> {
                try {
                    binder.dump(pipe[1].getFileDescriptor(), new String[0]);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    try { pipe[1].close(); } catch (java.io.IOException ignored) { }
                }
            });
            writer.start();
            int markers = 0;
            boolean denied = false;
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                    new android.os.ParcelFileDescriptor.AutoCloseInputStream(pipe[0])))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains(args[1])) markers++;
                    if (line.contains("Permission Denial")) denied = true;
                }
            }
            writer.join();
            System.out.println("DUMP_SYNTHETIC_LINES=" + markers + " DENIED=" + denied);
            return;
        }
        Object service = Class.forName("android.app.INotificationManager$Stub")
                .getMethod("asInterface", IBinder.class).invoke(null, binder);
        Class<?> api = Class.forName("android.app.INotificationManager");
        StatusBarNotification[] active = (StatusBarNotification[]) api
                .getMethod("getActiveNotificationsWithAttribution", String.class, String.class)
                .invoke(service, args[0], null);
        StatusBarNotification[] history = (StatusBarNotification[]) api
                .getMethod("getHistoricalNotificationsWithAttribution", String.class, String.class, int.class, boolean.class)
                .invoke(service, args[0], null, 100, true);
        System.out.println("ACTIVE_SYNTHETIC=" + count(active, args[1]) + " HISTORY_SYNTHETIC=" + count(history, args[1]));
    }

    private static int count(StatusBarNotification[] items, String marker) {
        int count = 0;
        if (items != null) for (StatusBarNotification item : items) {
            if (item.getPackageName().equals("org.bubble.notificationprobe") && marker.equals(item.getTag())) count++;
        }
        return count;
    }
}
