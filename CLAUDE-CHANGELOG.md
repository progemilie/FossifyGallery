# Changelog

Fork-only changes, newest first. Small fixes are left out.

## v1.10.2 - 2026.08.16 — metadata, settings

- **File descriptions** — read and edit a file's description in the metadata sheet. Stored in the
  file's own XMP, so other apps see it too.
- **Startup screen** — pick what the app opens on: the folder grid, all folders, favorites, the
  recycle bin, a folder group or any folder.
- **Thumbnail strip toggle** in the viewer's drop-down menu.

## v1.9.0 - 2026.08.16 — menus

- **Glass drop-down menus** on all three browsing screens, matching the other glass effects, with
  items gathered into sections.
- Viewer actions moved off the toolbar and into the drop-down, so they stop changing places.

## v1.8.0 - 2026.08.15 — folder grid

- **Folder groups** — several folders under one tile in the folder grid, with a collage cover.
  Nothing moves on disk. Tap to step into the group, group/rename/ungroup from the selection bar.
- **Drag folder tiles** to arrange the grid, or hold one over another to group them.
- The export now carries folder groups and the folder grid's own order as well as media orders.

## v1.7.0 - 2026.08.11 — performance

- Thumbnails come from the copy stored inside the photo — noticeably faster grids and strip.
- Grid thumbnails use half the memory, so more stay cached.
- Reshaped thumbnail strip: rounded corners, tighter towards the ends.

## v1.6.1 - 2026.08.11 — refactor

- Major code refactor, no new features. Most likely version that might have broken something maybe.

## v1.6.0 - 2026.08.10 — design

- **Glass effect on the quick action UI** — the rating and copy/move choosers wear the same frosted
  material as the search pill and follow the theme.
- A **Glass UI** switch under Look & feel turns it all off (Android 12+).

## v1.5.0 - 2026.08.10 — ratings

- **Bulk rate** — set a rating on several selected images at once.

## v1.4.0 - 2026.08.10 — custom order

- Custom order: buttons to move the selection straight to the top or bottom.

## v1.3.0 - 2026.08.10 — quick actions

- **Copy/move quick action** — hold the button to pick a folder without a dialog. Frequently used
  folders appear first.

## v1.2.0 - 2026.08.09 — bottom actions

- **Bottom action customization** — drag to reorder the bar, up to 8 actions.
- Mirror added to the bottom actions.

## v1.1.0 - 2026.08.09 — metadata

- **Metadata viewer** — swipe up while viewing an image, or use the properties button. Lists every
  metadata group the file actually carries, read straight off the file.

## v1.0.0 - 2026.08.09 — viewer, custom order, design

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
