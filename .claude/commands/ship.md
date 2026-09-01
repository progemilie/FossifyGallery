---
description: Bump the fork version, write the changelog entry, commit and tag
argument-hint: [feature|fix] <one-line summary>
allowed-tools: Bash, Read, Edit
---

Get this branch ready for a PR. `$ARGUMENTS` says whether it is a feature or a fix, and what to
call it.

1. **Check the bump.** `git diff dev...HEAD -- gradle.properties`. A branch carries at most one
   bump. If it has none, raise `FORK_VERSION_NAME` in `gradle.properties` — minor for a feature,
   patch for a fix — and commit it with the change it belongs to if that commit is still the tip;
   otherwise commit it on its own and say so.
2. **Write the changelog.** Add a section to the top of `FORK-CHANGELOG.md`:
   `## [vX.Y.Z] - YYYY.MM.DD — two or three words`, then the user-facing changes as bullets, the
   notable ones in bold. Internal fixes are left out unless they are major. Match the voice of the
   entries already there — what the user can now do, not what the code now does. Add the link
   definition at the foot: `[vX.Y.Z]: https://github.com/progemilie/FossifyGallery/releases/tag/vX.Y.Z`.
3. **Commit it last.** The changelog is the final commit before the PR, subject `docs: …`.
4. **Tag the bump commit** `vX.Y.Z`.

Stop there. Push and open the PR only if asked — and the PR goes to **`dev`**, with a title in the
same `feat:`/`fix:` form as the commits.
