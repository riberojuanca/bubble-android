package org.bubble.app;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract.Document;
import android.provider.MediaStore;
import android.util.Size;
import android.webkit.MimeTypeMap;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * One space (the bubble or the Meta space) as Bubble's copy there sees it, for {@link SpaceChannel}:
 * its photos by album and its whole storage as folders. Names and types cross the bridge; a file's
 * bytes only when that file is opened. Read-only. Local ids:
 * <ul>
 *   <li>{@code ""}: the space, with {@link #PHOTOS} and {@link #FILES} inside;</li>
 *   <li>{@code photos}, {@code album:<bucket id>}, {@code image:<id>}, {@code video:<id>}: the media store;</li>
 *   <li>{@code files:<path>}: a file or folder of the space's own storage, relative to its top.</li>
 * </ul>
 */
final class SpaceStore {

    static final String PHOTOS = "photos", ALBUM = "album:", IMAGE = "image:", VIDEO = "video:", FILES = "files:";
    /** Entries per answer: a Binder answer must stay well under 1 MB. */
    static final int PAGE = 500;
    static final String FROM = "from", MIME = "mime", FLAGS = "flags";

    private static final Uri MEDIA = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY);
    private static final String VISUAL = MediaStore.Files.FileColumns.MEDIA_TYPE + " IN ("
            + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE + "," + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO + ")";
    private static final String NEWEST = MediaStore.MediaColumns.DATE_MODIFIED + " DESC";

    private SpaceStore() {}

    static Bundle serve(Context c, int what, Bundle args) throws Exception {
        waitForStorage(c);
        String id = args.getString(ProfileBridge.ID, "");
        switch (what) {
            case ProfileBridge.SPACE_LIST: return list(c, id, args.getInt(FROM));
            case ProfileBridge.SPACE_OPEN: return open(c, id);
            case ProfileBridge.SPACE_THUMB: return thumb(c, id, args.getInt(ProfileBridge.BYTES));
            default: throw new IllegalArgumentException("Unknown space operation");
        }
    }

    /**
     * Right after the space is unlocked its media provider is still attaching the storage (seen on
     * the Pixel: the query 60 ms too early failed with "Volume external_primary not found"). Wait for
     * it, within the bridge's 10 s answer time, instead of failing.
     */
    private static void waitForStorage(Context c) throws InterruptedException {
        for (int i = 0; i < 25; i++) {
            if (MediaStore.getExternalVolumeNames(c).contains(MediaStore.VOLUME_EXTERNAL_PRIMARY)) {
                try (Cursor probe = c.getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        new String[]{MediaStore.Images.Media._ID}, null, null, null)) {
                    return;
                } catch (IllegalArgumentException notYet) {
                    // "Volume external_primary not found": attached a moment later.
                }
            }
            Thread.sleep(300);
        }
    }

    /** Up to {@link #PAGE} children of {@code id}, from the {@code from}-th: ids, names, types, sizes, dates, flags. */
    private static Bundle list(Context c, String id, int from) throws Exception {
        Rows rows = new Rows(from);
        if (id.isEmpty()) {
            rows.dir(PHOTOS, c.getString(R.string.space_photos), 0);
            rows.dir(FILES, c.getString(R.string.space_files), 0);
        } else if (id.equals(PHOTOS)) {
            albums(c, rows);
        } else if (id.startsWith(ALBUM)) {
            album(c, id.substring(ALBUM.length()), rows);
        } else if (id.startsWith(FILES)) {
            File dir = file(id);
            String[] names = dir.list();
            if (names == null) throw new FileNotFoundException(id);
            Arrays.sort(names, String.CASE_INSENSITIVE_ORDER);
            String prefix = id.equals(FILES) ? FILES : id + "/";
            for (String name : names) {
                if (name.startsWith(".")) continue;
                File f = new File(dir, name);
                if (f.isDirectory()) rows.dir(prefix + name, name, f.lastModified());
                else rows.file(prefix + name, name, mime(name), f.length(), f.lastModified());
            }
        } else {
            throw new FileNotFoundException(id);
        }
        return rows.bundle();
    }

    private static void albums(Context c, Rows rows) {
        // bucket id -> {name, count, newest date}, in newest-first order.
        Map<String, Object[]> buckets = new LinkedHashMap<>();
        String[] projection = {MediaStore.MediaColumns.BUCKET_ID, MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_MODIFIED};
        try (Cursor cur = c.getContentResolver().query(MEDIA, projection, VISUAL, null, NEWEST)) {
            while (cur != null && cur.moveToNext()) {
                String bucket = cur.getString(0);
                if (bucket == null) continue;
                Object[] b = buckets.get(bucket);
                if (b == null) buckets.put(bucket, new Object[]{cur.getString(1), 1, cur.getLong(2)});
                else b[1] = (Integer) b[1] + 1;
            }
        }
        for (Map.Entry<String, Object[]> e : buckets.entrySet()) {
            Object[] b = e.getValue();
            rows.album(ALBUM + e.getKey(), b[0] == null ? e.getKey() : (String) b[0], (Long) b[2] * 1000,
                    c.getResources().getQuantityString(R.plurals.photos_count, (Integer) b[1], b[1]));
        }
    }

    private static void album(Context c, String bucket, Rows rows) {
        String[] projection = {MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.MEDIA_TYPE};
        try (Cursor cur = c.getContentResolver().query(MEDIA, projection,
                VISUAL + " AND " + MediaStore.MediaColumns.BUCKET_ID + "=?", new String[]{bucket}, NEWEST)) {
            while (cur != null && cur.moveToNext()) {
                boolean video = cur.getInt(5) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO;
                String mime = cur.getString(2) != null ? cur.getString(2) : video ? "video/*" : "image/*";
                rows.file((video ? VIDEO : IMAGE) + cur.getLong(0), cur.getString(1), mime, cur.getLong(3),
                        cur.getLong(4) * 1000);
            }
        }
    }

    private static Bundle open(Context c, String id) throws Exception {
        Bundle out = new Bundle();
        out.putParcelable(ProfileBridge.FILE, id.startsWith(FILES)
                ? ParcelFileDescriptor.open(file(id), ParcelFileDescriptor.MODE_READ_ONLY)
                : c.getContentResolver().openFileDescriptor(media(id), "r"));
        return out;
    }

    private static Bundle thumb(Context c, String id, int size) throws Exception {
        int side = Math.max(64, Math.min(size, 512));
        Size wanted = new Size(side, side);
        Bitmap bitmap;
        if (id.startsWith(FILES)) {
            File f = file(id);
            String mime = mime(f.getName());
            if (mime.startsWith("image/")) bitmap = android.media.ThumbnailUtils.createImageThumbnail(f, wanted, null);
            else if (mime.startsWith("video/")) bitmap = android.media.ThumbnailUtils.createVideoThumbnail(f, wanted, null);
            else throw new FileNotFoundException("No thumbnail for " + id);
        } else {
            bitmap = c.getContentResolver().loadThumbnail(media(id), wanted, null);
        }
        ByteArrayOutputStream jpeg = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, jpeg);
        bitmap.recycle();
        Bundle out = new Bundle();
        out.putByteArray(ProfileBridge.BYTES, jpeg.toByteArray());
        return out;
    }

    private static Uri media(String id) throws FileNotFoundException {
        try {
            if (id.startsWith(IMAGE)) {
                return ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Long.parseLong(id.substring(IMAGE.length())));
            }
            if (id.startsWith(VIDEO)) {
                return ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, Long.parseLong(id.substring(VIDEO.length())));
            }
        } catch (NumberFormatException ignored) {
            // Falls through to "not found".
        }
        throw new FileNotFoundException(id);
    }

    /** A path of this space's own storage; never outside it (no "..", no symlink out). */
    private static File file(String id) throws Exception {
        File top = Environment.getExternalStorageDirectory().getCanonicalFile();
        String rel = id.substring(FILES.length());
        File f = (rel.isEmpty() ? top : new File(top, rel)).getCanonicalFile();
        if (!f.equals(top) && !f.getPath().startsWith(top.getPath() + File.separator)) throw new FileNotFoundException(id);
        if (!f.exists()) throw new FileNotFoundException(id);
        return f;
    }

    static String mime(String name) {
        int dot = name.lastIndexOf('.');
        String type = dot < 0 ? null : MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase(Locale.ROOT));
        return type != null ? type : "application/octet-stream";
    }

    /** One page of a listing, as parallel arrays. */
    private static final class Rows {
        private final int from;
        private int seen;
        final ArrayList<String> ids = new ArrayList<>(), names = new ArrayList<>(), mimes = new ArrayList<>(),
                summaries = new ArrayList<>();
        final ArrayList<Long> sizes = new ArrayList<>(), dates = new ArrayList<>();
        final ArrayList<Integer> flags = new ArrayList<>();

        Rows(int from) { this.from = from; }

        void dir(String id, String name, long modified) {
            add(id, name, Document.MIME_TYPE_DIR, -1, modified, null, 0);
        }

        void album(String id, String name, long modified, String summary) {
            add(id, name, Document.MIME_TYPE_DIR, -1, modified, summary, 0);
        }

        void file(String id, String name, String mime, long size, long modified) {
            boolean visual = mime.startsWith("image/") || mime.startsWith("video/");
            add(id, name, mime, size, modified, null, visual ? Document.FLAG_SUPPORTS_THUMBNAIL : 0);
        }

        private void add(String id, String name, String mime, long size, long modified, String summary, int flag) {
            if (seen++ < from || ids.size() >= PAGE) return;
            ids.add(id);
            names.add(name == null ? id : name);
            mimes.add(mime);
            sizes.add(size);
            dates.add(modified);
            summaries.add(summary);
            flags.add(flag);
        }

        Bundle bundle() {
            Bundle out = new Bundle();
            out.putStringArrayList(ProfileBridge.IDS, ids);
            out.putStringArrayList(ProfileBridge.NAMES, names);
            out.putStringArrayList(ProfileBridge.MIMES, mimes);
            out.putStringArrayList(ProfileBridge.SUMMARIES, summaries);
            long[] s = new long[sizes.size()], d = new long[dates.size()];
            int[] f = new int[flags.size()];
            for (int i = 0; i < s.length; i++) {
                s[i] = sizes.get(i);
                d[i] = dates.get(i);
                f[i] = flags.get(i);
            }
            out.putLongArray(ProfileBridge.SIZES, s);
            out.putLongArray(ProfileBridge.DATES, d);
            out.putIntArray(FLAGS, f);
            return out;
        }
    }
}
