# Plan 02: Long-press wording says "remove from this list", not "remove"

- Status: Done (built and installed 2026-11-26)
- Written against commit: `c31c631` (master, clean tree)
- Priority: 2 of 3

## Problem
The main screen's hint promises one thing and the dialog it leads to says the opposite:
- Hint under the widget cards (seen on the phone): "Tap a widget to edit it. **Long-press to remove it.**"
- Long-press opens a dialog titled "**Remove this widget?**" whose text starts "Apps can't take widgets off
  your home screen, only your launcher can..." and whose button says "**Remove from list**". The snackbar
  afterwards says "Removed from the list".

A user expects the widget to leave the home screen, then reads that it won't.

## Evidence
- `app/src/main/res/values/strings.xml`:
  - `home_hint` = "Tap a widget to edit it. Long-press to remove it."
  - `remove_title` = "Remove this widget?"
  - `remove_message`, `remove_confirm` = "Remove from list", `remove_done` = "Removed from the list" (all correct, keep them).
- `home/HomeScreen.kt:320` shows `home_hint`; `home/HomeScreen.kt:246-256` is the dialog.
- `CLAUDE.md`: "Long-press on a card in the app = 'Remove from list'" (the documented behavior; the button already matches it).

## Change (one decision: match the existing "Remove from list" wording, and say it's only for leftovers)
User's direction (2026-11-26): removing from the list is ONLY for the bug case "a widget is in this list but
isn't on your home screen". The wording must say so. Edit these strings in `app/src/main/res/values/strings.xml`:
- `home_hint` -> `Tap a widget to edit it. If one is listed here but isn't on your home screen, long-press it to remove it from this list.`
- `remove_title` -> `Remove from this list?`
- `remove_message` (lead with the leftover case) ->
  `Only do this if this widget isn\'t on your home screen anymore (a leftover from a setup you cancelled). Restarting your device also clears leftovers.\n\nIf it is on your home screen, remove it there instead: long-press it and choose Remove. Apps can\'t take widgets off your home screen.`

No code changes. Leave `remove_confirm` and `remove_done` as they are.

## Out of scope
- Don't change what long-press does.

## How to verify (on the phone)
1. Main screen: the hint under the cards reads "...Long-press to remove it from this list."
2. Long-press a card: the title reads "Remove from this list?" and the button "Remove from list". Tap Cancel
   (don't remove a real widget).

## Docs to update after implementation
- None (CLAUDE.md already describes "Remove from list").
