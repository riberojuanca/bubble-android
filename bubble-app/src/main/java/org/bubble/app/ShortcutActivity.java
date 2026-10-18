package org.bubble.app;

import android.app.Activity;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.os.Bundle;
import android.os.UserHandle;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * What a pinned shortcut from Bubble Apps, or a tile of the widget, opens. A Meta app starts at
 * once through LauncherApps; a bubble app goes through the engine (Android asks to unlock the
 * bubble when it is locked), with the logo, a loading bar with its steps and, when the bubble is
 * locked, a padlock on screen meanwhile.
 */
public final class ShortcutActivity extends Activity {

    static final String EXTRA_SPACE = "space";
    static final String EXTRA_PACKAGE = "package";
    /** The widget's Bubble tile: opens Bubble Apps, where the bubble's apps need the fingerprint. */
    static final String SPACE_GRID = "grid";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String space = getIntent().getStringExtra(EXTRA_SPACE);
        String pkg = getIntent().getStringExtra(EXTRA_PACKAGE);
        if (pkg == null || BootReceiver.insideBubble()) {
            finish();
            return;
        }
        if (SPACE_GRID.equals(space)) {
            startActivity(new android.content.Intent(this, AppsGridActivity.class));
            finish();
            return;
        }
        if (AppsGridActivity.SPACE_META.equals(space)) {
            UserHandle meta = AppsWidget.metaHandle(this);
            LauncherApps launcher = getSystemService(LauncherApps.class);
            List<LauncherActivityInfo> found = meta == null ? null : launcher.getActivityList(pkg, meta);
            if (found == null || found.isEmpty()) {
                Ui.page(this).addView(Ui.body(this, getString(R.string.error_no_meta)));
                return;
            }
            launcher.startMainActivity(found.get(0).getComponentName(), meta, null, null);
            finish();
            return;
        }
        LinearLayout page = Ui.page(this);
        page.addView(Ui.logo(this, 72));
        // A large padlock centred at the bottom, only when the bubble is locked: Android asks for
        // the fingerprint, and the padlock opens once it is given. Unlocked, the app just opens.
        boolean locked = bubbleStillLocked();
        // The loading bar and its step text in the middle, as on Bubble's own screens, a bit
        // narrower than the page: opening an app of the bubble takes a few seconds.
        page.addView(new android.view.View(this), new LinearLayout.LayoutParams(0, 0, 1f));
        LoadingBar bar = Ui.loadingBar(this);
        LinearLayout.LayoutParams barSize = (LinearLayout.LayoutParams) bar.getLayoutParams();
        barSize.width = Math.round(getResources().getDisplayMetrics().widthPixels * 0.6f);
        barSize.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        page.addView(bar);
        TextView label = Ui.loadingLabel(this);
        page.addView(label);
        progress = new android.view.View[]{bar, label};
        page.addView(new android.view.View(this), new LinearLayout.LayoutParams(0, 0, 1f));
        android.widget.ImageView lock = new android.widget.ImageView(this);
        lock.setVisibility(locked ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
        lock.setImageResource(R.drawable.ic_lock);
        lock.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.LILAC));
        lock.setContentDescription(getString(R.string.shortcut_lock));
        LinearLayout.LayoutParams lockSize = new LinearLayout.LayoutParams(Ui.dp(this, 96), Ui.dp(this, 96));
        lockSize.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        lockSize.bottomMargin = Ui.dp(this, 48);
        page.addView(lock, lockSize);
        this.lock = lock;
        opening = true;
        Ui.work(this, getString(R.string.working_opening), () -> new Controller(this).openInBubble(pkg, () -> {
            // Unlocked: the padlock opens before the app comes up.
            runOnUiThread(this::openLock);
            try {
                Thread.sleep(UNLOCK_SHOW_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }), log -> {
            opening = false;
            if (log.contains("launched ") || log.equals(getString(R.string.grid_still_locked))) finish();
            else Ui.alert(this, getString(R.string.error_title), log);
        });
    }

    /** How long the opened padlock shows before the app comes up. */
    private static final long UNLOCK_SHOW_MS = 550;

    private android.widget.ImageView lock;
    /** The loading bar and its step text: useful until the fingerprint prompt, then the padlock leads. */
    private android.view.View[] progress = new android.view.View[0];
    private boolean opening;
    private boolean prompted;

    /** A small press, then the shackle lifts (the open padlock) and settles. */
    private void openLock() {
        lock.animate().scaleX(0.88f).scaleY(0.88f).setDuration(120).withEndAction(() -> {
            lock.setImageResource(R.drawable.ic_lock_open);
            lock.animate().scaleX(1.08f).scaleY(1.08f).setDuration(180).withEndAction(() ->
                    lock.animate().scaleX(1f).scaleY(1f).setDuration(140).start()).start();
        }).start();
    }

    /**
     * Android's unlock prompt covers this screen; when this screen comes back the prompt is gone.
     * Still locked means it was cancelled: close at once instead of waiting. Unlocked, stay for the
     * padlock to open while the engine launches the app.
     */
    @Override protected void onPause() {
        super.onPause();
        if (!opening) return;
        prompted = true;
        Controller.unlockPromptOpened();
        // The fingerprint prompt is up and this screen shows behind it: the bar's job is done.
        // Alpha, so later steps cannot show it again.
        for (android.view.View v : progress) v.setAlpha(0f);
    }

    @Override protected void onResume() {
        super.onResume();
        if (!prompted || !opening) return;
        prompted = false;
        Controller.unlockPromptClosed();
        if (bubbleStillLocked()) finish();
    }

    /** From Android directly (no engine round trip); unknown counts as unlocked, the engine decides. */
    private boolean bubbleStillLocked() {
        int user = new Store(this).bubbleUser();
        try {
            return user > 0 && getSystemService(android.os.UserManager.class)
                    .isQuietModeEnabled(UserHandle.getUserHandleForUid(user * 100000));
        } catch (RuntimeException e) {
            return false;
        }
    }
}
