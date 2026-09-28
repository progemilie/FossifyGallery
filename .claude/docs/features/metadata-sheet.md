# The viewer's file metadata sheet

A swipe up over the media (or the Properties button) raises `views/MetadataSheet.kt`, listing every
group of metadata the file carries. The sheet also writes two things: the file's description, and
removal of metadata groups.

## Where it lives

| File | Job |
|---|---|
| `views/MetadataSheet.kt` | The sheet: peek height, sections, `attachTo()` |
| `views/MetadataRows.kt` | Building rows (split out for detekt) |
| `views/MetadataWrites.kt` | The description row and the Remove metadata row |
| `helpers/MetadataReader.kt` | Reads the file, off the main thread |
| `helpers/MetadataSummary.kt`, `MetadataFormat.kt` | The pinned summary rows; value formatting |
| `models/FileMetadata.kt` | What the reader hands back |
| `extensions/Description.kt`, `helpers/XmpDescription.kt`, `dialogs/EditDescriptionDialog.kt` | Descriptions |
| `dialogs/RemoveMetadataDialog.kt`, `extensions/MetadataStripping.kt` | Removal: the dialog, write access, scratch copy, caches |
| `helpers/MetadataStripper.kt`, `models/MetadataGroup.kt` | Which groups a file carries, and taking them off |
| `helpers/ContainerMetadata.kt`, `JpegSegments.kt`, `PngChunks.kt`, `WebpChunks.kt`, `ContainerBytes.kt` | Walking and rewriting containers block by block |
| `helpers/XmpFields.kt` | XMP's own copies of location and orientation |

## Reading

`MetadataReader` reads **straight off the file every time** — never from Room, MediaStore or the
`Medium` the grid was built from, all of which describe the last scan rather than the file now.
`com.drewnoakes:metadata-extractor` supplies one directory per group the file stores and those become
the sections verbatim, so nothing is dropped for want of a hand-written mapping; `ExifInterface`,
`MediaMetadataRetriever` and `MediaExtractor` fill in what it cannot parse.

The sheet rests at the height of the pinned summary, so the photo stays visible; dragging further
opens the collapsible sections, which build their rows only when first opened (a fat XMP packet has
hundreds of tags). Swiping to the next file keeps the old rows up until the new ones are read.

`MetadataSheet.attachTo()` is the whole of a viewer's wiring, including
`BaseViewerActivity.updateNavigationBarIconsForPanel()` — the viewer forces light system-bar icons,
which vanish against a light-theme sheet. The sheet is the only child of a full-screen
`CoordinatorLayout` of its own (`metadata_sheet_holder.xml`) and hides that holder with itself, so a
dismissed sheet leaves no layer between a finger and the photo.

## Descriptions

The **description** row is `dc:description` in the file's XMP — a language alternative, written as
`x-default`, read in all three forms other writers use. It is written the way a rating is: through
`XmpPacket`, date put back, no bitmap cache touched (see [editing files in place](file-edits.md)). A
file that can carry one (JPEG, PNG, WebP) always gets the row — an empty row is the only way in to
writing the first description. It can also be shown in the viewer's extended details.

## Removing metadata

**Remove metadata** offers the `MetadataGroup`s the file actually carries
(`MetadataStripper.removableGroups()`, which also hides the row when there are none), in place or
into a numbered copy beside it. Nothing is ticked to begin with. The row sits last, laid out like a
section heading.

`ContainerMetadata` and the per-format walkers do the removal by **copying the file out block by block
and leaving the unwanted ones behind — never by re-encoding**, so a stripped file is pixel for pixel
the file it came from. The work goes into a scratch file first and is only then copied over the
destination. Stripping shortens the file, and calls `TransformedMedia.onTransformed`.

Location and orientation are *not* whole blocks but fields inside the Exif, so `MetadataStripper`
settles them afterwards with `ExifInterface` plus `XmpFields` (XMP keeps its own copy of both; GPS
properties are matched by prefix so none is missed). They part company when the whole Exif goes: the
location cannot survive it (hence Exif ticking and locking Location in the dialog), while the
orientation is read off the source and **written back** unless it was asked for by name — a stripped
photo should not come out sideways.

## What breaks silently

- **Only the three formats the walkers understand are offered**; anything else is refused rather
  than copied.
- **So is a walk that stops short of the end** — a JPEG's scan, a PNG's IEND, the length its RIFF
  header gives — since the copy it wrote is missing the picture and an in-place strip would put it
  over the original. `blocksIn()` is null for such a file, which takes the action off the sheet.
- metadata-extractor builds its Exif directories by reflection; proguard keeps
  `com.drew.metadata.Directory` subclasses, or a release build shows one section named "Exif".
- `MetadataSummary`, `MetadataFormat` and `MetadataRows` are split out to stay under detekt's
  function-count threshold.
