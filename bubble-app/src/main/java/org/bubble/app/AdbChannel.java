package org.bubble.app;

/**
 * Bubble's channel to the phone's own wireless-debugging daemon (127.0.0.1), through which the
 * privileged engine runs with the shell UID.
 *
 * <p>Two phases, split so the hard one is isolated:
 * <ul>
 *   <li><b>pair</b> — once, with the 6-digit code the phone shows. This is the SPAKE2 handshake of
 *       {@link PairingClient}; it is the ONLY part that must be written by hand (see
 *       {@code docs/reverse-engineering/pairing-protocol.md}). It authorizes our key in the daemon's trust
 *       store; the shell UID cannot write that store directly (verified: SELinux denies
 *       {@code /data/misc/adb/adb_keys}), so pairing is unavoidable.</li>
 *   <li><b>connect / exec</b> — every time after. Plain mutual TLS 1.3 with the authorized key, then
 *       the adb stream protocol. No custom cryptography: {@code SSLSocket} does the TLS. This part
 *       is implemented in the app (no third-party library).</li>
 * </ul>
 */
interface AdbChannel {

    boolean isConnected();

    /** Open a TLS session to the local daemon; false while debugging is off or the key is not paired. */
    boolean connect(long timeoutMs);

    void disconnect();

    /** Run a command with the shell UID; returns its combined stdout+stderr once it exits. */
    String exec(String command) throws Exception;

    /**
     * Run a command through the "shell:" service rather than "exec:". Needed for the detached guard:
     * adbd kills an "exec:" command's process group when the stream closes, even a setsid child,
     * while a "shell:" one survives (verified on Android 17). Output is discarded.
     */
    void shellDetached(String command) throws Exception;

    /** {@link #exec} for a command that can stay silent for minutes (installing an APK). */
    String execLong(String command) throws Exception;

    /** Run a command and write its output to {@code sink} as it arrives (large outputs: an APK). */
    void execTo(String command, java.io.OutputStream sink) throws Exception;

    /**
     * Run a command and stream {@code data} to its stdin (adb's "exec-in", as {@code adb install}
     * does); returns its output once it exits. For large inputs such as APKs.
     */
    String execWithInput(String command, java.io.InputStream data) throws Exception;

    /** Write a small file as the shell UID (engine jar, rules file). */
    void writeFile(String path, byte[] data) throws Exception;
}
