# Testing

Bubble changes how Android profiles are created and governed, so testing happens on real system images, not mocks. This page lists what is tested, how, and what is still open.

## Rules

1. **Emulator first** for anything that affects profile creation or boot. A mistake there can boot-loop a phone, and adb cannot undo it ([why](reverse-engineering/private-space.md#the-boot-loop)).
2. **Then one real phone**, with a way back (the engine deletes a profile it just created if system processes crash).
3. Record how each claim was checked: command, log line, or source.

## Environments

| | Emulator | Phone |
|---|---|---|
| Image | Android 17 (API 37.1), `google_apis_ps16k`, x86_64, userdebug | Pixel 9a, Android 17, stock `user` build, locked bootloader |
| StrongBox | No (falls back to TEE) | Yes |
| Use | Profile exclusions, engine commands, firewall, smart lock, recents | Final check of every feature, real apps |

## Automated and scripted checks

- **Lint and compile**: `./gradlew :bubble-app:lint :bubble-app:assembleDebug`.
- **Minimal profile**: [`tools/emulator/try-profile.sh`](../tools/README.md#emulator-harness) gives PASS/FAIL for a keep list. Controls: the standard profile passes; the list that boot-looped the phone fails with the same SystemUI exception.
- **Bridge regression probe** (`bubble-app/src/androidTest/.../ProfileBridgeInstrumentation.java`): run on a configured phone **with Wi-Fi off**, so the engine cannot help. It checks that the bridge lists a profile's apps, can launch one in its own profile, and that a Wi-Fi/mobile change reaches that profile's VPN and changes its uid routing; it restores the user's choices even on failure.

  ```sh
  adb shell am instrument -w -e user <id> -e mode read org.bubble.app.test/org.bubble.app.ProfileBridgeInstrumentation
  adb shell am instrument -w -e user <id> -e mode launch -e package <pkg> org.bubble.app.test/org.bubble.app.ProfileBridgeInstrumentation
  adb shell am instrument -w -e user <id> -e mode network -e package <pkg> org.bubble.app.test/org.bubble.app.ProfileBridgeInstrumentation
  ```

  Build and install the test APK with `./gradlew :bubble-app:installDebugAndroidTest`.

## Verified end to end

On the emulator and then on the phone:

| Area | Checked |
|---|---|
| Key | RSA-2048 generated in the keystore (StrongBox on the phone, about 10 s) |
| Pairing | Daemon log `Pairing succeeded key=… bubble@device`, with the real notification flow (port found over mDNS) |
| adb-TLS channel | `adbd_wifi_secure_connect: connected`; engine running as `uid=2000 context=u:r:shell:s0` |
| Engine and guard | Engine pushed and run; guard survives the adb stream closing (parent becomes init) |
| Minimal bubble | Created, started and rebooted with no crash; F-Droid installs and runs inside |
| Firewall | Deny by default; VPN covers every uid of the profile except allowed apps; Wi-Fi-only app does not load on mobile data |
| Smart lock | Locks on screen off when idle; music keeps playing with the screen off; locks 5 s after playback stops |
| Recents | Bubble and Meta apps listed after `user_setup_complete=1` |
| Meta space | Facebook and Instagram run without crashing; guard disables Play Services if enabled |
| Bridge | Opens bubble apps and applies network changes without Wi-Fi |
| Logs | After `quiet-logs`, using the bubble leaves no event naming its user, uids or packages; persistent properties survive reboot |
| Usage dump | Denied to the probe after the app-op change; survives reboot |

## Bugs that only real devices revealed

Static review missed these; testing found them:

- **TLS with a keystore key**: TLS 1.3 signs with RSA-PSS and Conscrypt performs raw RSA through `Cipher`, so the key needs `PURPOSE_DECRYPT`, `DIGEST_NONE` and `ENCRYPTION_PADDING_NONE` besides the signature paddings.
- **adb connection handshake**: the connect port is not TLS from the start. The client sends `A_CNXN` in clear text, the daemon answers `A_STLS`, the client answers `A_STLS`, and only then TLS begins (`adb.cpp handle_new_connection`).
- **Guard killed at launch**: adbd kills the process group of an `exec:` command when its stream closes, even with `setsid`. Launched through `shell:` and kept open briefly (`… & sleep 1`), it survives.
- **Notification permission** (phone only): without requesting `POST_NOTIFICATIONS`, the pairing notification was silently suppressed.
- **Key generation on the main thread** (phone only): StrongBox takes about 10 s; moved off the UI thread.
- **Share sheet missing**: an app crashed with `ActivityNotFoundException … CHOOSER`; `com.android.intentresolver` added to the profiles.

## Notification hardening (initial emulator validation)

The Android 17 emulator checks passed listener revocation, the Play Services-UID dump restriction, reboot persistence and synthetic music/pause locking. The full enforce → undo-tweaks cycle restores user 0's listeners and assistant identically (the assistant approval is shared parent↔profiles, so only the parent's baseline is restored). The empirical leak→cut delivery test passed on both the emulator and the Pixel 9a. The privileged query path is covered by the notification-dump result (denied once the app-op is set to ignore); re-running the standalone query probe under an app UID aborts in ART in the test bench, a tooling limitation rather than a gap. See the [recorded results](reverse-engineering/notification-access.md#emulator-results-2026-10-18).

Host-only checks cover malformed command output, ignored mutations, repeated enforcement, recovery and journal-write failure (`tools/tests/NotificationPrivacyTest.java`). Java type checking does not establish Android permission behavior. See the [required emulator/Pixel matrix](reverse-engineering/notification-access.md#required-validation-before-release).

## Still open

- Phones other than the Pixel 9a, and Android versions other than 17.
- Network transitions in depth: losing the network, switching while an app transfers data, a profile restarting.
- Long-term behaviour across monthly system updates (transaction numbers and internal methods can change).
