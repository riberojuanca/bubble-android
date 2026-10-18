package org.bubble.notificationprobe;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.IBinder;

/** Synthetic media state, with no audio or network, for testing SmartLock's music exception. */
public final class MusicProbe extends Service {
    private MediaSession session;
    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(
                new NotificationChannel("music", "Synthetic music", NotificationManager.IMPORTANCE_LOW));
        startForeground(2, new Notification.Builder(this, "music").setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Synthetic SmartLock test").build());
        session = new MediaSession(this, "bubble-test-music");
        session.setActive(true);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        boolean paused = intent != null && "pause".equals(intent.getAction());
        session.setPlaybackState(new PlaybackState.Builder().setState(
                paused ? PlaybackState.STATE_PAUSED : PlaybackState.STATE_PLAYING, 0, paused ? 0 : 1).build());
        return START_NOT_STICKY;
    }
    @Override public void onDestroy() { session.release(); super.onDestroy(); }
    @Override public IBinder onBind(Intent intent) { return null; }
}
