package org.bubble.app;

import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Point;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsContract;
import android.provider.DocumentsProvider;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.util.ArrayList;

/**
 * "Bubble photos" in the phone's file picker (Wallpaper → My photos → Browse, or any app that asks
 * for a file): the bubble's photo folders, listed and opened through {@link ProfileBridge}. Nothing
 * is copied to the phone; only the photo the user picks is read, and only while the bubble is
 * unlocked. Document ids: {@code root}, {@code folder:<bucket id>}, {@code photo:<media id>}.
 */
public final class BubblePhotosProvider extends DocumentsProvider {

    private static final String ROOT = "root", FOLDER = "folder:", PHOTO = "photo:";

    private static final String[] ROOT_COLUMNS = {Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY, Root.COLUMN_ICON, Root.COLUMN_FLAGS, Root.COLUMN_MIME_TYPES};
    private static final String[] DOCUMENT_COLUMNS = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS, Document.COLUMN_SIZE,
            Document.COLUMN_SUMMARY};

    /** Name and type of the photos last listed, so a picked photo reports its real type. */
    private final java.util.Map<String, String[]> listed = new java.util.concurrent.ConcurrentHashMap<>();

    @Override public boolean onCreate() { return true; }

    /** The bubble, when this is the phone's copy and the profile bridge is set up; -1 otherwise. */
    private int bubble() {
        if (Process.myUid() / 100000 != 0 || !ProfileBridge.available(getContext())) return -1;
        return new Store(getContext()).bubbleUser();
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor roots = new MatrixCursor(projection != null ? projection : ROOT_COLUMNS);
        if (bubble() <= 0) return roots;
        roots.newRow()
                .add(Root.COLUMN_ROOT_ID, ROOT)
                .add(Root.COLUMN_DOCUMENT_ID, ROOT)
                .add(Root.COLUMN_TITLE, getContext().getString(R.string.photos_root))
                .add(Root.COLUMN_SUMMARY, getContext().getString(R.string.photos_root_summary))
                .add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
                .add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY)
                .add(Root.COLUMN_MIME_TYPES, "image/*");
        return roots;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(projection != null ? projection : DOCUMENT_COLUMNS);
        if (documentId.equals(ROOT)) {
            directory(result, ROOT, getContext().getString(R.string.photos_root), 0, null);
        } else if (documentId.startsWith(FOLDER)) {
            // Folder rows are only built from listings; a lone lookup needs only id and type.
            directory(result, documentId, documentId.substring(FOLDER.length()), 0, null);
        } else if (documentId.startsWith(PHOTO)) {
            String[] known = listed.get(documentId);
            result.newRow()
                    .add(Document.COLUMN_DOCUMENT_ID, documentId)
                    .add(Document.COLUMN_MIME_TYPE, known != null ? known[1] : "image/*")
                    .add(Document.COLUMN_DISPLAY_NAME, known != null ? known[0] : documentId.substring(PHOTO.length()))
                    .add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_THUMBNAIL);
        } else {
            throw new FileNotFoundException(documentId);
        }
        return result;
    }

    @Override
    public Cursor queryChildDocuments(String parentId, String[] projection, String sortOrder) {
        MatrixCursor result = new MatrixCursor(projection != null ? projection : DOCUMENT_COLUMNS);
        int user = bubble();
        try {
            if (user <= 0) throw new FileNotFoundException(parentId);
            if (ProfileBridge.locked(getContext(), user)) throw new IllegalStateException(getContext().getString(R.string.photos_locked));
            if (parentId.equals(ROOT)) {
                Bundle folders = ProfileBridge.photoFolders(getContext(), user);
                ArrayList<String> ids = folders.getStringArrayList(ProfileBridge.IDS);
                ArrayList<String> names = folders.getStringArrayList(ProfileBridge.NAMES);
                int[] counts = folders.getIntArray(ProfileBridge.COUNTS);
                long[] dates = folders.getLongArray(ProfileBridge.DATES);
                for (int i = 0; ids != null && i < ids.size(); i++) {
                    directory(result, FOLDER + ids.get(i), names.get(i), dates[i] * 1000,
                            getContext().getResources().getQuantityString(R.plurals.photos_count, counts[i], counts[i]));
                }
            } else if (parentId.startsWith(FOLDER)) {
                Bundle photos = ProfileBridge.photos(getContext(), user, parentId.substring(FOLDER.length()));
                long[] ids = photos.getLongArray(ProfileBridge.IDS);
                ArrayList<String> names = photos.getStringArrayList(ProfileBridge.NAMES);
                ArrayList<String> mimes = photos.getStringArrayList(ProfileBridge.MIMES);
                long[] sizes = photos.getLongArray(ProfileBridge.SIZES);
                long[] dates = photos.getLongArray(ProfileBridge.DATES);
                for (int i = 0; ids != null && i < ids.length; i++) {
                    String mime = mimes.get(i) == null ? "image/*" : mimes.get(i);
                    listed.put(PHOTO + ids[i], new String[]{names.get(i), mime});
                    result.newRow()
                            .add(Document.COLUMN_DOCUMENT_ID, PHOTO + ids[i])
                            .add(Document.COLUMN_MIME_TYPE, mime)
                            .add(Document.COLUMN_DISPLAY_NAME, names.get(i))
                            .add(Document.COLUMN_LAST_MODIFIED, dates[i] * 1000)
                            .add(Document.COLUMN_SIZE, sizes[i])
                            .add(Document.COLUMN_FLAGS, Document.FLAG_SUPPORTS_THUMBNAIL);
                }
            }
        } catch (Exception e) {
            // The file picker shows this text in place of the list.
            Bundle extras = new Bundle();
            extras.putString(DocumentsContract.EXTRA_ERROR, e.getMessage());
            result.setExtras(extras);
        }
        return result;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        if (!"r".equals(mode)) throw new UnsupportedOperationException("Bubble photos are read-only");
        try {
            return ProfileBridge.openPhoto(getContext(), user(), photoId(documentId));
        } catch (FileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public AssetFileDescriptor openDocumentThumbnail(String documentId, Point sizeHint, CancellationSignal signal)
            throws FileNotFoundException {
        try {
            byte[] jpeg = ProfileBridge.photoThumb(getContext(), user(), photoId(documentId),
                    Math.max(sizeHint.x, sizeHint.y));
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            new Thread(() -> {
                try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    out.write(jpeg);
                } catch (java.io.IOException ignored) {
                    // The picker stopped reading (scrolled away): nothing to do.
                }
            }, "bubble-photo-thumb").start();
            return new AssetFileDescriptor(pipe[0], 0, AssetFileDescriptor.UNKNOWN_LENGTH);
        } catch (FileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    private int user() throws FileNotFoundException {
        int user = bubble();
        if (user <= 0) throw new FileNotFoundException("No bubble");
        if (ProfileBridge.locked(getContext(), user)) throw new FileNotFoundException(getContext().getString(R.string.photos_locked));
        return user;
    }

    private static long photoId(String documentId) throws FileNotFoundException {
        if (!documentId.startsWith(PHOTO)) throw new FileNotFoundException(documentId);
        try {
            return Long.parseLong(documentId.substring(PHOTO.length()));
        } catch (NumberFormatException e) {
            throw new FileNotFoundException(documentId);
        }
    }

    private static void directory(MatrixCursor cursor, String id, String name, long modified, String summary) {
        cursor.newRow()
                .add(Document.COLUMN_DOCUMENT_ID, id)
                .add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
                .add(Document.COLUMN_DISPLAY_NAME, name)
                .add(Document.COLUMN_LAST_MODIFIED, modified > 0 ? modified : null)
                .add(Document.COLUMN_SUMMARY, summary)
                .add(Document.COLUMN_FLAGS, 0);
    }
}
