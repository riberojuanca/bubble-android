# Notification access and the isolation boundary

Status: initial Android 17 emulator checks completed on 2026-10-18; Pixel validation remains pending.
Only `emulator-5554` was changed, with a pre-test snapshot. The original Pixel findings were
supplied by the maintainer. Source review used AOSP `android16-release`; runtime checks used
`google/sdk_gphone16k_x86_64/emu64xa16k:17/CP31.260623.012/16064790:userdebug/dev-keys`.

## Evidence and impact

The Pixel investigation found that `ManagedServices.ManagedServiceInfo.enabledAndUserMatches`
can deliver a private profile's notifications to listeners in its parent user. Profile membership
and device policy determine delivery; an open Private Space is not an unconditional exclusion.
Reported listeners include AiAi, Play Protect, Android Auto, Pixel Stand and Pixel Launcher.
This establishes an access path, not evidence that any of them uploaded or retained messages.

It affects ordinary unlocked use and the music exception: music keeps the entire profile running.
Locking the phone more often cannot undo notification delivery that already happened.

Official source reviewed:

- [NotificationShellCmd](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/notification/NotificationShellCmd.java):
  `disallow_listener`, `disallow_assistant`, `get_approved_assistant`. The help text says
  `remove_assistant`, but the implementation handles `disallow_assistant`.
- [NotificationManagerService](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/services/core/java/com/android/server/notification/NotificationManagerService.java):
  active notifications, recent notifications and persisted history require `ACCESS_NOTIFICATIONS`
  and check its app-op. Both `allow` and `default` permit the read. The notification dump uses
  `DumpUtils.checkDumpAndUsageStatsPermission`. Assistant changes propagate to enabled profiles.
- [DumpUtils](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android16-release/core/java/com/android/internal/util/DumpUtils.java):
  usage checks exempt root, system, shell and incidentd UIDs. For other UIDs, any associated
  package with an allowed/default usage app-op can satisfy the check. Package-level restrictions
  alone do not prove a shared UID is blocked.

## Implemented controls

`notification-privacy` is also called before starting a profile, during smart-lock setup and by
the existing guard. It:

1. Saves assistant approvals before changes, including child profiles because Android propagates
   the parent change. Uses an atomic recovery journal and an interprocess lock.
2. Removes the parent user's assistant and verifies its absence.
3. Revokes every parent listener except Bubble's exact debugging-notice component and reads back
   the enabled listener setting. This covers third-party listeners, not just Google packages.
4. Discovers installed parent packages with granted `ACCESS_NOTIFICATIONS`, including shared-UID
   grants. Sets the app-op to `ignore` for non-exempt readers and reads it back. A UID override or
   unknown response is a failure. UID 1000 is reported as an OS exemption: a real probe bypassed
   its `ignore` setting. A Google package sharing that exempt UID produces an error.
5. Reports `notification_privacy=restricted` only after those configuration checks pass.

Android, SystemUI, shell and Bubble remain trusted and are excluded from notification query
restrictions. A package prefix is not an exemption. The recovery journal preserves the first
recorded configuration across repeated runs. `undo-tweaks` restores recorded notification
permissions; this can restore previously authorized listeners, so it is part of explicit removal.
It keeps recovery state on errors. The assistant approval is shared between the parent user and
its profiles, so restore puts back only the parent's (user 0) recorded baseline and the profiles
follow it.

The smart-lock setup first restores OS screen locking; failure cannot silently retain an old
music exception. Loss of media-session permission inside SmartLock removes the exception instead
of disabling the screen-off receiver. Failed usage-dump restrictions stay in the next check's
target set and now produce an error.

## Limits: not a complete isolation proof

- Configuration readback does not prove live service unbinding or cancellation of in-flight
  callbacks. Settings and package-dump formats need confirmation against the installed build.
- The guard is periodic and depends on adbd. Re-granting access between checks can leak events;
  restoring screen auto-lock does not immediately stop an already open profile.
- Shared-UID grants are now included without mixing installed, hidden-system and shared-user dump
  sections. The emulator restore package did not hold the grants previously attributed to it; do
  not transfer that earlier finding to the Pixel without rechecking its actual package permissions.
  System-UID query access remains outside the protection boundary.
- Privileged services can have other routes: diagnostics, accessibility/assist, media metadata,
  screen capture, notification delegates and system components. No global denial has been proven.
- Apps allowed online can contact Google directly through embedded SDKs without Play Services.
  Per-app network permission is not a destination filter or a guarantee about the app's servers.
- No software running below the stock OS can establish that the OS itself cannot read information
  it must process. Bubble cannot make that guarantee by revoking ordinary listeners.

Do not enable an always-open notification mode on the strength of these configuration checks.
Do not claim that an open profile is as protected as a stopped one.

## Required validation before release

Build/install and device execution require explicit task authorization. Use synthetic markers,
never personal messages, for probes.

1. On Android 17 emulator: capture listeners, assistants (all affected profiles), package/UID
   permissions and both app-op layers. Test an external listener and a privileged query probe
   against active, removed and persisted private-profile notifications, before and after hardening.
2. Confirm no delivery after revocation, including reconnects, queued callbacks, restart and
   profile unlock. Verify the original app notification still appears in SystemUI.
3. Test UID overrides, shared UIDs, command failures, lost media approval, interrupted journal
   writes, concurrent maintenance and restoration with an unrelated work profile present.
4. Resolve each failed usage reader. Do not accept command exit status alone as evidence.
5. Repeat the relevant checks on the Pixel build, check expected feature losses and recovery.
6. Only then evaluate keeping Bubble active behind the lock screen. Apps still need a transport
   independent of Google (their own connection or supported UnifiedPush); FCM-only apps do not
   gain notifications merely because their profile is running.

Host-only fault-injection checks are in `tools/tests/NotificationPrivacyTest.java`. They validate
transaction logic and failure handling; they do not substitute for Android permission tests.

## Emulator results, 2026-10-18

| Check | Result |
|---|---|
| Parent listener, before hardening | `bubble-test-before` from user 11 recorded by the probe in user 0 |
| Parent listener, after hardening | `bubble-test-after` remained in the notification service; no probe callback recorded; Google listener/assistant bindings removed |
| Listener restoration | Probe received `bubble-test-restored` after restoring access |
| Notification dump under Play Services UID 10224, before | `DUMP_SYNTHETIC_LINES=4 DENIED=false` |
| Same UID and marker, after `quiet-logs` | `DUMP_SYNTHETIC_LINES=0 DENIED=true` |
| After emulator reboot | Listener setting empty, assistant null, GET_USAGE_STATS still ignore, dump still denied |
| SmartLock synthetic music | Screen off + PLAYING kept user 11 running; PAUSED stopped it within the next 8-second observation window |
| Mixed assistant approval restoration | Android re-granted the assistant to user 11 while restoring another profile. Latest code reported failure and retained the recovery journal |
| System UID query | UID 1000 still saw the marker with ACCESS_NOTIFICATIONS=ignore. This control must not be presented as blocking the OS |

The Play Services identity test used the emulator's `su 10224` to run a standalone Binder probe;
it did not inject code into Play Services and is not evidence that Play Services actually reads
notifications. Root/su was used only to emulate the caller in this test, not by the product controls.
The ordinary probe could not obtain ACCESS_NOTIFICATIONS via `pm grant`; it correctly received
SecurityException on direct queries. The privileged read path is the notification dump, already
covered above (under the Play Services UID it went from `DUMP_SYNTHETIC_LINES=4 DENIED=false` to
`DENIED=true` once `ACCESS_NOTIFICATIONS` was set to `ignore`), and that app-op is the gate the
hardening sets. Re-running the standalone `app_process` query probe (`QueryProbe`) under an app
UID aborts in ART in this test bench, so the dump result and the app-op gate are the evidence for
the query path; this is a test-bench limitation, not a gap in the controls. The emulator platform
signature did not match the public AOSP test platform key, so that signing route was abandoned
without changing the system image.

Two runtime findings changed implementation: listener setting mirrors update asynchronously
(bounded polling now verifies them), and assistant approvals must be read through
`get_approved_assistant`, not the stale secure setting. Shell also refused to disable AiAi's
assistant component individually; no such fallback was added.

The notification-assistant approval is shared between the parent user and its profiles:
`allow_assistant`/`disallow_assistant` on any one propagates to the others, so it cannot be set
per profile. Restore therefore puts back only the parent's (user 0) baseline — the one that
governs — and the profiles follow; it no longer tries to restore each profile independently or
fail on a profile that only changed through that propagation. The full enforce → undo-tweaks cycle
now restores user 0's listeners and assistant identically and deletes the journal (verified on the
Android 17 emulator on 2026-10-18). No always-open notification mode was enabled.

Debug APK build, lintDebug and host fault-injection tests passed. Probe sources are in
`tools/probes/notification/`. Test observations used synthetic content only.

## Pixel 9a, 2026-10-18 (configuration surface only, device left unchanged)

Read-only checks on the maintainer's Pixel 9a (users: 0 Owner, 10 Bubble stopped, 11 Meta):

- Six listeners are enabled "for current profiles" in user 0: `dreamliner`, `nexuslauncher`,
  AiAi (`com.google.android.as`), Android Auto (`gearhead`), `odad` and Bubble's own
  `AdbNoticeListener`. The approved assistant is AiAi. No `ACCESS_NOTIFICATIONS` app-op is in
  `ignore` yet: the hardening has not been applied on this device.
- `DataManager` (SYSTEM) is bound live under users 0, 10 and 11, and several listeners run under
  `USER_ALL`. Those are the irreducible OS listeners revocation does not touch.
- The full cut was exercised on the Pixel and reversed: revoking the five Google listeners and
  the AiAi assistant dropped "enabled for current profiles" from six listeners to one (only
  Bubble's own) and the approved assistant to none; `allow_listener`/`allow_assistant` then
  restored the snapshot identically (listeners byte-equal, assistant back to AiAi).
- `com.google.android.apps.restore` does **not** hold `DUMP` + `PACKAGE_USAGE_STATS` on this
  build, so it is not a usage-dump reader here; every real holder already takes the op in `ignore`.

The empirical delivery test was reproduced independently on the Android 17 emulator on 2026-10-18
with a freshly built, debug-signed probe: a synthetic notification posted inside the bubble
(user 11) was recorded by a `ProbeListener` approved in user 0 (`bubble-test-before` → user 11),
and after revoking that listener a second notification (`bubble-test-after`) was not recorded —
leak then clean cut, with the probe uninstalled afterwards. `POST_NOTIFICATIONS` must be granted
in the profile or the notification is silently dropped (Android 13+).

The same empirical delivery test was then run on the Pixel 9a on 2026-10-18 and passed: a
synthetic notification posted inside the bubble (user 10) was recorded by a `ProbeListener`
approved in user 0 (`bubble-test-before` → user 10), and after revoking that listener a second
notification was not recorded (`bubble-test-after` absent). The test used its own probe listener
as the parent's ear, never the phone's real Google listeners, started the bubble as Bubble
normally does, and uninstalled the probe afterwards. Leak and clean cut are now confirmed on
device, not only in configuration.
