# Android developer verification and the bubble

Google's developer verification requirement and how it affects installing apps in the bubble. Researched on 2026-10-16; rules and rollout dates may change, so check the sources.

## What Google announced

- From **2026-09-30**, on certified devices, apps installed from participating sources must be registered by a verified developer. First in Brazil, Indonesia, Singapore and Thailand; **global in 2027** ([Android Help](https://support.google.com/android/answer/17065026?hl=en), [developer.android.com](https://developer.android.com/developer-verification)).
- It is enforced by a Google system service, **Android Developer Verifier** (`com.google.android.verifier`).
- **adb is exempt**: "Apps installed using ADB won't require verification" ([official FAQ](https://developer.android.com/developer-verification/guides/faq)).
- An **advanced flow** allows installing apps from unverified developers: enabled in Developer options, with a confirmation, a reboot and a one-time 24-hour wait ([9to5Google](https://9to5google.com/2026/03/19/android-advanced-flow-sideloading/)).
- It does not apply to AOSP, uncertified devices or regions without Google Mobile Services.

## How it works inside (community analysis of AOSP)

Source: [agnostic-apollo's analysis](https://gist.github.com/agnostic-apollo/b8d8daa24cbdd216687a6bef53d417a6); not independently checked against the source.

- `PackageInstallerSession` → `DeveloperVerifierController` → the verifier service.
- Per-user policy: `0` none (never blocks), `1` block if verification fails, `2` currently the same as 1, `3` block also when verification could not run (for example, verifier unavailable).
- adb sessions (`INSTALL_FROM_ADB`) skip the verifier unless `pm install --force-verification` is used.

## Measured on the phone

- `pm get-developer-verification-policy`: **`0`** in user 0 and in the bubble.
- The service provider is `com.google.android.verifier/…PlatformVerificationService` in both users.
- The verifier is installed in user 0 but **not in the bubble**: the minimal profile excludes it.

## What it means for Bubble

- **Adding apps keeps working**: copying from the phone (`install-existing`) creates no install session, and APKs go through `cmd package install` as `shell`, the exempt adb path.
- **F-Droid inside the bubble is exposed**: its installs use the normal installer. With policy 1 or 2, an unverified app would be blocked. With policy 3, since the verifier is not in the bubble, everything F-Droid installs might be blocked.
- **Ways out if that happens**: install the APKs through Bubble (exempt), or Google's advanced flow. Without root the policy cannot be changed: `shell` can read it but not set it.
- **The verifier must stay out of the bubble**: it contacts Google for every install (package and signature).

To check your phone: `adb shell pm get-developer-verification-policy --user <id>`.
