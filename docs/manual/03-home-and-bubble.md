# 3. The home screen and the bubble

## Bubble's home screen

At the top there is a status line, for example **Ready · no Google services**, and a small **spy icon** in the corner (the guard, see [chapter 10](10-settings-and-guard.md)). Below, one row per area:

| Row | Opens |
|---|---|
| **Bubble** | The state of the bubble, checking it, its fingerprint lock, deleting it |
| **Manage apps** | The apps inside the bubble, with their internet access ([chapter 4](04-apps.md), [chapter 5](05-internet.md)) |
| **Bubble Apps** | Opens the grid to place apps and the widget on the home screen ([chapter 7](07-bubble-apps-and-widget.md)) |
| **Meta space** | The optional space for Facebook and Instagram ([chapter 8](08-meta-space.md)) |
| **Firewall** | The firewall of your normal phone ([chapter 9](09-phone-firewall.md)) |
| **Settings** | Pairing again, the technical log, removing everything, the version |

Pull the screen down to check again. Bubble checks by itself when you open it (at most every couple of minutes) and every ten minutes while it stays open.

### Status messages

| Message | Meaning |
|---|---|
| Ready · no Google services | Everything is as it should be |
| Checking the bubble… | Bubble is asking the bubble for its state |
| There is no bubble yet | Bubble shows the setup assistant |
| Could not check the bubble right now | The bubble did not answer; try again or see [troubleshooting](12-troubleshooting.md) |
| Warning: Google services were put in the bubble (N) | Something installed Google services inside; the guard disabled them. To remove them for good, replace the bubble |
| Warning: Google services active in the bubble: N | Open Bubble again so the guard disables them |
| There is a private space … that is not the bubble yet | A Private Space exists but Bubble does not manage it; open Bubble → Bubble to decide |

While the bubble is locked, Bubble shows the last state it read; nothing can be asked inside a locked bubble. While nothing needs attention, home reads **Protected 🛡 and locked 🔒** (or **and open 🔓**). Tap the shield for the details of the protection; tap the padlock to unlock the bubble with Android's fingerprint or PIN prompt, or to lock it at once. Anything that needs the bubble open (its apps, files, photos or Downloads) also asks for the fingerprint by itself.

## The Bubble screen

- **State and details**: the Private Space user number and how many Google services are installed and active inside (both should be 0).
- **How to enter**: a reminder that the bubble is the *Private* section at the bottom of the app drawer.
- **Check now**: asks the bubble for its state.
- **Fingerprint**: a note on giving the bubble its own lock so its apps can use your fingerprint, with **Open security settings** ([chapter 6](06-locking.md)).
- **Delete and create a new bubble**: replaces the bubble. Its apps and data are lost.
- **Delete the bubble**: removes the bubble without creating another one ([chapter 11](11-removing.md)).

## Entering and leaving the bubble

- **Enter**: app drawer → **Private** (at the bottom) → unlock. Or open any bubble app from **Bubble Apps** or the widget ([chapter 7](07-bubble-apps-and-widget.md)); Android asks you to unlock first.
- **Leave**: use the lock button of the Private section, or just turn the screen off: Bubble locks the bubble by itself when the screen goes off, unless something inside is playing ([chapter 6](06-locking.md)).

When the bubble is locked, Android stops all its apps and keeps its storage encrypted. Its apps do not run, do not use the network and do not notify until you open it again.

The Private section only appears in the app drawer when the bubble has at least one app. Bubble always keeps F-Droid there for this reason.

## Photos and files between spaces

Each space keeps its own storage: photos and downloads saved inside the bubble (with Immich or a browser there) stay in the bubble, and the Meta space has its own. Bubble connects them without copying anything: from each space you can open the other spaces' photos and files where they are, and only the one you pick is read.

- **In Android's file picker**, in any app of any space: menu → **Browse** shows **Bubble** and **Meta space** (on the phone both, in the bubble the Meta space, in the Meta space the bubble). Each one has **Photos** (by album) and **Files** (its whole storage).
- **Apps that ask for an image** can also choose **Bubble photos** among the apps they offer: the other spaces' albums, with thumbnails.
- **Wallpaper from Wallpaper & style**: turn on Bubble → Settings → **Wallpapers with bubble photos**. Then Wallpaper & style → *Choose a photo* still opens Google Photos, with a **Bubble photos** button on top: tap it, open an album and tap a photo. Android's own wallpaper screen opens to crop it and set it on the home screen, the lock screen or both. The Pixel's wallpaper app always asks Google Photos by name, and Google Photos only shows the normal phone's photos, so the button is how the other spaces' photos get there. It uses an accessibility service that only sees which screen of those two apps is open, never what is on it.
- **Wallpaper from Bubble**: Bubble → Bubble (or Meta space) → **Bubble photos**, then the same steps.
- **Bubble files** and **Meta space files** (in Bubble's Bubble and Meta space screens) open Android's file browser inside that space, on its own storage.

If a space is locked or paused, the picker shows a button to open it: Android asks for your fingerprint or PIN when needed, and the list appears. Files are read-only from other spaces, and the phone's own files are not offered to the bubble or the Meta space. Bubble keeps no copy; the app you give a file to may keep its own (Android stores the wallpaper image, for example).

The gallery that some apps build themselves (Instagram's upload screen, for example) reads only its own space's photos. In those apps, use their option to choose a file or open the system picker, if they have one.

