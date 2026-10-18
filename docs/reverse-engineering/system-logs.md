# System logs

What the log daemon exposes about the bubble, and how Bubble keeps those entries from ever being written. Evidence from the phone and from AOSP `platform/system/logging` (branch `main`) where the phone's binary is not readable.

## Who reads

- `READ_LOGS` is granted to `com.google.android.gms` and `com.google.android.feedback`.
- **User consent since Android 13.** `logd` (`LogReader.cpp`) holds back every reader with log credentials and uid ≥ `AID_APP_START` and asks `LogcatManagerService`. On the phone (`services.jar`, `LogcatManagerService.shouldShowConfirmationDialog`): if the reader is in the foreground, SystemUI shows `LogAccessDialogActivity`; otherwise it is **rejected**. There is no exception for privileged apps.
- **Exception:** a reader that asks **only for the events buffer** is accepted without consent (`only_read_event_logs`). That is the real leak.

## What the events buffer says about the bubble

`logcat -b events` showed system events with user id 10 as their first field (`uc_send_user_broadcast`, `wm_create_task`, `cc_connect_state_changed`, `am_user_state_changed`, `um_user_visibility_changed`…) and events naming the bubble's packages or uids (`am_proc_start`, `am_uid_running`, `am_pss`, `notification_cancel_all: [1000,…,org.fdroid.fdroid,10,…]`). They reveal existence and use, not content.

## The lever

- `shell` is in group `log` (1007), and SELinux allows `(allow shell log_tag_prop (property_service (set)))`. `log.tag.*` and `persist.log.tag.*` are `log_tag_prop` (`plat_property_contexts`).
- **Filter when writing** (text logs): `liblog`'s `__android_log_buf_write` calls `__android_log_is_loggable` before sending (disassembled from the phone's `liblog.so`). Event writes (`__android_log_bwrite`) are not filtered in `liblog`.
- **Filter when receiving** (text and events): `logd`'s `SerializedLogBuffer::ShouldLog` applies `__android_log_is_loggable_len`; for events it uses the **event's name** (`tagToName`).
- Checked on the phone: with `setprop log.tag.BubbleProbe S` a message was not recorded; with the property cleared, it was.

So a silenced entry is **never written**, and nobody can read it later. It is not a filter that returns false data (that would need replacing `logd`, so root); it is simply not recorded.

## Applied

The engine (`quiet-logs`) sets 66 event tags to `S`, listed in `BubbleEngine.QUIET_EVENTS`. The list was built by using the bubble on the emulator (lock, unlock, open apps, type) and collecting every event that named user 10, its uids or its packages. After applying it, using the bubble left **no** such event.

- It writes both `log.tag.<event>=S` (effective immediately) and `persist.log.tag.<event>=S` (survives reboots). Checked on the phone: with only the persistent property, an `am_proc_start` was not written; with neither, it was. The events stay silent from boot, before Bubble runs.
- Side effect, accepted: those events are not written for the main phone either. They are system diagnostics.
- **Remove everything** clears both properties.

Note for scripts: `adb shell setprop X ""` drops the empty argument; quote it inside the remote command (`adb shell "setprop X ''"`).
