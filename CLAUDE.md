# OpenGIFWidget

Android app: home screen widgets that play pictures, GIFs and videos, in all widget sizes.
Open source, no ads, being prepared for public release.
The name is always written **OpenGIFWidget** (exact capitalization). Package id: `com.octorice.opengifwidget`.

## Tech stack
- Kotlin, Gradle Kotlin DSL (`*.gradle.kts`, versions in `gradle/libs.versions.toml`)
- minSdk 26, target the latest stable SDK
- App screens: Jetpack Compose (Material 3, dynamic color)
- Widget: classic `AppWidgetProvider` + `RemoteViews`. **Do NOT use Glance.**
- Only approved library beyond AndroidX/Compose: `pl.droidsonroids.gif:android-gif-drawable` (GifDecoder, GifDrawable)

## Test device
- OnePlus 15R (OxygenOS). Keep OxygenOS quirks in mind (battery optimization, launcher widget behavior).
- No root, ever. Every solution must work on a stock, unrooted phone.

## How the widget works
RemoteViews can't play GIFs directly, so:
1. On pick, the file is copied to `cache/import-<id>.gif`, decoded once into **master frames**
   (`FrameExtractor.extractMasterFromGif`: uncropped, max 768 px, WebP q90, max 150 frames, all frames kept),
   then the copy is deleted. Originals are never kept.
2. On Save, **widget frames** are derived from the master frames (`deriveWidgetFrames`: Fit or Fill crop,
   max 512 px, WebP q80, total-pixel budget for launcher memory). Re-crop / Fit-Fill only redo this step.
3. Frame sets live in `files/frames/<widgetId>/<version>/frame_000.webp`; `WidgetStore` keeps `frames_*` (widget)
   and `master_*` (master) for each widget. Unused version folders are deleted after each save / editor close.
4. `FrameProvider` (read-only ContentProvider, numeric paths only) serves widget frames to the launcher via `setImageViewUri`.
5. A `ViewFlipper` (autoStart) shows the frames. `Playback.plan()` applies speed + FPS cap at draw time
   (evenly picked subset of frames, loop duration unchanged), so no re-decoding is needed for speed/FPS changes.
- Always `removeAllViews` on the flipper before adding frames: launchers re-apply updates on top of the existing widget.
- Fill mode crops are baked into the widget frames (RemoteViews can't move an image); the crop center is stored per widget.
- In-app previews (`ui/FramePlayer.kt`) play frame files one at a time (two bitmaps reused), following the same `Playback` plan.
- Still pictures (non-GIF images, or 1-frame GIFs) = a frame set with 1 frame: the widget adds a single ImageView to
  `widget_still` instead of using the flipper. `StillImageDecoder`: ImageDecoder on API 28+ (applies EXIF itself),
  BitmapFactory + built-in `android.media.ExifInterface` on API 26-27 (AndroidX Exif would be a new library).
- Background color/opacity per widget (`WidgetBackground`): an ImageView (`widget_bg`) with a rounded shape, tinted via
  `setColorFilter` and faded via `setImageAlpha` (both RemoteViews-safe on API 26+). The root keeps a transparent rounded
  shape so Android 12+ still clips corners. Changing only the background doesn't remake frames.
- Apps can't remove widgets from the home screen (only the launcher/host can). Long-press on a card in the app =
  "Remove from list": deletes its data and sets `hidden_<id>` (cleared when the widget is saved again). Launchers
  sometimes keep "ghost" ids from cancelled setups (OnePlus: adding from the home screen widget menu, then cancelling
  the editor). So the editor sets `hidden_<id>` itself when it closes without saving and the widget has no picture.
  Launchers usually clean ghosts up on restart (then onDeleted runs).
- New widgets default to Fill.
- Videos (`gif/VideoSource`, `config/TrimDialog`): read in place with MediaMetadataRetriever (never copied, 1 GB limit),
  trim dialog (start + length, max 5 s), frames taken at the FPS cap -> master frames like GIFs. Android 9+: batch decoding
  with `getFramesAtIndex` (batch size ~48 MB of full-size frames) + parallel WebP saving (~1.8 s for 150 frames); fallback:
  frame-by-time (`OPTION_CLOSEST`, slow) with 2-4 parallel readers. Rotation: compare frame shape to the upright shape and
  rotate only if sideways (works whether or not the platform already rotated). Never request frames larger than the video.
  Cancel = cancelling the coroutine (`ensureActive` per frame/batch); `friendlyErrors` deletes partial frames.
- Speed per widget: `speed_<id>` (0.25-3x, steps of 0.05). FPS cap: `AppSettings` (`settings` prefs). Both are applied
  at draw time by `Playback.plan()`; changing the FPS cap calls `GifWidgetProvider.updateAll()`. Speed/name/background
  changes never remake frames.
- Settings screen: `settings/SettingsActivity` (not exported), opened from the gear on the main screen. About uses
  `BuildConfig.VERSION_NAME` (buildConfig enabled).
- App icon: adaptive icon from the user's artwork (`mipmap-*/ic_launcher_{foreground,background,monochrome}.png`),
  art scaled to fit the 66 dp safe circle; monochrome = tile silhouettes with the white line art cut out.
- Widget names: `title_<id>` in WidgetStore (max 40 chars); if never set, the file name without extension
  (`WidgetStore.displayName`). Shown on the app's cards; the launcher title stays invisible (Android has no per-widget title).
- Errors: everything in import/derive goes through `friendlyErrors {}` -> `ImportException(stringRes)`; the UI shows
  `friendlyMessage()`. Half-written frame folders are deleted on failure. Never let a file crash the app.

## Decisions for v1 (see git history for what's implemented)
- Frames: lossy WebP (~80 quality) with transparency, max 512 px longest side; still images: 1080 px, WebP ~90.
- "Master frames": one uncropped frame set per widget is the source of truth; Fit/Fill/crop/speed/FPS are derived
  from it without re-decoding the original. Original files are not kept.
- FPS cap (Settings): 10 / 15 / 24 / 30, default 30. Never above 30. Speed per widget: 0.25x-3x.
  target = avgDelay / speed. If target < 1000/fpsCap: keep floor(loop / minInterval) frames spread evenly
  (not "every Nth", which halved the fps of 30 ms GIFs), interval = loop / kept, so loop duration stays exact.
- Videos: MediaMetadataRetriever only (no FFmpeg, no audio), extracted at the FPS cap, max 5 s (150 frames).
- Input size limits per file: 50 MB (50,000,000 bytes, as Android displays sizes) for images/GIFs, 1 GB for videos.
- Fully offline: no INTERNET permission, no analytics, no tracking. Media only via photo picker / SAF, no storage permissions.
- All user-facing text in `strings.xml`.

## Rules
- **Don't add libraries/dependencies without asking first.**
- The user is learning: after each change, explain what changed and why, in simple terms.
- Work one milestone/step at a time. After each one: make sure it builds, explain exactly how to test it on the phone, then stop and wait.
- Git has a GitHub remote (`master` branch, account Octoricht). **Never commit or push unless the user says so.**
