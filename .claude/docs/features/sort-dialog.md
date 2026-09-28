# Sort by dialog

Sorting and grouping are one dialog, "Sort by", with a dropdown apiece and an arrow beside each for
which way it runs. It replaced two dialogs of radio buttons; "Group by" is gone from the menus.

## Where it lives

- `dialogs/ChangeSortingDialog.kt` — the dialog.
- `dialogs/SortingOptions.kt` — what each dropdown offers, `selectedOption`, the order arrow.
- `views/Dropdown.kt`, `views/DropdownParts.kt` — the field and the popup list it opens.
- `helpers/MediaFetcher.kt` → `sortMedia` / `groupMedia` — what the choices mean.
- `dialogs/ChangeGroupingDialog.kt` — unreferenced, kept as the record of what this replaced.

## How it works

The two orders are independent: a folder is often wanted newest first with its months running
oldest at the top. One "use for this folder only" tick covers both.

The group section goes away on the folder grid and under horizontal scrolling, and greys out while
sorting by rating or by hand — `groupMedia` ignores the grouping under either, since a rating brings
headers of its own and a hand made order is a flat list. Sorting by hand is only offered in a folder
that has been arranged; a shuffle and a hand made order hide the direction arrow (INVISIBLE, so the
field does not change width).

Sorting by rating falls back to newest first within each rating, the way Aves does.

`Dropdown` is a `PopupWindow`, modelled on `GlassMenu` but on a plain raised surface: a popup is its
own window, with nothing behind it for a `GlassPanel` to sample. It measures against the window's
visible frame on screen — a dialog is far shorter than the display it is centred on.

## What breaks silently

- **Grouping is written only when the user touches it.** `Config.getFolderGrouping` subtracts a
  global "by folder" on the way out for anything but the show-all view — arithmetic rather than a bit
  clear — so inside a folder it hands back a number that means nothing. Writing that back would wipe
  the setting on every OK.
- `selectedOption` falls back to the first option for any stored value the list does not offer (a
  rating sort inherited by the folder grid, the mangled grouping above), so a dropdown never names
  something it cannot show.
