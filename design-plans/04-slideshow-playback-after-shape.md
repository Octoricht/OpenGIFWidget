# Plan 04: Slideshow "Playback" sits after Shape, like the normal editor

- Status: Done (built and installed 2026-11-26)
- Written against commit: `0b04ccf` (master; slider-style and reset-wording changes uncommitted on top)
- Priority: 1 of 1

## Problem
Both editors share Display, Shape and Background in the same order, but the "Playback" section moves:
- Normal: Name -> Pick (Gallery, Files) -> Display -> Shape -> **Playback** -> Background
- Slideshow: Name -> Pictures -> Add pictures -> **Playback** -> Display -> Shape -> Background

In the slideshow, Display (whose text says "Tap Reposition on the preview to adjust the picture shown", acting
on the picture tapped in the grid) ends up far below the pictures, after Time per picture / Transition / Shuffle
(seen on the phone).

## Evidence
- `config/GifConfigActivity.kt`: `SlideshowSection(...)` is drawn first (~line 1164), then the shared
  Display (~1267), Shape (~1317), the normal-only Playback/Speed block (~1370), Background (~1379).
- `config/SlideshowUi.kt` ~line 196: `SlideshowSection` ends with its own Playback block
  (`if (slides.size > 1)`: SectionTitle `slideshow_playback`, `TimePerPicture`, slow-mode hint,
  `TransitionPicker`, Shuffle switch).
- `CLAUDE.md`: "Shape row under Fit/Fill"; spin switch under the Shape row. Nothing fixes Playback's place.

## Change (one decision: Playback has one place in both editors: after Shape, before Background)
1. `SlideshowUi.kt`: move the Playback block out of `SlideshowSection` into a new
   `internal fun SlideshowPlayback(slideCount, seconds, transition, shuffle, enabled, onSeconds, onTransition, onShuffle)`
   with the exact same contents and the same `slideCount > 1` rule. Remove the now-unused parameters from
   `SlideshowSection`.
2. `GifConfigActivity.kt`: in the Playback slot (where the normal editor shows Speed, after the Shape tools /
   spin / rounded-corners rows, before Background), call `SlideshowPlayback(...)` when `slideshow`, with the same
   24dp spacing as the other sections. `enabled` = `busy == null && !repositioning` (unchanged).
3. No new strings, styles or components.

## How to verify (on the phone)
1. Slideshow editor with 2+ pictures: Name -> Pictures -> Add pictures -> Display -> Shape -> Playback -> Background.
2. With 1 picture: no Playback section (as before).
3. Time per picture, slow-mode hint, Transition and Shuffle work exactly as before.
4. Normal editor unchanged.

## Docs to update after implementation
- `CLAUDE.md` slideshow notes: "Editor order: pictures, Add, Display, Shape, Playback, Background (Playback is after
  Shape in both editors)."
