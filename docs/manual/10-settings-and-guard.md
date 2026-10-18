# 10. Settings, the guard and the technical log

## Settings

| Option | What it does |
|---|---|
| **Pair again** | Repeats the pairing ([chapter 2](02-install-and-set-up.md)). Only needed if Bubble cannot connect anymore, for example after revoking debugging authorizations |
| **Technical log** | Shows the last output of the engine. Useful when something fails; you can select and copy it |
| **Check for updates** | Checks GitHub for a newer version and lets you download and install it |
| **Remove everything** | Leaves the phone as it was before Bubble ([chapter 11](11-removing.md)) |
| **About** | Bubble's version |

Bubble also checks automatically when you open it, at most once every six hours. If a newer release is available, tap **Update** to download it and confirm the installation in Android. The first time, Android may ask you to allow installations from Bubble; after enabling that permission, return to Bubble to continue. Updates keep your data and pairing. Bubble verifies the download checksum, package version and signing certificate before installation. There is no need to uninstall the old version.

## The guard

The guard is a small process that runs with the engine's identity. Every minute it:

- turns off any Google service that appears inside the bubble or turns on in the Meta space;
- re-applies the phone's firewall, if you use it;
- re-sends the firewall list to a profile that was locked when it changed.

Tap the **spy icon** at the top of Bubble's screens to see how it is running:

- **Watching all the time**: USB debugging is on, so the guard can stay alive.
- **Checks every time Bubble opens, does something or the phone starts**: the usual case. With USB and wireless debugging off the guard cannot stay alive, but then nobody else can put Google services in the bubble either: doing that also needs the `shell` identity.
- **Not running**: open Bubble to start it again.

## What Bubble does in the background

When you open Bubble (once per time Android starts Bubble), and only if there is Wi-Fi, Bubble runs a short maintenance in the background, while the screen already shows the state:

- silences the system events that reveal the use of the bubble (this setting also survives reboots);
- starts the guard;
- re-applies the bubble's firewall, reinstalls F-Droid or the keyboard if they were removed;
- reads your launcher's icon size for the widget.

During it, wireless debugging turns on for a few seconds and Android may show its *Wireless debugging connected* notification; Bubble hides it while it works. Without Wi-Fi this maintenance waits for the next time; opening apps and changing their internet access never need it.

## Wireless debugging and Developer options

Leave Developer options on. Bubble turns wireless debugging on only while it works and restores it as it was. USB debugging is your choice: on, the guard stays alive all the time; off, it checks at each Bubble session.
