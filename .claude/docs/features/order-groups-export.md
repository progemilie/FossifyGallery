# Order & groups export

All three hand made arrangements — folder groups, the folder grid's order, and each folder's media
order — go out and come back in one plain text file.

## Coupled with

Read these before changing this feature — each can break silently if this one changes without it.

- [folder-groups](folder-groups.md) — groups are written by name and resolved to this install's ids on import.
- [custom-media-order](custom-media-order.md) — media orders are written per folder, and never for the all media grid.

## Where it lives

- `helpers/OrderAndGroupsIO.kt` — `exportOrderAndGroups`, `importOrderAndGroups`, the parser.
- `activities/SettingsActivity.kt` — the export and import rows, `exportOrderAndGroupsTo` /
  `importOrderAndGroupsFrom`.

## The format

Bracketed sections, each followed by absolute paths, one per line:

```
[group:Trips]
/storage/emulated/0/Pictures/Italy

[order:folders]
/storage/emulated/0/DCIM/Camera
group:Trips

[/storage/emulated/0/dcim/camera]
/storage/emulated/0/DCIM/Camera/IMG_002.jpg
```

Nothing else is written. Import still skips blank lines and lines starting with `#`, which files
exported before carried as a header. Paths are absolute, so a bracketed line can never be mistaken
for one. **Groups go out by name, not
by id**, since a `folder_group:<id>` means nothing on the install reading it back; inside the folder
order a tile is written `group:<name>`, and tiles whose group is gone are dropped. `group:` can never
begin a real path, so a file older than groups reads exactly as it did.

## Import

Groups are applied first, so the folder order can resolve the names it refers to. A group is matched
by name (`replaceFolderGroup`), so importing twice does not leave two tiles. Sections the file names
replace what was there; anything it never mentions is left alone. Unreadable lines are skipped rather
than failing the file.

## What breaks silently

- **Import drops anything naming a file or folder that is not there, and drops a section left empty
  by that** — a group whose folders have all gone is no longer a group.
- The sentinel folders (`SHOW_ALL`, `FAVORITES`, `RECYCLE_BIN`) are exempt: nothing can stat them.
- The all media grid's media order is neither written nor read — see
  [custom media order](custom-media-order.md).
