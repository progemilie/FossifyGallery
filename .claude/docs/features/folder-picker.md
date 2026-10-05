# The folder picker

The screen a folder is picked on: where to copy or move to, which folder to open on startup, a
widget's folder, a folder to take a cover image from. It fills the window with the folder grid
running edge to edge under glass, the way the albums grid does: a back pill and, when copying or
moving, a round plus along the top; the search pill with the eye in it along the foot. Its title
names what the folder is for and, for a copy or a move, how many files - "Copy to (3)". After the
folder tiles comes an Other folder button.

## Coupled with

Read these before changing this feature — each can break silently if this one changes without it.

- [floating-chrome](floating-chrome.md) — the search pill is frosted by `FloatingTopBar.makeFloating()` at the foot of the screen, without `floatOver()`.

## Where it lives

| File | Job |
|---|---|
| `dialogs/PickDirectoryDialog.kt` | Upstream's picker: fetching and filtering the folders, the grid, search, back |
| `dialogs/FolderPickerScreen.kt` | Everything around the folder tiles: the window, the pills, the eye, Other folder, making a new folder |
| `dialogs/FolderPickerLayout.kt` | Insets, fades, padding the grid clear of the pills, the header and Other folder following the grid |
| `layout/dialog_directory_picker.xml` | |

The title is the caller's: `tryCopyMoveFilesTo()` passes "Copy to" or "Move to" and the file count,
Settings "Open on startup", and the rest keep "Select destination".

## How it works

**The window** is a `ComponentDialog` in `FullscreenDialog`'s theme, drawn edge to edge
(`fillScreen`). Its content is inflated with the activity's own theme, so the style carries only
what the window needs. `directories_content` - the grid, the header, Other folder and the fades - is
what every pill frosts; the top bar and the search pill float over it. The top bar is padded by the
status bar and cutout, the search pill by the navigation bar and keyboard, and the grid by the two
of them.

**The header** - the title, and the "No items found" a search can leave under it - sits over the
grid rather than in it, and `FolderPickerLayout.followGrid()` moves it with the grid's first row on
every frame, so it starts on the page and scrolls away above the screen. **Other folder** does the
same below the grid's last row, with the grid's bottom padding making room for it. Both are kept out
of the adapter so the grid's adapter is `DirectoryAdapter` itself: the fast scroller only asks the
RecyclerView's own adapter for its bubble's text. A grid scrolling sideways leaves the header at the
top and Other folder just above the search pill.

**A new folder** goes beside the folder the files come from - inside it, when that is a storage's
root - through commons' `CreateNewFolderDialog`. Once made, the files go straight into it through
the same callback a tapped folder takes, so it lands in the copy/move quick chooser's recents like
any other destination (see [hold-choosers](hold-choosers.md)). Only a copy or a move gets the plus
(`newFolderBeside` is null otherwise).

**The eye** is a menu item in the search pill, shown only while hidden folders are hidden everywhere
else. The eye open, in the text colour, brings in hidden and excluded folders (after the
hidden-items password, if set); shut, in the search hint's grey (`MEDIUM_ALPHA`), it takes them back
out. Either way the picker fetches its folders again, shows only the latest fetch to come back, and
filters it by whatever is typed in the search pill.

**Other folder** opens commons' file picker over the screen, which stays until a folder is picked
there, so cancelling it comes back to the grid.

## What breaks silently

- **Nothing but `DirectoryAdapter` can be the grid's adapter**, or the fast scroller's bubble goes:
  it asks `recyclerView.adapter` for `OnPopupTextUpdate`, and `ConcatAdapter` is final. Anything
  more in the grid floats over it the way the header and Other folder do.
- **Other folder is placed once the grid has an adapter**, so `gotDirectories()` sets one even for
  an empty first fetch; until then Other folder is kept below the screen.
- **Commons tints the search pill's menu in `updateColors()`**, so the eye is painted after it in
  `dressSearchBar()`, which is the only place `updateColors()` is called from.
