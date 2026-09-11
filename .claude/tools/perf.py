#!/usr/bin/env python3
"""Measure the Fossify Gallery debug build: what costs time, what costs memory,
and what fires more often than it should.

Companion to emu.py, and the same bargain: every subcommand prints a digest
rather than a dump. It borrows emu.py's adb wrapper, so device paths never pass
through Git Bash and need no MSYS_NO_PATHCONV.

    perf.py methods --swipes 3      what ran, how often, and for how long
    perf.py jank --swipes 3         frame times across an interaction
    perf.py mem --watch 6           where memory goes, and whether it comes back
    perf.py trace --seconds 5       a Perfetto trace for Android Studio
    perf.py counters                the app's own counters (needs Perf.kt)

Every command measures an interaction: `--swipes N` flings the grid, `--seconds N`
just waits while you drive it by hand, and anything after `--` is run as a command.

Run `perf.py <command> --help` for a command's own options.
"""

import argparse
import os
import subprocess
import sys
import time

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import artrace  # noqa: E402
import emu  # noqa: E402

DEVICE_DIR = "/data/local/tmp"
PERFETTO_DIR = "/data/misc/perfetto-traces"
HERE = os.path.dirname(os.path.abspath(__file__))
RECOVERY_SECONDS = 11


def adb_checked(*args):
    """adb, with stderr and the exit code kept: `am profile` reports failure quietly."""
    res = subprocess.run([emu.ADB, "-s", emu.SERIAL] + [str(a) for a in args],
                         capture_output=True)
    out = (res.stdout + res.stderr).decode("utf-8", errors="replace").strip()
    if res.returncode or "Error" in out or "Exception" in out:
        sys.exit(f"adb {' '.join(str(a) for a in args)} failed:\n{out}")
    return out


def pid():
    out = emu.sh("pidof", emu.PKG).strip()
    if not out:
        sys.exit(f"{emu.PKG} is not running -- try: emu.py launch")
    return out.split()[0]


def during(a):
    """Run whatever is being measured, or simply wait while it is driven by hand."""
    if a.swipes:
        for _ in range(a.swipes):
            subprocess.run([sys.executable, os.path.join(HERE, "emu.py"),
                            "swipe", "--dir", a.dir, "--ms", str(a.ms)],
                           capture_output=True)
    elif a.cmd:
        subprocess.run(a.cmd)
    else:
        time.sleep(a.seconds)


def add_interaction_args(s, seconds=2.0):
    s.add_argument("--seconds", type=float, default=seconds, help="wait this long")
    s.add_argument("--swipes", type=int, default=0, help="fling the grid this many times")
    s.add_argument("--dir", default="up", choices=["up", "down", "left", "right"])
    s.add_argument("--ms", type=int, default=300, help="duration of each swipe")
    s.add_argument("cmd", nargs="*", help="or, after --, a command to run while measuring")


# --------------------------------------------------------------------- methods

def cmd_methods(a):
    """Instrumented ART tracing: every call is recorded, so the counts are exact.

    Exact, but not free. Recording a call costs a couple of microseconds, so the app runs perhaps
    ten times slower and methods called very often look dearer than they are. Read the counts as
    truth and the times as a ranking.
    """
    pid()  # only to say something useful when the app is not running
    remote = f"{DEVICE_DIR}/methods.trace"
    local = os.path.join(emu.out_dir(a.out_dir), "methods.trace")

    # `am profile` fails quietly in three separate ways, each of which costs a run: given a pid it
    # records nothing (so: by process name), --clock-type dual writes an empty file, and a start
    # that follows a stop is liable to be swallowed. Starting twice papers over the last of those
    # but produces something worse -- a trace missing every thread that was already running, the
    # main one included. So: start once, check what came back, and repeat the whole measurement
    # if it is empty or has no main thread in it.
    for attempt in range(1, a.tries + 1):
        if attempt > 1:
            # ART needs roughly this long between sessions: measured empty at 2s and 5s of
            # idle and complete at 10s. Retrying any sooner just burns the interaction.
            print(f"waiting {RECOVERY_SECONDS}s for tracing to come back")
            time.sleep(RECOVERY_SECONDS)
        emu.sh("rm", "-f", remote)
        adb_checked("shell", "am", "profile", "start", emu.PKG, remote)
        try:
            during(a)
        finally:
            emu.sh("am", "profile", "stop", emu.PKG)
        emu.adb("pull", remote, local, check=True)
        if os.path.getsize(local) == 0:
            print(f"attempt {attempt}: empty trace, repeating the interaction")
            continue
        t = artrace.parse(local)
        if not usable(t, a.thread):
            print(f"attempt {attempt}: trace missing the {a.thread or 'main'} thread, repeating")
            continue
        break
    else:
        sys.exit(f"no usable trace after {a.tries} attempts -- is the app still in the foreground?")

    size = os.path.getsize(local)
    print(f"{t.elapsed_ms:.0f} ms of app time, {len(t.records)} calls recorded, "
          f"{size / 1e6:.1f} MB  ({local})")
    if t.overflowed:
        print("!! the buffer filled and the trace stops early -- measure a shorter interaction")
    tids = None
    if a.thread:
        tids = {tid for tid, name in t.threads.items() if a.thread in name}
    rows, total = artrace.by_name(t, artrace.totals(t, tids), a.all)
    print(artrace.table(rows, total, a.top, a.by))


def usable(t, want=None):
    """A trace with no records for the thread being asked about measured nothing worth reading."""
    wanted = want or "main"
    tids = {tid for tid, name in t.threads.items() if wanted in name}
    return any(tid in tids for tid, _, _, _ in t.records)


# ------------------------------------------------------------------------ jank

JANK_KEYS = (
    "Total frames rendered", "Janky frames", "50th percentile", "90th percentile",
    "95th percentile", "99th percentile", "Number Missed Vsync",
    "Number High input latency", "Number Slow UI thread", "Number Slow bitmap uploads",
    "Number Slow issue draw commands",
)


def gfxinfo():
    found = {}
    for line in emu.sh("dumpsys", "gfxinfo", emu.PKG).splitlines():
        line = line.strip()
        for k in JANK_KEYS:
            if line.startswith(k + ":"):
                found[k] = line.split(":", 1)[1].strip()
    return found


def cmd_jank(a):
    if a.read:
        stats = gfxinfo()
    else:
        emu.sh("dumpsys", "gfxinfo", emu.PKG, "reset")
        if a.reset:
            print("frame counters reset -- drive the app, then: perf.py jank --read")
            return
        during(a)
        stats = gfxinfo()
    if int(stats.get("Total frames rendered", "0") or 0) == 0:
        print("no frames were drawn: nothing on screen moved")
        return
    for k in JANK_KEYS:
        if k in stats:
            print(f"  {k:<32} {stats[k]}")
    print("-- percentiles are whole-frame time; over 16ms missed a vsync at 60Hz.")
    # this emulator files most frames in a 4950ms GPU bucket, so its GPU percentiles
    # are meaningless and are deliberately not reported
    print("   GPU percentiles are omitted: the emulator does not report them usefully.")


# ------------------------------------------------------------------------- mem

SUMMARY_ROWS = ("Java Heap", "Native Heap", "Code", "Stack", "Graphics",
                "Private Other", "System", "TOTAL PSS", "TOTAL RSS")


def meminfo():
    out = {}
    in_summary = False
    for line in emu.sh("dumpsys", "meminfo", emu.PKG).splitlines():
        if "App Summary" in line:
            in_summary = True
            continue
        s = line.strip()
        if s.startswith("TOTAL PSS:") or s.startswith("TOTAL RSS:"):
            parts = s.split()
            if len(parts) >= 3 and parts[2].isdigit():
                out[parts[0] + " " + parts[1].rstrip(":")] = int(parts[2])
        elif in_summary and ":" in s:
            label, _, rest = s.partition(":")
            nums = rest.split()
            if nums and nums[0].lstrip("-").isdigit():
                out[label.strip()] = int(nums[0])
    return out


def cmd_mem(a):
    rows = []
    for i in range(a.watch):
        rows.append(meminfo())
        if i + 1 < a.watch:
            time.sleep(a.every)
    if not rows[0]:
        sys.exit(f"no memory info for {emu.PKG} -- is it running?")
    first, last = rows[0], rows[-1]
    keys = [k for k in SUMMARY_ROWS if k in first] or sorted(first)
    span = a.every * (a.watch - 1)
    print(f"{emu.PKG}  {a.watch} sample(s) over {span:.0f}s, in KB")
    print(f"  {'':<16}{'first':>10}{'last':>10}{'delta':>10}{'peak':>10}")
    for k in keys:
        vals = [r.get(k, 0) for r in rows]
        print(f"  {k:<16}{first.get(k, 0):>10}{last.get(k, 0):>10}"
              f"{last.get(k, 0) - first.get(k, 0):>+10}{max(vals):>10}")
    if a.watch > 1:
        print("-- a delta that keeps climbing over longer runs is the one worth chasing")
    if a.dump:
        remote = f"{DEVICE_DIR}/heap.hprof"
        emu.sh("am", "dumpheap", emu.PKG, remote)
        time.sleep(3)
        local = os.path.join(emu.out_dir(a.out_dir), "heap.hprof")
        emu.adb("pull", remote, local, check=True)
        print(f"heap dump: {local}  (Android Studio: File > Open)")


# ----------------------------------------------------------------------- trace

CONFIG = """
buffers {{ size_kb: 131072 fill_policy: RING_BUFFER }}
data_sources {{
  config {{
    name: "linux.ftrace"
    ftrace_config {{
      ftrace_events: "sched/sched_switch"
      atrace_categories: "gfx"
      atrace_categories: "view"
      atrace_categories: "am"
      atrace_categories: "res"
      atrace_categories: "dalvik"
      atrace_apps: "{pkg}"
    }}
  }}
}}
data_sources {{ config {{ name: "linux.process_stats" }} }}
duration_ms: {ms}
"""


def cmd_trace(a):
    remote = f"{PERFETTO_DIR}/gallery.perfetto-trace"
    cfg = CONFIG.format(pkg=emu.PKG, ms=int(a.seconds * 1000)).encode()
    print(f"recording {a.seconds:.0f}s...")
    said = emu.adb("shell", "perfetto", "--txt", "-c", "-", "-o", remote, stdin=cfg)
    local = os.path.join(emu.out_dir(a.out_dir), "gallery.perfetto-trace")
    emu.adb("pull", remote, local)
    if not os.path.exists(local) or os.path.getsize(local) == 0:
        sys.exit(f"perfetto wrote nothing:\n{said.strip()}")
    print(f"{os.path.getsize(local) / 1e6:.1f} MB -> {local}")
    print("-- open at https://ui.perfetto.dev or in Android Studio. Sections named by")
    print("   Perf.section() appear as slices under the app's main thread.")


# -------------------------------------------------------------------- counters

def cmd_counters(a):
    action = "PERF_RESET" if a.reset else "PERF_DUMP"
    emu.adb("logcat", "-c", "-b", "main")
    emu.sh("am", "broadcast", "-a", f"{emu.PKG}.{action}", "-p", emu.PKG)
    if a.reset:
        print("counters reset")
        return
    time.sleep(0.5)
    lines = emu.adb("logcat", "-d", "-s", "Perf:D").splitlines()
    body = [ln.split(": ", 1)[1] for ln in lines if ": " in ln and "Perf" in ln]
    if not body:
        sys.exit("nothing came back -- this build has no Perf.kt, or nothing has been counted")
    for ln in body[-a.lines:]:
        print(ln)


# ------------------------------------------------------------------------ main

def main():
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--serial", default=emu.SERIAL, help="device (default %s)" % emu.SERIAL)
    p.add_argument("--pkg", default=emu.PKG, help="package (default %s)" % emu.PKG)
    p.add_argument("--out-dir", help="where traces and dumps are written")
    sub = p.add_subparsers(dest="cmd", required=True)

    def add(name, fn, help):
        s = sub.add_parser(name, help=help)
        s.set_defaults(fn=fn)
        return s

    s = add("methods", cmd_methods, "call counts and times, from an ART method trace")
    add_interaction_args(s, seconds=1.0)
    s.add_argument("--by", choices=["count", "self", "incl"], default="count")
    s.add_argument("--top", type=int, default=20)
    s.add_argument("--all", action="store_true", help="framework and library frames too")
    s.add_argument("--thread", help="only this thread, e.g. main")
    s.add_argument("--tries", type=int, default=3,
                   help="repeats allowed while chasing a usable trace; back-to-back traces "
                        "need about %ds between them" % RECOVERY_SECONDS)

    s = add("jank", cmd_jank, "frame times across an interaction")
    add_interaction_args(s)
    s.add_argument("--reset", action="store_true", help="zero the counters and stop")
    s.add_argument("--read", action="store_true", help="report without measuring anything new")

    s = add("mem", cmd_mem, "memory by category, sampled")
    s.add_argument("--watch", type=int, default=1, help="how many samples")
    s.add_argument("--every", type=float, default=5.0, help="seconds between samples")
    s.add_argument("--dump", action="store_true", help="also pull a heap dump for Studio")

    s = add("trace", cmd_trace, "a Perfetto system trace, for Studio or ui.perfetto.dev")
    s.add_argument("--seconds", type=float, default=5.0)

    s = add("counters", cmd_counters, "the app's own Perf counters")
    s.add_argument("--reset", action="store_true")
    s.add_argument("-n", "--lines", type=int, default=40)

    a = p.parse_args()
    emu.SERIAL, emu.PKG = a.serial, a.pkg
    a.fn(a)


if __name__ == "__main__":
    main()
