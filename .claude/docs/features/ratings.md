# Ratings

Photos are rated out of five, the way Aves does it, with the rating written into the file so Aves,
Lightroom, digiKam and Windows all read it. Media can be sorted and grouped by rating, and a rating
badge can be shown on thumbnails.

## Coupled with

Read these before changing this feature — each can break silently if this one changes without it.

- [file-edits](file-edits.md) — writes go through `XmpPacket`, put the date back and invalidate no cache.
- [metadata-sheet](metadata-sheet.md) — descriptions share the same XMP packet.
- [custom-media-order](custom-media-order.md) — both Room tables are keyed by path and move together on a rename.
- [sort-dialog](sort-dialog.md) — rating is offered as a sorting and as a grouping, and a scan has to read ratings for either.

## Where it lives

| File | Job |
|---|---|
| `helpers/XmpRating.kt` | Reading and writing `xmp:Rating` in a packet |
| `helpers/XmpPacket.kt` | The packet plumbing shared with descriptions and metadata removal |
| `extensions/Rating.kt` | `getFileRating`, `updateFileRating`, `updateFilesRating`, `storeRating`, `canBeRated`, labels |
| `models/MediaRating.kt`, `interfaces/MediaRatingsDao.kt` | The `media_ratings` cache (DB v12); `media.rating` column |
| `helpers/RatingScan.kt` | The rating side of a media scan |
| `helpers/RatingArrangement.kt` | What is sorted or grouped by rating; the one-off `keepRatingHeaders()` |
| `helpers/MediaFetcher.kt` | `SORT_BY_RATING`, `GROUP_BY_RATING` |
| `views/RatingChooser.kt`, `dialogs/RateMediumDialog.kt` | Hold the star and slide, or tap for a dialog — see [hold choosers](hold-choosers.md) |
| `adapters/MediaAdapter.kt` | Bulk rating a selection |

## The file is the authority

`xmp:Rating` (0–5) is written when a rating is set and removed when it is cleared, taking the whole
packet with it when nothing else is left in it. `MicrosoftPhoto:Rating` is kept in step only where the
file already carries it — never introduced, since a second source of truth is one for other apps to
disagree with. Not an Exif tag: `ExifInterface` has no constant for Windows' Exif Rating (0x4746).

Only JPEG, PNG and WebP can be rated (`canBeRated()`), the formats `ExifInterface` can write back; the
action is not offered for others. `updateFileRating` refuses them with a toast, so no caller checks.

## The cache

`media_ratings` remembers what each file was rated, keyed by lowercased path, with last-modified and
size as the staleness signature — kept out of the media table because media rows are dropped and
reinserted on every rescan. `RatingScan` answers a scan from the cache, opens a file only when nothing
still describes it (caching "no rating" too), and writes what it read at the end. **It only runs at
all when something will use the answer** — a thumbnail badge (`Config.showThumbnailRating`), or
sorting or grouping by rating on that folder or on the all media grid, which is put together out of
every folder's scan (`arrangesByRating`). The one pass over the whole of MediaStore asks whether
anything at all is (`isAnythingArrangedByRating`). A rename carries the row along
(`updateDBMediaPath`).

## Sorting and grouping

Rating is a sorting and a grouping like any other, and the two combine freely: sorted by rating
inside each month, or grouped by rating with each group in date order. A rating group is headed by
its stars, or "Unrated".

Within one rating a rating sort falls back to the date taken, newest first, the way Aves does. A scan
leaves the date taken as the last modified date, or as nothing, unless it is asked for the real one
(`getProperDateTaken`), so every scan sorting by rating asks for it.

Sorting by rating used to bring rating headers along whatever the grouping said. `keepRatingHeaders()`
turned those into saved groupings once, at startup (`RatingHeaders` is the working, unit tested), so
nothing changed on screen for anyone already sorting by rating.

## What breaks silently

- **Writing a rating never moves the file's date**, whatever "keep last modified" says: rating a
  photo is not a change to the photo, and re-dating it would reshuffle every date-sorted grid. The
  date is put back *before* the rescan, so MediaStore records the restored value.
- **No image cache is invalidated.** Writing XMP rewrites the container but not a pixel, so every
  decoded bitmap still stands. See [editing files in place](file-edits.md) for the contrast with a
  mirror.
- `storeRating` updates both the cache and the media row the grid reads, or the badge and the sort
  lag behind the file until the next scan.
