# Folder groups

Several folders drawn under one tile in the folder grid, with a collage cover. Nothing moves on disk.
Tapping the tile steps into the group; a selection can group, add to, rename or ungroup; a tile
dragged and held over another groups the two, and dropped between tiles arranges the grid.

## Coupled with

Read these before changing this feature — each can break silently if this one changes without it.

- [selection](selection.md) — the drag replaces drag-to-select on this grid, and both rely on change animations being off.
- [order-groups-export](order-groups-export.md) — groups travel by name, and tiles stand in the folder order under synthetic paths.
- [folder-cover-styles](folder-cover-styles.md) — the collage and group badge sit inside every style's layout; stack cards go flat behind a collage.
- [two-grids-one-window](two-grids-one-window.md) — an open group changes the search bar (`updateTopBarForGroup`), which belongs to whichever pane is up.
- [tabs](tabs.md) — a tab remembers the open group by id, one more reason ids are never reused.

## Where it lives

| File | Job |
|---|---|
| `models/FolderGroup.kt` | One group: id, name, ordered member paths |
| `extensions/FolderGroups.kt` | The definitions, held in `Config` as JSON, and every edit to them |
| `extensions/FolderGroupTiles.kt` | Turning groups into grid tiles and back: `applyFolderGroups`, `expandFolderGroups`, `pruneFolderGroups` |
| `extensions/CustomFolderOrder.kt` | The folder grid's hand made order |
| `adapters/FolderGroupActions.kt` | The action mode's group items, driving `DirectoryAdapter` from outside |
| `adapters/FolderDragMode.kt` | The drag gestures, also driving `DirectoryAdapter` |
| `views/FolderGroupThumbnail.kt` | The collage: 1, 2, 3 or 4 members |
| `dialogs/FolderGroupNameDialog.kt` | Naming and renaming |
| `activities/MainActivity.kt` | `narrowToOpenGroup`, `sortGroupMembers`, `getCurrentlyDisplayedDirs`, `updateTopBarForGroup` |

## Tiles

Definitions live in `Config` rather than Room because the grid reads them on the main thread. A
folder belongs to at most one group. A tile is a `Directory` under a synthetic `folder_group:<id>`
path, so selection, sorting, pinning and the custom folder order carry it with no case of their own,
and it holds its members in `groupMembers`. It sorts on values aggregated from its members: size and
count add up; a date takes the member that would have sorted first.

**Ids are never reused** (`Config.lastFolderGroupId`) — a synthetic path outlives its group in the
pinned folders and the folder order, and would otherwise attach itself to the next group made.
Removing a group clears its path out of those too.

`pruneFolderGroups()` drops members no longer on disk and any group left empty. Only outright absence
counts: a folder merely hidden or filtered out is still a member, or turning a filter on for a minute
would cost the user the group.

While a group is open, the search pill carries its name and its magnifier becomes the way back out
(`updateTopBarForGroup`). Sorting inside a group follows the root grid's rule: the chosen sorting
applies, and sort-by-custom means the group's own order, which is also what its collage reads.

## The folder grid's own order

`Config.customFoldersOrder` is one flat list of paths. It includes folders the grid is not showing —
hidden, filtered out, inside a group, not yet scanned — so every edit has to leave them where they
were: `saveCustomFolderOrder` fills the slots the shown folders already held rather than writing them
out first. A group tile made on a folder takes its slot (`replaceInCustomFolderOrder`).

## Dragging

`FolderDragMode` lifts a tile on the long press that selects it, replacing commons'
drag-to-range-select on this grid. Dropped between tiles it arranges the grid; held over the middle
of one it goes into a group with it, flying into the cover as it lands. Only a plain folder can be
carried into something: groups do not nest and are never merged. `FolderGroupActions` offers what the
selection can become — folders alone a new group, folders with exactly one group joining it, two
groups nothing — and undoes a flown-in drop if the group it was to make is never named.

## What breaks silently

- **A tile never reaches Room or the scan.** `expandFolderGroups()` puts one back into its folders;
  `MainActivity.getCurrentlyDisplayedDirs()` and `updateDirectories()` are the gates. Storing a tile
  puts a phantom folder in the database.
- **`applyFolderGroups()` says nothing about which group is open**, so the scan thread can build the
  root view while `MainActivity.narrowToOpenGroup()` picks the open group's members on the main
  thread, in the same pass that hands them to the adapter. Splitting those two apart is what used to
  leave the grid showing one state while the screen believed another.
- **While a group is open the grid is not the library** — anything re-scanning or re-sorting works
  from `mDirsIgnoringSearch`, never from what the adapter holds, which would read the rest of the
  library back as deleted.
- **The grid has to hold still under a lifted tile** or nothing could be dropped onto anything —
  hence `FOLDER_DROP_ZONE` (the middle of a tile, which arranging does not reach), the raised
  `FOLDER_DRAG_MOVE_THRESHOLD`, and change animations being off: a tile ticked mid drag would
  otherwise be drawn twice and the finger carry the wrong copy.
