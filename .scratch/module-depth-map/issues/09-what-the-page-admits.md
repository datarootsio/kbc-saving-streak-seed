# 09: What the page admits

**What to build:** The page states its own limits, in its own words, where a reader will see them.

It carries the date of the snapshot it represents, passed in when the tool runs rather than read from
the machine's clock, so the output stays identical between runs. It names itself an observation of the
codebase on that date, not a list of work to be done — this repository is extended by agents, and an
undated ranking of shallow modules reads as an instruction to start merging things. And it says
plainly what it did not measure: what was excluded and under which rule, and which parts of an
interface the tool cannot see at all.

That last part matters most. Invariants and ordering constraints are as much a part of an interface as
the methods are, they are not mechanically derivable, and a page that quietly leaves them out invites
a reader to mistake the score for the whole picture.

**Blocked by:** 06 (Behind the shape).

**Status:** needs-info

- [x] The snapshot date is supplied when the tool runs and never read from the system clock
- [x] The page shows the snapshot date it was given
- [x] The page names itself an observation on that date rather than a backlog, in words a reader will see without hunting
- [x] The page lists everything excluded from scoring and the rule that excluded it
- [x] The page states which parts of an interface the tool does not measure, naming invariants and ordering constraints
- [x] The page reports how much of the source was parsed, so a reader can weigh what they are looking at
- [x] Nothing on the page describes a module as needing to be changed

## Review feedback - attempt 1

All seven acceptance criteria are met on this repository and I saw each of them work — the date is
an argument and no clock is read anywhere, the page carries it, the framing sentence is the first
thing under the `h1`, the exclusions and their rules are listed, "What this page does not measure"
names the invariants and the ordering constraints, the coverage sentence reports 74 of 74, and a
scan of all 74 module panels finds no prescriptive word. What follows is not a missing criterion;
it is four defects in the code and prose this ticket added, each reproducible, each cheap, and each
one the page or the seam saying something that is not true. This tool's whole claim is that every
sentence on it can be checked, so these are worth a second attempt rather than a merge.

### 1. `a_snapshot_date` accepts a date written in non-ASCII digits, and the page renders it

`scripts/module_depth_map/graph.py:61` — `_A_DATE = re.compile(r"\A\d{4}-\d{2}-\d{2}\Z")` is
compiled without `re.ASCII`, so `\d` matches any Unicode decimal digit and `int()` then parses it.

    $ T=$(mktemp -d)
    $ python3 scripts/module-depth-map.py --snapshot-date '٢٠٢٦-٠٩-٠٦' --graph $T/g.json --page $T/p.html
    $ echo $?                       # 0
    $ python3 -c "import json;print(json.load(open('$T/g.json'))['snapshot'])"
    {'date': '٢٠٢٦-٠٩-٠٦'}

Fullwidth digits (`２０２６-０９-０６`) pass too. The page then reads "An observation of this
repository's source as it stood on ٢٠٢٦-٠٩-٠٦." `a_snapshot_date`'s own docstring says the check was
written out by hand rather than left to `date.fromisoformat` precisely to stop "the same argument
producing two different documents ... which is the one thing this tool may not do" — and this leaves
several spellings of one day each writing a different document. Expected: the same refusal
`20260906` gets. `r"\A[0-9]{4}-[0-9]{2}-[0-9]{2}\Z"`, or `re.ASCII`, closes it.
`test_the_committed_graph_says_which_day_it_is_a_picture_of` uses the same `\d` pattern, so it
would not catch a graph committed this way either.

### 2. `build()` now raises a second refusal, and its docstring still says it raises one

`scripts/module_depth_map/graph.py:289-317` — the docstring's last sentence still reads "... the one
failure this function is documented to raise", meaning `DuplicateModules`. The first statement in
the body is now `dated = a_snapshot_date(snapshot)`, which raises `SnapshotNotADate`. The paragraph
added for `snapshot` explains what the argument is and never says the call refuses. A caller reaching
the tool's own seam (`graph.build(roots, rules, snapshot)`), reading the contract and guarding only
`graph.DuplicateModules`, gets an uncaught traceback the first time a date is misspelled. This is the
same declared-versus-thrown disagreement the tool reports as a finding on other modules; the seam
should say what it refuses.

### 3. "as all three stood on the day above" — there is no three

`scripts/module_depth_map/page.py:504`. On this repository the page's opening lede renders:

    Every module this application is made of — both halves of it, java and typescript — grouped by
    the package it lives in, each drawn as what its interface costs a caller over a fan of everything
    it coordinates on that caller's behalf, as all three stood on the day above.

Nothing in that sentence was presented as a group of three. It is on the first screen of the real
page, in the sentence this ticket edited when it moved the framing up to the `dated` paragraph. It
reads worse on a single-language run, where the same clause follows "written in java":

    $ T=$(mktemp -d); mkdir -p $T/src/shop
    $ printf 'package shop;\npublic class Till { public void ring() {} }\n' > $T/src/shop/Till.java
    $ python3 scripts/module-depth-map.py --source $T/src --snapshot-date 2001-02-03 \
        --graph $T/g.json --page $T/p.html
    # renders: "...written in java, grouped by the package it lives in, ... as all three stood on
    #           the day above."

Expected: a clause that names what it is dating, and that stays true in both branches of the
language sentence.

### 4. The new coverage sentence does not agree with itself in the singular

`scripts/module_depth_map/page.py:611-627`. The same fixture above renders:

    Every number here was read from 1 of 1 source file under the source named above — every one of
    them. 1 of 1 module drawn here are scored, and what was left out of the scoring, and out of the
    reading altogether, is listed under the two headings that follow.

"1 of 1 module ... are scored". The noun is pluralised through `count(document_.modules.length,
"module", "modules")` but the verb is hardcoded `are`. This file already carries the fix for exactly
this, three hundred lines further down in the deletion-test paragraph:
`count(document_.scoring.modulesNeverScored, "module is", "modules are")`. Fold the verb into the
`count` call the same way.

### Smaller things, none of them blocking on their own

- `--help` prints `[--snapshot-date YYYY-MM-DD]` in the optional brackets while the argument's own
  help text four lines below says "Required". The comment in `cli.py:107` gives a fair reason for not
  using argparse's `required=True`; nothing was then done to keep the usage line honest.
- `page.py:611-627` calls the unread entries "source files" and "file(s)". `filesSeen`/`filesUnparsed`
  count unreadable *directories* too — which is why the alarm box immediately above says "source
  path" (`ADirectoryThatWillNotOpenIsNamedTest` asserts `filesUnparsed == 1` for a locked directory).
  With one locked directory the page calls a directory a file, two inches under a box that does not.
- "What was never scored, and under which rule" is drawn unconditionally. On a run where nothing is
  excluded it renders "0 of 1 modules are drawn but never scored ... Every rule that excluded
  anything is here with the count it excluded ... a reader can point at and argue with:" followed by
  three bullets that each say "— 0 modules —". Reproduced with the Till.java fixture above. Consider
  dropping zero-count rules from the list, or the whole section when nothing was excluded.
- `page.py:70` — `.dated .day { color: var(--ink); }` is a no-op. `.dated` never sets a colour, so
  the paragraph already inherits `--ink` from `body`. Measured in the browser: `body`, `p.dated` and
  `p.dated .day` are all `rgb(27, 28, 30)`; `p.lede` is `rgb(92, 96, 103)`. The comment above it
  describes a contrast the rule does not draw (the contrast is real, but it comes from `.dated` not
  being `.lede`).
- `graph.py:96` — `SnapshotNotADate.given` is stored and never read; `cli.py` logs `refused.reason`,
  which already embeds the value. Compare `DuplicateModules.clashes`, which the CLI does use.
- `graph.py:322-326` — the DEBUG line passes a fixed string literal as a `%s` argument
  (`reason=%s", dated, "handed to this run; ..."`). It can never vary; fold it into the format string.
- `tests/whatthepageadmits/test_the_page_says_what_it_did_not_measure.py:252` —
  `test_nothing_the_document_says_about_a_module_proposes_changing_it` substring-scans *every* string
  in the document, including `scoring.refusals.*.because` and `source.notRead.because`, which are
  prose about measures rather than about any module. `"to fix"` therefore bans the accurate phrase
  "nothing to fix"; `"rewrite"` would fail on a class named `Rewriter`, `"todo"` on `TodoItem`. It
  already cost one accurate sentence: `scoring.json:90` lost "This is the cheaper of the two to fix
  and the more expensive to be handed by a customer" and gained a paraphrase of its sibling
  finding's closing clause, so the two refusal findings now end on the same thought and a reader is
  told nothing about how they differ. Narrowing the scan to `document["modules"]` would let the
  sentence come back.

### What I ran

Everything below was run on `ticket/09-what-the-page-admits` at `5963cda`, working tree clean.

- `python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests` → **832 tests, OK**.
- `cd backend && ./mvnw test` → exit 0, 113 tests, BUILD SUCCESS.
- `cd frontend && npm run typecheck` (Node 24.16.0 on PATH; system node is v16) → exit 0.
- `python3 scripts/module-depth-map.py --snapshot-date 2026-09-06 --log-level DEBUG` → exit 0, and
  `git status --short` afterwards printed nothing, so the committed `docs/module-depth-map.{json,html}`
  are byte-identical to a fresh run. Log: `logs/09-what-the-page-admits.review.1.run.debug.log`, with
  `run started snapshotDate=2026-09-06 ...`, `snapshot date given date=2026-09-06 reason=handed to
  this run; no clock is read anywhere in this tool` at DEBUG, `graph built snapshotDate=2026-09-06
  ... filesParsed=74 filesUnparsed=0 modules=74 scored=38 neverScored=36`, and `run finished
  snapshotDate=2026-09-06 ... flows=3 flowsTraced=3`.
- Refusals, each over the real repository, each WARN with its reason and exit 6, and no output file
  written (`logs/09-what-the-page-admits.review.1.refusals.log`): no `--snapshot-date`; `2026-02-30`
  ("is not a day there was: day is out of range for month"); `2026-13-01` ("month must be in 1..12");
  `20260906`, `2026-W37-1` and `''` ("is not a day written YYYY-MM-DD"). `2024-02-29` is accepted.
- Determinism (`logs/09-what-the-page-admits.review.1.determinism.log`): two runs at `2001-02-03`
  are `cmp`-identical in both files; a run at `2027-01-01` differs from one at `2001-02-03` in the
  date and in nothing else once masked; today's date `2026-09-06` appears nowhere in a run dated
  `2001-02-03`; the only date-shaped string in either output is the one the run was given, and there
  is no time of day. `grep` over `scripts/module_depth_map/*.py` finds `datetime` used only to
  construct `datetime.date(year, month, day)` — no `now()`, no `today()`, no `time`.
- Playwright over `docs/module-depth-map.html`, console/pageerror/requestfailed subscribed:
  **zero console messages, zero page errors, zero failed requests** across light, dark, 1280px and
  900px (`logs/09-what-the-page-admits.review.1.browser.log`,
  `...review.1.panels.browser.log`). `scrollWidth == clientWidth` at both widths, so no sideways
  scroll. Screenshots read, not just taken: `...review.1.header.{light,dark}.png` (the bold dated
  sentence directly under the `h1`, "Snapshot 2026-09-06" as the first fact in the box),
  `...review.1.limits.{light,dark}.png`, `...review.1.neverscored.light.png` (data carrier 26,
  generated repository 9, entry point 1 — summing to the 36 the document reports),
  `...review.1.panel.png`.
- All 74 module panels opened one at a time and their text scanned with the page's own body text
  (`...review.1.alltext.txt`, 153,626 characters) for prescriptive words. Three distinct hits, all
  three the page's own denials: "nothing here is proposed", "no module here is named as one that
  ought to change", "not a proposal to delete it". Separately, all 2,516 strings in
  `docs/module-depth-map.json` scanned the same way: zero hits.

## Review feedback - attempt 2

All seven acceptance criteria are met on this repository and I exercised each one myself, and all
four defects plus all six smaller items from attempt 1 are genuinely fixed — I reproduced every one
of the attempt-1 repro steps and each now behaves as asked. The checkboxes stay ticked for that
reason. What follows is a second set of defects, most of them in the same places attempt 2 touched.
Two of them are the previous round's fixes applied at one of their two sites; one of them is a
guarantee this attempt weakened while the README went on advertising the strong version. This tool's
whole claim is that every sentence on it can be checked, so these are worth a third attempt.

### 1. The documented way to regenerate the page hardcodes one day, and no test can catch a stale one

`scripts/module_depth_map/README.md:10` and `scripts/module-depth-map.py:4` both print the
regeneration command as:

    python3 scripts/module-depth-map.py --snapshot-date 2026-09-06

Those are the two places a maintainer copies from. Meanwhile
`tests/thisrepository/test_the_committed_outputs_are_what_a_fresh_run_writes.py:41-54` feeds the
fresh run the date it reads **out of the committed graph itself**
(`the_day_the_committed_outputs_say_they_are_of()` json-loads `docs/module-depth-map.json` and hands
back `snapshot.date`).

Put together: six months from now somebody adds a Java class, sees the committed-outputs test fail,
copies the command as printed, and regenerates `docs/` from new source carrying `2026-09-06`. Both
byte comparisons then pass, because the fresh run was handed the same stale date out of the file
being compared. `ThisRepositoryIsDatedByWhoeverRanTheToolTest` only checks the date is *shaped* like
a day, so it passes too. The page then says "An observation of this repository's source as it stood
on 2026-09-06" about source from 2027 — which is the exact failure this whole ticket exists to
prevent.

The tests already know the right form: their own failure messages say
`--snapshot-date <the day you are dating it>`. Expected: the README and the script docstring say it
the same way, not with a literal that ages. Worth considering alongside it whether anything can
falsify a wrong-but-well-formed committed date at all, since it is the one value in those files no
reading of the repository can rediscover.

### 2. The README still advertises the whole-document scan this attempt replaced

`scripts/module_depth_map/README.md:211-213` reads:

    A test walks every string in the graph document — which is
    everything the page can say about any module — and fails on the words a proposal is
    written with.

That was true at attempt 1. Attempt 2 narrowed the scan to four prose keys under
`document["modules"]` plus `scoring.exclusions[].because`
(`PROSE_THIS_TOOL_WROTE_ABOUT_A_MODULE = ("because", "finding", "verdict", "matched")`,
`test_the_page_says_what_it_did_not_measure.py:463`), and the narrowing was correct — but the README
sentence was left describing the old behaviour, including the parenthetical ("which is everything
the page can say about any module") that is precisely the equivalence the narrowing exists to deny.
A maintainer reading this believes a prescriptive word anywhere in the document would be caught. It
would not. Reproduce by reading the two side by side. This is the same defect class attempt 1 was
sent back for in point 2 (`build()`'s docstring declaring one refusal while raising two) — fixed
there, recreated here, in an attempt whose own log says "README.md updated".

### 3. The narrowed scan no longer covers the refusal prose that lands on eight cards

`test_nothing_the_document_says_about_a_module_proposes_changing_it` runs over
`ThePageSaysItsOwnLimitsTest`'s fixture — `Till` / `Prices` / `Receipt`. None of those documents or
throws a refusal, so every module in that fixture carries **zero** findings. I checked:

    fixture modules: ['shop.till.Prices', 'shop.till.Receipt', 'shop.till.Till']
    findings per module: {'shop.till.Prices': 0, 'shop.till.Receipt': 0, 'shop.till.Till': 0}

    real repo: findings = 9 on 8 modules
    finding prose is verbatim scoring.refusals.*.because: True

So `scoring.refusals.{documentedNeverRaised,raisedNeverDocumented}.because` — copied verbatim onto
`modules[].findings[].because` and rendered on eight cards of the committed page — is now scanned by
nothing. The attempt-1 review found a real violation in exactly that prose ("This is the cheaper of
the two **to fix** and the more expensive to be handed by a customer"); the replacement scan could
not find it again. `scoring.exclusions[].because` was deliberately kept in scope because excluded
cards print it; `scoring.refusals.*.because` is printed on cards for the same reason and was not.
Expected: either a fixture with a `@throws`/`throw` disagreement so the findings path is populated,
or `scoring.refusals` kept in scope the way `scoring.exclusions` was.

(The page itself is clean today — I scanned all 74 panels and all 2,516 graph strings and found
nothing. This is a hole in the guarantee, not a live violation.)

### 4. The "source path, not file" fix was applied to one of its two sites

Attempt 1's smaller item said `filesSeen`/`filesUnparsed` count unopenable **directories**, so
calling them files disagrees with the alarm box. The new paragraph was fixed. The counts box three
inches above it was not — `page.py:551-552` still reads `fact("Files parsed", ...)` and
`fact("Files not parsed", ...)`.

Reproduce with a locked directory:

    T=$(mktemp -d); mkdir -p $T/src/shop $T/src/shut
    printf 'package shop;\npublic class Till { public void ring() {} }\n' > $T/src/shop/Till.java
    printf 'package shop;\npublic class Hidden {}\n' > $T/src/shut/Hidden.java
    chmod 000 $T/src/shut
    python3 scripts/module-depth-map.py --source $T/src --snapshot-date 2001-02-03 \
        --graph $T/g.json --page $T/p.html

The rendered page then says, in this order and within one screen:

    Files parsed        1 of 2
    Files not parsed    1
    1 source path could not be read; nothing declared inside is drawn below
    src/shut — directory could not be read: Permission denied
    ... Every number here was read from 1 of 2 source paths ... and 1 path could not be read at all

The box calls `src/shut` a file; the two things under it call it a path. That is the same
self-disagreement the fix was for, still on the page.

### 5. "named at the top of this page" points the wrong way

`page.py:761` tells a reader the invariants and the ordering constraints are "named at the top of
this page". `page.py:481`, in the paragraph directly under the `h1`, says "What it did not measure
is stated **below** in its own section". Both refer to "What this page does not measure", which is
neither at the top nor immediately below — on the real page it is the fourth block down, after the
counts box and the alarm band. A reader who follows "at the top of this page" scrolls to the `h1`
and finds neither word there. Expected: one direction, and one that is true — naming the section is
more robust than pointing up or down, since the order of blocks has already moved once in this
ticket.

### 6. "0 of 1 module drawn here are scored"

`verb(n, is, are)` at `page.py:540` special-cases only `n === 1`, so `n === 0` takes the plural while
the noun still agrees with `M`. Where `M` is 1 and `N` is 0 the coverage sentence renders a
disagreement:

    T=$(mktemp -d); mkdir -p $T/src/shop
    printf 'package shop;\npublic record Money(long cents) {}\n' > $T/src/shop/Money.java
    python3 scripts/module-depth-map.py --source $T/src --snapshot-date 2001-02-03 \
        --graph $T/g.json --page $T/p.html
    # renders: "... 0 of 1 module drawn here are scored, ..."

Narrower than the case attempt 1 reported — it needs a tree whose every module is excluded *and*
which holds exactly one — and it is not reachable from this repository or from any of its packages
(I checked all six: none gives M=1, N=0). The never-scored sentence is safe, because its branch only
renders when `N >= 1`. Listed because it is the same sentence and the same helper, and because
`ThePageSaysItsLimitsOnTheSmallestRunThereIsTest` is the class that would cover it.

### 7. `main` guards one of the two refusals `build` now declares

`cli.py:196-199` wraps `graph.build(...)` in `try: ... except graph.DuplicateModules`. `build`'s
docstring — correctly rewritten this attempt — now says it "Refuses with exactly two exceptions ...
`SnapshotNotADate` ... and `DuplicateModules`", and its first statement is
`dated = a_snapshot_date(snapshot)`. The raise is unreachable today only because `main`
pre-validates at `cli.py:138`. It is a latent version of the disagreement point 2 of attempt 1 was
about, and the new test only asserts the docstring names both — nothing asserts the caller guards
both. Cheap to close either by catching it or by saying in `main` why it cannot arrive.

### What I ran

Everything below on `ticket/09-what-the-page-admits` at `dc65795`, working tree clean.

- `cd backend && ./mvnw test` → exit 0, 113 tests, BUILD SUCCESS.
- `cd frontend && npx tsc --noEmit` → exit 0 (Node 24.16.0 on PATH; system node is v16).
- `python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests` → **841 tests, OK**.
- `python3 scripts/module-depth-map.py --snapshot-date 2026-09-06 --log-level DEBUG` → exit 0, and
  `git status --short` afterwards printed nothing, so the committed `docs/module-depth-map.{json,html}`
  are byte-identical to a fresh run. Log lines read: `run started snapshotDate=2026-09-06 ...`,
  DEBUG `snapshot date given date=2026-09-06 reason=handed to this run; no clock is read anywhere in
  this tool`, `graph built snapshotDate=2026-09-06 ... filesSeen=74 filesParsed=74 filesUnparsed=0
  modules=74 scored=38 neverScored=36`, `run finished snapshotDate=2026-09-06 ... flows=3
  flowsTraced=3`.
- **Refusals**, all over the real repository, each WARN with its reason, each exit 6, and no output
  file written (`logs/09-what-the-page-admits.review.2.refusals.log`): `٢٠٢٦-٠٩-٠٦`,
  `２０２６-０９-０６`, `2026-٠٩-06`, `١٢٣٤-٥٦-٧٨`, `20260906`, `2026-W37-1`, `''`, `today` all
  refused with "is not a day written YYYY-MM-DD" — attempt 1's defect 1 is fixed; `2026-13-01` and
  `2026-00-01` with "month must be in 1..12"; `2026-02-30`, `2026-01-00` and `2026-02-29` with "day
  is out of range for month"; no argument at all with "no snapshot date given ...". `2024-02-29`
  accepted, exit 0. Driven at the seam too (`graph.build`): `None` and the integer `20260906` are
  refused as well, and `SnapshotNotADate` no longer carries `.given`.
- **Determinism** (`...review.2.determinism.log`): two runs at `2001-02-03` `cmp`-identical in both
  files; `2001-02-03` vs `2027-01-01` identical once the date is masked; today's real date
  `2026-09-06` appears nowhere in a run dated `2001-02-03`; the only date-shaped string in either
  output is the one the run was given. `grep` over the tool finds `datetime` used only to construct
  `datetime.date(year, month, day)` — no `now()`, no `today()`, no `time`.
- **Playwright** (chromium, sync API, `console`/`pageerror`/`requestfailed` subscribed before
  navigating) over the real page and five fixtures — one module; one module plus a `chmod 000`
  directory; two modules with one excluded; an empty tree; a tree of one record where everything is
  excluded — in light and dark at 1280, and the real page also at 900 and 1024.
  **Zero console messages, zero page errors, zero failed requests on every load**
  (`...review.2.browser.log`, `...review.2.panels.browser.log`, `...review.2.edge.browser.log`,
  `...review.2.narrow.browser.log` hold only my own lines). `scrollWidth == clientWidth` at all three
  widths.
- Screenshots read, not just taken: `...review.2.repo.header.light.png` (the bold dated sentence
  directly under the `h1`, "Snapshot 2026-09-06" first in the box), `...review.2.repo.limits.dark.png`,
  `...review.2.repo.full.light.png`, `...review.2.one.header.light.png` (the single-language branch,
  reading "written in java, ... as the source stood on the day above" — attempt 1's defect 3 is
  fixed), `...review.2.panel.accountsservice.png`.
- **Grammar checked in every branch** (attempt 1's defect 4): `38 of 74 modules drawn here are
  scored` / `1 of 1 module drawn here is scored` / `1 of 2 modules drawn here is scored` /
  `0 of 0 modules drawn here are scored`, and `36 of 74 modules are drawn but never scored` /
  `1 of 2 modules is drawn but never scored` / `1 of 1 module is drawn but never scored`. The one
  that disagrees is `0 of 1 module drawn here are scored`, point 6 above. A run that excluded nothing
  renders "Nothing was left out. ..." with no empty bullets, and `1 of 2 source paths ... 1 path
  could not be read at all` on the locked-directory run.
- **All 74 module panels** opened one at a time and their dialog text plus the page body (154,153
  characters) scanned for the eight prescriptive words plus twelve more of my own: the only two hits
  were on my own extra word "needs a", both in prose about this tool's parser ("needs a type this
  tool never resolves", "needs a JSX parser"), neither about a module. Separately all 2,516 strings
  in `docs/module-depth-map.json`: **zero hits**. Criterion 7 holds on the page as it stands.
- `--help` now prints `usage: module-depth-map --snapshot-date YYYY-MM-DD [-h] [--source DIR] ...`,
  unbracketed and first, agreeing with the "Required" in its own help text — attempt 1's smaller
  item 1 is fixed. `graph.build.__doc__` names both refusals — attempt 1's defect 2 is fixed.
  `p.dated` measures `rgb(27, 28, 30)` against `p.lede`'s `rgb(92, 96, 103)` in light and
  `rgb(233, 234, 236)` against `rgb(162, 168, 176)` in dark, and `.dated .day` is gone.
- The running application was checked as well, though this branch touches no Java or TypeScript:
  `GET /api/customers` → 200, a deposit → 201 leaving
  `INFO i.d.s.deposits.DepositsService : deposit accepted depositId=1 savingsAccountId=1
  fromCurrentAccountId=1 amount=3.00 pointsEarned=3` in
  `logs/09-what-the-page-admits.app.2.backend.log`, a deposit of `0.00` → 400 with
  "A deposit has to be an amount of more than zero, and 0.00 is not.", and no ERROR or stack trace
  anywhere in that log. Vite on 5173 → 200.

## Review feedback - attempt 3

All seven acceptance criteria are met on this repository and I exercised every one of
them myself, and all seven defects from attempt 2 are genuinely fixed — I reproduced each
attempt-2 repro step and each now behaves as asked. The checkboxes stay ticked for that
reason. What sends this back is one defect the page's own new section produces on a run
neither previous review tried: the page denying a limit two lines under the limit it just
stated. Everything after it is smaller, and two of those are older than this branch.

### 1. "Nothing was left out." is drawn directly beneath a list of what was left out

`scripts/module_depth_map/page.py:711-718`. The zero-exclusion branch is guarded on
`document_.scoring.modulesNeverScored === 0` alone, and says nothing about
`source.notRead.paths`. On a run where a rule declined a path but excluded no module, the
section above lists the declined path and this one opens by denying it. Reproduce with a
source tree holding one class and a `node_modules` directory — the rule that matches it is
the one already in `scoring.json`:

    T=$(mktemp -d); mkdir -p $T/src/shop $T/src/node_modules/left-pad
    printf 'package shop;\npublic class Till { public void ring() {} }\n' > $T/src/shop/Till.java
    printf 'export const pad = (s) => s;\n' > $T/src/node_modules/left-pad/index.ts
    python3 scripts/module-depth-map.py --source $T/src --snapshot-date 2001-02-03 \
        --graph $T/g.json --page $T/p.html

The graph is `pathsNotRead=1 modules=1 scored=1 neverScored=0`, and the rendered page then
reads, in this order and inside one screen:

    What was not read at all
    ...
    1 path under the source read above matched that rule, and nothing inside them is
    drawn here:
      src/node_modules — a directory named node_modules

    What was never scored, and under which rule
    Nothing was left out. Every module drawn on this page carries a bar: none of the 3
    rules in scoring.json that decline to price a module matched anything this run read.

"Nothing was left out." is the page's own summary of its exclusions contradicting its own
list of them, one block apart, in the section this ticket added and on the subject this
ticket exists for. The rest of the sentence is true and the heading gives it its scope,
but the three words a skimming reader takes are unqualified and false on that run. This is
the same defect class attempt 2 was sent back for in point 6 ("0 of 1 module drawn here
are scored") and strictly larger: that one no reader could be misled by.

Expected: a sentence that is true on every run the tool can produce — either scoped to the
scoring ("Nothing was left out of the scoring"), or guarded on
`source.notRead.paths.length` as well as on `modulesNeverScored`.

`test_a_run_that_excluded_nothing_says_so_rather_than_listing_empty_rules`
(`test_the_page_says_what_it_did_not_measure.py:503-512`) is the test that covers this
branch, and it asserts three source-shape substrings against a fixture with no declined
path in it, so it cannot see this. A fixture with a declined path and no excluded module —
the one above — is what would.

### 2. The new coverage sentence names the two headings in the reverse of their order

`page.py:653-658`. It renders, on the committed page:

    ... 38 of 74 modules drawn here are scored, and what was left out of the scoring, and
    out of the reading altogether, is listed under the two headings that follow.

The two headings that follow are "What was not read at all" (the reading) and then "What
was never scored, and under which rule" (the scoring). The sentence names scoring first
and reading second; the page renders reading first and scoring second. Not literally
false — both are under the two headings — but a reader who follows the sentence looks for
the scoring heading and meets the reading one. It is the same wrong-direction class as
attempt 2's point 5, which this attempt fixed by naming the section instead of pointing at
it; the same remedy works here.

### Smaller things, both of them older than this branch

Neither is this ticket's doing — both are on `ticket/08-the-frontend-honestly` unchanged —
but both are the exact defects attempt 1 and attempt 2 sent this ticket back for, sitting
one file away from the code that fixed them, and closing them while in the area is cheap.

- `cli.py:286-290` — the end-of-run WARNING still calls unopenable directories files, and
  refers to one of them as "them": `the page is drawn from 1 of 2 source files: 1 could
  not be read, and every module in them is missing from it`. Attempt 2's point 4 asked for
  one name for one thing and this attempt gave the counts box "Source paths parsed"; the
  log line reporting the identical two counts still says "source files". Reproduce with
  `chmod 000` on a subdirectory of the source root and read the run's own stderr beside
  the page it wrote.
- `page.py:677-679` — `count(n, "path", "paths") + " under the source read above matched
  that rule, and nothing inside them is drawn here:"`. The noun is counted and the pronoun
  is hardcoded plural, so a run with one declined path renders "1 path ... nothing inside
  them". This is what `verb(n, of, is, are)` was added three sections away to stop.
  Reproduce with the `node_modules` fixture in point 1.

### Things I checked and am not asking for

Named so the next attempt does not spend time on them.

- **`main` guarding one of `build`'s two refusals** (attempt 2's point 7). The comment at
  `cli.py:193-201` plus `test_a_date_this_tool_will_not_read_never_reaches_the_build_seam`
  is one of the two remedies attempt 2 offered ("either by catching it or by saying in
  `main` why it cannot arrive"), and the claim it makes is true: `main` hands `build` what
  `a_snapshot_date` returned, and that function answers with the string it was given.
- **The hand-written `usage=` line** (`cli.py:69-72`). I put a new `--brand-new-option` on
  the parser and `test_every_argument_this_command_takes_is_on_its_usage_line` failed with
  `AssertionError: False is not true : ['--brand-new-option']`. The guard bites.
- **Zero-count exclusion rules no longer listed.** That is what attempt 1 asked for.
- **`a_snapshot_date` running twice per run.** Deliberate and documented: once so the CLI
  refuses before walking the source, once so a caller reaching the seam is held to the
  same rule.
- **The two refusal `because` sentences.** They no longer end on the same thought, which
  is what attempt 1 complained of.

### What I ran

Everything below on `ticket/09-what-the-page-admits` at `1c144f9`, working tree clean.

- `cd backend && ./mvnw test` → exit 0, `Tests run: 113, Failures: 0, Errors: 0`, BUILD
  SUCCESS. `cd frontend && npx tsc --noEmit` → exit 0 (Node 24.16.0 on PATH; system node
  is v16).
- `python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests` → **848
  tests, OK**, twice (once before my mutations and once after restoring them).
- `python3 scripts/module-depth-map.py --snapshot-date 2026-09-06 --log-level DEBUG` →
  exit 0, and `git status --short` printed nothing afterwards, so the committed
  `docs/module-depth-map.{json,html}` are byte-identical to a fresh run. Read from
  `logs/09-what-the-page-admits.review.3.run.debug.log`: `run started
  snapshotDate=2026-09-06 ...`, DEBUG `snapshot date given date=2026-09-06 reason=handed
  to this run; no clock is read anywhere in this tool`, `graph built
  snapshotDate=2026-09-06 ... filesSeen=74 filesParsed=74 filesUnparsed=0 pathsNotRead=0
  packages=9 modules=74 scored=38 neverScored=36`, `run finished snapshotDate=2026-09-06
  ... flows=3 flowsTraced=3`.
- **Refusals**, fourteen of them over the real repository, each a WARN naming its reason,
  each exit 6, and no output file written in any
  (`logs/09-what-the-page-admits.review.3.refusals.log`): `٢٠٢٦-٠٩-٠٦`, `２０２６-０９-０６`,
  `2026-٠٩-06`, `20260906`, `2026-W37-1`, `''` and `today` all refused with "is not a day
  written YYYY-MM-DD" — attempt 1's defect 1 stays fixed; `2026-13-01` and `2026-00-01`
  with "month must be in 1..12"; `2026-02-30`, `2026-01-00` and `2026-02-29` with "day is
  out of range for month"; no argument at all with "no snapshot date given, so there is
  nothing to date this page with". `2024-02-29` accepted, exit 0.
- **Determinism** (`...review.3.determinism.log`): two runs at `2001-02-03` `cmp`-identical
  in both files; `2001-02-03` against `2027-01-01` identical in both files once the date is
  masked; today's real date `2026-09-06` appears nowhere in a run dated `2001-02-03`; the
  only date-shaped string in either output of that run is `2001-02-03`; no time of day
  anywhere. `grep` over the tool finds `datetime` used only to build
  `datetime.date(year, month, day)` — no `now()`, no `today()`, no `time.`, and no
  `new Date` or `Date.now` in the generated page.
- **Playwright** (chromium, sync API, `console`/`pageerror`/`requestfailed` subscribed
  before every navigation) over the committed page and six fixtures — one module; one lone
  record where nothing is scored; one module plus a `chmod 000` directory; an empty tree;
  two modules with one excluded; and one module plus `node_modules` — in light and dark at
  1280, and the committed page also at 900, 1024 and 1440. **Zero console messages, zero
  page errors, zero failed requests on every load**, and `scrollWidth == clientWidth` at
  every width (`...review.3.browser.log`, `...review.3.narrow.browser.log`,
  `...review.3.panels.browser.log` hold only my own lines).
- Screenshots read, not merely taken: `...review.3.repo.light.top.png` and
  `...repo.dark.top.png` (the bold dated sentence directly under the `h1`, "Snapshot
  2026-09-06" the first fact in the counts box, "Source paths parsed 74 of 74"),
  `...review.3.locked.light.top.png` ("Source paths parsed 1 of 2 / Source paths not
  parsed 1" above the alarm band naming `src/shut` — attempt 2's point 4 is fixed),
  `...review.3.nought.dark.top.png`, `...review.3.narrow900.png`.
- **Grammar in every branch there is** (attempt 2's point 6): `38 of 74 modules drawn here
  are scored`, `1 of 1 module drawn here is scored`, `1 of 2 modules drawn here is
  scored`, `0 of 0 modules drawn here are scored`, and **`0 of 1 module drawn here is
  scored`** on the lone-record fixture — the sentence attempt 2 reported. Never-scored:
  `36 of 74 modules are drawn but never scored`, `1 of 2 modules is drawn but never
  scored`, `1 of 1 module is drawn but never scored`.
- **All 74 module panels** opened one at a time (`button.name`, then Escape; the card waits
  `A_SECOND_CLICK = 500`ms before opening, so a shorter wait opens nothing) and their
  dialog text plus the page body — 154,198 characters, `...review.3.alltext.txt` — scanned
  for the eight banned words plus twelve of my own. The only two hits were on my own extra
  word "needs a", both in prose about this tool's parser ("needs a type this tool never
  resolves", "needs a JSX parser"), neither about a module. Separately all 2,516 strings in
  `docs/module-depth-map.json`: **zero hits**. Criterion 7 holds.
- **Exclusion accounting**: `data carrier 26 + generated repository 9 + entry point 1 = 36`,
  equal to `scoring.modulesNeverScored`, and all 36 never-scored modules carry an
  `excludedBy` with both `rule` and `matched` (e.g. `{"matched": "annotated with
  SpringBootApplication", "rule": "entry point"}`). Criterion 4 holds.
- **Four of the new guards mutation-tested, and all four bite**: a prescriptive word put
  into `scoring.refusals.raisedNeverDocumented.because` failed
  `test_nothing_the_document_says_about_a_module_proposes_changing_it` with `'refactor'
  unexpectedly found ... : modules[2].findings[0].because`, which is attempt 2's point 3
  genuinely closed; a day put back into the README's command line failed
  `TheDocumentedWayToRegenerateDoesNotAge` with `Lists differ: [] != ['--snapshot-date
  2026-09-06']`; `verb` reverted to conjugating on N alone failed
  `test_the_verb_is_singular_when_either_number_beside_it_is_one`; a new option added to
  the parser failed `test_every_argument_this_command_takes_is_on_its_usage_line`. Every
  mutation was restored and `git status --short` was empty afterwards.
- The running application was exercised too, though this branch touches no Java or
  TypeScript (`git diff ticket/08-the-frontend-honestly..ticket/09-what-the-page-admits --
  backend frontend` is empty): `GET /api/customers` → 200; a 3.00 deposit → 201 leaving
  `INFO i.d.s.deposits.DepositsService : deposit accepted depositId=1 savingsAccountId=1
  fromCurrentAccountId=1 amount=3.00 pointsEarned=3 depositedAt=2026-09-06T10:08:39.324Z`
  in `logs/09-what-the-page-admits.app.3.backend.log`; a 0.00 deposit → 400 with "A deposit
  has to be an amount of more than zero, and 0.00 is not.". No ERROR and no stack trace in
  that log. Vite on 5173 → 200, its log clean.

### A note for whoever runs this next

The repository's only checkout was switched off `ticket/09-what-the-page-admits` and onto
`ticket/07-flows-through-the-modules` partway through this review (`git reflog`:
`checkout: moving from ticket/09-what-the-page-admits to ticket/07-flows-through-the-modules`).
Everything above was run before that happened, on ticket/09 — the rendered pages I read
carry "What this page does not measure" and "Source paths parsed", which exist on no other
branch — and the two defects above were reproduced afterwards from an extracted copy of
ticket/09's `scripts/` tree. This commit was made from a temporary `git worktree` on
ticket/09 so that the main checkout was left exactly as it was found, on
ticket/07-flows-through-the-modules with a clean tree.
