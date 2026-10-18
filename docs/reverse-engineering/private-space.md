# Private Space

Why the bubble is a Private Space, how Bubble creates one without Google services, and what a minimal profile needs to boot.

## User types on the phone

`dumpsys user`, user-types section:

```
profile.PRIVATE    PROFILE  mEnabled=true  mMaxAllowed=4  mMaxAllowedPerParent=1  mProfileParentRequired=true
full.SECONDARY     FULL     mEnabled=true  mMaxAllowed=3
profile.MANAGED    PROFILE  mEnabled=true  mMaxAllowedPerParent=1
profile.CLONE      PROFILE  mEnabled=true
```

`PRIVATE` is enabled, one per parent, and independent of a work profile (`MANAGED`). Bubble uses one of each: the bubble and the optional Meta space.

## What makes it hidden

Default properties of the `PRIVATE` type:

```
mProfileApiVisibility=1                     ← hidden
mShowInLauncher / mShowInSettings           ← hideable
mAuthAlwaysRequiredToDisableQuietMode=true  ← opening always asks for authentication
mCredentialShareableWithParent=true         ← can unlock with the phone's lock
mItemsRestrictedOnHomeScreen=true
mMediaSharedWithParent=false
```

`mProfileApiVisibility=1` is the key. Two checks in `services.jar` enforce it:

- `LauncherAppsService$LauncherAppsImpl.canAccessHiddenProfile`: access if the caller holds `ACCESS_HIDDEN_PROFILES_FULL`; otherwise denied if access for non-system apps is forbidden; otherwise it requires `ACCESS_HIDDEN_PROFILES` **and** the `HOME` role (being the launcher).
- `BroadcastHelper.canLauncherAccessProfile`: for a hidden profile it requires `ACCESS_HIDDEN_PROFILES_FULL`.

The phone's Google services hold neither, so the launcher APIs do not show them the bubble. Other paths remain; see [what Google can see](google-visibility.md).

When a Private Space is **locked** (quiet mode), Android stops all its processes and its credential-encrypted storage is locked.

## Creating one from `shell`

There is no restriction in the way: `dumpsys user` shows empty effective restrictions, so `no_add_private_profile` is not set.

```
pm create-user --profileOf 0 --user-type android.os.usertype.profile.PRIVATE Bubble
```

works with `shell`, which holds `CREATE_USERS`. The profile is hidden and stays stopped until `am start-user`.

**But it is born full of system apps.** On the Pixel, 186 packages, including `com.google.android.gms`, `com.google.android.gsf` and `com.android.vending`. The list comes from `preinstalled-packages-merged-*.xml` on read-only partitions.

## Removing system apps afterwards does not work

- `pm uninstall --user N <system package>` needs root on Android 17: `Failure [only root can delete system app for a particular user]`.
- `pm disable-user --user N` works but is reversible by anyone who can tap *Enable* in Settings.
- `pm install-existing --user N` brings a system package back, and after that it can only be disabled again.

## Excluding them at creation

`IUserManager.createProfileForUserWithThrow` takes a fifth argument, `disallowedPackages` (binder transaction 5 on this build; needs only `CREATE_USERS`). `PackageManagerService.createNewUserLI` installs every system package **except** those in the list. Excluded packages are never installed: their uninstall reason is `0`, so an OTA does not reinstall them either (it only reinstalls reason `1`). `pm create-user` passes `null`, which is why it installs everything.

Bubble calls it with `service call user 5`.

## The boot loop

The first attempt on the phone excluded 150 packages, including `com.android.systemui`. The phone entered a loop ("Can't load Android system") and had to be factory reset.

Reproduced on the emulator: it is not the profile that fails but **user 0's SystemUI**. When any profile appears, `wm.shell` calls `createContextAsUser` for it; if SystemUI is not installed there, it throws `IllegalStateException: Own package not found for user N: package=com.android.systemui`. SystemUI is persistent, restarts and crashes again; on a `user` build, RescueParty escalates to the recovery prompt. It happens **as soon as the profile is created**, without starting it.

Having `adb` authorized does not help: `pm remove-user` needs a working `system_server`. Since then, every new exclusion is tested on the emulator first.

## The minimal profile

Method: exclusion list = standard profile − keep list. Start from almost nothing and add only what each failure asks for. Pass: no crash of `system_server`, user 0's SystemUI or any process after create, start, reboot, start. Controls: standard profile passes; the boot-loop list fails with the same exception.

| Package | Why it is required (evidence) |
|---|---|
| `android` | The framework |
| `com.android.systemui` | Without it user 0's SystemUI crashes (the boot loop) |
| `com.android.providers.settings` | Every app crashes: `Failed to find provider settings for user N` |
| `com.google.android.permissioncontroller` | No permission dialogs: `No PermissionController package … for user N` |
| `com.android.server.telecom` | `Own package not found for user N: package=com.android.server.telecom` |
| `com.google.android.providers.media.module` | Storage stays `UNMOUNTED` (no `/sdcard`) |
| `com.android.providers.userdictionary` | Keyboards crash: `Failed to find provider user_dictionary` |
| `com.google.android.webview` | Android installs it even when excluded |
| `com.android.settings` | Without it **the bubble cannot be unlocked** (found on the phone): unlocking resolves `CONFIRM_DEVICE_CREDENTIAL_WITH_USER` inside the profile; without Settings only `systemui/.keyguard.WorkLockActivity` answers, which finishes itself when the device is not locked |

On the phone the keep list also includes the **enabled overlays** whose target is one of those packages (resources only): 43 entries in [`tools/private-space-keep.txt`](../../tools/private-space-keep.txt). The engine computes it on the phone at creation time: the required packages, Settings and WebView (AOSP and Google names), plus `[x]` overlays from `cmd overlay list --user 0` targeting them. It excludes everything else from `pm list packages -s -u --user 0`, a superset of the standard profile; excluding something that would not have been installed has no effect.

Later additions, each tested on the emulator:

- **Package installer** (`com.google.android.packageinstaller` or the AOSP one), so F-Droid can install apps inside.
- **Share sheet** (`com.android.intentresolver`), without which apps that share or "open with" crash.

## The "Private" section of the app drawer

The Pixel Launcher (`AlphabeticalAppsList.updateAdapterItems`) shows the section only if the profile has at least one app. A normal Private Space ships `com.android.privatespace` ("Install apps"); the minimal one does not, so Bubble installs F-Droid.

## Keyboards

Input methods are per user: `ime list -s --user 10` was empty, Gboard existed only in user 0. Bubble installs a keyboard in the bubble and enables it with `ime enable/set --user N`, with no internet access.

## Unlocking with fingerprint

With the shared lock, the profile has no fingerprints of its own, so apps inside that use `BiometricPrompt` cannot use biometrics. Enrolling a fingerprint from inside the bubble fails (`BiometricEnrollActivity` does not resolve in user 10). Giving the Private Space its own lock (Settings → Security & privacy → Private space → Private space lock) creates a credential for user 10 and lets fingerprints be enrolled for it.
