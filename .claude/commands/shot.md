---
description: Screenshot the emulator, optionally cropped to one view
argument-hint: [resource-id or visible text to crop to]
allowed-tools: Bash, Read
---

Look at what is on screen.

- No argument: `python .claude/tools/emu.py shot`
- With one: `python .claude/tools/emu.py shot --crop id=$ARGUMENTS`

Then Read the file the command names. It is half size — about a third of the tokens of a full grab
and legible for layout, spacing and colour. Reach for `--full` only when what is being judged is
genuinely finer than that, and prefer a crop over a full grab whenever the question is about one
part of the screen.

If nothing on screen matches the argument, run `python .claude/tools/emu.py ui --filter $ARGUMENTS`
to find what it is actually called rather than guessing again.
