# Growing a tile into the viewer

Tapping a photo grows that tile into the fullscreen one, and closing shrinks it back into whichever
tile was swiped to. The grid stays drawn underneath the whole way, so the two windows read as one
surface. The full viewer, the peek viewer and `PhotoVideoActivity` all fly this way.

## Coupled with

Read these before changing this feature — each can break silently if this one changes without it.

- [thumbnails](thumbnails.md) — the tap preloads the viewer's own requests; any change to them has to keep the two identical.
- [viewer-chrome](viewer-chrome.md) — the chrome is dressed from the medium named before the flight (`aimAtOpeningMedium`).
- [peek-viewer](peek-viewer.md) — the peek flies the same way.
- [thumbnail-strip](thumbnail-strip.md) — the strip is dressed before the flight lands.

## Where it lives

| File | Job |
|---|---|
| `helpers/ViewerTransition.kt` | The hand-off between the two activities: the tile's rect, the flight pictures, the grid left registered as the `Anchor` |
| `helpers/TileFlight.kt` | The viewer's half: `enter()` grows, `finishThrough()` shrinks, `holdWindowStill()` |
| `views/FlightOverlay.kt` | The picture in flight: rect and crop moved together |
| `adapters/MediaGridNavigator.kt` | `locateTile()` — the grid's answer to "where is the tile for this path" |
| `helpers/ViewerReturn.kt` | Puts the grid back onto the item the viewer was left on |
| `helpers/ViewerLaunchGuard.kt` | One tap opens one viewer |
| `extensions/Glide.kt` | `lowResPhotoRequest()` / `fullPhotoRequest()` — what the viewer paints with |
| `res/anim/viewer_hold.xml`, `ViewerTheme` | The no-motion window animation, the translucent theme |

Constants: `FLIGHT_GROW_MS` (250), `FLIGHT_DURATION_MS` (the shrink), `FLIGHT_CHROME_IN`,
`FLIGHT_SETTLE_*` in `Constants.kt`.

## The picture

**A flight is drawn with the photo's own picture, never the tile's.** With crop thumbnails on — the
default — a tile's bitmap has been through a `CenterCrop` and has no edges left to unfold, so a
flight drawn with it can only fill the screen and cut to the real photo at the end. The tap starts
two decodes instead, both through the requests the viewer itself paints with: `lowResPhotoRequest()`
— the copy stored inside the file, uncropped, which lands within a few milliseconds — and
`fullPhotoRequest()`, the photo proper behind it. `takeFlightPicture()` hands back the better of the
two in hand, and `TileFlight.pickUpPicture()` reads it every frame, so a flight sets off already
knowing the photo's proportions and is drawn with the real photo within a frame or two. The tile's
own bitmap is copied only as a stand-in for the frame before that, since Glide takes the original
back to its pool on the next rebind.

**Both are the requests the viewer binds**, every part of which is cache key, so the work the tap
started is the work the pager finds done rather than a second decode. The full one is sized off the
screen rather than off the view it lands in, a preload having no view to read.

**A flight is measured against the photo, never the screen** (`landing()`): a fitted photo often
covers little more than half the screen, and a flight sized by the screen overruns it and is yanked
back.

`FlightOverlay` moves the rect and the crop together; either alone leaves a cut at one end or the
other. It is never GONE, only INVISIBLE, since a flight is set up against its position on screen. A
close that comes before the viewer has taken over — the tile still growing, or its photo still
awaited — turns the overlay's picture round from wherever it has got to.

## The two directions

A shrink runs on a viewer that has had the main thread to itself, so it is drawn every frame. **A grow
is started from `onCreate` and has to be given the main thread, or it is not drawn**: inflating a
pager and starting a decode costs a third of a second of dropped frames. So the screen's setup is
handed to `enter()` and built once the flight lands — only the pager waits, since the flight is
already drawing what the viewer will draw, where it will draw it. The medium is named before the
flight (`aimAtOpeningMedium()`), so the bar, strip and buttons can ride in on `FLIGHT_CHROME_IN`
dressed for the right file.

## What breaks silently

It only reads as one surface while the grid is still drawn underneath, which takes three things, each
of which silently leaves the photo growing out of a black screen if it is missed:

- **A translucent theme** (`ViewerTheme`, only translucent in `values-v28`: before API 28 such an
  activity may not ask for an orientation, which the viewer does — so `ViewerTransition.isSupported`
  is false there and every screen falls back) **and** `Window.setFormat(TRANSLUCENT)` at runtime. The
  theme alone composites nothing.
- **No custom animation in `ActivityOptions`.** The system takes one as licence to drop the grid's
  window from the frame. The no-motion window animation lives in the theme instead —
  `viewer_hold` — and, since API 34 ignores `windowAnimationStyle`, is said again through
  `overrideActivityTransition` in `holdWindowStill()`. A close with no tile to shrink into names a
  slide of its own the same way.
- **The exit tile looked up on every page change**: the grid has to scroll and lay out to answer
  (`Anchor` is asynchronous), and a finger already lifted cannot wait a frame for it.

And:

- `ViewerLaunchGuard` turns away a second tap until the viewer that opened is back on top; two tiles
  tapped at once used to open two viewers.
- The grid anchor is dropped when the grid comes back up, and when it is destroyed
  (`dropWhenDestroyed`), or a stale grid answers for the next viewer.
- `PhotoFragment` reports no rect until something is drawn; reporting the view's whole bounds made
  a flight land and then stretch to the screen.
