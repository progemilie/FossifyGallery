---
description: Cut a fork release — bump the version, stamp the changelog, commit, open the PR, then tag
argument-hint: [major|minor|patch] [two or three words] | --tag
allowed-tools: Bash, Read, Edit
---

Cut a release of this fork. `$ARGUMENTS` may name the bump and the heading's two or three words;
work both out from the commits when it does not.

`--tag` on its own skips to **Once the PR is merged** below — use it when the release PR is already in.

Run from `dev`, clean and level with `origin/dev`. Stop and say so otherwise: the version is written
here and nowhere else, so a dirty tree or an unpulled `origin/dev` means the number would be a guess.

1. **Read the cycle.** `git describe --tags --abbrev=0` for the last release, then
   `git log <tag>..dev --format=%s` for what it holds. What ships is `FORK-CHANGELOG.md`'s
   `## [Unreleased]` section — if it is empty, there is nothing to release, so stop and say which
   commits landed without an entry.
2. **Decide the bump.** Any `feat:` in that range → **minor**. Only `fix:`/`tweak:`/`refactor:`/
   `perf:`/`chore:`/`docs:` → **patch**. Major is never automatic — `$ARGUMENTS` has to ask. Say
   which rule fired and which commits fired it.
3. **Branch.** `git switch -c release/vX.Y.Z`. The bump belongs to the `dev` line, and this branch
   exists only to carry the PR onto it.
4. **Write the version.** `FORK_VERSION_NAME` in `gradle.properties`. Upstream's `VERSION_NAME` and
   `VERSION_CODE` are left alone — check the diff touches neither.
5. **Stamp the changelog.** Rename `## [Unreleased]` to
   `## [vX.Y.Z] - YYYY.MM.DD — two or three words`, today's date with dots. Then edit what the
   section collected: fold duplicates together, bold the notable lines, drop anything not
   user-facing, and match the voice of the entries already there — what the user can now do, not
   what the code now does. Add
   `[vX.Y.Z]: https://github.com/progemilie/FossifyGallery/releases/tag/vX.Y.Z` at the top of the
   link definitions, and leave a fresh empty `## [Unreleased]` above the new heading for the next
   cycle to collect into.
6. **Commit those two files**, subject `chore(release): fork vX.Y.Z`. No body beyond the
   `Co-Authored-By:` trailer — the changelog carries the detail. The subject says *fork* because
   upstream's own releases read `chore(release): vX.Y.Z (N)` and the two sit side by side in the log
   after every sync.
7. **Push and open the PR** into `dev`, titled as the commit, with the version's changelog section
   as the body — the bullets only, without the heading or the link definition.

**Do not tag here.** The tag has to name the commit that ends up on `dev`, and a squashed or rebased
merge gives it a new sha, leaving the tag on a branch that is about to be deleted.

**Once the PR is merged:**

```
git switch dev && git pull
git tag vX.Y.Z          # dev's tip, the release commit
git push origin vX.Y.Z
```

Confirm the tag names a `chore(release):` commit before pushing it, and that the version in its
`gradle.properties` matches the tag.
