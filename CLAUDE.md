# CLAUDE.md

## Project overview

Fossify Gallery is a privacy-focused Android photo/video gallery app (Kotlin, single `:app`
Gradle module). This repo is a fork.

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

adb can be used to test the app in an emulator.

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
- That top padding breaks drag-to-reorder unless it is compensated for:
  `LinearLayoutManager.prepareForDrop()`, which the stock `onMoved()` calls to hold the swapped-with
  item still, feeds its current top to `scrollToPositionWithOffset()` — and that measures from
  *below* the padding, so every swap shoves the list down by it. Both grids therefore drag through
  `helpers/PaddedGridMoveCallback.kt` rather than commons' `ItemMoveCallback` directly.
- `helpers/FloatingTopBar.kt` strips the app bar's background/elevation, blurs the search pill on
  Android 12+, and doubles as the scroll listener that pans the bar away mid-drag and brings it back
  whenever the grid comes to rest. `makeFloating()` must run *after* every
  `MySearchMenu.updateColors()`, which repaints the band on each resume.
- `extensions/EdgeFade.kt` fades both ends of each grid so the system bars stay readable over
  scrolling content.
