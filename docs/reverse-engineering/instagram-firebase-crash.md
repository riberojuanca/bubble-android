# The Instagram/Facebook crash

Why Facebook and Instagram crash inside a Google-free profile, and the work profile ("Meta space") that lets them run.

Analysed versions: Instagram `451.0.0.53.80` (targetSdk 36), Google Play Services `26.40.32` on the phone, Android 17 `user` build. Static analysis of the APK and the system pulled from the phone (JADX and `dexdump`; decisive points checked in the DEX disassembly, since JADX mis-reconstructs some blocks), plus read-only queries. Obfuscated names below are the original DEX names.

## Symptom

In the bubble, Facebook crashes in a loop on start; on the phone it also wakes Instagram, which crashes with it. Stack: `firebase-iid-executor` → `PendingIntent.getBroadcast` → `PendingIntent.checkPendingIntent`.

## The chain inside the app (old bundled Firebase)

1. `X.07bd.A04()` reads the version of `com.google.android.gms` with `getPackageInfo`; not installed in the profile → 0.
2. `X.07QH.run()` picks the **old transport** when the version is below `12000000`.
3. The old transport, `X.07cd.A00()`, calls `X.07bd.A03()`.
4. `A03()` returns 0 if `checkPermission("com.google.android.c2dm.permission.SEND", "com.google.android.gms")` is denied. If it is granted, it looks for a `com.google.iid.TOKEN_REQUEST` receiver and, **even if there is none**, logs `Failed to resolve IID implementation package, falling back` and returns **2**.
5. With 0, it throws `IOException("MISSING_INSTANCEID_SERVICE")`, which is caught: clean failure.
6. With 2, it calls `PendingIntent.getBroadcast(context, 0, intent, 0)`: flags zero.
7. The framework rejects zero flags (`PENDING_INTENT_EXPLICIT_MUTABILITY_REQUIRED`, change id `160794467`) with `IllegalArgumentException`. The handler around it catches only `IOException`. The process dies.

This happens before anything is sent, so firewall, DNS or connectivity cannot change it.

## Why the permission is "granted" in a profile without Google

```
ApplicationPackageManager.checkPermission
  → PermissionManager.checkPackageNamePermission
  → IPermissionManager.checkPermission
  → PermissionService.checkPermission
  → FilteredSnapshot.getPackageState
  → ComputerEngine.getPackageStateFiltered
```

`PermissionService.checkPermission()` does not check `isInstalled()` for that user. `getPackageStateFiltered()` uses `shouldFilterApplication(…, false)`, which does not require installation, and `AppsFilterBase` lets everyone see packages in `mForceQueryable`; `dumpsys package queries` lists `com.google.android.gms` there. So the permission query answers "granted" while `getPackageInfo` says "not installed".

Read-only check (transaction 30 and argument order verified against this build's `IPermissionManager.Stub`):

```sh
adb shell service call permissionmgr 30 \
  s16 com.google.android.gms s16 com.google.android.c2dm.permission.SEND s16 default:0 i32 10
# 00000000 00000000  → no exception, granted
# with a non-existent package: 00000000 ffffffff → denied
adb shell cmd package query-receivers --user 10 --brief -a com.google.iid.TOKEN_REQUEST -p com.google.android.gms
# No receivers found
```

## What does not fix it

| Option | Why not |
|---|---|
| Firewall, DNS | The crash happens before any network call |
| Disable or remove GMS in the profile | It is already absent; the permission path does not look at installed/enabled state |
| Revoke SEND in the profile | `signature|privileged` without the development bit: not modifiable (`setRuntimePermissionGranted` rejects it) |
| Hide GMS from package visibility | It is in the global `forceQueryable` set |
| A substitute receiver in another package | The lookup is limited to `com.google.android.gms`, and the crash is before it matters |

## What does: GMS installed but disabled

If `com.google.android.gms` is **installed but disabled** in the profile, `getPackageInfo` returns its real version, Firebase takes the new transport (`MessengerIpcClient`), cannot bind, and fails cleanly: `Token retrieval failed: SERVICE_NOT_AVAILABLE`, retrying every 30 s, no crash. Instagram has its own notification channel (`:fbns`) and does not need Google's.

| Test | Result |
|---|---|
| Facebook, profile without GMS (emulator) | Crashes, as on the phone |
| Facebook, GMS installed and disabled (emulator) | No crash; two cold starts; no GMS process in the profile |
| `pm enable` GMS + one guard pass (emulator) | `meta-guard: disabled com.google.android.gms` |
| Instagram logged in (phone) | Registration fails cleanly; no crashes |

That breaks the bubble's rule (Google absent), so it lives in a **separate work profile**, never in the bubble. See [decision 0006](../decisions/0006-meta-space-with-google-off.md).

## How the Meta space is created

1. Minimal profile (the same keep list as the bubble) of type `profile.MANAGED`, created stopped.
2. While still stopped: `pm install-existing` then `pm disable-user` of `com.google.android.gms`, so it never runs.
3. Started and watched for stability, like the bubble.
4. Apps copied from the normal phone (`meta-add`); its own firewall (`meta-firewall`) and a keyboard without internet.

## The "Enable" button

Android's Settings (the profile's, and the *Work* tab in the main phone's) show *Enable* for the disabled GMS, and it works.

- `pm set-user-restriction` from `shell`: `SecurityException`, needs `MANAGE_USERS`.
- Bubble as profile owner (`dpm set-profile-owner`) with `DISALLOW_APPS_CONTROL`: the restriction was in effect per `dumpsys user`, yet Settings enabled GMS anyway. That code was removed.

Mitigation: the guard disables GMS in the Meta space on its next pass.

## The Settings screen Instagram opens

Seeing GMS disabled, Facebook and Instagram open its *App info* page on every start, to ask the user to enable it (`START u11 {act=android.settings.APPLICATION_DETAILS_SETTINGS …} from … com.instagram.android`). The engine disables the Meta space's own Settings (`pm disable-user --user N com.android.settings`, command `meta-settings-off`): the request has nowhere to go, Instagram does not crash and stays in the foreground. The main phone's Settings are not affected.

## Other notes

- Copies in the Meta space update from the copies on the normal phone, which therefore stay installed. Disabling the outer copy hides its icon, but every update re-enables it; `pm hide` is forbidden to `shell`; `pm suspend` greys the icon.
- The *Work* tab name and briefcase badge are set by the launcher for every work profile.
- Instagram can infer an approximate location from the IP address and the account even without the location permission.
