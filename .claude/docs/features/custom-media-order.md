# Per-folder custom media order

Sort-by-custom for media; upstream has it for folders only. A folder's pictures are dragged into an
order of the user's own, which then comes up whenever that folder is sorted by hand.

## Where it lives

- `models/MediaOrder.kt`, `interfaces/MediaOrderDao.kt` — the `media_order` Room table (DB v11).
- `extensions/CustomMediaOrder.kt` — save, read, remove; all blocking.
- `Config.customMediaOrderFolders` / `hasCustomMediaOrder()` — the index of which folders have one.
- `adapters/MediaReorderMode.kt` — the drag-to-arrange mode, driving `MediaAdapter` from outside.
- `views/ReorderPills.kt` — the pills the mode is worked through.
- `views/MediaGridPane.kt` — `startReordering` / `saveReordering` / `cancelReordering`,
  `resetCustomOrder`.
- `helpers/MediaFetcher.kt` → `sortMediaByCustomOrder`.
- `helpers/DragLift.kt` — how a picked up item looks, shared with the folder grid.

## Storage

`media_order` holds the arranged paths keyed by lowercased folder path, kept out of the media table
because media rows are dropped and reinserted on every rescan. `Config.customMediaOrderFolders` is
only an *index* of which folders have an order, so `hasCustomMediaOrder()` can be answered on the main
thread where Room would throw; **the table is the authority**. Files the saved order does not cover
sort after it, by path, so the tail has a fixed order of its own.

A rename carries the file's row along (`updateDBMediaPath`), or the renamed file drops out of the
arrangement.

## Arranging

`startReordering()` closes any search (an arrangement covers the whole folder), steps a simplified
grid in to `largestInteractive`, and flattens the list — headers have no place in a hand made order.
While the mode is on it owns the grid's gestures: **a tap marks** an item to travel with the next
drag, **a long press picks it up**. Dragging any marked item carries the whole group: the others
leave the grid for the length of the drag, so what is on screen is what the arrangement will look
like, and they land around the dropped item in the order they were picked up. The move-to-edge
buttons send every marked item to the top or bottom at once, in grid order. Back unmarks before it
leaves.

`ReorderPills` stand where a selection's would: the way out at the top, the two send-to-an-end arrows
opposite Save along the foot. The search pill goes altogether while arranging (not panned away), so a
scroll cannot bring it back. A picked up item is lifted by `DragLift.animatePickUp()`, the same lift a
folder tile gets, and edged in the `LitEdge` a folder cover wears for as long as it is held.

Save writes the order off the main thread, switches the folder to `SORT_BY_CUSTOM`, and puts the grid
back where it was. Reset asks first — nothing brings an arrangement back.

## What breaks silently

- **The all media grid can never be arranged by hand.** An order is a folder's own and that grid is
  the whole library under one `SHOW_ALL` key, growing and shrinking under any arrangement. The menu
  does not offer it, `startReordering()` refuses, and export/import skip it. Reset stays, for a grid
  arranged before this rule.
- `NearestCellMoveCallback` drops the item on the cell it covers most. ItemTouchHelper's default
  rule stops responding once a carried group slides the held item's cell out from under the finger.
- `PaddedGridMoveCallback` is needed by both grids' drags: the grid keeps its first row clear of the
  search bar by padding, and the stock `prepareForDrop` measures from below it, shoving the list down
  by the padding on every swap.
- `DragLift` uses animators of its own, not `view.animate()`: the item animator cancels whatever it
  finds on that builder, stranding a picked up item half lifted.
