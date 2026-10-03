# Peeking while selecting

While a selection is on, every tile big enough carries an expand button that opens a stripped-down
fullscreen viewer: the media, a tick-and-count pill, and the thumbnail strip. A thumbnail is too
small to choose between near identical frames, and this is how to look without dropping the
selection.

## Coupled with

Read these before changing this feature — each can break silently if this one changes without it.

- [selection](selection.md) — the peek writes the selection back through `applySelection`.
- [viewer-transition](viewer-transition.md) — it opens and closes with the same flight, and dresses its strip and pill before the flight lands.
- [thumbnail-strip](thumbnail-strip.md) — ticks go onto the strip's children through `setSelection`, not through a rebind.
- [two-grids-one-window](two-grids-one-window.md) — `REQUEST_PEEK` has to stay clear of every host's request codes.
- [video-player](video-player.md) — a video's frames keep clear of the strip by asking `isBottomActionBarAtFoot()`, which the peek leaves true.

## Where it lives

- `activities/PeekViewerActivity.kt` — the viewer. A `BaseViewerActivity` over upstream's
  `MyPagerAdapter` and `ViewPagerFragment`s, plus a `ViewerThumbnailStrip`.
- `helpers/PeekSession.kt` — what it is opened on and what it hands back.
- `adapters/MediaAdapter.kt` — `bindPeekButton`, `isPeekButtonUp`, `applySelection`.
- `views/MediaGridPane.kt` — opens it (`REQUEST_PEEK`) and applies the result.

## How it works

A separate activity rather than a flag threaded through `ViewPagerActivity`'s slideshow, bottom
actions and menu bookkeeping. It has no toolbar, filename, details, metadata sheet, bottom actions or
menu.

**Everything it shows it is handed.** `PeekSession` carries the list the grid is displaying — down to
the order a search left it in — and the set of selected paths, in memory, since a folder is past what
an intent carries. Nothing queries MediaStore or Room. The pill both reports and toggles whether the
item on screen is picked; the strip ticks the picked ones (`ViewerThumbnailStrip.setSelection`,
written onto laid out children rather than through a rebind). `selectedPaths` is the one thing the
peek writes, and on return the grid feeds the differences through `MediaAdapter.applySelection` and
scrolls to the item last looked at.

It opens and closes with the same [flight](viewer-transition.md) as the full viewer. The strip and
pill are dressed before the flight from the handed list; only the pager waits for it to land.

## What breaks silently

- The peek button shows only while selecting and only on tiles at least
  `R.dimen.peek_button_min_tile` wide; zoomed out, it would cover the picture it offers to show. On
  a video tile it takes the duration's corner.
- A hold on the button is forwarded to the tile (`root.performLongClick()`), since a hold is how a
  drag selection starts; the button gives no haptic of its own.
- `REQUEST_PEEK` must stay clear of every host's own request codes — see
  [two grids](two-grids-one-window.md).
- The peek leaves `isBottomActionBarAtFoot()` at its default. Its strip does not step aside for a
  video, so a video's frames keep the room a bottom bar would take and stand above the strip, in
  either orientation and whether or not bottom actions are turned on. The hosts with a bar answer
  for the setting themselves; read in the fragment, it put the frames on top of this strip.
- `currentMedium()` reads the pager's item, which is zero until there is an adapter; anything dressed
  before the flight lands has to use the path it was opened on.
