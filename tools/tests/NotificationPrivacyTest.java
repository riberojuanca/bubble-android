package org.bubble.shell;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Set;

/** Fault-injection checks for the host hardening transaction; never connects to Android. */
public final class NotificationPrivacyTest {
    private static final String OWN = "org.bubble.app/org.bubble.app.AdbNoticeListener";
    private static final String READER = "example.reader/example.reader.Listener";
    private static final String ASSISTANT = "example.reader/example.reader.Assistant";

    public static void main(String[] args) throws Exception {
        String inventory = "Packages:\n  Package [android] (a):\n  Package [example.shared] (b):\n"
                + "  Package [example.last] (c):\nHidden system packages:\n  Package [example.old] (d):\n"
                + "    android.permission.ACCESS_NOTIFICATIONS: granted=true\nShared users:\n"
                + "  SharedUser [example.uid] (e):\n    Packages\n      PackageSetting{abc example.shared/12345}\n"
                + "    android.permission.ACCESS_NOTIFICATIONS: granted=true\nOther section:\n"
                + "    android.permission.DUMP: granted=true\n";
        require(NotificationPrivacy.permissionHolders(inventory).equals(
                new LinkedHashSet<>(java.util.Arrays.asList("example.shared"))));
        reject(() -> PackagePermissions.parse("Permission Denial"));
        require(NotificationPrivacy.components("org.bubble.app/.AdbNoticeListener").contains(OWN));
        reject(() -> NotificationPrivacy.components("Error: permission denied"));
        require(NotificationPrivacy.mode("ACCESS_NOTIFICATIONS: ignore; time=+5s").equals("ignore"));
        require(NotificationPrivacy.mode("No operations.\nDefault mode: default").equals("default"));
        require(NotificationPrivacy.mode("Uid mode: ACCESS_NOTIFICATIONS: allow\nACCESS_NOTIFICATIONS: ignore").equals("allow"));
        reject(() -> NotificationPrivacy.mode("Unknown operation"));
        reject(() -> NotificationPrivacy.mode("Uid mode: ACCESS_NOTIFICATIONS: unknown\nACCESS_NOTIFICATIONS: ignore"));

        Fake device = new Fake();
        File journal = journal();
        NotificationPrivacy privacy = new NotificationPrivacy(device, journal);
        privacy.enforce();
        require(device.listeners.equals(new LinkedHashSet<>(java.util.Arrays.asList(OWN))));
        require(device.assistant.equals("null") && device.mode.equals("ignore"));
        // Repeated enforcement must not replace the original permissions with restricted ones.
        new NotificationPrivacy(device, journal).enforce();
        new NotificationPrivacy(device, journal).restore();
        require(device.listeners.contains(READER) && device.assistant.equals(ASSISTANT));
        require(device.mode.equals("allow") && !journal.exists());

        device = new Fake();
        File restoreJournal = journal();
        new NotificationPrivacy(device, restoreJournal).enforce();
        device.ignoreAppOp = true;
        Fake failedRestore = device;
        reject(() -> new NotificationPrivacy(failedRestore, restoreJournal).restore());
        require(restoreJournal.exists());

        device = new Fake();
        device.uidOverride = true;
        Fake uidOverride = device;
        reject(() -> new NotificationPrivacy(uidOverride, journalUnchecked()).enforce());
        require(device.mode.equals("allow"));

        device = new Fake();
        device.ignoreRevoke = true;
        Fake noRevoke = device;
        File failedJournal = journal();
        reject(() -> new NotificationPrivacy(noRevoke, failedJournal).enforce());
        require(failedJournal.exists());

        device = new Fake();
        device.ignoreAppOp = true;
        Fake noAppOp = device;
        reject(() -> new NotificationPrivacy(noAppOp, journalUnchecked()).enforce());

        device = new Fake();
        device.failedInventory = true;
        Fake noInventory = device;
        reject(() -> new NotificationPrivacy(noInventory, journalUnchecked()).enforce());

        device = new Fake();
        File unwritableJournal = new File(journal(), "missing/permissions.properties");
        Fake noJournal = device;
        reject(() -> new NotificationPrivacy(noJournal, unwritableJournal).enforce());
        require(device.listeners.contains(READER) && device.assistant.equals(ASSISTANT));
        System.out.println("Notification privacy: parsing, idempotence, recovery and failure checks passed");
    }

    private static File journal() throws Exception {
        return new File(Files.createTempDirectory("bubble-notification-test-").toFile(), "state.properties");
    }

    private static File journalUnchecked() {
        try { return journal(); } catch (Exception e) { throw new AssertionError(e); }
    }

    private static final class Fake implements NotificationPrivacy.Commands {
        final Set<String> listeners = new LinkedHashSet<>(java.util.Arrays.asList(OWN, READER));
        String assistant = ASSISTANT;
        String mode = "allow";
        boolean ignoreRevoke, ignoreAppOp, failedInventory, uidOverride;

        @Override public String run(String... args) {
            String command = String.join(" ", args);
            if (command.equals("pm list users")) return "Users:\n UserInfo{0:Owner:13} running";
            if (command.equals("pm list packages -U --user 0")) return "package:android uid:1000\npackage:example.reader uid:10123";
            if (command.equals("dumpsys package")) return failedInventory ? "Permission Denial" :
                    "Packages:\n  Package [android] (a):\n  Package [example.reader] (b):\n    android.permission.ACCESS_NOTIFICATIONS: granted=true\n";
            if (command.startsWith("settings get")) {
                return args[5].equals("enabled_notification_listeners") ? String.join(":", listeners) : assistant;
            }
            if (command.equals("cmd notification get_approved_assistant 0")) return assistant;
            if (command.startsWith("cmd notification disallow_assistant")) { assistant = "null"; return ""; }
            if (command.startsWith("cmd notification allow_assistant")) { assistant = args[3]; return ""; }
            if (command.startsWith("cmd notification disallow_listener")) {
                if (!ignoreRevoke) listeners.remove(args[3]);
                return "";
            }
            if (command.startsWith("cmd notification allow_listener")) { listeners.add(args[3]); return ""; }
            if (command.startsWith("appops get")) return (uidOverride ? "Uid mode: ACCESS_NOTIFICATIONS: allow\n" : "")
                    + "ACCESS_NOTIFICATIONS: " + mode;
            if (command.startsWith("appops set")) {
                if (!ignoreAppOp) mode = args[6];
                return "";
            }
            throw new AssertionError("Unexpected command " + command);
        }
    }

    private static void require(boolean value) { if (!value) throw new AssertionError(); }
    private static void reject(Runnable action) {
        try { action.run(); } catch (IllegalStateException expected) { return; }
        throw new AssertionError("Unsafe or unreadable state accepted");
    }
}
