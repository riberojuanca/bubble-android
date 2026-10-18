package org.bubble.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Home: the logo, a status line and one row per area (Bubble, Apps, Meta space, Firewall, Settings); each row
 * opens its own screen. Until Bubble is paired and has a bubble, the setup assistant runs instead.
 */
public final class MainActivity extends Activity {

    private Store store;
    private TextView status;
    private android.widget.Button unlock;
    private Ui.Row bubbleRow;
    private Ui.Row appsRow;
    private Ui.Row firewallRow;
    private boolean checking;
    private android.widget.ImageView spy;

    /** Last check, shared by every home instance in this process; the summary shown meanwhile. */
    private static long lastCheck;
    private static String lastSummary;
    private static boolean lastLocked;
    /** Once per process: redraw the bubble shortcuts on the home screen with the current icons. */
    private static boolean shortcutsRefreshed;
    private static final long RECHECK_MS = 10 * 60 * 1000;
    private static final long REOPEN_MS = 2 * 60 * 1000;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        WirelessDebugging.recover(this);
        trackForeground();

        if (BootReceiver.insideBubble()) {
            // Bubble's copy inside the bubble only runs the firewall; it is managed from outside.
            // Its icon is removed from the bubble's launcher from now on.
            BootReceiver.hideLauncher(this);
            LinearLayout page = Ui.page(this);
            page.addView(Ui.logo(this, 72));
            page.addView(Ui.body(this, getString(R.string.inside_bubble_note)));
            return;
        }
        // Android 13+: without this the pairing notification (the whole pairing UX) is suppressed.
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
        }
        if (getActionBar() != null) getActionBar().hide();

        LinearLayout page = Ui.page(this);
        // The guard's spy, top right, as on every other screen.
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.END);
        spy = Ui.spy(this);
        top.addView(spy);
        page.addView(top);
        page.addView(Ui.logo(this, 88));
        TextView name = Ui.title(this, getString(R.string.app_name));
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        page.addView(name);
        status = Ui.body(this, "");
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        page.addView(status);
        // Right where it says the bubble is locked: Android's own fingerprint / PIN prompt.
        unlock = Ui.button(this, getString(R.string.action_unlock), v -> unlockBubble());
        unlock.setVisibility(android.view.View.GONE);
        page.addView(unlock);
        // The bar runs right under the status line it belongs to ("Checking the bubble…").
        LoadingBar bar = Ui.loadingBar(this);
        page.addView(bar);
        page.addView(Ui.loadingLabel(this));
        page.addView(Ui.divider(this));

        bubbleRow = Ui.row(page, getString(R.string.home_bubble), "",
                v -> startActivity(new Intent(this, BubbleActivity.class)));
        appsRow = Ui.row(page, getString(R.string.home_apps), getString(R.string.home_apps_sub),
                v -> startActivity(new Intent(this, AppsActivity.class)));
        // The second launcher icon, also from here: placing apps on the home screen starts there.
        // With its own bubbles (no tile; light or dark with the theme), so it reads as that app.
        Ui.Row grid = new Ui.Row(this, true, getString(R.string.grid_label), getString(R.string.home_grid_sub),
                v -> startActivity(new Intent(this, AppsGridActivity.class)));
        // The adaptive foreground keeps a margin for the launcher's mask: zoom past it.
        grid.icon.setImageDrawable(new android.graphics.drawable.InsetDrawable(
                getDrawable(R.drawable.ic_launcher_apps_foreground), -0.3f));
        page.addView(grid.view);
        page.addView(Ui.divider(this));
        Ui.row(page, getString(R.string.home_meta), getString(R.string.home_meta_sub),
                v -> startActivity(new Intent(this, MetaActivity.class)));
        firewallRow = Ui.row(page, getString(R.string.home_firewall), "",
                v -> startActivity(new Intent(this, FirewallActivity.class)));
        Ui.row(page, getString(R.string.home_settings), getString(R.string.home_settings_sub),
                v -> startActivity(new Intent(this, SettingsActivity.class)));
        Ui.onPull(this, () -> {
            BubbleCache.clear();
            appsRow.setSubtitle(bubbleCounts());
            check();
        });
    }

    @Override protected void onResume() {
        super.onResume();
        // Back from Android's unlock screen: a cancel must not leave Bubble waiting a minute.
        if (unlocking) Controller.unlockPromptClosed();
        if (BootReceiver.insideBubble()) return;
        if (!store.paired() || store.bubbleUser() < 0) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        Updates.checkAutomatically(this);
        PhotosAssistService.checkLost(this);
        if (!shortcutsRefreshed) {
            shortcutsRefreshed = true;
            android.content.Context app = getApplicationContext();
            new Thread(() -> {
                try {
                    AppsGridActivity.refreshPinned(app);
                } catch (Exception e) {
                    android.util.Log.w("Bubble", "could not redraw the shortcuts: " + e);
                }
            }, "bubble-shortcuts").start();
        }
        firewallRow.setSubtitle(store.phoneFirewallOn()
                ? getString(R.string.firewall_phone_on, store.applied().size()) : getString(R.string.firewall_phone_off));
        appsRow.setSubtitle(bubbleCounts());
        // Checking connects to the phone (and turns wireless debugging on for a moment): once when
        // Bubble opens, then only if the last check is old, not on every return from a screen.
        if (lastSummary != null && System.currentTimeMillis() - lastCheck < RECHECK_MS) {
            bubbleRow.setSubtitle(lastSummary);
            unlock.setVisibility(lastLocked ? android.view.View.VISIBLE : android.view.View.GONE);
            String notice = store.takeNotice();
            status.setText(notice.isEmpty() ? lastSummary : lastSummary + "\n" + notice);
            return;
        }
        bubbleRow.setSubtitle(getString(R.string.bubble_checking));
        status.setText(getString(R.string.bubble_checking));
        check();
    }

    /** "With internet: 1 · Without internet: 3" when the bubble's apps are known. */
    private String bubbleCounts() {
        java.util.List<String[]> apps = BubbleCache.apps();
        if (apps == null) return getString(R.string.home_apps_sub);
        java.util.Set<String> allowed = store.bubbleAnyNet();
        int with = 0;
        for (String[] app : apps) if (allowed.contains(app[0])) with++;
        return getString(R.string.firewall_bubble_counts, with, apps.size() - with);
    }

    /**
     * Opening Bubble always checks: the bubble must still exist and stay free of Google services.
     * "Opening" is Bubble coming to the foreground from outside (no Bubble screen was visible),
     * counted across all of Bubble's screens; moving between them is not.
     */
    private static int startedScreens;
    private static boolean lifecycleTracked;

    private void trackForeground() {
        if (lifecycleTracked) return;
        lifecycleTracked = true;
        getApplication().registerActivityLifecycleCallbacks(new android.app.Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityStarted(Activity a) {
                Link.hold(a);
                // Back from outside: check again, unless the last check is under 2 minutes old.
                if (startedScreens++ == 0 && System.currentTimeMillis() - lastCheck > REOPEN_MS) lastSummary = null;
            }
            @Override public void onActivityStopped(Activity a) {
                startedScreens--;
                Link.unhold();
            }
            @Override public void onActivityCreated(Activity a, Bundle b) {}
            @Override public void onActivityResumed(Activity a) {}
            @Override public void onActivityPaused(Activity a) {}
            @Override public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            @Override public void onActivityDestroyed(Activity a) {}
        });
        startedScreens = 0; // this home is about to start and will be counted
    }

    /** Android's unlock screen was asked for; it covers this one until confirmed or cancelled. */
    private boolean unlocking;

    @Override protected void onPause() {
        super.onPause();
        if (unlocking) Controller.unlockPromptOpened();
    }

    private void unlockBubble() {
        unlocking = true;
        Ui.work(this, getString(R.string.working_unlocking), () -> new Controller(this).unlockBubble(), unlocked -> {
            unlocking = false;
            if (!unlocked) return;
            unlock.setVisibility(android.view.View.GONE);
            check();
        });
    }

    private void check() {
        if (checking) return;
        checking = true;
        LoadingBar bar = findViewById(R.id.loading_bar);
        if (bar != null) bar.start();
        Progress.Listener steps = Ui.showSteps(this);
        new Thread(() -> {
            Controller.BubbleReport r;
            try {
                r = new Controller(this).bubbleStatus();
            } catch (Exception e) {
                r = new Controller.BubbleReport(String.valueOf(e.getMessage()), false);
            }
            Controller.BubbleReport report = r;
            runOnUiThread(() -> {
                checking = false;
                if (bar != null) bar.stop();
                Ui.hideSteps(this, steps);
                Ui.refreshSpy(this, spy);
                if (isFinishing()) return;
                String summary = BubbleFlow.summary(this, store, report);
                boolean locked = BubbleFlow.locked(report);
                unlock.setVisibility(locked ? android.view.View.VISIBLE : android.view.View.GONE);
                if (report.connected) {
                    lastLocked = locked;
                    lastSummary = summary;
                    lastCheck = System.currentTimeMillis();
                }
                String notice = store.takeNotice();
                bubbleRow.setSubtitle(summary);
                status.setText(notice.isEmpty() ? summary : summary + "\n" + notice);
                if (store.bubbleUser() < 0 && report.connected) {
                    startActivity(new Intent(this, SetupActivity.class));
                    finish();
                }
            });
        }, "bubble-check").start();
    }
}
