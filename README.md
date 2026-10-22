# Bubble

**A Google-free space for your Android apps, on a stock phone. No root, no custom OS.**

Website: [riberojuanca.github.io/bubble-android](https://riberojuanca.github.io/bubble-android/)

Bubble turns Android's own *Private Space* into a "bubble": a separate, hidden profile where the apps you choose run natively, with their own data, **without Google services inside**, behind a deny-by-default firewall. Your banking and Google apps stay installed on the normal phone. Notification privacy controls affect features such as suggested replies, accessory forwarding and launcher badges.

> **Status: experimental.** Built and tested on a **Pixel 9a with Android 17** (stock, locked bootloader). Other phones and Android versions are untested; some parts are known to depend on Android 17 (see [Compatibility](#compatibility)).

## What Bubble does

- **Creates the bubble**: a minimal Private Space with Google Play Services, the Play Store and their plumbing left out from the first boot.
- **Keeps Google out**: a guard disables any Google service that shows up inside.
- **Hides its use**: the system events that would reveal what you do in the bubble are not written to the system log.
- **Filters the network**: every app inside starts without internet; you allow each one on Wi-Fi, on mobile data, or both.
- **Adds apps your way**: copy apps you already have, install APK files, or use F-Droid inside the bubble.
- **Locks itself smartly**: the bubble closes when the screen turns off, unless one of its apps is playing music.
- **Meta space** (optional): a separate work profile for Facebook and Instagram, which crash without Google; Google is installed there but turned off.
- **Bubble Apps and a widget**: all your bubble and Meta apps in one grid, and on your home screen as real icons (in the launcher's shape, with Bubble's small mark) or a widget. Bubble Apps is not a separate download: installing Bubble adds two icons to the app drawer, **Bubble** to set things up and **Bubble Apps** to open apps and place them on the home screen (also reachable from Bubble's home).
- **Photos and files between spaces**: from the bubble, the Meta space or the phone, open another space's photos and files where they are, in Android's file picker or as an image for any app, and pick a bubble or Meta photo as your wallpaper (also from Wallpaper & style, through an optional button over Google Photos). Nothing is copied: only the file you pick is read, and a locked space asks for your fingerprint right there. The phone's own files are not offered to the spaces.
- **Removes everything** in one step, leaving the phone as it was.

## What Bubble does not do

Bubble cannot make the bubble invisible to Android itself. The phone's Google services can still know that **a private profile exists** and **which apps are installed in it**; while the profile is open its notifications could also reach notification listeners on the phone. Bubble revokes the Google notification listeners and assistant that could read them (verified on the Android 17 emulator and a Pixel 9a), but Android's own system components still process notifications locally (on the Pixel this includes some Google components that run as the system itself), and complete isolation from the OS is not possible without root. Apps that require Google's Play Integrity check (many banking apps, some games) will not work inside. Read the full, honest [privacy model](docs/privacy-model.md).

## How it works, in one paragraph

Creating a profile without Google, filtering its network and managing its apps need system permissions no ordinary app can get. Bubble borrows them from Android's **`shell` identity**, through your phone's own **wireless debugging**, paired once with a code, with no computer. A small engine runs with that identity only while Bubble works. Day-to-day actions (opening apps, changing their network access) go directly from Bubble to its copy inside each profile, without wireless debugging. Details in [Architecture](docs/architecture.md).

## Get started

1. Download the APK from [Releases](https://github.com/riberojuanca/bubble-android/releases) and install it.
2. Open Bubble and follow the two setup steps: **pair** (turn on Developer options and Wireless debugging, type the 6-digit code) and **create the bubble** (about a minute).
3. Open the app drawer and unlock the **Private** section at the bottom: that is your bubble.

Bubble checks for new releases when you open it, at most once every six hours. Tap **Update** when offered, or use **Settings → Check for updates**. Android confirms the installation; your data and pairing are kept.

The [user manual](docs/manual/README.md) explains every screen, every option and what to do when something goes wrong.

## Documentation

- [User manual](docs/manual/README.md): install, daily use, troubleshooting, FAQ ([en español](docs/manual/es/README.md)).
- [Privacy model](docs/privacy-model.md): what Bubble protects and what it cannot.
- [Architecture](docs/architecture.md): the pieces and how they talk.
- [Reverse engineering](docs/reverse-engineering/README.md): what we found inside Android 17 to make this possible, with evidence.
- [Design decisions](docs/decisions/README.md).
- [App compatibility](docs/app-compatibility.md) and [testing](docs/testing.md).
- [Security](SECURITY.md): why Bubble needs each permission, and how to report a problem.
- [Contributing](CONTRIBUTING.md): build from source, conventions.

## Compatibility

| | |
|---|---|
| Tested | Pixel 9a, Android 17 (API 37), stock |
| Requires | Android 15 or later for Private Space; Bubble creates the bubble only on API 37 for now (an internal system call it uses is version-specific) |
| Needs | Developer options and Wireless debugging (Wi-Fi for setup) |
| Not needed | Root, an unlocked bootloader, a computer |

## Support the project

Bubble is free and open source, and will stay that way. If it is useful to you, you can support its development through [GitHub Sponsors](https://github.com/sponsors/riberojuanca) or a donation (see the *Sponsor* button on this repository).

## License

[MIT](LICENSE).
