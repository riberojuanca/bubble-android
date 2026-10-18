package org.bubble.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.app.Service;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.os.IBinder;

import java.net.InetSocketAddress;

/**
 * Pairing over a notification, the only workable flow on one device: opening the Bubble app would
 * close the system pairing server (verified: the server dies when the "Pair device" dialog loses
 * the foreground), but pulling down the notification shade keeps it alive. So the user opens the
 * system "Pair device with pairing code" dialog and types the 6-digit code into this notification.
 *
 * <p>Bubble finds the pairing port itself through mDNS ({@code _adb-tls-pairing._tcp}); the user
 * only reads the code, never the port.
 */
public final class PairingService extends Service {

    static final String ACTION_REPLY = "org.bubble.app.PAIR_REPLY";
    static final String EXTRA_CODE = "code"; // RemoteInput key, also a plain extra for testing
    private static final String CHANNEL = "bubble-pairing";
    private static final int NOTIFICATION_ID = 7;

    private volatile InetSocketAddress pairingEndpoint;

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, notification(getString(R.string.pairing_waiting_title),
                getString(R.string.pairing_waiting_body)),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);

        String code = extractCode(intent);
        if (code != null) {
            pairWith(code, startId);
        } else {
            discover();
        }
        return START_NOT_STICKY;
    }

    private void discover() {
        new Thread(() -> {
            try {
                InetSocketAddress endpoint = Mdns.resolve(this, Mdns.PAIRING, 180_000);
                if (endpoint != null) {
                    pairingEndpoint = endpoint;
                    update(getString(R.string.pairing_found_title), getString(R.string.pairing_found_body));
                } else {
                    update(getString(R.string.pairing_missing_title), getString(R.string.pairing_missing_body));
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }, "bubble-pairing-discover").start();
    }

    private void pairWith(String code, int startId) {
        new Thread(() -> {
            String result;
            try {
                InetSocketAddress endpoint = waitForEndpoint();
                if (endpoint == null) {
                    result = getString(R.string.pairing_not_found);
                } else {
                    // Controller build runs the slow StrongBox keygen; keep it off the main thread.
                    result = new Controller(this).pair(endpoint.getHostString(), endpoint.getPort(), code);
                }
            } catch (Exception e) {
                result = getString(R.string.error_title) + ": " + e.getMessage();
            }
            update(getString(R.string.pairing_result_title), result);
            stopForeground(STOP_FOREGROUND_DETACH);
            stopSelf(startId);
        }, "bubble-pairing").start();
    }

    /** The reply may arrive just before discovery resolves; give it a short grace period. */
    private InetSocketAddress waitForEndpoint() throws InterruptedException {
        for (int i = 0; i < 100 && pairingEndpoint == null; i++) Thread.sleep(100);
        if (pairingEndpoint != null) return pairingEndpoint;
        return Mdns.resolve(this, Mdns.PAIRING, 10_000);
    }

    private String extractCode(Intent intent) {
        if (intent == null) return null;
        Bundle remote = RemoteInput.getResultsFromIntent(intent);
        CharSequence typed = remote != null ? remote.getCharSequence(EXTRA_CODE) : null;
        if (typed == null) typed = intent.getStringExtra(EXTRA_CODE);
        if (typed == null) return null;
        String code = typed.toString().trim();
        return code.matches("[0-9]{6}") ? code : null;
    }

    private Notification notification(String title, String text) {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(CHANNEL, getString(R.string.pairing_channel),
                NotificationManager.IMPORTANCE_HIGH));

        RemoteInput remoteInput = new RemoteInput.Builder(EXTRA_CODE)
                .setLabel(getString(R.string.pairing_code_label)).build();
        Intent replyIntent = new Intent(this, PairingService.class).setAction(ACTION_REPLY);
        PendingIntent replyPending = PendingIntent.getService(this, 0, replyIntent,
                PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification.Action action = new Notification.Action.Builder(null, getString(R.string.pairing_action), replyPending)
                .addRemoteInput(remoteInput).build();

        return new Notification.Builder(this, CHANNEL)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
                .setOngoing(true)
                .addAction(action)
                .build();
    }

    private void update(String title, String text) {
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification(title, text));
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
