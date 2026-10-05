# The folder picker

The screen a folder is picked on: where to copy or move to, which folder to open on startup, a
widget's folder, a folder to take a cover image from. It fills the window, names what the folder is
for, and when copying or moving offers a new folder - a plus beside the title, and a dashed tile at
the end of the grid.

## Coupled with

Read these before changing this feature — each can break silently if this one changes without it.

- [folder-cover-styles](folder-cover-styles.md) — the new folder tile copies each style's shape, spacing and label placement by hand.

## Where it lives

| File | Job |
|---|---|
| `dialogs/PickDirectoryDialog.kt` | Upstream's picker: fetching and filtering the folders, the grid, search, back |
| `dialogs/FolderPickerScreen.kt` | Everything around the grid: the window, toolbar, Other folder, the eye, making a new folder |
| `adapters/NewFolderTileAdapter.kt` | The dashed tile, put after the folders with a `ConcatAdapter` |
| `views/NewFolderOutline.kt` | The outline and plus, sized like a cover |
| `layout/dialog_directory_picker.xml`, `layout/item_new_folder_{grid,list}.xml` | |

The title is the caller's: `tryCopyMoveFilesTo()` passes "Copy to" or "Move to", Settings "Open on
startup", and the rest keep "Select destination".

## How it works

**The window** is a `ComponentDialog` in `FullscreenDialog`'s theme, drawn edge to edge
(`fillScreen`). Its content is inflated with the activity's own theme, so the style carries only
what the window needs. The root is padded by the status bar and cutout; the bottom row by the
navigation bar and keyboard, or the grid instead when the bottom row has nothing to show.

**A new folder** goes beside the folder the files come from - inside it, when that is a storage's
root - through commons' `CreateNewFolderDialog`. Once made, the files go straight into it through
the same callback a tapped folder takes, so it lands in the copy/move quick chooser's recents like
any other destination (see [hold-choosers](hold-choosers.md)). Only a copy or a move gets one
(`newFolderBeside` is null otherwise). A search that finds nothing, and a library with no folders at
all, still show the tile.

**The eye** is shown only while hidden folders are hidden everywhere else. Lit with the eye open, it
brings in hidden and excluded folders (after the hidden-items password, if set); plain with the eye
shut, it takes them back out. Either way the picker fetches its folders again.

**Other folder** opens commons' file picker over the screen, which stays until a folder is picked
there, so cancelling it comes back to the grid.

## What breaks silently

- **The new folder tile is laid out apart from `DirectoryAdapter`**, from `FolderCoverStyle`'s
  margin, aspect ratio, corner radius and label placement and the scroll direction. A new style, or
  a change to how a style lays its label out, has to be followed in `NewFolderTileAdapter`.
- **`ConcatAdapter` hides the fast scroller's bubble** in a copy or move: the scroller asks the
  RecyclerView's own adapter for the text, and `ConcatAdapter` is final. The handle still works.
- **The grid's list is `directoryAdapter`**, never `directoriesGrid.adapter`, which is the
  `ConcatAdapter` in a copy or move.
- The bottom row is a `LinearLayout` on purpose: `RelativeLayout` centres a child across its whole
  height, inset padding included, which sank the button and the eye under the navigation bar.
