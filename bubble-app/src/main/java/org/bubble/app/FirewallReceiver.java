package org.bubble.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * Entry point for the engine into the bubble's firewall: the shell cannot start a non-exported
 * service, so it sends this broadcast instead. Protected by INTERACT_ACROSS_USERS, which the shell
 * holds and no app inside the bubble can, so no app there can lift the firewall. The engine
 * temp-allowlists Bubble first, which lets this receiver start the VPN service from background.
 */
public final class FirewallReceiver extends BroadcastReceiver {

    @Override public void onReceive(Context context, Intent intent) {
        BootReceiver.hideLauncher(context);
        Intent start = new Intent(context, BubbleVpnService.class);
        if (intent.hasExtra(BubbleVpnService.EXTRA_ALLOWED)) {
            start.putExtra(BubbleVpnService.EXTRA_ALLOWED, intent.getStringExtra(BubbleVpnService.EXTRA_ALLOWED));
        }
        context.startForegroundService(start);
    }
}
