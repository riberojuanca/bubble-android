# Security

Bubble holds a lot of power for an app. This page explains exactly what it holds, why, and how to report a problem.

## Reporting a vulnerability

Please **do not** open a public issue for a security problem. Use GitHub's private reporting: **Security → Report a vulnerability** on this repository. Include the phone, Android version, Bubble version, and steps to reproduce. You will get an answer as soon as possible; fixes are credited unless you prefer otherwise.

## What Bubble can do, and why

### The `shell` identity (through wireless debugging)

Granted once, by you, when you pair with the 6-digit code. While the engine runs, it has the powers of `adb shell`: create and delete profiles, install and remove apps in them, change system settings and network policy. Bubble needs this to build the bubble without Google and to filter its network; no ordinary permission allows it.

- **Who can use it:** only a client holding a key that the phone's adb daemon has authorized. Bubble's key is generated inside the Android Keystore (StrongBox when available) and **cannot be exported**: copying Bubble's files does not give someone the key.
- **When it is open:** only while Bubble works (setup, maintenance, actions that need it). Bubble connects to `127.0.0.1`; nothing listens for the outside network beyond Android's own wireless-debugging service.
- **The guard** is a long-lived `shell` process. It only re-applies Bubble's firewall rules and disables Google services in the profiles Bubble created.
- **Key expiry is disabled** (`adb_allowed_connection_time=0`) so Bubble keeps working; you can revoke it anytime in *Developer options → Revoke debugging authorizations*.

### Permissions granted with `pm grant`

These carry Android's `development` flag, which allows granting them over adb:

| Permission | Why |
|---|---|
| `WRITE_SECURE_SETTINGS` | Turn wireless debugging back on if you turned it off; keep Bubble's settings in place |
| `INTERACT_ACROSS_USERS` | Talk to Bubble's own copy inside the bubble and the Meta space (the bridge) |
| `MODIFY_QUIET_MODE` | Lock the bubble when the screen turns off (smart lock) |

### Normal permissions

- Internet: to download F-Droid and the fallback keyboard from f-droid.org, with pinned signatures.
- Network and Wi-Fi state, Wi-Fi multicast: to know whether wireless debugging can work and to find the pairing port on the phone (mDNS).
- Notifications: the pairing code field and warnings.
- Foreground service: while pairing and during maintenance.
- Boot completed: to restore wireless debugging and the guard after a reboot.
- Query all packages: to list your apps when you add them to the bubble or the Meta space.
- VPN service (inside each profile): the firewall.
- Notification listener, approved inside the bubble only: to know whether something is playing, for the smart lock. It reads no notification content.

## Design choices that limit risk

- **No third-party libraries.** Pairing (SPAKE2), the adb client and TLS handling are written in the app against Android's own crypto, so the code that touches the `shell` key is all in this repository.
- **The bridge** between Bubble's copies is exported only under a signature permission and checks that the caller has the same app id. A profile only accepts commands from user 0.
- **No app inside the bubble can turn its firewall off**: the list is owned by the main app.
- **User 0 is never modified** except for network rules you choose in the phone's firewall.
- **Remove everything** undoes every system change Bubble made.

## What an attacker with your unlocked phone could do

The same as with `adb` access: anything `shell` can do. That is true of any phone with wireless debugging paired. If this matters to you, revoke debugging authorizations when you do not need Bubble's management features; the bubble, its firewall and the bridge keep working without them.

## Supported versions

Only the latest release receives security fixes.
