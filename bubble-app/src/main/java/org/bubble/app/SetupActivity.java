package org.bubble.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * First run, only what is required, one step per screen: pair Bubble with wireless debugging, then
 * create the bubble (which also gets F-Droid and a keyboard). Options live in the app afterwards.
 */
public final class SetupActivity extends Activity {

    /** Android 17: the only version where Bubble creates the bubble (the engine refuses others). */
    static final int BUBBLE_SDK = 37;
    private static final int NOTIFICATIONS = 1;

    private Store store;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        if (getActionBar() != null) getActionBar().hide();
    }

    @Override protected void onResume() {
        super.onResume();
        // Pairing finishes in the notification while the user is in Settings: re-evaluate here.
        // Checked before pairing, so nobody grants everything to learn at the end it cannot work.
        if (Build.VERSION.SDK_INT < BUBBLE_SDK) showUnsupported();
        else if (!store.paired()) showPair();
        else if (store.bubbleUser() < 0) showCreate();
        else showDone();
    }

    private LinearLayout header(int step) {
        LinearLayout page = Ui.page(this);
        page.addView(Ui.logo(this, 64));
        if (step > 0) {
            TextView s = Ui.small(this, getString(R.string.setup_step, step, 2));
            s.setGravity(Gravity.CENTER_HORIZONTAL);
            page.addView(s);
        }
        return page;
    }

    private void showPair() {
        LinearLayout page = header(1);
        page.addView(Ui.title(this, getString(R.string.setup_pair_title)));
        page.addView(Ui.body(this, getString(R.string.setup_pair_why)));
        page.addView(Ui.body(this, getString(R.string.setup_pair_steps)));
        page.addView(Ui.button(this, getString(R.string.setup_pair_action), v -> {
            // The code is typed into Bubble's notification: ask here, where the prompt is seen.
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
                startPairing();
            } else if (store.notificationsAsked() && !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                // Denied for good: Android no longer shows the prompt, only its settings can.
                startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
            } else {
                store.setNotificationsAsked();
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATIONS);
            }
        }));
    }

    @Override public void onRequestPermissionsResult(int code, String[] permissions, int[] results) {
        if (code != NOTIFICATIONS) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startPairing();
        else android.widget.Toast.makeText(this, R.string.setup_pair_notifications, android.widget.Toast.LENGTH_LONG).show();
    }

    private void startPairing() {
        startForegroundService(new Intent(this, PairingService.class));
        startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
    }

    private void showUnsupported() {
        LinearLayout page = header(0);
        page.addView(Ui.title(this, getString(R.string.setup_unsupported_title)));
        page.addView(Ui.body(this, getString(R.string.setup_unsupported_body, Build.VERSION.RELEASE)));
    }

    private void showCreate() {
        LinearLayout page = header(2);
        page.addView(Ui.title(this, getString(R.string.setup_create_title)));
        page.addView(Ui.body(this, getString(R.string.setup_create_body)));
        page.addView(Ui.button(this, getString(R.string.setup_create_action),
                v -> BubbleFlow.create(this, r -> showDone())));
    }

    private void showDone() {
        LinearLayout page = header(0);
        page.addView(Ui.title(this, getString(R.string.setup_done_title)));
        page.addView(Ui.body(this, getString(R.string.setup_done_body)));
        String notice = store.takeNotice();
        if (!notice.isEmpty()) page.addView(Ui.small(this, notice));
        page.addView(Ui.button(this, getString(R.string.setup_done_action), v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }));
    }
}
