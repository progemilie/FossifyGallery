# Editing files in place

Several fork features write to the user's files: ratings, descriptions, mirroring, Exif rotation and
metadata removal. They split into two kinds, and each kind owes the rest of the app something
different.

## Where it lives

- `helpers/XmpPacket.kt` — parse, edit and serialise an XMP packet; `editXmp()`.
- `helpers/XmpRating.kt`, `XmpDescription.kt`, `XmpFields.kt` — what goes into (or comes out of) one.
- `extensions/Rating.kt`, `extensions/Description.kt` — rating and description writes.
- `extensions/Mirror.kt` — lossless mirror (horizontal flip).
- `extensions/Activity.kt` → `tryRotateByExif`, `fileTransformedSuccessfully`.
- `extensions/MetadataStripping.kt` — metadata removal; see [metadata sheet](metadata-sheet.md).
- `helpers/TransformedMedia.kt` — the cache invalidation an in-place edit needs.

## Metadata writes: rating, description

These rewrite the container but not a pixel.

- **The date is always put back**, regardless of "keep last modified", and before the rescan.
- **No bitmap cache is touched** — every decoded thumbnail is still the picture.
- Both go through `XmpPacket.editXmp()`, which returns `null` to drop an emptied packet, and the input
  unchanged when it could not parse it — callers use that to avoid rewriting the file over an edit that
  went nowhere. A write that would leave the file saying exactly what it said returns false and is
  skipped.
- Everything `XmpPacket` produces is pure ASCII (non-ASCII becomes character references), because
  `ExifInterface` hands XMP over as a String and that is only lossless for ASCII bytes.
- JPEG, PNG and WebP only — the formats `ExifInterface` can write.

## Picture edits: mirror, Exif rotate, metadata removal

These change what the file shows (or its bytes), sometimes without changing anything a cache key is
built from.

**Mirror** rewrites only the JPEG's Exif orientation, composed with the one it had, like upstream's
lossless rotate; other formats fall back to a decode, flip and re-encode. It is in the viewer's bottom
actions and the media selection's menu (not on the pill: it rewrites every selected file).

After any of these, in this order:

1. **`TransformedMedia.onTransformed(path)`** — before touching any cache. An Exif-only edit keeps the
   file's size, and with "keep last modified" on keeps its date, so every cache key
   (`Medium.getSignature()`, `Directory.getKey()`, Picasso's stable key, the adapters' hash diffing)
   is unchanged. This bumps a per-path version folded into those keys, and a global `generation`
   that a screen not on top checks on its next refresh to decide whether to rebind.
2. **`fileTransformedSuccessfully(path, oldLastModified)`** — restores the date when "keep last
   modified" is on, invalidates Picasso's key, clears Glide's disk and memory caches.
3. **Rescan**, after the date is back, so MediaStore's `DATE_MODIFIED` records the restored value.
4. Views that outlive the edit and hold a bitmap are told directly —
   `ViewerThumbnailAdapter.reload(path)`. Glide holds a resource for as long as a view is showing it,
   however thoroughly the caches are emptied.

## What breaks silently

- Any new in-place edit that skips `TransformedMedia.onTransformed` leaves every screen but the one
  that made it serving the old picture. `tryRotateByExif` once did exactly that.
- `TransformedMedia` is in-memory only, deliberately: the caches it invalidates do not outlive the
  process either.
- `saveFileRating` / `saveFileDescription` do not restore the date on their own; callers use
  `updateFileRating` / `updateFileDescription`.
