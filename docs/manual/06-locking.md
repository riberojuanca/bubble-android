# 6. Locking, fingerprint and music

## When the bubble locks

A locked bubble is stopped: Android closes all its apps and keeps its storage encrypted. Bubble locks it:

- **When the screen turns off**, if nothing inside is playing.
- **As soon as playing stops** (pause, end, error), if the screen is still off, after a few seconds so a pause between songs does not lock it.
- **When you lock it yourself** with the lock button of the Private section.

Opening it again always asks for your screen lock (or the bubble's own lock, see below). Bubble can lock the bubble but never unlock it by itself.

## Notifications: keep the bubble open

A locked bubble cannot receive anything: Signal and other messengers only get their messages when you unlock it. To be notified as usual, choose **Bubble → Settings → Lock the bubble → By hand**. The bubble then stays open when the screen turns off; the phone's Google services still cannot read its notifications. Lock it yourself with the padlock on Bubble's home; a restart locks it too.

## Music with the screen off: the smart lock

Android's own Private Space auto-lock stops the whole bubble when the screen locks, with no exception for music. Bubble replaces it: it sets Android's auto-lock to *Only after device restarts* and locks the bubble itself, except while one of its apps is playing. So you can listen to NewPipe or Spotify in the bubble with the phone locked; when you stop the music, the bubble locks.

While something plays, the whole bubble stays open behind your lock screen, not only that app. Its apps can still publish notifications and access allowed networks. This does not provide the same protection as stopping the profile.

Do not change the auto-lock in Android's Settings: Bubble manages it. If you set it back to *Every time device locks*, music will stop with the screen again; Bubble sets it back the next time it does its maintenance.

## Fingerprint inside the bubble

By default the bubble opens with your phone's screen lock. In that mode **apps inside the bubble cannot use your fingerprint**: Android does not give the bubble any fingerprints of its own. A banking app that offers "log in with fingerprint" sends you to Settings, and enrolling from there fails.

The fix is to give the bubble **its own lock**. Android makes you choose its PIN on screen, so Bubble cannot do it for you, but it is a one-time step:

1. **Open the bubble** (unlock the Private section). The change cannot be made while it is locked.
2. Go to **Settings → Security & privacy → Private space** (Bubble → Bubble → **Open security settings** gets you to Security & privacy).
3. Tap **Private space lock** and turn off **Use device screen lock**. Choose **Choose new lock** and confirm your phone's PIN.
4. Choose **PIN** and type a PIN for the bubble (it can differ from your phone's).
5. Back in Private space lock, tap **Fingerprint Unlock → Add fingerprint** and enroll a finger. You can use the same finger as for the phone.

From then on you open the bubble with that PIN or fingerprint, and apps inside can use the fingerprint. The smart lock works the same.

## Notifications

- **Open bubble**: apps with their own connection (Signal, for example) notify normally. Apps that rely on Google's push service do not.
- **Locked bubble**: no app runs, so nothing notifies until you open it.
- **Playing**: the bubble stays open, so its apps can notify meanwhile.
