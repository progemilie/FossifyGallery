# Tabs

The app can keep up to three places (`MAX_TABS`): a grid, a folder, a folder group or a file open in
the viewer. Off by default (`Config.tabsEnabled`); turning it off puts every screen back as it was.

## Coupled with

Read these before changing this feature — each can break silently if this one changes without it.

- [two-grids-one-window](two-grids-one-window.md) — switching between the two grids is a pane swap, and a restart empties `MediaActivity.mMedia`.
- [startup-screen](startup-screen.md) — a tab with no place, and the first tab on every launch, opens the startup screen.
- [folder-groups](folder-groups.md) — a tab keeps the open group's id.
- [hold-choosers](hold-choosers.md) — `TabChooser` is a hold chooser, placed and animated like the others.

## Where it lives

| File | Job |
|---|---|
| `models/Tab.kt` | `Tab` and `TabLocation` — where a tab is, and nothing else |
| `extensions/Tabs.kt` | The stored list (JSON in `Config`) and every edit to it |
| `helpers/TabSwitcher.kt` | Moving between tabs; the `Locatable` interface screens implement |
| `helpers/TabBar.kt` | The tab button on the end of a browsing screen's search bar |
| `views/TabChooser.kt`, `TabChooserRows.kt`, `TabCloseButton.kt` | The list held open from the button |
| `views/TabBadgeDrawable.kt` | The rounded square with the tab's number |

`MainActivity`, `MediaActivity` and `ViewPagerActivity` are `TabSwitcher.Locatable`.

## A tab is a place, not a screen

A tab holds a `TabLocation` — screen, path, the folder a viewer was opened from, the open group, the
subfolder drill-down — plus the path at the top of the grid and its offset (a path, so it survives the
rescan that rebuilds the list). No views, adapters or media: three tabs cost what one does.

Switching puts that place back up in the one set of screens the app has. Where both places are
`MainActivity`'s own grids, it swaps panes and no activity is touched. Anything deeper lives above
`MainActivity` in the stack, so `TabSwitcher.restart()` drops back to it (`CLEAR_TOP | SINGLE_TOP`,
arriving as `onNewIntent` with `RESTORE_TAB`) and builds the target's stack — the same stack the user
would have walked, so Back behaves normally inside a tab.

Every screen records where it is on the way out and on every switch (`TabSwitcher.record`), which is
what lets tabs survive the app closing. **The first tab is the app's own front door**: never closed,
and put back to the startup screen on every real launch (`resetFirstTab`), while the others stay as
they were left. A tab with no location, new or reset, comes up on the [startup screen](startup-screen.md).

## The button and the list

On the grids the button is a glass circle put *inside* commons' search bar, so it pans and insets
with it; the pill is shortened by `marginEnd` to make room. `TabBar.apply()` runs again after every
commons repaint, so it is idempotent. The viewer has no search bar: tabs are a bottom action
(`BOTTOM_ACTION_TABS`) and a toolbar entry instead.

A tap goes to the next tab, or opens one when there is only one. A hold opens the `TabChooser`, a
[hold chooser](hold-choosers.md): a column of numbers, and a last row for a new tab while there is
room. Resting on a row grows a `TabCloseButton` beside it, which has to be slid onto before letting go
— nothing closes by hesitating. The list sits beside the button rather than over it, so the finger
does not cover it.

## What breaks silently

- **`TabSwitcher.isSwitching` gates recording.** Between asking for a switch and landing, the screen
  on its way out still pauses and stops, and its place belongs to the *old* tab. The screen the tab
  lands on calls `onTabApplied()`.
- **A restored folder goes through `handleLockedFolderOpening`**, as tapping into it does. Restoring
  with `SKIP_AUTHENTICATION` opened protected folders with no password.
- The viewer path and grid scroll are taken off the intent as they are used, or every recreation of
  `MediaActivity` opens the viewer again. `takeTabScroll()` clears what it hands back.
- Only an actual launch resets the first tab — not another app's picker, and not a switch that had to
  build `MainActivity` fresh in a task it was not in (`resetFirstTabIfLaunched`).
- A tab landing closes any open search: the bar belongs to the screen, and a search left in the
  tab being left would narrow the one arriving.
- `isTabLocationGone` checks a deep tab's folder or file is still there; sentinel targets and the
  recycle bin are not stat'd.
- `restart()` empties `MediaActivity.mMedia`, or the incoming pane reads the old tab's list as its
  own until its first scan returns.
