# Thumbnail strip

A row of thumbnails between the photo and the bottom actions in the viewer, ported from Aves'
`ThumbnailScroller`. Whatever sits in the middle of the strip is what the pager shows, so scrolling
the strip and swiping the photo are two views of one position. The peek viewer carries it too.

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
