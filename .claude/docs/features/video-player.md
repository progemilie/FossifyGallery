# The video player

A video in the viewer plays in place, under controls of the fork's own: a play button on a dark disc
in the middle until it has started, then the time, play and pause, speed, loop and sound along the
foot, over a strip of the video's own frames as its progress bar, standing exactly where a photo has
the thumbnail strip. In the gallery's own viewer the chrome goes by itself once a video has played
five seconds untouched, and comes back when it ends. "Open videos on a separate screen" still hands
the video to `VideoPlayerActivity`, which keeps upstream's controls, and its page keeps the thumbnail
strip and the play button in the middle, which is the way there.

## Coupled with

Read these before changing this feature — each can break silently if this one changes without it.

- [thumbnail-strip](thumbnail-strip.md) — the strip steps aside on a video's page, and the scrubber takes its place, lined up from the strip's own dimensions.
- [viewer-chrome](viewer-chrome.md) — the chrome going by itself is the viewer's, asked for by the video.
- [viewer-transition](viewer-transition.md) — a close stops the frame strip reading (`onViewerClosing()`), or it decodes through the shrink.

## Where it lives

| File | Job |
|---|---|
| `fragments/VideoFragment.kt` | The player: ExoPlayer, the gestures, the controls' wiring |
| `views/VideoScrubber.kt` | The progress bar as a strip of frames, a `SeekBar` underneath |
| `views/VideoScrubberFrames.kt` | Reads the scrubber's frames, one load at a time, and caches them |
| `views/VideoPlayerParts.kt` | `SeekHints` (the "+ 20s"), `ChromeAutoHide` |
| `layout/video_controls.xml` | Time, play and pause, toggles and scrubber; `VideoPlayerActivity` keeps `bottom_video_time_holder.xml` |
| `layout/pager_video_item.xml` | The play button in the middle, the seek hints either side of it |
| `ViewPagerActivity` | `updateThumbnailStrip()` puts the strip away on a video's page; owns `ChromeAutoHide` |

## Gestures

The video is taken in thirds. A hold on the left third plays at half speed, a hold anywhere else
at 2x (upstream's). A double tap on a side third skips ten seconds that way, five in a video under
a minute (`skipLengthMs()`, which the time labels skip by too), and in the middle plays or pauses. A run of skips counts up in a hint on that side of the middle. Holds start only on
a playing video, as upstream's 2x hold did.

The strips are there only with volume and brightness gestures on, which is off by default for a new
install; an install from before that default changed keeps them on
(`Config.keepVideoGesturesOnEarlierInstalls()`, from `App.onCreate`).

The volume and brightness strips lie over the video's edges, so a touch listener hands their
gestures to `handleTouchHoldEvent()` too, ahead of their own handling. Once a hold has begun it
takes the rest of its gesture, the lift included: a drag then changes neither volume nor
brightness, and the lift is read as no tap by the strips or the video.

Until the video has started, a flick is the viewer's wherever it begins. The strips come only once
it has (`updateSideScrolls()`), and a flick begun on the play button goes to the flick handling
(`letFlicksThrough()`), which sends the button a cancel so it neither plays nor stays pressed.

## The chrome going by itself

The viewer owns it, not the video: `ViewPagerActivity` sees every touch on the screen, where the
video sees only its own controls. The fragment says only that a video started (`videoStarted()`);
from then, a finger down stops the wait and lifting it starts it over, so a drag, a hold or a chooser
held open never has the chrome go from under it. When five seconds run out the viewer takes the
chrome away only if the video still plays, the metadata sheet is down, and the window has focus — the
three dots' drop-down and every dialog take it, and the window getting it back starts the wait over,
since the tap that closed them went to them. A finished video brings the chrome back through
`videoEnded()`. `PhotoVideoActivity` and the peek viewer leave both hooks at their defaults.

## The scrubber

`VideoScrubber` is a `SeekBar` with its drawables taken away and its track drawn as frames, so a
drag, a tap, keys and TalkBack work as on any seek bar. Frames ahead of the playhead are shaded.
The frames stand to the pixel where a photo's thumbnails do, so a swipe between the two swaps one
strip for the other in place: `thumbnailStripBottom()` works out where the viewer lets the strip down
into the bottom actions, and the scrubber's bottom padding, the room for the playhead, hangs below.
`VideoScrubberFrames` reads as many frames as fill the track at the video's own proportions (held
to 0.5–1.8, at most 24), each at the smallest size that still covers its cell, and hands each over
as it comes; a finished set is cached by path, signature and track size, so a rotation reads again
for the new width and a return to the page does not.

Only the page on screen reads frames (`VideoScrubber.isOnScreen`, which the fragment keeps to its
`mIsFragmentVisible`), one load at a time on a thread of its own. The pager keeps two pages ready either
side; reading theirs too put up to five decoders to work beside the playing video, and left the page
being looked at waiting behind its neighbours. A page swiped away gives up what it had not finished,
as does every page once the viewer starts shrinking back into the grid; frames already cached show on
any page, so one sliding in is not bare.

A cell's frame is the keyframe before its time — cheap, and within a second of the exact one in a
phone's video. Where keyframes lie further apart than the cells, as in a screen recording, the last
keyframe comes round again for a later cell, which is then decoded on to its exact frame. The
comparison is with the last *keyframe*, not the last frame shown: against an exact frame the repeat
goes unnoticed, and the strip shows one keyframe in several cells out of order.

## What breaks silently

- **Seeks in a stream need scrubbing mode.** A drag along the scrubber turns on
  `ExoPlayer.isScrubbingModeEnabled` through `startScrubbing()`/`stopScrubbing()`; without it each
  seek cancels the last before it has drawn, and on a video with keyframes far apart the picture
  stands still until the finger lifts. Left on, it keeps playback suppressed. Playback resumes after
  one only if `mIsPlaying` still says so — a pause meanwhile (the screen going off) wins.
- **`fragmentClicked()` is a tap**, a toggle that closes an open metadata sheet rather than showing
  the chrome. Anything that wants the chrome a particular way goes through `setFullScreen()`.
- **The thumbnail strip's visibility goes through `updateThumbnailStrip()`**, which knows a video's
  page; set straight from the setting, the strip sits over the scrubber. A strip being scrolled
  stays up whatever its middle passes, and is looked at again once it settles.
- **The controls' visibility goes through `updateControls()`**, which knows the chrome, whether the
  video has started, and the player's error: shown anywhere else, the play button in the middle
  comes back over a playing video or an error message.
- **Hidden controls are `INVISIBLE`, never `GONE`.** The volume and brightness readout
  (`slide_info`) is laid out above them, and a RelativeLayout rule naming a gone view is dropped:
  with the chrome away, the readout sat at the top of the page. Still laid out, a page first shown
  with the chrome away reads its frames at once rather than when the chrome comes back.
- **The frames are placed from dimensions, not from the viewer's layout.** `thumbnailStripBottom()`
  repeats how tall `bottom_actions.xml` lays out and `viewer_strip_drop_into_actions`; a change to
  either moves the thumbnail strip, and the frames sit off it until the function follows.
- **The strips' visibility goes through `updateSideScrolls()`**, which knows the setting and whether
  the video has started: shown on a video not yet started, they take the flick down that closes it.
- **The gestures' default is written down once.** Left unwritten, an install that never touched the
  setting reads whatever the default is now, and a default worked out from `appRunCount` on each
  read turns them back on at a new install's second run.
- **Loop is the app's own setting**, so a page already started reads it again whenever it is shown
  (`updateLoop()`): toggled on one page, another would keep its old state and repeat mode.
- **The hints hang off `video_seek_hint_anchor`**, a zero-size view in the middle, not off the play
  button: that is gone once the video has started, and a RelativeLayout rule naming a gone view is
  dropped.
- Frame loads are told apart by `loadId`, not by key: a load given up and started again at the same
  size has the same key, and the old one's late frames would land in the new one's cells.
