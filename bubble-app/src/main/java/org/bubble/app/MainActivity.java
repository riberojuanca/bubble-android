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
 * Home: the logo, the state ("Protected and locked") and one row per area (Bubble Apps, Bubble, Manage apps,
 * Firewall, Meta space, Settings); each row
 * opens its own screen. Until Bubble is paired and has a bubble, the setup assistant runs instead.
 */
public final class MainActivity extends Activity {

    private Store store;
    private TextView status;
    /** "Protected 🛡 and locked 🔒", shown instead of the status text while the bubble is protected. */
    private LinearLayout stateLine;
    private TextView lockText;
    private android.widget.ImageView lockIcon;
    private Ui.Row bubbleRow;
    private Ui.Row appsRow;
    private Ui.Row firewallRow;
    private boolean checking;
    private android.widget.ImageView spy;

    /** Last check, shared by every home instance in this process; the summary shown meanwhile. */
    private static long lastCheck;
    private static String lastSummary;
    private static boolean lastProtected;
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
        // Bubble's own notices (a firewall left without its list, for example). Before setup the
        // setup screen asks instead, right before pairing: here onResume would cover the prompt.
        if (store.paired() && store.bubbleUser() >= 0
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
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
        stateLine = new LinearLayout(this);
        stateLine.setGravity(Gravity.CENTER);
        stateLine.addView(statePart(getString(R.string.state_protected), R.drawable.ic_shield,
                getString(R.string.state_shield_desc), v -> showProtection()));
        LinearLayout lock = statePart("", R.drawable.ic_lock, getString(R.string.state_lock_desc), v -> toggleLock());
        lockText = (TextView) lock.getChildAt(0);
        lockIcon = (android.widget.ImageView) lock.getChildAt(1);
        stateLine.addView(lock);
        stateLine.setVisibility(android.view.View.GONE);
        page.addView(stateLine);
        // The bar runs right under the status line it belongs to ("Checking the bubble…").
        LoadingBar bar = Ui.loadingBar(this);
        page.addView(bar);
        page.addView(Ui.loadingLabel(this));
        page.addView(Ui.divider(this));

        // The second launcher icon, also from here: placing apps on the home screen starts there.
        // With its own bubbles (no tile; light or dark with the theme), so it reads as that app.
        Ui.Row grid = new Ui.Row(this, true, getString(R.string.grid_label), getString(R.string.home_grid_sub),
                v -> startActivity(new Intent(this, AppsGridActivity.class)));
        // The adaptive foreground keeps a margin for the launcher's mask: zoom past it.
        grid.icon.setImageDrawable(new android.graphics.drawable.InsetDrawable(
                getDrawable(R.drawable.ic_launcher_apps_foreground), -0.3f));
        page.addView(grid.view);
        page.addView(Ui.divider(this));
        bubbleRow = Ui.row(page, R.drawable.ic_row_bubble, getString(R.string.home_bubble), "",
                v -> startActivity(new Intent(this, BubbleActivity.class)));
        appsRow = Ui.row(page, R.drawable.ic_row_apps, getString(R.string.home_apps), getString(R.string.home_apps_sub),
                v -> startActivity(new Intent(this, AppsActivity.class)));
        firewallRow = Ui.row(page, R.drawable.ic_wifi, getString(R.string.home_firewall), "",
                v -> startActivity(new Intent(this, FirewallActivity.class)));
        Ui.row(page, R.drawable.ic_row_files, getString(R.string.home_files), getString(R.string.home_files_sub),
                v -> startActivity(new Intent(this, SpacePhotosActivity.class).putExtra(SpacePhotosActivity.EXTRA_MANAGE, true)));
        Ui.row(page, R.drawable.ic_row_meta, getString(R.string.home_meta), getString(R.string.home_meta_sub),
                v -> startActivity(new Intent(this, MetaActivity.class)));
        Ui.row(page, R.drawable.ic_row_settings, getString(R.string.home_settings), getString(R.string.home_settings_sub),
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
            showState(lastSummary, lastProtected);
            return;
        }
        bubbleRow.setSubtitle(getString(R.string.bubble_checking));
        stateLine.setVisibility(android.view.View.GONE);
        status.setVisibility(android.view.View.VISIBLE);
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

    /** The status text, or "Protected 🛡 and locked/open 🔒" while nothing needs attention. */
    private void showState(String summary, boolean safe) {
        String notice = store.takeNotice();
        bubbleRow.setSubtitle(summary);
        stateLine.setVisibility(safe ? android.view.View.VISIBLE : android.view.View.GONE);
        String text = safe ? notice : notice.isEmpty() ? summary : summary + "\n" + notice;
        status.setText(text);
        status.setVisibility(text.isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
        if (safe) refreshLock();
    }

    /** A word and its lilac icon, tappable as one (48dp high). */
    private LinearLayout statePart(String text, int icon, String description, android.view.View.OnClickListener onClick) {
        LinearLayout part = new LinearLayout(this);
        part.setGravity(Gravity.CENTER_VERTICAL);
        part.setMinimumHeight(Ui.dp(this, 48));
        part.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        part.setBackground(Ui.drawableAttr(this, android.R.attr.selectableItemBackground));
        part.setContentDescription(description);
        part.setOnClickListener(onClick);
        part.addView(Ui.text(this, text, android.R.style.TextAppearance_DeviceDefault_Medium));
        android.widget.ImageView i = new android.widget.ImageView(this);
        i.setImageResource(icon);
        i.setImageTintList(android.content.res.ColorStateList.valueOf(Ui.LILAC));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20));
        lp.leftMargin = Ui.dp(this, 6);
        part.addView(i, lp);
        return part;
    }

    /** Read from Android each time: the bubble locks itself with the screen. */
    private boolean bubbleLocked() {
        try {
            return ProfileBridge.locked(this, store.bubbleUser());
        } catch (RuntimeException e) {
            return true;
        }
    }

    private void refreshLock() {
        boolean locked = bubbleLocked();
        lockText.setText(locked ? R.string.state_locked : R.string.state_open);
        lockIcon.setImageResource(locked ? R.drawable.ic_lock : R.drawable.ic_lock_open);
    }

    private void showProtection() {
        int google = store.lastBubbleGoogle(false);
        int g = store.guard();
        String text = (google == 0 ? getString(R.string.protection_google_none) : getString(R.string.protection_google_some, google))
                + "\n" + getString(g == Store.GUARD_ALWAYS ? R.string.protection_guard_always
                        : g == Store.GUARD_SESSION ? R.string.protection_guard_session : R.string.protection_guard_off)
                + (lastCheck == 0 ? "" : "\n" + getString(R.string.protection_checked,
                        android.text.format.DateUtils.getRelativeTimeSpanString(lastCheck)));
        Ui.alert(this, getString(R.string.protection_title), text);
    }

    /** Android's unlock screen was asked for; it covers this one until confirmed or cancelled. */
    private boolean unlocking;

    @Override protected void onPause() {
        super.onPause();
        if (unlocking) Controller.unlockPromptOpened();
    }

    /** Locked: Android's fingerprint / PIN prompt. Open: lock it now. */
    private void toggleLock() {
        if (bubbleLocked()) {
            unlocking = true;
            Ui.work(this, getString(R.string.working_unlocking), () -> new Controller(this).unlockBubble(), unlocked -> {
                unlocking = false;
                refreshLock();
            });
            return;
        }
        try {
            getSystemService(android.os.UserManager.class)
                    .requestQuietModeEnabled(true, ProfileBridge.handle(store.bubbleUser()));
        } catch (RuntimeException e) {
            android.widget.Toast.makeText(this, R.string.error_profile_setup, android.widget.Toast.LENGTH_LONG).show();
        }
        // Quiet mode is applied a moment later.
        lockIcon.postDelayed(this::refreshLock, 800);
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
                boolean safe = BubbleFlow.isProtected(store, report);
                if (report.connected) {
                    lastSummary = summary;
                    lastProtected = safe;
                    lastCheck = System.currentTimeMillis();
                }
                showState(summary, safe);
                if (store.bubbleUser() < 0 && report.connected) {
                    startActivity(new Intent(this, SetupActivity.class));
                    finish();
                }
            });
        }, "bubble-check").start();
    }
}
