# Copying and moving

Every copy and move goes through `copyMoveFilesToFolder()`, which hands the files to the fork's
`copyMoveFiles()` rather than commons' `copyMoveFilesTo()`. The fork's is a port of commons', kept
step for step, made only so the toasts saying a copy or move has begun and has succeeded follow
Settings → Files & recycle bin → "Show messages when copying or moving" (`Config.showCopyMoveToasts`,
off by default). Failures and partial successes are always told.

## Coupled with

Read these before changing this feature — each can break silently if this one changes without it.

- [hold-choosers](hold-choosers.md) — the quick chooser's recents are recorded in `copyMoveFilesToFolder()`.
- [folder-picker](folder-picker.md) — the dialog's destination comes in through `tryCopyMoveFilesTo()`.

## Where it lives

- `extensions/Activity.kt` → `tryCopyMoveFilesTo`, `copyMoveFilesToFolder`.
- `extensions/CopyMove.kt` — `copyMoveFiles`, and `CopyMoveReport`, the listener that toasts.

## How it works

The copying itself is still commons' `CopyMoveTask`, with its progress notification after three
seconds, and with commons asking for the notification permission first - a copy or move refused that
permission does not run. Only what starts the task and listens to it is the fork's. A move within
internal storage never reaches the task: it renames the files in place (`renameIntoFolder`).

## What breaks silently

- **`CopyMoveTask` holds its listener only weakly.** Commons' listener lives on the activity; the
  fork's is held in `CopyMoveReport.held` from the task's start until it reports, or it can be
  collected mid-copy and the copy then finishes without a toast or the caller's callback.
- **A commons update can change `copyMoveFilesTo` underneath the port** - compare the two when
  commons is bumped.
