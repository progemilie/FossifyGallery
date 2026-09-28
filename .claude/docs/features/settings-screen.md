# Settings screen

Settings are one card per section, each closed down to its icon, title and a line saying what is
inside until it is tapped. Only one is open at a time.

## Where it lives

- `activities/SettingsActivity.kt` — the screen; `revealCard`, `paintSettings`.
- `views/SettingsCard.kt` — a card, its animated height, `makeAccordion()`.
- `layout/activity_settings.xml` — the settings, a card tag around each section.
- `views/InfoPopup.kt` — the (i) note beside a setting (`View.explains(text)`), used by Tabs.

## How it works

**A card's rows are simply its children in the layout** — `SettingsCard.onFinishInflate` moves them
into a holder under the header — so `activity_settings.xml` still reads as the list of settings, the
screen finds its cards by walking the holder, and every row id and the setup function behind it is
upstream's, unchanged.

Opening or shutting animates the card's own height, the description leaving included, so the title
stays still while everything under it moves. `onOpenSettled` fires once the card that shut to make
room has settled too; `revealCard` then scrolls the opened card into view by the least that will do
it, never past its own title. The hairlines between rows are drawn in `dispatchDraw`, inset to where
the labels start, and cost no height.

The bar floats over the cards, which run under it softened by the same edge fades the grids use; the
heading fades out with the first of the scroll.

## What breaks silently

- **Only the card that can be seen is painted.** `updateTextColors` walks whatever it is handed, and
  handing it the whole screen on resume was 22ms of the 34ms the screen took to come up. A card is
  painted as it opens (`makeAccordion(::paintSettings)`), and whatever is open is painted again on
  resume in case the theme changed.
