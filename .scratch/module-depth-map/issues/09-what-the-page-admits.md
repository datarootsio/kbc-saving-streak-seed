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
