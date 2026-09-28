# CLAUDE.md

## Project overview

Fossify Gallery is a privacy-focused Android photo/video gallery app (Kotlin, single `:app`
Gradle module). This repo is a fork.

## Build system

- Single app module `:app` (see settings.gradle.kts, `rootProject.name = "Gallery"`).
- **Fork version:** `FORK_VERSION_NAME` in gradle.properties tracks this fork independently of
  upstream's `VERSION_NAME`/`VERSION_CODE` (inherited, never touched).
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

Unit tests live in `app/src/test/kotlin` (JUnit 4, JVM only) and run with `testFossDebugUnitTest`.
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

### Measuring it

`python .claude/tools/perf.py <cmd>` says what costs time and what fires too often, measuring an
interaction given as `--swipes N`, `--seconds N`, or a command after `--`. `methods` prints per
method call counts (exact) and times (a ranking only — recording costs ~10x the runtime); `jank`
frame-time percentiles; `mem` memory with deltas, `--dump` for an hprof; `trace` a Perfetto trace;
`counters` the app's own `helpers/Perf.kt`, which costs nanoseconds and so leaves an interaction
behaving like itself. Studio opens every file it writes.

Three traps it already avoids, all of which fail silently: **`am profile start` given a pid returns
success and records nothing**, so profile by process name; `--clock-type dual` writes an empty file;
and back-to-back sessions come back either empty or, worse, missing every thread that was already
running. So `methods` checks the trace actually contains the main thread and repeats the interaction
when it does not - never start twice to force a non-empty file, which is what produces the quietly
partial one. Its 8 MB buffer holds about one fling. `Perf.count`/`section` are inline and gated on a build-type literal so calls fold
away in release — the class itself survives R8 only because proguard keeps all `org.fossify.**`.

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

## Architecture

`.claude/docs/architecture.md` describes the app's shape in more depth and indexes the fork features,
each explained in a file of its own under `.claude/docs/features/` — read a feature's file before
working in that area. What is kept here is only what cuts across features.

**Before editing a fork file, grep `.claude/docs/features` for its class name**, read every doc that
names it, and then the docs each one lists under "Coupled with" — those are the features that break
silently when this one changes without them.

### Fossify Commons dependency

Most base classes, shared dialogs and extension functions come from the external
`org.fossify:commons` library, not this repo: `SimpleActivity` extends its `BaseSimpleActivity` and
nearly every screen extends `SimpleActivity`, `helpers/Config.kt` extends its `BaseConfig`, and its
dialogs and `org.fossify.commons.extensions.*` are used rather than reimplemented.

**Fork features live in files of their own and drive upstream classes from outside** wherever that
is reasonable, leaving the upstream class only a hook — upstream is merged in, and this is what keeps
the conflicts small.

### Package layout (app/src/main/kotlin/org/fossify/gallery/)

Conventional `activities/adapters/fragments/dialogs/helpers/models/views/`. Browsing is
`MainActivity` (the folder grid and the all media grid, as two panes of one window),
`MediaActivity` (a folder opened as a screen of its own) and `ViewPagerActivity` (fullscreen
viewer); `helpers/MediaFetcher.kt` is the MediaStore query engine and `databases/GalleryDatabase.kt`
the single Room DB (manual migrations, v4→v12).

No formal MVVM/MVP — activity/fragment plus base-class inheritance, with view binding enabled and
state held in activities/adapters/`Config` rather than ViewModels. Screens hand each other lists and
bitmaps through process-wide statics rather than intents, since a folder's paths run past what a
binder transaction carries. Anything the main thread has to read lives in `Config`, where Room would
throw.

### Media loading

Both Glide and Picasso are used deliberately for different jobs:
- **Glide** (with custom modules for SVG, WebP, AVIF, APNG, JPEG XL) drives thumbnail grids;
  gallery-specific behavior lives in `helpers/MyGlideImageDecoder.kt` and `extensions/Glide.kt`.
- **Picasso** + `subsamplingscaleimageview`/`gestureviews`/`androidphotofilters` power the
  fullscreen zoomable photo viewer, with custom pieces in `helpers/PicassoRegionDecoder.kt`
  and `helpers/PicassoRoundedCornersTransformation.kt`/`RotateTransformation.kt`.
- Video playback uses `androidx.media3.exoplayer`.

Every thumbnail goes through one pipeline — a `ThumbnailSource` (the photo's embedded copy where it
is big enough), a decode size snapped to `ThumbnailSizes`, and the WebP decoder held to the safe path
(CVE-2023-4863). See `.claude/docs/features/thumbnails.md`.

### Rules that break silently

- **A preload must describe the picture exactly as the bind that follows it does** — model,
  signature, `override()` size, transform and format are all cache key — or the grid decodes
  everything twice, so the request is shared rather than restated.
- **Anything that edits a file in place must call `TransformedMedia.onTransformed(path)`** before
  touching caches. Every cache key is built from path + last-modified + size, so an edit leaving
  those unchanged is otherwise invisible to every cache.
- **Paths the fork keys by must survive renames and synthetic paths.** Room tables keyed by path are
  carried through a rename in `updateDBMediaPath()`; a folder group's `folder_group:<id>` tile never
  reaches Room or the scan; the sentinel folders (`SHOW_ALL`, favourites, the recycle bin) cannot be
  stat'd.
- **The media grid's list is `gridSource()`, never `mMedia`** — a search narrows it.
- **Two panes, one window** — the search bar belongs to whichever pane is up, and a pane swap is a
  draw rather than a layout, so anything waiting on a layout pass has to be called outright.
- **Floating chrome** — `FloatingTopBar.keepGridClear()` pads the grid by the bar's height, which
  already carries the status bar inset; a re-inflated menu has to be recoloured or its icons draw
  invisibly; every glass panel comes and goes through `PanelAnim`'s `showPanel`/`hidePanel`.
- **The viewer's window is translucent** so a tile can grow into it over the grid; a custom
  animation in `ActivityOptions` or a missing `Window.setFormat(TRANSLUCENT)` leaves it growing out
  of a black screen.

## Code style

Keep code comments CONCISE and NOT TOO LONG. Comments dont need to explain small UI details. Comment
things that are not obvious and might raise questions otherwise. Avoid comments that restate obvious code

## Version control and GitHub

Branches:
- **`main`** only syncs from upstream and carries no fork work, do not change
- **`dev`** is the trunk and the only place a version is written
- work happens on branches off `dev`, squash-merged back by PR. Work branch names use the same types as commits.

### Commiting & Pull Requests

Commit and PR titles must follow a format of `type: summary`
- Allowed types: `feat`, `tweak`, `fix`, `chore`, `docs`, `refactor`, `perf`, `test`, `build`, `ci`

`FORK-CHANGELOG.md` records **notable** user-facing changes as they are made, under `## [Unreleased]` — include internal fixes only when major. When adding to the log add them to the correct section `### Added`, `### Changed`, `### Fixed`. Keep the entries simple and short, this is meant for the user to read.

Never change `gradle.properties` on a work branch, version is changed by a tool activated separately when version is ready.

Use the PR template `.github/PULL_REQUEST_TEMPLATE/pull_request_template.md` for pull requests.

### Releasing

Releases are driven by `/release`. The version is decided by the headings in `FORK-CHANGELOG.md`.
`### Added` makes it a minor release, otherwise it is a patch. This is the only tool that writes a version.
The version is tagged automatically by a GitHub workflow `fork-release.yml`.

Do not update `CLAUDE.md` unless asked to.