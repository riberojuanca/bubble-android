# Privacy model

What Bubble protects, what it cannot, and why. Every claim here comes from the phone itself (Pixel 9a, Android 17); the evidence is in [reverse engineering](reverse-engineering/README.md).

## The goal

**Google does not see what you use in the bubble.** Not "Google does not know the bubble exists": that would need control of the operating system (root or a custom OS such as GrapheneOS), which Bubble deliberately does not require.

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
| Usage-statistics dump | The `GET_USAGE_STATS` app-op is set to `ignore` for every app that holds `DUMP` + `PACKAGE_USAGE_STATS` |
| The bubble's apps talking to the internet | Deny-by-default firewall per app, separately for Wi-Fi and mobile data |
| The bubble's data at rest | A locked bubble is stopped and its storage stays encrypted; Bubble locks it with the screen unless something plays |

## What remains (the honest ceiling)

- **Existence**: Google can know there is a private profile, as on millions of phones that use Private Space.
- **The list of apps**: with `INTERACT_ACROSS_USERS`, Google can list which packages are installed in the bubble (verified with a probe app holding the same permissions). Not when they are used.
- **One exception to the usage-statistics restriction**: `com.google.android.apps.restore` accepts the app-op command but stays at `default`, so it could still read the dump.
- **Apps you copy from the normal phone**: the Play Store knows you downloaded them. It does not know they are in the bubble.
- **Your own traffic**: an app you allow online talks to its servers like anywhere else; your network provider sees destinations. Use a VPN on the normal phone or encrypted DNS if that matters.
- **What you do inside an app**: Meta sees what you do in Instagram, in the bubble or anywhere. Bubble isolates apps from your phone and from Google, not from their own servers.

None of these can be removed without root: the permissions are signature/privileged, the user enumeration lives in `system_server`, and SELinux (enforcing) forbids an app from hooking another process.

## The Meta space is a different trade-off

The Meta space has Google Play Services installed but disabled, so that Facebook and Instagram can run. If Google were enabled there (Android lets anyone tap *Enable* in Settings), it would see that profile's apps and their use, never the bubble. A work profile is not hidden: the phone's Google knows it exists and which apps it holds. See [Meta space](reverse-engineering/instagram-firebase-crash.md) and [decision 0006](decisions/0006-meta-space-with-google-off.md).

## Bubble's own power

Bubble borrows the `shell` identity and holds `WRITE_SECURE_SETTINGS`, `INTERACT_ACROSS_USERS` and `MODIFY_QUIET_MODE`. That is a lot of power in one app. It is granted only by an explicit user action (pairing), Bubble's adb key lives in the hardware keystore, and the code is open for review. See [SECURITY.md](../SECURITY.md).
