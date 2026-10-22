# Privacy model

What Bubble protects, what it cannot, and why. Existing device findings are recorded in [reverse engineering](reverse-engineering/README.md). New notification controls are source-reviewed and require device validation; do not treat implementation as proof of isolation.

## The goal

**Goal, not a verified guarantee: prevent Google services from observing activity inside the bubble.** An open Private Space can publish notifications to authorized services in its parent user. This contradicts the former unconditional claim. Notification hardening is implemented but not yet validated on Android 17; the operating system, SystemUI, shell and Bubble remain trusted. See [notification access](reverse-engineering/notification-access.md).

## Who is above the bubble

Android's main user and its system Google services (`com.google.android.gms`, Play Store…) sit above every profile. They hold strong permissions that cannot be revoked without root:

| Permission held by Google Play Services | What it allows over the bubble |
|---|---|
| `MANAGE_USERS` | Knowing that a private profile **exists** (id, type, creation date) |
| `INTERACT_ACROSS_USERS` (not `_FULL`) | **Listing the packages** installed in the bubble |
| `READ_LOGS` | Reading the system **events** buffer without asking (other buffers need user consent since Android 13) |
| `DUMP` + `PACKAGE_USAGE_STATS` | Reading the usage-statistics dump, which includes the bubble's app openings |
| no `ACCESS_HIDDEN_PROFILES_FULL` | Hidden profiles do **not** appear through the launcher APIs |
| no `INTERACT_ACROSS_USERS_FULL` | Usage statistics of the bubble cannot be **queried** directly |

## What Bubble closes

| Channel | How Bubble closes it |
|---|---|
| Google services inside the bubble (accounts, advertising ID, Firebase, Play Integrity) | Never installed: the bubble is created with them excluded; the guard disables any that appears |
| System events that name the bubble's user, uids or packages | Around 66 event tags set to silent (`log.tag.*` and the persistent `persist.log.tag.*`): they are never written, so nobody can read them later |
| Usage-statistics dump | The `GET_USAGE_STATS` app-op is set to `ignore` for every app that holds `DUMP` + `PACKAGE_USAGE_STATS`. `com.google.android.apps.restore` accepts the op but ignores it, so it is disabled in user 0 instead (re-enabled by *Remove everything*) |
| The bubble's apps talking to the internet | Deny-by-default firewall per app, separately for Wi-Fi and mobile data |
| Host notification access (pending device validation) | Revoke external listeners and the assistant; restrict ACCESS_NOTIFICATIONS for discovered permission holders. Read back settings and app-ops; failures are errors, not success |
| The bubble's data at rest | A locked bubble is stopped and its storage stays encrypted; Bubble locks it with the screen unless something plays |

## What remains (the honest ceiling)

- **Existence**: Google can know there is a private profile, as on millions of phones that use Private Space.
- **The list of apps**: with `INTERACT_ACROSS_USERS`, Google can list which packages are installed in the bubble (verified with a probe app holding the same permissions). Notification events and other privileged channels may also reveal activity; complete isolation has not been demonstrated.
- **Google code inside the system identity**: the Pixel ships some Google components (face enrollment, for example) inside Android's own system UID 1000. They run as the OS itself, so no setting can stop them from reading notifications. Bubble lists them in the technical log as system exemptions instead of claiming a block.
- **The usage-statistics restriction**: on the Pixel 9a tested (2026-10-18) every holder of `DUMP` + `PACKAGE_USAGE_STATS` takes the `GET_USAGE_STATS` app-op set to `ignore`. `com.google.android.apps.restore` did **not** hold those permissions on this build, so it is not a reader here; the earlier "exception" was not reproduced. As a safeguard, if it ever appears as a holder that accepts the op but keeps `default`, the engine disables it in user 0 and *Remove everything* re-enables it.
- **Apps you copy from the normal phone**: the Play Store knows you downloaded them. It does not know they are in the bubble.
- **Your own traffic**: an app you allow online talks to its servers like anywhere else; your network provider sees destinations. Use a VPN on the normal phone or encrypted DNS if that matters.
- **What you do inside an app**: Meta sees what you do in Instagram, in the bubble or anywhere. Bubble isolates apps from your phone and from Google, not from their own servers.

None of these can be removed without root: the permissions are signature/privileged, the user enumeration lives in `system_server`, and SELinux (enforcing) forbids an app from hooking another process.

## The Meta space is a different trade-off

The Meta space has Google Play Services installed but disabled, so that Facebook and Instagram can run. If Google were enabled there (Android lets anyone tap *Enable* in Settings), it would see that profile's apps and their use, never the bubble. A work profile is not hidden: the phone's Google knows it exists and which apps it holds. See [Meta space](reverse-engineering/instagram-firebase-crash.md) and [decision 0006](decisions/0006-meta-space-with-google-off.md).

## The channel between spaces

The bubble and the Meta space can read each other's photos and files, and the phone can read both, through Bubble ([cross-profile access](reverse-engineering/cross-profile.md#the-channel-between-spaces)). Only the file a user picks in Android's picker is read, read-only, and nothing is copied. For that, Bubble's copy in each space holds all-files access to that space's own storage. Once a Meta app has a picked file, it can keep it. The phone's files are never offered to the spaces, so the phone's Google never gets a way in through Bubble.

## Bubble's own power

Bubble borrows the `shell` identity and holds `WRITE_SECURE_SETTINGS`, `INTERACT_ACROSS_USERS` and `MODIFY_QUIET_MODE`. That is a lot of power in one app. It is granted only by an explicit user action (pairing), Bubble's adb key lives in the hardware keystore, and the code is open for review. See [SECURITY.md](../SECURITY.md).
