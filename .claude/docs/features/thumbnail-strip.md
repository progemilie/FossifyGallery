# Thumbnail strip

A row of thumbnails between the photo and the bottom actions in the viewer, ported from Aves'
`ThumbnailScroller`. Whatever sits in the middle of the strip is what the pager shows, so scrolling
the strip and swiping the photo are two views of one position. The peek viewer carries it too.

## Coupled with

Read these before changing this feature — each can break silently if this one changes without it.

- [file-edits](file-edits.md) — an in-place edit has to `reload` the strip's thumbnail by hand.
- [peek-viewer](peek-viewer.md) — the peek draws its ticks on the strip.
- [viewer-transition](viewer-transition.md) — the strip is centred on the opening photo before the flight lands.
- [thumbnails](thumbnails.md) — the strip is the one thumbnail view that must be reloaded by hand after an edit.
- [video-player](video-player.md) — a video's page puts the strip away for its own frame strip, lined up with it from `viewer_strip_drop_into_actions` and the bottom actions' height.

## Where it lives

- `views/ViewerThumbnailStrip.kt` — the strip: layout manager, snapping, per-frame decoration.
- `adapters/ViewerThumbnailAdapter.kt` — loads thumbnails and nothing else.
- `Config.showThumbnailStrip` — toggled in settings and from the viewer's drop-down.

## How it works

The strip is padded by half its width at either end, which lets the first and last item reach the
middle; a `LinearSnapHelper` measures against that same middle, so snapping and centring agree with no
offset of their own. The strip commits its middle to the pager as it moves (`onMediumPicked`), not
only once it settles, and swiping the photo scrolls the strip back with a `CenteringScroller` held to
a glide the eye can follow. A fling keeps four fifths of its velocity and lands on an item, the way
Aves' `KnownExtentScrollPhysics` does.

**Size, shade and gaps are drawn, not laid out.** `updateChildDecorations()` sets each child's scale
and shade from its own distance to the middle on every scroll frame, and pulls it towards the middle
by however much of the gaps between has closed. Through the adapter, the highlight landed a frame late
and trailed the thumbnails. Cells stay evenly spaced where they were laid out, so snapping and
`centeredPosition()` are untouched; `StripLayoutManager` lays out past both ends by as far as the end
thumbnail is pulled in, settled against itself over `EDGE_SETTLING_PASSES`.

Thumbnails decode at strip size in RGB_565, with their corners cut by an outline clip on the holder
rather than by Glide's `RoundedCorners`, which would need transparency. An unloaded cell shows a
placeholder of its own rather than black, which over a photo reads as a hole.

## What breaks silently

- A thumbnail already on screen keeps its bitmap however the caches behind it are emptied. After an
  in-place edit the viewer calls `ViewerThumbnailAdapter.reload(path)`; see
  [editing files in place](file-edits.md).
- The peek viewer's ticks are written onto laid out children (`setSelection`), not through a rebind,
  which would restart every Glide load for a tick.
- Its visibility goes through `ViewPagerActivity.updateThumbnailStrip()`, never straight from
  `Config.showThumbnailStrip`: on a video's page the strip is put away (INVISIBLE, so it keeps its
  place) and the [video's frame strip](video-player.md) stands there instead. Not while the user is
  scrolling it (`isUserScrolling`) — the pager follows the strip, and a video passing its middle
  would take it from under the finger — but once it settles (`onUserScrollEnded`).
