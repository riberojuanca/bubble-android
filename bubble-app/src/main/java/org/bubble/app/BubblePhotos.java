package org.bubble.app;

import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Size;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runs in Bubble's copy inside the bubble: reads the bubble's own media provider for
 * {@link BubblePhotosProvider} on the phone. Folders and names cross the bridge; a photo's bytes
 * only when that photo is opened.
 */
final class BubblePhotos {

    private static final Uri IMAGES = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
    private static final String NEWEST = MediaStore.Images.Media.DATE_MODIFIED + " DESC";

    private BubblePhotos() {}

    static Bundle serve(Context c, int what, Bundle args) throws Exception {
        if (c.checkSelfPermission(android.Manifest.permission.READ_MEDIA_IMAGES) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException(c.getString(R.string.photos_no_access));
        }
        waitForStorage(c);
        switch (what) {
            case ProfileBridge.PHOTO_FOLDERS: return folders(c);
            case ProfileBridge.PHOTOS: return photos(c, args.getString(ProfileBridge.ID));
            case ProfileBridge.PHOTO_THUMB: return thumb(c, args.getLong(ProfileBridge.ID), args.getInt(ProfileBridge.BYTES));
            case ProfileBridge.PHOTO_OPEN: return open(c, args.getLong(ProfileBridge.ID));
            default: throw new IllegalArgumentException("Unknown photo operation");
        }
    }

    /**
     * Right after the bubble is unlocked its media provider is still attaching the storage (seen on
     * the Pixel: the query 60 ms too early failed with "Volume external_primary not found"). Wait for
     * it, within the bridge's 10 s answer time, instead of failing.
     */
    private static void waitForStorage(Context c) throws InterruptedException {
        for (int i = 0; i < 25; i++) {
            if (MediaStore.getExternalVolumeNames(c).contains(MediaStore.VOLUME_EXTERNAL_PRIMARY)) {
                try (Cursor probe = c.getContentResolver().query(IMAGES, new String[]{MediaStore.Images.Media._ID},
                        null, null, null)) {
                    return;
                } catch (IllegalArgumentException notYet) {
                    // "Volume external_primary not found": attached a moment later.
                }
            }
            Thread.sleep(300);
        }
    }

    private static Bundle folders(Context c) {
        // bucket id -> {name, count, newest date}, in newest-first order.
        Map<String, Object[]> buckets = new LinkedHashMap<>();
        String[] projection = {MediaStore.Images.Media.BUCKET_ID, MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATE_MODIFIED};
        try (Cursor cur = c.getContentResolver().query(IMAGES, projection, null, null, NEWEST)) {
            while (cur != null && cur.moveToNext()) {
                String id = cur.getString(0);
                if (id == null) continue;
                Object[] b = buckets.get(id);
                if (b == null) buckets.put(id, new Object[]{cur.getString(1), 1, cur.getLong(2)});
                else b[1] = (Integer) b[1] + 1;
            }
        }
        ArrayList<String> ids = new ArrayList<>(), names = new ArrayList<>();
        int[] counts = new int[buckets.size()];
        long[] dates = new long[buckets.size()];
        int i = 0;
        for (Map.Entry<String, Object[]> e : buckets.entrySet()) {
            ids.add(e.getKey());
            names.add(e.getValue()[0] == null ? e.getKey() : (String) e.getValue()[0]);
            counts[i] = (Integer) e.getValue()[1];
            dates[i++] = (Long) e.getValue()[2];
        }
        Bundle out = new Bundle();
        out.putStringArrayList(ProfileBridge.IDS, ids);
        out.putStringArrayList(ProfileBridge.NAMES, names);
        out.putIntArray(ProfileBridge.COUNTS, counts);
        out.putLongArray(ProfileBridge.DATES, dates);
        return out;
    }

    private static Bundle photos(Context c, String folder) {
        if (folder == null) throw new IllegalArgumentException("Missing folder");
        String[] projection = {MediaStore.Images.Media._ID, MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.MIME_TYPE, MediaStore.Images.Media.SIZE, MediaStore.Images.Media.DATE_MODIFIED};
        ArrayList<String> names = new ArrayList<>(), mimes = new ArrayList<>();
        ArrayList<Long> ids = new ArrayList<>(), sizes = new ArrayList<>(), dates = new ArrayList<>();
        try (Cursor cur = c.getContentResolver().query(IMAGES, projection,
                MediaStore.Images.Media.BUCKET_ID + "=?", new String[]{folder}, NEWEST)) {
            while (cur != null && cur.moveToNext()) {
                ids.add(cur.getLong(0));
                names.add(cur.getString(1));
                mimes.add(cur.getString(2));
                sizes.add(cur.getLong(3));
                dates.add(cur.getLong(4));
            }
        }
        Bundle out = new Bundle();
        out.putLongArray(ProfileBridge.IDS, longs(ids));
        out.putStringArrayList(ProfileBridge.NAMES, names);
        out.putStringArrayList(ProfileBridge.MIMES, mimes);
        out.putLongArray(ProfileBridge.SIZES, longs(sizes));
        out.putLongArray(ProfileBridge.DATES, longs(dates));
        return out;
    }

    private static Bundle thumb(Context c, long id, int size) throws Exception {
        int side = Math.max(64, Math.min(size, 512));
        Bitmap bitmap = c.getContentResolver().loadThumbnail(ContentUris.withAppendedId(IMAGES, id),
                new Size(side, side), null);
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, jpeg);
        bitmap.recycle();
        Bundle out = new Bundle();
        out.putByteArray(ProfileBridge.BYTES, jpeg.toByteArray());
        return out;
    }

    private static Bundle open(Context c, long id) throws Exception {
        Bundle out = new Bundle();
        out.putParcelable(ProfileBridge.FILE,
                c.getContentResolver().openFileDescriptor(ContentUris.withAppendedId(IMAGES, id), "r"));
        return out;
    }

    private static long[] longs(ArrayList<Long> list) {
        long[] out = new long[list.size()];
        for (int i = 0; i < out.length; i++) out[i] = list.get(i);
        return out;
    }
}
