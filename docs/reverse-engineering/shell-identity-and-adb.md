# Shell identity and adb

How Bubble gets the `shell` identity without root, and keeps it across reboots without a computer.

## Why `shell`

`dumpsys package com.android.shell`, relevant granted permissions:

```
CREATE_USERS, QUERY_USERS, MANAGE_USERS (via role)
INTERACT_ACROSS_USERS, INTERACT_ACROSS_USERS_FULL, INTERACT_ACROSS_PROFILES
ACCESS_HIDDEN_PROFILES_FULL, START_CROSS_PROFILE_ACTIVITIES
INSTALL_PACKAGES, DELETE_PACKAGES, CLEAR_APP_USER_DATA, SUSPEND_APPS
MANAGE_NETWORK_POLICY, OBSERVE_NETWORK_POLICY, CONNECTIVITY_INTERNAL
MANAGE_APP_OPS_MODES, WRITE_SECURE_SETTINGS
```

An ordinary app (`untrusted_app`) has none. The bubble cannot be built from an APK alone; it has to borrow `shell`. The only way to get it without root is adb, and on the phone itself that means **wireless debugging** to `127.0.0.1`. The engine is then started with `app_process` and runs as uid 2000.

## Where adb keys live

`AdbDebuggingManager.getAdbFile()` → `/data/misc/adb/`: `adb_keys` (permanently authorized public keys) and `adb_temp_keys.xml` (keys with their last connection time). Pairing survives reboots.

`shell` cannot write `adb_keys` (SELinux denies it; verified on the emulator). A key enters the daemon's trust only through the pairing protocol; see [pairing](pairing-protocol.md).

## Key expiry

`AdbKeyStore` reads `adb_allowed_connection_time`. Built-in default: `604800000` ms = 7 days since the last connection. A value of `0` disables expiry:

```
settings put global adb_allowed_connection_time 0
```

## Wireless debugging after a reboot

Tested on the emulator; the same code ships on the phone. A common belief, repeated by Shizuku and its forks, is that wireless debugging always turns off at boot. **On Android 17 it does not:**

- `AdbService.enableADBdWifi()` / `disableADBdWifi()` store the state in `persist.adb.tls_server.enable`; `AdbService.systemReady()` restores `adb_wifi_enabled` from it at boot.
- `AdbWifiNetworkMonitor` stops only the TLS server when Wi-Fi is lost ("Wi-Fi network lost. Stopping adb over Wi-Fi.") without touching the switch, and restarts it on reconnect.
- Before starting it, `AdbDebuggingHandler.verifyWifiNetwork()` calls `AdbKeyStore.isTrustedNetwork(bssid, ssid)`. On an untrusted network SystemUI asks *"Allow wireless debugging on this network?"* (`WifiDebuggingActivity`) and nothing opens.
- A network becomes trusted only when the user ticks *Always allow on this network* (`addTrustedNetwork`). It matches by BSSID **or** SSID, so it works with mesh routers.

| Case (emulator) | Result |
|---|---|
| Switch on, untrusted network | SystemUI prompt; nothing open until accepted |
| Accepted with *Always allow* | TLS port open |
| Reboot on a trusted network | Switch still on, TLS port open, no prompt, no app involved |
| Wi-Fi off / on | TLS closes / reopens by itself |
| Reboot without Wi-Fi, connect later | TLS opens on connect, no prompt |

The prompt cannot and should not be bypassed: it is a security confirmation.

## The switch, if the user turns it off

The app cannot set `adb_wifi_enabled=1` itself unless it holds `WRITE_SECURE_SETTINGS`. Read with `aapt2 dump xmltree framework-res.apk --file AndroidManifest.xml`:

```
android.permission.WRITE_SECURE_SETTINGS  protectionLevel = 0x04000132
```

`0x32` = signature (`0x02`) + privileged (`0x10`) + **development (`0x20`)**. The development bit means `pm grant` can give it to an ordinary app, permanently. The same bit is on `INTERACT_ACROSS_USERS` and `MODIFY_QUIET_MODE`, which Bubble also uses (see [cross-profile](cross-profile.md) and [smart lock](quiet-mode-and-smart-lock.md)). The idea of `pm grant WRITE_SECURE_SETTINGS` + `adb_wifi_enabled` is not new: Shizuku forks, Automate and AutoJs6 use it.

`SettingsProvider` gates that setting on the `no_debugging_features` user restriction, which is not set on the phone.

## The Wi-Fi requirement

`AdbDebuggingManager` refuses to start ("Not connected to any wireless network. Not enabling adbwifi.") unless `WifiManager.getConnectionInfo()` returns a real Wi-Fi client connection with a valid network id and SSID/BSSID. Bubble does not need internet (it talks to `127.0.0.1`), but it does need Wi-Fi. A hotspot or a VPN does not count. Removing the check needs root.

This affects only the engine. Using the bubble, its firewall and the bridge do not need it.

## "Always allow on this network"

On a network not marked *Always allow*, turning wireless debugging on makes `AdbDebuggingManager` switch it off again and ask the user. If the user tapped *Allow* during pairing without ticking the box, Bubble could pair but never turn debugging on there again.

From the Pixel 9a's `services.jar` (Android 17, `dexdump`): `AdbService.allowWirelessDebugging(boolean alwaysAllow, String bssid, String ssid)` only enforces `MANAGE_DEBUGGING`, which `shell` holds, and its handler adds the trusted network (`AdbKeyStore.addTrustedNetwork(bssid, ssid)`) without checking whether debugging is already on. AOSP main ignores the call while debugging is on. The trusted networks are readable as `shell` in `dumpsys adb` (keystore, `wifiAP` with `bssid` and the quoted `ssid`), and the current network in `cmd wifi status`.

So, right after pairing, the engine (`trust-network`) marks the network the user just allowed as *Always allow*, unless it already is. Verified on the Pixel 9a: an already trusted network is detected and left alone. Adding an untrusted one has not been run on a device yet.

## The resulting chain

1. **Setup** (once, with the user's pairing code): pair Bubble's key; `pm grant` the development permissions; `adb_allowed_connection_time=0` (phone-wide; the old value is saved and *Remove everything* restores it).
2. **Each boot**: on a trusted Wi-Fi network, wireless debugging comes back by itself; if the user turned it off, Bubble turns it on with `WRITE_SECURE_SETTINGS`.
3. **When needed**: Bubble's own adb-TLS client connects to the local TLS port with its key and runs the engine.

## The key

```
pm list features | grep keystore
→ android.hardware.strongbox_keystore=300
  android.hardware.hardware_keystore=500
```

Bubble's RSA-2048 key is generated in the Android Keystore (StrongBox when present) and is not exportable: both pairing and the TLS connection use it through JSSE's `KeyManager`, which signs inside the keystore. Someone who copies the app's files cannot take the key elsewhere.

## Why the pairing step cannot be removed

It is the consent gate. A fresh APK has no powers; `shell` is handed over only by an explicit human action. If an app could get it without that, it would be a vulnerability. Bubble removes the repetition after reboots, not the consent.
