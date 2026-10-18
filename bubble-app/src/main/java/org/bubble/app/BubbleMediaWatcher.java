package org.bubble.app;

import android.service.notification.NotificationListenerService;

/**
 * The key to the bubble's media sessions: Android lets an app read its user's media sessions only
 * through a notification listener it has approved (the engine approves this one inside the bubble,
 * bubble-smart-lock). The work itself is done by {@link SmartLock}, run by the bubble's always-on
 * VPN: Android starts that at every unlock, while this listener is not always bound again after
 * the bubble was locked (seen on the emulator). Approval, not binding, is what the sessions need.
 */
public final class BubbleMediaWatcher extends NotificationListenerService {

    @Override public void onListenerConnected() {
        // Outside the bubble it has no job.
        if (!BootReceiver.insideBubble()) requestUnbind();
    }
}
