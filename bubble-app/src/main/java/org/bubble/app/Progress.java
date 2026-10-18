package org.bubble.app;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

/**
 * What Bubble is doing right now, in words for the user ("Turning on wireless debugging…",
 * "Finding the debugging port…"), shown under the loading bar of the screen that started the
 * work. The engine side reports steps from any thread; the screen receives them on the main one.
 */
final class Progress {

    interface Listener {
        void onStep(String step);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static Listener listener;

    private Progress() {}

    static synchronized void listen(Listener l) {
        listener = l;
    }

    static synchronized void stopListening(Listener l) {
        if (listener == l) listener = null;
    }

    static void step(Context c, int res, Object... args) {
        String text = c.getString(res, args);
        Listener l;
        synchronized (Progress.class) {
            l = listener;
        }
        if (l != null) MAIN.post(() -> l.onStep(text));
    }
}
