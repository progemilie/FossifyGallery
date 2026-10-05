# Folder cover styles

A folder tile can take one of four looks — **Square**, **Rounded**, **Card** (a tall cover with the
name on a frosted band) and **Stack** (a cover on two peeking cards) — with adjustable spacing and an
optional folder size on the details line.

## Coupled with

Read these before changing this feature — each can break silently if this one changes without it.

- [folder-groups](folder-groups.md) — every style has to hold a group's collage and badge.
- [thumbnails](thumbnails.md) — covers decode at the snapped column width less the style's inset, at its aspect ratio.
- [folder-picker](folder-picker.md) — the picker's new folder tile copies each style's shape, spacing and label placement by hand.

## Where it lives

| File | Job |
|---|---|
| `helpers/FolderCoverStyle.kt` | The styles, and everything code has to know about one; `FOLDER_SPACING_STEPS`, `folderDetailsLine` |
| `layout/directory_item_grid_{square,rounded_corners,card,stack}.xml` | One layout per style, all with the same ids |
| `adapters/DirectoryItemBinding.kt` | `GridDirectoryItemBinding` — one lookup for every style; `dressFor(style)` |
| `views/FolderCoverView.kt` | A cover that may be taller than wide, rounded by its outline, frosted under a label |
| `views/FolderStackCards.kt` | The two cards behind a stack cover |
| `dialogs/ChangeFolderThumbnailStyleDialog.kt` | Style, spacing, count and size, with a live two-tile sample |
| `adapters/DirectoryAdapter.kt` | `thumbnailSize()` / `coverDecodeSize()` |

Settings: `Config.folderStyle`, `folderSpacing`, `showFolderSize`.

## How it works

`FolderCoverStyle` is the one description of a style, read by both the grid and the dialog's
preview: its layout, how the bitmap's corners are cut, whether the label goes over the cover or
below it, its aspect ratio (Card is 5:4 tall), its corner radius, and the margin and cover inset at a
given spacing. Square tiles keep no margin and meet edge to edge.

The cover is decoded at the snapped column width less the style's inset, and the style's
proportions along ([thumbnails](thumbnails.md)).

**Card** is rounded by the view's outline, not in the bitmap: its frosted band is the cover's own
drawing replayed through a blur (`FrostPainter`), and a transparent corner would bleed into it. No
second image request. **Stack** records the cover once, small and blurred, and draws that recording
into both cards; behind a group collage or a padlock, and below Android 12, the cards are flat and
shaded from the theme.

Covers other than Square, and stack cards, wear the [`LitEdge`](floating-chrome.md#outlines).

## What breaks silently

- **Every style's layout must carry the same ids** — `GridDirectoryItemBinding` looks them up by id
  rather than through a generated binding per style.
- `FolderCoverStyle.aspectRatio` has to agree with `coverAspectRatio` in the layout.
- A folder size of 0 means it has not been summed yet, and is left off rather than shown as "0 B".
