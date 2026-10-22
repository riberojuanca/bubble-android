# 8. The Meta space

## Why it exists

Facebook and Instagram close by themselves as soon as they start inside the bubble. The cause is an old version of Google's Firebase library inside both apps: when Google is present on the phone but missing from the profile, it takes a broken path and crashes. It is a bug in those apps, not something Bubble can fix from outside. (Technical details in [reverse engineering](../reverse-engineering/instagram-firebase-crash.md).)

The Meta space is a **separate work profile** where they work: Google Play Services is **installed but turned off** there. The apps see it, take a path that fails cleanly, and keep working without Google notifications. Google never runs.

## What you gain and what you accept

**You gain**
- Facebook and Instagram work, isolated from your contacts, photos, files and other apps of the normal phone.
- The bubble stays free of Google.

**You accept**
- Android lets anyone turn Google back on from Settings (the **Enable** button of Google Play services in the Work profile). Bubble cannot block that button. If it happens, the guard turns Google off again, but meanwhile Google could see the apps of this space, never the bubble.
- The phone's Google knows the Meta space exists and which apps it has. A work profile is not hidden like the bubble.
- No notifications through Google for these apps (Instagram has its own channel for some). The "Enable Google Play services" notice they post is removed by Bubble as soon as it appears.

## Create it

**Bubble → Meta space → Create Meta space**. Bubble first shows a screen explaining all of the above; tap **Create**. It takes about a minute and needs Wi-Fi.

Then **Add apps from the phone** and choose Facebook, Instagram or others. They get internet on Wi-Fi and mobile data at once; the keyboard has none.

## Use it

Its apps are in the **Work** tab of the app drawer, with a briefcase badge. They are also in [Bubble Apps and the widget](07-bubble-apps-and-widget.md).

The Work tab has a **Pause work apps** switch: it stops the whole Meta space (no background activity, no notifications) until you resume it. The bubble and your normal phone are not affected.

**Meta space files** (Bubble → Meta space) opens Android's file browser on the Meta space's own storage. From the Meta space, the bubble's photos and files are in Android's file picker under **Bubble**, and the other way round ([chapter 3](03-home-and-bubble.md#photos-and-files-between-spaces)).

## Things to know

- **The Work tab name and the briefcase** come from Android; they cannot be changed.
- **Facebook and Instagram stay installed on your normal phone** too: the copies in the Meta space get their updates from there. You can leave the outside copies unused. If you uninstall them outside, the Meta copies keep working but stop getting updates.
- **"Enable Google Play services" prompts**: Facebook or Instagram may ask you to enable Google. Ignore or cancel it; the apps work without it. Bubble turns off the Meta space's own Settings so these apps cannot open that screen by themselves.
- **Location**: the apps have no location permission in the Meta space. They can still estimate where you are from your internet connection and your account.

## Delete it

**Bubble → Meta space → Delete the Meta space**. Its apps and data are lost; the bubble and the normal phone do not change.
