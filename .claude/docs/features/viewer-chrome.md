# The viewer's chrome

The fullscreen viewer shows the file's name as its heading with the extended details packed under
it, a [thumbnail strip](thumbnail-strip.md) and the [bottom action bar](bottom-actions.md) along the
foot, and a [glass drop-down](glass-menu.md) behind the three dots. Tapping the photo, or zooming
into it, takes all of that away.

## Coupled with

Read these before changing this feature — each can break silently if this one changes without it.

- [viewer-transition](viewer-transition.md) — the chrome rides in with the flight, dressed before the pager exists.

## Where it lives

- `activities/ViewPagerActivity.kt` — the viewer; `PhotoVideoActivity` and `VideoPlayerActivity`
  share `BaseViewerActivity` with it.
- `activities/BaseViewerActivity.kt` — what every viewer has in common: forced light system-bar
  icons, `updateNavigationBarIconsForPanel()`.
- `helpers/ViewerHeader.kt`, `layout/viewer_header.xml` — the heading in place of a toolbar title.
- `extensions/ExtendedDetails.kt` — the details, formatted, off the main thread.
- `views/DetailsFlowText.kt` — packs the details onto as few lines as fit, a dot between them.
- `fragments/PhotoFragment.kt` → `watchZoom()` — reports zooming in and out.

## The heading

The toolbar shows `viewer_header.xml` instead of a title, so the existing fullscreen fade takes the
heading away with the rest. `ViewerHeader.showDetails()` reads the details after `DETAILS_DELAY`: they
open the file, and scrolling the strip crosses photos faster than any can be opened, so only the one
still on screen when that pauses is read. `isStillCurrent` is asked again with the details in hand,
since a fast swipe can land reads out of order.

`getMediumExtendedDetails()` gives each field the least room it can — a short month name, "6000×4000
(24MP)" — drops anything the file says nothing about, and shows two fields that come out identical
once (a photo modified when it was taken). The name is left out with `skipName`, the heading already
being it. `DetailsFlowText` breaks the lines itself, because a TextView can only break at the spaces
around a dot and strands the dot at a line's end.

## Zooming takes the chrome away

Zooming into a photo does what a tap does: the bar, the strip and the buttons go, and come back when
the photo is let back out, by a double tap or a pinch. `watchZoom()` reads this once per frame from a
pre-draw listener rather than through a callback — only one of the three views a photo can be shown in
offers a zoom callback, and a double tap animates the zoom with no touch to hang anything off. The
question is the one flick-to-close already asks (`isFlickEligible()`), so an unchanged frame costs
three comparisons.

## The menu

The viewer's `VIEWER_MENU` opens short: Print, Show on map, Slideshow, Create shortcut and Change
orientation are its `hidden` section, revealed by an arrow on the Settings row. Rotate left and right
are icons at the top rather than a submenu. The thumbnail strip toggle and the favourite (a heart; the
star is the [rating](ratings.md)) live there too.

## What breaks silently

- The viewer forces light system-bar icons, which vanish against a light-theme panel;
  `updateNavigationBarIconsForPanel()` switches them while the metadata sheet is up.
- The watcher is hung on in `doOnAttach`: before the view is on a window, the observer it would join
  is a temporary one nothing draws through.
- The chrome is dressed from the medium on screen, which during an opening flight is named by
  `aimAtOpeningMedium()` before the pager exists — see [the flight](viewer-transition.md).
