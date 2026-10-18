# Cross-profile access

How Bubble opens bubble apps from the normal phone (Bubble Apps, the widget, home-screen shortcuts) and talks to its copies inside each profile.

## The public APIs exclude the bubble

- `CrossProfileApps`: `CrossProfileAppsServiceImpl` builds its targets with `getProfileIdsExcludingHidden`, which leaves out the Private Space. It works for the Meta space (a work profile), which also opens through `LauncherApps`.
- Opening like the launcher needs `ACCESS_HIDDEN_PROFILES`, which comes with the launcher (`HOME`) role.

## First approach: the engine

Each launch was an engine session: turn wireless debugging on, connect, `bubble-launch <id> <pkg> <bubble-pkg>`, disconnect. About 2 s on the emulator with the bubble unlocked. It needs Wi-Fi.

### `am start` and targetSdk 37

`am start --user 10` from `shell` fails for at least one app with targetSdk 37: "Activity class … does not exist", `START u0 … result code=-92`. The same command opens apps with targetSdk 35 and 36, and `cmd package resolve-activity --user 10` does find the activity. Root cause not isolated. Fallback: the engine opens `ProfileOpenActivity` of Bubble's copy in the bubble (exported only to holders of `INTERACT_ACROSS_USERS`), which starts the app like any app in the same profile. Checked on the phone: direct start fails → `ProfileOpenActivity` → `START u10 …`.

## Current approach: the bridge

`INTERACT_ACROSS_USERS` carries the `development` bit (like `WRITE_SECURE_SETTINGS`; see [shell identity](shell-identity-and-adb.md)), so `pm grant` can give it to Bubble in user 0 and in each profile. With it, `Context.bindServiceAsUser` reaches Bubble's copy inside the Private Space, which `CrossProfileApps` would not.

- **Transport:** `bindServiceAsUser` + `Messenger`.
- **Calls:** state of a profile (Google services present or enabled), its app list, open an app (the copy builds a `PendingIntent` for the app's launch activity and returns it; the main app sends it), apply network choices (the copy asks the main app for the current list, since the main app owns the user's choices).
- **Protection:** `ProfileControlService` is exported only with a signature permission (`org.bubble.app.permission.PROFILE_CONTROL`) and also checks that the caller has the same appId. The main app answers rule requests only for profiles Bubble manages; a profile accepts commands only from user 0.
- **Firewall start-up:** when a profile's VPN starts and the bridge is available, it blocks everything and asks the main app for the list, so an old file can never override a newer choice. It retries at 0, 3, 10 and 30 s; if no list arrives, it notifies the user and keeps blocking.

The bridge works without Wi-Fi or adb. The engine is still needed to create spaces, install apps and run maintenance; the first status check of each app process starts a maintenance session in the background when Wi-Fi is available.

## Unlocking from a shortcut

When the bubble is locked, opening an app asks Android to unlock it (`requestQuietModeEnabled(false, …)`), which shows the credential prompt. If no prompt has appeared after 3 s, Bubble asks again. If the user cancels, `UserManager.isQuietModeEnabled` is still true on return and the opening screen closes.
