package org.bubble.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * Bubble's gallery, with its own icon in every space: the photos and videos of each space, album by
 * album, read where they are through {@link SpaceChannel}. It opens on its own space; the header
 * switches to another. From the bubble or the Meta space the phone's are never shown (the phone can
 * still be a target of a move). Press and hold to choose several and move them to another space
 * through the engine. A locked space is opened right here.
 */
public final class GalleryActivity extends Activity {

    private static final int COLUMNS = 3;
    private static final int READ_MEDIA = 1;

    private LinearLayout list;
    private TextView headerAction;
    /** This copy's own space first, then the others it reaches. */
    private final List<SpaceChannel.Space> spaces = new ArrayList<>();
    /** The space shown, by name. */
    private String space;
    /** The album shown, or null for the space's albums. */
    private SpaceChannel.Entry album;
    /** Ids (in {@link #space}) chosen to move together. */
    private final LinkedHashSet<String> selected = new LinkedHashSet<>();
    /** Android's unlock screen was asked for; it covers this one until confirmed or cancelled. */
    private boolean unlocking;
    /** Android's unlock screen covered this one (so coming back means it closed). */
    private boolean covered;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        list = Ui.screen(this, getString(R.string.gallery_label), "", v -> onHeaderAction());
        headerAction = findViewById(R.id.screen_action);
        // The back arrow steps back like the gesture: out of choosing, then out of an album.
        findViewById(R.id.screen_back).setOnClickListener(v -> back());
        space = SpaceChannel.own(this).name;
        loadSpaces();
    }

    @Override protected void onPause() {
        super.onPause();
        if (unlocking) {
            covered = true;
            Controller.unlockPromptOpened();
        }
    }

    @Override protected void onResume() {
        super.onResume();
        // Back from Android's unlock screen: a cancel must not leave this screen waiting a minute.
        if (unlocking && covered) Controller.unlockPromptClosed();
        covered = false;
    }

    private final android.window.OnBackInvokedCallback stepBack = this::back;

    /** The gesture back steps back too while something is chosen or an album is open. */
    private void updateBack() {
        android.window.OnBackInvokedDispatcher dispatcher = getOnBackInvokedDispatcher();
        dispatcher.unregisterOnBackInvokedCallback(stepBack);
        if (album != null || !selected.isEmpty()) {
            dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, stepBack);
        }
    }

    private void back() {
        if (!selected.isEmpty()) {
            selected.clear();
            showAlbum(album);
        } else if (album != null) {
            showSpace();
        } else {
            finish();
        }
    }

    private void loadSpaces() {
        Ui.work(this, getString(R.string.working_opening), () -> {
            List<SpaceChannel.Space> found = new ArrayList<>();
            found.add(SpaceChannel.own(this));
            found.addAll(SpaceChannel.spaces(this));
            return found;
        }, found -> {
            spaces.clear();
            spaces.addAll(found);
            showSpace();
        });
    }

    private SpaceChannel.Space current() {
        for (SpaceChannel.Space s : spaces) if (s.name.equals(space)) return s;
        return spaces.get(0);
    }

    /** The header's action: the space shown (tap to switch), or the move while choosing. */
    private void updateHeader() {
        headerAction.setText(selected.isEmpty() ? getString(current().title()) + " ▾"
                : getString(R.string.gallery_move_n, selected.size()));
        updateBack();
    }

    private void onHeaderAction() {
        if (!selected.isEmpty()) {
            chooseTarget();
            return;
        }
        Ui.Sheet sheet = new Ui.Sheet(this).title(R.string.gallery_choose_space);
        for (SpaceChannel.Space s : spaces) {
            sheet.choice(getString(s.title()), null, () -> {
                space = s.name;
                showSpace();
            });
        }
        sheet.cancel().show();
    }

    /** The space's albums; its own space first asks for permission, a locked one to be opened. */
    private void showSpace() {
        album = null;
        selected.clear();
        updateHeader();
        list.removeAllViews();
        SpaceChannel.Space s = current();
        if (SpaceChannel.own(this).name.equals(s.name) && !canReadOwn()) {
            list.addView(Ui.body(this, getString(R.string.gallery_permission)));
            list.addView(Ui.button(this, getString(R.string.gallery_permission_allow), v -> requestPermissions(
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO}, READ_MEDIA)));
            return;
        }
        if (s.locked) {
            Ui.row(list, getString(R.string.space_open), getString(R.string.space_locked), v -> unlock(s));
            return;
        }
        Ui.work(this, getString(R.string.working_opening), () -> SpaceChannel.list(this, s.name, SpaceStore.PHOTOS), albums -> {
            if (!s.name.equals(space) || album != null) return;
            list.removeAllViews();
            list.addView(Ui.body(this, getString(R.string.gallery_hint)));
            if (albums.isEmpty()) list.addView(Ui.body(this, getString(R.string.gallery_empty)));
            for (SpaceChannel.Entry a : albums) Ui.row(list, a.name, a.summary, v -> showAlbum(a));
        });
    }

    private boolean canReadOwn() {
        return checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED;
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (requestCode == READ_MEDIA) showSpace();
    }

    private void unlock(SpaceChannel.Space s) {
        unlocking = true;
        Ui.work(this, getString(R.string.working_unlocking), () -> {
            // The phone's copy waits for the bubble's prompt to be confirmed; elsewhere Android is asked and the list reloads.
            if (SpaceChannel.onPhone() && SpaceChannel.BUBBLE.equals(s.name)) return new Controller(this).unlockBubble();
            SpaceChannel.unlock(this, s.name);
            for (int i = 0; i < 30; i++) {
                Thread.sleep(1000);
                for (SpaceChannel.Space now : SpaceChannel.spaces(this)) if (now.name.equals(s.name) && !now.locked) return true;
            }
            return false;
        }, unlocked -> {
            unlocking = false;
            if (unlocked) loadSpaces();
            else android.widget.Toast.makeText(this, R.string.grid_still_locked, android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    /** One album's photos and videos as a grid of thumbnails, newest first. */
    private void showAlbum(SpaceChannel.Entry entry) {
        String shownSpace = space;
        Ui.work(this, getString(R.string.working_opening), () -> SpaceChannel.list(this, shownSpace, entry.id), items -> {
            if (!shownSpace.equals(space)) return;
            album = entry;
            updateHeader();
            list.removeAllViews();
            list.addView(Ui.title(this, entry.name));
            List<SpaceChannel.Entry> media = new ArrayList<>();
            for (SpaceChannel.Entry item : items) {
                if (item.mime.startsWith("image/") || item.mime.startsWith("video/")) media.add(item);
            }
            GridLayout grid = new GridLayout(this);
            grid.setColumnCount(COLUMNS);
            list.addView(grid);
            int gap = Ui.dp(this, 4);
            int side = (getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 40)) / COLUMNS - gap;
            List<ImageView> cells = new ArrayList<>();
            for (int i = 0; i < media.size(); i++) {
                SpaceChannel.Entry item = media.get(i);
                FrameLayout frame = new FrameLayout(this);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = side;
                lp.height = side;
                lp.setMargins(0, 0, gap, gap);
                frame.setLayoutParams(lp);
                ImageView cell = new ImageView(this);
                cell.setScaleType(ImageView.ScaleType.CENTER_CROP);
                cell.setBackgroundColor(0x22888888);
                frame.addView(cell, new FrameLayout.LayoutParams(side, side));
                if (item.mime.startsWith("video/")) {
                    TextView play = Ui.text(this, "▶", android.R.style.TextAppearance_DeviceDefault_Medium);
                    play.setTextColor(0xFFFFFFFF);
                    play.setShadowLayer(4, 0, 0, 0xAA000000);
                    play.setContentDescription(getString(R.string.gallery_video));
                    FrameLayout.LayoutParams at = new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.BOTTOM);
                    at.setMargins(0, 0, Ui.dp(this, 6), Ui.dp(this, 4));
                    frame.addView(play, at);
                }
                markSelected(frame, selected.contains(item.id));
                int position = i;
                frame.setOnClickListener(v -> {
                    if (selected.isEmpty()) view(media, position);
                    else toggle(item.id, frame);
                });
                frame.setOnLongClickListener(v -> {
                    toggle(item.id, frame);
                    return true;
                });
                grid.addView(frame);
                cells.add(cell);
            }
            loadThumbs(shownSpace, media, cells, side);
        });
    }

    /** Thumbnails one by one off the main thread, each made in its own space. */
    private void loadThumbs(String shownSpace, List<SpaceChannel.Entry> media, List<ImageView> cells, int side) {
        SpaceChannel.Entry shown = album;
        new Thread(() -> {
            for (int i = 0; i < media.size(); i++) {
                if (isFinishing() || isDestroyed() || shown != album || !shownSpace.equals(space)) return;
                try {
                    byte[] jpeg = SpaceChannel.thumb(this, shownSpace, media.get(i).id, side);
                    android.graphics.Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                    ImageView cell = cells.get(i);
                    runOnUiThread(() -> cell.setImageBitmap(bitmap));
                } catch (Exception e) {
                    // Without a thumbnail it stays a grey tile; it still opens.
                }
            }
        }, "bubble-gallery-thumbs").start();
    }

    private void view(List<SpaceChannel.Entry> media, int position) {
        ArrayList<String> ids = new ArrayList<>(), mimes = new ArrayList<>();
        for (SpaceChannel.Entry item : media) {
            ids.add(item.id);
            mimes.add(item.mime);
        }
        startActivity(new Intent(this, GalleryViewerActivity.class)
                .putExtra(GalleryViewerActivity.EXTRA_SPACE, space)
                .putStringArrayListExtra(GalleryViewerActivity.EXTRA_IDS, ids)
                .putStringArrayListExtra(GalleryViewerActivity.EXTRA_MIMES, mimes)
                .putExtra(GalleryViewerActivity.EXTRA_POSITION, position));
    }

    private void toggle(String id, FrameLayout frame) {
        if (!selected.remove(id)) selected.add(id);
        markSelected(frame, selected.contains(id));
        updateHeader();
    }

    private void markSelected(FrameLayout frame, boolean on) {
        frame.setForeground(on ? new android.graphics.drawable.ColorDrawable(0x663B5BDB) : null);
    }

    /** Where the chosen ones can go: every other space, the phone included even from a profile. */
    private void chooseTarget() {
        Ui.Sheet sheet = new Ui.Sheet(this).title(R.string.gallery_move_to).message(R.string.gallery_move_body);
        List<SpaceChannel.Space> targets = new ArrayList<>(spaces);
        if (!SpaceChannel.onPhone()) targets.add(new SpaceChannel.Space(SpaceChannel.PHONE, 0, false));
        for (SpaceChannel.Space target : targets) {
            if (target.name.equals(space)) continue;
            String name = getString(target.title());
            sheet.choice(name, null, () -> {
                if (SpaceChannel.PHONE.equals(target.name)) {
                    new Ui.Sheet(this).title(getString(R.string.gallery_move_confirm, name)).message(R.string.gallery_move_phone)
                            .action(getString(R.string.gallery_move_confirm, name), () -> move(target.name)).cancel().show();
                } else {
                    move(target.name);
                }
            });
        }
        sheet.cancel().show();
    }

    private void move(String target) {
        String from = space;
        List<String> refs = new ArrayList<>(selected);
        Ui.work(this, getString(R.string.working_moving), () -> {
            SpaceChannel.move(this, from, target, refs);
            return refs.size();
        }, moved -> {
            android.widget.Toast.makeText(this, getString(R.string.gallery_moved_n, moved), android.widget.Toast.LENGTH_SHORT).show();
            selected.clear();
            if (album != null && from.equals(space)) showAlbum(album);
            else showSpace();
        });
    }
}
