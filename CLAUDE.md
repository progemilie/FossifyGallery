# CLAUDE.md

## Project overview

Fossify Gallery is a privacy-focused Android photo/video gallery app (Kotlin, single `:app`
Gradle module).

## Build system

- Single app module `:app` (see settings.gradle.kts, `rootProject.name = "Gallery"`).
- Kotlin 2.3.10, AGP 9.2.0, Gradle wrapper 9.4.1, KSP 2.3.7, Java/Kotlin target 17.
- compileSdk/targetSdk 36, minSdk 26 (see gradle/libs.versions.toml).
- One flavor dimension, `licensing`, with two flavors and no flavor-specific Kotlin code —
  differences are resource-only booleans (donate/Google-ties visibility):
  - `foss` — F-Droid/IzzyOnDroid build
  - `gplay` — Google Play build
- Build types: `debug` (`.debug` application id suffix) and `release` (minified, proguard).
- Signing: via `keystore.properties` (see `keystore.properties_sample`) or
  `SIGNING_KEY_ALIAS`/`SIGNING_KEY_PASSWORD`/`SIGNING_STORE_FILE`/`SIGNING_STORE_PASSWORD`
  env vars. Without either, release builds are built unsigned.

### Common commands

```
./gradlew assembleFossDebug      # debug build, F-Droid flavor
./gradlew assembleGplayDebug     # debug build, Google Play flavor
./gradlew assembleFossRelease    # release build (needs signing config)
./gradlew testFossDebugUnitTest  # unit tests — CI's actual task (see .github/workflows/pr.yml)
./gradlew detekt                 # static analysis (config: detekt.yml, baseline: app/detekt-baseline.xml)
./gradlew lint                   # Android Lint (config: lint.xml, baseline: app/lint-baseline.xml)
```

Note: there is currently no `app/src/test` or `app/src/androidTest` directory in this repo,
so `testFossDebugUnitTest` has no tests to execute yet.

Lint and Detekt both use baseline files to suppress pre-existing issues — new code should not
add new findings. `.editorconfig` enforces LF line endings, 4-space indent, 160-char max line
length.

## Architecture

### Fossify Commons dependency

Most base classes, shared dialogs, and extension functions come from the external
`org.fossify:commons` library (`libs.fossify.commons` in app/build.gradle.kts), not from this
repo. In particular:
- `SimpleActivity` (app/src/main/kotlin/org/fossify/gallery/activities/SimpleActivity.kt)
  extends commons' `BaseSimpleActivity`; nearly every activity in the app extends
  `SimpleActivity` (directly or via `BaseViewerActivity`).
- `helpers/Config.kt` extends commons' `BaseConfig` for SharedPreferences-backed settings.
- Shared dialogs (e.g. `FilePickerDialog`) and extensions (`org.fossify.commons.extensions.*`)
  are used throughout rather than reimplemented here.

When something looks unimplemented in this repo, check whether it lives in Fossify Commons
before adding it.

### Package layout (app/src/main/kotlin/org/fossify/gallery/)

- `activities/` — screens: `MainActivity` (folder grid), `MediaActivity` (media grid),
  `BaseViewerActivity`/`ViewPagerActivity`/`PhotoActivity`/`VideoActivity` (fullscreen
  swipeable viewer), `EditActivity`/`BaseCropActivity` (photo editor), `SettingsActivity`,
  `SearchActivity`, folder-management activities, `WidgetConfigureActivity`.
- `adapters/` — RecyclerView adapters (`DirectoryAdapter`, `MediaAdapter`, `MyPagerAdapter`, ...).
- `fragments/` — `ViewPagerFragment` base, `PhotoFragment`, `VideoFragment`.
- `dialogs/` — ~20 AlertDialog wrappers (sort/group/filter, resize, slideshow, ...).
- `helpers/` — `Config`, `MediaFetcher` (core MediaStore query engine), custom Glide/Picasso
  decoders and transformations, `MyWidgetProvider`, plus `TransformedMedia` and `CustomOrderIO`
  (see "Fork-specific features").
- `interfaces/` — Room DAOs and listener interfaces.
- `models/` — Room entities (`Directory`, `Medium`, `Widget`, `DateTaken`, `Favorite`,
  `MediaOrder`) and POJOs.
- `databases/GalleryDatabase.kt` — single Room DB, singleton via `getInstance(context)`,
  manual `Migration` objects (currently v4→v11).
- `asynctasks/GetMediaAsynctask.kt`, `jobs/NewPhotoFetcher.kt` — background media
  scanning/new-photo detection.
- `svg/` — Glide module/decoder for SVG support.
- `views/EditorDrawCanvas.kt` — draw-on-photo editor canvas.

No formal MVVM/MVP — this is activity/fragment + base-class inheritance, with view binding
enabled (`viewBinding = true`) and state held in activities/adapters/`Config` rather than
ViewModels.

### Media loading

Both Glide and Picasso are used deliberately for different jobs:
- **Glide** (with custom modules for SVG, WebP, AVIF, APNG, JPEG XL) drives thumbnail grids;
  gallery-specific behavior lives in `helpers/MyGlideImageDecoder.kt` and `extensions/Glide.kt`.
- **Picasso** + `subsamplingscaleimageview`/`gestureviews`/`androidphotofilters` power the
  fullscreen zoomable photo viewer, with custom pieces in `helpers/PicassoRegionDecoder.kt`
  and `helpers/PicassoRoundedCornersTransformation.kt`/`RotateTransformation.kt`.
- Video playback uses `androidx.media3.exoplayer`

## Fork-specific features

These do not exist upstream. Each one has a load-bearing invariant that is easy to break.

### Lossless mirror (horizontal flip)

`menu_mirror` in the viewer and the media-grid CAB flips an image by rewriting only its Exif
orientation (`extensions/Activity.kt`: `saveMirroredImageToFile`/`tryMirrorByExif`/
`saveImageMirror`, `extensions/ExifUtils.kt`: `mirroredOrientation`). Non-JPEGs fall back to
re-encoding the bitmap via `saveFile(..., flipHorizontal = true)`.

The catch: every cache key in the app is derived from path + last-modified + size
(`Medium.getSignature()`, `Directory.getKey()`), and an Exif-only rewrite changes none of them —
less still with "keep last modified" on. `helpers/TransformedMedia.kt` is the process-wide fix: it
bumps a per-path version appended to both cache keys, plus a global `generation` counter screens
compare against to decide whether to rebind stale bitmaps. **Anything that edits a file in place
must call `TransformedMedia.onTransformed(path)` before touching caches.** Restore the
last-modified date (`fileTransformedSuccessfully`, renamed from `fileRotatedSuccessfully`) *before*
`rescanPaths`, or MediaStore records the write's timestamp instead.

`RotateTransformation`/`MyGlideImageDecoder` take an `isFlipped` flag: SubsamplingScaleImageView
applies Exif orientation itself, so Glide's baked-in transform has to be undone — mirror first,
then rotate, since the two don't commute.

### Star ratings

A 0–5 rating per photo, ported from Aves. It lives in the file's XMP packet, not in the app:
`helpers/XmpRating.kt` reads and rewrites `xmp:Rating` (and keeps `MicrosoftPhoto:Rating` in step
when the file already had one), which is what Aves, Lightroom, digiKam and Windows all read.
androidx `ExifInterface` is pinned to 1.4.2 in the version catalog because XMP *writing* only
arrived in 1.4.0.

Three things about `XmpRating` are load-bearing:
- Reading is regex, writing is DOM. Reading runs once per file on a media scan, where building a
  DOM per photo would be the expensive part of the scan.
- Output is forced to ASCII (`OutputKeys.ENCODING = "US-ASCII"` onto a *stream*, not a Writer), so
  non-ASCII in someone else's XMP comes back as numeric character references. `ExifInterface` hands
  the packet over as a `String`, and that round trip is only lossless for ASCII.
- Setting 0 removes the property, and drops the whole packet when nothing else was in it. `apply`
  returns its input unchanged when nothing needs to change — that is how `extensions/Rating.kt`
  knows not to rewrite the file at all.

`updateFileRating` restores the file's last-modified date **unconditionally**, not gated on
`config.keepLastModified` like `fileTransformedSuccessfully` is: rating a photo is not a change to
the photo, and letting it re-date the file reshuffles every date-sorted grid. It deliberately does
*not* call `TransformedMedia.onTransformed` — writing XMP rewrites the container but not a pixel,
so every decoded bitmap still stands (unlike the mirror above). Only jpg/png/webp can be written at
all (`String.canBeRated()`); the viewer's star button hides itself for anything else.

Ratings are cached in a `media_ratings` Room table (migration 11→12) keyed by lowercased path, with
the file's last-modified and size as the staleness signature, plus a denormalized `media.rating`
column so media read back from the cache carry their badge immediately. `MediaFetcher.RatingScan`
is the only thing that opens files, and only when `Config.showThumbnailRating` is on or the folder
sorts by rating — otherwise a scan pays nothing. A rating of 0 is cached too; "no rating" is just
as expensive to work out again.

`SORT_BY_RATING` (1048576, past the last commons sorting) brings its own headers: `groupMedia`
forces `GROUP_BY_RATING` under it rather than offering a separate Group by entry, giving ★★★★★…★
and Unrated sections. Ties inside a rating fall back to newest first, after the descending sign
flip so reversing the ratings does not also flip the dates inside them.

The viewer's `bottom_rating` button is the Aves gesture: hold it and `views/RatingChooser.kt` pops
up over it, sliding left/right picks a rating without lifting off, and left of the first star
clears it. A plain tap opens `RateMediumDialog` instead. The button consumes all touches, so the
tap path goes through `performClick()` to keep accessibility working. The favourite button became a
heart (`ic_heart_outline_vector`/commons `ic_heart_vector`) to free the star for this.

### Per-folder custom media order

Sort-by-custom for media (upstream has it for folders only). Storage is split deliberately:

- `media_order` Room table (`models/MediaOrder.kt`, `interfaces/MediaOrderDao.kt`, migration
  10→11) holds the arranged paths, keyed by lowercased folder path. Kept out of the media table
  because media rows are dropped and reinserted on every rescan.
- `Config.customMediaOrderFolders` (a `StringSet` pref) is only an *index* of which folders have an
  order — it exists so `hasCustomMediaOrder()` can be answered on the main thread, where Room would
  throw. The table is the authority; the two drifting apart is handled in `getCustomMediaOrder`.

Access it through `extensions/Context.kt` (`saveCustomMediaOrder`/`getCustomMediaOrder`/
`removeCustomMediaOrder`) — all blocking, all off the main thread. `MediaFetcher.sortMedia` now
takes a `path` and routes `SORT_BY_CUSTOM` to `sortMediaByCustomOrder`; unknown media sorts to the
end by path. `groupMedia` force-disables grouping under custom sorting.

The reorder UI is a mode of `MediaAdapter` (`setReordering`, `ItemTouchHelperContract`, a
`NearestCellMoveCallback`) driven by `MediaActivity`'s reorder bar (`media_reorder_bar.xml`):
multi-select marks a group, dragging any marked item carries the whole group as one, and the drag
badge shows the carried count. While reordering, the toolbar menu is fully hidden and the grid's
bottom inset padding moves to the bar (`setupInsetPadding`). Orders are exported/imported as plain
text via `helpers/CustomOrderIO.kt` (`[folder]` sections, absolute paths under them) and wired into
Settings; import replaces only the folders the file names.

### Stable ordering and grid churn

Several sorts gained a `path` tiebreak (`getSortedDirectories`, `MediaFetcher.sortMedia`, custom
folder order). Without it, items tying on the sort key kept MediaStore cursor order, which differs
per scan — items jumped around the grid and album covers (simply the first item) swapped for no
reason. `MainActivity.setupAdapterThrottled` rate-limits mid-scan grid pushes to 500 ms, since each
one is a `notifyDataSetChanged()` that restarts every visible cover's image request; callers must
still end with a plain `setupAdapter(dirs)`. Rescan cleanup compares by path in a `HashSet`, not by
whole item, so a metadata-only refresh no longer races into deleting the row it just wrote.

### Chrome that floats over the content

The three browsing screens all put their content edge to edge and let the chrome sit over it. No
immersive/fullscreen mode is involved — commons' `EdgeToEdgeActivity` already calls
`WindowCompat.enableEdgeToEdge`, so the window has always drawn behind the system bars; what
changed is that the app no longer paints an opaque band under its own bar and no longer starts the
content below it.

**Viewer (`activity_medium.xml`, `fragment_holder.xml`).** The toolbar's title is gone, replaced by
an included `viewer_header.xml` — the file name, with the extended details wrapped underneath it
when `Config.showExtendedDetails` is on. Because the header lives *inside* the toolbar, the existing
fullscreen fade already takes it away with the rest of the chrome; nothing else has to know about
it. The details carry no `maxLines` and no ellipsis on purpose: which fields are on is the user's
choice under "Manage extended details", and cutting the last of them off would make that choice a
lie. The toolbar is `wrap_content` with `minHeight="?attr/actionBarSize"`, so it grows to whatever
is turned on — four lines with every field enabled.

The details themselves moved out of the fragments into `extensions/ExtendedDetails.kt`
(`Context.getMediumExtendedDetails`) — the activity owns the header now, and it already knows which
medium is showing and when a rating changed. `joinAsExtendedDetails` runs the fields together with
middle dots and makes the spaces *inside* a field unbreakable, so a line only ever ends between
fields rather than halfway through a date. `skipName = true` drops `EXT_NAME`, which the heading
above already says. Reading opens the file, so it runs off the main thread and the result is
dropped unless `getCurrentPath()` still matches the medium it was read for — a fast swipe starts
several of these.

The in-photo details panel (`photo_details`/`video_details`) and `ViewPagerFragment`'s copy of the
builder are gone with it, and so is the "hide extended details when fullscreen" setting: the
details ride the top bar now, which always hides. `Config.hideExtendedDetails` stays for settings
import/export. `menu_rotate` is `showAsAction="never"` — the top bar is the file's heading, not a
row of tools.

`BaseViewerActivity.onResume` forces light system-bar icons. The viewer's chrome is white over the
photo whichever theme the app is in, and dark status bar icons over a black backdrop are invisible.

**Grids (`activity_main.xml`, `activity_media.xml`).** `MySearchMenu` is now the *last* child of
the `CoordinatorLayout` rather than the first, and the content holder lost
`appbar_scrolling_view_behavior` — draw order is what puts the bar over the grid. The grid gets no
top inset of its own; `keepGridClearOfTopBar()` pads it by the bar's *measured* height, which
already carries the status bar inset, and re-runs off `FloatingTopBar.onHeightChanged` because a
rotation or an inset change moves it. Doing it in the layout instead would double up with the
inset, which is exactly what went wrong under `MainActivity`'s "switch to file search" link — that
link is laid out under the bar and the list under the link, so while it is up the list makes no room
of its own at all.

`helpers/FloatingTopBar.kt` does the rest. `makeFloating()` clears the app bar's background,
elevation and shadow, and must run *after* every `MySearchMenu.updateColors()`, which repaints the
band on each resume. Commons leaves the search pill at a quarter of the accent colour, which was
legible over a band of background colour and is not over a photo; on Android 12+ the pill instead
gets a `BlurView` inserted as its first child, sampling the grid holder and clipped to the pill's
own rounded outline, with the theme background over it at `PILL_TINT_ALPHA`. Below 12 there is no
hardware blur — BlurView's software path costs real frame time exactly while the grid is scrolling
— so `isSPlus()` gates it and older versions get a near-solid pill. Blur redraws are switched off
once the bar has finished panning off screen, which is the moment the grid is being scrolled
hardest.

`setFrameClearDrawable(SolidColorDrawable(...))` is what makes the pill frost *everything* under it
rather than only the photos, and it is not optional. BlurView clears its capture buffer to
transparent before drawing the grid into it, and the grid paints nothing of its own between the
thumbnails — so the capture comes back transparent over a date header, over the gaps between cells,
and over whatever part of the pill hangs past the end of a row. Blurring transparency leaves
transparency, and since the pill has no background of its own the real content then shows through
it sharp: section headers read straight over the search hint, and a thumbnail only half under the
bar looked half blurred. Starting every capture from the background the grid sits on fixes all
three. Note the drawable has to flood the canvas rather than fill its bounds — BlurView hands the
buffer over untransformed and never sets bounds, so a plain `ColorDrawable` paints nothing.

The same object is the grid's scroll listener. The two thresholds are deliberately lopsided: it
takes a real drag down (`top_bar_hide_threshold`) to pan the bar away, but only a nudge back up
(`top_bar_show_threshold`) to bring it in, and `onScrollStateChanged` brings it back whenever the
grid comes to rest — so the bar is only ever gone mid-gesture. Note `MySearchMenu.toggleHideOnScroll`
is a no-op in commons 6.1.6, which is why this is hand-rolled. Panning is switched off while the
grid scrolls sideways and while media is being reordered.

`extensions/EdgeFade.kt` softens both ends of each grid so the system bars stay readable over
whatever is scrolling past under them. The gradients are built at runtime rather than as drawables
because they are painted in `getProperBackgroundColor()` — white under a light theme, near black
under a dark one — and the clear end of the ramp is that same colour at zero alpha rather than
`Color.TRANSPARENT`, which is a transparent *black* and drags a grey cast through the middle of a
white fade. The top one is the weaker of the two and lives *inside* the holder so the blurred
backdrop behind the pill picks it up along with the grid; the bottom one sits over the holder and
is hidden while the reorder bar is up, because that bar brings its own background. Both are plain
non-clickable `View`s, so touches fall through to the grid behind them.

### Smaller behavior changes

- `ViewPagerActivity.finish()` returns the current path; `MediaActivity` scrolls to it and pulses
  an accent border on the thumbnail (`MediaAdapter.revealItem`, `HIGHLIGHT_*` constants).
- Extended details default per build type — `Config.showExtendedDetails` defaults to
  `BuildConfig.DEBUG`, and `DEFAULT_EXTENDED_DETAILS`/`DEBUG_EXTENDED_DETAILS` pick the fields.
  New `EXT_ORIENTATION` field renders via `ExifInterface.getReadableOrientation`.