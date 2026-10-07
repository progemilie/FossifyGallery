# The viewer in landscape

A landscape window has height to spare least of all, so the viewers lay their chrome out differently
there: the status bar stays away whether or not the chrome is up, the bottom action bar joins the top
bar in a single row, and the foot of the screen is left to the thumbnail strip and a video's frames,
which share their row with the video's controls. Portrait is laid out exactly as it always was.

## Coupled with

Read these before changing this feature — each can break silently if this one changes without it.

- [viewer-chrome](viewer-chrome.md) — everything laid out around the system bars asks `ViewerSystemBars` where they are.
- [bottom-actions](bottom-actions.md) — the bar is moved into the top row, and a button squeezed out of it is reached through the menu.
- [hold-choosers](hold-choosers.md) — a chooser held open from the top row hangs under its button.
- [video-player](video-player.md) — the controls share the frames' row, and nothing in it may change width while the video plays.
- [thumbnail-strip](thumbnail-strip.md) — the strip stands at the very foot, clear of the side bars.

## Where it lives

| File | Job |
|---|---|
| `helpers/ViewerSystemBars.kt` | Whether the window is in the landscape layout; the bars' visibility; the insets everything is laid out by |
| `helpers/LandscapeStatusBar.kt` | Where the status bar is hidden, for every screen; the other screens' side of it |
| `activities/BaseViewerActivity.kt` | `hasLandscapeLayout`, the opt-in; `onLandscapeLayoutChanged()`, the hook a viewer re-arranges in |
| `helpers/BottomActionsPlacement.kt` | Moves the bar between the foot and the top row, and fits the row to the room it has |
| `views/BottomActionButton.kt` | A bar button the row can squeeze out without undoing what the viewer asked of it |
| `views/VideoControlsLayout.kt` | The video's controls in a row of their own, or sharing the frames' row |

The main viewer, the viewer for files opened from other apps and the peek viewer opt in. The separate
video player does not, and keeps upstream's chrome - but not the status bar, which no screen shows in
landscape (see [architecture](../architecture.md#chrome)). Its controls follow the navigation bar
instead, which comes and goes with them.

## How it works

**The layout is a full-screen landscape window**: `isInLandscapeLayout` is false in multi-window mode,
where the window has only a share of the screen and the system bars are not the app's to take away.
A rotation or a change of window mode is checked from both `onConfigurationChanged` and
`onMultiWindowModeChanged`, since which comes first differs, and only a change of layout acts: the
bars are put the way the chrome is, the viewer re-arranges, and the insets are asked for again.

**The status bar is hidden for good, not with the chrome** - in every viewer, the separate video
player too, as on every other screen (`LandscapeStatusBar.isHidden`). With the chrome up only the
navigation bar shows, and a swipe from the top brings the status bar back as a transient bar over the
top row, which the system takes away again. `layoutInsets()` leaves the status bar out, so the top
bar, the video's speed pill and the metadata sheet's highest point all move up into its room - and do
not move when a dialog or the swipe brings the bar back for a moment. It is asked for before the first
frame, so the bar is already leaving as the photo grows in. A rotation acts on the status bar coming or
going; only a viewer with the landscape layout re-arranges for it.

**The bar is moved, not doubled.** `BottomActionsPlacement` takes it out of its place at the foot and
puts it in the toolbar as a custom view with end gravity, ahead of the heading in child order - the
toolbar measures its own buttons first and then its custom views in order, and the heading, being
`match_parent`, takes whatever is left when it gets there. Up there the bar loses its gradient, its
padding and its minimum height, and its buttons stand 44dp apart rather than 48, giving up padding
rather than icon size.

**The row is fitted before every frame.** The heading and the bar share all of the toolbar the
toolbar's own buttons leave, however many buttons are up, so their two widths together are the room
to share out; buttons are squeezed out from the end of the bar's order until the heading keeps
`viewer_top_row_min_title_width`. The fit runs as a pre-draw listener and holds the frame back when it
changes anything, so the row never shows a button over the heading even for one frame. Which buttons
apply to the file on screen is still the viewer's to say, through the visibility it sets;
`BottomActionButton` keeps that apart from being squeezed out, so the two never undo each other.

**A squeezed-out action goes into the three dots' menu.** `overflowed` is a mask of them, and each
viewer's `refreshMenuItems()` counts them as off the bar - so their menu items show like those of any
action not on it. Rating and Properties have no other entry in the viewer's menu, so they have items
of their own that show only in that case. Items the menu would otherwise make toolbar buttons
(`ifRoom`, or the external viewer's `always`) are kept in the drop-down while squeezed out, through
`keepOverflowedInMenu()`, which touches them only on a change since each touch rebuilds the toolbar's
buttons. A bar turned off squeezes nothing out.

**The foot.** With the bar gone from it, the thumbnail strip and the video's frames stand on the
navigation bar - or, where none lies along the foot, as with buttons at the side, clear of the edge by
the room the playhead hangs below the frames (`footInset()`). Both keep clear of side bars and a
cutout the same way (`sideInsets()`), so they still swap in place. The peek viewer has no bar, so its
video frames keep the room a bar would take, above its own strip, in either orientation.

## What breaks silently

- **Anything laid out around the status bar has to ask `systemBars.layoutInsets()`**, never the
  insets themselves: read directly, they report the hidden status bar's height and leave a gap for it.
- **A bar button's visibility is set the ordinary way, never through `isSqueezedOut`** - the row owns
  that, and resets it on every fit. And only by the viewer: anything else calling `setVisibility` with
  what it read back - as `ConstraintSet.applyTo` does unless told to ignore visibility - records a
  squeezed-out button as hidden. See `applyBottomActionsOrder()`.
- **An action added to the bar needs a menu item that shows when it is squeezed out**, or on a screen
  too narrow for it the action is simply gone in landscape.
- **The bar's order goes through `BottomActionsPlacement.applyOrder()`**, which is also the order the
  row squeezes buttons out from the end of; laid out by `applyBottomActionsOrder` alone, the row
  squeezes them out in the order it last knew.
- **Nothing sharing the frames' row may change width while the video plays**: the scrubber reads its
  frames again for every width, so a label growing by a pixel a second reads them all again each time.
  That is what the tabular figures, the minimum widths and the room kept for the controls that only
  come with playing are for.
- **A chooser opened from a bar button is told which way it opens** each time it opens
  (`dropsBelow`), from where the bar is then; set once, it opens toward the wrong end after a rotation.
