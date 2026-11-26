# Plan 01: Shape and Background rows open scrolled to the selected item

- Status: Done (built and installed 2026-11-26)
- Written against commit: `c31c631` (master, clean tree)
- Priority: 1 of 3 (do this first)

## Problem
In the widget editor, the Shape row and the Background row are horizontal `LazyRow`s that always open
scrolled to the start. When the saved choice sits further right, nothing in the visible part of the row looks
selected, so the editor seems to say "no shape" while the preview clearly shows one.

Seen on the test phone (OnePlus 15R): the widget "Crying Yamada" uses a custom (cat) shape. Opening its
editor, the Shape row shows None, Circle, CD, Heart, Star with **no tile highlighted**; the selected custom
tile is off-screen to the right. The only clue that a shape is on is that the Halftone edges chip is lit.

## Evidence
- `app/src/main/java/com/octorice/opengifwidget/ui/ShapeUi.kt:128-162` `ShapePicker`:
  `LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp))` with no `state`. Item order:
  index 0 = None, then `Shapes.all` (built-ins), then `customShapes`, then the "+" (Add new shape) tile.
  Selection is `shape == item` (None selected when `shape == null`).
- `app/src/main/java/com/octorice/opengifwidget/ui/BackgroundPicker.kt:91-135` `BackgroundPicker`:
  same `LazyRow` with no `state`. Item order: index 0 = Transparent (selected when `background.alpha == 0`),
  index 1 = Custom color rainbow (selected when `custom`), then `Swatches` (selected when
  `background.alpha > 0 && background.color == swatch.color`). With a preset like Butter or Peach, the
  selected circle is off-screen on a phone.
- Consumers: the editor (`config/GifConfigActivity.kt:1314` `ShapePicker(...)`, `:1376` `BackgroundPicker(...)`).
  Check with a search whether anything else calls them (e.g. Settings) and confirm the change is harmless there.

## Change (one decision: "a picker row opens showing its current choice")
For both pickers:
1. Create `val listState = rememberLazyListState()` and pass `state = listState` to the `LazyRow`.
2. Compute the selected item's index with the same rules the tiles already use for `selected`:
   - ShapePicker: `shape == null` -> 0; else `Shapes.all.indexOf(shape)` -> `+1`; else
     `customShapes.indexOf(shape)` -> `+ 1 + Shapes.all.size`; not found -> -1.
   - BackgroundPicker: `alpha == 0` -> 0; `custom` -> 1; else `2 + Swatches.indexOfFirst { it.color == background.color }`.
3. Scroll to it **once**, the first time the index is known (>= 0). Use a `remember { mutableStateOf(false) }`
   flag plus `LaunchedEffect(selectedIndex)`: if not yet done and `selectedIndex >= 0`, call
   `listState.scrollToItem((selectedIndex - 1).coerceAtLeast(0))` (one tile of context on the left), then set the flag.
   - Once only: tapping tiles afterwards must NOT make the row jump.
   - Why a LaunchedEffect and not `initialFirstVisibleItemIndex`: the custom-shape library may load after the
     first frame, so the index can be unknown at first.
4. No new colors, sizes or components. Reuse the existing `ShapeTile` / `SwatchCircle` selected styling unchanged.

## Out of scope
- Do not change tile order, sizes, selection styling, or what tapping does.
- Do not add libraries (project rule).

## How to verify (on the phone)
1. Open a widget that uses a custom shape (or a shape near the end, like Dots): the Shape row opens with the
   highlighted tile visible.
2. Pick a background color near the end (Peach), Save, reopen: the Peach circle is visible and ringed.
3. A widget with Shape None / Transparent background: rows open at the start, exactly as before.
4. Tap a few tiles: the row never jumps by itself.
5. Slideshow editor: same results.

## Docs to update after implementation
- `CLAUDE.md`, editor notes: add one line, "Shape and Background rows open scrolled to the current choice (once per editor visit)."
