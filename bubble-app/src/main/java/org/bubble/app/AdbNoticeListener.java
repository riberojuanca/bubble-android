package org.bubble.app;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Hides Android's "Wireless debugging connected" notification while Bubble itself is connected,
 * so each Bubble session does not pop it up. Only during Bubble's own sessions: at any other time
 * the notification keeps warning the user that someone is connected. The engine grants this
 * listener once (cmd notification allow_listener).
 */
public final class AdbNoticeListener extends NotificationListenerService {

    /** SystemMessage.NOTE_ADB_WIFI_ACTIVE, posted by the system ("android") while a device is connected. */
    private static final int NOTE_ADB_WIFI_ACTIVE = 62;
    private static final long SNOOZE_MS = 60_000;

    static final String COMPONENT = "org.bubble.app/org.bubble.app.AdbNoticeListener";

    @Override public void onListenerConnected() {
        StatusBarNotification[] active = getActiveNotifications();
        if (active != null) for (StatusBarNotification sbn : active) maybeHide(sbn);
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        maybeHide(sbn);
    }

    private void maybeHide(StatusBarNotification sbn) {
        if (!WirelessDebugging.inSession()) return;
        if (!"android".equals(sbn.getPackageName()) || sbn.getId() != NOTE_ADB_WIFI_ACTIVE) return;
        // Ongoing system notifications cannot be dismissed, but they can be snoozed. When the
        // session ends Android removes it anyway, so it does not come back.
        snoozeNotification(sbn.getKey(), SNOOZE_MS);
    }
}
