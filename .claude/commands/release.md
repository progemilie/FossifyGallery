---
description: Cut a fork release — name it, run the tool, open the PR
argument-hint: [two or three words] [--major|--minor|--patch]
allowed-tools: Bash, Read
---

Cut a release of this fork. Everything mechanical is `.claude/tools/release.py`; the one thing it
cannot work out is **what to call the release**, which is all `$ARGUMENTS` is for.

Run from `dev`, clean and level with `origin/dev` — the tool refuses otherwise.

1. **Read `## [Unreleased]` in `FORK-CHANGELOG.md`.** Those entries are already written and
   reviewed — do not rewrite, reword or reorder them. Read them only to name the release: two or
   three words for what this version is *about*, in the voice of the headings already there —
   `tabs`, `viewer menu`, `thumbnail cache`. Take it from `$ARGUMENTS` if given.
2. **Cut it.**
   ```
   python .claude/tools/release.py cut --name "two or three words"
   ```
   It derives the bump from the changelog (an `### Added` entry → minor, otherwise patch), creates
   `release/vX.Y.Z`, writes `FORK_VERSION_NAME`, stamps `[Unreleased]` with the version and date,
   adds the link definition, leaves a fresh empty `[Unreleased]`, and commits both files as
   `chore(release): fork vX.Y.Z`. Pass `--major` through if `$ARGUMENTS` asks for one.
3. **Push and open the PR**, using the changelog section as the body so the PR and the release read
   the same:
   ```
   git push -u origin release/vX.Y.Z
   python .claude/tools/release.py notes vX.Y.Z > "$TMP/notes.md"
   gh pr create --base dev --title "chore(release): fork vX.Y.Z" --body-file "$TMP/notes.md"
   ```

**That is the end of it.** Merging the PR is the user's call, and `.github/workflows/fork-release.yml`
does the rest: it sees `gradle.properties` change on `dev`, re-checks the changelog with
`release.py verify`, tags the merged commit and publishes the GitHub release. Nothing here tags
anything — a squash merge rewrites the sha, so a tag made now would name a commit about to be
deleted.

## Reporting back

**Two lines, and nothing else** when it goes to plan — the version and the PR link:

```
Bumped to v1.20.0 (minor). PR: https://github.com/progemilie/FossifyGallery/pull/12
```

Do not summarise the changelog, restate the entries, list the files, explain the workflow, or
describe what happens next. The user wrote the changelog and knows what is in it.

Add a line only when there is genuinely something to know — the bump was not the one the entries
implied, `--major` was used, `verify` warned, or `[Unreleased]` already holds entries for the next
cycle.

**If something fails, say what failed and stop.** Do not work around it: a dirty tree, an unpushed
`dev`, an empty `[Unreleased]`, entries outside a `### Added`/`### Changed`/`### Fixed` heading, or a
typo'd heading are each a thing to fix in the changelog or in git, not in the tool. Report the tool's
own message and what it points at, then let the user decide.
