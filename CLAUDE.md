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
findings. `.editorconfig` enforces LF, 4-space indent, 160-char max line length.

adb is on PATH for testing in the emulator: emulator-5554, a Pixel_10, API 37, 1080x2424 at
420dpi, with many folders of pictures under /Pictures.

## Architecture

### Fossify Commons dependency

Most base classes, shared dialogs and extension functions come from the external
`org.fossify:commons` library, not this repo: `SimpleActivity` extends its `BaseSimpleActivity` and
nearly every screen extends `SimpleActivity`, `helpers/Config.kt` extends its `BaseConfig`, and its
dialogs and `org.fossify.commons.extensions.*` are used rather than reimplemented.

**Fork features drive the upstream classes from outside wherever that is reasonable** — see
`FolderDragMode`, `FolderGroupActions` and `MediaReorderMode`, each of which drives an upstream
adapter rather than living inside it.

### Package layout (app/src/main/kotlin/org/fossify/gallery/)

Conventional `activities/adapters/fragments/dialogs/helpers/models/views/`. The three browsing
screens are `MainActivity` (folder grid), `MediaActivity` (media grid) and `ViewPagerActivity`
(fullscreen viewer); `helpers/MediaFetcher.kt` is the MediaStore query engine and
`databases/GalleryDatabase.kt` the single Room DB (manual migrations, currently v4→v12).

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
to the safe path (CVE-2023-4863).

Cache keys everywhere are derived from path + last-modified + size (`Medium.getSignature()`,
`Directory.getKey()`). **Anything that edits a file in place must call
`TransformedMedia.onTransformed(path)`** before touching caches — it bumps a per-path version folded
into both keys, plus a global generation counter screens use to decide whether to rebind stale
bitmaps. An edit leaving size and timestamp unchanged is otherwise invisible to every cache.

### Per-folder custom media order

Sort-by-custom for media (upstream has it for folders only). The `media_order` Room table holds the
arranged paths keyed by lowercased folder path — kept out of the media table because media rows are
dropped and reinserted on every rescan. `Config.customMediaOrderFolders` is only an *index* of which
folders have an order, so `hasCustomMediaOrder()` can be answered on the main thread where Room
would throw; the table is the authority. Access via `extensions/CustomMediaOrder.kt`, all blocking.

Reordering lives in `adapters/MediaReorderMode.kt` and is put up by `MediaActivity` through
`helpers/ReorderBar.kt`: multi-select marks a group, dragging any marked item carries the whole
group. The lift, ring and shadow it shares with the folder grid are in `helpers/DragLift.kt`.

### Folder groups

Several folders drawn under one tile in the folder grid. Nothing moves on disk. Definitions live in
`Config` as JSON (`extensions/FolderGroups.kt`) rather than in Room, because the grid reads them on
the main thread. A tile is a `Directory` under a synthetic `folder_group:<id>` path so selection,
pinning and the custom folder order carry it with no case of their own, and it holds its members in
`groupMembers`. **Ids are never reused** — a synthetic path outlives its group in those prefs, and
would otherwise attach itself to the next group made. "Open on startup" (`extensions/StartupScreen.kt`)
puts one of these paths in `Config.defaultFolder`; a group is stepped into before the first scan
rather than launched as a screen of its own.

Two rules keep the grid honest, both in `extensions/FolderGroupTiles.kt`:
- **A tile never reaches Room or the scan.** `expandFolderGroups()` puts one back into its folders;
  `MainActivity.getCurrentlyDisplayedDirs()` and `updateDirectories()` are the gates.
- **`applyFolderGroups()` says nothing about which group is open**, so the scan thread can build the
  root view while `MainActivity.narrowToOpenGroup()` picks the open group's members on the main
  thread, in the same pass that hands them to the adapter. Splitting those two apart is what used to
  leave the grid showing one state while the screen believed another.

While a group is open the grid is *not* the library — anything re-scanning or re-sorting has to work
from `mDirsIgnoringSearch`, never from what the adapter holds. Sorting inside a group follows the
same rule as the root grid: the chosen sorting applies, and sort-by-custom is the hand made order —
the group's own, which is also what its collage reads.

`adapters/FolderGroupActions.kt` (the action mode's group items) and `adapters/FolderDragMode.kt`
(the gestures) both drive `DirectoryAdapter` from outside. The drag lifts a tile on the long press
that selects it, replacing commons' drag-to-range-select on this grid, drops it between tiles to
arrange them and holds it over one to group the two. **The grid has to hold still under a lifted
tile** or nothing could be dropped onto anything — hence `FOLDER_DROP_ZONE`, the raised
`FOLDER_DRAG_MOVE_THRESHOLD`, and change animations being off: a tile ticked mid drag would
otherwise be drawn twice and the finger carry the wrong copy.

### Order & groups export

`helpers/OrderAndGroupsIO.kt` carries all three hand made arrangements — folder groups, the folder
grid's order, each folder's media order — in one plain text file of bracketed sections. Groups go
out by name, not by id, since a `folder_group:<id>` means nothing on the install reading it back.
**Import drops anything naming a file or folder that is not there, and drops a section left empty by
that**; the sentinel folders (`show_all`, favorites, recycle bin) are exempt, nothing can stat them.

### The viewer's bottom action bar

`helpers/BottomAction.kt` is the one table of bit, view id, label and icon that both the bar and
`ManageBottomActionsDialog` read; `parseBottomActionsOrder()` appends whatever a stored order
predates rather than dropping it. `applyBottomActionsOrder()` rebuilds bottom_actions.xml's
horizontal chain rather than reordering children — the chain is what spreads the buttons and skips
the GONE ones.

### Choosers held open over a bottom action button

Rating (`views/RatingChooser.kt`) and copy/move (`views/FolderChooser.kt`) answer a hold with a
picker the finger drags through without lifting off, and a tap with the dialog they always had. Both
are `views/HoldChooser.kt`s — a `GlassPanel` plus the gesture — put on a button by
`View.holdToChoose()`, so a screen only says what fills the chooser and what to do with the choice.

`revealOver()` lays a chooser out **INVISIBLE** and makes it VISIBLE only once positioned. The
folder list is **prefetched** into `mQuickChooserFolders` on every media change; it reads Room and
the filesystem, far too slow to run when the hold fires.

### The three dots' drop-down

`views/GlassMenu.replaceOverflow()` puts a `GlassPanel` in place of the platform's overflow popup on
all three browsing screens. It builds itself from the toolbar's own `Menu` every time it opens and
picks through `performIdentifierAction`, so each screen's `refreshMenuItems()` and click listener
carry over untouched; whatever the toolbar is already showing as a button of its own is left out.

`helpers/MenuSections.kt` holds one `MenuSpec` per screen: the sections, drawn with a dotted rule
between them, and which items are drawn as a row of icons rather than a row each. **A spec only
arranges — anything it fails to name is appended to the last section** rather than dropped, so no
action can go missing by being forgotten there.

### The viewer's file metadata sheet

A swipe up over the media raises `views/MetadataSheet.kt`, listing every group the file carries.

- `helpers/MetadataReader.kt` reads it off the main thread and **straight off the file every time**
  — never from Room, MediaStore or the `Medium` the grid was built from, all of which describe the
  last scan rather than the file as it is now. `com.drewnoakes:metadata-extractor` supplies one
  directory per group the file stores and those become the sections verbatim, so nothing is dropped
  for want of a hand-written mapping; `ExifInterface`, `MediaMetadataRetriever` and `MediaExtractor`
  fill in what it cannot parse.
- `MetadataSummary.kt` (pinned rows), `MetadataFormat.kt` (value formatting) and
  `views/MetadataRows.kt` are split out to stay under detekt's function-count threshold.
- `MetadataSheet.attachTo()` is the whole of a viewer's wiring, including
  `BaseViewerActivity.updateNavigationBarIconsForPanel()` — the viewer forces light system-bar
  icons, which vanish against a light-theme sheet.
- The two things the sheet *writes* live in `views/MetadataWrites.kt`, not in the sheet itself.
  - The **description** row: `dc:description` in the file's XMP, the way a rating is `xmp:Rating`
    (`helpers/XmpPacket.kt` is the plumbing both sit on). A file that can carry one always gets the
    row — an empty row is the only way in to writing the first description.
  - **Remove metadata**, offering the `MetadataGroup`s the file actually carries
    (`MetadataStripper.removableGroups()`, which is also what hides the row when there are none), in
    place or into a copy beside it. The row lives *inside* the peek, so the resting sheet already
    shows it.

`helpers/ContainerMetadata.kt` and the per-format walkers beside it (`JpegSegments`, `PngChunks`,
`WebpChunks`, over the byte plumbing in `ContainerBytes`) do the removal by **copying the file out
block by block and leaving the unwanted ones behind — never by re-encoding**, so a stripped file is
pixel for pixel the file it came from. Only the three formats those walkers understand are offered;
anything else is refused rather than copied.

Location and orientation are the two groups that are *not* whole blocks but fields inside the Exif,
so `MetadataStripper` settles them afterwards with `ExifInterface` plus `helpers/XmpFields.kt`. They
part company when the whole Exif goes: the location cannot survive it (hence Exif ticking and
locking Location in the dialog), while the orientation is read off the source and **written back**
unless it was asked for by name — a stripped photo should not come out sideways.

### Chrome that floats over the content

The three browsing screens draw content edge to edge with the chrome over it. No immersive mode is
involved — commons' `EdgeToEdgeActivity` already enables it; what changed is that the app no longer
paints an opaque band under its own bar.

- **Viewer** — the toolbar shows `viewer_header.xml` instead of a title, so the existing fullscreen
  fade takes it away with the rest of the chrome. Details are built by `extensions/ExtendedDetails.kt`
  off the main thread. `BaseViewerActivity` forces light system-bar icons.
- **Grids** — `MySearchMenu` is the *last* child of the `CoordinatorLayout` (draw order puts it over
  the grid) and the grid gets no top inset of its own; `keepGridClearOfTopBar()` pads it by the bar's
  measured height, which already carries the status bar inset. Doing it in the layout double-counts.
- **Frosted glass** — `helpers/Glass.kt` holds every colour and radius, `views/GlassPanel.kt` is the
  `BlurView` that wears it. A panel is told what to copy with `frost(contentBehind)`, which need not
  be an ancestor, and paints itself flat below Android 12.

## Style

Keep code comments CONCISE and NOT TOO LONG. Comments dont need to explain small UI details.
Comment things that are not obvious and might raise questions otherwise. Longer comments are
warranted when it is not immediatelly evident what the purpose of something is.

DO NOT make insignificant updates to CLAUDE.md file. Only for large features that change core functionality.
A UI feature does not need a large block of text in the CLAUDE.md file. Try to keep the file less than 200 lines.

Update `@CLAUDE-CHANGELOG.md` file after a new version bump and include new user facing changes. Fixes do
not need to be included, unless they are major. The file should be updated as the last commit before making
a PR. So if a PR is ordered to be made, first make a commit to change the changelog file.