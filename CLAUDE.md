# OpenGIFWidget

Android app: home screen widgets that play pictures, GIFs and videos, in all widget sizes.
Open source (GPL-3.0, see LICENSE; the only extra library, android-gif-drawable, is MIT and compatible), no ads,
being prepared for a GitHub release.
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
  In the editor the Fill preview is locked (no accidental drags): "Reposition" opens `CropView` (whole picture, widget-shaped
  window, outside dimmed; drag moves the window, limits from `FrameExtractor.cropRect`). Done keeps it, Cancel/Back restores
  it, Save is disabled while repositioning. No warning text about cropping (user's choice).
- In-app previews (`ui/FramePlayer.kt`) play frame files one at a time (two bitmaps reused), following the same `Playback` plan.
- Still pictures (non-GIF images, or 1-frame GIFs) = a frame set with 1 frame: the widget adds a single ImageView to
  `widget_still` instead of using the flipper. `StillImageDecoder`: ImageDecoder on API 28+ (applies EXIF itself),
  BitmapFactory + built-in `android.media.ExifInterface` on API 26-27 (AndroidX Exif would be a new library).
- Background picker (`ui/BackgroundPicker`): Transparent, a rainbow "Custom color" circle, then preset swatches.
  The rainbow opens `ui/ColorPickerDialog` (own Compose code, no library): saturation/brightness square, hue bar,
  hex box. A color that isn't a preset counts as custom (the rainbow shows it in its middle). Opacity stays the slider.
- Background color/opacity per widget (`WidgetBackground`): an ImageView (`widget_bg`) with a rounded shape, tinted via
  `setColorFilter` and faded via `setImageAlpha` (both RemoteViews-safe on API 26+). The root keeps a transparent rounded
  shape so Android 12+ still clips corners. Changing only the background doesn't remake frames.
  Android 8-11 never clip widgets: anything drawn edge to edge needs its own rounded shape (e.g. `widget_label_background`).
  For Fill pictures there, `deriveWidgetFrames` cuts rounded corners into the frames (transparent, anti-aliased,
  radius = widget corner radius / widget width). Per-widget editor switch "Rounded corners" (`round_<id>`, default on),
  shown only on Android 8-11 in Fill without a shape (`WidgetSizes.needsRoundedFrames`). `fit_round_<id>` records whether the current
  frames are rounded; `WidgetFrames.refreshIfNeeded` remakes them when that doesn't match (fixes older widgets on app open).
- Shapes ("cookie cutters", v1.1; `shape/Shapes.kt`): `WidgetShape(id, nameRes, aspect, path in a 0..1 unit square)`;
  `Shapes.all` is the registry (adding a shape = one entry + its string); row order (user-approved groups): Circle, CD,
  Heart, Star | Sparkle, Moon | Flower, Clover, Leaf, Chonky star | Scallop, Burst | Arch, Dots. Now: Circle, Heart, Star, Chonky star (both from `roundedStar(inner, tipRound,
  valleyRound)`: 5 corners each rounded with a quadratic curve, scaled to fill the unit square). Sparkle: a big slanted 4-point
  sparkle + a small one (`Shapes.Sparkle`: cubic sides bent toward each center, `FAT` = 0.32 chosen by the user); not square,
  its `aspect` (~1.2) comes from the drawing's bounds. Scallop: circle with 12 deep round bumps (depth 0.18)
  (`scallop(bumps, depth)`, polar half-circle per bump, drawn as 1440 short lines). Clover (4 circles) and Flower
  (5 petals) = overlapping same-direction circles (`circles()`, joined by the non-zero fill rule); Burst = 14 sharp points.
  `Fit` centers any drawing in the unit square. Moon (`moonOutline`): circle minus an offset circle (upper left), each tip rounded by a
  tiny circle touching both edges (user's "3. Fatter": offset 0.72, cut 0.86).
  CD (`RingsShape`): concentric rings with their own opacity, drawn with SRC so each ring replaces the one under it
  (measured on real PS5 discs: clear rim 0.985-1 at 40%, label to 0.22 solid, clear hub 25% with a 55% stacking ring at
  0.175-0.19, 55% hole lip 0.135-0.15, hole 0.135 cut: a bit bigger than real, chosen by the user).
  - Spin ("Make the CD spin", only shapes with `spins` = true, i.e. the CD): baked into the frames (`gif/Spin.kt`,
    `deriveWidgetFrames(spin=...)`): the picture is rotated around the box center before the mask is applied (the disc
    stays still). Still pictures become a loop of one turn; animations are repeated until about one turn fits (or hold
    several whole turns), so the loop always ends on whole turns. Up to 15 fps for the spin (never above the FPS cap,
    at least the animation's own rate), max 150 frames; the RAM budget then shrinks frames. `spin_<id>` = seconds per
    turn (null = off; choices 12..1 s, default 4), `fit_spin_<id>` = what the frames were made with (`WidgetFrames.spinFor`
    ignores spin for shapes that can't spin). The playback speed slider also speeds up the spin. Editor: a switch under
    the Shape row only for the CD, and a speed slider only once it's on (`SpinControls`); the preview spins live
    (`Modifier.spinning`, rotation inside the shape's mask).
  - Shine (`WidgetShape.renderShine`, the CD only, user's choices): a light layer drawn on top AFTER the mask (and after
    the spin turn, so the light stays still): white glints on the clear plastic only (two lobes from the top left via a
    SweepGradient, never fully opaque), bright edges on the ridges, a very faint rainbow line at the hub/label edge and
    the rim, a soft one-sided airbrush haze on the label (top left, up to 20%, gone before the middle), and a very faint
    airbrush grain on the label (fixed seed, so frames never flicker). Baked into each widget
    frame in `deriveWidgetFrames`; `ShapedBox` draws it on top in the editor (not on cards: their frames already have it). Mask-only (`hasOutline` = false), so partial transparency shows everywhere.
  - Patterns (`DotsPattern`, the "Dots" shape: 3x3-style grid of slightly overlapping dots, user's "2. Stuck together"):
    the dots keep ~56 dp; a bigger widget gets more dots, not bigger ones. `WidgetShape.forSize(dp)` returns the sized shape
    (`dots@6x3`, aspect = grid's, cached per grid) and `base` the picked one. ALWAYS resolve with
    `WidgetSizes.shapeFor(context, id, shape)` before using aspect / path / mask (WidgetFrames.make + refreshIfNeeded,
    the editor's `drawnShape`); save only the picked (base) shape in `shape_<id>`. `fit_shape_<id>` stores the sized id, so a
    resize that changes the grid re-cuts the frames. `Shapes.byId` reads sized ids via `WidgetShape.sized(key)` (used by the app's cards).
    `FillPattern`: fills the widget's whole shape, a rectangle with some corners rounded (true quarter circles, radii
    from the aspect); sized by aspect in 0.05 steps (`arch@1.50`). Arch (top = half circle as wide as the widget, or as
    round as the height allows) and Leaf (top-left + bottom-right radius 0.48 x shorter side, user's "2. Like yours"). `ShapeMask.render` draws the
  anti-aliased mask (white = shown, transparent = cut, partial alpha = partly see-through) and is the ONE source for
  widget frames, widget background and editor/card previews (`ui/ShapeUi.kt`: `ShapedBox`, `ShapePicker`; picker icons
  are drawn from the same path). Planned later: zoom in Reposition, custom shapes from photos, library, Pixelify, Reverse, painter.
  - Baking: `deriveWidgetFrames(shape=...)` applies the mask with `DST_IN` after the Fit/Fill crop. Frames are sized to the
    shape box (largest box of the shape's aspect in the widget, centered; `WidgetSizes.shapeMaxSidePx`), not the widget.
    Fill: crop aspect = shape aspect (Reposition/crop center unchanged; CropView shows the shape outline). Fit: picture
    centered inside the box. The mask is saved as `mask.webp` next to the widget frames; `FrameSet.masked` (`frames_masked_<id>`).
    The shape replaces Android 8-11 rounded corners (never both). Master frames are never touched.
  - Widget: shaped frames always use the Fit layout (fitCenter, never cropped again). `widget_bg` is hidden and
    `widget_shape_bg` (fitCenter) shows the mask via `FrameProvider.maskUri` (`/<id>/<version>/mask`, the only non-numeric
    path, read-only), tinted with `setColorFilter` + `setImageAlpha`, so background changes never remake frames.
    Shape None = the v1.0 widget (visibilities are always set explicitly because launchers re-apply on top).
  - Storage: `shape_<id>` (absent = None, so v1.0 widgets are unchanged), `fit_shape_<id>` = shape the frames were cut with;
    `refreshIfNeeded` remakes on mismatch and on size changes of the shape box (widget aspect changes don't re-crop a shape).
  - Editor: "Shape" row under Fit/Fill (None + shapes, scrolls sideways; "Custom" goes at the end later). Picking only changes
    the preview; frames are made on Save. Going from None to a shape opens Reposition once per editor visit (Fit or Fill).
    The Shape and Background rows open at their start, or scrolled to the current choice only if it would be off-screen
    there (`rememberRowShowing`), so Transparent / None stay in view whenever possible.
  - RAM: `UsageHealth.widgetCost` counts the mask as one extra frame when `frames.masked`; the per-widget pixel budget in
    `deriveWidgetFrames` counts it too.
- Shape tools (v1.1, `shape/ShapeStyle.kt`): Pixel edges (was "Pixelify"; `PixelSize` Small 32 / Medium 20 / Chonky 12
  cells across the box's long side), Halftone edges (same sizes; one edge style at a time) and Reverse. `StyledShape.of(sizedShape, style, widgetAspect)` wraps any shape (built-in, pattern,
  custom) and is a shape itself, so frames, background layer, previews, zoom, resize re-cuts and RAM work unchanged.
  Pixelify: square cells aligned to the shape box, each = the smooth mask's value at the cell CENTER, drawn without
  anti-aliasing (crisp steps; grays like the CD's become stepped grays). Halftone: the shape drawn 2 cells smaller on each side (so the outward fade fits in the box
  instead of being cut off), shrunk to a half-cell map,
  box-blurred twice (~1 cell) so the edge fades over a few dots, then anti-aliased white dots on a slanted grid (rows half a
  cell apart, odd rows shifted half a cell), radius = 0.56 cell x sqrt(value) (full = merged solid, faint = specks). Reverse: mask flipped with DST_OUT (70% -> 30%);
  the box becomes the whole widget (aspect in 0.05 steps), the hole = the shape box, centered. Order: Pixelify, then Reverse.
  Styled shapes: no outline (Reposition dims with the mask), no CD shine, no spin when reversed. Ids: `heart#p20#r1.50`, `heart#h20`
  (in `fit_shape_<id>`, so tool changes and reversed resizes re-cut); `Shapes.byId` reads them (`StyledShape.fromId`).
  Stored: `shape_pixel_<id>` / `shape_halftone_<id>` (PixelSize name) and `shape_reverse_<id>`; kept even with no shape (toggles keep their
  state), only applied with a shape (`WidgetSizes.shapeFor(context, id, shape, style)`). Editor: `ShapeTools` row (three
  FilterChips in a FlowRow, each with a small icon of its effect: `ic_pixel_edges` / `ic_halftone_edges` = a rounded corner
  as pixels / dots, `ic_reverse` = a square with a round hole; room for "Brush" later) shown only with a shape; the shared size choice appears once an
  edge style is on.
- Custom shapes (v1.1, `shape/CustomShapes.kt`, `ui/ShapeLibraryUi.kt`): any picture becomes a cutter. A shape either draws
  its outline (`WidgetShape.path`, `hasOutline`) or supplies a mask picture (`renderMask`); `ShapeMask.render` calls
  `renderMask`, so frames, background layer, previews, RAM count, zoom and resize re-cuts work unchanged.
  - Making one (`CustomShapes.makeMask`): photo picker / Files (image only), copied to `cache/shape-import` with the same
    50 MB checks (`GifStorage.importFile(..., target)`), decoded by `StillImageDecoder` (EXIF) at <= 1600 px, copy deleted.
    Any pixel with alpha < 255 -> use alpha, else darkness (black = the shape, white = cut, gray partly; the Pinterest
    stencil convention, chosen by the user). Empty borders
    (<= 8/255) trimmed; nothing left -> `error_shape_empty`. Shrunk to <= 512 px, own proportions, white + alpha.
  - Library: `files/shapes/<number>.png` (lossless PNG, also the thumbnail; original never kept), newest first.
    Widgets store `shape_<id>` = "custom:<number>" (no per-widget copy; packs can ship the PNGs later). `Shapes.byId(context,
    id)` resolves it via `CustomShapes.get` (digits only, cached per id); a missing file = no shape (never crash), and
    `refreshAll` on app open re-cuts such widgets without a shape. The library is listed oldest first.
  - UI: the Shape row = None, built-ins, your shapes (oldest first, no names), then always last the "+" tile
    ("Add new shape", opens the add flow; the new shape is applied). Settings > "Your shapes": a grid in the same order; tap = bigger preview + Delete. Add flow (`AddShapeDialogs`): Gallery/Files ->
    "Making your shape" -> preview (shape in color over a checkerboard) -> "Use this shape" / "Cancel".
    Icons/thumbnails are the shape itself (`ShapeIcon`: outline, or the mask tinted).
  - Guide (`ui/ShapeHelp.kt`, user-reviewed): a "How does this work?" link in the Add new shape dialog and in Settings >
    Your shapes opens a bottom sheet: 3 steps (pick -> cutter -> your GIF inside), a highlighted "Where do I find shapes?"
    card (Pinterest first; search words "shape", "shape stencil", "silhouette", never "sticker"), then the two kinds in two
    separate boxes: pictures without transparency FIRST (darkness decides: black + gray examples in the same box), then
    transparent pictures. No tips section. Drawn in code (no image files); the
    pretend GIF is a moving gradient. A NestedScrollConnection takes all leftover scroll/fling, so flicking the guide never closes
    the sheet (it used to close on a fast flick back to the top). Wording: say "transparent", never "see-through". Keep it uncluttered (no extra examples).
  - Tutorial ("Try it yourself!" at the end of the guide): shows the built-in black-and-white cat
    (`drawable-nodpi/tutorial_cat.png`, 512 px, user-approved drawing: wide "mochi" head, tall rounded ears, no eyes) and runs
    the REAL add steps on it (`CustomShapes.makeMaskFromPicture`, same rules as a picked picture) in `AddShapeDialogs`'
    tutorial mode: "Making your shape" (kept up ~1.2 s so it's seen) + hint, preview + hint, Use this shape, then
    "Your cat shape is ready!" saying where it went (editor: on the preview, Save to keep it; Settings: in every Shape row).
  - Shapes over the checkerboard (`MaskPreview`, the guide's cutter) use a DARK theme shade (primaryContainer when the
    accent is light), so they never blend into the white/gray checkerboard.
  - Delete (`CustomShapes.delete`): confirm (says how many widgets use it), PNG removed, each user widget: `saveShape(null)`
    + `refreshIfNeeded` (re-cut, old frames deleted; never RAM-limited) + redraw.
  - Reposition (`ZoomView`) dims outside a custom shape with its mask (DstOut on a layer), since there's no outline.
, shared by editor preview, Reposition and frame baking): the "box" = shape box, or the widget
  without a shape. Zoom 1 = Fill (picture just covers the box); `fitZoom` = Fit (whole picture just fits, <= 1).
  `Zoom.box()` = the part of the picture the box shows, in picture pixels (can reach past the picture when zoomed out):
  where the picture is bigger than the box, the box stays inside it; where smaller, the picture stays inside the box
  (it can move around). Limits: min = half of Fit (`MIN_OF_FIT`), max = 2x Fill but only while master frames have at least
  1 px per real widget-frame px (`maxZoom`; the editor's `outputSidePx` includes the RAM budget split over all frames,
  which makes frames of long GIFs/videos small), never below 1.5x (`MIN_MAX_ZOOM`). Snapping within 4% of Fit/Fill, with a haptic tick.
  - Storage: `zoom_<id>` only for a custom zoom; absent = the Fit / Fill preset in `scale_mode_<id>` (so v1.0 widgets
    keep the exact old code paths). `fit_zoom_<id>` = zoom the frames were made with; `refreshIfNeeded` remakes on mismatch,
    and a custom zoom without a shape re-cuts when the widget's aspect changes (like Fill).
  - Baking: `deriveWidgetFrames(zoom=...)` (crop gives box aspect + center): frames = the box, picture drawn into it, empty
    space transparent; `FrameSet.boxed` (`frames_boxed_<id>`) -> widget uses the Fit layout. Presets (zoom null) use the
    unchanged legacy paths. Frame size = box size, like Fill, so the RAM estimate rules don't change.
  - Editor: Reposition works in Fit and Fill: `ZoomView` (window fixed, picture moves under it; pinch + drag via
    `detectTransformGestures`, values read through `rememberUpdatedState` so a playing GIF doesn't restart the gesture) +
    a logarithmic `ZoomSlider`. While repositioning the zoom is always a number; Done turns it into the FIT preset (zoom ==
    fitZoom and centered) or FILL (zoom == 1), else it stays custom. Cancel/Back restores zoom, mode and center. Fit/Fill
    buttons are presets (lit only when the zoom is exactly on them; Fit recenters). A Fit preset is always drawn centered.
    Picking a first shape opens Reposition (Fit or Fill) on the next frame, once the new box is known.
  - Editor callbacks (onSave, start/endReposition, remakeFrames(), hasChanges()) must read ONLY live state (`by remember`
    vars, `saved`, `rememberUpdatedState` values), never plain `val`s computed during composition, and are never passed as
    `::fn` (Compose keeps an old copy of function references: Done dropped custom zooms and Save closed without saving).
- Apps can't remove widgets from the home screen (only the launcher/host can). Long-press on a card in the app =
  "Remove from list": deletes its data and sets `hidden_<id>` (cleared when the widget is saved again). Launchers
  sometimes keep "ghost" ids from cancelled setups (OnePlus: adding from the home screen widget menu, then cancelling
  the editor). So the editor sets `hidden_<id>` itself when it closes without saving and the widget has no picture.
  Launchers usually clean ghosts up on restart (then onDeleted runs).
- Slideshow (v1.1, done): a second widget kind. `SlideshowWidgetProvider` (empty subclass of the `open`
  `GifWidgetProvider`, own receiver + `xml/slideshow_widget_info.xml` + `widget_preview_slideshow`) so the launcher's
  widget list shows two entries. `GifWidgetProvider.isSlideshow(context, id)` asks the launcher which kind a widget is;
  ALWAYS list widgets with `GifWidgetProvider.allWidgetIds` (both kinds), never `getAppWidgetIds` of one class
  (the onUpdate sweep / onDisabled would delete the other kind's files). Main screen: "Add widget" splits into
  "Normal widget" / "Slideshow". Transitions = the flipper's inAnimation/outAnimation (free, tested on OnePlus: Android
  fade, slide, and a crossfade that fades the new picture in during the first half and the old one out during the
  second, because the flipper stacks children in list order). RemoteViews can't set animations from code, so
  `widget_gif.xml` has one flipper per transition (`widget_flipper` = none, `widget_flipper_fade`, `widget_flipper_slide`,
  anims in `res/anim/slideshow_*`); `showOnlyFlipper` always sets all their visibilities. Time per picture
  (`slide_seconds_<id>`, `SlideTiming.CHOICES` 1-60 s, default 5) and `transition_<id>` (default FADE) are applied at
  draw time (never remake frames); `HomeData` copies the time into the card's FrameSet.intervalMs.
  In-app previews play the transition too (`ui/SlideTransition`, same 500 ms anims; cards and the editor's tap/pick),
  ONLY for fast slideshows: never wrap animation frames in it (two frames overlapping made shaped edges flicker).
  - Slow mode (`SlideTiming.isSlow`: 5 min, 15, 30 min, 1, 3, 6, 12 h, 1 day): `widget/SlowSlideshow.kt`. The widget
    shows ONE frame (still path) = `currentFrame` (`slide_pos_<id>`, `slide_order_<id>` with Shuffle, `slide_changed_<id>`);
    every redraw (re)sets an inexact non-waking alarm (`AlarmManager.set(RTC)`, requestCode = widget id, no permission)
    for changedAt + interval -> `NextPictureReceiver` advances + redraws. Overdue alarms fire when the phone is used again;
    reboot clears alarms, RestoreWidgetsReceiver's redraw sets them again. Non-slow redraws / onDeleted cancel it.
    Save restarts at picture 1 when pictures or playback changed. RAM: `widgetCost(onePicture = true)` = 1 frame.
  - Pictures only (a GIF becomes its first frame), max 20 (`MAX_SLIDES`). Each picture = its own still master folder
    (`Slide(master, name, sizeBytes)`, id = master version; `GifStorage.newVersion()` keeps versions unique when several
    are imported at once). `slides_<id>` = JSON list in play order (`WidgetStore.getSlides/saveSlides`, counted by
    `usedFrameVersions`), `shuffle_<id>`. The saved `StoredGif` has no master; its widget frames = one frame per picture
    (`FrameExtractor.deriveSlideshowFrames`: every picture Fit/Fill-ed, centered, into the same box, so frames are
    `boxed`, same size; shape/mask/shine/corners like normal frames; budget shared). `WidgetFrames.makeSlideshow` /
    `refreshIfNeeded` (slideshows also re-cut on widget aspect changes in Fit). Widget: speed ignored, Shuffle =
    `plan.frames.shuffled()` at every redraw. Editor (`config/SlideshowUi.kt`): preview shows the tapped picture; grid
    (long-press drag to reorder, x on the tapped one), Gallery/Files multi-pick, Shuffle; no spin/speed.
  - Per-picture Reposition: `Slide.zoom` (null = follow Fit/Fill, centered) + `centerX/Y` (JSON "z"/"cx"/"cy", only when
    custom). Reposition in the editor moves the TAPPED picture (`repositioningSlideId`; customZoom/cropCenter are only
    working values, reset to null/center after Done/Cancel; Done on exactly the preset + centered = `reset()`). Grid
    is disabled while repositioning; repositioned tiles show a small mark. Fit/Fill for all with custom crops ->
    `ResetCropsDialog` ("Reset N repositioned pictures?"; never say "crop" to the user). Frames: `SlidePicture` carries each picture's zoom/center into
    `deriveSlideshowFrames` (same `Zoom.box` math as the preview).
    Removed pictures' folders are deleted on Save or when the editor closes (unsaved picks are NOT discarded per pick).
  - Editor order: pictures, Add pictures, Display, Shape, Playback (`SlideshowPlayback`), Background. Playback sits
    after Shape in BOTH editors (normal: Speed).
- New widgets default to Fill.
- Widget shape (`WidgetSizes.currentSizeDp`): portrait = min width x max height, landscape = max width x min height.
  Landscape only counts on big screens (smallestScreenWidthDp >= 600, e.g. tablets): phone home screens stay portrait
  even when the app is rotated. After the tablet rotates, frames are remade on the next app open (shape changed > 10%).
- `GifConfigActivity.EXTRA_ON_HOME_SCREEN`: set by the widget's tap intent and the app's list (widget already placed),
  not by the launcher's add flow. Then the button says "Save" even before a picture is picked (Pixel places pinned
  widgets without opening the editor), and closing without saving doesn't hide the widget as a ghost.
- Videos (`gif/VideoSource`, `config/TrimDialog`): read in place with MediaMetadataRetriever (never copied, 1 GB limit),
  trim dialog (start + length, max 3 s, 2 s on low-end phones), frames taken at the FPS cap -> master frames like GIFs. Android 9+: batch decoding
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
- Videos: MediaMetadataRetriever only (no FFmpeg, no audio), extracted at the FPS cap, max 3 s (2 s on low-end phones).
- Input size limits per file: 50 MB (50,000,000 bytes, as Android displays sizes) for images/GIFs, 1 GB for videos.
- Fully offline: no INTERNET permission, no analytics, no tracking. Media only via photo picker / SAF, no storage permissions.
- All user-facing text in `strings.xml`.

## Release / compatibility (Milestone 7)
- compileSdk/targetSdk 37 (Android 17, latest stable), minSdk 26. All 4 ABIs, no abiFilters; native libs are 16 KB aligned.
- Only permission: RECEIVE_BOOT_COMPLETED. `RestoreWidgetsReceiver` redraws all widgets on BOOT_COMPLETED and
  MY_PACKAGE_REPLACED (battery-killer recovery). Photo picker backport declared for Android 8-10 (ModuleDependencies).
- Release: R8 optimizes the WHOLE app. Do not set `packageScope` (the template's androidx/kotlin-only scope produced an
  APK that crashed at startup with IllegalAccessError). android-gif-drawable keeps `GifInfoHandle` (native) by itself.
  Always smoke-test the release APK (sign with the debug key to install over the dev build).
- `FrameLimits.forDevice()`: low-RAM phones (isLowRamDevice or memoryClass <= 128) get smaller widget frames.
- Editor layout: the preview (and Reposition) is pinned at the top, at most 40% of the screen height and 380dp;
  everything else scrolls underneath it. The editor's title bar is 48dp tall (not 64) to leave more room.
- Layouts: main grid `StaggeredGridCells.Adaptive(170.dp)`, editor/Settings max width 640dp, trim dialog scrolls,
  no fixed heights around text. Widget preview: `drawable-nodpi/widget_preview.png` + `layout/widget_preview.xml`.
- Main screen footer (under the widget cards): "OpenGIFWidget <version> · Get updates", same link as Settings > About.
- Settings > About: "Get updates" opens the GitHub releases page in the browser (just a link; the app keeps NO internet
  permission and never checks for updates itself: user's choice, also what F-Droid/IzzyOnDroid expect).
- Settings > Reliability explains per-brand battery settings; opens ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
  (no REQUEST_IGNORE_BATTERY_OPTIMIZATIONS permission: Play restricts it and the app doesn't need it).
- Device tiers (`DeviceProfile`, by total RAM): LOW (<3.5 GB reported or isLowRamDevice) / MID (<7 GB) / HIGH.
  Max FPS cap 15 / 24 / 30 (also the default; higher options shown disabled in Settings), max video clip 2 / 3 / 3 s,
  per-widget memory budget (`FrameLimits`) ~12 / 24 / 32 MB.
- Usage health (`home/UsageHealth`, shown by `HealthHeader`): estimated launcher memory = shown frames x w x h x 4 bytes
  (after FPS cap/speed); amber > 2% of RAM, red > 4% (calibrated on a smooth 9-widget, ~175 MB setup on a 12 GB phone).
  Flips/s can only make it amber (> 8 x max FPS) because widgets on other pages are paused. Storage: amber < 1 GB free,
  red < 300 MB. "Heavy" tag (>= 60% of the per-widget budget) only when the header isn't blue. It's an estimate (no access to the launcher's real memory).
- Health panel (`home/HealthSheet`, a bottom sheet opened by tapping the header): explains widgets / storage / home
  screen RAM (with a bar: RAM used vs the hard limit). When amber/red, "What you can do": lower FPS cap (applied in place,
  shows RAM saved), the 3 heaviest widgets (tap = editor), how to remove widgets, storage settings (storage problems).
  Only actions the app can really do. User-facing text says "RAM", not "memory".
- Hard limit (`UsageHealth.memoryLimitBytes`): 8% of RAM, 5% on LOW-tier devices (the OS takes a fixed 1-1.5 GB, so small
  phones have far less truly free memory). Checked in the editor's save (`checkMemoryLimit`): others + this widget's new
  cost > limit -> `error_home_screen_full`, new frames deleted, old fit info restored. A save that doesn't make the widget
  heavier is always allowed. Saving new frames is also refused below 300 MB free storage. Resizes are never blocked.
- Testing checklist: TESTING.md.

## Rules
- **Don't add libraries/dependencies without asking first.**
- The user is learning: after each change, explain what changed and why, in simple terms.
- Work one milestone/step at a time. After each one: make sure it builds, explain exactly how to test it on the phone, then stop and wait.
- Git has a GitHub remote (`Octoricht/OpenGIFWidget`, `master` branch). **Never commit or push unless the user says so.**
- **No AI attribution in commits:** never add `Co-Authored-By` (or similar) lines to commit messages. Commits use the
  repo-local identity `Octorice <160361948+Octoricht@users.noreply.github.com>` (keeps the personal email private).
