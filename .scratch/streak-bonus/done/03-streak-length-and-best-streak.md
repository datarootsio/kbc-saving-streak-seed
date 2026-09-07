# 03: A streak of consecutive secured weeks, and the best one ever run

**What to build:** A customer can see how many consecutive weeks they have secured on a savings
account, and the longest streak they have ever run on it. A week is secured once at least €50 of new
savings has landed in it. The current streak is the run of consecutive secured weeks ending at the
most recently secured one — and it counts only while it is still alive, meaning the last secured week
is the current week or the one immediately before it. A customer who skipped last week has a current
streak of zero *now*, on a Wednesday, without waiting for this week to end.

The best-ever streak is the longest such run anywhere in the account's history, and it survives a
lapse: losing the streak costs the run, not the record.

Both figures appear on the savings account resource and on the savings account page beside the week's
progress. Both are derived from the deposit records; nothing is stored and nothing is counted
incrementally.

**Blocked by:** 02 (this week's new savings — supplies the week and the €50 threshold).

Status: done

- [x] The savings account resource reports the current streak in weeks and the best-ever streak in weeks.
- [x] Paying €50 into an account in a week where nothing had landed yet makes the current streak one week.
- [x] Several deposits in one week that together reach €50 secure it exactly as a single €50 deposit does.
- [x] Securing the following week too makes the current streak two weeks.
- [x] A week in which €30 landed does not secure it: the streak that ran up to the previous week is over, and the current streak reads zero.
- [x] A week in which nothing landed ends the streak the same way.
- [x] Once a streak has ended, the best-ever streak still reports its length.
- [x] The current streak reads zero as soon as a week has passed unsecured, even mid-way through the following week and before any deposit is made in it.
- [x] Securing a week after a lapse gives a current streak of one, while the best-ever streak keeps the higher figure.
- [x] A withdrawal, of any size and at any point in a week, neither ends a streak nor prevents a week from being secured that had already taken €50 in.
- [x] Each savings account carries its own streak; deposits into one never lengthen another's.
- [x] The savings account page shows the current streak and the best-ever streak beside the week's progress.
- [x] DEBUG logging shows the weeks the derivation walked back through, which of them were secured, and where the streak was found to end.
- [x] Points earned by a deposit are still unchanged: one per whole euro.

## Verified

Reviewed on attempt 1, on `ticket/03-streak-length-and-best-streak` — everything it adds on top of
`ticket/02-this-weeks-new-savings`, which is two commits (`c7b51f3` the feature, `05de32c` the ticket
move). **All fourteen criteria met and seen working**, on the running application and in the backend
log, not from test output alone.

### Checks, run again by me

- `cd backend && ./mvnw test` → `BUILD SUCCESS`, `Tests run: 143, Failures: 0, Errors: 0` (135 on the
  parent branch, so +8). Run twice: before my mutation and again after reverting it.
- `cd frontend && npm run typecheck` → exit 0 (Node 24.16.0 first on PATH; the system node is 16).
- Both match the orchestrator's `checks.1.log`. `git status --porcelain` empty at the end.

### The derivation, over HTTP on a throwaway database

One account, one week per clock advance, reading `GET /api/savings-accounts/1` after every step. Full
transcript in `logs/03-streak-length-and-best-streak.review.1.curl.log`. The clock started on Monday
2026-09-07, so every week below is a real Monday-to-Sunday week in Brussels.

| what I did | `currentStreakWeeks` / `bestStreakWeeks` | criterion |
| --- | --- | --- |
| fresh account, nothing ever paid in | `0 / 0` | 1 |
| €50.00 into an empty week (exactly the minimum, so the `>=` boundary from above) | `1 / 1` | 2 |
| advance a week, €50.00 again | `2 / 2` | 4 |
| advance, then €20 + €20 + €10 in one week — the run moved only on the third | `2 → 2 → 3` | 3 |
| advance, €30.00, read while that week is still running | `3 / 3` | 5 (see below) |
| advance into the week after the €30 week, nothing deposited | `0 / 3` | 5, 7 |
| advance 2 more days — a Wednesday, still nothing deposited | `0 / 3` | 8 |
| €55.00 in that week | `1 / 3` | 9 |
| withdraw the whole €55.00 — the week still read `newSavingsThisWeek: 55.00` | `1 / 3` | 10 |
| advance, €60.00, advance (empty week), advance again | `2 → 2 → 0 / 3` | 6 |
| accounts 2 and 3 throughout all of the above | `0 / 0` | 11 |
| €50.00 into account 3 — accounts 1 and 2 unmoved | acct3 `1 / 1` | 11 |

Beyond the ticket, four edge cases it does not name:

- **€49.99 in a week does not secure it.** `stillNeededThisWeek: 0.01`, `currentStreakWeeks: 0`, and
  the run lapsed the following week. The threshold is a real `>=` and not a rounding.
- **Gross, not net.** €30 in, the whole €30 straight back out, then €20 in →
  `newSavingsThisWeek: 50.00`, `currentStreakWeeks: 1`. The withdrawal did not reduce the week's
  progress towards the minimum.
- **Emptying the account mid-run.** Withdrawing the entire €100 balance from account 3 while it was
  on a run of two left it on `2 / 2`.
- **A long run.** Six more consecutive secured weeks on account 2 climbed `2,3,4,5,6,7` and held at
  `7 / 7` through an empty week, then read `0 / 7` the week after that one. The record survives a
  lapse of any length.

**On criterion 5, which needs reading against the ticket's own alive rule.** "A week in which €30
landed does not secure it … the current streak reads zero" cannot mean *while that week is still
running*: the ticket's third sentence says a run counts "while the last secured week is the current
week or the one immediately before it", and during the €30 week the week immediately before is
secured, so the run is alive and a deposit before Sunday would continue it. Criterion 6 is worded in
parallel ("a week in which nothing landed ends the streak the same way"), and read the other way it
would zero every streak at midnight each Monday. The implementation reads both as the week *after*
the unsecured one, which is also exactly what criterion 8 spells out. I exercised it both ways —
`3 / 3` during the €30 week, `0 / 3` the week after — and the implementer's reading is the only
self-consistent one. The next agent should not "fix" this.

### The page

Driven with Playwright (chromium, sync API), script at
`logs/03-streak-length-and-best-streak.review.1.drive.py`, console/pageerror/requestfailed subscribed
before navigating and written to `logs/03-streak-length-and-best-streak.review.1.browser.log`.
Screenshots read, not merely taken.

- Account 1, lapsed with a record: `'This week | € 0,00 of € 50,00 | € 50,00 more to go | no weeks in
  a row | best ever 3 weeks'` — under a rule inside the week cell.
  (`.review.1.light-acct1-lapsed-w900.png`)
- Account 2, never secured a week: `'… | € 42,40 more to go | no week secured yet'` — one sentence,
  because "best ever 0 weeks" is a record nobody set. (`.review.1.light-acct2-noweek-w900.png`)
- A €45 deposit typed into that page's own form took it to `'1 week in a row | best ever 1 week'`
  **without a reload**. (`.review.1.light-acct2-deposited-w900.png`)
- A live two-week run: `'2 weeks in a row  best ever 2 weeks'`, on one line at 900px.
  (`.review.1.tworun-w900.png`)
- 11 widths (320→1440) × light and dark: `overflow=0` at every one, and no text node's box escaped
  its cell at any of them. Screenshots at 400/700/1280 in both themes; the 400px and dark shots are
  fully styled and legible, the streak line wrapping onto two lines where the cell is narrow.
- **0 console errors and 0 pageerrors.** The `requestfailed … net::ERR_ABORTED` lines are the
  pre-existing StrictMode double-mount aborting its first fetch — `AbortController` at
  `App.tsx:282`/`:630` on the parent branch, and the same lines appear in ticket 02's own review logs.
  Vite's log (`app.1.frontend.log`) has nothing in it but the ready banner.

### The log, which is where I checked the derivation rather than the response body

201 requests served across my session (`177 × Completed 200`, `24 × Completed 201`), **0 ERROR, 0
5xx, 0 WARN** — correct, since this ticket adds no refusal. 62 `streak of secured weeks derived`
lines from `io.dataroots.savingstreak.streaks.StreaksService`. Criterion 13 is met in full: one line
per read names the weeks walked, what landed in each, whether that secured it, and where the run
ended. The €30 week, verbatim:

```
streak of secured weeks derived from the ledger savingsAccountId=1 zone=Europe/Brussels
thisWeek=2026-09-28/2026-10-04 weeklyMinimum=50.00 earlierDeposits=5 weeksWithSavingInThem=4
walkedBackThrough=[2026-09-28/2026-10-04 EUR 30.00 not secured, short by EUR 20.00 but still
running, so it ends nothing — stepped over; 2026-09-21/2026-09-27 EUR 50.00 secured;
2026-09-14/2026-09-20 EUR 50.00 secured; 2026-09-07/2026-09-13 EUR 50.00 secured;
2026-08-31/2026-09-06 EUR 0.00 not secured, short by EUR 50.00 — the run ends here]
streakEndedAt=2026-08-31/2026-09-06 EUR 0.00 not secured, short by EUR 50.00
securedWeeks=[2026-09-07; 2026-09-14; 2026-09-21] currentStreakWeeks=3 bestStreakWeeks=3
```

The `— stepped over` clause is what makes the alive rule readable rather than something a reader has
to infer, and `securedWeeks` is what lets the best-ever figure be counted off the line by hand, since
the walk never reaches it. Both string-building helpers are behind `log.isDebugEnabled()`
(`StreaksService.java:92`, `:180`), so a read outside a debug session does not join strings it throws
away. No `System.out` and no `console.log` anywhere in the diff.

### Points are untouched (criterion 14)

Every deposit I made earned exactly one point per whole euro: €50→50, €30→30, €55→55, €49.99→49,
€7.60→7, and the balance moved by exactly that each time. `DepositsService`'s own INFO line is
byte-unchanged in the diff.

### The tests are load-bearing — checked, not taken on trust

I mutated the alive rule myself: replaced `if (thisWeekIsStillRunning)` in
`StreaksService.streakBehind` with `if (false)`, so the walk never steps over a still-running
unsecured current week. **Four classes went red** —
`AStreakBuildsWeekByWeekApiTest`, `AWeekShortOfTheMinimumEndsTheStreakApiTest`,
`AWeekWithNothingInItEndsTheStreakApiTest` and `AWithdrawalLeavesTheStreakAloneApiTest`
(`Tests run: 143, Failures: 4`; see `logs/…review.1.mutation-alive-rule.log`). Restored with
`git checkout --`, confirmed byte-identical against a copy taken before the edit, and the suite
re-run green. The one behaviour a reader would most want covered is genuinely covered.

The four clock-moving classes each boot their own application on a database nothing has ever been
written to (`support/AnApplicationWithAClockToMove.java`), one test per class because the development
clock only goes forward — the right call, and the reason a run can be counted from zero at all.
`TheStreakOnASavingsAccountApiTest` asserts on deltas against the shared seeded accounts, as every
other test in the repo does.

### Ticket 02's two handover notes are both honoured

`NewSavingsThisWeek.week()` now has callers (`StreaksService.java:127` and `:268`), and
`ClockConfiguration.java:64` still borrows `SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN` unchanged — the
only addition to `SavingsWeek` is `previous()`, which does not touch how weeks are counted.

### One design decision worth knowing about, deliberately left

Deposits dated in the future are invisible to the streak: `depositsLandedBefore` is bounded at the
start of the current week and the current week's own total is bounded at its end, so a trainer who
winds the clock forward, pays money in and winds it back cannot leave a secured week in a run nobody
has lived through. That is the implementer's decision rather than the ticket's, it is documented at
the call site, and it is the right way round — but it is worth remembering when ticket 04 prices a
deposit off the same derivation.

### /code-review outcome, and the five notes it left

`/code-review` over `ticket/02-this-weeks-new-savings..ticket/03-streak-length-and-best-streak`
found **no correctness bug**, all fourteen criteria genuinely met and test-covered. It independently
proved the same two things I did: that `StreakOfSecuredWeeks`' `currentWeeks <= bestWeeks` invariant
is unreachable (every week the walk counts is a map entry ≥ €50 on adjacent Mondays, which is exactly
what `longestRunIn` counts), and that `securedBy(raw)` and `isSecured(scaled)` can never disagree,
because `AmountOfMoney.quotedToTheCent` makes every `DepositLanded.amount` exactly two decimal places
and `AmountOfMoney` refuses anything with more. It also confirmed that `landedBefore` (`< :until`)
and `landedBetween` (`>= :from and < :until`) agree about the boundary, so the current week's entry
cannot be double-counted.

Its five findings are all low, none blocks a merge, and I am recording them rather than sending the
ticket back for them:

1. **`StreaksService.java:173` — the invariant is constructed before the DEBUG line that would
   explain it.** If it ever tripped, `IllegalArgumentException` would reach no handler in
   `RefusalsAsHttp`, the GET would 500, and `io.dataroots.savingstreak` would have said nothing about
   it — no ERROR, and the `walkedBackThrough=`/`securedWeeks=` line that exists to make the walk
   reproducible never reached. Left because the branch is unreachable by two independent proofs; if
   ticket 04 changes how the run is counted, log the walk before constructing the record.
2. **`StreaksService.java:149` — `walkedBackThrough` is filled on every read, not only under
   debug**, which its own "Guarded, because rendering the weeks is work" comment does not quite
   describe: only the string rendering in `asWalked` is guarded. The list is bounded by the run's
   length, so this is allocation, not a scan. `:161` also builds `weekAsCounted` twice per iteration.
3. **`StreaksService.java:136` — "the future is not history" is applied to whole weeks only.**
   `depositsLandedBefore` drops future weeks, but the current week's own total is summed across the
   whole week, so a deposit stamped Friday counts while the app believes it is Tuesday. That is
   ticket 02's established meaning of `newSavingsThisWeek` rather than anything this ticket changed,
   and the development clock only moves forward, so I could not reach it from the outside.
4. **`DepositRepository.java:62` — `landedBefore` sorts the whole account history** and its only
   caller merges the rows into a `HashMap` keyed by week, so the order is never observed. The
   javadoc's "oldest first" is a promise nobody needs; it mirrors `landedBetween`'s prior art.
5. **`SavingsAccountController.java:26` — the class javadoc still enumerates the Streaks module's
   contribution as "how far into this week's saving it is"** and not the run of weeks. The count of
   four modules is still right and the method javadoc immediately above the endpoint was updated, so
   this is an omission rather than a contradiction.

Separately, and not against this diff: the repo still has no `CONTEXT.md` and no `docs/adr/` despite
CLAUDE.md's "Domain docs" section. That gap predates every ticket in this feature.
