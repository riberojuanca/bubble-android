# Firewall layers

The per-app network controls available without root, what each one survives, and what resets them. Bubble uses two different mechanisms: a VPN inside each profile, and kernel rules for apps of the normal phone.

## Inside the bubble and the Meta space: an always-on VPN

Bubble's copy in each profile runs `BubbleVpnService` as that profile's **always-on VPN** (`always_on_vpn_app`, without lockdown):

- Android starts it every time the profile is unlocked, including after a reboot (about 3 s on the emulator). It needs neither `shell` nor Wi-Fi. This matters because on Android 17 the profile's `BOOT_COMPLETED` does not reach Bubble's icon-less copy, and broadcasts sent while the profile is locked are dropped.
- Known gap: between unlocking and the VPN coming up, a blocked app could briefly reach the network. Lockdown would close it, but would cut the whole profile's network if the VPN ever failed; it was not adopted.
- Allowed apps are excluded from the VPN (`addDisallowedApplication`) and use the network directly. Everything else is routed into the tunnel, which drops all packets. Deny by default.
- **Wi-Fi and mobile data separately.** Rules are tokens: `pkg` (always), `pkg@wifi`, `pkg@mobile`. The VPN tracks the active network with `ConnectivityManager.registerBestMatchingNetworkCallback` and rebuilds the tunnel with the list for that transport when it changes.
- The VPN consent normally needs the `vpndialogs` package, which the minimal profile does not have; `shell` grants it with `appops set --user N <pkg> ACTIVATE_VPN allow`, and sets the always-on package with `settings put --user N secure always_on_vpn_app`.
- A profile's own VPN slot is taken by the firewall, so a personal VPN can only be used on the normal phone.

## Apps of the normal phone (user 0)

Bubble can also cut specific apps of the main phone (for example, device-management apps a user does not want online) without a VPN. This is the only case where the engine touches user 0, and only with network rules: it never installs, removes, disables or suspends anything there.

### Why not a VPN here

- A VPN firewall is an app: if Android stops it or something tears the VPN down, blocking ends. On the phone, the Play Store (`com.android.vending`) holds `CONNECTIVITY_INTERNAL`, which explains reports of it dropping VPN firewalls during downloads.
- The Play Store does **not** hold `NETWORK_SETTINGS` or `MANAGE_NETWORK_POLICY`, which control the layers below. `shell` holds both.
- At boot a VPN does not exist until its app starts.

### The layers

| Layer | Blocks | Survives reboot | Who can undo it |
|---|---|---|---|
| Chain `OEM_DENY_3` (`cmd connectivity set-package-networking-enabled false <pkg>`) | Everything, Wi-Fi and mobile, foreground and background; enforced in the kernel (BPF) | **No** | Holders of `NETWORK_SETTINGS`; the guard re-applies it |
| Policy `REJECT_METERED_BACKGROUND` (`cmd netpolicy add restrict-background-blacklist <uid>`) | Background data on metered networks | Yes, loaded by `system_server` before any app… | …but **Settings clears it ~10 s after boot** unless the user set it from Settings |
| Wi-Fi as metered (`cmd netpolicy set metered-network <SSID> true`) | Makes the previous layer apply on Wi-Fi too | Yes (WifiConfigStore, ~15 s delayed write) | — |
| The guard (`watch`) | Re-applies whatever is missing every minute | No: Bubble restarts it when it reconnects | Nothing else: a uid-2000 process, outside Doze and battery restrictions, detached from the adb connection that launched it |

Functional test: a browser with the chain active → `ERR_NAME_NOT_RESOLVED`; released → page loads.

`OEM_DENY_3` was disabled and unused on the phone (`get-chain3-enabled` → `chain:disabled`), so using it does not override anything of the manufacturer. The rule works per appId, so it also applies to the same app inside a profile.

### What Settings clears at boot

An `ipc-trace` during boot points to `com.android.settings.fuelgauge.datasaver.DynamicDenylistManager.resetDenylistIfNeeded`, called from `onBootComplete` via `BatterySettingsMigrateChecker`:

- It gets `NetworkPolicyManager.getUidsWithPolicy(REJECT_METERED_BACKGROUND)` and resets the policy of every uid **not** in its "manual denylist" preference.
- That preference is filled only when the user turns off **Background data** for the app in Settings (`DataSaverBackend.setIsDenylisted`). `syncPolicyIfNeeded` copies existing policies once (`manual_denylist_synced`) and ignores them afterwards.
- Tested: a block made in Settings survived two reboots; one made with `cmd` was cleared every time.

So, for background blocking from the first instant of boot, also without Wi-Fi, turn off *Background data* once by hand for each blocked app. The manual describes it.

### A reboot, in order

1. `system_server` starts: background policy loaded, networks metered, chain off.
2. ~10 s after `boot_completed`: Settings clears non-manual policies.
3. Bubble regains `shell` (needs trusted Wi-Fi) and starts the guard: chain and everything else.

Remaining gap: between 2 and 3, background data for apps without the manual Settings block; foreground until 3.

### Rejected

- Global Data Saver (`cmd netpolicy set restrict-background true`) persists, but Google Play Services is in its exception list while every other app's background is blocked.
- Android has no "all Wi-Fi metered" switch; it is per network, so the guard marks each new saved network.
- Rules go by **package**, not uid: a factory reset changes uids.
