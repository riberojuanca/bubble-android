package org.bubble.app;

import android.app.Notification;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * In Bubble's copy inside the Meta space: removes the "Enable Google Play services" notice that
 * Facebook, Instagram or Edits post when they find Play Services disabled. Google's client library
 * posts it under the app's own package; every other notification is left alone. The engine
 * approves this listener in the Meta space only (BubbleEngine.metaNoticeListener).
 */
public final class GmsNoticeListener extends NotificationListenerService {

    /** The channel Google's client library creates for its availability notices. */
    private static final String CHANNEL = "com.google.android.gms.availability";
    /** GooglePlayServicesUtil.GMS_AVAILABILITY_NOTIFICATION_ID, posted without a tag. */
    private static final int NOTICE_ID = 10436;
    private static final long SNOOZE_MS = 24 * 60 * 60_000L;

    @Override public void onListenerConnected() {
        // Outside the profiles it has no job.
        if (!BootReceiver.insideBubble()) {
            requestUnbind();
            return;
        }
        StatusBarNotification[] active = getActiveNotifications();
        if (active != null) for (StatusBarNotification sbn : active) maybeHide(sbn);
    }

    @Override public void onNotificationPosted(StatusBarNotification sbn) {
        maybeHide(sbn);
    }

    private void maybeHide(StatusBarNotification sbn) {
        Notification n = sbn.getNotification();
        boolean notice = CHANNEL.equals(n.getChannelId()) || (sbn.getId() == NOTICE_ID && sbn.getTag() == null);
        if (!notice) return;
        // A notice the user cannot swipe away cannot be cancelled either, only snoozed; the app
        // posts it again on its next start and it is hidden again.
        if ((n.flags & (Notification.FLAG_ONGOING_EVENT | Notification.FLAG_NO_CLEAR)) != 0) {
            snoozeNotification(sbn.getKey(), SNOOZE_MS);
        } else {
            cancelNotification(sbn.getKey());
        }
    }
}
