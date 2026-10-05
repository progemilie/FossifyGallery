# The folder picker

The screen a folder is picked on: where to copy or move to, which folder to open on startup, a
widget's folder, a folder to take a cover image from. It fills the window with the folder grid
running edge to edge under glass, the way the albums grid does: a back pill and, when copying or
moving, a round plus along the top; the search pill with the eye in it along the foot. Its title
names what the folder is for and, for a copy or a move, how many files - "Copy to (3)". After the
folder tiles come a dashed new folder tile (copy and move only) and an Other folder button.

## Coupled with

Read these before changing this feature — each can break silently if this one changes without it.

- [folder-cover-styles](folder-cover-styles.md) — the new folder tile copies each style's shape, spacing and label placement by hand.
- [floating-chrome](floating-chrome.md) — the search pill is frosted by `FloatingTopBar.makeFloating()` at the foot of the screen, without `floatOver()`.

## Where it lives

| File | Job |
|---|---|
| `dialogs/PickDirectoryDialog.kt` | Upstream's picker: fetching and filtering the folders, the grid, search, back |
| `dialogs/FolderPickerScreen.kt` | Everything around the folder tiles: the window, the pills, the eye, the tiles after the folders, making a new folder |
| `dialogs/FolderPickerLayout.kt` | Insets, fades, padding the grid clear of the pills, the header following the grid |
| `adapters/NewFolderTileAdapter.kt` | The dashed tile |
| `adapters/OtherFolderAdapter.kt` | The Other folder button, as a row (or sideways a column) of its own |
| `views/NewFolderOutline.kt` | The outline and plus, sized like a cover |
| `layout/dialog_directory_picker.xml`, `layout/item_new_folder_{grid,list}.xml`, `layout/item_other_folder.xml` | |

The title is the caller's: `tryCopyMoveFilesTo()` passes "Copy to" or "Move to" and the file count,
Settings "Open on startup", and the rest keep "Select destination".

## How it works

**The window** is a `ComponentDialog` in `FullscreenDialog`'s theme, drawn edge to edge
(`fillScreen`). Its content is inflated with the activity's own theme, so the style carries only
what the window needs. `directories_content` - the grid, the header and the fades - is what every
pill frosts; the top bar and the search pill float over it. The top bar is padded by the status bar
and cutout, the search pill by the navigation bar and keyboard, and the grid by the two of them.

**The header** - the title, and the "No items found" a search can leave under it - sits over the
grid rather than in it, and `FolderPickerLayout.followGrid()` moves it with the grid's first row on
every frame, so it starts on the page and scrolls away above the screen. Kept out of the adapter so
the folder tiles' positions stay `DirectoryAdapter`'s own. A grid scrolling sideways leaves it put.

**The grid** is `FolderPickerScreen.gridAdapter()`: the folders, then whichever of the new folder tile
and Other folder the picker has, joined with a `ConcatAdapter`. A span lookup gives Other folder the
whole row. A search finding nothing, and a library with no folders at all, still show both.

**A new folder** goes beside the folder the files come from - inside it, when that is a storage's
root - through commons' `CreateNewFolderDialog`. Once made, the files go straight into it through
the same callback a tapped folder takes, so it lands in the copy/move quick chooser's recents like
any other destination (see [hold-choosers](hold-choosers.md)). Only a copy or a move gets one
(`newFolderBeside` is null otherwise).

**The eye** is a menu item in the search pill, shown only while hidden folders are hidden everywhere
else. The eye open, in the text colour, brings in hidden and excluded folders (after the
hidden-items password, if set); shut, in the search hint's grey (`MEDIUM_ALPHA`), it takes them back
out. Either way the picker fetches its folders again.

**Other folder** opens commons' file picker over the screen, which stays until a folder is picked
there, so cancelling it comes back to the grid.

## What breaks silently

- **The new folder tile is laid out apart from `DirectoryAdapter`**, from `FolderCoverStyle`'s
  margin, aspect ratio, corner radius and label placement and the scroll direction. A new style, or
  a change to how a style lays its label out, has to be followed in `NewFolderTileAdapter`.
- **The outline is `wrap_content` tall on purpose**: a grid row stretches every tile to its tallest,
  and a `match_parent` outline took the label's room as well, pushing "New folder" out of the tile.
- **`ConcatAdapter` hides the fast scroller's bubble** whenever Other folder or the new folder tile is
  there: the scroller asks the RecyclerView's own adapter for the text, and `ConcatAdapter` is final.
  The handle still works.
- **The grid's list is `directoryAdapter`**, never `directoriesGrid.adapter`, which is usually the
  `ConcatAdapter`.
- **Commons tints the search pill's menu in `updateColors()`**, so the eye is painted after it in
  `dressSearchBar()`, which is the only place `updateColors()` is called from.
