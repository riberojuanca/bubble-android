package org.bubble.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Process;

/**
 * After boot (and after Bubble updates itself). Outside the bubble it starts {@link GuardService}.
 * Inside it, where Bubble's copy only runs the firewall, it restores the VPN with the saved list:
 * the profile's own BOOT_COMPLETED arrives each time the bubble is unlocked.
 */
public final class BootReceiver extends BroadcastReceiver {

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent == null ? null : intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            return;
        }
        if (insideBubble()) {
            hideLauncher(context);
            if (BubbleVpnService.configured(context)) {
                context.startForegroundService(new Intent(context, BubbleVpnService.class));
            }
        } else {
            context.startForegroundService(new Intent(context, GuardService.class));
        }
    }

    /**
     * Bubble's copy inside the bubble (or the Meta space) has nothing to show, so it removes its
     * launcher icons there, Bubble and Bubble Apps, and its widget (only for that profile). The shell cannot do it: Android 17 refuses component state
     * changes from the shell ("Shell cannot change component state").
     */
    static void hideLauncher(Context context) {
        for (Class<?> entry : new Class<?>[]{MainActivity.class, AppsGridActivity.class, AppsWidget.class}) {
            context.getPackageManager().setComponentEnabledSetting(
                    new android.content.ComponentName(context, entry),
                    android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    android.content.pm.PackageManager.DONT_KILL_APP);
        }
    }

    /** Bubble's copy inside the bubble runs as a profile user, never as the primary user 0. */
    static boolean insideBubble() {
        return !Process.myUserHandle().equals(android.os.UserHandle.getUserHandleForUid(0));
    }
}
