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
| `helpers/ViewerReturn.kt` | Puts the grid back onto the item the viewer was left on, unless a photo shrank back into its tile |
| `helpers/ViewerLaunchGuard.kt` | One tap opens one viewer |
| `helpers/ViewerOpening.kt` | A flick down while a photo is still opening closes it; `DownFlick` |
| `extensions/Glide.kt` | `lowResPhotoRequest()` / `fullPhotoRequest()` — what the viewer paints with |
| `res/anim/viewer_hold.xml`, `ViewerTheme` | The no-motion window animation, the translucent theme |

Constants: `FLIGHT_GROW_MS` (220), `FLIGHT_SHRINK_MS` (220), `FLIGHT_CHROME_IN`,
`FLIGHT_SETTLE_*`, `OPENING_FLICK_DP` in `Constants.kt`.

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

A shrink is given the main thread by the viewer stopping everything else — see [a close stops the
viewer](#a-close-stops-the-viewer). **A grow is started from `onCreate` and has to be given the main
thread, or it is not drawn**: inflating a pager and starting a decode costs a third of a second of
dropped frames. So the screen's setup is handed to `enter()` and built once the flight lands — only
the pager waits, since the flight is already drawing what the viewer will draw, where it will draw
it. The medium is named before the flight (`aimAtOpeningMedium()`), so the bar, strip and buttons can
ride in on `FLIGHT_CHROME_IN` dressed for the right file.

## Flicking away a photo that is still opening

Someone who opens the wrong photo flicks it away at once, while nothing is there to take the flick:
the pager is built only once the grow lands, a photo still loading turns touches down, and for the
first few hundred milliseconds the viewer's window is not taking touches at all, so the gesture goes
to the grid's window underneath. Both ends watch for it:

- `ViewerOpening.watchGrid` sits ahead of the dispatch of every screen that opens a flight
  (`MainActivity`, `MediaActivity`, `SearchActivity`). From `beginFlight` until the viewer has the
  screen, a gesture on the grid is kept from it — it would scroll the grid under the flight — and a
  flick down closes the viewer, or has it close as soon as it is up (`takeCloseAsked`, checked before
  anything is flown, so that viewer goes at once).
- `ViewerOpening.watchViewer` does the same in the viewer's own window until the stage is
  revealed; from then on the fragment's own flick handling takes over.

`began()` forgets the last viewer's closer. That viewer is already closing but is only destroyed
once the grid has gone idle, so a flick made before the next one is up would be sent to it and lost.

Every flick is timed by its events' own clock (`eventTime - downTime`), never by when the app gets
round to an event: a viewer setting up can hold an ACTION_UP back for half a second, and a flick
timed by that reads as a slow drag.

## A close stops the viewer

The close people make most is a photo looked at and flicked away, and it comes while the viewer is
still setting up. `ViewPagerActivity` builds its pager only once `GetMediaAsynctask` has read the
whole library back in — later the bigger the library — and after that the pages either side and the
zoomable layer load. All of it runs on the main thread, and whatever lands during a shrink freezes it
for as long as it takes. So a close has the viewer do nothing more (`stopStage()` in `TileFlight.kt`):

- The stage is hidden (`INVISIBLE`) rather than left faded out, since a faded view still uploads
  every picture that finishes decoding in it. Not `GONE`, which would lay the whole screen out again
  in the shrink's first frame.
- Every `ViewPagerFragment` is told (`onViewerClosing()`); `PhotoFragment` drops the zoomable layer it
  has scheduled.
- `gotMedia` drops a list read in while `flight.isClosing`, and holds back one read in under a finger
  until the finger lifts: a pager rebuilt half way through a flick loses the flick, and one rebuilt
  just before a close is what the shrink's first frame waits on. Only a rebuild waits: held back,
  the first build left the flight to give up and land on an empty pager, and the photo vanished
  until the finger lifted.

The shrink also runs on the clock, so a slow frame skips it ahead: at 180ms the emulator skipped the
end of a quarter of the shrinks made while a photo was still opening, against one in thirty at 220 —
which is what keeps `FLIGHT_SHRINK_MS` there.

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
- **The grid only moves while the viewer covers it.** Asking for the exit tile centres it out of
  sight; a viewer closed while still opening has not asked yet, so its photo lands in the tile
  where it was. Once a photo has shrunk back into a tile (`ViewerTransition.hasFlownBack`),
  `ViewerReturn` leaves the grid where it is — scrolling it then is a jump seen on the way back.

And:

- `ViewerLaunchGuard` turns away a second tap until the viewer that opened is back on top; two tiles
  tapped at once used to open two viewers.
- Anything a viewer starts once `isClosing` is set lands in the frames of the shrink: a new kind of
  loading has to check it, or stop in `onViewerClosing()`.
- The grid anchor is dropped when the grid comes back up, and when it is destroyed
  (`dropWhenDestroyed`), or a stale grid answers for the next viewer.
- `PhotoFragment` reports no rect until something is drawn; reporting the view's whole bounds made
  a flight land and then stretch to the screen.
