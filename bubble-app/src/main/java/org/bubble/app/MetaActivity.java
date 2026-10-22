package org.bubble.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * The Meta space: a work profile apart from the bubble, for apps that close without Google
 * (Facebook, Instagram). Without one: why it exists and a button that explains the tradeoff before
 * creating it. With one: its state, its apps (Wi-Fi and mobile data toggles, remove), adding and
 * deleting.
 */
public final class MetaActivity extends Activity {

    private static final int ADD_APPS = 4;
    private TextView state;
    private LinearLayout content;
    private LinearLayout list;
    private boolean loaded;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout page = Ui.screen(this, getString(R.string.home_meta), null, null);
        state = Ui.title(this, "");
        page.addView(state);
        content = Ui.column(this);
        page.addView(content);
        Ui.onPull(this, this::check);
    }

    @Override protected void onStart() {
        super.onStart();
        // Read once on entering; again after a change made here or a pull.
        if (!loaded) {
            loaded = true;
            check();
        }
    }

    private void check() {
        if (state.getText().length() == 0) state.setText(R.string.meta_checking);
        Ui.work(this, getString(R.string.meta_checking), () -> new Controller(this).metaStatus(), this::show);
    }

    private void show(Controller.MetaReport r) {
        content.removeAllViews();
        if (!r.connected) {
            state.setText(R.string.meta_unknown);
            return;
        }
        if (r.user < 0) {
            state.setText(R.string.meta_none);
            content.addView(Ui.body(this, getString(R.string.meta_intro)));
            content.addView(Ui.button(this, getString(R.string.meta_create), v -> explainAndCreate()));
            return;
        }
        state.setText(r.log.contains("meta_locked=true") ? R.string.grid_meta_paused
                : r.googleActive ? R.string.meta_google_on : R.string.meta_ready);
        content.addView(Ui.body(this, getString(R.string.meta_how_to_enter)));
        content.addView(Ui.small(this, getString(R.string.meta_hint)));
        content.addView(Ui.button(this, getString(R.string.meta_add),
                v -> startActivityForResult(AppPickerActivity.intent(this, AppPickerActivity.MODE_META), ADD_APPS)));
        // Its own files, and the other spaces' photos, as for the bubble.
        content.addView(Ui.button(this, getString(R.string.meta_files), v -> openFiles()));
        content.addView(Ui.button(this, getString(R.string.photos_root),
                v -> startActivity(new android.content.Intent(this, SpacePhotosActivity.class))));
        content.addView(Ui.title(this, getString(R.string.meta_inside)));
        list = Ui.column(this);
        content.addView(list);
        content.addView(Ui.dangerButton(this, getString(R.string.meta_delete), v -> confirmDelete(r.user)));
        reloadApps();
    }

    /** Android's file browser inside the Meta space, on its own storage. */
    private void openFiles() {
        Ui.work(this, getString(R.string.working_opening), () -> new Controller(this).openMetaFiles(), log -> {
            if (!log.isEmpty()) Ui.alert(this, getString(R.string.error_title), log);
        });
    }

    /** The tradeoff first: Google installed but off, what is gained and what is accepted. */
    private void explainAndCreate() {
        new Ui.Sheet(this)
                .title(R.string.meta_details_title)
                .message(R.string.meta_details_body)
                .action(getString(R.string.meta_action_create), () -> Ui.work(this, getString(R.string.working_meta),
                        () -> new Controller(this).createMeta(), r -> {
                            if (r.failed()) Ui.alert(this, getString(R.string.error_title), problem(r));
                            show(r);
                        }))
                .cancel()
                .show();
    }

    private void confirmDelete(int user) {
        new Ui.Sheet(this)
                .title(R.string.meta_delete_title)
                .message(R.string.meta_delete_body)
                .danger(getString(R.string.action_delete), () -> Ui.work(this, getString(R.string.working_meta),
                        () -> new Controller(this).deleteMeta(user), r -> {
                            if (r.failed()) Ui.alert(this, getString(R.string.error_title), problem(r));
                            show(r);
                        }))
                .cancel()
                .show();
    }

    private void reloadApps() {
        Ui.work(this, getString(R.string.working_reading),
                () -> AppInfo.inside(this, new Controller(this).metaApps()), this::showApps);
    }

    private void showApps(List<AppInfo> apps) {
        if (list == null) return;
        list.removeAllViews();
        if (apps.isEmpty()) list.addView(Ui.body(this, getString(R.string.apps_none)));
        List<android.widget.ImageView> icons = new java.util.ArrayList<>();
        for (AppInfo app : apps) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(this, 4), Ui.dp(this, 2), 0, Ui.dp(this, 2));
            row.setMinimumHeight(Ui.dp(this, 52));
            android.widget.ImageView icon = Ui.appIcon(this);
            row.addView(icon);
            icons.add(icon);
            row.addView(Ui.listText(this, app.label),
                    new LinearLayout.LayoutParams(0, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            for (boolean wifi : new boolean[]{true, false}) {
                android.widget.ImageView net = Ui.iconButton(this, 0, "", null);
                Ui.showNetwork(this, net, new Store(this).metaNet(wifi).contains(app.pkg), wifi);
                net.setOnClickListener(v -> toggleNetwork(net, app, wifi));
                row.addView(net);
            }
            row.addView(Ui.iconButton(this, R.drawable.ic_delete, getString(R.string.action_remove),
                    v -> confirmRemove(app)));
            LinearLayout cell = Ui.column(this);
            cell.addView(row);
            cell.addView(Ui.divider(this));
            list.addView(cell);
        }
        Ui.loadIcons(this, apps, icons);
    }

    private void toggleNetwork(android.widget.ImageView v, AppInfo app, boolean wifi) {
        Store store = new Store(this);
        java.util.Set<String> allowed = store.metaNet(wifi);
        if (!allowed.remove(app.pkg)) allowed.add(app.pkg);
        store.setMetaNet(wifi, allowed);
        Ui.showNetwork(this, v, allowed.contains(app.pkg), wifi);
        Ui.work(this, getString(R.string.working_applying), () -> new Controller(this).applyMetaNetwork(), log -> { });
    }

    private void confirmRemove(AppInfo app) {
        new Ui.Sheet(this)
                .title(getString(R.string.meta_remove_title, app.label))
                .message(R.string.meta_remove_body)
                .danger(getString(R.string.action_remove), () -> Ui.work(this, getString(R.string.working_applying),
                        () -> new Controller(this).removeMetaApps(java.util.Collections.singleton(app.pkg)),
                        log -> reloadApps()))
                .cancel()
                .show();
    }

    private String problem(Controller.MetaReport r) {
        int at = r.log.indexOf("bubble-shell:");
        return !r.connected ? r.log : at >= 0 ? r.log.substring(at) : getString(R.string.error_generic);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ADD_APPS && resultCode == RESULT_OK) reloadApps();
    }
}
