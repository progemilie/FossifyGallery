# Changelog

Fork-only changes, newest first. Small fixes are left out.

## [v1.16.0] - 2026.08.23 — navigation

- **A bottom pill for moving between Pictures and Albums.** Both grids float one over their
  content, with the view you are on highlighted. Its Menu opens the same drop-down as the three
  dots, and the old switch-view buttons are gone from the top search pill.
- **The two views are one screen now.** Tapping across slides the pictures past each other while
  the pill and the search bar stay exactly where they are, with nothing to wait for in between -
  only the bar's buttons change. Opening the app in Pictures no longer passes through Albums
  on the way.

## [v1.15.0] - 2026.08.22 — selecting

- Peek at full screen media when selecting multiple items.

## [v1.14.0] - 2026.08.22 — sorting

- **Sorting and grouping are one dialog.** Two dropdowns, each with its own ascending/descending arrow. The separate "Group by" menu entry is gone.

## [v1.13.4] - 2026.08.22 — grouping

- Grouping by date can be set to ascending again, whatever the sorting is.

## [v1.13.3] - 2026.08.22 — scrolling

- The media grid decodes thumbnails ahead of the way it is being scrolled, so fewer tiles come up
  blank on a fling.

## [v1.13.2] - 2026.08.22 — thumbnail cache

- Thumbnails are decoded at a set of standard sizes rather than one per column count, so column
  counts that differ by a little share a cached thumbnail. Roughly half as many thumbnails on disk.

## [v1.13.1] - 2026.08.22 — thumbnail cache

- The zoomed-out grid caches a smaller thumbnail, cutting what its rungs take up on disk and in
  memory.

## [v1.13.0] - 2026.08.21 — zoom levels

- **Every pinch step is a visible change.** The media grid's column counts now follow one ladder — single steps up to 7, then 10, 14, 20, 28 and on.

## [v1.12.1] - 2026.08.17 — thumbnail cache

- The media grid no longer stores a second copy of a thumbnail that is a pixel off the first, so the
  cache holds one picture per column count instead of up to three.

## [v1.12.0] - 2026.08.17 — zooming

- **Pinch to zoom, rebuilt**
- **Tap to zoom back in** — a tap in the zoomed-out grid steps one count back down

## [v1.11.0] - 2026.08.16 — removing metadata

- **Remove metadata** from the metadata sheet: location, camera details, captions, colour profile and
  the rest — pick what comes off the file, or take everything in one tap. Only what the file actually
  carries is offered.
- **Save as a new file** puts the stripped copy beside the original instead of writing over it.

## [v1.10.2] - 2026.08.16 — metadata, settings

- **File descriptions** — read and edit a file's description in the metadata sheet. Stored in the
  file's own XMP, so other apps see it too.
- **Startup screen** — pick what the app opens on: the folder grid, all folders, favorites, the
  recycle bin, a folder group or any folder.
- **Thumbnail strip toggle** in the viewer's drop-down menu.

## [v1.9.0] - 2026.08.16 — menus

- **Glass drop-down menus** on all three browsing screens, matching the other glass effects, with
  items gathered into sections.
- Viewer actions moved off the toolbar and into the drop-down, so they stop changing places.

## [v1.8.0] - 2026.08.15 — folder grid

- **Folder groups** — several folders under one tile in the folder grid, with a collage cover.
  Nothing moves on disk. Tap to step into the group, group/rename/ungroup from the selection bar.
- **Drag folder tiles** to arrange the grid, or hold one over another to group them.
- The export now carries folder groups and the folder grid's own order as well as media orders.

## [v1.7.0] - 2026.08.11 — performance

- Thumbnails come from the copy stored inside the photo — noticeably faster grids and strip.
- Grid thumbnails use half the memory, so more stay cached.
- Reshaped thumbnail strip: rounded corners, tighter towards the ends.

## [v1.6.1] - 2026.08.11 — refactor

- Major code refactor, no new features. Most likely version that might have broken something maybe.

## [v1.6.0] - 2026.08.10 — design

- **Glass effect on the quick action UI** — the rating and copy/move choosers wear the same frosted
  material as the search pill and follow the theme.
- A **Glass UI** switch under Look & feel turns it all off (Android 12+).

## [v1.5.0] - 2026.08.10 — ratings

- **Bulk rate** — set a rating on several selected images at once.

## [v1.4.0] - 2026.08.10 — custom order

- Custom order: buttons to move the selection straight to the top or bottom.

## [v1.3.0] - 2026.08.10 — quick actions

- **Copy/move quick action** — hold the button to pick a folder without a dialog. Frequently used
  folders appear first.

## [v1.2.0] - 2026.08.09 — bottom actions

- **Bottom action customization** — drag to reorder the bar, up to 8 actions.
- Mirror added to the bottom actions.

## [v1.1.0] - 2026.08.09 — metadata

- **Metadata viewer** — swipe up while viewing an image, or use the properties button. Lists every
  metadata group the file actually carries, read straight off the file.

## [v1.0.0] - 2026.08.09 — viewer, custom order, design

Initial fork work, landed over 2026.08.06–08.09 before fork versions were tracked.

- **Ratings out of five** — hold the star in the viewer and slide to pick, or tap for a dialog.
  Written to the file's XMP, so Aves, Lightroom, digiKam and Windows read it. Sort and group by
  rating. Favourite became a heart to free the star up.
- **Custom media order per folder** — drag to arrange, multi-select to move several at once.
- **Export and import** the custom order to a file.
- **Thumbnail strip** under the photo in the viewer.
- **Floating chrome** — content runs edge to edge with the search pill floating over the grid, and
  the viewer's top bar showing the file name and its details.
- Leaving the viewer reveals and grows the thumbnail you were on.
- **Lossless mirror** (horizontal flip) in the media grid's selection bar and the viewer.

[v1.16.0]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.16.0
[v1.15.0]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.15.0
[v1.14.0]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.14.0
[v1.13.4]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.13.4
[v1.13.3]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.13.3
[v1.13.2]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.13.2
[v1.13.1]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.13.1
[v1.13.0]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.13.0
[v1.12.1]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.12.1
[v1.12.0]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.12.0
[v1.11.0]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.11.0
[v1.10.2]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.10.2
[v1.9.0]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.9.0
[v1.8.0]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.8.0
[v1.7.0]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.7.0
[v1.6.1]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.6.1
[v1.6.0]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.6.0
[v1.5.0]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.5.0
[v1.4.0]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.4.0
[v1.3.0]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.3.0
[v1.2.0]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.2.0
[v1.1.0]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.1.0
[v1.0.0]: https://github.com/progemilie/FossifyGallery/releases/tag/v1.0.0
