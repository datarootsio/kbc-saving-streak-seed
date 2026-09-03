# 01: Modules on a page

**What to build:** One command turns this repository's source into a single page that shows every
module in the application, grouped by the package it lives in. Nothing is scored or judged yet — the
deliverable is that a reader who has never opened the codebase can see what it is made of, and that
running the command twice produces exactly the same answer both times.

This ticket also lays down the properties every later ticket inherits: a graph document that the page
is a pure rendering of, output that never varies between runs or machines, an honest report of how
much of the source was actually understood, and a page that is readable in either theme at a normal
screen width.

**Blocked by:** None (can start immediately).

**Status:** needs-info

- [x] A single command reads the backend source and writes two outputs: a machine-readable graph document and a self-contained HTML page
- [x] The page opens in a browser with no server and no network access, with nothing loaded from outside the file
- [x] Every module in the application appears on the page, identified at class grain and grouped visually by its package
- [x] Nothing appears on the page that is not present in the graph document
- [x] Running the command twice over unchanged source produces byte-identical output, and a test asserts this
- [ ] No timestamps, absolute paths, or machine-specific values appear anywhere in either output
- [x] Every collection in the graph document is emitted in a stable sorted order
- [x] A source file the tool cannot parse is reported loudly and named, never silently treated as empty
- [x] The run reports how many source files were parsed and how many were not, so a reader can judge how much weight the page deserves
- [x] The page is legible in both light and dark themes, and at laptop width nothing forces the body to scroll sideways
- [x] The tool depends on nothing outside the Python standard library, and its tests run with the standard library's own test runner


## Review feedback - attempt 1

The tool works, and most of this ticket is genuinely done: I ran it, read its logs, opened the
page in both themes and drove the parse-failure path. Two things send it back. Both are
reproducible in a few commands, and neither needs the application running.

### 1. The source-root label is checkout-specific, so the output is not the same on every machine

`graph.label_for` finds the repository root by walking up for a *directory* named `.git`:

    if os.path.isdir(os.path.join(walk, ".git")):

In a **git worktree** — which is how this repo's own agent protocol isolates work, and what
`git worktree add` produces — `.git` is a **file**, not a directory. The walk therefore never
finds a repository, falls through to `return os.path.basename(absolute)`, and the graph records
the root as `java` instead of `backend/src/main/java`.

Reproduce, from the repository root:

    git worktree add --detach /tmp/wt HEAD
    cd /tmp/wt && python3 scripts/module-depth-map.py --graph /tmp/wt.json --page /tmp/wt.html

Expected: `roots=backend/src/main/java`, and bytes identical to a normal clone.
Actually seen (stderr):

    INFO  module_depth_map.graph graph built roots=java filesSeen=71 filesParsed=71 filesUnparsed=0 packages=8 modules=71
    INFO  module_depth_map.cli run finished graphBytes=29963 pageBytes=35938 ...

against `graphBytes=31187 pageBytes=37162` from the ordinary checkout — the same source, two
checkouts, ~1.2KB of difference in both outputs. The page's "Source read" field reads `java`
rather than `backend/src/main/java`. That is a checkout-shape-specific value sitting in both
outputs, which is what the unticked criterion forbids, and it breaks the spec's "byte-identical
on every run and on every machine" and user story 27 (regenerate on a branch, diff against main
— a reviewer who does that in a worktree gets a diff on every line of the roots and label).

The tool's own suite catches this and was never run where it fires. In the same worktree:

    cd /tmp/wt && python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests

    FAIL: test_this_repository_is_named_by_its_own_layout_rather_than_its_location
    AssertionError: Lists differ: ['backend/src/main/java'] != ['java']
    Ran 32 tests ... FAILED (failures=1)

Detect a `.git` that is a file as well as a directory (`os.path.exists`, not `os.path.isdir`),
and add a test that pins the label when `.git` is a file — a fixture tree with a plain `.git`
file in it is enough; you do not need a real worktree to assert the rule.

Clean up after reproducing: `git worktree remove --force /tmp/wt && git worktree prune`.

### 2. Sixteen compiled `__pycache__/*.pyc` files are committed to the branch

`git ls-files | grep pycache` returns 16 files under `scripts/module_depth_map/`. They are build
output, they are machine-specific, and they carry the implementing machine's absolute home path:

    $ git show HEAD:scripts/module_depth_map/__pycache__/cli.cpython-310.pyc | strings | grep /Users
    /Users/alial-gburi/repos/kbc-saving-streak/scripts/module_depth_map/cli.py

They also dirty the working tree the moment anyone uses the tool. A `.pyc` header stores the
source file's mtime, so on any fresh clone or worktree the first run rewrites all of them:

    $ touch scripts/module_depth_map/*.py scripts/module_depth_map/tests/*/*.py
    $ python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests   # OK, 32 tests
    $ git status --short
     M scripts/module_depth_map/__pycache__/__init__.cpython-310.pyc
     M scripts/module_depth_map/__pycache__/cli.cpython-310.pyc
     ... (15 files)

Simply running the documented command `python3 scripts/module-depth-map.py` does the same. This
repo's `.gitignore` already excludes the other two toolchains' build output (`backend/target/`,
`frontend/node_modules/`, `frontend/dist/`) and its comments say in as many words that leaving
regenerated files tracked "would trip the clean-working-tree gate next run". A third toolchain
arriving without that entry is the rule this change skipped.

`git rm -r --cached` the `__pycache__` directories and add `__pycache__/` (and `*.pyc`) to
`.gitignore`.

### What I did verify, so you do not have to redo it

Everything below was run on this branch at `865cd2d` and passed; only the two points above need
work, plus a regenerated `docs/module-depth-map.{json,html}` once the label fix lands.

- `cd backend && ./mvnw test` — 113 tests, BUILD SUCCESS. `cd frontend && npm run typecheck` — clean.
- `python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests` — 32 tests, OK
  (in an ordinary checkout).
- Two runs into separate directories are byte-identical (`cmp` on both files), and both match the
  committed `docs/module-depth-map.json` / `docs/module-depth-map.html` exactly.
- 71 `.java` files on disk, 71 in the graph, 0 unparsed, 8 packages; no path on disk missing from
  the graph and no graph path missing from disk.
- The parse-failure path is honest. A fixture with an unclosed brace, no package declaration, no
  type declaration and invalid UTF-8 produced one WARNING per file naming it and its reason, a
  summary WARNING, and none of them became an empty module:

      WARNING module_depth_map.graph could not parse source file root=fx path=shop/till/Unclosed.java reason=braces do not balance: 1 unclosed at end of file
      WARNING module_depth_map.cli the page is drawn from 1 of 5 source files: 4 could not be read, and every module in them is missing from it

- `--source /no/such/dir` logs `ERROR module_depth_map.cli refused to run: no such source directory
  /no/such/dir` and exits 2.
- Playwright over `file://.../docs/module-depth-map.html` at 1280x800 and 1024 wide, in both
  `color_scheme` settings: 8 package sections, 71 module cards, exactly one network request (the
  file itself), zero console messages, zero page errors, zero failed requests, and
  `documentElement.scrollWidth == window.innerWidth` at both widths. Screenshots read: styled and
  legible light (`rgb(247,247,245)` on `rgb(27,28,30)`) and dark (`rgb(22,24,27)` on
  `rgb(233,234,236)`). Browser log at
  `.scratch/module-depth-map/logs/01-modules-on-a-page.review.1.browser.log`.
- Imports across the whole tool are stdlib only: `argparse json logging os re shutil sys tempfile
  unittest`.
