# Reverse engineering

Bubble is built on facts read from a real phone, not on documentation or assumptions. This section records those findings: what Android 17 does, where in its code it does it, and how each claim was checked.

## Method

- **Device**: Pixel 9a, stock Android 17 (API 37), locked bootloader, verified boot green, `user` build. Findings were also reproduced on the Android 17 emulator (`sdk_gphone64`, Google APIs image).
- **Sources**: the phone's own `framework.jar`, `services.jar` (system_server), `framework-res.apk`, `SettingsProvider.apk` and `Launcher3`, pulled with `adb pull` and read with `dexdump`, JADX and `aapt2`. Where a binary is not readable (`logd`, `liblog`), the AOSP source of the `main` branch is cited and the behaviour is then checked on the phone.
- **Probes**: `dumpsys`, `cmd`, `service call` and small throw-away probe apps installed on the emulator with exactly the permissions the phone's Google services hold.
- **Safety rule**: anything that changes how the system boots is tried on the emulator first. One experiment on the real phone caused a boot loop and a factory reset; see [Private Space](private-space.md#the-boot-loop).

Transaction numbers, constants and method names below are for the build tested. Check them again on another version before reusing them.

## Topics

| Topic | Question it answers |
|---|---|
| [Private Space](private-space.md) | Why the bubble is a hidden profile, how it is created without Google, and the minimum set of packages it needs |
| [Shell identity and adb](shell-identity-and-adb.md) | How an app gets `shell` power without root and keeps it across reboots |
| [Pairing protocol](pairing-protocol.md) | The wireless-debugging pairing (SPAKE2 + TLS 1.3), implemented without libraries |
| [What Google can see](google-visibility.md) | The permissions of the phone's Google services, tested one by one against the bubble |
| [System logs](system-logs.md) | What the log daemon exposes and how events are kept from being written |
| [Quiet mode and smart lock](quiet-mode-and-smart-lock.md) | Why music stops when a Private Space locks, and how Bubble locks it itself |
| [Cross-profile access](cross-profile.md) | Opening bubble apps from outside, and the Binder bridge between Bubble's copies |
| [Recents and setup state](recents-and-setup.md) | Why bubble apps vanished from the recent apps |
| [Launcher and widgets](launcher-and-widgets.md) | Icon size, folders, and what a widget can and cannot do |
| [Firewall layers](firewall-layers.md) | The per-app network controls available to `shell`, and what resets them |
| [The Instagram/Facebook crash](instagram-firebase-crash.md) | Why Meta's apps crash in a Google-free profile, and the work profile that avoids it |
| [The virtualization dead end](avf-dead-end.md) | Why the bubble is not a virtual machine |
| [The ceiling without root](no-root-ceiling.md) | What cannot be changed without owning the operating system |
