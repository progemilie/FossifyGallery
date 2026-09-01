# Architecture notes

How each fork feature works. CLAUDE.md keeps the rules that break silently when missed and
points here for the rest; read the section for an area before working in it.

### The zoomed-out media grid

`helpers/GridZoom.kt` is the ladder of column counts the media grid is pinched through
(`helpers/GridPinchZoom.kt`, which replaces commons' unusable zoom listener): every screen takes a
prefix of one sequence — single steps to 7, then 1.4x apart — cut three **simplified** rungs past
`interactiveMax`, the count whose tile is nearest 55dp. A phone gets 1-7, 10, 14, 20 and no screen
more than fourteen rungs; the sideways grid divides the height. **`interactiveMax` is a boundary,
not a rung** — a wide screen steps 14 to 20 — so anything naming a tappable count wants
`largestInteractive`.

Past `interactiveMax` a tile is only its picture, and a screenful is several hundred of them.
`MediaAdapter` binds `photo_item_grid_simple.xml` — a bare `MySquareImageView`, no listeners, no
badges, no selection — and `MediaGridPane.mediaForGrid()` drops the grouping headers, which would
leave ragged gaps. Nothing there is tappable, so a tap zooms in one rung and scrolls the item that
was under the finger back under it. `helpers/SimpleThumbnailLoader.kt` prepares its Glide request
**once** and reuses it, at one `override()` size for every rung, because a fling across twenty
columns binds ~100 items in a frame.

Two rules for anything touching this:
- **The grid's source is `gridSource()`, never `mMedia`** — a search narrows it, and rebuilding from
  `mMedia` puts the whole library back on screen.
- Screens with no pinch of their own (search, the picker dialog) read `interactiveMediaColumnCnt()`
  rather than `Config.mediaColumnCnt`, or they inherit a count whose items cannot be tapped.

### Per-folder custom media order

Sort-by-custom for media (upstream has it for folders only). The `media_order` Room table holds the
arranged paths keyed by lowercased folder path — kept out of the media table because media rows are
dropped and reinserted on every rescan. `Config.customMediaOrderFolders` is only an *index* of which
folders have an order, so `hasCustomMediaOrder()` can be answered on the main thread where Room
would throw; the table is the authority. Access via `extensions/CustomMediaOrder.kt`, all blocking.

Reordering lives in `adapters/MediaReorderMode.kt` and is put up by `MediaGridPane` through
`helpers/ReorderBar.kt`: multi-select marks a group, dragging any marked item carries the whole
group. The lift, ring and shadow it shares with the folder grid are in `helpers/DragLift.kt`.

### Two grids, one window

Pictures and Albums are two panes of `MainActivity` rather than two screens, because that is the
only way the pill and the search bar can hold still through a swap: an activity handover costs
~400ms to its first frame, which either a window animation covers — carrying the chrome off with it
— or a frozen screen does. A swap slides only `content_holder`'s two children.

`interfaces/GridPane.kt` is what a screen asks of whichever grid is up; `helpers/GridChrome.kt` is
the bar, the pill, and the `bind()` that points them at a pane. `views/MediaGridPane.kt` is the
media grid itself, worn by `MainActivity` as Pictures and by `MediaActivity` for one folder;
`MediaActivity.mMedia` stays where the viewer looks for it. Three traps:

- **A re-inflated menu has to be recoloured** — commons tints the icons in `updateColors()`, and
  untinted ones draw invisibly rather than not at all.
- **The bar belongs to whichever pane is up**, which `updateTopBarForGroup()` checks before dressing
  it: opening in Pictures runs the folder pane's startup behind it, and a swap in flight has not
  reached the frame where the bar changes over.
- A swap moves panes by `translationX`, a draw and not a layout, so anything that waits on a layout
  pass (`FloatingTopBar.keepGridClear()`) has to be called outright instead.

### Folder groups

Several folders drawn under one tile in the folder grid. Nothing moves on disk. Definitions live in
`Config` as JSON (`extensions/FolderGroups.kt`) rather than in Room, because the grid reads them on
the main thread. A tile is a `Directory` under a synthetic `folder_group:<id>` path so selection,
pinning and the custom folder order carry it with no case of their own, and it holds its members in
`groupMembers`. **Ids are never reused** — a synthetic path outlives its group in those prefs, and
would otherwise attach itself to the next group made.

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
Where the items come from is the caller's to say, so `openedBy()` serves the selection pill's menu
button off an action mode's menu with the same panel.

`helpers/MenuSections.kt` holds one `MenuSpec` per screen: the sections, drawn with a dotted rule
between them, and which items are drawn as a row of icons rather than a row each. **A spec only
arranges — anything it fails to name is appended to the last shown section** rather than dropped, so
no action can go missing by being forgotten there. A spec's `hidden` list is a section the menu opens
without, revealed by an arrow the last shown row wears beside whatever that row already does.

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
    place or into a copy beside it. Last of all, past everything the file has to say, and laid out
    like a section heading so the column of text down the sheet is unbroken.

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

### Growing a tile into the viewer

Tapping a photo grows that tile into the fullscreen one, and closing shrinks it back into whichever
tile was swiped to. `helpers/ViewerTransition.kt` is the hand-off between the two activities - the
tile's rect, and the grid left registered as the `Anchor` that answers where to fly back to;
`helpers/TileFlight.kt` is the viewer's half, worn by all three fullscreen screens.

**A flight is drawn with the photo's own picture, never the tile's.** With crop thumbnails on - the
default - a tile's bitmap has been through a `CenterCrop` and has no edges left to unfold, so a
flight drawn with it can only fill the screen and cut to the real photo at the end. The tap starts
`lowResPhotoRequest()` instead: the copy stored inside the file, uncropped, and *the same request
the viewer paints first* - so the flight sets off already knowing the photo's proportions, and the
hand-over at the end is nothing happening at all. `views/FlightOverlay.kt` moves the rect and
the crop together; either one alone leaves a cut at one end or the other.

It only reads as one surface while the grid is still drawn underneath, which takes three more
things, each of which silently leaves the photo growing out of a black screen if it is missed:

- **A translucent theme** (`ViewerTheme`, only translucent in `values-v28`: before API 28 such an
  activity may not ask for an orientation, which the viewer does) **and** `Window.setFormat`
  `TRANSLUCENT` at runtime. The theme alone composites nothing.
- **No custom animation in `ActivityOptions`.** The system takes one as licence to drop the grid's
  window from the frame. The no-motion window animation lives in the theme instead - `viewer_hold`,
  which is also why a close with no tile to shrink into has to name a slide of its own.
- **The exit tile looked up on every page change**: the grid has to scroll and lay out to answer,
  and a finger already lifted cannot wait a frame for it.

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
- **A selection** — the platform's contextual action bar is never built:
  `onWindowStartingSupportActionMode` is AppCompat's offer to supply an action mode of a screen's
  own, and `helpers/SelectionChrome.kt` takes it, so nothing covers the top of the grid.
  `views/SelectionPills.kt` is what stands in its place — the count at the top, the actions along
  the foot, built to the navigation pill's measurements and never panned away with the grid.
  Everything upstream does through the action mode carries over: the adapter inflates its menu,
  hides what does not apply in `prepareActionMode()` and invalidates on every change, which is what
  fills the pills in again.
- **Frosted glass** — `helpers/Glass.kt` holds every colour and radius, `views/GlassPanel.kt` is the
  `BlurView` that wears it. A panel is told what to copy with `frost(contentBehind)`, which need not
  be an ancestor, and paints itself flat below Android 12. **Every panel comes and goes through
  `helpers/PanelAnim.kt`'s `showPanel`/`hidePanel`** — one `PanelMotion` named at the one call site,
  matched to the platform drop-down animation `GlassMenu`'s popup still gets for free. Nothing there
  touches translation: a panel places itself against its anchor with it.
