package org.bubble.shell;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Bubble privileged engine.
 *
 * <p>This program is meant to be launched through {@code app_process} from the device's own
 * wireless-debugging channel (see tools/bubble-bootstrap.sh). When launched that way it inherits
 * the {@code shell} UID, which on this device holds CREATE_USERS, INTERACT_ACROSS_USERS,
 * SUSPEND_APPS, CLEAR_APP_USER_DATA, MANAGE_NETWORK_POLICY and WRITE_SECURE_SETTINGS. A normal
 * installed app does not hold any of those, which is exactly why the bubble cannot be built from
 * inside an ordinary APK alone.
 *
 * <p>It does not use any hidden framework API by reflection. It orchestrates the stock system
 * CLIs ({@code pm}, {@code cmd connectivity}, {@code settings}) as child processes; those children
 * inherit this process' shell UID and therefore its granted permissions. This keeps the engine
 * readable and avoids fighting the hidden-API blocklist.
 *
 * <p>Honest scope: the bubble is a separate Android user with Google removed and its network forced
 * through a deny-by-default firewall. The host Android and its system Google services still sit
 * ABOVE the bubble; this engine does not and cannot hide the bubble from them without root. See the
 * experiment README for the reverse-engineering evidence behind each limit.
 */
public final class BubbleEngine {

    private static final String BUBBLE_USER_NAME = "Bubble";
    private static final String PRIVATE_TYPE = "android.os.usertype.profile.PRIVATE";
    /**
     * The Meta space: a work profile, apart from the bubble, for apps that crash when Play Services
     * is absent (Facebook, Instagram: their Firebase builds a PendingIntent without mutability
     * flags). Play Services is installed there but disabled: the app sees its version, takes the
     * transport that fails cleanly, and Google never runs. Settings can still enable it (not even a
     * profile owner's no_control_apps blocks "Enable"; tested on the emulator), so the guard turns
     * it off again. See docs/reverse-engineering/instagram-firebase-crash.md.
     */
    private static final String META_USER_NAME = "Meta";
    private static final String MANAGED_TYPE = "android.os.usertype.profile.MANAGED";
    private static final String PLAY_SERVICES = "com.google.android.gms";

    /** IUserManager.createProfileForUserWithThrow on Android 17 (API 37); verified on emulator and Pixel. */
    private static final String TX_CREATE_PROFILE = "5";
    private static final int VALIDATED_SDK = 37;

    /**
     * Never excluded from the bubble. Each one is a crash or a dead feature when missing (validated
     * on the Android 17 emulator and the Pixel 9a, 16/10/2026). com.android.systemui above all:
     * without it the user-0 SystemUI crash-loops and the phone boot-loops.
     */
    private static final String[] REQUIRED = {
            "android",
            "com.android.systemui",
            "com.android.providers.settings",
            "com.google.android.permissioncontroller",
            "com.android.server.telecom",
            "com.google.android.providers.media.module",
            "com.android.providers.userdictionary",
            "com.android.settings",                       // unlocking the profile needs it
    };

    /**
     * AOSP names of the same roles, WebView (Android installs it anyway) and the package installer:
     * without it F-Droid inside the bubble downloads but cannot install (verified on the emulator).
     * The installer is not a Google service, whatever its package name on Pixels. Same for the
     * file picker (DocumentsUI): without it any "choose a file or folder" crashes the app that asks
     * (Signal restoring a backup: ActivityNotFoundException OPEN_DOCUMENT_TREE, Pixel 17/10/2026).
     */
    private static final String[] ALSO_KEPT = {
            "com.android.permissioncontroller",
            "com.android.providers.media.module",
            "com.google.android.webview",
            "com.android.webview",
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.google.android.documentsui",
            "com.android.documentsui",
            "com.android.externalstorage",
    };

    /** Package installer names: Pixel/GMS builds, then AOSP. */
    private static final String[] INSTALLERS = {
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
    };

    /** File picker names: Pixel/GMS builds, then AOSP. */
    private static final String[] FILE_PICKERS = {
            "com.google.android.documentsui",
            "com.android.documentsui",
    };
    /**
     * The "Open with" / share sheet (ACTION_CHOOSER): without it an app that shares or opens a
     * link crashes (Spotify inside the bubble: ActivityNotFoundException CHOOSER, Pixel
     * 17/10/2026). An AOSP module, not a Google service.
     */
    private static final String[] CHOOSERS = {
            "com.android.intentresolver",
    };
    /**
     * The storage provider behind the file browser's "internal storage": without it "Bubble files"
     * opens DocumentsUI with nothing in it (Pixel 18/10/2026). AOSP, no network, not Google.
     */
    private static final String[] STORAGE = {
            "com.android.externalstorage",
    };

    /** Package name prefixes removed from the bubble user before it is first started. */
    private static final String[] GOOGLE_PREFIXES = {
            "com.google.",
            "com.android.vending",   // Play Store
            "com.android.gsf",       // Google Services Framework stub
    };

    /**
     * Google services and the system plumbing that only serves them (package, or prefix followed
     * by "."). Their presence makes a profile useless as a bubble.
     */
    private static final String[] GOOGLE_SERVICES = {
            "com.google.android.gms",                     // Play Services
            "com.google.android.gsf",                     // Services Framework
            "com.android.vending",                        // Play Store
            "com.google.android.syncadapters",            // account sync (contacts, calendar)
            "com.google.android.partnersetup",
            "com.google.android.setupwizard",
            "com.google.android.backuptransport",
            "com.google.android.feedback",
            "com.google.android.configupdater",
            "com.google.android.onetimeinitializer",
            "com.google.android.apps.restore",
    };

    /**
     * System events (the "events" log buffer) that name the bubble's user, its uids or its
     * packages while it is used. Google reads that buffer without consent (logd lets an
     * events-only reader through), so these are never written: logd drops an event whose tag
     * property is "S". Inventory taken on the Android 17 emulator using the bubble, 16/10/2026;
     * see docs/reverse-engineering/system-logs.md. They are diagnostics only: the same events
     * of the primary Android go silent too, and nothing on the phone depends on them.
     */
    private static final String[] QUIET_EVENTS = {
            "am_activity_launch_time", "am_compact", "am_focused_root_task",
            "am_foreground_service_start", "am_foreground_service_stop", "am_freeze", "am_kill",
            "am_low_memory", "am_mem_factor", "am_pre_boot", "am_proc_bound", "am_proc_died",
            "am_proc_start", "am_pss", "am_uid_active", "am_uid_idle", "am_uid_running",
            "am_uid_stopped", "am_unfreeze", "am_user_state_changed", "am_wtf",
            "cc_connect_state_changed", "dvm_lock_sample", "input_focus", "input_interaction",
            "jank_cuj_events_begin_request", "jank_cuj_events_end_request", "notification_alert",
            "notification_cancel_all", "notification_canceled", "notification_clicked",
            "notification_enqueue", "notification_panel_hidden", "notification_visibility",
            "service_manager_slow", "service_manager_stats", "snet_event_log",
            "ssm_user_completed_event", "sysui_multi_action", "uc_send_user_broadcast",
            "uc_start_user_internal", "um_user_visibility_changed", "wm_activity_launch_time",
            "wm_add_to_stopping", "wm_create_activity", "wm_create_task", "wm_destroy_activity",
            "wm_finish_activity", "wm_on_activity_result_called", "wm_on_create_called",
            "wm_on_destroy_called", "wm_on_paused_called", "wm_on_resume_called",
            "wm_on_start_called", "wm_on_stop_called", "wm_on_top_resumed_gained_called",
            "wm_on_top_resumed_lost_called", "wm_pause_activity", "wm_restart_activity",
            "wm_resume_activity", "wm_set_keyguard_shown", "wm_set_resumed_activity",
            "wm_task_created", "wm_task_moved", "wm_task_removed", "wm_task_to_front"
    };

    private BubbleEngine() {}

    public static void main(String[] args) {
        if (args.length == 0) {
            usage();
            System.exit(2);
        }
        try {
            switch (args[0]) {
                case "bubble-create":
                    bubbleCreate();
                    break;
                case "bubble-adopt":
                    bubbleAdopt();
                    break;
                case "bubble-recreate":
                    bubbleRecreate(requireUserId(args));
                    break;
                case "bubble-delete":
                    bubbleDelete(requireUserId(args));
                    break;
                case "undo-tweaks":
                    undoTweaks();
                    break;
                case "bubble-status":
                    bubbleStatus();
                    break;
                case "bubble-apps":
                    bubbleApps(requireUserId(args));
                    break;
                case "bubble-add":
                    bubbleAdd(requireUserId(args), packagesFrom(args, 2));
                    break;
                case "bubble-tools":
                    requireBubble(requireUserId(args));
                    ensureSystemTools(requireUserId(args));
                    break;
                case "bubble-allow-installs":
                    bubbleAllowInstalls(requireUserId(args), requireArg(args, 2, "package"));
                    break;
                case "bubble-firewall":
                    bubbleFirewall(requireUserId(args), requireArg(args, 2, "bubble-package"),
                            args.length > 3 ? Arrays.asList(args).subList(3, args.length) : new ArrayList<>());
                    break;
                case "meta-create":
                    metaCreate();
                    break;
                case "meta-status":
                    metaStatus();
                    break;
                case "meta-add":
                    metaAdd(requireUserId(args), packagesFrom(args, 2));
                    break;
                case "meta-delete":
                    metaDelete(requireUserId(args));
                    break;
                case "meta-apps":
                    requireMeta(requireUserId(args));
                    listApps(requireUserId(args), Arrays.asList(PLAY_SERVICES));
                    break;
                case "meta-remove":
                    requireMeta(requireUserId(args));
                    removeFrom(requireUserId(args), packagesFrom(args, 2));
                    break;
                case "meta-firewall":
                    requireMeta(requireUserId(args));
                    spaceFirewall(requireUserId(args), requireArg(args, 2, "bubble-package"),
                            args.length > 3 ? Arrays.asList(args).subList(3, args.length) : new ArrayList<>(),
                            META_FIREWALL_STATE, META_FIREWALL_PENDING);
                    break;
                case "meta-settings-off":
                    requireMeta(requireUserId(args));
                    metaSettingsOff(requireUserId(args));
                    break;
                case "launcher-icons":
                    launcherIcons();
                    break;
                case "bubble-smart-lock":
                    bubbleSmartLock(requireUserId(args), requireArg(args, 2, "bubble-package"));
                    break;
                case "meta-keyboard":
                    requireMeta(requireUserId(args));
                    setKeyboard(requireUserId(args), requireArg(args, 2, "ime-id"));
                    break;
                case "quiet-logs":
                    quietLogs();
                    break;
                case "notification-privacy":
                    enforceNotificationPrivacy();
                    break;
                case "bubble-keyboard":
                    bubbleKeyboard(requireUserId(args), requireArg(args, 2, "ime-id"));
                    break;
                case "bubble-remove":
                    bubbleRemove(requireUserId(args), packagesFrom(args, 2));
                    break;
                case "bubble-launch":
                    bubbleLaunch(requireUserId(args), requireArg(args, 2, "package"),
                            args.length > 3 ? args[3] : null);
                    break;
                case "firewall-on":
                    firewall(requireUserId(args), true);
                    break;
                case "firewall-off":
                    firewall(requireUserId(args), false);
                    break;
                case "persist-access":
                    persistAccess();
                    break;
                case "status":
                    status(requireUserId(args));
                    break;
                case "host-block":
                    hostBlock(Arrays.asList(args).subList(1, args.length), true);
                    break;
                case "host-unblock":
                    hostBlock(Arrays.asList(args).subList(1, args.length), false);
                    break;
                case "host-apply":
                    hostApply(readRules(requireArg(args, 1, "rules-file")), true);
                    break;
                case "watch":
                    watch(requireArg(args, 1, "rules-file"),
                            args.length > 2 ? Integer.parseInt(args[2]) : 5,
                            args.length > 3 ? Integer.parseInt(args[3]) : -1);
                    break;
                case "host-bgdata-off":
                    for (int i = 1; i < args.length; i++) hostBackgroundDataOff(args[i]);
                    if (args.length < 2) throw new EngineError("missing argument: package");
                    break;
                case "host-metered":
                    meterAllWifi(true);
                    break;
                case "host-status":
                    hostStatus(args.length > 1 ? readRules(args[1]) : new ArrayList<>());
                    break;
                default:
                    usage();
                    System.exit(2);
            }
        } catch (EngineError e) {
            System.err.println("bubble-shell: " + e.getMessage());
            System.exit(1);
        }
    }

    // ---- operations -------------------------------------------------------

    /**
     * Create the bubble as a minimal Private Space and start it. If one already exists (Android
     * allows one per parent) nothing is changed: its contents are reported with
     * {@code bubble_decision=needed}, and the user picks bubble-adopt or bubble-recreate.
     *
     * <p>{@code pm create-user} cannot exclude packages and, on Android 17, a system app can no
     * longer be uninstalled per user without root. So the profile is created through
     * IUserManager.createProfileForUserWithThrow, whose fifth argument lists the system packages
     * that are never installed in it. Everything but {@link #keepList()} is excluded, so Google is
     * absent from the first boot. Evidence: docs/reverse-engineering/private-space.md.
     *
     * <p>Safety net (lesson of the 16/10/2026 boot loop): right after creating and after starting,
     * system_server and the user-0 SystemUI are watched; if either restarts or anything lands in
     * the crash buffer, the profile is removed at once, while the system can still do it.
     */
    private static void bubbleCreate() {
        if (privateProfileId() > 0) {
            bubbleStatus();
            say("bubble_decision=needed");
            return;
        }
        startBubble(createMinimalPrivateSpace(), true);
    }

    /** Use the existing Private Space as the bubble, only if no Google service is inside it. */
    private static void bubbleAdopt() {
        int id = privateProfileId();
        if (id <= 0) throw new EngineError("there is no private space to adopt");
        List<String> google = googleServices(id, false);
        if (!google.isEmpty()) {
            throw new EngineError("refusing to adopt user " + id + ", Google inside: " + String.join(",", google));
        }
        say("bubble adopted: existing private space user " + id);
        startBubble(id, false);
    }

    /**
     * Remove the existing Private Space (its apps and data are lost) and create a minimal one. The
     * id must be the one the user saw, so a stale decision never removes a different profile.
     */
    private static void bubbleRecreate(int userId) {
        int id = privateProfileId();
        if (id != userId) throw new EngineError("user " + userId + " is not the current private space (" + id + ")");
        Result rm = shWithHeartbeat("removing", "pm", "remove-user", String.valueOf(id));
        if (rm.code != 0 || privateProfileId() > 0) throw new EngineError("could not remove user " + id + ": " + rm.err());
        say("removed private space user " + id);
        startBubble(createMinimalPrivateSpace(), true);
    }

    /**
     * The bubble locks itself, except while one of its apps plays (music with the screen off).
     * Android's own auto-lock stops the whole profile when the screen locks, with no exception per
     * app. Instead Bubble's copy inside the bubble (SmartLock, run by its always-on VPN) watches
     * that profile's media sessions (readable through an approved notification listener) and turns
     * quiet mode on itself when the screen goes off and nothing plays, or as soon as playing
     * stops; MODIFY_QUIET_MODE has the
     * development bit, so the shell grants it. Only once both are in place is Android's auto-lock
     * set to "only after restart"; otherwise it stays with the screen.
     */
    private static void bubbleSmartLock(int userId, String bubblePkg) {
        requireBubble(userId);
        // Re-establish the OS fallback before granting a music exception. An older installation
        // may have left this at 2 even if approval or the VPN no longer works.
        requireScreenLock();
        enforceNotificationPrivacy();
        String user = String.valueOf(userId);
        if (!listPackages(userId).contains(bubblePkg)) sh("pm", "install-existing", "--user", user, bubblePkg);
        Result grant = sh("pm", "grant", "--user", user, bubblePkg, "android.permission.MODIFY_QUIET_MODE");
        Result listen = sh("cmd", "notification", "allow_listener", bubblePkg + "/.BubbleMediaWatcher", user);
        if (grant.code != 0 || listen.code != 0) {
            throw new EngineError("smart lock: could not grant access; screen auto-lock restored");
        }
        if (sh("settings", "put", "--user", "0", "secure", "private_space_auto_lock", "2").code != 0
                || !"2".equals(sh("settings", "get", "--user", "0", "secure", "private_space_auto_lock").out.trim())) {
            requireScreenLock();
            throw new EngineError("smart lock: could not configure auto-lock");
        }
        say("smart lock: on");
    }

    private static void requireScreenLock() {
        if (sh("settings", "put", "--user", "0", "secure", "private_space_auto_lock", "0").code != 0
                || !"0".equals(sh("settings", "get", "--user", "0", "secure", "private_space_auto_lock").out.trim())) {
            throw new EngineError("could not restore screen auto-lock");
        }
    }

    private static NotificationPrivacy notificationPrivacy() {
        return new NotificationPrivacy(args -> {
            Result result = sh(args);
            if (result.code != 0 || !result.errText.trim().isEmpty()) {
                throw new IllegalStateException("Notification privacy command failed: " + result.err());
            }
            return result.out;
        }, new java.io.File("/data/local/tmp/bubble/notification-privacy.properties"));
    }

    private static void enforceNotificationPrivacy() {
        try (java.io.RandomAccessFile lock = new java.io.RandomAccessFile(
                "/data/local/tmp/bubble/notification-privacy.lock", "rw");
                java.nio.channels.FileLock ignored = lock.getChannel().lock()) {
            NotificationPrivacy privacy = notificationPrivacy();
            privacy.enforce();
            if (!privacy.systemQueryExemptions.isEmpty()) {
                say("notification_system_uid_exempt=" + String.join(",", privacy.systemQueryExemptions));
            }
            say("notification_privacy=restricted");
        } catch (Exception e) {
            requireScreenLock();
            throw new EngineError("notification privacy NOT VERIFIED: " + e.getMessage());
        }
    }

    /**
     * The home-screen icon size of the phone's launcher, for Bubble's widget to match it. No API
     * gives it, but Launcher3-based launchers (the Pixel's) print their DeviceProfile when dumped:
     * "iconSizePx=173" (Pixel 9a, 4x6 grid, 420 dpi). Reported as launcher_icon_px, or nothing.
     */
    private static void launcherIcons() {
        String home = sh("cmd", "package", "resolve-activity", "--brief", "-a", "android.intent.action.MAIN",
                "-c", "android.intent.category.HOME").out.trim();
        home = home.substring(home.lastIndexOf('\n') + 1).trim();
        if (!home.contains("/")) return;
        for (String line : sh("dumpsys", "activity", home).out.split("\n")) {
            line = line.trim();
            if (line.startsWith("iconSizePx=")) {
                say("launcher_icon_px=" + line.substring("iconSizePx=".length()).replace(",", "").trim());
                return;
            }
        }
    }

    /** Remove the bubble with its apps and data, without creating another one. */
    private static void bubbleDelete(int userId) {
        requireBubble(userId);
        Result rm = shWithHeartbeat("removing", "pm", "remove-user", String.valueOf(userId));
        if (rm.code != 0 || privateProfileId() > 0) throw new EngineError("could not remove user " + userId + ": " + rm.err());
        new java.io.File(FIREWALL_STATE).delete();
        new java.io.File(FIREWALL_PENDING).delete();
        say("removed bubble user " + userId);
    }

    /**
     * Leave the system as Bubble found it, for "Remove everything": the silenced events write again
     * (log.tag.* back to unset), the usagestats dump goes back to its default for the apps it was
     * denied to, the adb key expiry goes back to its old value, and Bubble's saved state is deleted. The bubble, the Meta space and the phone's
     * firewall are removed by their own commands first.
     */
    private static void undoTweaks() {
        try (java.io.RandomAccessFile lock = new java.io.RandomAccessFile(
                "/data/local/tmp/bubble/notification-privacy.lock", "rw");
                java.nio.channels.FileLock ignored = lock.getChannel().lock()) {
            notificationPrivacy().restore();
        } catch (Exception e) {
            throw new EngineError("could not restore notification access: " + e.getMessage());
        }
        int restored = 0;
        for (String tag : QUIET_EVENTS) {
            sh("setprop", "persist.log.tag." + tag, "");
            if (sh("setprop", "log.tag." + tag, "").code == 0) restored++;
        }
        say("events restored: " + restored);
        java.io.File holders = new java.io.File(USAGE_HOLDERS);
        if (holders.exists()) {
            List<String> pkgs = readRules(USAGE_HOLDERS);
            for (String pkg : pkgs) sh("appops", "set", pkg, "GET_USAGE_STATS", "default");
            say("usage dump: back to default for " + pkgs.size() + " apps");
        }
        if (new java.io.File(USAGE_DISABLED).exists()) {
            List<String> pkgs = readRules(USAGE_DISABLED);
            for (String pkg : pkgs) sh("pm", "enable", "--user", "0", pkg);
            say("usage dump: re-enabled " + pkgs.size() + " readers in user 0");
        }
        restoreAccessExpiry();
        for (String path : new String[]{USAGE_HOLDERS, USAGE_DISABLED, FIREWALL_STATE, FIREWALL_PENDING,
                META_FIREWALL_STATE, META_FIREWALL_PENDING, CONNECTION_TIME_BEFORE}) {
            new java.io.File(path).delete();
        }
    }

    /** Start the bubble if stopped, give it its system tools, watch the system, then report. */
    private static void startBubble(int id, boolean created) {
        enforceNotificationPrivacy();
        ensureSystemTools(id);
        if (!privateProfileLine(id).contains("(running)")) {
            int[] before = systemPids();
            sh("logcat", "-b", "crash", "-c");
            Result start = shWithHeartbeat("starting", "am", "start-user", "-w", String.valueOf(id));
            if (start.code != 0) throw new EngineError("could not start user " + id + ": " + start.err());
            watchStable(id, before, 20, "start", created);
        }
        bubbleStatus();
    }

    private static int createMinimalPrivateSpace() {
        return createMinimalProfile(BUBBLE_USER_NAME, PRIVATE_TYPE);
    }

    /** A profile of user 0 with only the keep list installed; stopped, watched for stability. */
    private static int createMinimalProfile(String name, String type) {
        String sdk = sh("getprop", "ro.build.version.sdk").out.trim();
        if (!sdk.equals(String.valueOf(VALIDATED_SDK))) {
            // The binder transaction number belongs to this Android version's IUserManager.
            throw new EngineError("creation validated only on API " + VALIDATED_SDK + ", this is API " + sdk);
        }
        List<String> keep = keepList();
        for (String pkg : REQUIRED) {
            if (!keep.contains(pkg)) throw new EngineError("keep list lost a required package: " + pkg);
        }
        List<String> disallowed = new ArrayList<>();
        for (String pkg : systemPackages()) if (!keep.contains(pkg)) disallowed.add(pkg);

        List<String> call = new ArrayList<>(Arrays.asList("service", "call", "user", TX_CREATE_PROFILE,
                "s16", name, "s16", type, "i32", "0", "i32", "0",
                "i32", String.valueOf(disallowed.size())));
        for (String pkg : disallowed) { call.add("s16"); call.add(pkg); }

        say("creating " + name + " profile: keep=" + keep.size() + " excluded=" + disallowed.size());
        int[] before = systemPids();
        sh("logcat", "-b", "crash", "-c");
        Result r = shWithHeartbeat("creating", call.toArray(new String[0]));
        int id = profileId(type);
        if (id <= 0) throw new EngineError("profile not created: " + (r.out + r.errText).trim());
        watchStable(id, before, 20, "create", true);
        say("created " + name + " profile user " + id + " packages=" + listPackages(id).size());
        return id;
    }

    /**
     * Bubbles created before the installer joined the keep list (or adopted ones) get it back with
     * install-existing; it is a system package, so this only flips its installed flag.
     */
    /** The system tools every bubble needs, for bubbles made before they were kept: installer, file picker, share sheet. */
    private static void ensureSystemTools(int id) {
        List<String> inside = listPackages(id);
        List<String> system = systemPackages();
        ensureOneOf(id, INSTALLERS, "installer", inside, system);
        ensureOneOf(id, FILE_PICKERS, "file picker", inside, system);
        ensureOneOf(id, CHOOSERS, "share sheet", inside, system);
        ensureOneOf(id, STORAGE, "storage provider", inside, system);
        // Made without the setup wizard, the profile stays "setup not complete" and Android leaves
        // its apps out of Recents (RecentTasks.getRecentTasksImpl skips such tasks; the value is
        // copied into each task when it is created, so tasks made before stay hidden).
        sh("settings", "put", "--user", String.valueOf(id), "secure", "user_setup_complete", "1");
    }

    private static void ensureOneOf(int id, String[] names, String role, List<String> inside, List<String> system) {
        for (String pkg : names) {
            if (inside.contains(pkg)) return;
            if (!system.contains(pkg)) continue;
            Result r = sh("pm", "install-existing", "--user", String.valueOf(id), pkg);
            say((r.code == 0 ? role + " added: " : "COULD NOT ADD " + role + " ") + pkg);
            return;
        }
    }

    /**
     * Packages the bubble keeps: the required core (each one a crash or a dead feature when
     * missing, see the guide) plus the enabled resource overlays that target them.
     */
    private static List<String> keepList() {
        List<String> keep = new ArrayList<>(Arrays.asList(REQUIRED));
        keep.addAll(Arrays.asList(ALSO_KEPT));
        String target = null;
        for (String line : sh("cmd", "overlay", "list", "--user", "0").out.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) {
                target = null;
            } else if (!line.startsWith("[") && !line.startsWith("---")) {
                target = line;
            } else if (line.startsWith("[x] ") && target != null && keep.contains(target)) {
                String overlay = line.substring(4).trim();
                if (!keep.contains(overlay)) keep.add(overlay);
            }
        }
        return keep;
    }

    /** Every system package the device knows, installed for user 0 or not. */
    private static List<String> systemPackages() {
        Result r = sh("pm", "list", "packages", "-s", "-u", "--user", "0");
        if (r.code != 0) throw new EngineError("could not list system packages: " + r.err());
        List<String> out = new ArrayList<>();
        for (String line : r.out.split("\n")) {
            line = line.trim();
            if (line.startsWith("package:")) out.add(line.substring("package:".length()));
        }
        if (out.size() < REQUIRED.length) throw new EngineError("system package list looks wrong: " + out.size());
        return out;
    }

    /**
     * Fail if system_server or the user-0 SystemUI restarts, or anything crashes, within
     * {@code seconds}; a profile created by this run is removed then (an adopted one, with the
     * user's data, never is). Prints a line every few seconds to keep the adb stream alive.
     */
    private static void watchStable(int userId, int[] before, int seconds, String phase, boolean removeOnFail) {
        for (int waited = 0; waited < seconds; waited += 5) {
            pause(5000);
            int[] now = systemPids();
            String crash = sh("logcat", "-b", "crash", "-d").out;
            boolean crashed = crash.contains("Process: ");
            if (before[0] <= 0 || before[1] <= 0 || now[0] != before[0] || now[1] != before[1] || crashed) {
                String what = "system unstable after " + phase + " (system_server " + before[0] + "->"
                        + now[0] + ", systemui " + before[1] + "->" + now[1] + (crashed ? ", crash logged" : "") + ")";
                if (!removeOnFail) throw new EngineError(what + "; profile " + userId + " kept");
                Result rm = sh("pm", "remove-user", String.valueOf(userId));
                throw new EngineError(what + "; profile " + userId
                        + (rm.code == 0 ? " removed" : " NOT removed: " + rm.err()));
            }
            say("stable " + (waited + 5) + "s after " + phase);
        }
    }

    /** {system_server pid, user-0 SystemUI pid}; -1 when not found. */
    private static int[] systemPids() {
        int server = parseTrailingInt(sh("pidof", "system_server").out.trim());
        int ui = -1;
        for (String line : sh("ps", "-A", "-o", "PID,USER,NAME").out.split("\n")) {
            String[] f = line.trim().split("\\s+");
            if (f.length == 3 && f[2].equals("com.android.systemui") && f[1].startsWith("u0_")) {
                ui = Integer.parseInt(f[0]);
            }
        }
        return new int[] {server, ui};
    }

    /**
     * A Google service can be put back into the bubble by anyone holding the shell identity
     * (pm install-existing), and then only root can uninstall it. Disabled, it can never run; the
     * user is told through bubble-status (bubble_google_services > 0, bubble_google_active = 0).
     */
    private static void bubbleGuard(int id) {
        if (privateProfileId() != id) return; // the bubble is gone; a new one gets a new guard
        // A firewall list sent while the bubble was locked was dropped by Android (the VPN then
        // starts with the old one), so it was marked pending: send it again once it is unlocked.
        if (new java.io.File(FIREWALL_PENDING).exists() && privateProfileLine(id).contains("(running)")) {
            String[] saved = readFirewallState(FIREWALL_STATE);
            if (saved != null) {
                List<String> allowed = new ArrayList<>(Arrays.asList(saved).subList(1, saved.length));
                bubbleFirewall(id, saved[0], allowed);
            }
        }
        for (String pkg : googleServices(id, true)) {
            sh("am", "force-stop", "--user", String.valueOf(id), pkg);
            Result r = sh("pm", "disable-user", "--user", String.valueOf(id), pkg);
            System.out.println("bubble-guard: " + (r.code == 0 ? "disabled " : "COULD NOT DISABLE ") + pkg
                    + " in user " + id);
        }
        enforceNotificationPrivacy();
    }

    /** Report the bubble: its user id (or none), whether it runs, packages and Google services. */
    private static void bubbleStatus() {
        int id = privateProfileId();
        if (id <= 0) {
            say("bubble_user_id=none");
            return;
        }
        String line = privateProfileLine(id);
        List<String> google = googleServices(id, false);
        say("bubble_user_id=" + id);
        say("bubble_running=" + line.contains("(running)"));
        say("bubble_locked=" + line.contains("QUIET_MODE"));
        say("bubble_packages=" + listPackages(id).size());
        say("bubble_google_services=" + google.size());
        say("bubble_google_active=" + googleServices(id, true).size());
        if (!google.isEmpty()) say("bubble_google=" + String.join(",", google));
        say("bubble_keyboard=" + bubbleKeyboard(id));
    }

    // ---- the Meta space ------------------------------------------------------

    /**
     * Create the Meta space: a minimal work profile with Play Services installed and disabled while
     * the profile is still stopped, so it never starts. Any failure removes the profile this run
     * created.
     */
    private static void metaCreate() {
        if (metaProfileId() > 0) {
            metaStatus();
            return;
        }
        if (profileId(MANAGED_TYPE) > 0) throw new EngineError("there is already another work profile on this phone");
        int id = createMinimalProfile(META_USER_NAME, MANAGED_TYPE);
        String user = String.valueOf(id);
        try {
            Result add = sh("pm", "install-existing", "--user", user, PLAY_SERVICES);
            Result off = sh("pm", "disable-user", "--user", user, PLAY_SERVICES);
            if (add.code != 0 || off.code != 0 || !googleServices(id, true).isEmpty()) {
                throw new EngineError("Play Services could not be left disabled: " + (add.err() + " " + off.err()).trim());
            }
            ensureSystemTools(id);
            metaSettingsOff(id);
            int[] before = systemPids();
            sh("logcat", "-b", "crash", "-c");
            Result start = shWithHeartbeat("starting", "am", "start-user", "-w", user);
            if (start.code != 0) throw new EngineError("could not start user " + id + ": " + start.err());
            watchStable(id, before, 20, "start", false);
        } catch (EngineError e) {
            Result rm = sh("pm", "remove-user", user);
            throw new EngineError(e.getMessage() + "; Meta space " + (rm.code == 0 ? "removed" : "NOT removed: " + rm.err()));
        }
        metaStatus();
    }

    private static void metaStatus() {
        int id = metaProfileId();
        if (id <= 0) {
            say("meta_user_id=none");
            return;
        }
        List<String> packages = listPackages(id);
        say("meta_user_id=" + id);
        say("meta_running=" + privateProfileLine(id).contains("(running)"));
        say("meta_play_services=" + (!packages.contains(PLAY_SERVICES) ? "absent"
                : listPackages(id, "-e").contains(PLAY_SERVICES) ? "enabled" : "disabled"));
        say("meta_google_active=" + googleServices(id, true).size());
        say("meta_packages=" + packages.size());
        say("meta_keyboard=" + bubbleKeyboard(id));
    }

    private static void metaAdd(int userId, List<String> pkgs) {
        requireMeta(userId);
        addExisting(userId, pkgs);
    }

    /** Remove the Meta space with its apps and data; the bubble and user 0 are not touched. */
    private static void metaDelete(int userId) {
        requireMeta(userId);
        Result rm = shWithHeartbeat("removing", "pm", "remove-user", String.valueOf(userId));
        if (rm.code != 0 || metaProfileId() > 0) throw new EngineError("could not remove user " + userId + ": " + rm.err());
        say("removed Meta space user " + userId);
    }

    /**
     * Facebook and Instagram see Play Services installed but disabled and open its "App info" in
     * Settings to have it enabled, on every start (seen on the Pixel and the emulator). With the
     * profile's own Settings disabled that request finds nothing to open; the apps keep working
     * (emulator: no crash, Instagram stays in front). The phone's Settings, and its Work tab, are
     * not touched.
     */
    private static void metaSettingsOff(int id) {
        Result r = sh("pm", "disable-user", "--user", String.valueOf(id), "com.android.settings");
        say((r.code == 0 ? "Meta space Settings off" : "COULD NOT TURN OFF Meta space Settings: " + r.err()));
    }

    /**
     * Apps of the Meta space find Play Services disabled and post Google's "Enable Google Play
     * services" notice. Bubble's copy there removes it (GmsNoticeListener); the approval covers only
     * that profile's notifications.
     */
    private static boolean metaNoticeListener(int id, String bubblePkg) {
        Result r = sh("cmd", "notification", "allow_listener", bubblePkg + "/.GmsNoticeListener", String.valueOf(id));
        say(r.code == 0 ? "Meta space: Google notices hidden" : "COULD NOT hide Google notices in the Meta space: " + r.err());
        return r.code == 0;
    }

    /** Once per guard process: Meta spaces set up before GmsNoticeListener existed get it too. */
    private static boolean metaNoticesChecked;

    /** Keep every Google service of the Meta space disabled, as the guard does for the bubble. */
    private static void metaGuard() {
        int id = metaProfileId();
        if (id <= 0) return;
        if (!metaNoticesChecked) {
            String[] saved = readFirewallState(META_FIREWALL_STATE);
            if (saved != null && listPackages(id).contains(saved[0])) metaNoticesChecked = metaNoticeListener(id, saved[0]);
        }
        // As for the bubble: a list sent while the Meta space was paused is sent again once it runs.
        if (new java.io.File(META_FIREWALL_PENDING).exists() && privateProfileLine(id).contains("(running)")) {
            String[] saved = readFirewallState(META_FIREWALL_STATE);
            if (saved != null) {
                List<String> allowed = new ArrayList<>(Arrays.asList(saved).subList(1, saved.length));
                spaceFirewall(id, saved[0], allowed, META_FIREWALL_STATE, META_FIREWALL_PENDING);
            }
        }
        for (String pkg : googleServices(id, true)) {
            sh("am", "force-stop", "--user", String.valueOf(id), pkg);
            Result r = sh("pm", "disable-user", "--user", String.valueOf(id), pkg);
            System.out.println("meta-guard: " + (r.code == 0 ? "disabled " : "COULD NOT DISABLE ") + pkg
                    + " in user " + id);
        }
    }

    /** Id of the work profile Bubble made (named Meta), or -1; an employer's work profile is never it. */
    private static int metaProfileId() {
        int id = profileId(MANAGED_TYPE);
        return id > 0 && privateProfileLine(id).contains(" name=" + META_USER_NAME + ",") ? id : -1;
    }

    private static void requireMeta(int userId) {
        guardNotSystemUser(userId);
        if (metaProfileId() != userId) throw new EngineError("user " + userId + " is not the Meta space");
    }

    /** Id of the Private Space of user 0, or -1. From "cmd user list -v". */
    private static int privateProfileId() {
        return profileId(PRIVATE_TYPE);
    }

    /** Id of the first profile of that type, or -1. Android allows one of each per parent. */
    private static int profileId(String type) {
        for (String line : sh("cmd", "user", "list", "-v").out.split("\n")) {
            if (!line.contains("type=" + type.substring("android.os.usertype.".length()) + ",")) continue;
            int at = line.indexOf("id=");
            if (at < 0) continue;
            int end = line.indexOf(',', at);
            return Integer.parseInt(line.substring(at + 3, end < 0 ? line.length() : end).trim());
        }
        return -1;
    }

    private static String privateProfileLine(int id) {
        for (String line : sh("cmd", "user", "list", "-v").out.split("\n")) {
            if (line.contains(" id=" + id + ",")) return line;
        }
        return "";
    }

    /**
     * Google services installed in the profile, disabled ones included unless {@code enabledOnly}
     * (pm lists both by default). Only the
     * services count: they are what follows the user across apps. Google's ordinary apps (YouTube,
     * Maps) installed through F-Droid's Aurora Store are allowed, like any other app.
     */
    private static List<String> googleServices(int userId, boolean enabledOnly) {
        List<String> found = new ArrayList<>();
        for (String pkg : enabledOnly ? listPackages(userId, "-e") : listPackages(userId)) {
            if (isGoogleService(pkg)) found.add(pkg);
        }
        return found;
    }

    // ---- apps inside the bubble ---------------------------------------------
    //
    // An app the user already has outside is enabled in the bubble with install-existing: the
    // same installed code, fresh data, and no Google services inside (none exist there). Play keeps
    // updating it while it stays installed outside. APK files go through "cmd package install
    // --user N" from the app's channel; the guard catches a Google service arriving that way.

    /**
     * The apps in the bubble that are not part of its minimal base: the user's own, and system apps
     * added later (such as Gboard). One "app=<package> path=<apk>" line each; the path lets the app
     * outside read the label.
     */
    private static void bubbleApps(int userId) {
        requireBubble(userId);
        listApps(userId, new ArrayList<>());
    }

    /** One "app=" line per app of that profile beyond the keep list and {@code alsoBase}. */
    private static void listApps(int userId, List<String> alsoBase) {
        List<String> base = keepList();
        base.addAll(alsoBase);
        for (String line : sh("pm", "list", "packages", "-f", "--user", String.valueOf(userId)).out.split("\n")) {
            line = line.trim();
            int eq = line.lastIndexOf('=');
            if (!line.startsWith("package:") || eq < 0) continue;
            String pkg = line.substring(eq + 1);
            if (!base.contains(pkg)) say("app=" + pkg + " path=" + line.substring("package:".length(), eq));
        }
    }

    /**
     * The bubble's own firewall, deny by default: a VPN run by Bubble's copy inside the bubble
     * routes every app of the bubble into a dead tunnel except the allowed ones (VPNs are per
     * profile, so the same apps outside keep their network). Ensures that copy is there with the
     * VPN consent (appops, no dialog) as the profile's always-on VPN, then hands it the list; the
     * copy hides its own launcher icon (the shell cannot, on Android 17).
     */
    /** The last firewall sent: package of Bubble, then the allowed packages, one per line. */
    private static final String FIREWALL_STATE = "/data/local/tmp/bubble/bubble-firewall.txt";
    /** The apps denied the usagestats dump, found by denyUsageDump. */
    private static final String USAGE_HOLDERS = "/data/local/tmp/bubble/usage-holders.txt";
    /** adb_allowed_connection_time before Bubble set it to 0, for undo-tweaks. */
    private static final String CONNECTION_TIME_BEFORE = "/data/local/tmp/bubble/adb-connection-time.txt";
    /** Usage readers disabled in user 0 because the app-op would not take; re-enabled by undo. */
    private static final String USAGE_DISABLED = "/data/local/tmp/bubble/usage-disabled.txt";
    /**
     * Usage-stats readers that accept the GET_USAGE_STATS app-op (exit 0) but stay at "default",
     * so the op never denies the dump. The only no-root way left to stop them reading it is to
     * disable them in user 0. Kept to packages that are safe to disable there and that undo-tweaks
     * re-enables: com.google.android.apps.restore is the device-transfer app, not needed for
     * normal use. Anything else that resists the op stays a reported failure, never disabled blind.
     */
    private static final java.util.Set<String> UNRESTRICTABLE_USAGE_READERS =
            new java.util.HashSet<>(Arrays.asList("com.google.android.apps.restore"));
    /** Present while the saved list could not reach the bubble (it was locked). */
    private static final String FIREWALL_PENDING = "/data/local/tmp/bubble/bubble-firewall.pending";

    /** The same two files for the Meta space's firewall. */
    private static final String META_FIREWALL_STATE = "/data/local/tmp/bubble/meta-firewall.txt";
    private static final String META_FIREWALL_PENDING = "/data/local/tmp/bubble/meta-firewall.pending";

    private static String[] readFirewallState(String stateFile) {
        List<String> lines = new java.io.File(stateFile).exists() ? readRules(stateFile) : new ArrayList<>();
        return lines.isEmpty() ? null : lines.toArray(new String[0]);
    }

    private static void bubbleFirewall(int userId, String bubblePkg, List<String> allowed) {
        requireBubble(userId);
        spaceFirewall(userId, bubblePkg, allowed, FIREWALL_STATE, FIREWALL_PENDING);
    }

    /** The firewall of the bubble or of the Meta space: same VPN, its own saved list. */
    private static void spaceFirewall(int userId, String bubblePkg, List<String> allowed, String stateFile, String pendingFile) {
        String user = String.valueOf(userId);
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(stateFile)) {
            StringBuilder sb = new StringBuilder(bubblePkg).append('\n');
            for (String pkg : allowed) sb.append(pkg).append('\n');
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            say("could not save firewall state: " + e.getMessage());
        }
        java.io.File pending = new java.io.File(pendingFile);
        if (!privateProfileLine(userId).contains("(running)")) {
            // Locked: the broadcast below is dropped. The VPN keeps the old list until the bubble is
            // unlocked and the guard (or the next Bubble session) sends this one again.
            try {
                pending.createNewFile();
            } catch (java.io.IOException ignored) {
                // The next Bubble session sends the list anyway.
            }
            say("bubble locked: firewall list saved, applied when the profile is unlocked");
        } else {
            pending.delete();
        }
        if (!listPackages(userId).contains(bubblePkg)) {
            Result r = sh("pm", "install-existing", "--user", user, bubblePkg);
            if (r.code != 0) throw new EngineError("could not put " + bubblePkg + " in the bubble: " + r.err());
        }
        sh("appops", "set", "--user", user, bubblePkg, "ACTIVATE_VPN", "allow");
        if (stateFile.equals(META_FIREWALL_STATE)) metaNoticeListener(userId, bubblePkg);
        // Provision the local profile bridge once. Afterward, app opening and network controls
        // use Android Binder directly and work without Wi-Fi, ADB, or an internet connection.
        for (String target : new String[]{"0", user}) {
            sh("pm", "grant", "--user", target, bubblePkg, "android.permission.INTERACT_ACROSS_USERS");
            sh("pm", "grant", "--user", target, bubblePkg, "android.permission.MODIFY_QUIET_MODE");
        }
        // Only inside: the copy there hands one photo at a time to "Bubble photos" on the phone.
        sh("pm", "grant", "--user", user, bubblePkg, "android.permission.READ_MEDIA_IMAGES");
        // Always-on (without lockdown): Android itself starts the VPN every time the bubble is
        // unlocked. Needed because on Android 17 the profile's BOOT_COMPLETED does not reach this
        // copy, and broadcasts sent while the bubble is locked are dropped (both seen on the Pixel).
        sh("settings", "put", "--user", user, "secure", "always_on_vpn_app", bubblePkg);
        sh("settings", "put", "--user", user, "secure", "always_on_vpn_lockdown", "0");
        // The shell cannot start the app's private service; its receiver (guarded by a permission
        // only the shell holds) does, and the temp allowlist lets it start one from background.
        sh("cmd", "deviceidle", "tempwhitelist", "-u", user, "-d", "30000", bubblePkg);
        Result r = sh("am", "broadcast", "--user", user, "-f", "0x20", "-n", bubblePkg + "/.FirewallReceiver",
                "--es", "allowed", String.join(",", allowed));
        say((r.code == 0 ? "bubble firewall (deny by default), allowed: " : "COULD NOT SET BUBBLE FIREWALL: ")
                + (allowed.isEmpty() ? "none" : String.join(",", allowed)));
    }

    private static void bubbleAdd(int userId, List<String> pkgs) {
        requireBubble(userId);
        addExisting(userId, pkgs);
    }

    /** Enable in that profile apps installed outside; Google services are always refused. */
    private static void addExisting(int userId, List<String> pkgs) {
        List<String> outside = listPackages(0);
        for (String pkg : pkgs) {
            if (isGoogleService(pkg)) {
                say("refused " + pkg + ": Google services are never added");
            } else if (!outside.contains(pkg)) {
                say("refused " + pkg + ": not installed outside the bubble");
            } else {
                Result r = sh("pm", "install-existing", "--user", String.valueOf(userId), pkg);
                say((r.code == 0 ? "added " : "COULD NOT ADD ") + pkg + (r.code == 0 ? "" : ": " + r.err()));
            }
        }
    }

    /** Uninstall for the bubble only; the copy outside, if any, stays. */
    private static void bubbleRemove(int userId, List<String> pkgs) {
        requireBubble(userId);
        removeFrom(userId, pkgs);
    }

    /**
     * Open an app of the bubble from Bubble Apps outside: Android hides the Private Space from
     * every app but the launcher, so only the shell can start its activities. Locked, it says so.
     */
    private static void bubbleLaunch(int userId, String pkg, String bubblePkg) {
        requireBubble(userId);
        if (privateProfileLine(userId).contains("QUIET_MODE")) {
            say("bubble locked: unlock it first");
            return;
        }
        String user = String.valueOf(userId);
        String found = sh("cmd", "package", "resolve-activity", "--user", user, "--brief",
                "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", pkg).out.trim();
        String component = found.substring(found.lastIndexOf('\n') + 1).trim();
        if (!component.contains("/")) throw new EngineError("no launcher activity for " + pkg + " in user " + userId);
        Result r = sh("am", "start", "--user", user, "-n", component);
        if ((r.code != 0 || r.out.contains("Error")) && bubblePkg != null && listPackages(userId).contains(bubblePkg)) {
            // Refused for the shell (Bitwarden, targetSdk 37, on the Pixel): Bubble's copy in the
            // same profile opens it as any app there would.
            r = sh("am", "start", "--user", user, "-n", bubblePkg + "/.ProfileOpenActivity",
                    "--es", "package", pkg);
        }
        boolean ok = r.code == 0 && !r.out.contains("Error");
        say((ok ? "launched " : "COULD NOT LAUNCH ") + component + (ok ? "" : ": " + (r.out + r.err()).trim()));
    }

    private static void removeFrom(int userId, List<String> pkgs) {
        for (String pkg : pkgs) {
            Result r = sh("pm", "uninstall", "--user", String.valueOf(userId), pkg);
            say((r.code == 0 ? "removed " : "COULD NOT REMOVE ") + pkg + (r.code == 0 ? "" : ": " + r.err()));
        }
    }

    /** Let an app store inside the bubble (F-Droid) install apps, as "Allow from this source" does. */
    private static void bubbleAllowInstalls(int userId, String pkg) {
        requireBubble(userId);
        Result r = sh("appops", "set", "--user", String.valueOf(userId), pkg, "REQUEST_INSTALL_PACKAGES", "allow");
        say((r.code == 0 ? "installs allowed for " : "COULD NOT ALLOW INSTALLS for ") + pkg);
    }

    /**
     * The keyboards enabled in the bubble (comma-separated), or "none". Any keyboard the user enabled
     * counts; Bubble only steps in when there is none. Not default_input_method: Android empties it
     * while the profile is locked (seen on the emulator), so it would always read as none.
     */
    private static String bubbleKeyboard(int id) {
        List<String> enabled = new ArrayList<>();
        for (String line : sh("ime", "list", "-s", "--user", String.valueOf(id)).out.split("\n")) {
            if (line.trim().contains("/")) enabled.add(line.trim());
        }
        return enabled.isEmpty() ? "none" : String.join(",", enabled);
    }

    /** Keyboards are per profile: enable the bubble's own and make it the default there. */
    private static void bubbleKeyboard(int userId, String imeId) {
        requireBubble(userId);
        setKeyboard(userId, imeId);
    }

    private static void setKeyboard(int userId, String imeId) {
        Result enable = sh("ime", "enable", "--user", String.valueOf(userId), imeId);
        Result set = sh("ime", "set", "--user", String.valueOf(userId), imeId);
        say((enable.code == 0 && set.code == 0 ? "keyboard set: " : "COULD NOT SET KEYBOARD ") + imeId
                + (set.code == 0 ? "" : ": " + set.err()));
    }

    /** Silence the bubble-revealing events, persistently (persist.log.tag.*) and at once (log.tag.*). */
    private static void quietLogs() {
        int set = 0;
        String props = sh("getprop").out; // one read of all properties: a getprop each took seconds
        for (String tag : QUIET_EVENTS) {
            // persist.log.tag.* survives reboots (logd honours it too: verified on the Pixel, an
            // am_proc_start with only the persist property set was not written), so the events
            // stay silent from boot without Wi-Fi or a Bubble session; log.tag.* acts at once.
            for (String prop : new String[]{"persist.log.tag." + tag, "log.tag." + tag}) {
                if (props.contains("[" + prop + "]: [S]")) continue;
                if (sh("setprop", prop, "S").code == 0) set++;
            }
        }
        say("quiet events: " + QUIET_EVENTS.length + " (newly set " + set + ")");
        try {
            denyUsageDump();
        } catch (EngineError e) {
            requireScreenLock();
            throw e;
        }
        lockWithScreen();
    }

    /**
     * Unset, private_space_auto_lock means 2, "only after restart" (UserManagerService reads it
     * with that default), so an unlocked bubble stays open behind the lock screen. 0 locks it as
     * soon as the device locks (verified on the emulator: ~3 s, needs a screen lock). A value the
     * user picked in Settings is kept.
     */
    private static void lockWithScreen() {
        String now = sh("settings", "get", "--user", "0", "secure", "private_space_auto_lock").out.trim();
        if (!"null".equals(now)) {
            say("auto lock: kept " + now);
        } else if (sh("settings", "put", "--user", "0", "secure", "private_space_auto_lock", "0").code == 0) {
            say("auto lock: with the screen");
        } else {
            say("auto lock: COULD NOT SET");
        }
    }

    /**
     * The usagestats dump lists every app start and stop in the bubble, with its time, and any
     * holder of DUMP + PACKAGE_USAGE_STATS may read it (on the Pixel: gms, Play Store and five
     * more Google apps). It also requires the GET_USAGE_STATS app op
     * (DumpUtils.checkDumpAndUsageStatsPermission), so the op set to "ignore" denies it; the op
     * survives reboots and app updates. Those apps lose their own usage stats too. Holders are
     * found on the phone, never listed by hand. See docs/reverse-engineering/google-visibility.md.
     */
    private static void denyUsageDump() {
        // Re-discover readers at every maintenance pass. Older cached files omitted failed
        // targets, and newly granted permissions must not wait a day to be considered.
        Result inventory = sh("dumpsys", "package");
        if (inventory.code != 0 || !inventory.out.contains("  Package [android]")) {
            throw new EngineError("cannot verify usage readers: package inventory unavailable");
        }
        Map<String, java.util.Set<String>> granted;
        try {
            granted = PackagePermissions.parse(inventory.out);
        } catch (IllegalStateException e) {
            throw new EngineError(e.getMessage());
        }
        List<String> installed = listPackages(0);
        List<String> tried = new ArrayList<>();
        for (Map.Entry<String, java.util.Set<String>> e : granted.entrySet()) {
            String p = e.getKey();
            if (!e.getValue().contains("android.permission.DUMP")
                    || !e.getValue().contains("android.permission.PACKAGE_USAGE_STATS")
                    || !installed.contains(p) || p.equals("android") || p.equals("com.android.shell")
                    || p.equals("org.bubble.app")) continue;
            sh("appops", "set", p, "GET_USAGE_STATS", "ignore");
            tried.add(p);
        }
        // Preserve failed targets too. Dropping them made the next cached check report success
        // while a known reader remained authorized.
        List<String> denied = Arrays.asList(sh("appops", "query-op", "GET_USAGE_STATS", "ignore").out.split("\n"));
        List<String> disabledInUser0 = listPackages(0, "-d");
        StringBuilder list = new StringBuilder();
        StringBuilder disabled = new StringBuilder();
        int count = 0;
        for (String p : tried) {
            list.append(p).append('\n');
            if (denied.contains(p)) {
                count++;
                continue;
            }
            // The op was accepted but did not take. For a reader known safe to disable in user 0
            // (com.google.android.apps.restore), close the dump by disabling it; undo re-enables it.
            if (UNRESTRICTABLE_USAGE_READERS.contains(p)) {
                if (!disabledInUser0.contains(p)) {
                    sh("pm", "disable-user", "--user", "0", p);
                    disabledInUser0 = listPackages(0, "-d");
                }
                if (disabledInUser0.contains(p)) {
                    disabled.append(p).append('\n');
                    count++;
                    say("usage dump: disabled reader " + p + " in user 0 (op would not take)");
                    continue;
                }
            }
            say("usage dump: COULD NOT DENY " + p);
        }
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(USAGE_HOLDERS)) {
            out.write(list.toString().getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            say("could not save usage holders: " + e.getMessage());
        }
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(USAGE_DISABLED)) {
            out.write(disabled.toString().getBytes(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            say("could not save disabled usage readers: " + e.getMessage());
        }
        say("usage dump: denied to " + count + " apps");
        if (count != tried.size()) throw new EngineError("usage dump restriction incomplete; see failed packages above");
    }

    /** Operate only on the current Private Space, never on user 0 or another profile. */
    private static void requireBubble(int userId) {
        guardNotSystemUser(userId);
        if (privateProfileId() != userId) throw new EngineError("user " + userId + " is not the bubble");
    }

    private static List<String> packagesFrom(String[] args, int from) {
        if (args.length <= from) throw new EngineError("missing argument: package");
        return Arrays.asList(args).subList(from, args.length);
    }

    private static boolean isGoogleService(String pkg) {
        for (String prefix : GOOGLE_SERVICES) {
            if (pkg.equals(prefix) || pkg.startsWith(prefix + ".")) return true;
        }
        return false;
    }

    /**
     * Deny-by-default network for the bubble. The OEM_DENY_3 firewall chain is enabled, and every
     * current package in the bubble user gets its deny bit set. Bubble's own local VPN (running
     * inside the bubble user) is the only sanctioned way out; with the VPN down, the bubble has no
     * network. firewall-off lifts the chain again.
     */
    private static void firewall(int userId, boolean on) {
        guardNotSystemUser(userId);
        Result chain = sh("cmd", "connectivity", "set-chain3-enabled", on ? "true" : "false");
        if (chain.code != 0) throw new EngineError("could not toggle firewall chain: " + chain.err());
        if (on) {
            for (String pkg : listPackages(userId)) {
                sh("cmd", "connectivity", "set-package-networking-enabled", "false", pkg);
            }
        }
        System.out.println("firewall=" + (on ? "on" : "off") + " user=" + userId);
    }

    /**
     * Make the wireless-debugging access survive reboots without re-pairing.
     *
     * <p>Reverse engineered from services.jar: the paired key lives in
     * /data/misc/adb/adb_temp_keys.xml and expires after adb_allowed_connection_time ms (default
     * 604800000 = 7 days). Setting it to 0 disables expiry. Wireless debugging itself survives
     * reboots on Android 17 (persist.adb.tls_server.enable) on networks marked "Always allow".
     *
     * <p>The setting is phone-wide: it also keeps computers authorized earlier from expiring. The
     * value found first is saved, and undo-tweaks puts it back.
     */
    private static void persistAccess() {
        java.io.File saved = new java.io.File(CONNECTION_TIME_BEFORE);
        if (!saved.exists()) {
            String before = sh("settings", "get", "global", "adb_allowed_connection_time").out.trim();
            // Versions before this one set 0 without saving the old value: a 0 found with nothing
            // saved is taken as Bubble's own, so removal returns to Android's default.
            if (before.equals("0")) before = "null";
            try (java.io.FileOutputStream out = new java.io.FileOutputStream(saved)) {
                out.write(before.getBytes(StandardCharsets.UTF_8));
            } catch (java.io.IOException e) {
                throw new EngineError("could not save adb key expiry: " + e.getMessage());
            }
        }
        Result r = sh("settings", "put", "global", "adb_allowed_connection_time", "0");
        if (r.code != 0) throw new EngineError("could not persist adb access: " + r.err());
        System.out.println("adb_allowed_connection_time=0 (paired key will not expire)");
    }

    /** Put back the key expiry found before Bubble ("null": Android's default, 7 days). */
    private static void restoreAccessExpiry() {
        String before;
        if (new java.io.File(CONNECTION_TIME_BEFORE).exists()) {
            List<String> saved = readRules(CONNECTION_TIME_BEFORE);
            before = saved.isEmpty() ? "null" : saved.get(0);
        } else {
            // Set up by a version that saved nothing: a 0 is Bubble's own, as in persistAccess.
            String now = sh("settings", "get", "global", "adb_allowed_connection_time").out.trim();
            if (!now.equals("0")) return;
            before = "null";
        }
        Result r = before.equals("null")
                ? sh("settings", "delete", "global", "adb_allowed_connection_time")
                : sh("settings", "put", "global", "adb_allowed_connection_time", before);
        if (r.code != 0) throw new EngineError("could not restore adb key expiry: " + r.err());
        say("adb key expiry restored: " + (before.equals("null") ? "default" : before));
    }

    // ---- host firewall (user 0, network rules only) ------------------------
    //
    // The only operations the engine performs on the primary Android. They never install, remove,
    // disable or suspend anything: they only deny network to chosen packages and mark Wi-Fi
    // networks metered. Evidence: docs/reverse-engineering/firewall-layers.md.
    //
    // Two layers, because neither survives everything on its own:
    //  1. OEM_DENY_3 firewall chain (cmd connectivity): kernel-enforced, Wi-Fi + mobile, foreground
    //     + background, no VPN. Lost on every reboot, so it is re-applied by host-apply/watch.
    //  2. REJECT_METERED_BACKGROUND uid policy (cmd netpolicy), effective because every network is
    //     metered. Loaded by system_server at boot, before any app runs. Settings wipes the ones it
    //     did not create itself ~10 s after boot (DynamicDenylistManager.resetDenylistIfNeeded);
    //     only the user's "Background data" toggle in Settings makes it permanent.

    private static void hostBlock(List<String> pkgs, boolean block) {
        if (pkgs.isEmpty()) throw new EngineError("missing argument: package");
        if (block) enableChain();
        for (String pkg : pkgs) {
            int uid = hostUid(pkg);
            sh("cmd", "connectivity", "set-package-networking-enabled", block ? "false" : "true", pkg);
            sh("cmd", "netpolicy", block ? "add" : "remove", "restrict-background-blacklist",
                    String.valueOf(uid));
            System.out.println((block ? "blocked " : "unblocked ") + pkg + " uid=" + uid);
        }
    }

    /** Re-apply every rule that is missing. Returns true if anything had to be fixed. */
    private static boolean hostApply(List<String> pkgs, boolean verbose) {
        boolean fixed = false;
        if (!pkgs.isEmpty() && !chainEnabled()) {
            enableChain();
            fixed = true;
            if (verbose) System.out.println("chain3 re-enabled");
        }
        for (String pkg : pkgs) {
            int uid;
            try {
                uid = hostUid(pkg);
            } catch (EngineError e) {
                continue; // not installed (yet); a later pass picks it up
            }
            if (!sh("cmd", "connectivity", "get-package-networking-enabled", pkg).out.contains(":deny")) {
                sh("cmd", "connectivity", "set-package-networking-enabled", "false", pkg);
                fixed = true;
                if (verbose) System.out.println("re-denied " + pkg);
            }
            if (!hasBackgroundDeny(uid)) {
                sh("cmd", "netpolicy", "add", "restrict-background-blacklist", String.valueOf(uid));
                fixed = true;
                if (verbose) System.out.println("re-added background deny uid=" + uid + " (" + pkg + ")");
            }
        }
        if (meterAllWifi(verbose)) fixed = true;
        return fixed;
    }

    /**
     * Long-lived guard, every few seconds: re-applies the host rules file (when it lists anything)
     * and disables any Google service that shows up inside the bubble ({@code bubbleUser}) or turns on
     * in the Meta space; any other profile, such as a Private Space the user keeps with Google, or an
     * employer's work profile, is never touched. It runs as the shell UID, so
     * app standby, Doze and battery restrictions do not apply to it, and no other app can stop it
     * (unlike a VpnService, which Play Store and others can tear down).
     */
    private static void watch(String rulesFile, int intervalSec, int bubbleUser) {
        if (intervalSec < 1) throw new EngineError("interval must be >= 1 s");
        System.out.println("watch: every " + intervalSec + " s, rules " + rulesFile);
        while (true) {
            try {
                List<String> rules = new java.io.File(rulesFile).exists() ? readRules(rulesFile) : new ArrayList<>();
                // No rules means the host firewall is off: touch nothing on the host (not even Wi-Fi).
                if (!rules.isEmpty() && hostApply(rules, true)) System.out.println("watch: repaired");
                if (bubbleUser > 0) bubbleGuard(bubbleUser);
                metaGuard();
            } catch (EngineError e) {
                System.err.println("watch: " + e.getMessage());
            }
            try {
                Thread.sleep(intervalSec * 1000L);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /**
     * Mark every saved Wi-Fi network metered. Android has no global switch, only a per-network
     * override; Wi-Fi persists it to WifiConfigStore with a ~15 s delayed write.
     */
    private static boolean meterAllWifi(boolean verbose) {
        boolean changed = false;
        for (String line : sh("cmd", "netpolicy", "list", "wifi-networks").out.split("\n")) {
            int sep = line.lastIndexOf(';');
            if (sep <= 0 || line.substring(sep + 1).trim().equals("true")) continue;
            String id = line.substring(0, sep);
            sh("cmd", "netpolicy", "set", "metered-network", id, "true");
            changed = true;
            if (verbose) System.out.println("metered " + id);
        }
        return changed;
    }

    /**
     * Turn off "Background data" for a package through the Settings screen itself, so Settings
     * records it in its own manual denylist and stops wiping the policy on every boot
     * (DynamicDenylistManager.resetDenylistIfNeeded). Settings keeps that list in its private
     * preferences, which the shell UID cannot write, so the screen is the only way in.
     *
     * <p>Locale-independent: the first checkable widget on that screen is the "Background data"
     * switch. The result is verified against the real policy, never assumed. The phone must be
     * unlocked with the screen on.
     */
    private static void hostBackgroundDataOff(String pkg) {
        int uid = hostUid(pkg);
        // Settings shows the switch from the live policy; clear it first so the tap means "off".
        sh("cmd", "netpolicy", "remove", "restrict-background-blacklist", String.valueOf(uid));
        sh("input", "keyevent", "KEYCODE_WAKEUP");
        Result start = sh("am", "start", "-W",
                "-a", "android.settings.IGNORE_BACKGROUND_DATA_RESTRICTIONS_SETTINGS",
                "-d", "package:" + pkg);
        if (start.code != 0) throw new EngineError("could not open data screen for " + pkg + ": " + start.err());
        boolean tapped = false;
        for (int attempt = 0; attempt < 4 && !tapped; attempt++) {
            pause(1500);
            int[] center = firstCheckableCenter();
            if (center != null) {
                sh("input", "tap", String.valueOf(center[0]), String.valueOf(center[1]));
                tapped = true;
            } else {
                int[] size = screenSize();
                String x = String.valueOf(size[0] / 2);
                sh("input", "swipe", x, String.valueOf(size[1] * 3 / 4), x, String.valueOf(size[1] / 4), "300");
            }
        }
        pause(1000);
        sh("input", "keyevent", "KEYCODE_HOME");
        if (!tapped) throw new EngineError("background-data switch not found for " + pkg);
        if (!hasBackgroundDeny(uid)) {
            throw new EngineError("switch tapped but background data still allowed for " + pkg);
        }
        System.out.println("background data off (kept by Settings) " + pkg + " uid=" + uid);
    }

    private static boolean hasBackgroundDeny(int uid) {
        String list = sh("cmd", "netpolicy", "list", "restrict-background-blacklist").out;
        return (" " + list.replace('\n', ' ') + " ").contains(" " + uid + " ");
    }

    /** Center of the first checkable node on screen, from a uiautomator dump; null if none. */
    private static int[] firstCheckableCenter() {
        String dump = "/data/local/tmp/bubble-ui.xml";
        sh("uiautomator", "dump", dump);
        String xml = sh("cat", dump).out;
        sh("rm", "-f", dump);
        for (String node : xml.split("<node ")) {
            if (!node.contains("checkable=\"true\"")) continue;
            int b = node.indexOf("bounds=\"[");
            if (b < 0) continue;
            String[] n = node.substring(b + 9, node.indexOf('"', b + 9))
                    .replace("][", ",").replace("]", "").split(",");
            if (n.length != 4) continue;
            return new int[] {
                    (Integer.parseInt(n[0]) + Integer.parseInt(n[2])) / 2,
                    (Integer.parseInt(n[1]) + Integer.parseInt(n[3])) / 2 };
        }
        return null;
    }

    /** Screen size in pixels from "wm size" (the override line wins when present). */
    private static int[] screenSize() {
        int[] size = {1080, 2400};
        for (String line : sh("wm", "size").out.split("\n")) {
            int colon = line.indexOf(':');
            String[] wh = colon < 0 ? new String[0] : line.substring(colon + 1).trim().split("x");
            if (wh.length == 2) size = new int[] {Integer.parseInt(wh[0].trim()), Integer.parseInt(wh[1].trim())};
        }
        return size;
    }

    private static void say(String line) {
        System.out.println(line);
        System.out.flush();
    }

    /** Run a command while printing a line every 5 s, so the adb stream's read timeout never fires. */
    private static Result shWithHeartbeat(String label, String... cmd) {
        final Result[] box = new Result[1];
        final RuntimeException[] failure = new RuntimeException[1];
        Thread worker = new Thread(() -> {
            try {
                box[0] = sh(cmd);
            } catch (RuntimeException e) {
                failure[0] = e;
            }
        });
        worker.start();
        try {
            while (true) {
                worker.join(5000);
                if (!worker.isAlive()) break;
                say(label + "...");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new EngineError(label + " interrupted");
        }
        if (failure[0] != null) throw failure[0];
        return box[0];
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void hostStatus(List<String> pkgs) {
        System.out.println("chain3=" + (chainEnabled() ? "enabled" : "disabled"));
        System.out.print(sh("cmd", "netpolicy", "list", "restrict-background-blacklist").out);
        System.out.print(sh("cmd", "netpolicy", "list", "wifi-networks").out);
        for (String pkg : pkgs) {
            System.out.print(sh("cmd", "connectivity", "get-package-networking-enabled", pkg).out);
        }
    }

    private static boolean chainEnabled() {
        return sh("cmd", "connectivity", "get-chain3-enabled").out.contains("chain:enabled");
    }

    private static void enableChain() {
        Result r = sh("cmd", "connectivity", "set-chain3-enabled", "true");
        if (r.code != 0) throw new EngineError("could not enable firewall chain: " + r.err());
    }

    /** App UID of a package in user 0, e.g. "package:com.x uid:10308" -> 10308. */
    private static int hostUid(String pkg) {
        for (String line : sh("pm", "list", "packages", "-U", "--user", "0", pkg).out.split("\n")) {
            line = line.trim();
            int u = line.indexOf(" uid:");
            if (line.startsWith("package:" + pkg + " ") && u > 0) {
                String uids = line.substring(u + 5);
                int comma = uids.indexOf(',');
                return Integer.parseInt(comma < 0 ? uids : uids.substring(0, comma));
            }
        }
        throw new EngineError("package not installed for user 0: " + pkg);
    }

    /** Rules file: one package name per line; blank lines and '#' comments ignored. */
    private static List<String> readRules(String path) {
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(
                new java.io.FileInputStream(path), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                int hash = line.indexOf('#');
                line = (hash < 0 ? line : line.substring(0, hash)).trim();
                if (!line.isEmpty()) out.add(line);
            }
        } catch (java.io.IOException e) {
            throw new EngineError("could not read rules file " + path + ": " + e.getMessage());
        }
        return out;
    }

    private static void status(int userId) {
        System.out.println("whoami_uid_domain: run via app_process from the paired adb channel");
        Result users = sh("pm", "list", "users");
        System.out.print(users.out);
        List<String> pkgs = listPackages(userId);
        long google = pkgs.stream().filter(BubbleEngine::isGoogle).count();
        System.out.println("user " + userId + ": packages=" + pkgs.size() + " google_present=" + google);
    }

    // ---- helpers ----------------------------------------------------------

    private static List<String> listPackages(int userId, String... filters) {
        List<String> cmd = new ArrayList<>(Arrays.asList("pm", "list", "packages"));
        cmd.addAll(Arrays.asList(filters));
        cmd.add("--user");
        cmd.add(String.valueOf(userId));
        Result r = sh(cmd.toArray(new String[0]));
        if (r.code != 0) throw new EngineError("could not list packages for user " + userId + ": " + r.err());
        List<String> out = new ArrayList<>();
        for (String line : r.out.split("\n")) {
            line = line.trim();
            if (line.startsWith("package:")) out.add(line.substring("package:".length()));
        }
        return out;
    }

    private static boolean isGoogle(String pkg) {
        for (String p : GOOGLE_PREFIXES) {
            if (pkg.equals(p) || pkg.startsWith(p)) return true;
        }
        return false;
    }

    private static void guardNotSystemUser(int userId) {
        if (userId <= 0) {
            throw new EngineError("refusing to operate on user " + userId
                    + ": user 0 is the primary Android and must stay untouched (banking apps live there)");
        }
    }

    private static int requireUserId(String[] args) {
        String raw = requireArg(args, 1, "user-id");
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException e) {
            throw new EngineError("invalid user-id: " + raw);
        }
    }

    private static String requireArg(String[] args, int index, String name) {
        if (args.length <= index) throw new EngineError("missing argument: " + name);
        return args[index];
    }

    private static int parseTrailingInt(String s) {
        int i = s.length() - 1;
        while (i >= 0 && !Character.isDigit(s.charAt(i))) i--;
        int end = i + 1;
        while (i >= 0 && Character.isDigit(s.charAt(i))) i--;
        if (end == i + 1) return -1;
        try {
            return Integer.parseInt(s.substring(i + 1, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private static Result sh(String... cmd) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
            String out = read(p.getInputStream());
            String err = read(p.getErrorStream());
            int code = p.waitFor();
            return new Result(code, out, err);
        } catch (Exception e) {
            throw new EngineError("failed to run " + Arrays.toString(cmd) + ": " + e.getMessage());
        }
    }

    private static String read(java.io.InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append('\n');
        }
        return sb.toString();
    }

    private static void usage() {
        System.err.println(String.join("\n",
                "Bubble privileged engine (run via app_process from the paired adb channel).",
                "Usage: bubble-shell <command> [args]",
                "  bubble-create               create the minimal Private Space and start it; if one",
                "                              exists, report it and ask (bubble_decision=needed)",
                "  bubble-adopt                use the existing Private Space, only without Google services",
                "  bubble-recreate <user-id>   remove that Private Space (data lost), create a new one",
                "  bubble-delete <user-id>     remove the bubble (data lost), create nothing",
                "  undo-tweaks                 restore silenced events and usage dump, delete saved state",
                "  bubble-status               report the bubble's user id, state and Google left",
                "  bubble-apps <id>            list the user's apps inside the bubble",
                "  bubble-add <id> <pkg>...    add apps installed outside (never Google services)",
                "  bubble-remove <id> <pkg>... uninstall apps from the bubble only",
                "  bubble-launch <id> <pkg> [bubble-pkg]  open that app of the bubble (unlocked)",
                "  meta-create                 create the Meta space (work profile, Play Services disabled)",
                "  meta-status                 report the Meta space: id, state, Play Services",
                "  meta-add <id> <pkg>...      add apps installed outside to the Meta space",
                "  meta-delete <id>            remove the Meta space with its apps and data",
                "  meta-apps <id>              list the user's apps in the Meta space",
                "  meta-remove <id> <pkg>...   uninstall apps from the Meta space only",
                "  meta-firewall <id> <bubble-pkg> [pkg...]  only these Meta apps have network",
                "  meta-keyboard <id> <ime-id> enable and select a keyboard in the Meta space",
                "  meta-settings-off <id>      turn off the Meta space's own Settings (stops the Play Services prompt)",
                "  bubble-smart-lock <id> <bubble-pkg>  the bubble locks itself unless an app inside plays",
                "  launcher-icons              report the launcher's home-screen icon size in px",
                "  bubble-tools <id>               add the installer and file picker if missing",
                "  bubble-allow-installs <id> <pkg>  let an app store inside install apps",
                "  bubble-keyboard <id> <ime>  enable and select the bubble's keyboard",
                "  quiet-logs                  stop writing the events that reveal the bubble",
                "  notification-privacy        restrict host listeners, assistant and notification queries",
                "  bubble-firewall <id> <bubble-pkg> [pkg...]  only these apps inside have network",
                "  firewall-on <user-id>       deny-by-default network for the bubble",
                "  firewall-off <user-id>      lift the firewall chain",
                "  persist-access              make the paired adb key never expire",
                "  status <user-id>            report users and Google presence",
                "Host firewall (user 0, network rules only):",
                "  host-block <pkg>...         deny all network to packages (Wi-Fi + mobile)",
                "  host-unblock <pkg>...       lift those rules",
                "  host-apply <rules-file>     re-apply missing rules and meter every Wi-Fi",
                "  watch <rules-file> [s] [id] guard every s seconds (default 5): re-apply host rules,",
                "                              disable any Google service inside bubble user id",
                "  host-bgdata-off <pkg>...    turn off Background data via Settings (survives boot)",
                "  host-metered                mark every saved Wi-Fi network metered",
                "  host-status [rules-file]    show chain, denylist, Wi-Fi meteredness"));
    }

    private static final class Result {
        final int code;
        final String out;
        final String errText;
        Result(int code, String out, String errText) { this.code = code; this.out = out; this.errText = errText; }
        String err() {
            String e = errText == null ? "" : errText.trim();
            return e.isEmpty() ? ("exit " + code) : e.toLowerCase(Locale.ROOT);
        }
    }

    private static final class EngineError extends RuntimeException {
        EngineError(String m) { super(m); }
    }
}
