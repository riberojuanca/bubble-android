# Tools

| Path | What |
|---|---|
| `private-space-keep.txt` | Reference keep list of the minimal Private Space on the Pixel 9a. The engine computes the real list on each phone |
| `emulator/` | Harness to test a profile exclusion list on the emulator before any real phone |
| `probes/UsageProbe.java` | Probe app: what an app holding Google's cross-user permissions can read about another profile |

## Emulator harness

Image: `system-images;android-37.1;google_apis_ps16k;x86_64` (Android 17, userdebug). Create an AVD, boot it once, and save a snapshot named `clean` with no profiles.

- `reset.sh`: restores the `clean` snapshot and waits for boot.
- `try-profile.sh <keep-list>`: creates a `PRIVATE` profile with `service call user 5`, excluding every package of `stock-emulator.txt` (or `$STOCK`) not in the keep list. Prints **FAIL** on any crash of `system_server`, user 0's SystemUI or any process in the crash buffer after create, start, reboot and start again; **PASS** otherwise.
- `stock-emulator.txt`: the 194 packages a standard Private Space gets on the emulator. `stock-pixel9a.txt`: the 186 it got on the Pixel 9a.
- `keep-emulator.txt`: the validated minimal keep list (8 entries; 9 installed, since WebView is always added).

Environment: `ADB` (default `adb`), `SERIAL` (default `emulator-5554`). Run `reset.sh` before each attempt.

```sh
tools/emulator/reset.sh
tools/emulator/try-profile.sh tools/emulator/keep-emulator.txt
# PASS user=10 installed=9 excluded=185
```

## Usage probe

Built by hand (aapt2 + javac + d8) with `UsageProbe-AndroidManifest.xml`, installed on the **emulator**, and given with `pm grant` the permissions Google Play Services holds on the phone (`INTERACT_ACROSS_USERS`, `DUMP`, `PACKAGE_USAGE_STATS`). It logs (tag `UsageProbe`) what it can read about another user: installed packages, usage statistics, the usage dump. Pass the user id with `--ei user <id>`. Results: [what Google can see](../docs/reverse-engineering/google-visibility.md).
