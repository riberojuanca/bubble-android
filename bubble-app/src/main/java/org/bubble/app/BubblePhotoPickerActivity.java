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
 * The bubble's photos: its folders, then a grid of one folder's photos. Any app that asks for an
 * image (ACTION_PICK, ACTION_GET_CONTENT) can choose Bubble here and gets only the picked photo,
 * read through BubblePhotosProvider. Opened from Bubble, a photo becomes the wallpaper through
 * Android's own crop-and-set screen. A locked bubble asks for Android's unlock prompt right here.
 */
public final class BubblePhotoPickerActivity extends Activity {

    private static final String AUTHORITY = "org.bubble.app.photos";
    private static final int COLUMNS = 3;

    private LinearLayout list;
    private boolean picking;
    /** The folder shown, or null for the list of folders. */
    private String folder;
    /** Android's unlock screen was asked for; it covers this one until confirmed or cancelled. */
    private boolean unlocking;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        String action = getIntent().getAction();
        picking = Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action);
        LinearLayout page = Ui.screen(this, getString(R.string.photos_root), null, null);
        page.addView(Ui.body(this, getString(picking ? R.string.photos_pick_hint : R.string.photos_wallpaper_hint)));
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        page.addView(list);
        showFolders();
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

    /** While a folder is shown, back returns to the folders instead of closing. */
    private final android.window.OnBackInvokedCallback backToFolders = this::showFolders;

    private void backGoesToFolders(boolean on) {
        android.window.OnBackInvokedDispatcher back = getOnBackInvokedDispatcher();
        back.unregisterOnBackInvokedCallback(backToFolders);
        if (on) back.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backToFolders);
    }

    private int bubble() {
        return new Store(this).bubbleUser();
    }

    /** The folders, after unlocking the bubble here if it is locked. */
    private void showFolders() {
        folder = null;
        backGoesToFolders(false);
        list.removeAllViews();
        int user = bubble();
        if (user <= 0 || !ProfileBridge.available(this)) {
            list.addView(Ui.body(this, getString(R.string.photos_no_access)));
            return;
        }
        if (ProfileBridge.locked(this, user)) {
            unlocking = true;
            Ui.work(this, getString(R.string.working_unlocking), () -> new Controller(this).unlockBubble(), unlocked -> {
                unlocking = false;
                if (unlocked) {
                    showFolders();
                } else {
                    // Cancelled: back to whoever asked for a photo, with nothing chosen.
                    android.widget.Toast.makeText(this, R.string.grid_still_locked, android.widget.Toast.LENGTH_SHORT).show();
                    finish();
                }
            });
            return;
        }
        Ui.work(this, getString(R.string.working_reading), () -> ProfileBridge.photoFolders(this, user), folders -> {
            ArrayList<String> ids = folders.getStringArrayList(ProfileBridge.IDS);
            ArrayList<String> names = folders.getStringArrayList(ProfileBridge.NAMES);
            int[] counts = folders.getIntArray(ProfileBridge.COUNTS);
            if (ids == null || ids.isEmpty()) {
                list.addView(Ui.body(this, getString(R.string.photos_none)));
                return;
            }
            for (int i = 0; i < ids.size(); i++) {
                String id = ids.get(i), name = names.get(i);
                Ui.row(list, name, getResources().getQuantityString(R.plurals.photos_count, counts[i], counts[i]),
                        v -> showPhotos(id, name));
            }
        });
    }

    /** One folder's photos as a grid of thumbnails, newest first. */
    private void showPhotos(String id, String name) {
        int user = bubble();
        Ui.work(this, getString(R.string.working_reading), () -> ProfileBridge.photos(this, user, id), photos -> {
            folder = id;
            backGoesToFolders(true);
            list.removeAllViews();
            list.addView(Ui.title(this, name));
            long[] ids = photos.getLongArray(ProfileBridge.IDS);
            GridLayout grid = new GridLayout(this);
            grid.setColumnCount(COLUMNS);
            list.addView(grid);
            int gap = Ui.dp(this, 4);
            int side = (getResources().getDisplayMetrics().widthPixels - Ui.dp(this, 40)) / COLUMNS - gap;
            List<ImageView> cells = new ArrayList<>();
            for (int i = 0; ids != null && i < ids.length; i++) {
                long photo = ids[i];
                ImageView cell = new ImageView(this);
                cell.setScaleType(ImageView.ScaleType.CENTER_CROP);
                cell.setBackgroundColor(0x22888888);
                GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
                lp.width = side;
                lp.height = side;
                lp.setMargins(0, 0, gap, gap);
                cell.setLayoutParams(lp);
                cell.setOnClickListener(v -> picked(photo));
                grid.addView(cell);
                cells.add(cell);
            }
            loadThumbs(user, ids, cells, side);
        });
    }

    /** Thumbnails one by one off the main thread, each straight from the bubble's media provider. */
    private void loadThumbs(int user, long[] ids, List<ImageView> cells, int side) {
        String shown = folder;
        new Thread(() -> {
            for (int i = 0; ids != null && i < ids.length; i++) {
                if (isFinishing() || isDestroyed() || shown == null || !shown.equals(folder)) return;
                try {
                    byte[] jpeg = ProfileBridge.photoThumb(this, user, ids[i], side);
                    android.graphics.Bitmap bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.length);
                    ImageView cell = cells.get(i);
                    runOnUiThread(() -> cell.setImageBitmap(bitmap));
                } catch (Exception e) {
                    // A photo without a thumbnail stays a grey tile; it can still be picked.
                }
            }
        }, "bubble-photo-thumbs").start();
    }

    private void picked(long photo) {
        Uri uri = DocumentsContract.buildDocumentUri(AUTHORITY, "photo:" + photo);
        if (picking) {
            setResult(RESULT_OK, new Intent().setData(uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
            finish();
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
