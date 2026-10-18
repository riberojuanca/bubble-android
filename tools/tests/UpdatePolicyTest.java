package org.bubble.app;

/** JVM checks for update ordering and untrusted release inputs; no Android runtime needed. */
public final class UpdatePolicyTest {
    public static void main(String[] args) {
        require(UpdatePolicy.versionCode("v0.1.1") == 101, "Patch version");
        require(UpdatePolicy.versionCode("1.0.0") == 10000, "Major version");
        require(UpdatePolicy.versionCode("0.2.0") > UpdatePolicy.versionCode("0.1.99"), "Rollover");
        reject(() -> UpdatePolicy.versionCode("0.1.100"));
        reject(() -> UpdatePolicy.versionCode("0.01.1"));
        reject(() -> UpdatePolicy.versionCode("0.1.1-beta"));
        reject(() -> UpdatePolicy.versionCode("210000.0.0"));
        require(UpdatePolicy.isTrustedUrl(UpdatePolicy.DOWNLOAD_PREFIX + "v0.1.1/bubble-0.1.1.apk"),
                "Official release URL");
        require(UpdatePolicy.isTrustedUrl("https://release-assets.githubusercontent.com/file?token=value"),
                "GitHub asset redirect");
        require(!UpdatePolicy.isTrustedUrl("http://github.com/file"), "Reject cleartext");
        require(!UpdatePolicy.isTrustedUrl("https://github.com.evil.example/file"), "Reject host suffix");
        require(!UpdatePolicy.isTrustedUrl("https://github.com@evil.example/file"), "Reject user info");
        require(!UpdatePolicy.isTrustedUrl("https://evil.example.githubusercontent.com.evil.example/file"),
                "Reject deceptive CDN host");
        require(!UpdatePolicy.isTrustedUrl("https://github.com:444/file"), "Reject alternate port");
        require(!UpdatePolicy.isTrustedUrl("file:///tmp/update.apk"), "Reject local file URLs");
        String hash = "a".repeat(64);
        require(UpdatePolicy.checksum(hash + "  bubble-0.1.1.apk\n", "bubble-0.1.1.apk").equals(hash),
                "Checksum manifest");
        require(UpdatePolicy.checksum(hash.toUpperCase(java.util.Locale.ROOT) + " *bubble-0.1.1.apk",
                "bubble-0.1.1.apk").equals(hash), "Binary checksum format");
        reject(() -> UpdatePolicy.checksum(hash + " other.apk", "bubble-0.1.1.apk"));
        reject(() -> UpdatePolicy.checksum("abc bubble-0.1.1.apk", "bubble-0.1.1.apk"));
        reject(() -> UpdatePolicy.checksum(hash + " bubble-0.1.1.apk\n" + hash + " other.apk",
                "bubble-0.1.1.apk"));
        System.out.println("Update policy: 20 checks passed");
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void reject(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Invalid input was accepted");
    }
}
