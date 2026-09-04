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

## Review feedback - attempt 4

All three attempt-2 blockers and all four "smaller things" are genuinely fixed — I reproduced
every one of the previous round's commands and each now behaves as that feedback asked (proof at
the bottom, so you do not redo it). Ten of the eleven boxes stand.

What sends this back is the same checkbox as last time, criterion 8, reached by two routes the
attempt-3 cross-check does not cover. The property the ticket buys is "silence means nothing was
missed", and there are still two shapes of broken file where the run says `filesUnparsed=0`, logs
no WARNING, and drops a declaration anyway. There is also a third bug in the same masking code
that goes the other way: it fails a **legal** Java file and paints the alarm band over it.

Every reproduction below is one command and needs nothing running. Run them from the repository
root on `ticket/01-modules-on-a-page` at `96d1b15`.

### 1. An unterminated `/*` swallows the rest of the file in silence (blocker)

`javasource.mask_comments_and_literals`, the `/*` branch: when the closing `*/` is never found the
loop runs to end of file, the `if i < n:` guard at the end is simply false, and no failure is
recorded. Everything after the opener is blanked — so `_TYPE` never sees the declarations in it,
and `_RESERVED_DECLARATION` cannot fire either, because the cross-check runs over the *masked*
text and there is no keyword left in it to count. The guard that was added in attempt 3 to make
"loudly" true is blind to exactly this case.

    mkdir -p /tmp/g6/shop/till
    printf 'package shop.till;\npublic class A {}\n/* forgot to close\nclass B { void x() {} }\nclass C { void y() {} }\n' > /tmp/g6/shop/till/A.java
    python3 scripts/module-depth-map.py --source /tmp/g6 --graph /tmp/g6.json --page /tmp/g6.html

Expected: the file named with a reason such as `block comment is never closed`, `filesUnparsed=1`.
Actually seen:

    INFO  module_depth_map.graph graph built roots=g6 filesSeen=1 filesParsed=1 filesUnparsed=0 packages=1 modules=1

and `modules: ['shop.till.A']`, `unparsed: []`. `B` and `C` are gone with nothing said. An
unterminated block comment is the most ordinary way a Java file breaks mid-edit, and it is the
canonical example of "a source file the tool cannot parse".

The same hole exists for an unterminated `"""` and an unterminated `"` string — both have the same
`if i < n:` guard that already *knows* the delimiter was never found and closes the region quietly
instead of raising. Raise a `ParseFailure` naming the unclosed form and the line it opened on.
(An unterminated `"""` happens to be caught today only when the swallowed region also leaves the
braces unbalanced; make it explicit rather than incidental.)

### 2. Braces that are unbalanced but net to zero drop a type in silence (blocker)

`javasource.parse` checks only `final_depth != 0`. A file whose braces go negative and come back
passes, and any type matched while the depth is negative falls out of both `top_level`
(`depth == 0`) and `nested_under` (which only collects `depth != 0` types seen *after* a depth-0
one).

    mkdir -p /tmp/g1/shop/till
    printf 'package shop.till;\n}\nclass Vanished {\nclass Real {}\n' > /tmp/g1/shop/till/Real.java
    python3 scripts/module-depth-map.py --source /tmp/g1 --graph /tmp/g1.json --page /tmp/g1.html

Expected: the file named with a reason, `filesUnparsed=1`.
Actually seen: `filesSeen=1 filesParsed=1 filesUnparsed=0 modules=1`, `modules: ['shop.till.Real']`,
`unparsed: []`, no WARNING. `Vanished` is nowhere.

The mirror image of it is worse, because it does not drop the type — it moves it onto a module
that does not own it:

    printf 'package shop.till;\npublic class A {}\n}\nclass B {\n' > /tmp/g5/shop/till/A.java

gives `modules: [('A', ['B'])]` — a top-level class drawn on the page as a nested type of an
unrelated module, again with `filesUnparsed=0` and no warning. `ParsedFile.nested_under` reaches
this through its `elif seen_it` branch, which attributes *any* non-zero depth to the preceding
top-level type.

Fail the file as soon as the running depth goes negative (`_brace_depths` already computes it), and
treat a type at a depth the walk cannot explain as a parse failure rather than as data.

### 3. A legal Java text block containing `\"""` is falsely reported as unreadable

The other direction of the same code. The `"""` branch of `mask_comments_and_literals` hunts for
the next `"""` without honouring backslash escapes — but `\"""` is exactly how the JLS says to put
three consecutive double quotes inside a text block. The escaped quote closes the block early, the
rest of the literal is scanned as source, and the braces stop balancing.

    mkdir -p /tmp/g3/shop/till
    cat > /tmp/g3/shop/till/A.java <<'JAVA'
    package shop.till;

    public class A {
        static final String S = """
            say \"""
            """;
        class Real {}
    }
    JAVA
    python3 scripts/module-depth-map.py --source /tmp/g3 --graph /tmp/g3.json --page /tmp/g3.html

Expected: `A` with `nested: ["Real"]`, `filesUnparsed=0`.
Actually seen:

    WARNING module_depth_map.graph could not parse source file root=g3 path=shop/till/A.java reason=braces do not balance: 1 unclosed at end of file
    INFO  module_depth_map.graph graph built roots=g3 filesSeen=1 filesParsed=0 filesUnparsed=1 packages=0 modules=0

Two real modules are dropped from the page and the red "1 source file could not be read" band is
painted over a file with nothing wrong with it. This is the same "an alarm that cries wolf stops
being read" argument that got `package-info.java` fixed in attempt 3, except here the input is
legal Java rather than a descriptor. Handle `\` escapes in the text-block branch the way the
single/double-quote branch already does. I checked the neighbouring cases and they are fine —
a plain text block, one containing `"` or `""`, and a string containing `\\` all parse correctly;
it is only the escaped triple quote.

### 4. The test suite breaks when `TMPDIR` sits inside a git checkout

`tests/support/sourcetrees.py` builds fixture roots under `tempfile.mkdtemp()`, and `graph.label_for`
walks *up* from a root looking for `.git`. If the temp directory is inside any git checkout, the
fixture is labelled by its path under that repo instead of by its own name, and the suite goes red
for a reason unrelated to the code under test. This matters here specifically because this repo's
agent protocol runs inside `git worktree` checkouts.

    mkdir -p /tmp/tmpinrepo/.git /tmp/tmpinrepo/tmp
    TMPDIR=/tmp/tmpinrepo/tmp python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests

    FAIL: test_the_source_root_is_named_by_where_it_sits_in_its_repository
    FAIL: test_a_file_with_no_type_declaration_at_all_is_reported_rather_than_ignored
    FAIL: test_the_file_is_named_in_the_graph_with_the_reason_it_could_not_be_read
    Ran 53 tests ... FAILED (failures=3)

Give each fixture root its own `.git` marker (the suite already has `_gitdir_pointer` for this), or
make the fixture helper stop the walk at the scratch directory.

### Smaller things worth fixing in the same pass

- **`nested` can list the same name twice.** `graph.build` does `sorted(n.name for ...)` over simple
  names with no de-duplication and no depth. `class A { class B { class C {} } class D { class C {} } }`
  gives `nested: ["B","C","C","D"]`, and the page renders `nested: B, C, C, D` — a reader cannot tell
  `A.B.C` from `A.D.C`, and the repeat reads as a rendering bug. Method-local classes are flattened
  into the same list too. Not reachable in this repo today: I checked every one of the 7 nested names
  in the committed graph against its source file and all resolve to a real declaration.
- **`--log-level` is ignored on any second in-process run.** `cli.main` calls `logging.basicConfig`,
  which is a no-op once the root logger has a handler. Verified: `cli.main([... '--log-level','ERROR'])`
  followed by `cli.main([... '--log-level','DEBUG'])` leaves the root logger at level 40 and prints no
  DEBUG at all. Harmless from the shell, but it means the suite's own per-test log levels take effect
  by test-execution order. Pass `force=True`, or configure the `module_depth_map` logger rather than
  the root.
- **The graph is written before the page is rendered, and neither write is guarded.** `cli.main`
  writes the JSON, then renders and writes the HTML. If the second one fails (read-only `docs/`, full
  disk) the process dies with a traceback and leaves a fresh graph beside a stale page — the one thing
  `cli.py`'s own docstring says cannot happen ("the page can never describe a codebase the graph does
  not"). Render both to bytes first, then write both. Note the duplicate-id path already gets this
  right by writing neither.
- **`label_for` returns `"."` when the source root is the repository root.** `--source .` from the repo
  root writes `"roots": ["."]`, renders "Source read: ." on the page, and prefixes unparsed entries as
  `./shop/till/X.java`. `os.path.relpath` returning `"."` for the identity case needs its own branch.
- **An I/O error is reported as a parse failure.** `graph._read` raises `javasource.ParseFailure` for
  `OSError`, so a dangling symlink logs `could not parse source file ... reason=could not be opened:
  No such file or directory`. Catching it at all was the right attempt-3 fix; the wording and the type
  now conflate "the parser cannot read this Java" with "this file could not be opened", which are
  different things for the reader and for any second language back-end.
- **`page.render` serialises the document a second time.** `cli.main` calls `graph.serialise`, then
  `page.render` calls it again and round-trips the result through decode/encode. Passing the bytes in
  makes "the page carries exactly the bytes the graph file holds" structural instead of incidental.
- **Refusals are logged at ERROR.** `cli.py` ("refused to run: no such source directory", "refused to
  run: N module id(s)...") and `graph.py` ("refusing to build the graph: ...") all use `log.error`.
  CLAUDE.md's logging section reserves ERROR for unexpected failures with the exception and asks for
  WARN on every refusal with its reason. Two earlier reviews let this pass, so treat it as a nit
  rather than a blocker — but the per-file failures already use WARNING, so the two levels currently
  disagree about what a refusal is.
- **Dead weight:** `_depth_at(masked, offsets, position)` never reads `masked`; `_files_under` sorts
  three times where the final `found.sort` alone decides the order; `_bytes_of` is copy-pasted into
  two test modules while `tests/support/` exists for exactly that.

### What I did verify, so you do not have to redo it

On this branch at `96d1b15`. Everything below passed.

- **Lab checks.** `cd backend && ./mvnw test` — 113 tests, BUILD SUCCESS (run twice).
  `cd frontend && npm run typecheck` — clean. `python3 -m unittest discover -t scripts -s
  scripts/module_depth_map/tests` — 53 tests, OK. Matches the orchestrator's
  `01-modules-on-a-page.checks.4.log`.
- **All three attempt-2 blockers are genuinely fixed.** Dangling symlink: `WARNING ... reason=could
  not be opened: No such file or directory`, `filesUnparsed=1`, exit 0, **both outputs written**.
  `@Deprecated public class Foo {}\nclass Bar {}`: `modules: ['shop.till.Bar', 'shop.till.Foo']`.
  `/*/ class Ghost {} */`: `modules: ['shop.till.Real']` only. Also correct: nested-after-annotation
  (`Outer -> [Kind]`), `class B { class N {} }` (`B -> [N]`), `@interface` (kind `annotation`),
  sealed/non-sealed, CRLF, `Thing.class` not mistaken for a declaration, `class` inside a string and
  inside a text block correctly masked.
- **All four attempt-2 "smaller things" are fixed.** `lines` now matches `wc -l` for all 71 modules
  (checked programmatically, zero mismatches). `package-info.java` and `module-info.java` are read at
  DEBUG, counted as parsed, no alarm band, no module. Duplicate ids across two roots log both paths at
  ERROR, exit 3, and write **neither** output. The committed `docs/` files are pinned by a test.
- **The tests bite.** I mutated seven rules one at a time and every one was caught by a named test,
  then restored the tree clean: `_read` dropping `OSError` (4 errors), `/*` not advancing past its own
  star (1), the reserved-keyword cross-check removed (1), `lines` back to `count("\n")+1` (3, including
  both committed-output tests), `label_for` back to `os.path.isdir` (2), duplicate-id refusal removed
  (2), `_TYPE` re-anchored to start-of-line (1).
- **Determinism is solid.** Two runs into separate directories are byte-identical to each other and to
  the committed `docs/module-depth-map.{json,html}`. Five more runs under
  `LC_ALL=C LANG=C TZ=Pacific/Kiritimati PYTHONHASHSEED=13..65`: all identical. A run from another cwd
  with an absolute `--source`: identical. In a throwaway `git worktree` (whose `.git` is a file):
  `roots=backend/src/main/java`, `graphBytes=31187 pageBytes=37162`, both files byte-identical to the
  committed ones, suite 53 OK, `git status` clean inside it. Worktree removed and pruned.
- **No machine-specific values.** Zero `/Users/`, zero `\d{4}-\d{2}-\d{2}`, zero `\d{2}:\d{2}:\d{2}` in
  either output. The tool imports nothing that reads a clock, the environment, randomness, the network
  or a subprocess — a grep for `time.|datetime|random|uuid|getenv|environ|getcwd|socket|urllib|subprocess`
  over the whole tool matches only the word "time" inside a docstring.
- **Completeness and sorting for this repository.** 71 `.java` on disk, 71 in the graph, 0 unparsed,
  8 packages; set difference empty both ways; every module name equals its file's basename; no dangling
  `moduleIds`; `modules`, `packages`, `moduleIds` and every `nested` list are sorted.
- **The masking invariant holds.** `mask_comments_and_literals` returns exactly the same length as its
  input for all 71 backend files and for nine pathological inputs (unterminated block comment, text
  block, string, trailing backslash, `/*/`, empty, lone `/`, `'\''`, `//` at EOF), so brace offsets
  cannot drift.
- **The page.** Playwright over `file://`, `console`/`pageerror`/`requestfailed` subscribed before
  navigating, light and dark at 1024 and 1280 (plus 768/1440/1920): 71 module cards, 8 package sections,
  0 alarm bands, exactly **one** network request per load (the file itself), zero console messages, zero
  page errors, zero failed requests, and `documentElement.scrollWidth == window.innerWidth` at every
  width. Screenshots read and legible: light `rgb(27,28,30)` on `rgb(247,247,245)`, dark
  `rgb(233,234,236)` on `rgb(22,24,27)`, "Source read backend/src/main/java", "Files parsed 71 of 71".
  The rendered card names are exactly the graph's module names, sorted — the page really is a pure
  rendering. The committed HTML contains no `http://`, no `https://` and no `src=`/`href=` at all.
- **The alarm band is honest.** A fixture with an unclosed brace, no package declaration, no type
  declaration and invalid UTF-8 gave one named WARNING each with a distinct reason, the summary WARNING
  ("the page is drawn from 1 of 5 source files"), and the page drew the band naming all four with their
  reasons. A `chmod 000` file gives `reason=could not be opened: Permission denied`. `--source /no/such/dir`
  and `--source <a file>` both log `ERROR ... refused to run: no such source directory` and exit 2.
- **The embedded graph cannot break out of its script element.** A source directory literally named
  `</script><img src=x onerror=alert(1)>` is escaped to `</script>...`; the browser rendered it
  as text in the alarm band, injected no `<img>`, fired no dialog and logged nothing.
- **The application still runs.** The branch changes no Java, TypeScript or `pom.xml` at all
  (`git diff --name-only agentic_engineered..HEAD` matches none of them). Against the orchestrator's
  running app: `GET /api/customers` 200; `POST /api/savings-accounts/1/deposits {"amount":"12.50"}` 201,
  and the backend log shows `INFO i.d.s.deposits.DepositsService : deposit accepted depositId=1
  savingsAccountId=1 fromCurrentAccountId=1 amount=12.50 pointsEarned=12` with its two DEBUG lines
  above it.
- Logs and screenshots from this review at
  `.scratch/module-depth-map/logs/01-modules-on-a-page.review.4.{browser.log,light.png,dark.png,light.narrow.png,broken.png}`.
