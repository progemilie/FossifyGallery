---
description: Run detekt and Android Lint, reporting only new findings
allowed-tools: Bash
---

Run `./gradlew detekt lintFossDebug --console=plain 2>&1 | tail -40`.

Both use baseline files (`app/detekt-baseline.xml`, `app/lint-baseline.xml`) that already cover
everything inherited, so anything reported is new and belongs to the current branch. Report only
those, each as file:line plus the rule. If both pass, say so in one line.

Do not add to a baseline to make a finding go away.
