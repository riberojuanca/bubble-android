# 1. Before you start

## What Bubble is

Bubble gives you a second space on your phone, the **bubble**, for apps you do not want near Google or near the rest of your phone. Inside the bubble:

- There are **no Google services**: no Google Play Services, no Play Store, no Google account. Apps that use Google's tracking libraries through Play Services get nothing from them.
- Apps have **their own data**, separate from the same app on your normal phone.
- Apps have **no internet until you allow it**, separately for Wi-Fi and mobile data.
- The bubble is **hidden**: other apps on your phone cannot list its apps.

The bubble is Android's own *Private Space* feature, built in a stricter way. Apps run natively, as fast as anywhere else; nothing is emulated.

## What you need

- A phone with **Android 17** (tested on a Pixel 9a). Private Space exists since Android 15, but Bubble only creates the bubble on Android 17 for now.
- **Wi-Fi** for the setup and for some maintenance tasks. Daily use works without Wi-Fi.
- To turn on **Developer options** and **Wireless debugging** once. Bubble explains how.
- About **two minutes**.

You do **not** need root, an unlocked bootloader, a custom ROM or a computer.

## What to expect

**Works well**
- Messaging and open-source apps (Signal, NewPipe, F-Droid apps, Bitwarden, DuckDuckGo, Immich, Spotify…).
- Music in the background with the screen off (see [chapter 6](06-locking.md)).
- Facebook and Instagram, in the separate Meta space.

**Works with limits**
- **Notifications that come through Google** do not arrive inside the bubble. Apps with their own connection (Signal, for example) notify normally while the bubble is open.
- **A locked bubble is stopped**: its apps do not run and do not notify until you open it again (unless something is playing).
- Some apps cannot **remember your login** because they store it with Google services.

**Does not work**
- Apps that require Google's **Play Integrity** check: many banking apps, Google Wallet, some games. They usually open but refuse to log in.
- Apps that are themselves part of Google services (Gmail with your account, Google Drive…). There is no point in isolating them.

## What Bubble changes on your phone

- Creates the bubble (a Private Space) and, if you ask, the Meta space (a work profile).
- Turns on wireless debugging only while it works, and leaves it as it found it.
- Silences around 66 system events that would reveal your use of the bubble (they stop being written for the normal phone too; they are diagnostics only).
- Denies the "usage statistics dump" to the Google apps that could read it.
- Sets the Private Space auto-lock to "only after restart" and locks the bubble itself instead (see [chapter 6](06-locking.md)).
- Only if you use the phone's firewall: blocks the network of the apps you choose on the normal phone.

All of it is undone by **Settings → Remove everything** ([chapter 11](11-removing.md)).
