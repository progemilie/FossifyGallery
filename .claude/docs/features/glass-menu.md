# The three dots' drop-down

A frosted `GlassPanel` in place of the platform's overflow popup on all three browsing screens and
behind a selection's menu segment, with items gathered into sections.

## Where it lives

- `views/GlassMenu.kt` — the drop-down; `replaceOverflow()` and `openedBy()`.
- `views/GlassMenuParts.kt` — rows and icon rows (split out for detekt).
- `views/DottedDivider.kt` — the rule between sections.
- `helpers/MenuSections.kt` — one `MenuSpec` per screen: `FOLDER_GRID_MENU`, `MEDIA_GRID_MENU`,
  `VIEWER_MENU`, `SELECTION_MENU`, `SELECTION_MEDIA_MENU`.

## How it works

`GlassMenu.replaceOverflow()` builds the drop-down from the toolbar's own `Menu` every time it opens
and picks through `performIdentifierAction`, so each screen's `refreshMenuItems()` and click listener
carry over untouched; whatever the toolbar already shows as a button of its own is left out. Where
the items come from is the caller's to say, so `openedBy()` serves the navigation pill's menu segment
and the selection pill's menu button — an action mode's menu — with the same panel. Opened from the
foot of the screen it drops upward, and a submenu growing upward is moved as well as resized.

A `MenuSpec` gives the sections, drawn with a dotted rule between them, and which items are drawn
as a row of icons (`MenuIcon`) rather than a row each. Icons are named in the spec, since the menu
gives several of those items none. Only one of each on/off pair in an icon row is visible at a time,
which keeps a row of pairs short.

A spec's `hidden` list is a section the menu opens without, revealed by an arrow the last shown row
wears beside whatever that row already does — the viewer keeps Print, Show on map, Slideshow, Create
shortcut and Change orientation there; a picture selection keeps its rarer items there too. The menu
opens short again next time.

## What breaks silently

- **A spec only arranges — anything it fails to name is appended to the last shown section** (never
  the hidden one) rather than dropped, so no action can go missing by being forgotten there.
  Whether an item is there at all stays the screen's `refreshMenuItems()` business.
- The spec is asked again on every open rather than held, because the screen holding two panes swaps
  which one applies.
