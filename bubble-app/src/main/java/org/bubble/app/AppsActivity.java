package org.bubble.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import java.util.List;

/**
 * The apps inside the bubble, one line each: icon, name, network on Wi-Fi and on mobile data
 * (each icon crossed out when off, a tap toggles it) and remove. Adding: apps already on the phone, or an APK file (from the bubble's
 * Downloads or the phone's files); F-Droid inside installs the rest.
 */
public final class AppsActivity extends Activity {

    private static final int PICK_APK = 2;
    private static final int ADD_APPS = 3;
    private List<AppInfo> shown;
    private LinearLayout list;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout page = Ui.screen(this, getString(R.string.home_apps), null, null);
        page.addView(Ui.small(this, getString(R.string.apps_hint)));
        page.addView(Ui.button(this, getString(R.string.apps_add_from_phone),
                v -> startActivityForResult(AppPickerActivity.intent(this, AppPickerActivity.MODE_ADD), ADD_APPS)));
        page.addView(Ui.button(this, getString(R.string.apps_add_apk), v -> chooseApkSource()));
        page.addView(Ui.title(this, getString(R.string.apps_inside)));
        page.addView(Ui.search(this, this::filter));
        list = Ui.column(this);
        page.addView(list);
        // Apps installed inside (F-Droid) are not known until the list is read again.
        Ui.onPull(this, () -> {
            BubbleCache.clear();
            reload();
        });
    }

    @Override protected void onStart() {
        super.onStart();
        // Read once on entering; again only after a change made here, not on every return.
        if (!loaded) {
            loaded = true;
            reload();
        }
    }

    private boolean loaded;

    private void reload() {
        Ui.work(this, getString(R.string.working_reading),
                () -> AppInfo.inside(this, new Controller(this).bubbleApps()), this::show);
    }

    private void show(List<AppInfo> apps) {
        shown = apps;
        list.removeAllViews();
        rows.clear();
        if (apps.isEmpty()) list.addView(Ui.body(this, getString(R.string.apps_none)));
        java.util.List<android.widget.ImageView> icons = new java.util.ArrayList<>();
        for (AppInfo app : apps) {
            android.widget.LinearLayout row = new android.widget.LinearLayout(this);
            row.setOrientation(android.widget.LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(this, 4), Ui.dp(this, 2), 0, Ui.dp(this, 2));
            row.setMinimumHeight(Ui.dp(this, 52));
            android.widget.ImageView icon = Ui.appIcon(this);
            row.addView(icon);
            icons.add(icon);
            row.addView(Ui.listText(this, app.label),
                    new android.widget.LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            for (boolean wifi : new boolean[]{true, false}) {
                android.widget.ImageView net = Ui.iconButton(this, 0, "", null);
                showNetwork(net, app, wifi);
                net.setOnClickListener(v -> toggleNetwork(net, app, wifi));
                row.addView(net);
            }
            row.addView(Ui.iconButton(this, R.drawable.ic_delete, getString(R.string.action_remove),
                    v -> confirmRemove(app)));
            // Each row carries its own loading bar, right above its divider: a change to this app
            // shows here, not at the top of the screen.
            LoadingBar bar = new LoadingBar(this);
            bar.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 2)));
            rowBars.put(app.pkg, bar);
            android.widget.LinearLayout cell = Ui.column(this);
            cell.addView(row);
            cell.addView(bar);
            cell.addView(Ui.divider(this));
            list.addView(cell);
            rows.add(cell);
        }
        Ui.loadIcons(this, apps, icons);
    }

    private final java.util.List<android.view.View> rows = new java.util.ArrayList<>();
    private final java.util.Map<String, LoadingBar> rowBars = new java.util.HashMap<>();

    /** Apps whose network changed and is not applied yet, with a counter per change. */
    private final java.util.Map<String, Integer> pending = new java.util.HashMap<>();
    private boolean applying;

    /** This app's network in the bubble on Wi-Fi or on mobile data; crossed out: none there. */
    private void showNetwork(android.widget.ImageView v, AppInfo app, boolean wifi) {
        Ui.showNetwork(this, v, new Store(this).bubbleNet(wifi).contains(app.pkg), wifi);
    }

    /**
     * The icon changes at once (the choice is saved right away) and the firewall catches up in the
     * background: taps on this or other apps never wait. Changes are coalesced: one apply runs at
     * a time with the latest list, and runs again if more taps came meanwhile.
     */
    private void toggleNetwork(android.widget.ImageView v, AppInfo app, boolean wifi) {
        Store store = new Store(this);
        java.util.Set<String> allowed = store.bubbleNet(wifi);
        if (!allowed.remove(app.pkg)) allowed.add(app.pkg);
        store.setBubbleNet(wifi, allowed);
        showNetwork(v, app, wifi);
        Integer n = pending.get(app.pkg);
        if (n == null) {
            LoadingBar bar = rowBars.get(app.pkg);
            if (bar != null) bar.start();
        }
        pending.put(app.pkg, n == null ? 1 : n + 1);
        applyPending();
    }

    private void applyPending() {
        if (applying || pending.isEmpty()) return;
        applying = true;
        java.util.Map<String, Integer> batch = new java.util.HashMap<>(pending);
        new Thread(() -> {
            Exception error = null;
            String log = "";
            try {
                log = new Controller(this).applyBubbleNetwork();
            } catch (Exception e) {
                error = e;
            }
            Exception err = error;
            boolean locked = log.contains("bubble locked");
            runOnUiThread(() -> {
                applying = false;
                for (java.util.Map.Entry<String, Integer> e : batch.entrySet()) {
                    // Done for this app unless it was tapped again while applying.
                    if (e.getValue().equals(pending.get(e.getKey()))) {
                        pending.remove(e.getKey());
                        LoadingBar bar = rowBars.get(e.getKey());
                        if (bar != null) bar.stop();
                    }
                }
                if (err != null && !isFinishing()) {
                    Ui.alert(this, getString(R.string.error_title), String.valueOf(err.getMessage()));
                } else if (locked && !isFinishing()) {
                    android.widget.Toast.makeText(this, new Store(this).guard() == Store.GUARD_ALWAYS
                            ? R.string.firewall_locked_auto : R.string.firewall_locked_open, android.widget.Toast.LENGTH_LONG).show();
                }
                applyPending();
            });
        }, "bubble-internet").start();
    }

    private void confirmRemove(AppInfo app) {
        new Ui.Sheet(this)
                .title(getString(R.string.apps_remove_title, app.label))
                .message(R.string.apps_remove_body)
                .action(getString(R.string.action_remove), () -> Ui.work(this, getString(R.string.working_applying),
                        () -> new Controller(this).removeApps(java.util.Collections.singleton(app.pkg)), log -> reload()))
                .cancel()
                .show();
    }

    private void filter(String query) {
        for (int i = 0; i < rows.size() && shown != null; i++) {
            AppInfo app = shown.get(i);
            boolean match = query.isEmpty() || app.label.toLowerCase(java.util.Locale.ROOT).contains(query) || app.pkg.contains(query);
            rows.get(i).setVisibility(match ? android.view.View.VISIBLE : android.view.View.GONE);
        }
    }

    /** An APK from the bubble's Downloads (where a browser inside saves it) or from the phone's files. */
    private void chooseApkSource() {
        new Ui.Sheet(this)
                .title(R.string.apps_add_apk)
                .choice(getString(R.string.apk_from_bubble), null, this::pickBubbleDownload)
                .choice(getString(R.string.apk_from_phone), null, this::pickApk)
                .cancel()
                .show();
    }

    /** An APK of the bubble's Downloads with what its file says: app name and icon. */
    private static final class Download {
        final String[] apk;
        final CharSequence label;
        final android.graphics.drawable.Drawable icon;

        Download(android.content.Context c, String[] apk) {
            this.apk = apk;
            android.content.pm.PackageManager pm = c.getPackageManager();
            android.content.pm.PackageInfo info = pm.getPackageArchiveInfo(apk[3], 0);
            if (info != null && info.applicationInfo != null) {
                info.applicationInfo.sourceDir = apk[3];
                info.applicationInfo.publicSourceDir = apk[3];
                label = info.applicationInfo.loadLabel(pm);
                icon = info.applicationInfo.loadIcon(pm);
            } else {
                label = apk[2];
                icon = null;
            }
        }
    }

    private void pickBubbleDownload() {
        Ui.work(this, getString(R.string.working_reading), () -> {
            List<String[]> found = new Controller(this).bubbleDownloads();
            if (found == null) return null;
            List<Download> out = new java.util.ArrayList<>();
            for (String[] apk : found) out.add(new Download(this, apk));
            return out;
        }, apks -> {
            if (apks == null) {
                new Ui.Sheet(this)
                        .title(R.string.apk_from_bubble)
                        .message(R.string.apk_bubble_locked)
                        .action(getString(R.string.action_unlock), () -> Ui.work(this, getString(R.string.working_unlocking),
                                () -> new Controller(this).unlockBubble(), unlocked -> { if (unlocked) pickBubbleDownload(); }))
                        .cancel()
                        .show();
                return;
            }
            if (apks.isEmpty()) {
                Ui.alert(this, getString(R.string.apk_from_bubble), getString(R.string.apk_bubble_none));
                return;
            }
            Ui.Sheet sheet = new Ui.Sheet(this).title(R.string.apk_from_bubble);
            for (Download d : apks) {
                String[] apk = d.apk;
                sheet.choice(d.label, apk[2] + " · " + android.text.format.Formatter.formatShortFileSize(this, Long.parseLong(apk[1])),
                        d.icon, () -> Ui.work(this, getString(R.string.working_installing),
                                () -> new Controller(this).installDownload(apk), out -> {
                                    if (!out.contains("Success")) Ui.alert(this, getString(R.string.error_title), out);
                                    else reload();
                                }));
            }
            sheet.cancel().show();
        });
    }

    private void pickApk() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/vnd.android.package-archive");
        startActivityForResult(pick, PICK_APK);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ADD_APPS && resultCode == RESULT_OK) reload();
        if (requestCode == PICK_APK && resultCode == RESULT_OK && data != null && data.getData() != null) {
            android.net.Uri apk = data.getData();
            Ui.work(this, getString(R.string.working_installing), () -> new Controller(this).installApk(apk), out -> {
                if (!out.contains("Success")) Ui.alert(this, getString(R.string.error_title), out);
                else reload();
            });
        }
    }
}
