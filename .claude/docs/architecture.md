# Architecture

The shape of the app as the fork has left it, and the conventions every feature leans on. Each fork
feature has a file of its own under [features/](features/) — read it before working in that area.
Each opens with **Coupled with**: the features that break silently when this one changes without them.
A coupling is listed in both files, and only when there is something that breaks — a feature that
is merely related is linked in the prose instead.
CLAUDE.md keeps only the rules that break silently when missed.

## Fork and upstream

Upstream (FossifyOrg) is merged into `dev` from time to time, so **every fork feature lives in a file
of its own** and the upstream class it touches keeps only a hook that calls it. `MediaReorderMode`,
`MediaGridNavigator`, `FolderDragMode` and `FolderGroupActions` drive upstream adapters from outside;
`extensions/Mirror.kt`, `Rating.kt`, `CustomMediaOrder.kt` and the like keep fork code out of
upstream's shared `Activity.kt`/`Context.kt`. A fork change to an upstream file should be the
smallest hook that will do.

Much of what a screen is made of comes from `org.fossify:commons` and cannot be edited: its
`MySearchMenu` is the search bar, its `BaseSimpleActivity` the base of every screen, its
`BaseConfig` the base of `Config`. Where the fork needs more of one, it reaches in through public
bindings (the tab button and the glass pill are built inside `MySearchMenu.binding`) or re-applies
itself after commons repaints (`FloatingTopBar.makeFloating` runs after every `updateColors()`).

## Screens

| Screen | What it is |
|---|---|
| `MainActivity` | **Albums** (the folder grid) and **Pictures** (all media) as two panes of one window. See [two grids](features/two-grids-one-window.md). |
| `MediaActivity` | A `MediaGridPane` in a window of its own: a folder, favourites, the recycle bin, or another app's picker. |
| `ViewPagerActivity` | The fullscreen viewer. `PhotoVideoActivity` (a file opened from outside) and `VideoPlayerActivity` share `BaseViewerActivity` with it. |
| `PeekViewerActivity` | A stripped-down viewer opened while selecting. See [peek](features/peek-viewer.md). |
| `SettingsActivity` | Settings as a first page of categories, each opening a page of its own. See [settings](features/settings-screen.md). |

`MediaGridPane` is the media grid itself — everything ever done to it — and the activities around it
are only the window and the chrome floating over it. `interfaces/GridPane.kt` is what a screen asks
of whichever grid is up; `helpers/GridChrome.kt` is the search bar and navigation pill pointed at it.

**Screens hand each other state in memory, not through intents.** A folder runs to thousands of
paths, past what a binder transaction carries, so the viewer reads the grid's list from
`MediaActivity.mMedia`, the peek from `PeekSession`, and a flight's bitmap from `ViewerTransition`.
All three are process-wide statics that the receiving screen reads once it is up.

## Where state lives

| What | Where | Why there |
|---|---|---|
| Media and folders as last scanned | Room, `media` / `directories` | upstream's; rows are dropped and reinserted on every rescan |
| A folder's hand made media order | Room, `media_order` (v11) | outlives rescans; `Config.customMediaOrderFolders` indexes it for the main thread |
| Ratings, as a cache | Room, `media_ratings` (v12) | the file's XMP is the authority; this spares a scan opening every file |
| Folder groups, tabs, folder order, bottom action order | `Config`, as JSON or joined strings | read on the main thread, where Room throws |
| Rating, description, every metadata field | the file itself | travels with the photo to other apps |

Room migrations are written by hand (`databases/GalleryDatabase.kt`, v4→v12). The two fork tables
are keyed by lowercased path, so **a rename must carry them along**: `updateDBMediaPath()` updates
both, or a renamed file leaves its folder's order and has its rating read again.

Anything keyed by path must also survive the synthetic paths the fork invents: `folder_group:<id>`
for a group tile, and upstream's sentinels `SHOW_ALL`, `FAVORITES`, `RECYCLE_BIN`, none of which can
be stat'd.

## Thumbnails and caches

Every thumbnail goes through one pipeline — `ThumbnailSource` (the photo's embedded copy where it is
big enough), sizes snapped to `ThumbnailSizes`, requests shared with `ThumbnailPrefetcher` — and every
cache key is built from path + last-modified + size. An in-place edit that leaves those unchanged has
to call `TransformedMedia.onTransformed(path)`. See [thumbnails](features/thumbnails.md) and
[editing files in place](features/file-edits.md).

## Chrome

The browsing screens draw content edge to edge with frosted glass chrome floating over it: a search
pill at the top, a navigation pill at the foot, and selection pills in place of an action bar. Every
panel is a `GlassPanel`, comes and goes through `PanelAnim`, and takes its colours from `Glass`. See
[floating chrome and glass](features/floating-chrome.md).

## Conventions

- **Blocking work says so.** Functions that touch the file, Room or MediaStore are documented
  "Blocking, call it off the main thread" and callers use `ensureBackgroundThread`.
- **Detekt's function-count threshold shapes files.** Classes that would cross it are split into a
  sibling (`GlassMenuParts`, `MetadataRows`, `TabChooserRows`, `FolderChooserRows`, `DropdownParts`,
  `EdgeAutoScroller`); run `./gradlew detekt` before folding one back.
- **Measure rather than guess.** `helpers/Perf.kt` counts and times named work in debug builds and
  folds away entirely in release; read it with `.claude/tools/perf.py counters`.
- Proguard keeps all of `org.fossify.**`, so unreferenced classes still ship (`ChangeGroupingDialog`
  is kept as the record of what the sort dialog replaced). Libraries that build classes by reflection
  need their own keep rules — metadata-extractor's no-arg constructors are one.

## Features

Browsing
- [Two grids, one window](features/two-grids-one-window.md) — Pictures and Albums as panes of
  `MainActivity`, the navigation pill, the pane a folder is opened in.
- [Zooming the media grid](features/grid-zoom.md) — the ladder of column counts, the simplified
  grid past it, and the smooth pinch between counts.
- [Thumbnails](features/thumbnails.md) — embedded copies, the size ladder, the prefetcher, cache
  keys.
- [Selection](features/selection.md) — the pills that replace the action bar, the tick, selecting a
  header's group, and unpicking by drag.
- [Peeking while selecting](features/peek-viewer.md) — a fullscreen look without dropping the
  selection.
- [Sort by dialog](features/sort-dialog.md) — sorting and grouping in one dialog of dropdowns.
- [Tabs](features/tabs.md) — up to three remembered places.
- [Startup screen](features/startup-screen.md) — what the app opens on.

Arranging
- [Per-folder custom media order](features/custom-media-order.md) — arranging a folder by hand.
- [Folder groups](features/folder-groups.md) — several folders under one tile, and dragging tiles
  about the folder grid.
- [Folder cover styles](features/folder-cover-styles.md) — square, rounded, card and stack.
- [Order & groups export](features/order-groups-export.md) — all three arrangements in one file.

The viewer
- [Growing a tile into the viewer](features/viewer-transition.md) — the flight between the grid and
  the fullscreen photo.
- [The viewer's chrome](features/viewer-chrome.md) — the heading, extended details, zoom taking the
  chrome away.
- [Thumbnail strip](features/thumbnail-strip.md) — the row of thumbnails under the photo.
- [The video player](features/video-player.md) — the controls, the frame strip as progress bar, and
  holding or double tapping either side.
- [Bottom action bar](features/bottom-actions.md) — the one table behind the bar and its dialog.
- [The viewer in landscape](features/landscape-viewer.md) — the status bar kept away, the bar in the
  top row, the strips at the very foot.
- [Choosers held open over a button](features/hold-choosers.md) — rating, copy/move and tabs.
- [Metadata sheet](features/metadata-sheet.md) — reading every group, descriptions, and removing
  metadata.

Files
- [Ratings](features/ratings.md) — stars stored in XMP, cached for scans, sorted and grouped by.
- [Editing files in place](features/file-edits.md) — XMP writes, mirroring, and what an edit owes
  the caches.
- [The folder picker](features/folder-picker.md) — the fullscreen screen a copy or move picks its
  folder on, making a new folder from it, and the eye for hidden folders.

Look & feel
- [Floating chrome and glass](features/floating-chrome.md) — the search pill, glass panels, their
  motion, outlines.
- [The three dots' drop-down](features/glass-menu.md) — `GlassMenu` and `MenuSpec`.
- [Settings screen](features/settings-screen.md) — a page per category, and searching them.
