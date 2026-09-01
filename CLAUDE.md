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
- One flavor dimension, `licensing`: `foss` (F-Droid/IzzyOnDroid) and `gplay` (Google Play). No
  flavor-specific Kotlin — the differences are resource-only booleans.
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

There is no `app/src/test` or `app/src/androidTest` yet, so `testFossDebugUnitTest` runs nothing.
Lint and Detekt use baseline files to suppress pre-existing issues — new code should not add new
findings. `.editorconfig` enforces LF, 4-space indent, 160-char max line length. `/build` and
`/check` in `.claude/commands/` wrap the two loops these are usually run as.

### Driving the emulator

`python .claude/tools/emu.py <cmd>` is the way in — every subcommand takes `--help`, and each prints
a digest rather than a dump, which is most of what a session costs. It drives adb from Python with
list arguments, so device paths never pass through Git Bash and need no `MSYS_NO_PATHCONV`.

- `launch --fresh` force-stops, launches and waits for the window to hold still, printing the
  activity it settled on; `--clear` wipes data and re-grants the media permissions. `focus`, `idle`
  and `stop` are those pieces on their own.
- `ui` prints one line per named node — `id  text  [left,top][right,bottom]  Class` — so the raw
  uiautomator XML is never read. `--filter x` narrows it, `--clickable` keeps only what is tappable.
- `tap <resource-id|visible text|x,y>` looks the bounds up itself and taps the centre, so no
  coordinate is worked out by hand; it takes the first of several matches and says how many it saw.
  `--settle` waits for the screen that follows. `find` does the same lookup without tapping.
- `shot` writes a half-size PNG and **prints the path to Read** — about 870 tokens against 2,400 for
  `--full`. `--crop id=<view>` costs far less again (a top bar is ~110) and is the right choice
  whenever the question is about one part of the screen.
- `film --tap <target> --frames 8` taps and *then* films, tiling the frames into one labelled sheet:
  a whole transition for ~550 tokens. A grab costs ~250ms, so slow the animation with `anim 10`
  rather than asking for a shorter interval; `anim 1` puts it back.
- `prefs get [key]` / `prefs set k=v` read and write `Prefs.xml` through `run-as`. A set force-stops
  the app first — it rewrites its prefs as it exits — and fails loudly if the write did not stick.
- `logcat --since-launch` shows crashes only, unless given `--tag MetaDbg:D` or `--grep`.
- `install [--build]` assembles and installs, printing only the `e:` lines when a build fails.
  Also `swipe --dir`, `key`, `text`, `push --scan`, `rotate`, `devices`.

Two facts the script already knows, worth knowing anyway: the launcher entry is a per-theme
`activity-alias` (`SplashActivity.Pink`, `.Red`, …), so **`am start -n …/SplashActivity` silently
does nothing** — a launch goes through the launcher category; and the APK filename carries
`FORK_VERSION_NAME`, so match `app/build/outputs/apk/foss/debug/*.apk` rather than naming it.

**Where the script does not reach, use the tools directly** — it is a convenience, not a wall.
`adb`, `emulator` and `python` (3.13.1, with Pillow) are all on PATH, and a raw `adb shell` naming
a device path wants `MSYS_NO_PATHCONV=1` under Git Bash, which is the trap `emu.py` exists to avoid.
Anything worth doing twice belongs in the script. The device is emulator-5554, a Pixel_10, API 37,
1080x2424 at 420dpi, with many folders of pictures under /Pictures; `emulator -avd Pixel_10` boots
it when nothing is attached.

### Shipping a change

Commit subjects are `feat:`/`fix:`/`tweak:`/`refactor:`/`docs:` and then lowercase prose. A commit
that bumps `FORK_VERSION_NAME` is tagged `v<version>`, and PRs go to **`dev`**, not `main`.
`FORK-CHANGELOG.md` takes a `## [v1.19.0] - YYYY.MM.DD — two or three words` heading plus a link
definition at the foot pointing at that tag's release; it is written as the last commit before a
PR. `/ship` walks all of that in order.

## Architecture

Feature-level detail lives in `.claude/docs/architecture.md`; what is kept here is the shape of
the app and the rules that break silently when missed.

### Fossify Commons dependency

Most base classes, shared dialogs and extension functions come from the external
`org.fossify:commons` library, not this repo: `SimpleActivity` extends its `BaseSimpleActivity` and
nearly every screen extends `SimpleActivity`, `helpers/Config.kt` extends its `BaseConfig`, and its
dialogs and `org.fossify.commons.extensions.*` are used rather than reimplemented.

**Fork features drive the upstream classes from outside wherever that is reasonable** — see
`FolderDragMode`, `FolderGroupActions` and `MediaReorderMode`, each of which drives an upstream
adapter rather than living inside it.

### Package layout (app/src/main/kotlin/org/fossify/gallery/)

Conventional `activities/adapters/fragments/dialogs/helpers/models/views/`. Browsing is
`MainActivity` (both top level grids, see below), `MediaActivity` (a folder opened as a screen of
its own) and `ViewPagerActivity` (fullscreen viewer); `helpers/MediaFetcher.kt` is the MediaStore
query engine and `databases/GalleryDatabase.kt` the single Room DB (manual migrations, v4→v12).

No formal MVVM/MVP — activity/fragment plus base-class inheritance, with view binding enabled and
state held in activities/adapters/`Config` rather than ViewModels.

### Media loading

Both Glide and Picasso are used deliberately for different jobs:
- **Glide** (with custom modules for SVG, WebP, AVIF, APNG, JPEG XL) drives thumbnail grids;
  gallery-specific behavior lives in `helpers/MyGlideImageDecoder.kt` and `extensions/Glide.kt`.
- **Picasso** + `subsamplingscaleimageview`/`gestureviews`/`androidphotofilters` power the
  fullscreen zoomable photo viewer, with custom pieces in `helpers/PicassoRegionDecoder.kt`
  and `helpers/PicassoRoundedCornersTransformation.kt`/`RotateTransformation.kt`.
- Video playback uses `androidx.media3.exoplayer`.

Nothing small is decoded from a whole photo if the photo carries a copy of itself: anything drawing
a thumbnail loads a `ThumbnailSource`, and `helpers/ExifThumbnailLoader.kt` swaps in the file's
embedded copy when it is big enough. Worth it because `inSampleSize` saves the inverse transform but
not the pass over the entropy-coded data — a 12MP JPEG costs the same ~37ms at any size, against
~3ms for the 512x384 copy inside it. The loader gives that copy an Exif header carrying the photo's
own orientation, or a rotated photo faces different ways in the grid and the viewer.

Every Glide thumbnail goes through `loadImageBase()`, which is also where the WebP decoder is held
to the safe path (CVE-2023-4863) — except the zoomed-out media grid, which has its own loader below.
Both hand their prepared request to `helpers/ThumbnailPrefetcher.kt`, which decodes what the media
grid is scrolling towards — about a screenful ahead of the finger against a quarter of one behind.
**A preload must describe the picture exactly as the bind that follows it does** — model, signature,
`override()` size, transform and format are all cache key — or the grid decodes everything twice, so
the request is shared rather than restated.

Cache keys everywhere are derived from path + last-modified + size (`Medium.getSignature()`,
`Directory.getKey()`). **Anything that edits a file in place must call
`TransformedMedia.onTransformed(path)`** before touching caches — it bumps a per-path version folded
into both keys, plus a global generation counter screens use to decide whether to rebind stale
bitmaps. An edit leaving size and timestamp unchanged is otherwise invisible to every cache.

### Fork features, and the rules they impose

Each is explained in `.claude/docs/architecture.md` — read that section before working in the area.
What is listed here is only what breaks *silently* when it is missed.

- **The zoomed-out media grid** — the grid's source is `gridSource()`, never `mMedia`; a screen with
  no pinch of its own reads `interactiveMediaColumnCnt()`, not `Config.mediaColumnCnt`; and
  `interactiveMax` is a boundary rather than a rung, so anything naming a tappable count wants
  `largestInteractive`. A preload must describe the picture exactly as the bind that follows it does.
- **Per-folder custom media order** — the `media_order` table is the authority;
  `Config.customMediaOrderFolders` is only an index, so the main thread can answer
  `hasCustomMediaOrder()` where Room would throw.
- **Two grids, one window** — the bar belongs to whichever pane is up, which `updateTopBarForGroup()`
  checks first; a re-inflated menu has to be recoloured or its icons draw invisibly; a swap moves
  panes by `translationX`, so anything waiting on a layout pass has to be called outright.
- **Folder groups** — a group tile never reaches Room or the scan (`expandFolderGroups()` is the
  gate); ids are never reused; and while a group is open, anything re-scanning or re-sorting works
  from `mDirsIgnoringSearch`, never from what the adapter holds.
- **Order & groups export** — import drops anything naming a file or folder that is not there, and
  any section left empty by that; the sentinel folders are exempt, nothing can stat them.
- **The viewer's bottom action bar** — `helpers/BottomAction.kt` is the one table both the bar and
  `ManageBottomActionsDialog` read; `applyBottomActionsOrder()` rebuilds the chain rather than
  reordering children.
- **Choosers held open over a button** — `revealOver()` lays one out INVISIBLE and shows it only once
  positioned; the folder list is prefetched into `mQuickChooserFolders`, far too slow to build when
  the hold fires.
- **The three dots' drop-down** — a `MenuSpec` only arranges: anything it fails to name is appended
  to the last shown section rather than dropped, so no action goes missing by being forgotten there.
- **The metadata sheet** — `MetadataReader` reads straight off the file every time, never from Room,
  MediaStore or the `Medium` the grid was built from; removal copies the file block by block and
  never re-encodes.
- **Growing a tile into the viewer** — a flight is drawn with the photo's own picture, never the
  tile's, and needs all of: the translucent theme *and* `Window.setFormat(TRANSLUCENT)`, no custom
  animation in `ActivityOptions`, and the exit tile looked up on every page change. Miss one and the
  photo grows out of a black screen.
- **Chrome that floats over the content** — `keepGridClearOfTopBar()` pads the grid by the bar's
  measured height, which already carries the status bar inset; doing it in the layout double-counts.
  Every glass panel comes and goes through `PanelAnim`'s `showPanel`/`hidePanel`.

## Style

Keep code comments CONCISE and NOT TOO LONG. Comments dont need to explain small UI details.
Comment things that are not obvious and might raise questions otherwise. Longer comments are
warranted when it is not immediatelly evident what the purpose of something is.

DO NOT make insignificant updates to CLAUDE.md file. Only for large features that change core functionality.
A UI feature does not need a large block of text in the CLAUDE.md file. Try to keep the file less than 200 lines.

`FORK-CHANGELOG.md` records user-facing changes after a version bump — fixes only when major.
See *Shipping a change* above for how a version, a tag and a PR go out together.
