package org.bubble.app;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * The phone's firewall (outside the bubble): on/off, a search box and the full lists, split into
 * the apps the user installed and the system's own, saved from the header. Internet for the
 * bubble's apps lives in Apps (one switch per app), not here.
 */
public final class FirewallActivity extends Activity {

    private Store store;
    private Switch enabled;
    private final List<Ui.CheckRow> rows = new ArrayList<>();

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        store = new Store(this);
        LinearLayout page = Ui.screen(this, getString(R.string.home_firewall), getString(R.string.action_save),
                v -> save());
        page.addView(Ui.body(this, getString(R.string.firewall_intro)));

        enabled = new Switch(this);
        enabled.setText(R.string.firewall_phone_switch);
        enabled.setTextAppearance(android.R.style.TextAppearance_DeviceDefault_Medium);
        enabled.setChecked(store.phoneFirewallOn());
        enabled.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        page.addView(enabled);
        page.addView(Ui.divider(this));

        page.addView(Ui.search(this, this::filter));

        Set<String> wanted = store.wanted();
        List<AppInfo> mine = new ArrayList<>(), system = new ArrayList<>();
        for (AppInfo app : AppInfo.outside(this)) (app.system ? system : mine).add(app);
        section(page, getString(R.string.section_user_apps), mine, wanted);
        section(page, getString(R.string.section_system_apps), system, wanted);
    }

    private void section(LinearLayout page, String title, List<AppInfo> apps, Set<String> wanted) {
        page.addView(Ui.title(this, title));
        List<android.widget.ImageView> icons = new ArrayList<>();
        for (AppInfo app : apps) {
            Ui.CheckRow row = new Ui.CheckRow(this, app.label, app.pkg, wanted.contains(app.pkg));
            rows.add(row);
            icons.add(row.icon);
            page.addView(row.view);
        }
        Ui.loadIcons(this, apps, icons);
    }

    private void filter(String query) {
        for (Ui.CheckRow row : rows) row.view.setVisibility(row.matches(query) ? View.VISIBLE : View.GONE);
    }

    private void save() {
        Set<String> chosen = new TreeSet<>();
        for (Ui.CheckRow row : rows) if (row.box.isChecked()) chosen.add(row.pkg);
        boolean on = enabled.isChecked();
        Ui.work(this, getString(R.string.working_applying), () -> new Controller(this).applyPhone(chosen, on),
                log -> finish());
    }
}
