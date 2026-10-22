package org.bubble.app;

import android.content.Context;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.UserManager;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The channel between spaces: from any of Bubble's copies (the phone, the bubble, the Meta space),
 * the other spaces' photos and files, read where they are through {@link ProfileBridge}. Nothing is
 * copied. The phone's copy calls each space directly; a profile's copy asks the phone's, which
 * passes the call on only to the other space Bubble manages (never the phone's own files). Every
 * screen or provider that needs another space's files goes through here: {@link SpacesProvider} for
 * Android's file picker, {@link SpacePhotosActivity} for "pick an image" and the wallpaper.
 */
final class SpaceChannel {

    static final String BUBBLE = "bubble", META = "meta", SPACE = "space";

    private SpaceChannel() {}

    /** Another space as this copy sees it. */
    static final class Space {
        final String name;
        final int user;
        final boolean locked;

        Space(String name, int user, boolean locked) {
            this.name = name;
            this.user = user;
            this.locked = locked;
        }

        int title() { return BUBBLE.equals(name) ? R.string.home_bubble : R.string.home_meta; }
    }

    /** One file, folder or album. */
    static final class Entry {
        final String id, name, mime, summary;
        final long size, modified;
        final int flags;

        Entry(String id, String name, String mime, String summary, long size, long modified, int flags) {
            this.id = id;
            this.name = name;
            this.mime = mime;
            this.summary = summary;
            this.size = size;
            this.modified = modified;
            this.flags = flags;
        }

        boolean directory() { return android.provider.DocumentsContract.Document.MIME_TYPE_DIR.equals(mime); }
    }

    /** Thrown while the space is locked or paused: Android's own unlock is needed first. */
    static final class LockedException extends IOException {
        final String space;

        LockedException(Context c, String space) {
            super(c.getString(BUBBLE.equals(space) ? R.string.photos_locked : R.string.grid_meta_paused));
            this.space = space;
        }
    }

    static boolean onPhone() {
        return Process.myUid() / 100000 == 0;
    }

    /** The spaces this copy can reach (the others), with their state now. Only off the main thread. */
    static List<Space> spaces(Context c) throws Exception {
        List<Space> out = new ArrayList<>();
        if (!ProfileBridge.available(c)) return out;
        if (onPhone()) {
            Store store = new Store(c);
            for (String name : new String[]{BUBBLE, META}) {
                int user = BUBBLE.equals(name) ? store.bubbleUser() : store.metaUser();
                if (user > 0) out.add(new Space(name, user, !open(c, user)));
            }
            return out;
        }
        Bundle answer = ProfileBridge.call(c, 0, ProfileBridge.SPACES, null);
        ArrayList<String> names = answer.getStringArrayList(ProfileBridge.NAMES);
        int[] users = answer.getIntArray(ProfileBridge.IDS);
        boolean[] locked = answer.getBooleanArray(ProfileBridge.LOCKED);
        for (int i = 0; names != null && users != null && locked != null && i < names.size(); i++) {
            out.add(new Space(names.get(i), users[i], locked[i]));
        }
        return out;
    }

    /** Running and unlocked, so its copy and its storage answer. */
    static boolean open(Context c, int user) {
        return !ProfileBridge.locked(c, user) && c.getSystemService(UserManager.class).isUserUnlocked(ProfileBridge.handle(user));
    }

    /** The children of {@code id} (see {@link SpaceStore} for ids; "" is the space itself). */
    static List<Entry> list(Context c, String space, String id) throws Exception {
        List<Entry> out = new ArrayList<>();
        for (int from = 0; ; from += SpaceStore.PAGE) {
            Bundle args = new Bundle();
            args.putString(ProfileBridge.ID, id);
            args.putInt(SpaceStore.FROM, from);
            Bundle page = call(c, space, ProfileBridge.SPACE_LIST, args);
            ArrayList<String> ids = page.getStringArrayList(ProfileBridge.IDS), names = page.getStringArrayList(ProfileBridge.NAMES),
                    mimes = page.getStringArrayList(ProfileBridge.MIMES), summaries = page.getStringArrayList(ProfileBridge.SUMMARIES);
            long[] sizes = page.getLongArray(ProfileBridge.SIZES), dates = page.getLongArray(ProfileBridge.DATES);
            int[] flags = page.getIntArray(SpaceStore.FLAGS);
            if (ids == null || names == null || mimes == null || summaries == null || sizes == null || dates == null || flags == null) {
                throw new IOException("Invalid listing");
            }
            for (int i = 0; i < ids.size(); i++) {
                out.add(new Entry(ids.get(i), names.get(i), mimes.get(i), summaries.get(i), sizes[i], dates[i], flags[i]));
            }
            if (ids.size() < SpaceStore.PAGE) return out;
        }
    }

    /** One file opened for reading where it is; only this file leaves its space. */
    static ParcelFileDescriptor open(Context c, String space, String id) throws Exception {
        Bundle args = new Bundle();
        args.putString(ProfileBridge.ID, id);
        ParcelFileDescriptor file = call(c, space, ProfileBridge.SPACE_OPEN, args)
                .getParcelable(ProfileBridge.FILE, ParcelFileDescriptor.class);
        if (file == null) throw new IOException("Missing file");
        return file;
    }

    /** A small JPEG of a photo or video, made in its space. */
    static byte[] thumb(Context c, String space, String id, int size) throws Exception {
        Bundle args = new Bundle();
        args.putString(ProfileBridge.ID, id);
        args.putInt(ProfileBridge.BYTES, size);
        byte[] jpeg = call(c, space, ProfileBridge.SPACE_THUMB, args).getByteArray(ProfileBridge.BYTES);
        if (jpeg == null) throw new IOException("Missing thumbnail");
        return jpeg;
    }

    /** Ask Android to open a locked or paused space: its own unlock prompt, when one is needed. */
    static void unlock(Context c, String space) throws Exception {
        if (onPhone()) {
            c.getSystemService(UserManager.class).requestQuietModeEnabled(false, ProfileBridge.handle(user(c, space)));
        } else {
            Bundle args = new Bundle();
            args.putString(SPACE, space);
            ProfileBridge.call(c, 0, ProfileBridge.SPACE_UNLOCK, args);
        }
    }

    private static int user(Context c, String space) throws Exception {
        for (Space s : spaces(c)) if (s.name.equals(space)) return s.user;
        throw new java.io.FileNotFoundException(space);
    }

    private static Bundle call(Context c, String space, int operation, Bundle args) throws Exception {
        Space target = null;
        for (Space s : spaces(c)) if (s.name.equals(space)) target = s;
        if (target == null) throw new java.io.FileNotFoundException(space);
        if (target.locked) throw new LockedException(c, space);
        if (onPhone()) return ProfileBridge.call(c, target.user, operation, args);
        args.putString(SPACE, space);
        return ProfileBridge.call(c, 0, operation, args);
    }

    /**
     * On the phone's copy, for a profile's request: the other space it names. A profile never
     * reaches itself through here, nor the phone.
     */
    static int relayTarget(Context c, int requester, String space) {
        Store store = new Store(c);
        int user = BUBBLE.equals(space) ? store.bubbleUser() : META.equals(space) ? store.metaUser() : -1;
        if (user <= 0 || user == requester) throw new SecurityException("Unknown space");
        return user;
    }

    /** On the phone's copy: the spaces a profile can reach, all but itself. */
    static Bundle spacesFor(Context c, int requester) {
        Store store = new Store(c);
        ArrayList<String> names = new ArrayList<>();
        List<Integer> users = new ArrayList<>();
        for (String name : new String[]{BUBBLE, META}) {
            int user = BUBBLE.equals(name) ? store.bubbleUser() : store.metaUser();
            if (user > 0 && user != requester) {
                names.add(name);
                users.add(user);
            }
        }
        int[] ids = new int[users.size()];
        boolean[] locked = new boolean[users.size()];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = users.get(i);
            locked[i] = !open(c, ids[i]);
        }
        Bundle out = new Bundle();
        out.putStringArrayList(ProfileBridge.NAMES, names);
        out.putIntArray(ProfileBridge.IDS, ids);
        out.putBooleanArray(ProfileBridge.LOCKED, locked);
        return out;
    }
}
