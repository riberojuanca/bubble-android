# 4. Apps in the bubble

Open **Bubble → Apps**. The list shows the apps inside the bubble with three icons each: **Wi-Fi**, **mobile data** ([chapter 5](05-internet.md)) and **remove** (the bin). Use the search box to find one; pull down to read the list again (for example after installing something with F-Droid).

## Adding apps

There are three ways, and you can mix them.

### Copy an app you already have on the phone

**Add apps from the phone** lists the apps you installed on your normal phone. Tick the ones you want and tap **Add**.

- The copy inside **starts with no data**: you log in again, and nothing from the outside copy is shared.
- It is the **same installed app**: while it stays installed outside, the Play Store keeps it updated, and the copy inside gets each update.
- You can **uninstall it outside** afterwards; the copy inside stays and keeps working, but stops getting updates. To update it, install it outside again (from Play or an APK) and uninstall it outside once more.
- Downloading the app from Play tells Google you have that app; Google does not see that it is in the bubble, nor what you do with it.

### Install an APK file

**Install an APK file** offers two sources:

- **From the bubble's Downloads**: an APK you downloaded with a browser inside the bubble. If the bubble is locked, Android asks for your fingerprint or PIN first.
- **From the phone's files**: an APK on your normal phone. It is installed only inside the bubble.

Files and photos downloaded by apps inside the bubble belong to its separate storage. They do not automatically appear in the normal phone's Downloads or photo gallery. Bubble keeps Android's document picker inside the profile so apps can choose files there; the APK selector above only lists APKs, not photos or other downloads. To use a bubble file in the Meta space or on the normal phone, pick it from **Bubble** in that space's file picker ([chapter 3](03-home-and-bubble.md#photos-and-files-between-spaces)).

### Use F-Droid inside the bubble

F-Droid is the open-source app store, already installed in the bubble. Open it from the Private section and install apps as usual. They appear in Bubble → Apps after pulling down to refresh. F-Droid is the only app with internet by default, so it can download.

## New apps have no internet

Every app you add starts **without internet**. Turn it on with the Wi-Fi and mobile data icons in Bubble → Apps ([chapter 5](05-internet.md)). Apps you add to the Meta space are different: they get internet at once ([chapter 8](08-meta-space.md)).

## Removing apps

Tap the **bin** next to the app and confirm. The app and its data are deleted **from the bubble only**; the copy on your normal phone, if any, is not touched.

## Keyboards

Keyboards are per space, so the bubble has its own. Bubble enables your phone's **Gboard** inside, **without internet** (it cannot send what you type anywhere). If your phone has no Gboard, Bubble installs **HeliBoard** from F-Droid. If you enable another keyboard inside the bubble, Bubble respects it.

## Google apps

Google's *apps* (YouTube, Maps…) are allowed in the bubble like any other app; what the bubble excludes are Google's *services* (Play Services, the Play Store and their plumbing). Bubble refuses to add those, and the guard turns them off if they ever appear.

## Apps that need Google

- **Without push notifications through Google** most apps work normally.
- **Apps that check Play Integrity** usually open but refuse to log in. They cannot work in the bubble.
- **Facebook and Instagram** crash when they start in the bubble. Use the [Meta space](08-meta-space.md).

The [app compatibility list](../app-compatibility.md) records what has been tested.
