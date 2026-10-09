# Choosers held open over a button

Rating, copy/move and tabs answer a hold on their button with a picker the finger drags through
without lifting off, and a tap with the dialog (or action) they always had. Held from a button in a
top bar - the toolbar's tab button, or any of the bar's in landscape - the picker hangs under it.

## Coupled with

Read these before changing this feature — each can break silently if this one changes without it.

- [floating-chrome](floating-chrome.md) — `PanelAnim` never touches translation, because choosers place themselves with it.
- [tabs](tabs.md) — `TabChooser` is a hold chooser too.
- [landscape-viewer](landscape-viewer.md) — the viewer says which way a chooser opens from where its bar is when it is held.

## Where it lives

| File | Job |
|---|---|
| `views/HoldChooser.kt` | The base: a `GlassPanel` plus the gesture; `View.holdToChoose()` puts one on a button |
| `views/RatingChooser.kt` | A row of stars, centred on the screen |
| `views/FolderChooser.kt`, `FolderChooserRows.kt` | Copy/move destinations |
| `views/EdgeAutoScroller.kt` | Scrolls the folder list while the finger rests near an end |
| `extensions/QuickChooserFolders.kt` | Builds the folder list, off the main thread |
| `views/TabChooser.kt` | See [tabs](tabs.md) |
| `helpers/PanelAnim.kt` | `showPanel`/`hidePanel`, and `markChosen`, the swell |

## How it works

A subclass says only what the finger is over (`updateSelectionFor(rawX, rawY)`); `holdToChoose`'s
`onOpen` fills the chooser and may answer false to let the hold fall through to a tap, and `onChosen`
reads the pick after the chooser has closed. Whatever the finger is over swells (`markChosen`: 1.25x
for a star or a number, 1.12x for a line of text), on the label and never on the plate behind it — a
plate drawn bigger than its row runs past the list and has its corners clipped. Rows are two views
for that reason: the plate outside, the label inside.

**Rating**: slide right to fill, left to empty, past the first star to clear. Centred on the screen
rather than over the button, since the finger is about to sweep across it.

**Copy/move**: destinations of past operations lead (`Config.recentCopyMoveDestinations`, recorded
in `copyMoveFilesToFolder()` so the dialog teaches the quick list too), then the rest in the grid's
sorting, up to `MAX_QUICK_CHOOSER_FOLDERS`. Built most-recent-last and opened scrolled to its end,
which is nearest the finger. Folders the operation could only fail on are left out — the chooser has
nowhere to explain a refusal. Dragging up past the list keeps the top row and keeps scrolling;
sliding down off it or off a side clears the pick, so a hold that never moved does nothing. Both paths
still run through commons' `CopyMoveTask`, started by the fork's port (see [copy-move](copy-move.md)).

**Hanging under a button** (`dropsBelow`): a chooser is translated to stand a gap under its button
rather than where its layout put it, and grows out of its top edge. The folder list is the mirror of
itself there: the most recent destination at the top, opened scrolled there, its rows sliding down
into place, the button past its top edge picking nothing and dragging on past its foot holding the
last row - and as many rows at once as fit the room above the navigation bar (`roomBelow`), worked
out as it opens, the rows being built only then.

## What breaks silently

- **`revealOver()` lays a chooser out INVISIBLE** and makes it VISIBLE only once positioned — it
  cannot be placed until measured, and going visible first shows it at its untranslated spot for a
  frame. That is also why "is it up" is `!isGone()`, not `isVisible()`.
- **The folder list is prefetched** into `ViewPagerActivity.mQuickChooserFolders` whenever the viewer
  moves to another folder; it reads Room and the filesystem, far too slow to build when the hold
  fires. Once per folder, not per swipe.
- **Nothing in `PanelAnim` touches translation** — a chooser places itself against its button with
  `translationX/Y`. One opening upward has its translation cleared, or after hanging under a button
  it would open where it last hung.
- **`dropsBelow` is set every time the chooser opens**, before `revealOver`: the same chooser serves
  buttons at the top and at the foot, and the bar's buttons move between the two with the screen's
  orientation.
- `FolderChooser` sits at detekt's function-count threshold; that is why `EdgeAutoScroller` and
  `FolderChooserRows` are separate files.
