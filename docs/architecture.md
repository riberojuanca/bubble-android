# Architecture

## The pieces

```
 Normal phone (user 0)                         Bubble (Private Space, user 10)
 ┌──────────────────────────────┐              ┌─────────────────────────────────┐
 │ Bubble app (org.bubble.app)  │──Binder──────│ Bubble's copy                   │
 │  UI, widget, Bubble Apps     │  (bridge)    │  ProfileControlService          │
 │  ProfileBridge               │              │  BubbleVpnService (firewall)    │
 │  adb-TLS client ─┐           │              │  SmartLock (inside the VPN)     │
 └──────────────────┼───────────┘              │  apps: Signal, NewPipe, F-Droid │
                    │ wireless debugging        └─────────────────────────────────┘
                    ▼ (127.0.0.1, own key)      Meta space (work profile, user 11)
 ┌──────────────────────────────┐              ┌─────────────────────────────────┐
 │ Engine: bubble-shell.jar     │──pm/cmd──────│ Bubble's copy + firewall        │
 │  runs as `shell` via         │  settings    │ Play Services installed, OFF    │
 │  app_process; guard (watch)  │              │ apps: Facebook, Instagram       │
 └──────────────────────────────┘              └─────────────────────────────────┘
```

### 1. The bubble: a Private Space

A hidden Android profile (`android.os.usertype.profile.PRIVATE`). Hidden profiles are invisible to apps without `ACCESS_HIDDEN_PROFILES_FULL`; a locked one is stopped and encrypted. Bubble creates it through `IUserManager.createProfileForUserWithThrow` with the list of system packages **not** to install, so Google services never enter it. See [reverse engineering: Private Space](reverse-engineering/private-space.md).

### 2. The engine (`bubble-shell`)

A Java program run with `app_process` over the phone's own wireless-debugging channel, so it inherits the `shell` UID and its permissions (`CREATE_USERS`, `INTERACT_ACROSS_USERS_FULL`, `MANAGE_NETWORK_POLICY`, `NETWORK_SETTINGS`, `WRITE_SECURE_SETTINGS`…). It does not use hidden APIs by reflection: it orchestrates the system's own command-line tools (`pm`, `cmd`, `settings`, `appops`, `service call`). The app ships the engine inside its APK and pushes it when it changes.

Commands are listed by running it without arguments; the main ones: `bubble-create/-adopt/-recreate/-delete/-status`, `bubble-add/-remove/-apps/-launch`, `bubble-firewall`, `bubble-smart-lock`, `meta-create/-status/-add/-remove/-firewall/-delete`, `quiet-logs`, `undo-tweaks`, `launcher-icons`, `host-*` (phone firewall), `watch` (the guard).

### 3. The app (`bubble-app`)

A normal APK with no third-party libraries. It holds:

- the **pairing** client (SPAKE2 + TLS, compatible with AOSP's `adb pair`) and an **adb-TLS client**, so it can open the `shell` channel by itself, with its key in the Android Keystore (StrongBox);
- the **UI**, **Bubble Apps** and the **widget**;
- the **bridge** (`ProfileBridge`): Binder calls to its own copy inside each profile.

### 4. Bubble's copy inside each profile

The same APK, installed in the bubble and in the Meta space, with its launcher icons disabled there. It runs:

- `BubbleVpnService`: the profile's **always-on VPN** and firewall, deny by default. Allowed apps are excluded from the VPN; the rest go into a tunnel that drops everything. It follows the active network and applies the Wi-Fi or mobile-data list.
- `SmartLock`: locks the bubble when the screen goes off unless one of its media sessions is playing.
- `ProfileControlService`: answers the bridge.

### 5. The bridge

With `INTERACT_ACROSS_USERS` and `MODIFY_QUIET_MODE`, both grantable with `pm grant` because they carry the `development` protection bit, Bubble reaches its copies with `bindServiceAsUser`, without wireless debugging. Used for: the state of a profile, its app list, opening an app (the copy returns a `PendingIntent` the main app sends), applying network choices (the copy pulls the list from the main app, which owns it). The service is exported only with a signature permission and checks that the caller is the same app. See [reverse engineering: cross-profile access](reverse-engineering/cross-profile.md).

### 6. The guard

The engine's `watch` command, started detached from the adb connection. Every minute it disables Google services in the bubble or the Meta space, re-applies the phone's firewall and re-sends firewall lists that could not reach a locked profile. It lives as long as `adbd` does: always with USB debugging on, otherwise per session.

## Who does what

| Task | Needs |
|---|---|
| Using the bubble (entering, apps, firewall) | Nothing from Bubble: it is native Android |
| Opening a bubble app from the widget, changing internet access, reading state | The bridge (no Wi-Fi) |
| Creating or deleting spaces, adding or installing apps, maintenance, the guard | The engine (wireless debugging, so Wi-Fi) |

## After a reboot

Wireless debugging comes back by itself on a trusted Wi-Fi network (`persist.adb.tls_server.enable`). Bubble's key never expires (`adb_allowed_connection_time=0`), and the app holds `WRITE_SECURE_SETTINGS` to turn wireless debugging on if the user turned it off. At boot and at each first status check, Bubble runs its maintenance when Wi-Fi is available. The silencing of system events survives reboots by itself (`persist.log.tag.*`). See [reverse engineering: shell identity](reverse-engineering/shell-identity-and-adb.md).

## Setup, once

1. Install the APK.
2. Turn on Developer options and Wireless debugging; pair with the 6-digit code.
3. Bubble pushes the engine, grants itself `WRITE_SECURE_SETTINGS`, `INTERACT_ACROSS_USERS` and `MODIFY_QUIET_MODE`, makes its key non-expiring.
4. Bubble creates the bubble, installs F-Droid and a keyboard without internet, sets up the firewall, the smart lock and the silenced events.
