#!/usr/bin/env python3
"""Drive the Fossify Gallery debug build on the emulator.

Every subcommand prints a digest, never a dump: `ui` summarises the view tree in
a few dozen lines instead of 200 KB of XML, `shot` halves the screenshot before
saving it, `logcat` shows crashes unless asked for more. adb is called with list
arguments straight from Python, so device paths never pass through Git Bash and
need no MSYS_NO_PATHCONV.

Run `emu.py <command> --help` for a command's own options.
"""

import argparse
import io
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET

PKG = os.environ.get("EMU_PKG", "org.fossify.gallery.debug")
SERIAL = os.environ.get("EMU_SERIAL", "emulator-5554")
APK_GLOB = "app/build/outputs/apk/foss/debug/*.apk"
# The launcher entry is a per-theme activity-alias (SplashActivity.Pink, .Red, ...)
# and the bare SplashActivity is disabled, so a launch goes through the category.
LAUNCH_CATEGORY = "android.intent.category.LAUNCHER"


def adb_path():
    found = shutil.which("adb")
    if found:
        return found
    fallback = os.path.join(
        os.environ.get("LOCALAPPDATA", ""), "Android", "Sdk", "platform-tools", "adb.exe"
    )
    if os.path.exists(fallback):
        return fallback
    sys.exit("adb not found on PATH or under %LOCALAPPDATA%/Android/Sdk/platform-tools")


ADB = adb_path()


def adb(*args, binary=False, stdin=None, check=False):
    """Run adb and return stdout (str, or bytes when binary)."""
    cmd = [ADB, "-s", SERIAL] + [str(a) for a in args]
    res = subprocess.run(cmd, capture_output=True, input=stdin)
    if check and res.returncode != 0:
        sys.exit(f"adb {' '.join(str(a) for a in args)} failed:\n{res.stderr.decode(errors='replace')}")
    if binary:
        return res.stdout
    return res.stdout.decode("utf-8", errors="replace").replace("\r\n", "\n")


def sh(*args, **kw):
    return adb("shell", *args, **kw)


def out_dir(explicit=None):
    d = explicit or os.environ.get("CLAUDE_SCRATCHPAD") or os.path.join(tempfile.gettempdir(), "emu")
    os.makedirs(d, exist_ok=True)
    return d


def tokens(w, h):
    """Roughly what an image of this size costs to look at.

    Anything longer than 2000px is scaled down before it is shown, so a
    full-resolution grab costs no more than that -- but nothing less either.
    """
    longest = max(w, h)
    if longest > 2000:
        w, h = w * 2000 / longest, h * 2000 / longest
    return int(w * h / 750)


def screen_size():
    m = re.search(r"(\d+)x(\d+)", sh("wm", "size"))
    return (int(m.group(1)), int(m.group(2))) if m else (1080, 2424)


# --------------------------------------------------------------------------- ui

def dump_xml():
    """The view tree. Straight to stdout where the device allows it."""
    raw = adb("exec-out", "uiautomator", "dump", "/dev/tty", binary=True)
    text = raw.decode("utf-8", errors="replace")
    if "<hierarchy" not in text:
        sh("uiautomator", "dump", "/sdcard/ui.xml")
        text = adb("exec-out", "cat", "/sdcard/ui.xml", binary=True).decode("utf-8", errors="replace")
    start = text.find("<hierarchy")
    end = text.rfind("</hierarchy>")
    if start < 0 or end < 0:
        sys.exit("could not read the view tree (is the screen on?)")
    return text[start:end + len("</hierarchy>")]


def bounds_of(node):
    m = re.match(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.get("bounds", ""))
    return tuple(int(g) for g in m.groups()) if m else None


def short_id(node):
    rid = node.get("resource-id", "")
    return rid.split("/")[-1] if rid else ""


def nodes(include_all=False):
    """Every node worth naming: one with an id, a label, or something to tap."""
    root = ET.fromstring(dump_xml())
    keep = []
    for n in root.iter("node"):
        b = bounds_of(n)
        if not b or b[2] <= b[0] or b[3] <= b[1]:
            continue
        interesting = (
            short_id(n) or n.get("text") or n.get("content-desc") or n.get("clickable") == "true"
        )
        if include_all or interesting:
            keep.append((n, b))
    return keep


def cmd_ui(a):
    rows = []
    for n, b in nodes(a.all):
        if a.clickable and n.get("clickable") != "true":
            continue
        label = n.get("text") or n.get("content-desc") or ""
        if a.filter and a.filter.lower() not in (short_id(n) + label).lower():
            continue
        cls = n.get("class", "").rsplit(".", 1)[-1]
        rows.append((short_id(n), label.replace("\n", " ")[:30], b, cls))
    if not rows:
        print("no matching nodes")
        return
    for rid, label, b, cls in rows:
        print(f"{rid:<30} {label:<32} [{b[0]},{b[1]}][{b[2]},{b[3]}]  {cls}")
    print(f"-- {len(rows)} nodes")


def resolve(target):
    """A resource id, some visible text, or literal x,y -> a centre point."""
    if re.fullmatch(r"\d+\s*,\s*\d+", target):
        x, y = (int(v) for v in target.split(","))
        return x, y, None
    found = nodes()
    for match in (
        lambda n: short_id(n) == target,
        lambda n: n.get("text") == target or n.get("content-desc") == target,
        lambda n: target.lower() in short_id(n).lower(),
        lambda n: target.lower() in (n.get("text", "") + n.get("content-desc", "")).lower(),
    ):
        hits = [(n, b) for n, b in found if match(n)]
        if hits:
            n, b = hits[0]
            extra = f" ({len(hits)} matches, took the first)" if len(hits) > 1 else ""
            return (b[0] + b[2]) // 2, (b[1] + b[3]) // 2, (b, short_id(n), extra)
    sys.exit(f"nothing on screen matches {target!r} -- try: emu.py ui --filter {target}")


def cmd_find(a):
    x, y, info = resolve(a.target)
    if info is None:
        print(f"{x},{y}")
        return
    b, rid, extra = info
    print(f"{rid or a.target}  [{b[0]},{b[1]}][{b[2]},{b[3]}]  centre {x},{y}  "
          f"{b[2] - b[0]}x{b[3] - b[1]}{extra}")


# ------------------------------------------------------------------------ input

def cmd_tap(a):
    x, y, info = resolve(a.target)
    sh("input", "tap", x, y)
    where = f" ({info[1]})" if info and info[1] else ""
    print(f"tapped {x},{y}{where}")
    if a.settle:
        wait_idle(a.settle)


def cmd_swipe(a):
    w, h = screen_size()
    if a.points:
        x1, y1, x2, y2 = (int(v) for v in re.split(r"[ ,]+", a.points.strip()))
    else:
        cx, cy = w // 2, h // 2
        dx, dy = int(w * a.frac / 2), int(h * a.frac / 2)
        vec = {"up": (0, -dy), "down": (0, dy), "left": (-dx, 0), "right": (dx, 0)}[a.dir]
        x1, y1 = cx - vec[0] // 2, cy - vec[1] // 2
        x2, y2 = x1 + vec[0], y1 + vec[1]
    sh("input", "swipe", x1, y1, x2, y2, a.ms)
    print(f"swiped {x1},{y1} -> {x2},{y2} in {a.ms}ms")


def cmd_key(a):
    name = a.key if a.key.startswith("KEYCODE_") else "KEYCODE_" + a.key.upper()
    sh("input", "keyevent", name)
    print(name)


def cmd_text(a):
    sh("input", "text", a.text.replace(" ", "%s"))
    print("typed " + repr(a.text))


# ------------------------------------------------------------ launch and waiting

def focused():
    """The focused activity, or None while a transition holds no window."""
    # "window d" carries mCurrentFocus in half the bytes of the full window dump
    m = re.search(r"mCurrentFocus=Window\{[^}]*?\s(\S+)/(\S+?)\}", sh("dumpsys", "window", "d"))
    return m.group(1) + "/" + m.group(2) if m else None


def wait_idle(timeout=8.0):
    """Poll until a real window has held focus briefly, rather than sleeping blind.

    Focus is null mid-transition and while a splash is up, so null never counts
    as settled -- otherwise a launch reports done before the app is on screen.
    """
    deadline = time.time() + timeout
    last, stable_since = None, time.time()
    while time.time() < deadline:
        now = focused()
        if now != last:
            last, stable_since = now, time.time()
        elif now is not None and time.time() - stable_since > 0.6:
            return now
        time.sleep(0.25)
    return last or "(nothing focused)"


def cmd_idle(a):
    print(wait_idle(a.timeout))


def cmd_focus(a):
    print(focused() or "(nothing focused -- mid transition?)")


def cmd_launch(a):
    if a.clear:
        sh("pm", "clear", PKG)
        # a cleared app has no permissions left, and is useless without them
        sh("pm", "grant", PKG, "android.permission.READ_MEDIA_IMAGES")
        sh("pm", "grant", PKG, "android.permission.READ_MEDIA_VIDEO")
        sh("appops", "set", PKG, "MANAGE_EXTERNAL_STORAGE", "allow")
    if a.fresh or a.clear:
        sh("am", "force-stop", PKG)
    if a.activity:
        name = a.activity if "." in a.activity else ".activities." + a.activity
        sh("am", "start", "-n", PKG + "/" + PKG.replace(".debug", "") + name)
    else:
        sh("monkey", "-p", PKG, "-c", LAUNCH_CATEGORY, "1")
    print(wait_idle(a.timeout))


def cmd_stop(a):
    sh("am", "force-stop", PKG)
    print("stopped " + PKG)


# ----------------------------------------------------------------- screenshots

def grab():
    from PIL import Image
    png = adb("exec-out", "screencap", "-p", binary=True)
    if not png.startswith(b"\x89PNG"):
        sys.exit("screencap returned no image")
    return Image.open(io.BytesIO(png)).convert("RGB")


def crop_spec(img, spec):
    if spec.startswith("id="):
        _, _, info = resolve(spec[3:])
        if info is None:
            sys.exit("--crop id= wants a resource id or some visible text")
        b = info[0]
        return img.crop(b), "%s [%d,%d][%d,%d]" % (info[1], b[0], b[1], b[2], b[3])
    x, y, w, h = (int(v) for v in re.split(r"[ ,]+", spec))
    return img.crop((x, y, x + w, y + h)), "%d,%d %dx%d" % (x, y, w, h)


def save(img, path, note=""):
    img.save(path, optimize=True)
    w, h = img.size
    print("%s  %dx%d  ~%d tokens%s" % (path, w, h, tokens(w, h), note))


def cmd_shot(a):
    img = grab()
    note = ""
    if a.crop:
        img, what = crop_spec(img, a.crop)
        note = "  (cropped to %s)" % what
    scale = 1.0 if a.full else a.scale
    if scale != 1.0:
        img = img.resize((max(1, int(img.width * scale)), max(1, int(img.height * scale))))
    name = a.out or ("shot-" + time.strftime("%H%M%S") + ".png")
    if not name.lower().endswith(".png"):
        name += ".png"
    save(img, os.path.join(out_dir(a.dir), name), note)


def cmd_film(a):
    """A burst of frames tiled into one sheet: a whole transition, looked at once."""
    from PIL import Image, ImageDraw
    trigger = None
    if a.tap:
        # resolve before filming starts: reading the view tree is the slow part,
        # and doing it mid-burst would let the transition finish unseen
        x, y, _ = resolve(a.tap)
        trigger = lambda: sh("input", "tap", x, y)
    elif a.key:
        name = a.key if a.key.startswith("KEYCODE_") else "KEYCODE_" + a.key.upper()
        trigger = lambda: sh("input", "keyevent", name)
    frames = []
    if trigger:
        trigger()
    t0 = time.time()
    for _ in range(a.frames):
        frames.append((int((time.time() - t0) * 1000), grab()))
        time.sleep(a.interval / 1000.0)
    thumbs = [(ms, f.resize((int(f.width * a.scale), int(f.height * a.scale))))
              for ms, f in frames]
    tw, th = thumbs[0][1].size
    cols = a.cols or len(thumbs)
    rows = (len(thumbs) + cols - 1) // cols
    sheet = Image.new("RGB", (cols * tw, rows * (th + 14)), "black")
    draw = ImageDraw.Draw(sheet)
    for i, (ms, t) in enumerate(thumbs):
        x, y = (i % cols) * tw, (i // cols) * (th + 14)
        sheet.paste(t, (x, y + 14))
        draw.text((x + 3, 2), "+%dms" % ms, fill="white")
    save(sheet, os.path.join(out_dir(a.dir), a.out or "film.png"),
         "  (%d frames, %dms apart)" % (len(thumbs), a.interval))


# --------------------------------------------------------------------- logcat

def cmd_logcat(a):
    args = ["logcat", "-d"]
    if a.since_launch:
        pid = sh("pidof", PKG).strip().split(" ")[0]
        if pid:
            args += ["--pid", pid]
    if a.tag:
        args += ["-s", a.tag]
    lines = adb(*args).splitlines()
    if not a.tag and not a.grep:
        # crashes only: what the log is nearly always opened for
        lines = [ln for ln in lines
                 if re.search(r"FATAL EXCEPTION|AndroidRuntime\s*:\s*E|E AndroidRuntime", ln)]
    if a.grep:
        lines = [ln for ln in lines if re.search(a.grep, ln)]
    lines = lines[-a.lines:]
    print("\n".join(lines) if lines else "(nothing matched -- no crashes)")


def cmd_clearlog(a):
    adb("logcat", "-c")
    print("logcat cleared")


# ---------------------------------------------------------------------- prefs

PREF_PATH = "shared_prefs/Prefs.xml"


def read_prefs():
    text = adb("exec-out", "run-as", PKG, "cat", PREF_PATH,
               binary=True).decode("utf-8", errors="replace")
    if "<map" not in text:
        sys.exit("could not read " + PREF_PATH + " (is the debug build installed?)")
    return ET.fromstring(text[text.find("<map"):])


def pref_items(root):
    for el in root:
        value = el.get("value")
        yield el.get("name"), value if value is not None else (el.text or "")


def cmd_prefs(a):
    if a.action in ("get", "dump"):
        for name, value in sorted(pref_items(read_prefs())):
            wanted = a.action == "dump" or not a.pairs or any(
                k.lower() in (name or "").lower() for k in a.pairs)
            if wanted:
                print("%s = %s" % (name, value))
        return
    # the app rewrites Prefs.xml as it exits, so it has to be down before a write
    sh("am", "force-stop", PKG)
    root = read_prefs()
    for pair in a.pairs:
        key, _, value = pair.partition("=")
        for el in list(root):
            if el.get("name") == key:
                root.remove(el)
        if value in ("true", "false"):
            tag, attrs, text = "boolean", {"value": value}, None
        elif re.fullmatch(r"-?\d+", value):
            tag, attrs, text = "int", {"value": value}, None
        elif re.fullmatch(r"-?\d*\.\d+", value):
            tag, attrs, text = "float", {"value": value}, None
        else:
            tag, attrs, text = "string", {}, value
        attrs["name"] = key
        ET.SubElement(root, tag, attrs).text = text
        print("%s = %s  (%s)" % (key, value, tag))
    xml = "<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" + ET.tostring(
        root, encoding="unicode")
    # one argument, not several: adb joins them unquoted, so a loose ">" would
    # redirect in the device shell's own directory rather than the app's
    sh("run-as %s sh -c 'cat > %s'" % (PKG, PREF_PATH), stdin=xml.encode("utf-8"))
    written = dict(pref_items(read_prefs()))
    for pair in a.pairs:
        key, _, value = pair.partition("=")
        if written.get(key) != value:
            sys.exit("write did not stick: %s is %r" % (key, written.get(key)))


# ------------------------------------------------------------ device and build

def cmd_push(a):
    adb("push", a.local, a.remote, check=True)
    if a.scan:
        sh("content", "call", "--uri", "content://media/external",
           "--method", "scan_file", "--arg", a.remote)
    print("pushed %s -> %s%s" % (a.local, a.remote, " and scanned" if a.scan else ""))


def cmd_anim(a):
    for key in ("window_animation_scale", "transition_animation_scale", "animator_duration_scale"):
        sh("settings", "put", "global", key, a.scale)
    print("animation scales = %s%s" % (a.scale, "  (slow motion)" if float(a.scale) > 1 else ""))


def cmd_rotate(a):
    sh("settings", "put", "system", "accelerometer_rotation", "0")
    sh("settings", "put", "system", "user_rotation", {0: 0, 90: 1, 180: 2, 270: 3}[a.degrees])
    print("rotated to %d degrees" % a.degrees)


def cmd_install(a):
    import glob
    repo = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
    if a.build:
        gradlew = os.path.join(repo, "gradlew.bat" if os.name == "nt" else "gradlew")
        res = subprocess.run([gradlew, "assembleFossDebug", "--console=plain", "-q"],
                             cwd=repo, capture_output=True)
        text = (res.stdout + res.stderr).decode("utf-8", errors="replace")
        if res.returncode != 0:
            bad = [ln for ln in text.splitlines() if ln.startswith("e: ") or "FAILED" in ln]
            print("\n".join(bad[:20]) or text[-2000:])
            sys.exit("build failed")
        print("build ok")
    # the apk filename carries FORK_VERSION_NAME, so it is matched, never named
    apks = sorted(glob.glob(os.path.join(repo, APK_GLOB)), key=os.path.getmtime)
    if not apks:
        sys.exit("no apk under " + APK_GLOB + " -- run with --build")
    said = adb("install", "-r", apks[-1]).strip().splitlines()
    print((said[-1] if said else "?") + "  (" + os.path.basename(apks[-1]) + ")")


def cmd_devices(a):
    print(adb("devices").strip())
    print("package %s   adb %s" % (PKG, ADB))


# ------------------------------------------------------------------------ main

def main():
    global SERIAL, PKG
    p = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("--serial", default=SERIAL, help="device (default %s)" % SERIAL)
    p.add_argument("--pkg", default=PKG, help="package (default %s)" % PKG)
    sub = p.add_subparsers(dest="cmd", required=True)

    def add(name, fn, help):
        s = sub.add_parser(name, help=help)
        s.set_defaults(fn=fn)
        return s

    s = add("shot", cmd_shot, "screenshot, half size by default")
    s.add_argument("-o", "--out", help="file name")
    s.add_argument("--dir", help="output directory")
    s.add_argument("--scale", type=float, default=0.5)
    s.add_argument("--full", action="store_true", help="full resolution, ~3x the tokens")
    s.add_argument("--crop", help="id=<resource-id or text>, or x,y,w,h")

    s = add("film", cmd_film, "burst of frames tiled into one contact sheet")
    s.add_argument("--tap", help="tap this first, then film -- resolved before the burst starts")
    s.add_argument("--key", help="press this first, e.g. BACK")
    s.add_argument("--frames", type=int, default=8)
    s.add_argument("--interval", type=int, default=0,
                   help="extra ms between frames; a grab alone costs ~250ms, so slow the "
                        "animations (anim 10) rather than filming faster")
    s.add_argument("--scale", type=float, default=0.25)
    s.add_argument("--cols", type=int, default=0)
    s.add_argument("-o", "--out")
    s.add_argument("--dir")

    s = add("ui", cmd_ui, "view tree as one line per node")
    s.add_argument("--filter", help="only nodes whose id or text contains this")
    s.add_argument("--clickable", action="store_true", help="only tappable nodes")
    s.add_argument("--all", action="store_true", help="include unnamed nodes too")

    s = add("find", cmd_find, "bounds and centre of one node")
    s.add_argument("target")

    s = add("tap", cmd_tap, "tap a resource id, some visible text, or x,y")
    s.add_argument("target")
    s.add_argument("--settle", type=float, nargs="?", const=8.0, default=0,
                   help="wait for the screen to settle afterwards")

    s = add("swipe", cmd_swipe, "swipe by direction, or by explicit points")
    s.add_argument("--dir", choices=["up", "down", "left", "right"], default="up")
    s.add_argument("--points", help="x1,y1,x2,y2")
    s.add_argument("--frac", type=float, default=0.6, help="fraction of the screen")
    s.add_argument("--ms", type=int, default=300)

    s = add("key", cmd_key, "keyevent, e.g. BACK")
    s.add_argument("key")

    s = add("text", cmd_text, "type text")
    s.add_argument("text")

    s = add("launch", cmd_launch, "launch through the launcher, then wait for idle")
    s.add_argument("--fresh", action="store_true", help="force-stop first")
    s.add_argument("--clear", action="store_true", help="wipe data, then re-grant permissions")
    s.add_argument("--activity", help="a named activity instead of the launcher")
    s.add_argument("--timeout", type=float, default=12.0)

    add("stop", cmd_stop, "force-stop the app")
    add("focus", cmd_focus, "the focused activity")

    s = add("idle", cmd_idle, "wait until the screen stops changing")
    s.add_argument("--timeout", type=float, default=8.0)

    s = add("logcat", cmd_logcat, "crashes only, unless given --tag or --grep")
    s.add_argument("--since-launch", action="store_true", help="this process only")
    s.add_argument("--tag", help="e.g. MetaDbg:D or AndroidRuntime:E")
    s.add_argument("--grep")
    s.add_argument("-n", "--lines", type=int, default=60)
    add("clearlog", cmd_clearlog, "clear the log buffer")

    s = add("prefs", cmd_prefs, "read or write Prefs.xml through run-as")
    s.add_argument("action", choices=["get", "set", "dump"])
    s.add_argument("pairs", nargs="*", help="keys to get, or key=value to set")

    s = add("push", cmd_push, "push a file and tell MediaStore about it")
    s.add_argument("local")
    s.add_argument("remote")
    s.add_argument("--scan", action="store_true")

    s = add("anim", cmd_anim, "animation scales (10 = slow motion, 1 = normal)")
    s.add_argument("scale")

    s = add("rotate", cmd_rotate, "rotate the screen")
    s.add_argument("degrees", type=int, choices=[0, 90, 180, 270])

    s = add("install", cmd_install, "install the debug apk, found by glob")
    s.add_argument("--build", action="store_true", help="assemble it first")

    add("devices", cmd_devices, "what is attached")

    a = p.parse_args()
    SERIAL, PKG = a.serial, a.pkg
    a.fn(a)


if __name__ == "__main__":
    main()
