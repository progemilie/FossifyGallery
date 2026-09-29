# The video player

A video in the viewer plays in place, under controls of the fork's own: play and pause on a dark
disc in the middle, with ten seconds either way beside it once the video has started; the time,
speed, loop and sound along the foot; and a strip of the video's own frames as its progress bar,
where a photo has the thumbnail strip. The chrome goes by itself once a video has played three
seconds untouched, and comes back when it ends. "Open videos on a separate screen" still hands the
video to `VideoPlayerActivity`, which keeps upstream's controls, and its page keeps the thumbnail
strip.

## Coupled with

Read these before changing this feature — each can break silently if this one changes without it.

- [thumbnail-strip](thumbnail-strip.md) — the strip steps aside on a video's page, and the scrubber takes its place.

## Where it lives

| File | Job |
|---|---|
| `fragments/VideoFragment.kt` | The player: ExoPlayer, the gestures, the controls' wiring |
| `views/VideoScrubber.kt` | The progress bar as a strip of frames, a `SeekBar` underneath |
| `views/VideoPlayerParts.kt` | `SeekHints` (the "+ 20s"), `RewindScan` (a held rewind), `ChromeAutoHide` |
| `layout/video_controls.xml` | Time, toggles and scrubber; `VideoPlayerActivity` keeps `bottom_video_time_holder.xml` |
| `layout/pager_video_item.xml` | The transport discs in the middle, the seek hints above them |
| `ViewPagerActivity.updateThumbnailStrip()` | Puts the thumbnail strip away on a video's page |

## Gestures

The video is taken in thirds. A hold on the left third winds back at twice the speed it plays, a
hold anywhere else plays at 2x (upstream's). A double tap on a side third skips ten seconds that
way, and in the middle plays or pauses. A run of skips counts up in a hint over that side's skip
button — the same place whether it came from a double tap or the button. Holds start only on a
playing video, as upstream's 2x hold did.

ExoPlayer plays nothing backwards, so `RewindScan` seeks, every 100 ms, to wherever the clock says
the rewind has got to: a slow decoder shows fewer frames rather than a slower rewind.

## The scrubber

`VideoScrubber` is a `SeekBar` with its drawables taken away and its track drawn as frames, so a
drag, a tap, keys and TalkBack work as on any seek bar. Frames ahead of the playhead are shaded.
It reads as many frames as fill the track at the video's own proportions (held to 0.5–1.8, at most
24), shows each as it comes, and caches them by path, signature and track size: a rotation reads
again for the new width, a return to the page does not.

A cell's frame is the nearest keyframe — cheap, and within half a second of the exact one in a
phone's video. Only a keyframe the same as the cell before's (keyframes further apart than the
cells, as in a screen recording) is decoded on from to the exact frame.

## What breaks silently

- **Seeks in a stream need scrubbing mode.** A drag and a held rewind turn on
  `ExoPlayer.isScrubbingModeEnabled` for as long as they last; without it each seek cancels the last
  before it has drawn, and on a video with keyframes far apart the picture stands still until the
  finger lifts. Left on, it keeps playback suppressed.
- **The thumbnail strip's visibility goes through `updateThumbnailStrip()`**, which knows a video's
  page; set straight from the setting, the strip sits over the scrubber.
- **The hints hang off `video_transport_anchor`**, a zero-size view in the middle, not off the
  transport: that is gone while an error shows, and a RelativeLayout rule naming a gone view is
  dropped.
- Frame loads are told apart by `loadId`, not by key: a load given up and started again at the same
  size has the same key, and the old one's late frames would land in the new one's cells.
- `VideoScrubber` sits at detekt's function-count threshold; the frames' fade is an arrival time per
  cell read in `onDraw` for that reason.
