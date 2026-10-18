# 0006. Meta space: a separate work profile with Google installed but off

- Date: 2026-10-17
- Status: Accepted

## Decision

Apps that crash without Google Play Services (Facebook, Instagram) do not go into the bubble. They go into a **separate work profile, the Meta space**, which Bubble creates only on request, after showing what it implies. There, Play Services is **installed and disabled** before the profile ever starts, Bubble's firewall applies (only added apps are online; the keyboard is not), the profile's own Settings are disabled, and the guard keeps Play Services off.

The bubble stays free of Google services; [0004](0004-no-microg-no-own-store.md) does not change for it.

## Reasons

- The crash comes from the old Firebase bundled in those apps: with Google absent but visible to the system they take a broken path; with Google installed and disabled they fail cleanly. See [the Instagram/Facebook crash](../reverse-engineering/instagram-firebase-crash.md).
- Putting it in the bubble would be irreversible without root (`shell` cannot uninstall Play Services from a profile) and, if it were ever enabled, Google would see the whole bubble. In a separate profile the worst case is that it sees only the Meta apps, and deleting the profile undoes everything.
- Compared with leaving the apps on the normal phone, the profile isolates them from its contacts, photos and other apps.

## Accepted limits

- The *Enable* button for Play Services in Android's Settings cannot be blocked (tested: neither a user restriction via `shell` nor Bubble as profile owner); the guard turns it off afterwards.
- A work profile is not hidden like a Private Space: it shows as a *Work* tab, and the phone's Google knows it exists and which apps it holds.
- No push notifications through Google for those apps (Instagram uses its own channel).
