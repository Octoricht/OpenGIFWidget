# Plan 03: Cat tutorial names the button that's actually on screen

- Status: Done (built and installed 2026-11-26)
- Written against commit: `c31c631` (master, clean tree)
- Priority: 3 of 3

## Problem
At the end of the "Try it yourself!" cat tutorial (started from the editor's Shape row, "+" tile ->
How does this work?), the dialog "Your cat shape is ready!" says "...Tap **Save** to put it on your home
screen...". For a widget that is being added (not yet saved), the editor's bottom button reads
"**Add to home screen**", not "Save", so the instruction names a button that isn't there.

## Evidence
- `app/src/main/res/values/strings.xml`: `tutorial_done_editor` = "It's on your widget's preview now. Tap Save
  to put it on your home screen. You'll always find it in the Shape row."
- `config/GifConfigActivity.kt:1038-1046`: `AddShapeDialogs(tutorialDoneText = stringResource(R.string.tutorial_done_editor), ...)`.
- `config/GifConfigActivity.kt:1106`: the bottom button label is
  `stringResource(if (saved.gif == null && !onHomeScreen) R.string.editor_add else R.string.editor_save)`
  ("Add to home screen" vs "Save").
- The Shape row (with the "+" tile) is always shown (`GifConfigActivity.kt:1313`), so the tutorial is reachable
  while the button says "Add to home screen".
- `ui/ShapeLibraryUi.kt:301-305` just shows `tutorialDoneText`. Settings passes its own text
  (`tutorial_done_settings`); leave that untouched.

## Change (one decision: the text uses the real button label)
1. `strings.xml`: change `tutorial_done_editor` to take the label as an argument:
   `It\'s on your widget\'s preview now. Tap %1$s to keep it. You\'ll always find it in the Shape row.`
   ("to put it on your home screen" becomes "to keep it" so it also reads naturally as
   "Tap Add to home screen to keep it."). The guide text was reviewed by the user, so show them this one
   sentence before finishing.
2. `GifConfigActivity.kt`: compute the button label once in composition, e.g.
   `val saveLabel = stringResource(if (saved.gif == null && !onHomeScreen) R.string.editor_add else R.string.editor_save)`,
   use it for `SaveBar(label = saveLabel, ...)` and pass
   `tutorialDoneText = stringResource(R.string.tutorial_done_editor, saveLabel)` to `AddShapeDialogs`.
   This is display text, not a callback, so the project's rule about callbacks reading only live state isn't
   affected. Make sure `saveLabel` is declared before the `AddShapeDialogs(...)` call.

## Out of scope
- Settings' tutorial text, the tutorial steps, and the button logic itself.

## How to verify (on the phone)
1. Add a NEW widget (main screen -> Add widget -> Normal widget), pick a GIF, then tap Shape "+" ->
   How does this work? -> Try it yourself -> finish: the dialog says "Tap Add to home screen to keep it."
   and the bottom button says "Add to home screen".
2. Do the same from an existing widget's editor: it says "Tap Save to keep it."
3. Then delete the tutorial cat shape in Settings > Your shapes if you don't want to keep it.

## Docs to update after implementation
- None.
