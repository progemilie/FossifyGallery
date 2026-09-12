#!/usr/bin/env python3
"""Cut a fork release, or read a release's notes back out of the changelog.

    release.py cut --name "two or three words"   # branch, stamp both files, commit
    release.py notes v1.20.0                     # that version's section body

FORK-CHANGELOG.md is the authority. Its `## [Unreleased]` section decides the
bump — `### Added` means minor, anything else patch — so the number follows from
the entries that were already written and reviewed. The only thing this cannot
work out is what to call the release, which is why --name is required.

`notes` is what feeds both the PR body and the GitHub release, so the two cannot
drift. Stdlib only: it also runs on the CI runner.
"""

import argparse
import datetime
import os
import re
import subprocess
import sys

CHANGELOG = "FORK-CHANGELOG.md"
PROPS = "gradle.properties"
KEY = "FORK_VERSION_NAME"
TAG_URL = "https://github.com/progemilie/FossifyGallery/releases/tag/{tag}"
TRAILER = "Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"

# Keep a Changelog's set. Anything else under [Unreleased] is a typo, and a
# typo'd "### Added" would quietly turn a minor release into a patch.
SECTIONS = ("Added", "Changed", "Deprecated", "Removed", "Fixed", "Security")
MINOR_SECTION = "Added"

VERSION_RE = re.compile(r"^(\d+)\.(\d+)\.(\d+)$")
HEADING_RE = re.compile(r"^## \[(?P<name>[^\]]+)\]")
LINKDEF_RE = re.compile(r"^\[v\d+\.\d+\.\d+\]:")


def fail(msg):
    sys.exit("release: " + msg)


def normalise(version):
    return version if version.startswith("v") else "v" + version


def git(*args, check=True, stdin=None):
    r = subprocess.run(
        ("git",) + args, capture_output=True, text=True, input=stdin, encoding="utf-8"
    )
    if check and r.returncode != 0:
        fail("git {} failed:\n{}".format(" ".join(args), (r.stderr or r.stdout).strip()))
    return r


def repo_root():
    r = git("rev-parse", "--show-toplevel")
    return r.stdout.strip()


def read_text(path):
    """Return (text with \\n newlines, the newline the file actually uses)."""
    with open(path, encoding="utf-8", newline="") as f:
        raw = f.read()
    return raw.replace("\r\n", "\n"), ("\r\n" if "\r\n" in raw else "\n")


def write_text(path, text, newline):
    with open(path, "w", encoding="utf-8", newline="") as f:
        f.write(text.replace("\n", newline) if newline != "\n" else text)


# --- changelog ------------------------------------------------------------


def split_section(text, name):
    """Locate one `## [name]` section. Returns (start, body_start, end) line indices."""
    lines = text.split("\n")
    start = None
    for i, line in enumerate(lines):
        m = HEADING_RE.match(line)
        if m and m.group("name") == name:
            start = i
            break
    if start is None:
        return None
    end = len(lines)
    for i in range(start + 1, len(lines)):
        if HEADING_RE.match(lines[i]) or LINKDEF_RE.match(lines[i]):
            end = i
            break
    return start, start + 1, end


def parse_sections(body_lines):
    """Group a section's lines under their `### X` headings.

    Returns (ordered {heading: [lines]}, loose lines that precede any heading).
    """
    groups = {}
    loose = []
    current = None
    for line in body_lines:
        if line.startswith("### "):
            current = line[4:].strip()
            groups.setdefault(current, [])
        elif line.strip():
            (groups[current] if current else loose).append(line)
    return groups, loose


def next_version(current, bump):
    m = VERSION_RE.match(current)
    if not m:
        fail("{} in {} is not X.Y.Z: {!r}".format(KEY, PROPS, current))
    x, y, z = (int(g) for g in m.groups())
    if bump == "major":
        return "{}.0.0".format(x + 1)
    if bump == "minor":
        return "{}.{}.0".format(x, y + 1)
    return "{}.{}.{}".format(x, y, z + 1)


# --- gradle.properties ----------------------------------------------------


def read_fork_version(text):
    for line in text.split("\n"):
        if line.startswith(KEY + "="):
            return line[len(KEY) + 1 :].strip()
    fail("no {} in {}".format(KEY, PROPS))


def set_fork_version(text, version):
    """Rewrite only the FORK_VERSION_NAME line, and prove nothing else moved."""
    out = []
    hits = 0
    for line in text.split("\n"):
        if line.startswith(KEY + "="):
            out.append("{}={}".format(KEY, version))
            hits += 1
        else:
            out.append(line)
    if hits != 1:
        fail("expected exactly one {} line in {}, found {}".format(KEY, PROPS, hits))
    new = "\n".join(out)
    changed = [
        (a, b) for a, b in zip(text.split("\n"), out) if a != b
    ]
    if len(changed) != 1 or not changed[0][0].startswith(KEY + "="):
        fail("refusing to write {}: the edit touched more than {}".format(PROPS, KEY))
    return new


# --- commands -------------------------------------------------------------


def cmd_notes(args):
    tag = normalise(args.version)
    body, _ = released_section(tag)
    while body and not body[0].strip():
        body.pop(0)
    while body and not body[-1].strip():
        body.pop()
    if not body:
        fail("the {} section is empty".format(tag))
    print("\n".join(body))


def released_section(tag):
    """The (lines, heading) of a stamped version's section, or exit with why not."""
    text, _ = read_text(CHANGELOG)
    found = split_section(text, tag)
    if not found:
        fail("no `## [{}]` section in {}".format(tag, CHANGELOG))
    start, body_start, end = found
    lines = text.split("\n")
    return lines[body_start:end], lines[start]


def cmd_title(args):
    """`v1.20.0 - two or three words`, for the GitHub release's title."""
    tag = normalise(args.version)
    _, heading = released_section(tag)
    name = heading.split("—", 1)[1].strip() if "—" in heading else ""
    print("{} — {}".format(tag, name) if name else tag)


def cmd_verify(args):
    """Assert the changelog and gradle.properties agree about one release.

    CI runs this before it tags, so the same parser that wrote the release is the
    one that approves it.
    """
    tag = normalise(args.version)
    version = tag[1:]

    props, _ = read_text(PROPS)
    current = read_fork_version(props)
    if current != version:
        fail("{} is {} but the tag would be {}".format(KEY, current, tag))

    body, heading = released_section(tag)
    groups, loose = parse_sections(body)
    if loose:
        fail("{} entries are not under a ### section:\n  {}".format(tag, "\n  ".join(loose[:5])))
    unknown = [h for h in groups if h not in SECTIONS]
    if unknown:
        fail("unknown ### heading(s) in {}: {}".format(tag, ", ".join(unknown)))
    if not any(groups.values()):
        fail("the {} section has no entries".format(tag))

    text, _ = read_text(CHANGELOG)
    found = split_section(text, "Unreleased")
    if not found:
        fail("no `## [Unreleased]` section left in {} for the next cycle".format(CHANGELOG))
    _, u_start, u_end = found
    u_groups, u_loose = parse_sections(text.split("\n")[u_start:u_end])
    # Not a failure: work can legitimately land on dev between cutting a release
    # and merging it, and those entries belong to the next cycle.
    leftover = u_loose + [l for v in u_groups.values() for l in v]
    if leftover:
        print(
            "warning: [Unreleased] already has {} entr{} for the next release".format(
                len(leftover), "y" if len(leftover) == 1 else "ies"
            ),
            file=sys.stderr,
        )

    if not re.search(r"^\[{}\]: \S+".format(re.escape(tag)), text, re.M):
        fail("no `[{}]:` link definition in {}".format(tag, CHANGELOG))

    print("{} verified: {}".format(tag, ", ".join("{} {}".format(len(v), h.lower()) for h, v in groups.items() if v)))


def cmd_cut(args):
    words = args.name.split()
    if not 1 <= len(words) <= 4:
        fail("--name wants two or three words, got {}".format(len(words)))
    name = " ".join(words)

    branch = git("rev-parse", "--abbrev-ref", "HEAD").stdout.strip()
    if branch != "dev":
        fail("run this on dev, not {} - the version is written on dev only".format(branch))
    if git("status", "--porcelain").stdout.strip():
        fail("working tree is dirty; commit or stash first")

    git("fetch", "--quiet", "origin", "dev")
    counts = git("rev-list", "--left-right", "--count", "origin/dev...dev").stdout.split()
    behind, ahead = int(counts[0]), int(counts[1])
    if behind or ahead:
        fail(
            "dev is {} behind and {} ahead of origin/dev; pull or push first".format(
                behind, ahead
            )
        )

    changelog, cl_nl = read_text(CHANGELOG)
    found = split_section(changelog, "Unreleased")
    if not found:
        fail("no `## [Unreleased]` section in {}".format(CHANGELOG))
    start, body_start, end = found
    lines = changelog.split("\n")
    groups, loose = parse_sections(lines[body_start:end])

    if loose:
        fail(
            "these [Unreleased] entries are not under a ### section:\n  "
            + "\n  ".join(loose[:5])
        )
    unknown = [h for h in groups if h not in SECTIONS]
    if unknown:
        fail(
            "unknown ### heading(s) under [Unreleased]: {} (expected one of {})".format(
                ", ".join(unknown), ", ".join(SECTIONS)
            )
        )
    filled = {h: v for h, v in groups.items() if v}
    if not filled:
        fail("[Unreleased] is empty - nothing to release")
    empty = [h for h, v in groups.items() if not v]
    if empty:
        fail("drop the empty ### {} heading(s) from [Unreleased]".format(", ".join(empty)))

    props, props_nl = read_text(PROPS)
    current = read_fork_version(props)

    if args.bump:
        bump, why = args.bump, "--{} was asked for".format(args.bump)
    elif filled.get(MINOR_SECTION):
        bump = "minor"
        why = "[Unreleased] has {} ### {} entr{}".format(
            len(filled[MINOR_SECTION]),
            MINOR_SECTION,
            "y" if len(filled[MINOR_SECTION]) == 1 else "ies",
        )
    else:
        bump = "patch"
        why = "[Unreleased] has no ### {} entries, only {}".format(
            MINOR_SECTION, ", ".join(sorted(filled))
        )

    version = next_version(current, bump)
    tag = "v" + version
    release_branch = "release/" + tag

    if git("rev-parse", "-q", "--verify", "refs/tags/" + tag, check=False).returncode == 0:
        fail("tag {} already exists".format(tag))
    if (
        git("rev-parse", "-q", "--verify", "refs/heads/" + release_branch, check=False).returncode
        == 0
    ):
        fail("branch {} already exists".format(release_branch))

    # --- write ---
    date = datetime.date.today().strftime("%Y.%m.%d")
    heading = "## [{}] - {} — {}".format(tag, date, name)
    lines[start] = heading
    lines[start:start] = ["## [Unreleased]", ""]

    linkdef = "[{}]: {}".format(tag, TAG_URL.format(tag=tag))
    for i, line in enumerate(lines):
        if LINKDEF_RE.match(line):
            lines.insert(i, linkdef)
            break
    else:
        fail("found no `[vX.Y.Z]:` link definitions to insert above in " + CHANGELOG)

    git("switch", "--quiet", "-c", release_branch)
    try:
        write_text(CHANGELOG, "\n".join(lines), cl_nl)
        write_text(PROPS, set_fork_version(props, version), props_nl)
        git("add", "--", CHANGELOG, PROPS)
        staged = git("diff", "--cached", "--name-only").stdout.split()
        if sorted(staged) != sorted([CHANGELOG, PROPS]):
            fail("expected to stage only {} and {}, staged {}".format(CHANGELOG, PROPS, staged))
        git(
            "commit",
            "--quiet",
            "-F",
            "-",
            stdin="chore(release): fork {}\n\n{}\n".format(tag, TRAILER),
        )
    except SystemExit:
        git("switch", "--quiet", "--force", "dev")
        git("branch", "--quiet", "-D", release_branch, check=False)
        raise

    print("{} -> {} ({}: {})".format(current, version, bump, why))
    print(
        "{} on {}".format(
            git("rev-parse", "--short", "HEAD").stdout.strip(), release_branch
        )
    )


def main():
    p = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    sub = p.add_subparsers(dest="cmd", required=True)

    c = sub.add_parser("cut", help="branch, stamp the changelog and version, commit")
    c.add_argument("--name", required=True, metavar="WORDS", help="two or three words for the heading")
    g = c.add_mutually_exclusive_group()
    for b in ("major", "minor", "patch"):
        g.add_argument(
            "--" + b, dest="bump", action="store_const", const=b,
            help="force a {} bump instead of deriving it".format(b),
        )
    c.set_defaults(func=cmd_cut, bump=None)

    n = sub.add_parser("notes", help="print a version's changelog section")
    n.add_argument("version", help="e.g. v1.20.0")
    n.set_defaults(func=cmd_notes)

    v = sub.add_parser("verify", help="assert the changelog and version agree (CI runs this)")
    v.add_argument("version", help="e.g. v1.20.0")
    v.set_defaults(func=cmd_verify)

    t = sub.add_parser("title", help="print a version's release title")
    t.add_argument("version", help="e.g. v1.20.0")
    t.set_defaults(func=cmd_title)

    args = p.parse_args()
    # The changelog is full of em dashes and `notes` output gets redirected to a
    # file; a redirected stdout on Windows is cp1252 and would mangle or throw.
    sys.stdout.reconfigure(encoding="utf-8")
    os.chdir(repo_root())
    args.func(args)


if __name__ == "__main__":
    main()
