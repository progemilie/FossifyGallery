# Startup screen

A setting for what the app opens on: Pictures, Albums, Favorites, or any folder. It took over
upstream's "Set as default folder", which only ever named a folder.

## Where it lives

- `extensions/StartupScreen.kt` — `startupTargets`, `startupTargetLabel`, `isStartupTargetGone`.
- `activities/MainActivity.kt` → `openDefaultFolder()`.
- Stored in upstream's `Config.defaultFolder`: `""` is Albums, `SHOW_ALL` Pictures, `FAVORITES`
  Favorites, anything else a folder path.

## How it works

`openDefaultFolder()` runs before the first scan, so nothing is fetched for a grid that is not the
one opening. A pane target is simply the pane brought up; a folder or Favorites is a `MediaActivity`
launched over Albums. It returns whether it launched one, so the caller does not open a second.

The stored value is a name for a screen, never a promise it still exists. `openDefaultFolder()` asks
`isStartupTargetGone` first and resets the setting to Albums for a deleted folder, or for a target
the setting has stopped offering (folder groups, the recycle bin — both were offered once). Choosing
Pictures is `Config.showAll` turned on rather than a folder opened.

## What breaks silently

- **The setting decides the pane, not `Config.showAll`.** `showAll` is left pointing at whichever
  pane the app was last closed on; while that leftover decided it, the setting held only until the
  first swap.
- A recreated `MainActivity` must not run the startup screen again — under "don't keep activities"
  that is a screen there is no backing out of.
- The first [tab](tabs.md) is reset to this on every launch; a new tab also opens here.
