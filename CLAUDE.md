# WuxiaGIFWidget

Personal Android app: home screen widgets that play animated GIFs, in all widget sizes.

## Tech stack
- Kotlin, Gradle Kotlin DSL (`*.gradle.kts`, versions in `gradle/libs.versions.toml`)
- minSdk 26
- App screens: Jetpack Compose
- Widget: classic `AppWidgetProvider` + `RemoteViews`. **Do NOT use Glance.**

## Test device
- OnePlus 15R (OxygenOS). Keep OxygenOS quirks in mind (battery optimization, launcher widget behavior).
- No root, ever. Every solution must work on a stock, unrooted phone.

## How the GIF widget works
RemoteViews can't play GIFs directly, so:
1. Decode the GIF into individual frames with `pl.droidsonroids.gif.GifDecoder` (android-gif-drawable, approved library).
2. Save each frame as a PNG in app storage.
3. Serve the PNGs through the app's own `ContentProvider`, and set them with `setImageViewUri`.
4. Put the frames in a `ViewFlipper` with `autoStart` on and the flip interval = the GIF's average frame duration.

## Rules
- **Don't add libraries/dependencies without asking first.**
- The user is learning: after each change, explain what changed and why, in simple terms.
- Work in small steps. After each step, explain how to test it on the phone.
- Git has a GitHub remote (`master` branch, account Octoricht). **After each completed step, commit and push** with a message describing what was done. Don't commit at other times unless the user asks.
