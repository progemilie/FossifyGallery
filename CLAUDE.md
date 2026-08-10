# CLAUDE.md

## Project overview

Fossify Gallery is a privacy-focused Android photo/video gallery app (Kotlin, single `:app`
Gradle module). This repo is a fork.

## Build system

- Single app module `:app` (see settings.gradle.kts, `rootProject.name = "Gallery"`).
- **Fork version:** `FORK_VERSION_NAME` in gradle.properties tracks this fork independently of
  upstream's `VERSION_NAME`/`VERSION_CODE` (which stay as inherited from upstream).
  **Bump it in the same commit as every feature (minor) and every fix (patch), but only on a per branch basis (maximum one bump per working branch)**.
  Changes that cannot affect the build — docs, CI config, comments — need neither a bump nor a tag.
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

adb is added to path. It can be used to test in an android emulator.
The device being emulated is emulator-5554, it is a Pixel_10, API version 37, resolution 1080x2424, density 420dpi
The device has many folders with many pictures in the /Pictures folder.

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

### Per-folder custom media order

Sort-by-custom for media (upstream has it for folders only). The `media_order` Room table holds the
arranged paths keyed by lowercased folder path — kept out of the media table because media rows are
dropped and reinserted on every rescan. `Config.customMediaOrderFolders` is only an *index* of which
folders have an order, so `hasCustomMediaOrder()` can be answered on the main thread where Room
would throw; the table is the authority. Access via `extensions/Context.kt`
(`saveCustomMediaOrder`/`getCustomMediaOrder`/`removeCustomMediaOrder`) — all blocking, all off the
main thread.

Reordering lives in `adapters/MediaReorderMode.kt`, which drives `MediaAdapter` rather than living
inside it, and is put up by `MediaActivity` through `helpers/ReorderBar.kt`: multi-select marks a
group and dragging any marked item carries the whole group. Orders export/import as plain text via
`helpers/CustomOrderIO.kt`.

### The viewer's bottom action bar

`helpers/BottomAction.kt` is the one table of bit, view id, label and icon that both the bar and
`ManageBottomActionsDialog` read; an action added there is picked up by both, and
`parseBottomActionsOrder()` appends whatever a stored order predates rather than dropping it.

`applyBottomActionsOrder()` rebuilds bottom_actions.xml's horizontal chain rather than reordering
children — the chain is what spreads the buttons and what skips the GONE ones.

### Choosers held open over a bottom action button

Two buttons answer a hold with a picker the finger drags through without ever lifting off, and a tap
with the dialog they always had: rating (`views/RatingChooser.kt`) and copy/move
(`views/FolderChooser.kt`). Both share the `chooser_*` dimens and are `views/HoldChooser.kt`s, which
is a `GlassPanel` plus the gesture: `View.holdToChoose()` is what puts one on a button, so a screen
only says what fills the chooser and what to do with the choice.

`revealOver()` lays a chooser out **INVISIBLE** and only makes it VISIBLE once it has been
positioned over the button.

The list is **prefetched** into `mQuickChooserFolders` on every media change: it reads Room and the
filesystem, far too slow to run when the hold fires. Past destinations live in
`Config.recentCopyMoveDestinations`, recorded in `copyMoveFilesToFolder()`

### The viewer's file metadata sheet

A swipe up over the media in the viewer raises `views/MetadataSheet.kt`, listing every metadata group the file carries.

- `helpers/MetadataReader.kt` reads it, off the main thread, **straight off the file every time** —
  never from Room, MediaStore or the `Medium` the grid was built from, all of which describe the
  last scan rather than the file as it is now. `com.drewnoakes:metadata-extractor` supplies one
  directory per group the file stores (JPEG, Exif IFD0, Exif SubIFD, GPS, IPTC, XMP, ICC, PNG-\*,
  QuickTime, MP4, …) and those become the collapsible sections verbatim, so nothing is dropped for
  want of a hand-written mapping. `ExifInterface` fills in for formats the library cannot parse;
  `MediaMetadataRetriever` and `MediaExtractor` cover video.
- `helpers/MetadataSummary.kt` picks the pinned rows; `helpers/MetadataFormat.kt` holds the pure
  value formatting; `views/MetadataRows.kt` inflates them. Splitting those out is what keeps each
  file under detekt's function-count threshold — check `./gradlew detekt` before folding them back
  together.
- `MetadataSheet.attachTo()` is the whole of a viewer's wiring: the map row, the back gesture, and
  `BaseViewerActivity.updateNavigationBarIconsForPanel()`, which hands the navigation bar back its
  normal icons while the sheet covers it — the viewer otherwise forces light icons, which vanish
  against a light-theme sheet.

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
- **Frosted glass** — the search pill and both choosers are the same material: `helpers/Glass.kt`
  holds every colour and radius, `views/GlassPanel.kt` is the `BlurView` that wears it. A panel is
  told what to copy with `frost(contentBehind)`, which need not be an ancestor, and paints itself
  flat where the platform has no cheap blur (below Android 12).

## Style

Keep code comments CONCISE and NOT TOO LONG. Comments dont need to explain small UI details.
Comment things that are not obvious and might raise questions otherwise. Longer comments are
warranted when it is not immediatelly evident what the purpose of something is.

DO NOT make insignificant updates to CLAUDE.md file. Only for large features that change core functionality.
A UI feature does not need a large block of text in the CLAUDE.md file. Try to keep the file less than 200 lines.
