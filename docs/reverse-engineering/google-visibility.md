# What Google can see

**Scope correction:** the probes below cover specific APIs, not every way to observe the profile.
Open-profile notifications expose an additional path; see [notification access](notification-access.md).
The usage-dump restriction is not complete while any reader remains authorized. Shared UIDs and
the system-UID exemptions in DumpUtils also require validation.

The permissions of the phone's Google services, and what each one actually allows over the bubble, tested rather than assumed.

## Separate processes, separate SELinux domains

`ps -eZ`:

```
u:r:system_server:s0                 system    system_server
u:r:gmscore_app:s0:c512,c768         u0_a188   com.google.android.gms
u:r:systemui_app:s0:c12,...          u0_a268   com.android.systemui
u:r:untrusted_app:...                          ← where Bubble runs
getenforce = Enforcing
```

Google asks `system_server` and `logd` for data over Binder. Bubble is not in that path, and SELinux forbids an app from injecting into another domain. A "mirror" that feeds Google false data cannot be built without root.

## Google's permissions (`dumpsys package com.google.android.gms`)

Granted: `INTERACT_ACROSS_USERS` (the weak one), `MANAGE_USERS`, `READ_LOGS`, `QUERY_ALL_PACKAGES`, `DUMP`, `PACKAGE_USAGE_STATS`, `REAL_GET_TASKS`, `GET_APP_OPS_STATS`.
Not granted: `ACCESS_HIDDEN_PROFILES_FULL`, `INTERACT_ACROSS_USERS_FULL`. The same is true for `com.android.vending`, `com.google.android.gsf` and `com.google.android.googlequicksearchbox`.

These are signature/privileged permissions. `pm revoke` only changes runtime (dangerous) permissions, so none can be removed without root.

## The probe

A small app (`UsageProbe`) installed on the emulator and given, with `pm grant`, the same relevant permissions Google holds on the phone: `INTERACT_ACROSS_USERS`, `DUMP`, `PACKAGE_USAGE_STATS`. Results against the bubble (user 10):

| Call | Result |
|---|---|
| `createContextAsUser(user 10)` | Fails: "Own package not found for user 10" (the probe, like Google, is not installed there) |
| `IUsageStatsManager.queryUsageStats(…, userId = 10)` | **Denied**: needs `INTERACT_ACROSS_USERS_FULL` |
| `PackageManager.getInstalledPackagesAsUser(0, 10)` | **Allowed**: the full list of the bubble's apps |
| `usagestats` dump (`IBinder.dump`) | **Allowed**, and it includes a `user=10` section with every app open and close, with timestamps |

So, before Bubble's countermeasure, Google could know which apps are in the bubble **and when they are used**. Whether it actually reads this was not checked. Because of the package list, renaming apps was rejected: they can be recognised by their APK.

## Closing the usage dump

The dump also requires the `GET_USAGE_STATS` app-op (`DumpUtils.checkDumpAndUsageStatsPermission`). `shell` can change app-ops. Bubble sets it to `ignore` for every app that holds `DUMP` + `PACKAGE_USAGE_STATS`. With the probe: `Permission Denial: … android:get_usage_stats app-op not allowed`, and it survives reboots.

On the phone that is 10 apps: `com.google.android.gms`, `com.android.vending`, `com.google.android.odad`, `com.google.android.pixelsystemservice`, `com.google.android.apps.restore`, `com.google.android.apps.carrier.log`, `com.google.android.apps.internal.betterbug`, `com.google.android.as`, `com.google.android.googlequicksearchbox`, `com.google.android.aicore`.

- **Earlier "exception" not reproduced:** a prior note said `com.google.android.apps.restore` accepts `appops set` (exit 0) but stays at `default`. On the Pixel 9a tested on 2026-10-18 that package did **not** hold `DUMP` + `PACKAGE_USAGE_STATS`, so it is not a usage-dump reader on this build, and every real holder took the op in `ignore`. As a safeguard the engine still handles a reader that accepts the op but keeps `default`: it disables only `com.google.android.apps.restore` in user 0 (`pm disable-user --user 0`), and `undo-tweaks` re-enables it. Any other reader that resists the op stays a reported failure, never disabled blind. The `disable-user`/`enable` mechanism itself was verified reversible on the Android 17 emulator.
- **Cost:** those apps also lose the usage statistics of the main phone.
- The engine now repeats the full search during maintenance, records failed targets as well, and reports an incomplete restriction as an error. The previous successful-only daily cache could conceal known failures.
- Rejected: `cmd usagestats delete-package-data <pkg> --user 10` returns 0 but the dump still shows the events (it does not clear what is in memory).

## Conclusion

| Google can | Google cannot |
|---|---|
| Know a private profile exists (`MANAGE_USERS`) | Query the bubble's usage statistics |
| List the packages in it (`INTERACT_ACROSS_USERS`) | Read the usage dump (app-op set to `ignore`, except `apps.restore`) |
| Read the events log buffer (`READ_LOGS`) | Read events Bubble keeps from being written ([system logs](system-logs.md)) |
| | See it through the launcher APIs (hidden profile) |
| | Read its data (encrypted while locked; separate user) |
| | Run inside it (excluded at creation) |
