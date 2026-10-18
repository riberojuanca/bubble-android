# 0005. An offline keyboard and a firewall inside each profile

- Date: 2026-10-16 (extended 2026-10-17)
- Status: Accepted

## Decision

The bubble's default keyboard is Gboard, copied from the phone and **without internet inside the bubble**. HeliBoard (from f-droid.org, with a pinned signature) is the fallback for phones without Gboard. Any other keyboard the user enables is respected.

Network access is cut by a **firewall inside the profile**: a local VPN (`BubbleVpnService`) run by Bubble's copy installed in the bubble.

## Reasons

- A keyboard sees everything typed. Without Google services in the bubble and without network, Gboard has no way to send data.
- The phone's kernel firewall (`cmd connectivity set-package-networking-enabled`) works by **appId**, so it blocks the app in every profile at once (verified: uid 10179 and 1010179 together). It cannot cut only the copy inside.
- VPNs are per profile: the bubble's sees only the bubble's uids (verified: Gboard outside with network, inside routed to the VPN).

## Deny by default (user decision, 2026-10-16)

Every app in the bubble goes into a tunnel that drops everything, except the allowed ones (`addDisallowedApplication`). Only F-Droid is online by default; the keyboard and any new app start offline. Verified on the emulator and the phone: the VPN covers the whole uid range of the profile except the allowed apps.

## How it works

- The engine (`bubble-firewall`) makes sure Bubble's copy is in the bubble, grants it the VPN consent with `appops ACTIVATE_VPN allow` (the minimal profile has no `vpndialogs`), and sets it as the profile's always-on VPN (`always_on_vpn_app`, without lockdown), so Android starts it on every unlock and after reboots.
- The copy hides its own launcher icon in the bubble (on Android 17, `shell` cannot change another app's component state; the app can change its own).
- The list of allowed apps is owned by the main app and pulled by the copy over the [bridge](../reverse-engineering/cross-profile.md). No app inside the bubble can turn the firewall off.
- The keyboard is added to the firewall **before** it becomes the input method.

## Wi-Fi and mobile data (2026-10-17)

Each app has separate permissions for Wi-Fi and mobile data. Tokens: `pkg` (both), `pkg@wifi`, `pkg@mobile`. The VPN follows the best non-VPN network ([`registerBestMatchingNetworkCallback`](https://developer.android.com/reference/android/net/ConnectivityManager#registerBestMatchingNetworkCallback(android.net.NetworkRequest,%20android.net.ConnectivityManager.NetworkCallback,%20android.os.Handler)), API 31+) and applies the matching list; Ethernet counts as Wi-Fi. Until the transport is known, or when the network is lost, only apps allowed on both are let through. A new tunnel is established before the old descriptor is closed, as [`VpnService.Builder.establish`](https://developer.android.com/reference/android/net/VpnService.Builder#establish()) allows.

## Known limits

- Between unlocking the bubble and the VPN starting, a blocked app could briefly reach the network. Lockdown would close this but would cut the whole bubble if the VPN failed; not adopted.
- Permissions are chosen by the best network, not filtered per packet by transport. An app excluded from the VPN that deliberately picks another available network is not restricted separately. Verified on the phone: a Wi-Fi-only app does not load on mobile data.
