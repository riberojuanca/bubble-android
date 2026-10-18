package org.bubble.app;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.Process;
import android.os.UserManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Inside the bubble: locks it when the screen goes off, unless one of its apps is playing, and
 * as soon as playing stops. Android's own auto-lock (left on "only after restart" by the engine)
 * would stop the whole profile with the screen, music included. Started by the bubble's always-on
 * VPN, which lives as long as the bubble is unlocked. Reading the sessions needs the approved
 * {@link BubbleMediaWatcher}; locking needs MODIFY_QUIET_MODE (both set by bubble-smart-lock).
 * Without them it does nothing and Android's auto-lock stays as it was.
 */
final class SmartLock {

    /** A short pause between tracks or while buffering does not lock the bubble. */
    private static final long GRACE_MS = 5000;

    /** One per process; it holds the application context only, which lives as long as the process. */
    @android.annotation.SuppressLint("StaticFieldLeak")
    private static SmartLock running;

    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<MediaController> controllers = new ArrayList<>();
    private final ComponentName key;
    private final MediaSessionManager sessions;
    private boolean screenOff;

    static void start(Context context) {
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        // Right after an unlock or an update Android may not have taken the watcher's approval
        // back yet ("Missing permission to control media"): try again a little later.
        for (long delay : new long[]{0, 3000, 10000, 30000}) main.postDelayed(() -> tryStart(app), delay);
    }

    private static synchronized void tryStart(Context app) {
        if (running != null || !BootReceiver.insideBubble()) return;
        try {
            running = new SmartLock(app);
            android.util.Log.i("Bubble", "smart lock on");
        } catch (SecurityException e) {
            // Not approved (yet): Android's own auto-lock applies meanwhile.
            android.util.Log.w("Bubble", "smart lock not ready: " + e.getMessage());
        }
    }

    private SmartLock(Context context) {
        this.context = context;
        key = new ComponentName(context, BubbleMediaWatcher.class);
        sessions = context.getSystemService(MediaSessionManager.class);
        sessions.addOnActiveSessionsChangedListener(this::track, key, handler);
        track(sessions.getActiveSessions(key));
        screenOff = !context.getSystemService(PowerManager.class).isInteractive();
        IntentFilter filter = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        context.registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
        if (screenOff) scheduleLock();
    }

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) {
            screenOff = Intent.ACTION_SCREEN_OFF.equals(intent.getAction());
            handler.removeCallbacks(lockIfIdle);
            if (screenOff) lockIfIdle.run();
        }
    };

    private final MediaController.Callback playback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState state) {
            scheduleLock();
        }

        @Override public void onSessionDestroyed() {
            scheduleLock();
        }
    };

    private final Runnable lockIfIdle = this::lockIfIdle;

    private void lockIfIdle() {
        if (!screenOff || playing()) return;
        try {
            context.getSystemService(UserManager.class).requestQuietModeEnabled(true, Process.myUserHandle());
        } catch (RuntimeException e) {
            android.util.Log.w("Bubble", "could not lock the bubble", e);
        }
    }

    private void track(List<MediaController> active) {
        for (MediaController c : controllers) c.unregisterCallback(playback);
        controllers.clear();
        if (active != null) {
            for (MediaController c : active) {
                c.registerCallback(playback, handler);
                controllers.add(c);
            }
        }
        scheduleLock();
    }

    private void scheduleLock() {
        handler.removeCallbacks(lockIfIdle);
        if (screenOff) handler.postDelayed(lockIfIdle, GRACE_MS);
    }

    /** Asked of Android each time: a destroyed session keeps its last state in its controller. */
    private boolean playing() {
        for (MediaController c : sessions.getActiveSessions(key)) {
            PlaybackState state = c.getPlaybackState();
            if (state == null) continue;
            int s = state.getState();
            if (s == PlaybackState.STATE_PLAYING || s == PlaybackState.STATE_BUFFERING
                    || s == PlaybackState.STATE_CONNECTING) return true;
        }
        return false;
    }
}
