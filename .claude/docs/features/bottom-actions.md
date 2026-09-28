# The viewer's bottom action bar

The row of buttons under a fullscreen photo can be reordered and filled with up to eight actions,
Mirror, Rating and Tabs among them.

## Where it lives

- `helpers/BottomAction.kt` — `ALL_BOTTOM_ACTIONS`, `parseBottomActionsOrder`,
  `applyBottomActionsOrder`.
- `dialogs/ManageBottomActionsDialog.kt`, `adapters/ManageBottomActionsAdapter.kt` — the dialog.
- `layout/bottom_actions.xml` — the bar, one horizontal ConstraintLayout chain.
- `Config.visibleBottomActions` (a bit mask, upstream's) and `Config.bottomActionsOrder`.

## How it works

`BottomAction.kt` is the one table of bit, view id, label and icon that both the bar and
`ManageBottomActionsDialog` read. `ALL_BOTTOM_ACTIONS` is also the shipping order, and the fallback
for anything a saved order does not mention.

The dialog has a Visible section, in the bar's order and draggable, and a Hidden section always kept
in shipping order, so no arrangement of it is ever the user's to lose.

Rating and copy/move answer a hold with a [chooser](hold-choosers.md); a tap still opens their dialog.

## What breaks silently

- **`parseBottomActionsOrder()` appends whatever a stored order predates** rather than dropping it,
  and drops duplicates and unknown ids, so the result names every action exactly once. An action
  added to the table later lands in its default spot for everyone.
- **`applyBottomActionsOrder()` rebuilds the horizontal chain rather than reordering children** — the
  chain is what spreads the buttons and skips the GONE ones, and re-adding views would leave every
  constraint pointing at its old neighbour. Every button is chained whatever its visibility, so the
  ones that come and go with the file (rating, rotate) need no re-chaining.
