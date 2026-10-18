# Launcher and widgets

What an app can and cannot do with the Pixel Launcher (Launcher3) and home-screen widgets, as found while building Bubble Apps and its widget.

## Folders: not possible

No app can create a launcher folder. The Pixel Launcher's database provider requires `ACCESS_LAUNCHER_DATA`, a system permission (`shell` gets "Permission Denial"), and its `TestInfo` provider is disabled. Bubble uses a widget instead.

## Icon size: in the launcher's dump

There is no API for the launcher's icon size, but Launcher3 prints it:

```
dumpsys activity <home activity> → iconSizePx
```

173 px with a 4×6 grid and 150 px with 5×6 on the Pixel 9a (1080×2424, 420 dpi). The engine reads it (`launcher-icons`) once per app process, so the widget follows grid changes. The widget draws icons at 93% of that size: without labels underneath, full-size icons looked larger than the launcher's.

## What a widget can show

- Only the classic `RemoteViews` widget views are available. The list in the phone's `framework.jar` has vertical scrolling collections (`GridView`, `ListView`, `StackView`) but **no horizontal scrolling**.
- Remote Compose (`RemoteComposePlayer`) exists on Android 17 and allows it, but needs an alpha AndroidX library, and Bubble uses no third-party libraries.
- A widget cannot blur what is behind it.
- Row height has to come from the widget's real size: `OPTION_APPWIDGET_MAX_HEIGHT` minus margins and header, divided by the number of rows; columns from `OPTION_APPWIDGET_MIN_WIDTH`. That is how exactly two rows fill the widget.
- `requiresFadingEdge` fades the edge that has more content, the only scroll hint that kept the design clean.

## Icons of work-profile apps

`LauncherApps` gives Meta space apps with `getUserBadgedIcon` (briefcase badge). Bubble app icons come from the bridge or the last list read, since the launcher APIs do not expose hidden profiles.

## Light and dark

Resources in `drawable/` and `drawable-night/` let Android pick a light or dark version of Bubble's logo, adaptive launcher icon and widget background. The launcher caches app icons, so a theme change may not appear immediately there.

Pinned app shortcuts are different: `AppsGridActivity.pin()` passes an image, not a reference to a theme-aware resource. A bubble app's shortcut uses the app's own icon drawn edge to edge on the 108dp adaptive canvas (`Ui.adaptiveBitmap()` → `Icon.createWithAdaptiveBitmap()`), so the launcher masks it to its own shape instead of framing it. A Meta app's shortcut is still a plain bitmap with the work badge (`Icon.createWithBitmap()`). No current code regenerates those images on theme changes; waiting for the launcher cache alone does not fix that. Updating them would require republishing their icons with `ShortcutManager.updateShortcuts()`.

The Pixel launcher badges every pinned shortcut (`IconCache.getShortcutIcon` → `getShortcutInfoBadgeItem`): the badge is the launcher's own rendering of the icon of `ShortcutInfo.getActivity()`, drawn in a circle, after its icon style (dark or themed) is applied. `extra_shortcut_badge_override_package` in the shortcut's extras can name another package, but only when `InstallSessionHelper.isTrustedPackage` holds for the shortcut's package: the launcher itself or a `FLAG_SYSTEM` app (read from the launcher's dex on the Pixel 9a, 18/10/2026). No ordinary app can remove or restyle the badge. Bubble's launcher icon keeps an opaque background: `AdaptiveIconDrawable` paints black under a transparent one (tried on 18/10/2026: the icon and the badge turned black even in light mode). Bubble therefore places apps as one-cell widgets (`AppIconWidget`), which get no badge: the real icon, Bubble's own mark as a resource (so the launcher resolves its light or dark version) and the name.

## Wallpaper & style and Google Photos

Read from the Pixel's wallpaper app (`com.google.android.apps.wallpaper`, `CategoriesFragment.startPhotoPicker` and `CustomizationPickerFragment`) and confirmed in logcat, 18/10/2026: *Choose a photo* sends `ACTION_PICK` `image/*` with `setPackage("com.google.android.apps.photos")`. Only when that throws `ActivityNotFoundException` does it retry without a package. Android shows no chooser for a package-bound intent, so no other app can be offered there.

Google Photos' picker (`picker.external.ExternalPickerActivity`) lists the normal phone's MediaStore (`external_primary`) and the account's cloud library; its only cross-profile code is `CrossProfileApps`, for connected work-profile apps. The bubble's storage is another user's, encrypted with another key, so its photos cannot be indexed there without copying them.

The shell cannot disable a single component of another app (`Shell cannot change component state … to 2`), only whole packages. So Bubble does not change either app: an accessibility service (`PhotosAssistService`, limited to window changes of those two packages, no window content) puts a **Bubble photos** button over Google Photos' picker when the wallpaper app opened it. The button closes the picker and opens `BubblePhotoPickerActivity`, which unlocks the bubble in place and hands the photo to `WallpaperManager.getCropAndSetWallpaperIntent`.
