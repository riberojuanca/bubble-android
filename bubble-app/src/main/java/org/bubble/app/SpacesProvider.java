package org.bubble.app;

import android.app.AuthenticationRequiredException;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Point;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;

import java.io.FileNotFoundException;
import java.io.OutputStream;
import java.util.List;

/**
 * The other spaces in Android's file picker, in every space: on the phone the bubble and the Meta
 * space, in the bubble the Meta space, in the Meta space the bubble. Each one shows its photos by
 * album and its own storage, read where they are through {@link SpaceChannel}; nothing is copied,
 * and only the file the user picks is read. A locked space asks for Android's unlock from the
 * picker itself. Document ids: {@code <space>/<id in that space>} (see {@link SpaceStore}).
 */
public final class SpacesProvider extends DocumentsProvider {

    static final String AUTHORITY = "org.bubble.app.spaces";

    private static final String[] ROOT_COLUMNS = {Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY, Root.COLUMN_ICON, Root.COLUMN_FLAGS, Root.COLUMN_MIME_TYPES};
    private static final String[] DOCUMENT_COLUMNS = {Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_DISPLAY_NAME, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS, Document.COLUMN_SIZE,
            Document.COLUMN_SUMMARY};

    /** Name and type of the entries last listed, so a picked file reports its real ones. */
    private final java.util.Map<String, SpaceChannel.Entry> listed = new java.util.concurrent.ConcurrentHashMap<>();

    @Override public boolean onCreate() { return true; }

    static String documentId(String space, String id) {
        return space + "/" + id;
    }

    @Override
    public Cursor queryRoots(String[] projection) {
        MatrixCursor roots = new MatrixCursor(projection != null ? projection : ROOT_COLUMNS);
        List<SpaceChannel.Space> spaces;
        try {
            spaces = SpaceChannel.spaces(getContext());
        } catch (Exception e) {
            return roots;
        }
        for (SpaceChannel.Space space : spaces) {
            roots.newRow()
                    .add(Root.COLUMN_ROOT_ID, space.name)
                    .add(Root.COLUMN_DOCUMENT_ID, documentId(space.name, ""))
                    .add(Root.COLUMN_TITLE, getContext().getString(space.title()))
                    // Not the state: the picker keeps this line while it is open, so "locked" stayed
                    // after unlocking (seen on the emulator). Opening a locked space says so.
                    .add(Root.COLUMN_SUMMARY, getContext().getString(R.string.space_summary))
                    .add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
                    .add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY)
                    .add(Root.COLUMN_MIME_TYPES, "*/*");
        }
        return roots;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(projection != null ? projection : DOCUMENT_COLUMNS);
        String[] parts = split(documentId);
        SpaceChannel.Entry known = listed.get(documentId);
        if (known == null && parts[1].startsWith(SpaceStore.FILES) && !parts[1].equals(SpaceStore.FILES)) {
            // A file or folder not listed in this process yet: its folder says which it is.
            int slash = parts[1].lastIndexOf('/');
            String parent = slash < 0 ? SpaceStore.FILES : parts[1].substring(0, slash);
            queryChildDocuments(documentId(parts[0], parent), null, (String) null).close();
            known = listed.get(documentId);
        }
        if (known != null) {
            row(result, documentId, known);
        } else if (parts[1].isEmpty()) {
            int title = SpaceChannel.BUBBLE.equals(parts[0]) ? R.string.home_bubble : R.string.home_meta;
            row(result, documentId, new SpaceChannel.Entry("", getContext().getString(title), Document.MIME_TYPE_DIR, null, -1, 0, 0));
        } else if (parts[1].startsWith(SpaceStore.IMAGE) || parts[1].startsWith(SpaceStore.VIDEO)) {
            // A photo or video picked earlier, in a process that has not listed its album.
            String mime = parts[1].startsWith(SpaceStore.IMAGE) ? "image/*" : "video/*";
            row(result, documentId, new SpaceChannel.Entry(parts[1], parts[1], mime, null, -1, 0, Document.FLAG_SUPPORTS_THUMBNAIL));
        } else if (parts[1].equals(SpaceStore.PHOTOS) || parts[1].equals(SpaceStore.FILES) || parts[1].startsWith(SpaceStore.ALBUM)) {
            row(result, documentId, new SpaceChannel.Entry(parts[1], parts[1], Document.MIME_TYPE_DIR, null, -1, 0, 0));
        } else {
            throw new FileNotFoundException(documentId);
        }
        return result;
    }

    @Override
    public Cursor queryChildDocuments(String parentId, String[] projection, String sortOrder) throws FileNotFoundException {
        MatrixCursor result = new MatrixCursor(projection != null ? projection : DOCUMENT_COLUMNS);
        String[] parts = split(parentId);
        try {
            for (SpaceChannel.Entry entry : SpaceChannel.list(getContext(), parts[0], parts[1])) {
                String id = documentId(parts[0], entry.id);
                listed.put(id, entry);
                row(result, id, entry);
            }
        } catch (SpaceChannel.LockedException e) {
            throw unlockFirst(e);
        } catch (FileNotFoundException e) {
            throw e;
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
        if (!"r".equals(mode)) throw new UnsupportedOperationException("Other spaces are read-only");
        String[] parts = split(documentId);
        try {
            return SpaceChannel.open(getContext(), parts[0], parts[1]);
        } catch (SpaceChannel.LockedException e) {
            throw unlockFirst(e);
        } catch (FileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    @Override
    public AssetFileDescriptor openDocumentThumbnail(String documentId, Point sizeHint, CancellationSignal signal)
            throws FileNotFoundException {
        String[] parts = split(documentId);
        try {
            byte[] jpeg = SpaceChannel.thumb(getContext(), parts[0], parts[1], Math.max(sizeHint.x, sizeHint.y));
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            new Thread(() -> {
                try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    out.write(jpeg);
                } catch (java.io.IOException ignored) {
                    // The picker stopped reading (scrolled away): nothing to do.
                }
            }, "bubble-space-thumb").start();
            return new AssetFileDescriptor(pipe[0], 0, AssetFileDescriptor.UNKNOWN_LENGTH);
        } catch (FileNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    /** The picker shows the message with a button that asks Android to open the space. */
    private AuthenticationRequiredException unlockFirst(SpaceChannel.LockedException e) {
        Intent unlock = new Intent(getContext(), SpaceUnlockActivity.class).putExtra(SpaceChannel.SPACE, e.space);
        return new AuthenticationRequiredException(e, PendingIntent.getActivity(getContext(), e.space.hashCode(), unlock,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
    }

    /** {space, id in that space}. */
    private static String[] split(String documentId) throws FileNotFoundException {
        int slash = documentId.indexOf('/');
        if (slash <= 0) throw new FileNotFoundException(documentId);
        String space = documentId.substring(0, slash);
        if (!SpaceChannel.BUBBLE.equals(space) && !SpaceChannel.META.equals(space)) throw new FileNotFoundException(documentId);
        return new String[]{space, documentId.substring(slash + 1)};
    }

    private static void row(MatrixCursor cursor, String id, SpaceChannel.Entry entry) {
        cursor.newRow()
                .add(Document.COLUMN_DOCUMENT_ID, id)
                .add(Document.COLUMN_MIME_TYPE, entry.mime)
                .add(Document.COLUMN_DISPLAY_NAME, entry.name)
                .add(Document.COLUMN_LAST_MODIFIED, entry.modified > 0 ? entry.modified : null)
                .add(Document.COLUMN_SIZE, entry.size >= 0 ? entry.size : null)
                .add(Document.COLUMN_SUMMARY, entry.summary)
                .add(Document.COLUMN_FLAGS, entry.flags);
    }
}
