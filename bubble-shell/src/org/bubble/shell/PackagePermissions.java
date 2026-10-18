package org.bubble.shell;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads installed package and shared-UID grants without mixing dump sections. */
final class PackagePermissions {
    private static final Pattern MEMBER = Pattern.compile("PackageSetting\\{[^ ]+ ([^/]+)/[0-9]+\\}");
    private static final Pattern GRANT = Pattern.compile("\\s+([^ :]+): granted=true(?:[ ,].*)?");

    static Map<String, Set<String>> parse(String dump) {
        Map<String, Set<String>> packages = new LinkedHashMap<>();
        Map<String, Set<String>> sharedPermissions = new LinkedHashMap<>();
        Map<String, Set<String>> sharedMembers = new LinkedHashMap<>();
        String section = "", current = null;
        for (String line : dump.split("\n")) {
            if (!line.isEmpty() && !Character.isWhitespace(line.charAt(0))) {
                section = line.trim();
                current = null;
                continue;
            }
            if (section.equals("Packages:") && line.startsWith("  Package [")) {
                current = name(line);
                packages.put(current, new LinkedHashSet<>());
            } else if (section.equals("Shared users:") && line.startsWith("  SharedUser [")) {
                current = name(line);
                sharedPermissions.put(current, new LinkedHashSet<>());
                sharedMembers.put(current, new LinkedHashSet<>());
            } else if (current != null) {
                Matcher permission = GRANT.matcher(line);
                if (section.equals("Packages:") && permission.matches()) {
                    packages.get(current).add(permission.group(1));
                } else if (section.equals("Shared users:")) {
                    Matcher member = MEMBER.matcher(line);
                    if (member.find()) sharedMembers.get(current).add(member.group(1));
                    if (permission.matches()) sharedPermissions.get(current).add(permission.group(1));
                }
            }
        }
        if (!packages.containsKey("android")) throw new IllegalStateException("Incomplete installed package permissions");
        for (String group : sharedMembers.keySet()) {
            for (String pkg : sharedMembers.get(group)) {
                if (packages.containsKey(pkg)) packages.get(pkg).addAll(sharedPermissions.get(group));
            }
        }
        return packages;
    }

    private static String name(String line) {
        int start = line.indexOf('['), end = line.indexOf(']', start);
        if (start < 0 || end < 0) throw new IllegalStateException("Malformed package header");
        return line.substring(start + 1, end);
    }
}
