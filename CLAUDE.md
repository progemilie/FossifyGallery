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

### Smaller behavior changes

- `ViewPagerActivity.finish()` returns the current path; `MediaActivity` scrolls to it and pulses
  an accent border on the thumbnail (`MediaAdapter.revealItem`, `HIGHLIGHT_*` constants).
- Extended details default per build type — `Config.showExtendedDetails` defaults to
  `BuildConfig.DEBUG`, and `DEFAULT_EXTENDED_DETAILS`/`DEBUG_EXTENDED_DETAILS` pick the fields.
  New `EXT_ORIENTATION` field renders via `ExifInterface.getReadableOrientation`.