# Selection

Selecting in either grid is made through two frosted pills instead of the platform's contextual
action bar, with a tick that settles rather than flashes, a tick on each group header that takes the
whole group, and a hold on a picked item that unpicks by drag.

## Where it lives

- `helpers/SelectionChrome.kt` — supplies the action mode, and fills the pills from it.
- `views/SelectionPills.kt` — the two pills: the count and the way out at the top, the actions
  along the foot.
- `helpers/SelectionMark.kt` — how a ticked item is drawn, for the media grid, the folder grid and
  the reorder mode alike.
- `adapters/MediaAdapter.kt` — header ticks (`armForGroup`, `toggleGroup`), `UnpickingDrag`, peek
  buttons (`bindPeekButton`), `applySelection`.

## The action mode, supplied by us

The platform's action bar is never built. `onWindowStartingSupportActionMode` is AppCompat's offer
to supply an action mode of a screen's own, and `SelectionChrome.start()` takes it, so nothing covers
the top of the grid and the grid's edge fades stay what the system bars read against.
`SelectionChrome.over()` is the one way a grid screen puts it up.

Everything upstream does through the action mode carries over untouched: the adapter inflates its
menu into `Mode`'s, hides what does not apply in `prepareActionMode()` and invalidates on every
change, which is what fills the pills in again. `Mode`'s menu is a real one borrowed from a
`PopupMenu` that is never shown, since only a support-library menu can be wrapped by AppCompat.

The bottom pill holds up to four named actions (`PILL_ACTIONS`: delete, share, properties, pin),
so a button stays in the same place whatever is selected. Everything else sits behind its menu
segment, which opens the same [glass drop-down](glass-menu.md) the three dots do
(`SELECTION_MENU`, `SELECTION_MEDIA_MENU`). Mirror is kept in the menu on purpose: it rewrites every
selected file.

While a selection is on, the search bar goes (`FloatingTopBar.isCovered`) but its room stays, so the
grid does not jump under a long press. A folder opened as a screen has no pill of its own to make
room at its foot, so `MediaGridPane.reserveBottomRoom()` adds some, into the base commons rebuilds
the padding from too.

## The mark

`SelectionMark.bind()` draws the tick growing in over a hairline-rimmed circle, and settles the
picture under it a third of the way to black for as long as it is picked. The rim is uncoloured, so
the accent circle still reads over a photo of its own colour.

## Group headers

While selecting, each group header wears a tick; tapping it takes the whole group in, or lets it go
once the group is entirely in. Only the tick is a target, so a stray tap on a date cannot carry two
hundred photos. The group is read off the grid at the moment of the tap (`groupRange()`), not bound
in: a header outlives several positions as the list scrolls. The tick hangs into the header's own
padding (`sinkCheckIntoPadding`), so a header is the same height whether a selection is on or not.

## Unpicking by drag

A hold on an item already picked unpicks it, and the drag that follows (`UnpickingDrag`) unpicks what
it passes over and puts back what it leaves. Any other hold is upstream's: pick, and drag to pick.

## What breaks silently

- **Everything a selection draws on a tile is settled as the tile is attached as well as when it is
  bound.** A tile the recycler kept detached just off screen is put back without a rebind, and would
  otherwise come back wearing whatever the last selection left on it — peek buttons, group ticks.
- **The grids keep their change animation off** (`settleChangeAnimations`). Ticking an item
  rebinds it, and the cross-fade a rebind is answered with draws the tile twice, a second and
  contrary animation. An item already on screen in the other state animates; a fresh bind snaps,
  told apart by the `MarkState` each tile keeps in a tag.
- **Changing many items goes through upstream's own toggle**, additions before removals and only the
  last redrawing the title (`applySelection`, `toggleGroup`). An empty selection ends the mode, and
  swapping one item for another would otherwise pass through one.
- The action mode's gestures belong to reordering while it is on — `isSelecting()` is false there.
