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
- [landscape-viewer](landscape-viewer.md) — the controls share the frames' row in landscape, where nothing in it may change width.
- [peek-viewer](peek-viewer.md) — the peek's strip never steps aside, so the frames stand above it only because the peek answers `isBottomActionBarAtFoot()` true.

## Where it lives

| File | Job |
|---|---|
| `fragments/VideoFragment.kt` | The player: ExoPlayer, the gestures, the controls' wiring |
| `views/VideoScrubber.kt` | The progress bar as a strip of frames or a plain line (`ProgressLine`), a `SeekBar` underneath |
| `views/VideoScrubberFrames.kt` | Reads the scrubber's frames, one load at a time, and caches them |
| `views/VideoPlayerParts.kt` | `SeekHints` (the "+ 20s"), `ChromeAutoHide` |
| `views/VideoControlsLayout.kt` | The controls in a row of their own, or sharing the frames' row in landscape |
| `layout/video_controls.xml` | Time, play and pause, toggles and scrubber; `VideoPlayerActivity` keeps `bottom_video_time_holder.xml` |
| `layout/pager_video_item.xml` | The play button in the middle, the seek hints either side of it |
| `ViewPagerActivity` | `updateThumbnailStrip()` puts the strip away on a video's page; owns `ChromeAutoHide` |

## Gestures

The video is taken in thirds. A hold on the left third plays at half speed, a hold anywhere else
at 2x (upstream's). A double tap on a side third skips ten seconds that way, five in a video under
a minute (`skipLengthMs()`, which the time labels skip by too), and in the middle plays or pauses.
A run of skips counts up in a hint on that side of the middle. Holds start only on a playing video,
as upstream's 2x hold did.

With the volume or brightness gesture on, a strip lies over that edge of the video (volume the
right, brightness the left) to take a vertical drag. A touch listener hands their gestures to
`handleTouchHoldEvent()` too, ahead of their own handling, and once a hold has begun it takes the
rest of its gesture, the lift included: a drag then changes neither volume nor brightness, and the
lift is read as no tap by the strips or the video.

Every gesture has a setting of its own, on the Gestures page under Video player, and
`VideoPlayerActivity` obeys them too. Volume and brightness are off by default, and until set fall
back to upstream's single `allowVideoGestures`, which an old settings backup imports into both; the
hold and the double tap skip are on. With
skipping off, a double tap on a side plays or pauses as one in the middle does (`doubleTapSide()`).

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
With no bar along the foot - none at all, or up in the landscape top row - both stand at the very
foot (`ViewerSystemBars.footInset()`). There is no scrim behind the controls: the frames sit on the
video as the thumbnails sit on a photo, the time keeping a text shadow and play and pause a dark disc.

With "Show the video's frames in its progress bar" off (`videoFrameStrip`), the scrubber draws a
plain line with a round handle across the middle of the same track instead, and reads no frames
(`showsFrames`). It keeps its size, so the controls stand where they do over the frames, and the
landscape row is laid out the same.

In the viewers' landscape layout the time, play and pause and the toggles share the frames' row,
before and after them, rather than standing in a row of their own over it. `VideoControlsLayout` moves
the same views between the two arrangements and puts each back where it came from, so portrait keeps
its layout exactly.
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
  takes the bar's height from `getBottomActionsHeight()`, less `viewer_strip_drop_into_actions`; a
  change to how tall `bottom_actions.xml` lays out moves the thumbnail strip, and the frames sit off
  it until that function follows. Whether the
  bar is along the foot at all is the host's to say (`isBottomActionBarAtFoot()`), the bottom actions
  setting included: the peek has no bar but keeps that room for its strip, whatever the setting says.
- **Nothing sharing the frames' row in landscape may change width while the video plays**: the
  scrubber reads its frames again for every width it is drawn at. The time is in tabular figures and
  kept as wide as the longest it says, the speed as wide as its widest, and the controls that only come
  with playing hold their room (INVISIBLE, not GONE) until it starts - see `VideoControlsLayout`.
- **The strips' visibility goes through `updateSideScrolls()`**, which knows the settings and whether
  the video has started: shown on a video not yet started, they take the flick down that closes it.
  Gone until then, they miss the first layout, so `MediaSideScroll` works a drag out against its
  height at the time: measured at the first layout it was 0, and every drag went to 0 or 100%.
- **Loop is the app's own setting**, so a page already started reads it again whenever it is shown
  (`updateLoop()`): toggled on one page, another would keep its old state and repeat mode.
- **The hints hang off `video_seek_hint_anchor`**, a zero-size view in the middle, not off the play
  button: that is gone once the video has started, and a RelativeLayout rule naming a gone view is
  dropped.
- Frame loads are told apart by `loadId`, not by key: a load given up and started again at the same
  size has the same key, and the old one's late frames would land in the new one's cells.
