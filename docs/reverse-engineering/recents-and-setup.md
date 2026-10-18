# Recents and setup state

Bubble and Meta space apps did not appear when swiping between apps: they seemed to close, but they had only vanished from the recent apps.

## Cause

`RecentTasks.getRecentTasksImpl` (phone's `services.jar`) skips every task whose `mUserSetupComplete` is false, with the debug message "Skipping, user setup not complete". Profiles created without the setup wizard keep `user_setup_complete=0`.

## Fix

```
settings put --user N secure user_setup_complete 1
```

The engine sets it in `ensureSystemTools`, for the bubble and the Meta space.

The value is **copied into each task when it is created**, so tasks that existed before the fix stay hidden. They are removed with `am stack remove <task id>` (ids from `am stack list`), or disappear when the app is closed and opened again.

## Evidence

Emulator: a new task was listed; an old one stayed skipped until removed. Applied on the phone to both profiles.
