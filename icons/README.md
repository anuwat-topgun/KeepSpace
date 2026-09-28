# KeepSpace App Icons

This package contains app icon exports created from the selected KeepSpace icon.

## Included

### iOS
- `ios/AppIcon.appiconset/`
- Ready to drop into Xcode asset catalog
- Includes `Contents.json`
- Includes all commonly required iPhone, iPad, and App Store sizes

### Android
- `android/res/mipmap-*/ic_launcher.png`
- `android/res/mipmap-*/ic_launcher_round.png`
- `android/play-store/ic_launcher-playstore.png`

### Web
- `web/icon-192.png`
- `web/icon-512.png`

## Using them in the apps

Run `python3 icons/build_icons.py` (Pillow only). It builds every size from `source/KeepSpace_icon_source_1254.png`
and writes straight into the projects:

- iOS: `ios/SmartStorage/Resources/Assets.xcassets/AppIcon.appiconset` (all 18 sizes, opaque, `Contents.json` from `ios/`)
- Android: `android/app/src/main/res/mipmap-*/` — `ic_launcher`, `ic_launcher_round` (48–192 px) and adaptive layers
  `ic_launcher_foreground`/`ic_launcher_background` (108–432 px) with `mipmap-anydpi-v26/*.xml`
- Google Play: `icons/build/ic_launcher-playstore.png` (512 px, full-bleed, upload in Play Console)

**Why not just copy the exported files:** the source is a rounded square on an opaque black background, so the
exports had black corners. iOS applies its own corner mask (its radius is smaller than the artwork's, so black
slivers showed), and Android launchers showed opaque black corners, or shrank the legacy PNG into a pale circle.
The script fills the black background with the artwork's own colours (full-bleed square for iOS / Play), gives the
Android legacy icons real transparency, and builds a proper adaptive icon (artwork foreground + the same colours
extended as the background) so circle, squircle and square launcher shapes all fill cleanly.

The files in this folder are left exactly as delivered.

## Notes
- Source icon used: `source/KeepSpace_icon_source_1254.png`
- The delivered package has no Android adaptive layers; `build_icons.py` derives them from the same artwork (see above).
- Not done: Android 13 themed (monochrome) icon and iOS 18 dark/tinted variants — both need designed alternates.
