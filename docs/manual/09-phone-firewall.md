# 9. The phone's firewall

**Bubble → Firewall** cuts the internet (Wi-Fi and mobile data) of apps on your **normal phone**. It is meant for system apps that phone home and that you do not use (for example Device Setup, DeviceLock Controller or SIM Manager), but works for any app.

## Use it

1. Turn on **Phone firewall**.
2. Tick the apps that must lose their internet. Use the search box; *Your apps* and *System apps* are listed separately.
3. Tap **Save**.

Turning the switch off lifts every rule but remembers your list.

## How it works, and its limits

The phone's firewall does not use a VPN (Play Store can take a VPN down; it cannot touch these rules). It combines three layers, applied by the engine:

- A kernel firewall chain that cuts the app completely. It is lost at every reboot; the guard puts it back.
- Background data blocked on metered networks, with every saved Wi-Fi network marked as metered, so it applies on Wi-Fi too.
- The guard, which re-applies everything every minute while it runs.

Limits:

- **After a reboot**, until Bubble has Wi-Fi and restores the rules, a blocked app can use the network. Background data stays blocked from the start only if you also turn off **Settings → Apps → [app] → Mobile data & Wi-Fi → Background data** once by hand.
- **The rule is per app, across spaces**: blocking an app on the normal phone also blocks the same app inside the bubble.
- **Blocking SIM Manager** prevents downloading or managing eSIMs while it is blocked.
- Separate Wi-Fi and mobile data choices exist only inside the bubble and the Meta space for now, not on the normal phone.
