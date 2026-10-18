# App compatibility

What happened with real apps on a Pixel 9a with Android 17, observed in the phone's log while they were used. Results depend on app versions; reports for other apps are welcome.

## Results

| App | Where | Result | Evidence |
|---|---|---|---|
| Signal | Bubble | Works | Opens from Bubble Apps, the widget and shortcuts |
| NewPipe | Bubble | Works, also with the screen off (smart lock) | Media session `PLAYING` with the screen off; Wi-Fi-only rule respected |
| Spotify | Bubble | Works, including login and playback with the screen locked | Crashed before the share sheet was added: `ActivityNotFoundException: No Activity found to handle Intent { act=android.intent.action.CHOOSER }` |
| Bitwarden | Bubble | Works | `am start` from `shell` fails (`result code=-92`, targetSdk 37); opens through Bubble's own copy or the bridge |
| Immich, DuckDuckGo, F-Droid | Bubble | Work | No errors on launch |
| Facebook, Instagram | Meta space | Work, without notifications through Google | Crash in the bubble; see [the Instagram/Facebook crash](reverse-engineering/instagram-firebase-crash.md) |
| Banking app with biometric login | Bubble | Works after giving the bubble its own lock with fingerprints | With the shared lock, the profile has no fingerprints of its own |
| Banking app protected by DexGuard | Bubble | Opens and logs in; does not remember the user | Logs `requires Google Play services, but they are missing` without crashing |
| Payment app requiring Play Integrity | Bubble | Opens; login fails | `PlayCore … IntegrityService : Phonesky is not installed` |

## What to expect

- **Most apps work** without Google services, losing at most push notifications (unless they support UnifiedPush or have their own channel).
- **Apps that require Play Integrity will not work**: there is no Play Store or Play Services to answer the check. This is by design ([decision 0004](decisions/0004-no-microg-no-own-store.md)).
- **"Remember me"** can depend on Google services (Block Store, Credential Manager), so some apps forget the user.
- **Biometrics** inside the bubble need the bubble's own lock ([manual](manual/06-locking.md)).
- **Apps with an old bundled Firebase** may crash on start in a profile without Google; the Meta space is the workaround.
- The minimal profile needs some AOSP pieces that are not Google's: package installer, file picker and share sheet. Bubble adds them; without the share sheet any "share" or "open with" crashes the app.

## Reporting

Open an issue with the app name and version, where you used it (bubble or Meta space), what happened, and the relevant log lines if you can get them.
