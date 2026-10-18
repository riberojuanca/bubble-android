# 0003. Survive reboots without a computer

- Date: 2026-10-16
- Status: Accepted

## Decision

After setup, Bubble regains `shell` without a computer and without pairing again, also after a reboot:

- it grants itself `WRITE_SECURE_SETTINGS` (possible because the permission has the `development` bit) so it can turn wireless debugging back on;
- its adb key never expires (`adb_allowed_connection_time=0`);
- it reconnects to `127.0.0.1` with its own adb-TLS client and its StrongBox key.

## Reasons

`WRITE_SECURE_SETTINGS` has `protectionLevel=0x04000132`, which includes `development` (`0x20`), so `pm grant` to an ordinary app works and is permanent. On Android 17, wireless debugging also stays on across reboots on trusted networks (`persist.adb.tls_server.enable`), contrary to common belief.

## Limits

- The TLS channel only starts on a real Wi-Fi connection (`AdbDebuggingManager` check). This affects managing the bubble, not using it or its firewall.
- The initial pairing is not removed: it is the consent gate that keeps this from being a backdoor.

Evidence: [shell identity and adb](../reverse-engineering/shell-identity-and-adb.md).
