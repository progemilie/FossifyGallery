# Chrome that floats over the content

The browsing screens draw content edge to edge with the chrome over it, made of one frosted glass
material. No immersive mode is involved — commons' `EdgeToEdgeActivity` already enables it; what the
fork changed is that the app no longer paints an opaque band under its own bar.

## Coupled with

Read these before changing this feature — each can break silently if this one changes without it.

- [grid-zoom](grid-zoom.md) — the zoom mirrors the grid padding set here, and the glass panels copy the zoom overlay every frame.
- [two-grids-one-window](two-grids-one-window.md) — a pane swap is a draw, so `keepGridClear()` has to be called outright during one.
- [selection](selection.md) — a selection covers the bar (`isCovered`) while keeping its room, so the grid does not jump.
- [hold-choosers](hold-choosers.md) — choosers place themselves with translation, which is why `PanelAnim` never touches it.

## Where it lives

| File | Job |
|---|---|
| `helpers/FloatingTopBar.kt` | Lifts commons' search bar off its band, pads the grid clear of it, pans it with the grid |
| `helpers/ScrollPanner.kt` | The pan-away, shared by the search pill and the navigation pill |
| `extensions/EdgeFade.kt` | The fades at either end of a grid that the system bars read against |
| `helpers/Glass.kt` | Every colour, alpha and blur radius of the glass |
| `views/GlassPanel.kt` | The `BlurView` that wears it |
| `helpers/PanelAnim.kt` | `showPanel` / `hidePanel`, `PanelMotion`, `PanelPivot`, `markChosen` |
| `helpers/LitEdge.kt` | The outline a card stands out by |
| `helpers/Hairline.kt` | The plain hairline a drop-down's surface keeps |
| `views/Dropdown.kt`, `views/InfoPopup.kt` | Popups on a plain raised surface, where there is nothing to frost |

The pills built on this are described with their features: the [navigation pill](two-grids-one-window.md),
[selection pills](selection.md), [reorder pills](custom-media-order.md), [choosers](hold-choosers.md),
[the drop-down](glass-menu.md). The viewer's heading is in [viewer chrome](viewer-chrome.md).

## The grids

`MySearchMenu` is the *last* child of the `CoordinatorLayout` (draw order puts it over the grid) and
the grid gets no top inset of its own; `FloatingTopBar.keepGridClear()` pads it by the bar's measured
height. `FloatingTopBar.makeFloating` runs after every one of commons' `updateColors()`, which paints
the band back. The pill's shadow needs every parent between it and the `CoordinatorLayout` to stop
clipping.

`FloatingTopBar.isAvailable` and `isCovered` both answer through one place: covered (a selection, an
arrangement) the bar goes GONE but its room stays, so the grid does not jump; unavailable, it is gone
altogether.

## Glass

`Glass` works every colour out from the theme rather than resources: a panel is the background
carried towards white, over a blurred copy of what is behind it. `GlassPanel.frost(contentBehind)`
points it at what to copy, which need not be an ancestor. Below Android 12 there is no cheap blur, and
with the Glass UI setting off (`Config.glassUI`), panels paint themselves flat (`Glass.isEnabled`).
A pill floating over a grid is dressed by `GlassPanel.dressAsFloatingPill()`.

**Every panel comes and goes through `PanelAnim`'s `showPanel`/`hidePanel`** — one `PanelMotion`
(`GROW`, `FADE`, `NONE`) named at the one call site, matched to the platform drop-down animation
`GlassMenu`'s popup still gets for free. Nothing there touches translation: a panel places itself
against its anchor with it.

## Outlines

A card that has to stand out wears `LitEdge`: a fine line in the text colour, lit along the top and
fading down the sides. Folder covers (all but Square), stack cards, a thumbnail held in the reorder
mode and a glass panel set `isEdged` (the reorder mode's Save) wear it. `LitEdge`'s defaults are the
look, so applying it names none of them: `LitEdgeDrawable` is it as a foreground or background,
`LitEdgePainter` for a view drawing its own shapes. A drop-down's surface keeps the plain `Hairline`,
weighted by `R.dimen.hairline_width`.

## What breaks silently

- **`FloatingTopBar.keepGridClear()` pads by a height that already carries the status bar inset** — doing
  it in the layout double-counts.
- **A glass panel copies what is behind it into a software canvas every frame.** Anything under
  one that draws a lot (the [zoom overlay](grid-zoom.md)) must draw only what the canvas clip shows.
  A video's surface cannot be copied at all.
- A popup (`Dropdown`, `InfoPopup`) is a window of its own with nothing behind it to sample, so it uses
  a plain raised surface, outlined for the pure black theme.
- `EdgeFade`'s clear end is the theme colour at zero alpha, not `Color.TRANSPARENT` — a transparent
  *black* drags grey through a white fade.
