#!/usr/bin/env python3
"""Read an ART method trace and say where the time went.

`am profile start` writes the old dmtrace format: a text header naming every
thread and method, then fixed-width binary records of method entry and exit.
This turns that into per-method totals -- call count, self time, inclusive time
-- so a profile can be read as a table rather than opened in a GUI.

Used as a module by perf.py; runnable on its own against a pulled .trace file.
"""

import argparse
import os
import struct
import sys
from collections import defaultdict

MAGIC = 0x574F4C53  # 'SLOW'
ENTER, EXIT, UNWIND = 0, 1, 2
OURS = "org.fossify.gallery."
# Frames that say nothing on their own: the cost is always in what they called.
NOISE = ("java.lang.reflect.", "kotlin.jvm.internal.Intrinsics")


class Trace:
    def __init__(self, props, threads, methods, records):
        self.props = props
        self.threads = threads      # tid -> name
        self.methods = methods      # id -> "Class.method"
        self.records = records      # (tid, method_id, action, wall_usec)

    @property
    def sampled(self):
        return "sampling-interval-us" in self.props or self.props.get("sampled") == "true"

    @property
    def overflowed(self):
        return self.props.get("data-file-overflow") == "true"

    @property
    def elapsed_ms(self):
        """Measured off the records: the header's own figure wraps when a trace is cut short."""
        walls = [w for _, _, _, w in self.records]
        return (max(walls) - min(walls)) / 1000 if walls else 0


def parse(path):
    raw = open(path, "rb").read()
    end = raw.find(b"*end\n")
    if raw[:8] != b"*version" or end < 0:
        sys.exit(f"{path} is not an ART method trace (no *version/*end header)")

    props, threads, methods = {}, {}, {}
    section = None
    for line in raw[:end].decode("utf-8", errors="replace").splitlines():
        if line.startswith("*"):
            section = line
            continue
        if section == "*version":
            if "=" in line:
                k, v = line.split("=", 1)
                props[k] = v
        elif section == "*threads":
            tid, _, name = line.partition("\t")
            if tid.strip().isdigit():
                threads[int(tid)] = name
        elif section == "*methods":
            f = line.split("\t")
            if len(f) >= 3:
                methods[int(f[0], 16)] = f"{f[1]}.{f[2]}"

    b = end + len("*end\n")
    magic, version, offset, _start = struct.unpack_from("<IHHQ", raw, b)
    if magic != MAGIC:
        sys.exit(f"{path}: bad binary magic {magic:#x}, expected 'SLOW'")
    size = struct.unpack_from("<H", raw, b + 16)[0] if version >= 3 else 9

    # tid, then method|action, then the clocks the header says are present
    clock = props.get("clock", "wall")
    tid_fmt = "B" if version == 1 else "H"
    body = raw[b + offset:]
    records = []
    for off in range(0, len(body) - size + 1, size):
        r = body[off:off + size]
        tid, ma = struct.unpack_from("<" + tid_fmt + "I", r, 0)
        at = struct.calcsize("<" + tid_fmt + "I")
        # the wall clock is the first time field whatever the clock setting -- with
        # clock=dual the second one counts far past the length of the run and is not it
        wall = struct.unpack_from("<I", r, at)[0]
        records.append((tid, ma & ~0x3, ma & 0x3, wall))
    return Trace(props, threads, methods, records)


def totals(trace, threads=None):
    """Walk the enter/exit pairs into per-method count, self and inclusive time.

    Inclusive time is only credited when a method returns to depth zero, or a
    recursive call would count the same microseconds once per frame.
    """
    count = defaultdict(int)
    self_us = defaultdict(int)
    incl_us = defaultdict(int)
    stacks = defaultdict(list)      # tid -> [[method, entered, children_us], ...]
    depth = defaultdict(lambda: defaultdict(int))
    last = {}

    for tid, method, action, wall in trace.records:
        if threads and tid not in threads:
            continue
        last[tid] = wall
        stack = stacks[tid]
        if action == ENTER:
            count[method] += 1
            depth[tid][method] += 1
            stack.append([method, wall, 0])
            continue
        # an exit with nothing open, or for something further down, is a trace
        # that started mid-call: unwind to it rather than dropping the frame
        if not any(f[0] == method for f in stack):
            continue
        while stack:
            m, entered, children = stack.pop()
            elapsed = max(wall - entered, 0)
            self_us[m] += elapsed - children
            depth[tid][m] -= 1
            if depth[tid][m] == 0:
                incl_us[m] += elapsed
            if stack:
                stack[-1][2] += elapsed
            if m == method:
                break

    # whatever was still on the stack when the trace stopped
    for tid, stack in stacks.items():
        while stack:
            m, entered, children = stack.pop()
            elapsed = max(last.get(tid, entered) - entered, 0)
            self_us[m] += elapsed - children
            depth[tid][m] -= 1
            if depth[tid][m] == 0:
                incl_us[m] += elapsed
            if stack:
                stack[-1][2] += elapsed

    return {m: (count[m], self_us[m], incl_us[m]) for m in count}


def by_name(trace, rows, keep_all=False):
    """Merge method ids that share a name, and say what share of the whole run each is.

    A Kotlin override reaches the trace twice -- once as itself and once as the
    bridge method the framework calls -- under one name. Counts and self time
    add up across the two; inclusive time does not, because the bridge's call
    sits inside the real one's, so the larger of the two is the honest figure.
    """
    total_self = sum(r[1] for r in rows.values()) or 1
    merged = {}
    for method, (n, self_us, incl_us) in rows.items():
        name = trace.methods.get(method, f"<{method:#x}>")
        if not keep_all and (not name.startswith(OURS) or name.startswith(NOISE)):
            continue
        was = merged.get(name)
        merged[name] = (
            n + was[0], self_us + was[1], max(incl_us, was[2])
        ) if was else (n, self_us, incl_us)
    return [(name, *v) for name, v in merged.items()], total_self


def table(rows, total_self, top, by="self", title=""):
    key = {"self": 2, "incl": 3, "count": 1}[by]
    rows = sorted(rows, key=lambda r: -r[key])
    lines = [title] if title else []
    lines.append(f"{'calls':>8} {'self ms':>9} {'incl ms':>9} {'avg us':>8}  {'%':>5}  method")
    for name, n, self_us, incl_us in rows[:top]:
        lines.append(
            f"{n:>8} {self_us / 1000:>9.1f} {incl_us / 1000:>9.1f} "
            f"{self_us / max(n, 1):>8.0f}  {100 * self_us / total_self:>5.1f}  {name}"
        )
    kept = sum(r[2] for r in rows)
    lines.append(f"-- {min(top, len(rows))} of {len(rows)} methods; "
                 f"{kept / 1000:.0f} ms of the run's {total_self / 1000:.0f} ms of self time")
    return "\n".join(lines)


def main():
    p = argparse.ArgumentParser(description=__doc__,
                                formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("trace")
    p.add_argument("--top", type=int, default=25)
    p.add_argument("--by", choices=["self", "incl", "count"], default="self")
    p.add_argument("--all", action="store_true", help="framework and library frames too")
    p.add_argument("--thread", help="only this thread, by name (e.g. main)")
    a = p.parse_args()

    if not os.path.exists(a.trace):
        sys.exit(f"no such file: {a.trace}")
    t = parse(a.trace)
    tids = None
    if a.thread:
        tids = {tid for tid, name in t.threads.items() if a.thread in name}
        if not tids:
            sys.exit(f"no thread named {a.thread}; have: {', '.join(sorted(t.threads.values()))}")
    print(f"{t.elapsed_ms:.0f} ms, {len(t.records)} records, {len(t.threads)} threads, "
          f"clock={t.props.get('clock')}")
    if t.overflowed:
        print("!! the trace buffer filled and the trace is cut short -- capture for less time")
    rows, total = by_name(t, totals(t, tids), a.all)
    print(table(rows, total, a.top, a.by))


if __name__ == "__main__":
    main()
