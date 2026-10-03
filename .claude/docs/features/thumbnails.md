# Thumbnails

Everything that draws a picture small goes through one pipeline, built so a picture is decoded once,
from the cheapest source that covers the size, and found in the cache by every screen that asks
for it after.

## Coupled with

Read these before changing this feature — each can break silently if this one changes without it.

- [grid-zoom](grid-zoom.md) — the zoom overlay and the simplified grid's loader share the bind's requests; `simpleThumbnailSize` is deliberately off the `ThumbnailSizes` ladder.
- [file-edits](file-edits.md) — every cache key is path + last-modified + size, so each in-place edit has to call `TransformedMedia.onTransformed`.
- [viewer-transition](viewer-transition.md) — a flight's pictures are the viewer's own cached requests (`lowResPhotoRequest`, `fullPhotoRequest`, `videoStillRequest`); changing one changes what the tap preloads.
- [folder-cover-styles](folder-cover-styles.md) — a cover's decode size comes from the style's inset and aspect ratio.
- [thumbnail-strip](thumbnail-strip.md) — the strip decodes its own thumbnails at strip size, outside the grid pipeline.

## Where it lives

| File | Job |
|---|---|
| `helpers/ExifThumbnailLoader.kt` | `ThumbnailSource`, and the Glide loader that swaps in a photo's embedded copy |
| `helpers/ThumbnailSizes.kt` | The ladder every thumbnail's decode size is snapped to |
| `helpers/ThumbnailPrefetcher.kt` | Decodes what the media grid is scrolling towards |
| `extensions/Context.kt` → `loadImageBase()` | Every Glide thumbnail but the simplified grid's |
| `helpers/SimpleThumbnailLoader.kt` | The zoomed-out grid's one prepared request, see [grid zoom](grid-zoom.md) |
| `extensions/Glide.kt` | The viewer's `lowResPhotoRequest()` / `fullPhotoRequest()` / `videoStillRequest()` |
| `helpers/TransformedMedia.kt` | Invalidates cache keys after an in-place edit |

## The embedded copy

A JPEG cannot be decoded cheaply by asking for less of it: `inSampleSize` saves the inverse
transform but not the pass over the entropy-coded data, which is the bulk of the cost. A 12MP photo
costs ~37ms at any size; the 512x384 copy the camera stored inside it makes the same grid cell in
~3ms.

Anything drawing a thumbnail loads a `ThumbnailSource(path)`. `ExifThumbnailLoader` answers its byte
stream from the embedded copy when the file is a JPEG or raw, big enough to be worth looking inside,
and carries a compressed copy that covers the requested size (within `COVERAGE_TOLERANCE`).
Anything else is handed on, untouched, to whichever loader would have taken the path.

- The copy is stored the same way up as the photo but says nothing about it, so it is given **an
  Exif header of its own carrying the photo's orientation**. Glide then turns and flips it by the
  same code that turns the photo; without it a rotated photo faces different ways in the grid and
  the viewer.
- A path resolves to a byte stream and two kinds of file descriptor, and a video's frame comes from
  the descriptors. `ThumbnailSourcePassThroughLoader` registers those as pass-throughs; without
  them every video loses its thumbnail.

## The size ladder

The decode size is part of Glide's cache key, so grids a few pixels apart used to store the same
picture twice. `ThumbnailSizes.snap()` rounds every tile to the nearest rung of one geometric ladder
(36, 51, 71, 100, 140, 196, 274, 384, 538, 753…, 1.4x apart) and lets the ImageView take up the
rest. **The ladder is anchored on 384**, the short side of the usual embedded copy: nothing at or
below it can be rounded past it, and a tile a little above rounds down onto it and picks the copy up.
The folder grid snaps its covers too (`DirectoryAdapter.thumbnailSize()`), then takes the style's
aspect ratio along.

Grid thumbnails decode in RGB_565, half the memory; Glide keeps ARGB for anything with transparency,
and rounded-corner covers are cut with an alpha mask, so those stay ARGB.

## Prefetching

`ThumbnailPrefetcher` decodes about a screenful ahead of the finger and a quarter behind while the
media grid scrolls, half a screenful both ways once it rests (which also warms a freshly opened
folder), capped at `MAX_IN_FLIGHT`. It sits on top of RecyclerView's own `GapWorker`, it does not
replace it. `reset()` whenever an item moves or a tile resizes: every outstanding request would
otherwise fetch the wrong picture or the right one at the wrong size.

## What breaks silently

- **A preload must describe the picture exactly as the bind that follows does** — model,
  signature, `override()` size, transform and decode format are all cache key. So the request is
  shared, not restated: `loadImageBase` and `SimpleThumbnailLoader` hand their prepared request to
  the prefetcher, and `MediaAdapter.thumbnailRequestAt` to the zoom overlay. Miss one and the grid
  decodes everything twice.
- **Every cache key comes from path + last-modified + size** (`Medium.getSignature()`,
  `Directory.getKey()`, the adapters' hash diffing). An edit that leaves both unchanged — a lossless
  mirror, an Exif rotate, "keep last-modified" on — is invisible to every cache unless it calls
  `TransformedMedia.onTransformed(path)` first. That bumps a per-path version folded into both keys
  and a global `generation` that a screen checks on its next refresh to decide whether to rebind. It
  is in-memory only; Glide's disk cache is cleared on every transform. See
  [editing files in place](file-edits.md).
- A thumbnail already on screen keeps the bitmap it was handed however thoroughly the caches are
  emptied. Views that can outlive an edit need telling — `ViewerThumbnailAdapter.reload(path)`.
- `loadImageBase` is also where the WebP decoder is held to the safe path (CVE-2023-4863,
  `WebpDownsampler.USE_SYSTEM_DECODER = false`); `SimpleThumbnailLoader` repeats it. Any new loader
  must too.
- A failed load leaves `ScaleType.CENTER` for its warning icon; binds reset `scaleType` first.
