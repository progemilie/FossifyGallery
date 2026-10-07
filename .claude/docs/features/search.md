# The search's options

Opening a search dims and blurs the grid and puts options up under the bar: Type pills, the cameras
the photos came from, and file size brackets. A pill narrows the grid, closes the search and becomes
a chip on the bar. It replaced upstream's Filter media dialog, which hid types from the whole app and
is gone (`Config.filterMedia` is fixed at the default).

## Coupled with

Read these before changing this feature — each can break silently if this one changes without it.

- [two-grids-one-window](two-grids-one-window.md) — the bar is the window's: `GridChrome.bind()` hands the chip and the options to whichever pane is up, a pill picked in Albums is Pictures narrowed by it, and "Search all files by default" carries an open search over to Pictures.
- [floating-chrome](floating-chrome.md) — the overlay goes in just above the content the glass copies, and the pill's edge is a `LitEdge` whose light can move.
- [ratings](ratings.md) — `media_traits` is a cache like `media_ratings`: keyed by lowercased path, checked against last-modified and size, and carried through a rename in `updateDBMediaPath()`.
- [custom-media-order](custom-media-order.md) — arranging takes a filter off along with the search.
- [grid-zoom](grid-zoom.md) — a filter narrows `gridSource()` as much as a search does.

## Where it lives

| File | Job |
|---|---|
| `helpers/SearchChrome.kt` | The dim, the options, the chip and the edge an open search adds to the bar, moved together; the field's focus, which follows the keyboard |
| `views/SearchOverlay.kt`, `SearchOverlaySections.kt`, `SearchPill.kt`, `FlowRow.kt` | The dim and the blur, and the options as headed rows of pills |
| `views/FilterChip.kt` | The chip, built into commons' bar |
| `models/SearchFilter.kt` | `SearchFilter`, `MediaKind`, `SizeRange` |
| `helpers/SearchOptions.kt` | What a grid can offer (`searchOptionsOf`), what a filter lets through (`matches`), `MediaFacts` |
| `interfaces/SearchTarget.kt` | What the options ask of a pane: `loadSearchOptions`, `applyFilter`, `activeFilter` |
| `helpers/TraitIndex.kt`, `TraitReader.kt`, `models/MediaTraits.kt`, `interfaces/SearchDao.kt` | The `media_traits` cache (DB v13), and the media table read whole |
| `helpers/ViewerNarrowing.kt` | The results a narrowed grid hands the viewer |
| `extensions/Search.kt` | `searchDB`, `libraryMedia`, `traitLookup`, `mediaFacts` |
| `res/interpolator/search_*.xml` | The curves the overlay and the edge move on |

## Opening and closing

`SearchChrome` is built by `GridChrome` and puts a `SearchOverlay` into the screen at `content_holder`'s
index + 1: under the bar, over everything the glass copies. Opening is one entrance — the dim (and with
it a `RenderEffect` blur on `content_holder`, Android 12+ with Glass UI on) comes in on a curve, the
pill's edge comes up with its light along the bottom and the light rises to rest along the top, and
the options rise the last 24dp into place as they fade in. The edge is the Save button's at twice the
opacity. The dim takes every touch, and a tap anywhere a pill is not puts the keyboard away.

Typing hands the grid back for its live results: the dim and the options fade, the edge stays while
the search is open, and clearing the text brings them back. Closing fades everything at once.

The field holds its focus, and so the cursor, only while the keyboard is up (`SearchKeyboard`):
whatever puts the keyboard away — Back, a tap on the dim, opening a result — leaves the search open
with nothing blinking in it. So Back takes the keyboard first, then closes the search, then takes
the chip off.

The field asks for no fullscreen editor (`IME_FLAG_NO_FULLSCREEN`, `IME_FLAG_NO_EXTRACT_UI`), which a
landscape keyboard would otherwise put over the whole screen: the options stay in sight, scrolling in
what the keyboard leaves.

## The options

Type (Videos, Selfies, Panorama, Screenshots, GIFs, RAW images, SVGs, Favourites), Device (the six
cameras with the most photos, the rest behind More) and File size (under 1 MB, 1–10, 10–100, over 100).
**Only what would change the grid is offered**: a pill nothing matches, or that everything already
matches, is left out, and a section with nothing left goes. A pane counts them off the main thread
from its own media; Albums counts the media table across the folders it shows (`libraryMedia`).

Screenshots also takes in screen recordings, by folder name or file name. Panorama is a photo at least
2.5× wider than tall, a photo sphere by its XMP (`GPano:`), or a file the camera named `PANO_`.
Selfies are photos whose EXIF lens name says front camera — Pixels and iPhones write one; Samsung
writes none, so its selfies are not found.

## Filtering

One filter at a time, lasting as long as a search: leaving the folder, a pane swap or a tab landing
takes it off. While one is on, the bar carries it as a chip (the cross takes it off) in place of the
bar's hint, which `FilterChip` puts aside and hands back - unless a pane has named the bar again in
the meantime - and the pill and the tab button go as they do for a search. Opening the search with a
chip on keeps it, lit among the pills — picked again, it comes off — and typing narrows within it.

`MediaGridPane.searchQueryChanged()` narrows by the filter and the text together, off the main thread,
and drops any answer a later one has overtaken (`mSearchGeneration`). `mSearchResults` is set whenever
either narrows the grid.

A photo opened from a narrowed grid swipes through the results only: the grid hands the viewer the
results' paths (`ViewerNarrowing`, with an intent extra saying so), and every list the viewer reads
in keeps to them. A rename in the viewer carries the path along.

## The trait index

`TraitIndex` reads each photo's make, model, lens name, dimensions and XMP in one open of the file
(`TraitReader`), and keeps what the search needs in `media_traits`. It runs after a scan, never in
one — at the end of the folder grid's rescan and after a pane's fresh load — one pass at a time on a
minimum-priority thread, opening only what is new or changed. A first pass hands over every 200 photos,
and an open search listens (`TraitIndex.Listener`) so its pills fill in. On the emulator a pass over
~16,000 files takes 0.9s once read, and a photo takes about 2.5ms to read the first time.

## What breaks silently

- **The overlay has to sit between the content and the bar.** Inside `content_holder`, the pill would
  frost the dim; over the bar, the pill would be dimmed.
- **The blur reaches only what the hardware draws.** Glass panels copy `content_holder` in software, so
  the pill keeps frosting the sharp grid — and anything that wants the blurred look captured will not
  get it.
- **A filter goes on before the search closes.** Closing empties the field, and the grid has to be
  narrowed already when it rebuilds for that.
- **`Medium.size` is 0 from a folder scan unless it sorts by size.** Sizes go through `MediaFacts`:
  MediaStore in one query, then the file.
- **A search can open onto a pane still loading** — one carried over by "Search all files by default"
  — so a pane answers its options again once its media arrives (`mOptionsWanted`).
- **Commons' `toggleForceArrowBackIcon(false)` puts the magnifier back with a search open**, so
  `SearchChrome.bind()` puts the arrow back for a search carried across a swap.
- **Commons opens the search again every time the field takes focus**, open already or not, and
  commons' `hideKeyboard()` takes the focus away (opening a result does). `GridChrome` lets only the
  first opening through: another would bring the dim back over typed results, the faded pills under it.
- **Turning the screen rebuilds both browsing screens with the search closed** (`configChanges` is
  only `orientation`), so the field keeps neither its text nor its focus: handed back, they came up
  in a closed bar, the grid narrowed by the text and a cursor blinking.
- **Nothing faded may be pressed.** The overlay lets touches through from the moment it starts to leave
  (`dimTo(0f)`), and drops its pills once gone, so the next opening has none waiting unseen while its
  own are counted.
- **Every list the viewer reads in must go through `mNarrowing`**, and only a viewer the narrowed grid
  opened takes the results — a shortcut or another app sees the whole folder.
