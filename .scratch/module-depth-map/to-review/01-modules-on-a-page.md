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

**Status:** needs-review

- [x] A single command reads the backend source and writes two outputs: a machine-readable graph document and a self-contained HTML page
- [x] The page opens in a browser with no server and no network access, with nothing loaded from outside the file
- [x] Every module in the application appears on the page, identified at class grain and grouped visually by its package
- [x] Nothing appears on the page that is not present in the graph document
- [x] Running the command twice over unchanged source produces byte-identical output, and a test asserts this
- [x] No timestamps, absolute paths, or machine-specific values appear anywhere in either output
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

## Review feedback - attempt 2

Both attempt-1 blockers are genuinely fixed, and I confirmed that first (details at the bottom, so
you do not redo them). What sends this back is a third thing neither round exercised: the criterion
"a source file the tool cannot parse is reported loudly and named, never silently treated as empty"
is only true for the failure modes the tests cover. Two other ways a file can go wrong are handled
much worse than the ticket asks — one kills the run outright, the other drops a module in silence.
That checkbox is now unticked; the other ten stand.

All three reproductions below take one command each and need nothing running.

### 1. A file the tool cannot open crashes the run and writes neither output (blocker)

`graph._read` catches only `UnicodeDecodeError`. Any other `OSError` — a broken symlink, a file with
no read permission, a file deleted between the `os.walk` and the `open` — propagates out of
`graph.build`, past `cli.main`, and terminates the process with a traceback. Reproduce:

    mkdir -p /tmp/fx/shop/till
    printf 'package shop.till;\n\npublic class Good {}\n' > /tmp/fx/shop/till/Good.java
    ln -s /no/such/target.java /tmp/fx/shop/till/Dangling.java
    python3 scripts/module-depth-map.py --source /tmp/fx --graph /tmp/fx.json --page /tmp/fx.html

Expected, by the criterion and by `graph.py`'s own docstring ("A file that cannot be read is named in
the document and logged, never counted as a module with nothing in it"): a WARNING naming
`shop/till/Dangling.java` with its reason, `filesUnparsed=1`, and both outputs still written.

Actually seen:

    INFO  module_depth_map.cli run started sources=/tmp/fx graph=/tmp/fx.json page=/tmp/fx.html
    Traceback (most recent call last):
      File "scripts/module-depth-map.py", line 19, in <module>
        sys.exit(main(sys.argv[1:]))
      File "scripts/module_depth_map/cli.py", line 59, in main
        document = graph.build([graph.java_root(directory) for directory in sources])
      File "scripts/module_depth_map/graph.py", line 89, in build
        text = _read(whole)
      File "scripts/module_depth_map/graph.py", line 151, in _read
        with open(whole, "rb") as handle:
    FileNotFoundError: [Errno 2] No such file or directory: '/tmp/fx/shop/till/Dangling.java'

Neither `/tmp/fx.json` nor `/tmp/fx.html` exists afterwards. One unreadable file therefore destroys
the whole page rather than costing one card, and the reason reaches the reader as a Python stack
trace rather than as the named, counted failure the ticket asks for. Catch `OSError` in `_read`
alongside `UnicodeDecodeError` and raise `ParseFailure` with the error text, and add a test for it —
a `ParseFailure` with a mocked-out or symlinked file is enough.

### 2. A declaration preceded by an annotation on the same line vanishes with no signal (blocker)

`javasource._TYPE` anchors at `^[ \t]*` followed only by modifiers, so an annotation ahead of the
keyword on the same line means no match at all. The file still parses, so nothing is warned, nothing
is counted, and the module is simply absent — which is the exact shape of failure the criterion says
must never happen. Reproduce:

    mkdir -p /tmp/f2/shop/till
    printf 'package shop.till;\n\n@Deprecated public class Foo {}\nclass Bar {}\n' > /tmp/f2/shop/till/Foo.java
    python3 scripts/module-depth-map.py --source /tmp/f2 --graph /tmp/f2.json --page /tmp/f2.html

Expected: `Foo` and `Bar` both present, or a loud failure naming the file.
Actually seen:

    INFO  module_depth_map.graph graph built roots=f2 filesSeen=1 filesParsed=1 filesUnparsed=0 packages=1 modules=1
    modules: ['shop.till.Bar']    unparsed: []

`Foo` is gone, `filesUnparsed=0`, no WARNING anywhere. The same happens to nested types —
`public class Outer {\n    @Deprecated public enum Kind { A }\n}` yields `Outer` with `nested: []` —
and to any declaration that is not first on its line, e.g. `class B { class N {} }` loses `N`. The
file is only reported as unparseable when *every* top-level type is hidden this way.

This does not affect the committed page today: I grepped
`^[ \t]*@[A-Za-z]+.*[ \t](class|interface|enum|record)[ \t]+\w+` across `backend/src/main/java` and
got no matches, so all 71 files are read completely right now. It is still a blocker, because the
property the ticket buys is "silence means nothing was missed", and here silence does not mean that.
Either match a declaration after same-line annotations, or count the declarations found against the
`class|interface|enum|record` keywords present in the masked text and raise a `ParseFailure` when
they disagree — the second is cheaper and keeps the "loudly" promise for whatever else the pattern
does not cover.

### 3. `/*/` is read as a whole comment, inventing a module that is not in the source

`mask_comments_and_literals` starts hunting for `*/` at the opening `/*` rather than two characters
later, so the opener's own `*` plus the following `/` can close it. Reproduce:

    mkdir -p /tmp/f5/shop/till
    printf 'package shop.till;\n\n/*/ class Ghost {} */\npublic class Real {}\n' > /tmp/f5/shop/till/Real.java
    python3 scripts/module-depth-map.py --source /tmp/f5 --graph /tmp/f5.json --page /tmp/f5.html

Expected: only `shop.till.Real` — Java reads that whole line as a comment.
Actually seen: `modules: ['shop.till.Ghost', 'shop.till.Real']`, `unparsed: []`. A module that does
not exist is drawn on the page. Start the inner scan at `i + 2`. No file in this repo contains `/*/`
today, so the committed page is unaffected.

### Smaller things worth fixing in the same pass

- **`lines` is one too high on every module.** `javasource.parse` computes `text.count("\n") + 1`,
  which counts the trailing newline as a line. `AccountPairing.java` is 22 lines; the committed
  `docs/module-depth-map.json` records `"lines": 23`, and every other entry is off by the same one.
  Nothing renders it yet, so no criterion fails — but a later ticket that scores or ranks on `lines`
  inherits the error silently. Use `len(text.splitlines())`.
- **`package-info.java` is reported as a file that could not be read.** A type-free but perfectly
  well-formed `/**\n * Docs.\n */\npackage shop.till;\n` raises `no top-level type declaration`, which
  decrements `filesParsed` and paints the red "N source files could not be read" banner on the page.
  (`module-info.java` fails the same way with `no package declaration`.) There is none in this repo
  today, so nothing is wrong with the committed output — but the alarm band only stays trustworthy if
  it never cries wolf. Recognise both names and skip them rather than counting them as failures.
- **Module ids are assumed unique but nothing enforces it.** `--source` is documented as repeatable.
  With two roots each holding `shop/till/Till.java`, `modules` has two entries with the id
  `shop.till.Till` and `packages[0].moduleIds` is `["shop.till.Till", "shop.till.Till"]`. The page's
  `byId` map overwrites, so the same card is drawn twice, both showing the second root's data, and the
  first root's `root`/`path` appear nowhere. Reachable the first time anyone adds
  `backend/src/test/java` or the frontend as a second root, which later tickets in this spec require.
  Either make the id carry the root, or refuse duplicate ids loudly.
- **Nothing pins the committed `docs/module-depth-map.{json,html}` to a fresh run.**
  `test_the_command_writes_the_same_two_files_twice_over_this_repository` compares two fresh runs to
  each other, not to the checked-in files, and there is no CI. Add a Java class to the backend and the
  committed page goes stale while all 35 tests still pass — and that page is the artefact a reader is
  meant to trust. A test that regenerates into a temp directory and byte-compares against `docs/`
  closes it in a few lines.

### What I did verify, so you do not have to redo it

On this branch at `f1ab916`. Both attempt-1 blockers are fixed; everything below passed.

- **The worktree label is fixed.** `git worktree add --detach /tmp/wt HEAD` (its `.git` is a 77-byte
  `gitdir:` file), then the tool inside it:
  `INFO module_depth_map.graph graph built roots=backend/src/main/java filesSeen=71 filesParsed=71 filesUnparsed=0 packages=8 modules=71`
  and `graphBytes=31187 pageBytes=37162` — the ordinary checkout's numbers, not attempt 1's
  29963/35938. `cmp` of both worktree outputs against the committed `docs/module-depth-map.{json,html}`:
  identical. Suite inside the worktree: 35 tests, OK. Worktree removed and `git worktree prune` run.
- **The new tests really pin that rule.** Reverting `os.path.exists` back to `os.path.isdir` inside the
  throwaway worktree fails exactly three tests:
  `test_a_root_is_named_by_its_layout_when_git_is_a_file`,
  `test_the_two_checkout_shapes_write_the_same_bytes` and
  `test_this_repository_is_named_by_its_own_layout_rather_than_its_location`. The first two are
  fixture-based, so they fire in an ordinary clone too.
- **No bytecode is tracked.** `git ls-files | grep -Ec "pycache|\.pyc$"` is `0`; `.gitignore` has
  `__pycache__/` and `*.pyc`. Re-ran attempt 1's repro (`touch` every `.py`, run the suite, run the
  tool) and `git status --short` is empty afterwards. That run also regenerated
  `docs/module-depth-map.{json,html}` with no diff, so the committed outputs are current.
- **Determinism.** 20 consecutive runs, every one byte-identical to the committed outputs. Suite run 5
  times: OK each time. Neither output contains `/Users/`, an ISO date or an `hh:mm:ss`.
- **Completeness for this repository.** 71 `.java` on disk, 71 in the graph, 0 unparsed, 8 packages;
  set difference empty both ways. Nested-type extraction spot-checked against source
  (`DepositRefused -> [Kind]`, `CustomerAccountsResponse -> [CurrentAccountResponse, SavingsAccountResponse]`),
  including the word "record" in prose javadoc being correctly masked.
- **The page.** Playwright over `file://`, console/pageerror/requestfailed subscribed before
  navigating, in light and dark at 1024 and 1280: 71 module cards, 8 package sections,
  `scrollWidth == innerWidth` at both widths, exactly one network request per load (the file itself),
  zero console messages, zero page errors, zero failed requests. Screenshots read — styled and legible,
  light `rgb(247,247,245)` on `rgb(27,28,30)`, dark `rgb(22,24,27)` on `rgb(233,234,236)`,
  "Source read: backend/src/main/java". Logs and images at
  `.scratch/module-depth-map/logs/01-modules-on-a-page.review.2.{browser.log,light.png,dark.png,broken.png}`.
- **The failure modes that *are* handled.** A fixture with an unclosed brace, no package declaration,
  no type declaration and invalid UTF-8 gave one named WARNING each with its reason, a summary WARNING,
  and none became an empty module; the page drew the alarm band naming all four. `--source /no/such/dir`
  and a `--source` pointing at a file both log
  `ERROR module_depth_map.cli refused to run: no such source directory ...` and exit 2. An empty root
  runs cleanly with `modules=0`. Two roots emit `roots` sorted.
- **Lab checks.** `cd backend && ./mvnw test` — 113 tests, BUILD SUCCESS (run twice).
  `cd frontend && npm run typecheck` — clean. `python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests` —
  35 tests, OK. Imports are stdlib only. No `print(` in the tool; every module has a named logger.
