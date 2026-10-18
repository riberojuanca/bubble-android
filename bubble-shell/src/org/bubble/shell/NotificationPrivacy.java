package org.bubble.shell;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Host notification access hardening. Does not claim isolation from the operating system. */
final class NotificationPrivacy {
    interface Commands { String run(String... args); }

    private static final String LISTENERS = "enabled_notification_listeners";
    private static final String OWN_LISTENER = "org.bubble.app/org.bubble.app.AdbNoticeListener";
    private final Commands commands;
    private final File journal;
    private final Properties previous = new Properties();
    final Set<String> systemQueryExemptions = new LinkedHashSet<>();

    NotificationPrivacy(Commands commands, File journal) {
        this.commands = commands;
        this.journal = journal;
        if (journal.exists()) {
            try (FileInputStream in = new FileInputStream(journal)) {
                previous.load(in);
            } catch (java.io.IOException e) {
                throw new IllegalStateException("Cannot read notification recovery state", e);
            }
        }
    }

    void enforce() {
        // Save before mutation. The assistant approval is shared between the parent user and its
        // profiles, so the parent's (user 0) baseline is what restore puts back.
        String assistant = commands.run("cmd", "notification", "get_approved_assistant", "0").trim();
        Set<String> assistants = components(assistant);
        if (!assistants.isEmpty()) {
            remember("assistants.0", assistant);
        }
        for (String component : assistants) {
            commands.run("cmd", "notification", "disallow_assistant", component, "0");
        }
        if (!components(commands.run("cmd", "notification", "get_approved_assistant", "0")).isEmpty()) {
            throw new IllegalStateException("Host notification assistant remains authorized");
        }
        for (String component : components(setting("0", LISTENERS))) {
            if (OWN_LISTENER.equals(component)) continue;
            remember("listener." + component, "revoked");
            commands.run("cmd", "notification", "disallow_listener", component, "0");
        }
        awaitListeners(null);

        // Permission holders are discovered, not limited to a hard-coded list of Google apps.
        Set<String> installed = new LinkedHashSet<>();
        java.util.Map<String, Integer> uids = new java.util.LinkedHashMap<>();
        for (String line : commands.run("pm", "list", "packages", "-U", "--user", "0").split("\n")) {
            if (line.trim().isEmpty()) continue;
            Matcher match = Pattern.compile("package:([^ ]+) uid:([0-9]+)").matcher(line.trim());
            if (!match.matches()) throw new IllegalStateException("Unrecognized host UID inventory");
            installed.add(match.group(1));
            uids.put(match.group(1), Integer.parseInt(match.group(2)));
        }
        if (!installed.contains("android")) throw new IllegalStateException("Incomplete host package inventory");
        String dump = commands.run("dumpsys", "package");
        if (!dump.contains("  Package [android]")) throw new IllegalStateException("Incomplete permission inventory");
        for (String pkg : permissionHolders(dump)) {
            if (!installed.contains(pkg) || trusted(pkg)) continue;
            if (uids.get(pkg) == 1000) {
                // A real Android 17 probe still reads notifications as UID 1000 with this
                // app-op at ignore. Do not mutate it or report that configuration as a block.
                if (pkg.startsWith("com.google.")) {
                    throw new IllegalStateException("Google notification reader shares the exempt system UID: " + pkg);
                }
                systemQueryExemptions.add(pkg);
                continue;
            }
            String response = commands.run("appops", "get", "--user", "0", pkg, "ACCESS_NOTIFICATIONS");
            String effectiveBefore = mode(response);
            if ("ignore".equals(effectiveBefore) || "deny".equals(effectiveBefore)) continue;
            // An explicit UID mode overrides the per-package setting. Refuse an unverified result.
            if (uidMode(response) != null) {
                throw new IllegalStateException("Notification access has a UID override for " + pkg);
            }
            String old = packageMode(response);
            remember("op." + pkg, old);
            commands.run("appops", "set", "--user", "0", pkg, "ACCESS_NOTIFICATIONS", "ignore");
            String effective = mode(commands.run("appops", "get", "--user", "0", pkg, "ACCESS_NOTIFICATIONS"));
            if (!"ignore".equals(effective) && !"deny".equals(effective)) {
                throw new IllegalStateException("Notification query access remains for " + pkg);
            }
        }
    }

    /** Restore only our recorded changes; keep the journal if any operation fails. */
    void restore() {
        for (String key : previous.stringPropertyNames()) {
            if (key.startsWith("op.")) {
                String pkg = key.substring(3);
                String current = packageMode(commands.run("appops", "get", "--user", "0", pkg, "ACCESS_NOTIFICATIONS"));
                if ("ignore".equals(current)) {
                    commands.run("appops", "set", "--user", "0", pkg, "ACCESS_NOTIFICATIONS", previous.getProperty(key));
                    if (!previous.getProperty(key).equals(packageMode(commands.run("appops", "get", "--user", "0", pkg, "ACCESS_NOTIFICATIONS")))) {
                        throw new IllegalStateException("Could not restore notification access for " + pkg);
                    }
                }
            } else if (key.startsWith("listener.")) {
                String component = key.substring(9);
                commands.run("cmd", "notification", "allow_listener", component, "0");
                awaitListeners(component);
            }
        }
        // The notification-assistant approval is shared between the parent user and its profiles:
        // allow/disallow on any one propagates to the others, so it cannot be set per profile.
        // Restore the parent (user 0) baseline, which is the one that governs; the profiles follow.
        // restoreAssistant verifies user 0 and throws if it could not be restored.
        restoreAssistant("0");
        if (journal.exists() && !journal.delete()) throw new IllegalStateException("Cannot remove notification recovery state");
    }

    private void restoreAssistant(String user) {
        String old = previous.getProperty("assistants." + user);
        if (old == null) return;
        Set<String> wanted = components(old);
        for (String current : components(assistant(user))) {
            if (!wanted.contains(current)) commands.run("cmd", "notification", "disallow_assistant", current, user);
        }
        for (String component : wanted) commands.run("cmd", "notification", "allow_assistant", component, user);
        if (!components(assistant(user)).equals(wanted)) {
            throw new IllegalStateException("Could not restore assistant for user " + user);
        }
    }

    private String setting(String user, String key) {
        return commands.run("settings", "get", "--user", user, "secure", key).trim();
    }

    private String assistant(String user) {
        return commands.run("cmd", "notification", "get_approved_assistant", user).trim();
    }

    private void awaitListeners(String restored) {
        // Android 17 writes the Settings mirror asynchronously after changing live approvals.
        // A bounded wait avoids mistaking that delayed write for an unsuccessful revocation.
        for (int attempt = 0; attempt < 30; attempt++) {
            Set<String> current = components(setting("0", LISTENERS));
            if (restored != null && current.contains(restored)) return;
            current.remove(OWN_LISTENER);
            if (restored == null && current.isEmpty()) return;
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Interrupted while verifying notification listeners", e);
            }
        }
        throw new IllegalStateException(restored == null ? "Host notification listeners remain authorized"
                : "Could not restore listener " + restored);
    }

    private void remember(String key, String value) {
        if (previous.containsKey(key)) return;
        previous.setProperty(key, value);
        File temp = new File(journal.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) {
            previous.store(out, "Notification access before Bubble hardening");
            out.getFD().sync();
        } catch (java.io.IOException e) {
            throw new IllegalStateException("Cannot save notification recovery state", e);
        }
        if (!temp.renameTo(journal)) throw new IllegalStateException("Cannot commit notification recovery state");
    }

    static Set<String> components(String raw) {
        Set<String> result = new LinkedHashSet<>();
        if (raw.trim().isEmpty() || "null".equals(raw.trim())) return result;
        for (String component : raw.trim().split(":")) {
            if (!component.matches("[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+")) {
                throw new IllegalStateException("Unrecognized notification component response");
            }
            int slash = component.indexOf('/');
            if (component.charAt(slash + 1) == '.') {
                component = component.substring(0, slash + 1) + component.substring(0, slash) + component.substring(slash + 1);
            }
            result.add(component);
        }
        return result;
    }

    static String mode(String output) {
        String override = uidMode(output);
        return override == null ? packageMode(output) : override;
    }

    private static String uidMode(String output) {
        Matcher uid = Pattern.compile("Uid mode:.*?ACCESS_NOTIFICATIONS: (allow|ignore|deny|default|foreground)(?:;|\\s|$)").matcher(output);
        if (uid.find()) return "default".equals(uid.group(1)) ? null : uid.group(1);
        if (output.contains("Uid mode:")) throw new IllegalStateException("Unrecognized notification UID app-op response");
        return null;
    }

    private static String packageMode(String output) {
        Matcher pkg = Pattern.compile("(?m)^\\s*ACCESS_NOTIFICATIONS: (allow|ignore|deny|default|foreground)(?:;|\\s|$)").matcher(output);
        if (pkg.find()) return pkg.group(1);
        if (output.contains("No operations.")) return "default";
        throw new IllegalStateException("Unrecognized notification app-op response");
    }

    static Set<String> permissionHolders(String dump) {
        Set<String> result = new LinkedHashSet<>();
        for (java.util.Map.Entry<String, Set<String>> entry : PackagePermissions.parse(dump).entrySet()) {
            if (entry.getValue().contains("android.permission.ACCESS_NOTIFICATIONS")) result.add(entry.getKey());
        }
        return result;
    }

    private static boolean trusted(String pkg) {
        // Required OS presentation, shell administration and Bubble itself remain in the trust boundary.
        return pkg.equals("android") || pkg.equals("com.android.systemui")
                || pkg.equals("com.android.shell") || pkg.equals("org.bubble.app");
    }
}
