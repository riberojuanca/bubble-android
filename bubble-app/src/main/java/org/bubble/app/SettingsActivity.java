package org.bubble.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Rarely needed things: pairing again, the technical log, removing everything, version. */
public final class SettingsActivity extends Activity {

    /** Opened from the "turned off" notification: offer to turn the Wallpaper button back on. */
    static final String EXTRA_ASSIST = "assist";

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout page = Ui.screen(this, getString(R.string.home_settings), null, null);
        Ui.row(page, getString(R.string.settings_repair), getString(R.string.settings_repair_sub), v -> {
            startForegroundService(new Intent(this, PairingService.class));
            Ui.alert(this, getString(R.string.setup_pair_title), getString(R.string.setup_pair_next));
        });
        Ui.row(page, getString(R.string.settings_log), getString(R.string.settings_log_sub), v -> showLog());
        assist = Ui.row(page, getString(R.string.settings_assist), "", null);
        assist.view.setOnClickListener(v -> toggleAssist(assist));
        if (getIntent().getBooleanExtra(EXTRA_ASSIST, false) && !PhotosAssistService.enabled(this)) toggleAssist(assist);
        Ui.Row lock = Ui.row(page, getString(R.string.settings_lock), lockMode(), null);
        lock.view.setOnClickListener(v -> pickLock(lock));
        Ui.Row shape = Ui.row(page, getString(R.string.settings_icon_shape),
                getString(IconShape.title(new Store(this).iconShape())), null);
        shape.view.setOnClickListener(v -> pickShape(shape));
        if (Updates.fromStore(this)) {
            Ui.row(page, getString(R.string.settings_updates), getString(R.string.settings_updates_store),
                    v -> Ui.alert(this, getString(R.string.settings_updates), getString(R.string.settings_updates_store)));
        } else {
            Ui.row(page, getString(R.string.settings_updates), getString(R.string.settings_updates_sub),
                    v -> startActivity(new Intent(this, UpdateActivity.class)));
            Ui.Row auto = Ui.row(page, getString(R.string.settings_updates_auto), autoUpdates(), null);
            auto.view.setOnClickListener(v -> {
                Updates.setAutomatic(this, !Updates.automatic(this));
                auto.setSubtitle(autoUpdates());
            });
        }
        Ui.row(page, getString(R.string.settings_remove_all), getString(R.string.settings_remove_all_sub), v -> explainRemoveAll());
        Ui.row(page, getString(R.string.settings_about), version(), v -> { });
    }

    private Ui.Row assist;

    private String autoUpdates() {
        return getString(Updates.automatic(this) ? R.string.settings_updates_auto_on : R.string.settings_updates_auto_off);
    }

    private String lockMode() {
        return getString(new Store(this).keepBubbleOpen() ? R.string.settings_lock_manual : R.string.settings_lock_screen);
    }

    private void pickLock(Ui.Row row) {
        Store store = new Store(this);
        new Ui.Sheet(this).title(R.string.settings_lock).message(R.string.lock_body)
                .choice(getString(R.string.settings_lock_screen), null, () -> {
                    store.setKeepBubbleOpen(false);
                    row.setSubtitle(lockMode());
                })
                .choice(getString(R.string.settings_lock_manual), null, () -> {
                    store.setKeepBubbleOpen(true);
                    row.setSubtitle(lockMode());
                })
                .cancel().show();
    }

    /** Also after coming back from Android's accessibility settings. */
    @Override protected void onResume() {
        super.onResume();
        assist.setSubtitle(getString(PhotosAssistService.enabled(this) ? R.string.settings_assist_on
                : PhotosAssistService.lost(this) ? R.string.settings_assist_lost : R.string.settings_assist_off));
        PhotosAssistService.checkLost(this);
    }

    /** On: explain what the accessibility service sees first. Off: at once. */
    private void toggleAssist(Ui.Row row) {
        if (PhotosAssistService.enabled(this)) {
            if (!PhotosAssistService.setEnabled(this, false)) {
                startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return;
            }
            row.setSubtitle(getString(R.string.settings_assist_off));
            return;
        }
        new Ui.Sheet(this).title(R.string.settings_assist).message(R.string.assist_explain)
                .action(getString(R.string.assist_turn_on), () -> {
                    if (PhotosAssistService.setEnabled(this, true)) {
                        row.setSubtitle(getString(R.string.settings_assist_on));
                        return;
                    }
                    // Not paired yet: Android's own switch, under Accessibility → Bubble.
                    android.widget.Toast.makeText(this, R.string.assist_manual, android.widget.Toast.LENGTH_LONG).show();
                    startActivity(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
                })
                .cancel().show();
    }

    /**
     * The same shape as the launcher's (Wallpaper & style): the launcher does not tell other apps
     * which one it uses. Every icon Bubble draws, and the ones already on the home screen, follow it.
     */
    private void pickShape(Ui.Row row) {
        Store store = new Store(this);
        Ui.Sheet sheet = new Ui.Sheet(this).title(R.string.settings_icon_shape).message(R.string.shape_body);
        for (String s : IconShape.ALL) {
            sheet.choice(getString(IconShape.title(s)), s.equals(store.iconShape()) ? getString(R.string.shape_current) : null, () -> {
                store.setIconShape(s);
                row.setSubtitle(getString(IconShape.title(s)));
                BubbleCache.clear();
                AppsWidget.refresh(this);
            });
        }
        sheet.cancel().show();
    }

    /** What goes and what stays first, then a second confirmation: apps and data are lost. */
    private void explainRemoveAll() {
        new Ui.Sheet(this)
                .title(R.string.remove_all_title)
                .message(R.string.remove_all_body)
                .action(getString(R.string.action_continue), () -> new Ui.Sheet(this)
                        .title(R.string.remove_all_confirm_title)
                        .message(R.string.remove_all_confirm_body)
                        .danger(getString(R.string.settings_remove_all), this::removeAll)
                        .cancel()
                        .show())
                .cancel()
                .show();
    }

    private void removeAll() {
        Ui.work(this, getString(R.string.working_applying), () -> new Controller(this).removeEverything(), log -> {
            boolean failed = log.contains("bubble-shell:");
            new Ui.Sheet(this)
                    .title(failed ? R.string.error_title : R.string.remove_all_done_title)
                    .message(failed ? log.substring(log.indexOf("bubble-shell:")) : getString(R.string.remove_all_done_body))
                    .action(getString(android.R.string.ok), () -> {
                        startActivity(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP));
                        finish();
                    })
                    .show();
        });
    }

    private void showLog() {
        TextView t = Ui.small(this, new Store(this).log());
        t.setTextIsSelectable(true);
        t.setTypeface(android.graphics.Typeface.MONOSPACE);
        new Ui.Sheet(this).title(R.string.settings_log).view(t)
                .action(getString(android.R.string.ok), null).show();
    }

    private String version() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return getString(R.string.settings_version, pi.versionName);
        } catch (Exception e) {
            return "";
        }
    }
}
