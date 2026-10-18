# 12. Troubleshooting

Start with **Settings → Technical log**: it shows the last thing the engine did and why it failed.

## Setup and connection

**"Could not connect to wireless debugging. Check that Wi-Fi is on."**
Wireless debugging only works on Wi-Fi. Connect to Wi-Fi and try again. On a new network, Android may ask *"Allow wireless debugging on this network?"*: tick **Always allow** and accept.

**"This setup operation needs Wi-Fi."**
Creating spaces, installing and the maintenance use wireless debugging. Opening apps and changing their internet access do not.

**The pairing code is rejected.**
Open *Pair device with pairing code* again (the code changes each time), keep the dialog open, tap **Pair** in Bubble and type the new code in Bubble's notification.

**Bubble paired once but now cannot connect.**
If you revoked debugging authorizations or reset the phone, use **Settings → Pair again**.

## The bubble

**"The bubble is still locked."**
You cancelled the unlock, or Android did not show it. Try again; Bubble asks Android once more if the unlock screen does not appear within a few seconds.

**"Could not reach the profile. Unlock it and try again."**
The bubble (or the Meta space) is locked or paused, or its copy of Bubble is missing. Unlock it and retry. If it persists, open Bubble with Wi-Fi so it can repair the profile.

**"Open Bubble with its setup connection once to finish configuring this profile."**
The profile's firewall was never set up. Open Bubble with Wi-Fi.

**The Private section is missing from the app drawer.**
It only shows when the bubble has at least one app. Bubble reinstalls F-Droid at its next maintenance.

**Bubble apps disappear from the recent apps.**
Fixed in current versions. If you created the bubble with an older version, apps opened before the update may stay hidden until they are closed and opened again.

## Apps

**An app crashes when it starts inside the bubble.**
- Facebook, Instagram (and other apps with an old Firebase): use the [Meta space](08-meta-space.md).
- An app that shares or "opens with": current versions include Android's share sheet in the bubble; open Bubble with Wi-Fi once so it is added to older bubbles.

**An app has no internet.**
Check its Wi-Fi and mobile data icons in Bubble → Apps, and which network the phone is using. If every app of the bubble lost internet and you see *"Apps in this space have no internet"*, open Bubble so the firewall gets its list again.

**An app asks for my fingerprint but cannot use it.**
Give the bubble its own lock ([chapter 6](06-locking.md)).

**A banking app opens but will not log in.**
It most likely requires Play Integrity, which cannot pass without Google services. It cannot work in the bubble.

**An app does not remember my login.**
Some apps store logins with Google services, which the bubble does not have.

## Meta space

**Facebook or Instagram show "Enable Google Play services".**
Cancel it. The apps work without it.

**Google got enabled in the Meta space.**
Bubble shows a warning. Open Bubble: the guard turns it off within a minute.

## Music and locking

**Music stops when the screen turns off.**
Check that the app reports that it is playing (its media notification is visible). If you changed the Private space auto-lock in Android's Settings, Bubble sets it back at its next maintenance.

**The bubble does not lock when the screen turns off.**
Something inside is playing. Stop it and the bubble locks a few seconds later.

## Reporting a problem

Open an issue with: your phone and Android version, what you did, what you expected, what happened, and the **technical log** (remove anything personal first). For security problems, see [SECURITY.md](../../SECURITY.md).
