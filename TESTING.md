# OpenGIFWidget testing checklist

Run through this before each release. Test files (pictures, GIFs, videos, damaged files) can be
dropped onto an emulator window to copy them into its Download folder.

## Emulators (Android Studio > Device Manager > + > Create Virtual Device)

| # | Hardware | System image | Main focus |
|---|---|---|---|
| 1 | Pixel 2 (small, 5") | API 26, Google APIs | Oldest supported Android: still pictures via BitmapFactory + EXIF, slow video fallback, pre-12 widgets |
| 2 | Medium Phone | API 30, Google APIs | Pre-Android 12 widgets (no rounded clipping, old widget picker), photo picker backport |
| 3 | Pixel 8 | API 34, Google Play | Install Nova Launcher from Play and test widgets there |
| 4 | Medium Phone | API 37 (latest) | Edge-to-edge, predictive back, themed icons |
| 5 | Pixel Tablet | API 37 | Tablet layout, landscape, 3+ card columns |
| 6 | Pixel 9 Pro Fold | API 37 | Fold/unfold with the app and the editor open |

Real devices that can't be emulated: Samsung (One UI), Xiaomi (MIUI/HyperOS), OnePlus (OxygenOS).

## On every emulator

**Widgets**
- [ ] Widget list shows OpenGIFWidget with its preview picture
- [ ] Add a widget: the editor opens; Cancel = no widget and no card in the app
- [ ] Add a GIF, a picture, and a video widget; all three play / show correctly
- [ ] Resize each widget from small (1x1) to very large: no stretching; Fit and Fill both correct
- [ ] Fill: drag the crop in the editor, Save: the widget shows that part
- [ ] Background color + opacity, Transparent with a transparent GIF/PNG
- [ ] Speed 0.25x and 3x; FPS cap 10 vs 30 in Settings changes all widgets right away
- [ ] Reboot the emulator: every widget comes back and animates
- [ ] Remove a widget from the home screen: its card disappears and storage drops

**Files**
- [ ] Gallery button and Files button both work
- [ ] Photo rotated with EXIF shows upright; portrait video shows upright
- [ ] Damaged file: friendly message, no crash. 60 MB picture: "limit is 50 MB" message
- [ ] Video trim: preview plays the clip; dragging Start/Length shows first/last frame; Cancel during "Taking frames"

**Screens**
- [ ] Rotate to landscape on every screen (main, editor, trim dialog, Settings): nothing cut off, scrolls when needed
- [ ] Dark and light theme (system setting): everything readable
- [ ] Largest font size (Settings > Display > Font size): buttons and progress text not cut off
- [ ] Right-to-left: Developer options > "Force RTL layout direction": layout mirrors, back arrow points right
- [ ] Edge-to-edge: nothing hidden under the status bar, navigation bar or camera cutout

## Extra per device
- **API 26**: add a JPEG photo taken in portrait (EXIF rotation via the old decoder); add a video (slow path, progress + Cancel)
- **API 30**: widget corners (rounded by our own background, not clipped by the system)
- **API 34 + Nova Launcher**: add, resize, reconfigure (long-press > widget options), remove
- **Tablet**: main screen shows 3-4 columns; editor content stays centered and not stretched
- **Foldable**: open the editor folded, unfold mid-edit: the preview and crop keep working
- **Low RAM**: create an AVD with 1 GB RAM (Device Manager > edit > Advanced > RAM): big GIFs still add without crashing the launcher

## Battery killers (real phones)
- Samsung / Xiaomi / Huawei / OnePlus: leave widgets for a day with battery saver on, then reboot: they should come back.
  If not, follow Settings > Reliability in the app and retest.
