---
description: Build the foss debug APK, install it on the emulator, and launch it
allowed-tools: Bash
---

Put the current working tree on the emulator, and report only what went wrong.

1. `python .claude/tools/emu.py install --build` — assembles `assembleFossDebug` and installs the
   APK it finds by glob. On failure it prints the `e:` lines and nothing else.
2. `python .claude/tools/emu.py launch --fresh` — it should report `MainActivity`.
3. `python .claude/tools/emu.py logcat --since-launch` — it should report no crashes.

Say in one line whether the app is up. Never paste gradle output that is not an error.
