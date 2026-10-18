# 0004. No microG, no own app store

- Date: 2026-10-16
- Status: Accepted for the bubble. Apps that crash without Google services go to the Meta space ([0006](0006-meta-space-with-google-off.md)).

## Decision

No replacement for Google Play Services (microG) goes into the bubble. Apps enter the bubble in three ways:

1. **Copied from the normal phone** (`pm install-existing --user N`): the main way.
2. **An APK** installed only in the bubble.
3. **F-Droid** inside the bubble, optional, and app stores installed from it (for example Aurora Store, which downloads Play apps with an anonymous account).

Bubble does not build its own store. Push without Google: UnifiedPush, in apps that support it.

## Reasons

- microG needs to spoof Google's signature, which needs root or a custom OS.
- Most apps work without Google services, losing at most push notifications.
- An own store that fetches APKs from GitHub would reinvent F-Droid and, worse, install unsigned and uncurated APKs.
- Apps that require Play Integrity (many banking apps, some games) stay out on purpose.

## What "no Google" means

The bubble excludes Google's **services**: Play Services, Services Framework, the Play Store and their system plumbing, which follow the user across apps. Google's **apps** (YouTube, Maps) are allowed like any other. The engine refuses to add Google services and counts only those.

## Copying apps from the phone (user decision, 2026-10-16)

- Inside, the app starts with no data and no Google services.
- While it stays installed outside, Play updates it and the copy inside updates with it (same package). Tested on the emulator: v1 added → removed outside (stays inside, v1) → v2 installed outside → inside v2.
- Accepted cost: the Play account records that the user downloaded the app. It does not see that it is in the bubble, or what is done there.
