# Quiet mode and smart lock

Why music stops when a Private Space locks, and how Bubble locks the bubble itself so it does not.

## The problem

With Android's Private Space auto-lock set to *every time the phone locks* (`private_space_auto_lock=0`), turning the screen off puts the profile in quiet mode and Android stops the whole user (`stopUserWithDelayedLocking`, `killForegroundAppsForUser`). Music in the bubble stops. `UserManagerService` has no per-app exception.

## The pieces

- **Locking from inside.** `MODIFY_QUIET_MODE` is `signature|privileged|development|role`, so `shell` can grant it to Bubble's copy inside the bubble (`pm grant --user N`). With it, `UserManager.requestQuietModeEnabled(true, own user)` locks the bubble. Unlocking still needs the user's credential: turning quiet mode off without it requires `MANAGE_USERS`.
- **Knowing what plays.** Media sessions of a user can only be read by an approved notification listener in that same user; from user 0 the bubble's sessions are invisible (`MediaSessionStack` filters by user). Bubble's copy declares an empty listener (`BubbleMediaWatcher`) approved with `cmd notification allow_listener … N`; the approval alone is enough to call `MediaSessionManager.getActiveSessions`.
- **Something always running.** The listener is not always rebound after a lock, so the logic (`SmartLock`) runs inside the bubble's always-on VPN, which Android starts on every unlock.

## Behaviour

- Screen off, nothing playing → lock now.
- Screen off, something playing → wait; lock 5 s after it stops (pause, end or error).
- Android's own auto-lock is set to *only after restart* (`2`), so Android no longer stops the bubble on screen off.

The engine's `bubble-smart-lock <id> <bubble-pkg>` grants, approves and only then changes Android's auto-lock; if any step fails it leaves the auto-lock as it was. Every Bubble session re-applies it, so a change made in Settings is undone.

## Evidence

- Emulator, nothing playing, screen off: `requestQuietModeEnabled called by package org.bubble.app` within about 1 s, three lock/unlock cycles in a row. With a stream playing in the background it kept playing with the screen off; when the stream failed (HTTP 403 → `ERROR` state) the bubble locked 5 s later.
- Phone: screen off with nothing playing → locked by `org.bubble.app`. Music apps kept playing with the screen locked.

## Limit

While something plays, the whole bubble stays unlocked, not only that app. It remains hidden exactly as when locked.
