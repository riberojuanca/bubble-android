# 3. The home screen and the bubble

## Bubble's home screen

At the top there is a status line, for example **Ready · no Google services**, and a small **spy icon** in the corner (the guard, see [chapter 10](10-settings-and-guard.md)). Below, one row per area:

| Row | Opens |
|---|---|
| **Bubble** | The state of the bubble, checking it, its fingerprint lock, deleting it |
| **Apps** | The apps inside the bubble, with their internet access ([chapter 4](04-apps.md), [chapter 5](05-internet.md)) |
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

While the bubble is locked, Bubble shows the last state it read; nothing can be asked inside a locked bubble.

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
