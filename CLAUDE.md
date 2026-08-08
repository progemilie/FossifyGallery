# CLAUDE.md

## Project overview

Fossify Gallery is a privacy-focused Android photo/video gallery app (Kotlin, single `:app`
Gradle module). This repo is a fork — see "Fork-specific features" for what differs from
upstream.

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
  decoders and transformations, `MyWidgetProvider`.
- `interfaces/` — Room DAOs and listener interfaces.
- `models/` — Room entities (`Directory`, `Medium`, `Widget`, `DateTaken`, `Favorite`,
  `MediaOrder`) and POJOs.
- `databases/GalleryDatabase.kt` — single Room DB, singleton via `getInstance(context)`,
  manual `Migration` objects (currently v4→v12).
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
- Video playback uses `androidx.media3.exoplayer`.

Cache keys everywhere are derived from path + last-modified + size (`Medium.getSignature()`,
`Directory.getKey()`). **Anything that edits a file in place must call
`TransformedMedia.onTransformed(path)`** (`helpers/TransformedMedia.kt`) before touching caches —
it bumps a per-path version folded into both keys, plus a global generation counter screens use to
decide whether to rebind stale bitmaps. An edit that leaves size and timestamp unchanged is
otherwise invisible to every cache in the app.

## Fork-specific features

These do not exist upstream. Each is listed with the invariant that is easy to break; details
live in the code.

### Lossless mirror (horizontal flip)

Flips an image by rewriting only its Exif orientation (`extensions/Activity.kt`,
`extensions/ExifUtils.kt`); non-JPEGs fall back to re-encoding the bitmap. Restore the
last-modified date (`fileTransformedSuccessfully`) *before* `rescanPaths`, or MediaStore records
the write's timestamp instead. `RotateTransformation`/`MyGlideImageDecoder` take an `isFlipped`
flag because SubsamplingScaleImageView applies Exif orientation itself — mirror first, then
rotate; the two don't commute.

### Star ratings

A 0–5 rating per photo, stored in the file's XMP packet (`xmp:Rating`, plus `MicrosoftPhoto:Rating`
when already present) so Aves/Lightroom/digiKam/Windows read it too. `helpers/XmpRating.kt` reads by
regex and writes by DOM, forcing ASCII output; androidx `ExifInterface` is pinned to 1.4.2 because
XMP writing arrived in 1.4.0. Only jpg/png/webp are writable (`String.canBeRated()`).

`updateFileRating` restores the last-modified date **unconditionally** (not gated on
`config.keepLastModified`) — rating a photo is not a change to the photo, and re-dating it
reshuffles every date-sorted grid. It deliberately does *not* call `TransformedMedia.onTransformed`:
the container changes, no pixel does.

Ratings are cached in a `media_ratings` Room table keyed by lowercased path, staleness-checked by
last-modified + size, with a denormalized `media.rating` column. `MediaFetcher.RatingScan` is the
only thing that opens files, and only when `Config.showThumbnailRating` is on or the folder sorts by
rating. `SORT_BY_RATING` forces `GROUP_BY_RATING` rather than offering a separate Group by entry.

### Per-folder custom media order

Sort-by-custom for media (upstream has it for folders only). The `media_order` Room table holds the
arranged paths keyed by lowercased folder path — kept out of the media table because media rows are
dropped and reinserted on every rescan. `Config.customMediaOrderFolders` is only an *index* of which
folders have an order, so `hasCustomMediaOrder()` can be answered on the main thread where Room
would throw; the table is the authority. Access via `extensions/Context.kt`
(`saveCustomMediaOrder`/`getCustomMediaOrder`/`removeCustomMediaOrder`) — all blocking, all off the
main thread.

Reordering is a mode of `MediaAdapter` driven by `MediaActivity`'s reorder bar: multi-select marks a
group and dragging any marked item carries the whole group. Orders export/import as plain text via
`helpers/CustomOrderIO.kt`.

### Stable ordering and grid churn

Several sorts gained a `path` tiebreak (`getSortedDirectories`, `MediaFetcher.sortMedia`, custom
folder order) — without it, items tying on the sort key kept MediaStore cursor order, which differs
per scan, so items jumped around and album covers swapped for no reason.
`MainActivity.setupAdapterThrottled` rate-limits mid-scan grid pushes to 500 ms (each is a
`notifyDataSetChanged()` that restarts every visible cover's image request); callers must still end
with a plain `setupAdapter(dirs)`. Rescan cleanup compares by path in a `HashSet`, not by whole
item.

### Chrome that floats over the content

The three browsing screens draw content edge to edge with the chrome over it. No immersive mode is
involved — commons' `EdgeToEdgeActivity` already enables edge to edge; what changed is that the app
no longer paints an opaque band under its own bar.

- **Viewer** — the toolbar shows `viewer_header.xml` (file name plus extended details) instead of a
  title, so the existing fullscreen fade takes it away with the rest of the chrome. Details are
  built by `extensions/ExtendedDetails.kt` off the main thread and dropped if the user has swiped
  on. `BaseViewerActivity` forces light system-bar icons — the viewer's chrome is white over the
  photo in either theme.
- **Grids** — `MySearchMenu` is the *last* child of the `CoordinatorLayout` (draw order is what puts
  it over the grid) and the grid gets no top inset of its own; `keepGridClearOfTopBar()` pads it by
  the bar's measured height, which already carries the status bar inset. Doing it in the layout
  instead double-counts the inset.
- `helpers/FloatingTopBar.kt` strips the app bar's background/elevation, blurs the search pill on
  Android 12+, and doubles as the scroll listener that pans the bar away mid-drag and brings it back
  whenever the grid comes to rest. `makeFloating()` must run *after* every
  `MySearchMenu.updateColors()`, which repaints the band on each resume.
- `extensions/EdgeFade.kt` fades both ends of each grid so the system bars stay readable over
  scrolling content.

### Viewer thumbnail strip

A row of thumbnails between the photo and the bottom actions, ported from Aves' `ThumbnailScroller`.
Whatever sits in the middle of `views/ViewerThumbnailStrip.kt` is what the pager shows, and it says
so on every scroll frame rather than waiting to settle, so the photo keeps up with the strip;
`onPageSelected` scrolls the strip back the other way. The two are one position, so both directions
have to agree on where "the middle" is. That middle is created by padding the strip by half its own
width at either end (set in `onMeasure`, so it survives rotation) rather than by any offset of its
own, which is also what a plain `LinearSnapHelper` and `SNAP_TO_START` measure against.

`CenteringScroller` overrides `onTargetFound` to floor how long that move takes. A `LinearSmoothScroller`
times itself by distance, and the distance for the usual case — one photo swiped, one thumbnail to
move — is about a frame and a half, which arrives before it can be seen to leave.

How big and how shaded each thumbnail is drawn comes from its own distance to that middle, applied
to the children in `updateChildDecorations` on every scroll frame. It is deliberately not a selected
position handed to the adapter: that change lands a frame later and then animates from there, and the
highlight visibly trails the thumbnails it is marking. The adapter therefore only loads images.

The strip and the bottom actions stack inside `bottom_chrome`, and it is that wrapper the fullscreen
fade animates — each child keeps whatever visibility its own setting gave it. With the buttons turned
off the strip becomes the bottom of the screen and takes the navigation bar inset as a margin itself.

Aves' fling coasts for seconds; `StripSnapHelper` keeps four fifths of the velocity (fling distance
grows with about v^1.74, so that is about two thirds of the travel) and settles at 60ms/inch.
Thumbnails load RGB_565 at strip size with a quarter-size pass first, and an unloaded cell shows
`viewer_strip_placeholder`, not the grid's black one — over a photo, black is a hole. `PhotoFragment`
gained the same idea: `buildLowResRequest` decodes a 320px version alongside the full one, so
arriving at an unread photo is a soft version of it rather than a black screen. Crossing photos this
fast is also why `updateHeaderDetails` is posted with a delay — it opens the file, which is only
worth doing for the photo still on screen once the scrolling pauses.
