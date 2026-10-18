package org.bubble.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Adding apps from the phone to the bubble (or to the Meta space): a search box and the apps the
 * user installed (never one already inside), added from the header.
 */
public final class AppPickerActivity extends Activity {

    static final String EXTRA_MODE = "mode";
    static final int MODE_ADD = 2;
    static final int MODE_META = 3;

    private int mode;
    private Store store;
    private final List<Ui.CheckRow> rows = new ArrayList<>();
    private LinearLayout list;

    static Intent intent(Activity from, int mode) {
        return new Intent(from, AppPickerActivity.class).putExtra(EXTRA_MODE, mode);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mode = getIntent().getIntExtra(EXTRA_MODE, MODE_ADD);
        store = new Store(this);
        String title = getString(mode == MODE_META ? R.string.picker_meta_title : R.string.picker_add_title);
        String action = getString(R.string.action_add);
        LinearLayout root = Ui.screen(this, title, action, v -> save(checked()));
        root.addView(Ui.small(this, getString(R.string.picker_add_hint)));
        root.addView(Ui.search(this, this::filter));
        list = Ui.column(this);
        root.addView(list);
        Ui.onPull(this, () -> {
            BubbleCache.clear();
            load();
        });
        load();
    }

    private void load() {
        Ui.work(this, getString(R.string.working_reading), () -> {
            Set<String> inside = new TreeSet<>();
            Controller c = new Controller(this);
            for (String[] app : mode == MODE_META ? c.metaApps() : c.bubbleApps()) inside.add(app[0]);
            List<AppInfo> addable = new ArrayList<>();
            for (AppInfo app : AppInfo.outside(this)) {
                // Apps the user installed; never one already inside.
                if (!app.system && !inside.contains(app.pkg)) addable.add(app);
            }
            return addable;
        }, apps -> show(apps, new TreeSet<>()));
    }

    private void show(List<AppInfo> apps, Set<String> checked) {
        list.removeAllViews();
        rows.clear();
        if (apps.isEmpty()) list.addView(Ui.body(this, getString(R.string.picker_empty)));
        List<android.widget.ImageView> icons = new ArrayList<>();
        for (AppInfo app : apps) {
            Ui.CheckRow row = new Ui.CheckRow(this, app.label, app.pkg, checked.contains(app.pkg));
            rows.add(row);
            icons.add(row.icon);
            list.addView(row.view);
        }
        Ui.loadIcons(this, apps, icons);
    }

    private void filter(String query) {
        for (Ui.CheckRow row : rows) row.view.setVisibility(row.matches(query) ? android.view.View.VISIBLE : android.view.View.GONE);
    }

    private Set<String> checked() {
        Set<String> chosen = new TreeSet<>();
        for (Ui.CheckRow row : rows) if (row.box.isChecked()) chosen.add(row.pkg);
        return chosen;
    }

    private void save(Set<String> chosen) {
        if (chosen.isEmpty()) {
            finish();
            return;
        }
        Ui.Job<String> job = mode == MODE_META ? () -> new Controller(this).addMetaApps(chosen)
                : () -> new Controller(this).addApps(chosen);
        Ui.work(this, getString(R.string.working_applying), job, log -> {
            setResult(RESULT_OK);
            finish();
        });
    }
}
