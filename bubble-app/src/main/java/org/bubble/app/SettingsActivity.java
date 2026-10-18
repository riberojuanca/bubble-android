package org.bubble.app;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Rarely needed things: pairing again, the technical log, removing everything, version. */
public final class SettingsActivity extends Activity {

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout page = Ui.screen(this, getString(R.string.home_settings), null, null);
        Ui.row(page, getString(R.string.settings_repair), getString(R.string.settings_repair_sub), v -> {
            startForegroundService(new Intent(this, PairingService.class));
            Ui.alert(this, getString(R.string.setup_pair_title), getString(R.string.setup_pair_next));
        });
        Ui.row(page, getString(R.string.settings_log), getString(R.string.settings_log_sub), v -> showLog());
        Ui.Row shape = Ui.row(page, getString(R.string.settings_icon_shape),
                getString(IconShape.title(new Store(this).iconShape())), null);
        shape.view.setOnClickListener(v -> pickShape(shape));
        Ui.row(page, getString(R.string.settings_updates), getString(R.string.settings_updates_sub),
                v -> startActivity(new Intent(this, UpdateActivity.class)));
        Ui.row(page, getString(R.string.settings_remove_all), getString(R.string.settings_remove_all_sub), v -> explainRemoveAll());
        Ui.row(page, getString(R.string.settings_about), version(), v -> { });
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
                        .action(getString(R.string.settings_remove_all), this::removeAll)
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
