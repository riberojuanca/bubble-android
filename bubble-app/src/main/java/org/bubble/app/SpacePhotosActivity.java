package org.bubble.app;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * The photos of the other spaces (from the phone: the bubble and the Meta space; from one of them:
 * the other), album by album, through {@link SpaceChannel}. Any app that asks for an image
 * (ACTION_PICK, ACTION_GET_CONTENT) can choose Bubble here and gets only the picked photo, read
 * through {@link SpacesProvider}. Opened from Bubble, a photo becomes the wallpaper through
 * Android's own crop-and-set screen. A locked space is opened right here.
 */
public final class SpacePhotosActivity extends Activity {

    private static final int COLUMNS = 3;

    /** Opened from the "Files" menu: tapping a bubble photo offers moving it to Work. */
    static final String EXTRA_MANAGE = "manage";

    private LinearLayout list;
    private boolean picking;
    private boolean manage;
    /** The album shown, as a document id, or null for the list of albums. */
    private String album;
    /** Android's unlock screen was asked for; it covers this one until confirmed or cancelled. */
    private boolean unlocking;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String action = getIntent().getAction();
        picking = Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action);
        manage = getIntent().getBooleanExtra(EXTRA_MANAGE, false);
        LinearLayout page = Ui.screen(this, getString(R.string.photos_root), null, null);
        page.addView(Ui.body(this, getString(picking ? R.string.photos_pick_hint
                : manage ? R.string.photos_manage_hint : R.string.photos_wallpaper_hint)));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        page.addView(list);
        showAlbums();
    }

    /** Android's unlock screen covered this one (so coming back means it closed). */
    private boolean covered;

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
        // Not on the first resume: the unlock starts in onCreate, before its prompt is up.
        if (unlocking && covered) Controller.unlockPromptClosed();
        covered = false;
    }

    /** While an album is shown, back returns to the albums instead of closing. */
    private final android.window.OnBackInvokedCallback backToAlbums = this::showAlbums;

    private void backGoesToAlbums(boolean on) {
        android.window.OnBackInvokedDispatcher back = getOnBackInvokedDispatcher();
        back.unregisterOnBackInvokedCallback(backToAlbums);
        if (on) back.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backToAlbums);
    }

    /** Each space with its albums; a locked one first asks Android to open it here. */
    private void showAlbums() {
        album = null;
        backGoesToAlbums(false);
        list.removeAllViews();
        Ui.work(this, getString(R.string.working_reading), () -> {
            List<Object[]> spaces = new ArrayList<>();
            for (SpaceChannel.Space space : SpaceChannel.spaces(this)) {
                spaces.add(new Object[]{space, space.locked ? null : SpaceChannel.list(this, space.name, SpaceStore.PHOTOS)});
            }
            return spaces;
        }, spaces -> {
            if (spaces.isEmpty()) {
                list.addView(Ui.body(this, getString(R.string.photos_no_access)));
                return;
            }
            for (Object[] s : spaces) {
                SpaceChannel.Space space = (SpaceChannel.Space) s[0];
                @SuppressWarnings("unchecked") List<SpaceChannel.Entry> albums = (List<SpaceChannel.Entry>) s[1];
                if (spaces.size() > 1) list.addView(Ui.title(this, getString(space.title())));
                if (albums == null) {
                    Ui.row(list, getString(R.string.space_open), getString(R.string.space_locked), v -> unlock(space));
                } else if (albums.isEmpty()) {
                    list.addView(Ui.body(this, getString(R.string.photos_none)));
                } else {
                    for (SpaceChannel.Entry a : albums) {
                        Ui.row(list, a.name, a.summary, v -> showPhotos(space.name, a));
                    }
                }
            }
        });
    }

    private void unlock(SpaceChannel.Space space) {
        unlocking = true;
        Ui.work(this, getString(R.string.working_unlocking), () -> {
            // The phone's copy waits for the bubble's prompt to be confirmed; elsewhere Android is asked and the list reloads.
            if (SpaceChannel.onPhone() && SpaceChannel.BUBBLE.equals(space.name)) return new Controller(this).unlockBubble();
            SpaceChannel.unlock(this, space.name);
            for (int i = 0; i < 30; i++) {
                Thread.sleep(1000);
                for (SpaceChannel.Space now : SpaceChannel.spaces(this)) if (now.name.equals(space.name) && !now.locked) return true;
            }
            return false;
        }, unlocked -> {
            unlocking = false;
            if (unlocked) showAlbums();
            else android.widget.Toast.makeText(this, R.string.grid_still_locked, android.widget.Toast.LENGTH_SHORT).show();
        });
    }

    /** One album's photos as a grid of thumbnails, newest first. */
    private void showPhotos(String space, SpaceChannel.Entry entry) {
        Ui.work(this, getString(R.string.working_reading), () -> SpaceChannel.list(this, space, entry.id), items -> {
            album = SpacesProvider.documentId(space, entry.id);
            backGoesToAlbums(true);
            list.removeAllViews();
            list.addView(Ui.title(this, entry.name));
            List<SpaceChannel.Entry> photos = new ArrayList<>();
            for (SpaceChannel.Entry item : items) if (item.mime.startsWith("image/")) photos.add(item);
            GridLayout grid = new GridLayout(this);
            grid.setColumnCount(COLUMNS);
            list.addView(grid);
            int gap = Ui.dp(this, 4);
            int side = (getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 40)) / COLUMNS - gap;
            List<ImageView> cells = new ArrayList<>();
            for (SpaceChannel.Entry photo : photos) {
                ImageView cell = new ImageView(this);
                cell.setScaleType(ImageView.ScaleType.CENTER_CROP);
                cell.setBackgroundColor(0x22888888);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = side;
                lp.height = side;
                lp.setMargins(0, 0, gap, gap);
                cell.setLayoutParams(lp);
                cell.setOnClickListener(v -> picked(SpacesProvider.documentId(space, photo.id)));
                grid.addView(cell);
                cells.add(cell);
            }
            loadThumbs(space, photos, cells, side);
        });
    }

    /** Thumbnails one by one off the main thread, each made in the photo's own space. */
    private void loadThumbs(String space, List<SpaceChannel.Entry> photos, List<ImageView> cells, int side) {
        String shown = album;
        new Thread(() -> {
            for (int i = 0; i < photos.size(); i++) {
                if (isFinishing() || isDestroyed() || shown == null || !shown.equals(album)) return;
                try {
                    byte[] jpeg = SpaceChannel.thumb(this, space, photos.get(i).id, side);
                    android.graphics.Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                    ImageView cell = cells.get(i);
                    runOnUiThread(() -> cell.setImageBitmap(bitmap));
                } catch (Exception e) {
                    // A photo without a thumbnail stays a grey tile; it can still be picked.
                }
            }
        }, "bubble-photo-thumbs").start();
    }

    /** Move a photo of the bubble into Work; a photo already in Work has nowhere to go. */
    private void offerMove(String documentId) {
        int slash = documentId.indexOf('/');
        String space = documentId.substring(0, slash);
        String ref = documentId.substring(slash + 1);
        if (SpaceChannel.META.equals(space)) {
            android.widget.Toast.makeText(this, R.string.move_already_work, android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        if (!SpaceChannel.BUBBLE.equals(space)) return;
        int fromUser = new Store(this).bubbleUser();
        new Ui.Sheet(this).title(R.string.move_title).message(R.string.move_body)
                .action(getString(R.string.move_action), () -> Ui.work(this, getString(R.string.working_applying),
                        () -> new Controller(this).moveToWork(fromUser, ref), log -> {
                            android.widget.Toast.makeText(this, R.string.move_done, android.widget.Toast.LENGTH_SHORT).show();
                            showAlbums();
                        }))
                .cancel().show();
    }

    private void picked(String documentId) {
        Uri uri = DocumentsContract.buildDocumentUri(SpacesProvider.AUTHORITY, documentId);
        if (picking) {
            setResult(RESULT_OK, new Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            finish();
            return;
        }
        if (manage) {
            offerMove(documentId);
            return;
        }
        // Android's own wallpaper screen: preview, crop, home or lock screen. It reads only this photo.
        try {
            Intent set = WallpaperManager.getInstance(this).getCropAndSetWallpaperIntent(uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(set);
        } catch (Exception e) {
            Ui.alert(this, getString(R.string.error_title), String.valueOf(e.getMessage()));
        }
    }
}
