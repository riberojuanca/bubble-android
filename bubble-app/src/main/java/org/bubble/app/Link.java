package org.bubble.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * Bubble's one connection to the phone, shared by every screen and service in the process. While
 * a Bubble screen is visible it is "held": wireless debugging stays on and the connection stays
 * open, so only the first action pays for turning debugging on, finding the port and the TLS
 * handshake; the next ones are almost instant. When the last screen goes away it is released
 * after a short grace (switching apps and back keeps it), closing the connection and turning
 * debugging off again.
 */
final class Link {

    private static final long GRACE_MS = 15_000;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static BubbleKey key;
    private static AdbChannelImpl channel;
    private static int holders;
    private static boolean held;
    private static Context app;

    private Link() {}

    /** The adb identity; loading it from StrongBox is slow, so it is loaded once. */
    static synchronized BubbleKey key() throws Exception {
        if (key == null) key = BubbleKey.load();
        return key;
    }

    static synchronized AdbChannelImpl channel(Context c) throws Exception {
        if (channel == null) channel = new AdbChannelImpl(c.getApplicationContext(), key());
        return channel;
    }

    /** Whether the connection is being kept open (a Bubble screen is visible). */
    static synchronized boolean held() {
        return held;
    }

    /** A Bubble screen became visible. */
    static synchronized void hold(Context c) {
        app = c.getApplicationContext();
        holders++;
        MAIN.removeCallbacks(RELEASE);
        if (!held && WirelessDebugging.hasWifi(app)) {
            held = true;
            WirelessDebugging.acquire(app);
        }
    }

    /** A Bubble screen went away; the last one releases after the grace period. */
    static synchronized void unhold() {
        if (holders > 0 && --holders == 0) MAIN.postDelayed(RELEASE, GRACE_MS);
    }

    private static final Runnable RELEASE = () -> {
        AdbChannelImpl ch;
        synchronized (Link.class) {
            if (holders > 0 || !held) return;
            held = false;
            ch = channel;
        }
        // Closing the socket and switching debugging off do I/O: off the main thread.
        new Thread(() -> {
            if (ch != null) ch.disconnect();
            WirelessDebugging.release(app);
        }, "bubble-link-release").start();
    };
}
