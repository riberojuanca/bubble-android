package org.bubble.app;

import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Release identifiers and download checks shared by the updater and its JVM checks. */
final class UpdatePolicy {
    static final String DOWNLOAD_PREFIX =
            "https://github.com/riberojuanca/bubble-android/releases/download/";
    static final long MAX_APK_BYTES = 128L * 1024 * 1024;

    private UpdatePolicy() {}

    static long versionCode(String version) {
        Matcher match = Pattern.compile("v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)")
                .matcher(version);
        if (!match.matches()) throw new IllegalArgumentException("Invalid release version");
        long major = Long.parseLong(match.group(1));
        int minor = Integer.parseInt(match.group(2));
        int patch = Integer.parseInt(match.group(3));
        if (major > 209999 || minor > 99 || patch > 99) {
            throw new IllegalArgumentException("Release version out of range");
        }
        return major * 10000 + minor * 100 + patch;
    }

    static boolean isTrustedUrl(String value) {
        try {
            URI uri = URI.create(value);
            String host = uri.getHost();
            return "https".equals(uri.getScheme()) && uri.getUserInfo() == null
                    && (uri.getPort() == -1 || uri.getPort() == 443) && host != null
                    && (host.equals("github.com") || host.equals("api.github.com")
                    || host.endsWith(".githubusercontent.com"));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static String checksum(String text, String filename) {
        String[] fields = text.trim().split("\\s+");
        if (fields.length != 2 || !fields[0].matches("[0-9a-fA-F]{64}")
                || !fields[1].replaceFirst("^\\*", "").equals(filename)) {
            throw new IllegalArgumentException("Invalid release checksum");
        }
        return fields[0].toLowerCase(java.util.Locale.ROOT);
    }
}
