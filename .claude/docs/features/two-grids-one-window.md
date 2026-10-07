# Two grids, one window

Pictures (the all media grid) and Albums (the folder grid) are two panes of `MainActivity` rather
than two screens, because that is the only way the navigation pill and the search bar can hold
still through a swap. An activity handover costs ~400ms to its first frame, which either a window
animation covers — carrying the chrome off with it — or a frozen screen does.

## Coupled with

Read these before changing this feature — each can break silently if this one changes without it.

- [tabs](tabs.md) — a tab switch between the grids is a pane swap in this screen.
- [startup-screen](startup-screen.md) — startup decides which pane opens.
- [floating-chrome](floating-chrome.md) — the chrome is the window's, pointed at a pane by `GridChrome.bind()`; a swap is a draw, not a layout.
- [folder-groups](folder-groups.md) — an open group changes the bar that belongs to the pane that is up.
- [glass-menu](glass-menu.md) — the navigation pill opens the drop-down, and its spec is asked again on every open because panes swap.
- [peek-viewer](peek-viewer.md) — the pane's request codes, `REQUEST_PEEK` among them, sit clear of each host's own.
- [search](search.md) — the chip and the options follow the bar to whichever pane is up, a pill picked in Albums swaps to Pictures, and a search can be carried across a swap.

## Where it lives

- `activities/MainActivity.kt` — both panes, the swap, startup, tab restore.
- `activities/MediaActivity.kt` — the window a `MediaGridPane` is shown in when it is a screen of its
  own: a folder tapped into, favourites, the recycle bin, another app's picker.
- `views/MediaGridPane.kt` — the media grid and every last thing done to it. `MediaGridPane.Host` is
  what it asks of the screen around it.
- `interfaces/GridPane.kt` — what a screen asks of whichever grid is up.
- `helpers/GridChrome.kt` — the search bar, the pill, and the `bind()` that points them at a pane.
- `views/NavPill.kt`, `views/NavPillSegment.kt` — the frosted pill: Pictures, Albums, and a second
  door to the three dots' drop-down.
- `helpers/ScrollPanner.kt` — pans the search bar and the pill out of the way of a scroll.

## How it works

A swap slides only `content_holder`'s two children, by `translationX`. The bar changes over in one
frame at the halfway mark (`HALFWAY`), read off the animation rather than timed beside it so it
stays halfway whatever the device's animation scale is. `GridChrome.bind()` swaps the toolbar's
menu and re-aims the listeners and the panning; nothing about the bar itself moves.

`MediaActivity.mMedia` stays where the viewer looks for the grid's list, whichever window the pane
is in.

The pill is for the two top level grids only. `onPaneStateChanged()` takes it away while a folder
group is stepped into, a search or a filter narrows the grid, an arrangement or a selection is on, a
picker is asking for a picture, or the grid scrolls sideways (no room to pan it out of). A tap on the
segment already showing scrolls that pane back to the top — jumping to a few rows down first
(`RecyclerView.smoothScrollToTop()`), since a smooth scroll from thousands of items crawls.

`ScrollPanner` is shared by the bar and the pill so both agree about what counts as scrolling away.
`panWith(grid)` lets go of the previous grid first; during a swap both grids are on screen, and
chrome still following the one leaving would pan away with it.

Search: the bar names the open folder (or group) rather than saying "Search in …", and Back puts
the keyboard away before it closes the search (see [search](search.md)). With "Search all files by
default" on, opening the search in Albums swaps to Pictures with the search still open
(`swapTo(keepSearch = true)`).

## What breaks silently

- **A re-inflated menu has to be recoloured** — commons tints the icons in `updateColors()`, and
  untinted ones draw invisibly rather than not at all.
- **The bar belongs to whichever pane is up**, which `updateTopBarForGroup()` checks before dressing
  it: opening in Pictures runs the folder pane's startup behind it, and a swap in flight has not
  reached the frame where the bar changes over.
- **A swap is a draw, not a layout** — anything that waits on a layout pass
  (`FloatingTopBar.keepGridClear()`) has to be called outright instead.
- **A pane off screen must not keep working.** `MediaGridPane.mIsActive` says whether it is the one
  up, and the three second MediaStore poll asks it; `isDestroyed` used to answer that, back when the
  grid you left was a finished activity. `onTrimMemory` takes the off-screen pane out of the layout so
  its bound thumbnails are recycled and Glide can reclaim them; it goes back in, at the same index,
  before it is next shown. The visible pane is never touched.
- **Request codes.** The pane is worn by more than one host, so its `startActivityForResult` codes
  (`REQUEST_PEEK` and friends) sit in a block of their own, clear of `MainActivity`'s and commons'.
  Sharing one made closing the peek read as a completed pick, which finished the app.
- The all media grid (`isAllMediaGrid()`, `SHOW_ALL`) can never be arranged by hand — see
  [custom media order](custom-media-order.md).
