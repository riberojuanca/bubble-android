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

The launcher badges every shortcut with the icon of the app that pinned it. Bubble's launcher icon therefore has no background layer, so the badge shows only the bubbles (light or dark version) without a tile.
