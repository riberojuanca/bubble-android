package org.bubble.notificationprobe;

import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Records only synthetic notifications posted by this probe, never other apps' content. */
public final class ProbeListener extends NotificationListenerService {
    @Override public void onNotificationPosted(StatusBarNotification notification) {
        if (!getPackageName().equals(notification.getPackageName())) return;
        String tag = notification.getTag();
        if (tag == null || !tag.startsWith("bubble-test-")) return;
        getSharedPreferences("received", MODE_PRIVATE).edit()
                .putInt(tag, notification.getUid() / 100000).commit();
    }
}
