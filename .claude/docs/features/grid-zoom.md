# Zooming the media grid

Pinching the media grid moves smoothly through a ladder of column counts; past the counts a finger
can tap, the grid is drawn simplified. The folder grid steps one count at a time instead.

## Coupled with

Read these before changing this feature — each can break silently if this one changes without it.

- [thumbnails](thumbnails.md) — the overlay asks for pictures through the bind's own request (`MediaAdapter.thumbnailRequestAt`), as the prefetcher does; change how a tile is requested and landing a zoom decodes every tile again.
- [floating-chrome](floating-chrome.md) — `GridZoomLayout` mirrors the grid's padding, which `FloatingTopBar.keepGridClear()` sets; the glass panels copy the overlay in software every frame.
- [selection](selection.md) — `MediaGridPane.reserveBottomRoom()` changes the grid's padding, which `GridZoomLayout` has to mirror.
- [custom-media-order](custom-media-order.md) — reordering steps a simplified grid in to `largestInteractive`, and its list must not change during a zoom.

## Where it lives

| File | Job |
|---|---|
| `helpers/GridZoom.kt` | The ladder of column counts, and `simpleThumbnailSize` |
| `helpers/GridPinchZoom.kt` | Follows two fingers; `PinchSteps` for the folder grid |
| `views/MediaGridZoom.kt` | The media grid's continuous zoom: level, fling, settle |
| `views/ZoomSetup.kt` | Everything a zoom is drawn from, read off the grid when fingers land |
| `helpers/ZoomScene.kt` | Where everything is drawn at a level between two counts |
| `helpers/GridZoomLayout.kt` | The grid at a count it is not showing, worked out rather than laid out |
| `views/GridZoomOverlay.kt` | Draws the scene in place of the grid |
| `helpers/ZoomThumbnails.kt` | The pictures the overlay draws with |
| `views/GridHandover.kt` | The grid fading out as a zoom starts and back in once it lands |
| `helpers/SimpleThumbnailLoader.kt` | The simplified grid's one prepared Glide request |

## The ladder

Every screen takes a prefix of one sequence — single steps to 7, then 1.4x apart (`RUNG_GROWTH`):
1–7, 10, 14, 20, 28, 39, 55, 77… — cut three **simplified** rungs past `interactiveMax`, the count
whose tile is nearest 55dp. A phone gets 1–7, 10, 14, 20 and no screen more than fourteen rungs; the
sideways grid divides the height. One sequence for every screen keeps `snap()` exact: `Config`
stores four counts (orientation × scroll direction) and each is a rung of every other ladder, so
rotating never drifts a count onto a neighbouring rung.

**`interactiveMax` is a boundary, not a rung** — a wide screen steps 14 to 20 — so anything naming a
tappable count wants `largestInteractive`.

## The simplified grid

Past `interactiveMax` a tile is only its picture, and a screenful is several hundred of them.
`MediaAdapter` binds `photo_item_grid_simple.xml` — a bare `MySquareImageView`, no listeners, no
badges, no selection — and `MediaGridPane.mediaForGrid()` drops the grouping headers, which would
leave ragged gaps. Nothing there is tappable, so a tap zooms in one rung and scrolls the item that
was under the finger back under it.

`SimpleThumbnailLoader` prepares its Glide request **once** and reuses it, because a fling across
twenty columns binds ~100 items in a frame. Every simplified rung decodes at one size,
`GridZoom.simpleThumbnailSize` — taken from the second simplified rung and rounded up to a power of
two, not to a `ThumbnailSizes` rung — so pinching between them decodes nothing.

## Pinching

`GridPinchZoom` follows the fingers' separation itself: commons' `MyZoomListener` scrolls on the same
events, ignores pinches for a second after a lift and steps once per gesture, and
`ScaleGestureDetector` will not start below `config_minScalingSpan` (27mm). The folder grid takes the
spread in steps (`PinchSteps`); the media grid follows it continuously through `MediaGridZoom`. A
level is a rung plus a fraction, one rung per 1.6x of spread (`SPREAD_PER_STEP`); letting go settles
on the nearest rung, or carries on to the next when the fingers lift fast enough
(`FLING_STEPS_PER_SECOND`). Pinching past either end of the ladder stretches a little and gives.

While the fingers are down **the grid itself is left alone**. `GridZoomOverlay`, under it in the
pane, draws the two neighbouring counts from `ZoomScene`: each scaled so their tiles are one size,
and lined up on one lattice. **Across, both are pinned at the edge rows start from** — the left, or
the right in a right-to-left locale — so the grid only grows and shrinks away from it: the column one
count lacks comes in or goes out at the far edge, and nothing slides sideways between counts. Along,
they are lined up by the tile under the fingers, whose row at either count is drawn in one place, so
the rows the pinch is over stay under it. A tile never travels; each cell only changes over from one
count's picture to the other's. Headers keep their length while rows scale. Only pictures are drawn,
never badges. On landing the grid is put at the count and scrolled to lie exactly as last
drawn, then `GridHandover` fades it back in once its tiles have their pictures (at most
`PICTURES_WAIT_MS`).

`ZoomThumbnails` asks for each tile's picture exactly as the grid's bind at that count would, rate
limited per frame (`MISSING_PER_FRAME`, `SHARPER_PER_FRAME`, `ASKING_NANOS_PER_FRAME`). Until one
arrives, a tile is drawn with any other picture of that medium to hand — usually the one borrowed
from the grid's own tile when the pinch began.

## The folder grid

No ladder: it steps one column at a time up to `GridZoom.folderColumnMax()`, the count whose cover is
nearest 80dp (five on a phone). A stored count can be past it, so the grid and the folder picker read
`fittedDirColumnCnt()` rather than `Config.dirColumnCnt`.

## What breaks silently

- **The grid's source is `gridSource()`, never `mMedia`** — a search narrows it, and rebuilding from
  `mMedia` puts the whole library back on screen.
- **Screens with no pinch of their own** (search, the picker dialog) read
  `interactiveMediaColumnCnt()` rather than `Config.mediaColumnCnt`, or they inherit a count whose
  items cannot be tapped.
- **`GridZoomLayout` must place things exactly where the grid does** — span borders as
  GridLayoutManager spreads them, insets through the decoration's own `TileInsets`, header length
  measured off a real header. Any change to the grid's padding, decoration, tile or header layout
  has to be mirrored there, or the zoom jumps where it starts and where the grid takes over.
- **Nothing may change the grid's list during a zoom** — it is drawn from, and put back from, the
  list as it was; `setupAdapter()` defers until `onZoomFinished`.
- **The overlay's pictures are the bind's own requests** (`MediaAdapter.thumbnailRequestAt`), the
  same rule as the prefetcher's, or landing decodes every tile again. Those borrowed from the grid's
  tiles at the start are only safe until the grid rebinds: `ZoomThumbnails.stopBorrowing()` first.
- **The glass panels copy whatever is behind them in software every frame**, the overlay included,
  so it draws only what the canvas's clip shows and only the screen's own draw asks for pictures.
- **Nothing may follow the zoom by transforming the grid as a whole.** Headers keep their length
  while rows scale, so one transform drifts from the drawing by every header above the screen — a
  second copy of the screen sliding past, well down a grouped library. `GridHandover` places each
  tile and header on its own; "along" is kept in doubles, since well down a library it is millions
  of pixels, where a float holds whole pixels or worse.
