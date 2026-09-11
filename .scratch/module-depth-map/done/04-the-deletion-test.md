# 04: The deletion test

**What to build:** Each scored module carries a verdict on the page: does deleting it concentrate
complexity, or merely move it to its callers? A module coordinating almost nothing while several
callers go through it is a pass-through, and the page says so. A module with substantial reach earns
its keep, and the page says that too.

The verdict is computed, not written. It states the numbers it came from — how much the module
reaches, how many modules call it — so that a reader can check the reasoning rather than take the
label on trust.

**Blocked by:** 03 (Reach, and the fan).

**Status:** done

- [x] The graph records, for every module, how many other modules call it
- [x] A module with reach of one or less and two or more callers is reported as a pass-through
- [x] A module with substantial reach is reported as earning its keep
- [x] Every verdict states the reach and caller counts it was derived from
- [x] The same source always produces the same verdict, with no wording that varies between runs
- [x] The verdict appears on the page beside the module it judges
- [x] A fixture pass-through and a fixture deep module each receive the verdict they were built to receive
- [x] Run against this repository, the tool identifies the accounts module's service as a pass-through without being told to

## Verified

Reviewed on `ticket/04-the-deletion-test` at `e31b727`, against `ticket/03-reach-and-the-fan`.
Nothing was changed; this is a record of what was run and seen.

**Checks, run again from scratch.** `python3 -m unittest discover -t scripts -s
scripts/module_depth_map/tests` — 374 tests, OK. `cd backend && ./mvnw test` — 113 tests, 0
failures, BUILD SUCCESS. `cd frontend && npm run typecheck` — clean. The branch changes no
Java and no TypeScript (`git diff --stat ...-- backend frontend` is empty).

**The suite bites.** Three mutations applied to a copy and reverted, each leaving the tree
clean afterwards: `gone_through = True` (ignore `callersAtLeast`) → 5 failures; deleting
`drawVerdict(item, module);` from the card → 2 failures; `called_by[target].add(...)`
replaced by `pass` → 15 failures.

**Determinism, and the committed outputs.** Two fresh runs of `python3
scripts/module-depth-map.py` into separate directories: graph and page byte-identical to
each other *and* to `docs/module-depth-map.json` / `docs/module-depth-map.html` (four
`cmp`s, all silent). No `Date`, `Math.random` or `toLocale` anywhere in `page.py`, and the
three verdict words appear in the analyser only inside comments and docstrings — never in
a string literal (`grep -rn "pass-through\|earns its keep\|no finding" scripts/module_depth_map/*.py`).

**The graph.** Independently recomputed every module's callers from the fans in the
document; all 71 matched `callers.moduleIds` exactly, every list sorted and deduplicated,
no module its own caller. Independently recomputed every verdict from `scoring.deletionTest`
thresholds; 0 violations. `deletionTest.reach`/`callers` equal `reach.count`/`callers.count`
on all 71. `modulesByVerdict` = 4 pass-through, 9 earns its keep, 22 no finding, with
`modulesNeverScored` 36 making up the 71.

**The page, driven in chromium** (Playwright, sync API), light and dark at 1024 and 1280:
71 cards each, `scrollWidth - clientWidth == 0` at both widths, and the browser log
(`.scratch/module-depth-map/logs/04-the-deletion-test.review.1.browser.log`) holds **0**
console messages, page errors and failed requests. Every one of the 71 cards was
cross-checked against the graph — verdict present exactly when the graph holds one, text
starting with the document's own verdict word, all three counts in the line, `title` equal
to the rule's `because`, the `found` class only on the pass-through word — **0 problems**.
Screenshots read, not just taken: `AccountsService` renders

    pass-through — coordinates 5 things behind 11 methods a caller can reach, with 5 modules going through it

in alarm ink under a hairline rule, beneath its bar and fan; `DepositsService` renders
`earns its keep — coordinates 8 things behind 3 methods ... with 2 modules going through
it`; `CustomerRepository` (never scored) ends at "reaches nothing this graph holds" with
no verdict at all. Both themes legible.

**Refusals, over the CLI.** Six unusable configurations, each exiting **4**, writing
nothing, and logging a WARN naming the reason: `callersAtLeast: 1` → *"complexity that
moves to one caller, or to none, has not moved anywhere a reader can see"*; no
`deletionTest` → *"deletionTest is missing, and it has to be an object"*; `perMethod: true`
→ *"it has to be a whole number of things"*; a duplicated verdict word → *"a module given
one of them could not be told from the other"*; `neverBelow: -1` → *"there is no such
thing as less than none of them"*; a blank verdict → *"a verdict with nothing written on
it is one no reader could act on"*.

**Logging.** A full `--log-level DEBUG` run is 491 lines with 0 WARNING and 0 ERROR. The
rule is logged once (`deletion test rules read verdicts=pass-through,earns its keep,no
finding reachAtMostPerMethod=1 reachAtMostNeverBelow=1 callersAtLeast=2`), the inputs
behind each decision at DEBUG (`deletion test read name=AccountsService reach=5 methods=11
allowance=11 callers=5 verdict=pass-through`, plus `callers counted module=... through=...`),
and the findings at INFO — four of them, e.g.

    INFO deletion test finding module=io.dataroots.savingstreak.accounts.AccountsService verdict=pass-through reach=5 methods=11 callers=5 through=...DepositsService,...WithdrawalsService,...RewardsService,...CustomerController,...SavingsAccountController

which is criterion 8 met without the page being opened.

**Criterion by criterion.** Callers recorded for all 71 modules. Every scored module with
reach ≤ 1 and ≥ 2 callers is a pass-through (`MovableClock` 0/3, `AmountOfMoney` 0/2), which
holds by construction because the allowance is never below 1. Substantial reach earns its
keep — every scored module with reach ≥ 4 does, except `AccountsService`, which criterion 8
requires be named a pass-through; the two only reconcile if "coordinates almost nothing" is
read against what the module presents, and that reading is the committed rule, argued for
in `scoring.json` and printed on the page. Both fixtures receive their built-for verdicts
(`TheTwoVerdictsAFixtureIsBuiltToEarnTest`). Nothing on the page or in the graph is ranked
or proposed for change.

**Noted, not blocking.** A `/code-review` pass over the range raised six points; none is an
unmet criterion, and two are worth a later ticket rather than this one:

- Reach-0 modules can carry the pass-through finding, where the verdict's own `because`
  ("hands whatever it coordinates back") has nothing to hand back. This is what criterion 2
  literally asks for ("reach of one or less"), and the page and README both warn that the
  fan is a floor — but `AmountOfMoney` is a parsing helper whose deletion would duplicate a
  rule rather than move coordination, and only `MovableClock` is named as the specimen.
- "Building one is calling it" makes a Spring `@Configuration` count toward `callersAtLeast`.
  Checked on this repository and it changes no verdict: `MovableClock`'s other two callers
  (`ClockOnStartUp`, `ClockService`) are real calls through a field, and both of
  `AmountOfMoney`'s are `called on AmountOfMoney`.
- The page and README both say "two numbers" / "both thresholds" and then name three
  (`perMethod`, `neverBelow`, `callersAtLeast`) — a small wart on a page whose premise is
  that the reader can count.
- `graph.py`'s "not counted as a caller" WARN cannot fire (every `moduleId` in a fan was
  resolved from the same module list), and the duplicate-verdict refusal compares unstripped
  strings, so `"pass-through "` would pass validation and render identically.

Also still open and still untouched here, flagged by the reviews of 02 and 03: a refused
deposit of 0 and a refused reward claim leave no `io.dataroots.savingstreak` WARN — only
Spring's `ExceptionHandlerExceptionResolver` at DEBUG. Confirmed again against the running
app (`POST /api/savings-accounts/1/deposits` with `0.00` → 400, no service WARN; the same
account's over-withdrawal → 400 *with* `WARN i.d.s.deposits.WithdrawalsService : withdrawal
rejected ... reason=There is not enough...`). It deserves its own ticket.
