# 04: A deposit is paid at the streak's multiplier and credits a streak bonus

**What to build:** The payoff. A deposit now earns one point per whole euro *multiplied by* the
streak multiplier, and the uplift is credited as a streak bonus beside the base points.

One rule decides every case: **a deposit is paid at the multiplier of the streak as it stands once
that deposit has been counted.** If the deposit is what carries its week past €50, the streak is a
week longer by the time the deposit is priced, so the deposit itself already earns the new, higher
rate. If it does not secure the week, it earns whatever the live streak was already paying. If there
is no live streak, the length is zero and the rate is 1.00×. Implement it as one function of the
streak length after the deposit, not as a set of special cases.

The ladder: a streak of zero or one week pays 1.00×; a streak of *n* weeks pays
`min(1.00 + 0.10 × (n − 1), 1.50)`, so the sixth week and every week after it pays 1.50×. The step
and the cap are named constants here.

Points stay whole and round down twice: floor the amount to whole euros, multiply, floor the product.
€7.60 at 1.30× is 7 base points and 9 in total, so a bonus of 2. Each deposit credits its base
accrual exactly as it does today, plus a streak bonus for the uplift when the uplift is non-zero. The
multiplier a deposit was paid at is recorded at the moment of the deposit and never recomputed — the
derived figures answer "what is my rate", the ledger answers "what was this deposit paid".

The deposit response reports the base points, the streak bonus points, the multiplier applied and
the total; its existing points-earned figure means the total credited, which is unchanged at 1.00×
and therefore still true of every deposit made before this feature existed. The savings account
resource and page gain the current multiplier — the rate the next deposit will earn at.

**Blocked by:** 01 (points reported by reason) and 03 (streak length and best streak).

Status: done

- [x] A deposit made with no live streak earns one point per whole euro, exactly as before.
- [x] The deposit that carries a week past €50 is itself paid at the multiplier of the streak that now includes that week.
- [x] Deposits made earlier in that same week keep the points they were already paid; nothing is topped up retrospectively.
- [x] The second consecutive secured week pays 1.10×, the third 1.20×, the sixth 1.50×.
- [x] The seventh and every later consecutive secured week still pays 1.50× and no more.
- [x] After a lapse, the first week of the new streak pays 1.00× again.
- [x] A deposit below €50 earns points at the current multiplier and does not secure the week on its own.
- [x] Points are whole: €7.60 at 1.30× earns 7 base and 2 bonus, and €3 at 1.50× earns 3 base and 1 bonus.
- [x] A deposit of less than one euro earns nothing at any multiplier, bonus included.
- [x] The points balance rises by the base points plus the bonus points, and the two always sum to the total the deposit reported.
- [x] The bonus is credited as its own batch under its own reason; no batch is credited when the uplift is zero.
- [x] Spending points still draws from the oldest batch first and treats a bonus batch no differently from a base one.
- [x] The deposit response reports base points, streak bonus points, the multiplier applied and the total, and its existing points-earned figure is the total.
- [x] The savings account resource and the savings account page report the current multiplier, alongside the streak figures already there.
- [x] Withdrawing money takes back neither the base points nor the bonus.
- [x] No table is added and no column is dropped or retyped; the new reason is another value in the column that already records why points were earned.
- [x] One INFO line per deposit carries the week, the new savings in it, whether this deposit secured it, the streak length, the multiplier, the base points and the bonus points.

## Verified

Reviewed on attempt 1, on `ticket/04-the-multiplier-pays-a-streak-bonus` — everything it adds on top
of `ticket/03-streak-length-and-best-streak`, which is two commits (`84c3df1` the feature, `95ec6c7`
the ticket move). **All seventeen criteria met and seen working**, driven over HTTP and in the
browser against the running application and read back out of the backend log, not from test output
or response bodies alone.

### Checks, run again by me

- `cd backend && ./mvnw test` → `BUILD SUCCESS`, `Tests run: 150, Failures: 0, Errors: 0` (143 on the
  parent branch, so +7). Run three separate times.
- `cd frontend && npm run typecheck` → exit 0 (Node 24.16.0 first on PATH; the system node is 16).
- Both match the orchestrator's `checks.1.log`. `git status --porcelain` empty at the end.
- Transcripts: `logs/04-…review.1.checks.log`, `…checks2.log`.

### The ladder, over HTTP on the orchestrator's throwaway database

Savings account 1, starting empty, clock on Monday 2026-09-07, one `POST /api/dev/clock/advance`
of 7 days per week. Full transcript in `logs/04-…review.1.curl.log`. Every row below is the
`POST /api/savings-accounts/1/deposits` response read back field by field.

| week | deposit | base | bonus | total | rate | criterion |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | €20 (no run at all) | 20 | 0 | 20 | 1.00 | 1 |
| 1 | €30 — secures, run becomes 1 | 30 | 0 | 30 | 1.00 | 2 (no visible jump, by design) |
| 2 | €20 — does not secure, live run of 1 | 20 | 0 | 20 | 1.00 | 7 |
| 2 | €30 — **carries the week over**, run becomes 2 | 30 | 3 | 33 | **1.10** | 2, 4 |
| 3 | €50 | 50 | 10 | 60 | 1.20 | 4 |
| 4 | €20 — below the minimum, run still 3 | 20 | 4 | 24 | 1.20 | 7 |
| 4 | €30 — carries it over, run becomes 4 | 30 | 9 | 39 | 1.30 | 2 |
| 4 | €7.60 at 1.30 | **7** | **2** | 9 | 1.30 | 8 |
| 5 | €50 | 50 | 20 | 70 | 1.40 | 4 |
| 6 | €50 | 50 | 25 | 75 | **1.50** | 4 |
| 6 | €3.00 at 1.50 | **3** | **1** | 4 | 1.50 | 8 |
| 6 | €0.90 at 1.50 | 0 | 0 | 0 | 1.50 | 9 |
| 7 | €50 — the cap holds | 50 | 25 | 75 | **1.50** | 5 |

`pointsBalance` finished at **459**, which is exactly the thirteen totals added up, and `basePoints +
streakBonusPoints == pointsEarned` on every single response (criteria 10, 13).

**Nothing was topped up behind the crossing deposit** (criterion 3). `GET /api/savings-accounts/1/deposits`
still reports deposit 3 as `20 / 20 / 0 / 1.00` and deposit 6 as `24 / 20 / 4 / 1.20`, unchanged by
the deposits that secured their weeks afterwards.

**The lapse** (criterion 6). Advanced into week 8 with no deposit: `currentStreakWeeks 7,
currentMultiplier 1.50` — still alive, last secured week is the one immediately before. Advanced
again into week 9: `currentStreakWeeks 0, bestStreakWeeks 7, currentMultiplier 1.00` **before any
deposit was made**. The next €50 was paid at `1.00` (base 50, bonus 0); the week after that at `1.10`.

**Withdrawal** (criterion 15). `POST /api/savings-accounts/1/withdrawals` for €100 with the run at 7
weeks: money went 361.50 → 261.50, and `pointsBalance` (459), `newSavingsThisWeek` (50.00),
`currentStreakWeeks` (7), `bestStreakWeeks` (7) and `currentMultiplier` (1.50) were all byte-identical
before and after.

### The credit rows, read in SQLite

`points_credit` on the running database, which is where criteria 11 and 12 actually live:

- **Own batch, own reason.** Every uplift is a separate row with `reason = STREAK_BONUS` pointing at
  the same `source_reference_id` as its `BASE_ACCRUAL` row.
- **No batch when the uplift is zero.** Deposits 1, 2, 3, 12 and 14 have a `BASE_ACCRUAL` row and no
  `STREAK_BONUS` row at all — including deposit 12 (€0.90), whose euros floored away and which still
  gets its zero base batch as before. I also forced the harder case the ticket does not name: **€5 at
  a live 1.10×** floors to `floor(5.50) = 5`, an uplift of zero at a rate above 1.00, and it likewise
  left exactly one row (`id 29, 5, BASE_ACCRUAL, deposit 18`).
- **FIFO treats a bonus batch as any other** (criterion 12). Claimed `FAMILY_CINEMA_PACK` (180 points)
  against a balance of 564. Batches 1–7 came back fully drawn (`remaining_points 0`) — including the
  two `STREAK_BONUS` batches at ids 5 and 7 — and batch 8 sat at 3 of 20, which is 20+30+20+30+**3**+50+**10**
  = 163 plus 17, exactly the earned order with the bonus rows in their places. Balance 564 → 384.

### The schema (criterion 16)

- **No table added.** Nine `@Entity` classes on the parent branch and nine on this one; the table list
  in SQLite is identical.
- **The new reason is another value in the existing column.** `points_credit.reason` is still
  `varchar(255)`, not dropped and not retyped — only its `check (…)` list gained `'STREAK_BONUS'`.
- **A nullable `multiplier_applied` column was added to `deposit`.** The criterion forbids adding a
  *table* and dropping or retyping a *column*, and permits this by its wording; spec.md's "no schema
  change is needed" sentence pulls the other way. I read them together and accepted it, because the
  same spec demands the rate be *recorded and never recomputed* and the rate is not recoverable from
  the points: base 7 / total 9 is consistent with both 1.30 and 1.40, and base 0 carries nothing at
  all. The implementer flagged it rather than slipping it in. I confirmed the fallback works: nulling
  `multiplier_applied` on deposit 1 in SQLite, the history reports it as `1.00` with `pointsEarned`
  unchanged at 20.

### The page

Driven with Playwright (chromium, `console` / `pageerror` / `requestfailed` subscribed before
navigating), script at `logs/04-…review.1.drive.py`, log at `…review.1.browser.log`. Screenshots read,
not merely taken.

- Account 1 at a live run of 2 after a lapse from 7: `earning 1,10× per euro` in the amber the points
  balance uses, above `2 weeks in a row  best ever 7 weeks`. Light and dark, fully styled and legible.
- Account 2, which had never secured a week: `earning 1,00× per euro` / `no week secured yet` — the
  ordinary rate is shown rather than hidden, and account 2 keeps its own run rather than account 1's.
- **A deposit typed into the page's own form**, no reload: €60 into account 2 took it to `1 week in a
  row / best ever 1 week` still at `1,00×`; a week later a second €60 flipped the cell live to
  `earning 1,10× per euro / 2 weeks in a row`, and the history rows read `+ 60` then `+ 66` — the same
  arithmetic `AWeekWithNothingInItEndsTheStreakApiTest` asserts.
- 11 widths from 320 to 1440, light and dark: **page overflow 0 at every one**, and nothing escaping
  the week cell at any of them. The `×` never leaves its figure.
- **0 console errors, 0 pageerrors.** The `net::ERR_ABORTED` lines are StrictMode's double mount
  aborting its first fetch and appear in tickets 02 and 03's review logs too (33 of them there).
  Vite's log holds only its ready banner.

### The log (criterion 17, and the house rule)

`logs/04-…app.1.backend.log`, `io.dataroots.savingstreak` at DEBUG. **0 WARN and 0 ERROR from any
logger, 0 responses in the 5xx range** across 2158 lines. One INFO line per deposit carrying every
value the criterion names:

```
deposit accepted depositId=7 savingsAccountId=1 fromCurrentAccountId=1 amount=30.00
week=2026-09-28/2026-10-04 newSavingsThisWeek=50.00 securedByThisDeposit=true streakWeeks=4
multiplier=1.30 basePoints=30 streakBonusPoints=9 pointsEarned=39
pointsByReason={BASE_ACCRUAL=30, STREAK_BONUS=9} depositedAt=2026-09-28T21:33:42.145Z
```

and the two floorings underneath it, which is the whole pricing redone in one line:

```
points to credit worked out from the amount savingsAccountId=1 depositId=8 amountInEuros=7.60
wholeEuros=7 multiplier=1.30 paidAtTheRate=9 streakBonus=2 earnedAt=…
```

with `WeekAndStreakDerivation`'s `walkedBackThrough=[… 2026-09-28/2026-10-04 EUR 57.60 secured;
2026-09-21/2026-09-27 EUR 50.00 secured; … 2026-08-31/2026-09-06 EUR 0.00 not secured, short by
EUR 50.00 — the run ends here] … currentStreakWeeks=4 bestStreakWeeks=4 multiplier=1.30` saying which
weeks the walk went through to arrive at the rate. SLF4J throughout, no `System.out` anywhere in the
diff.

### Refusals

`amount 0`, `amount abc`, `amount -5`, an amount larger than the current account holds, savings
account 999 on both `POST` and `GET`, and a claim beyond the balance — all read byte-identically to
before, and account 1's points, run and multiplier were unmoved afterwards. No refused deposit wrote
a `deposit` row or a credit. Deposit refusals still resolve through `RefusalsAsHttp#depositRefused`
with their reason in the `org.springframework.web` DEBUG log and no WARN from
`io.dataroots.savingstreak`; that is pre-existing (the parent branch has no `log.warn` on that path
either) and this ticket adds no refusal of its own — the one it *does* add, a rate below 1.00 in
`PointsService`, logs WARN with its reason.

### The tests are load-bearing — mutated myself, both caught

Reverted and the suite re-run green afterwards; `git status --porcelain` empty.

| mutation | result |
| --- | --- |
| the cap removed (`min(…, THE_MOST_A_STREAK_PAYS)` dropped) | 1 failure — `TheMultiplierClimbsToACapAndStopsApiTest`, `expected: 1.50 but was: 1.60` at the seventh week |
| the ladder never climbs (`weeksBeyondTheFirst = 0`) | **7 failures** across both new and pre-existing streak classes |

The two pre-existing assertions this branch changed are strengthened, not weakened, and I recomputed
both by hand: `50 + 55 + 33 + 50` and `60 + 66` are exactly what the one rule pays for those
sequences, and the `60 + 66` case I then reproduced independently through the page on account 2.

### Notes for whoever merges — none of these is a criterion failure

1. **An existing database from before this branch rejects a bonus.** `ddl-auto=update` adds the new
   `deposit.multiplier_applied` column but does not widen `points_credit`'s check constraint, so a
   database created under ticket 03 still carries `check (reason in ('BASE_ACCRUAL'))`. I started this
   branch's application on port 8081 against a real ticket-03-era database file and made a
   bonus-earning deposit: **HTTP 500**, `[SQLITE_CONSTRAINT_CHECK] A CHECK constraint failed (CHECK
   constraint failed: reason in ('BASE_ACCRUAL'))` (`logs/04-…review.1.olddb.backend.log`). The
   transaction rolls back cleanly — no deposit row, no money moved — and a fresh database is
   unaffected. I did not fail the ticket on it: `application.properties` says migrations are deferred
   for this slice, spec.md puts migrating explicitly out of scope, `data/*.db` is gitignored local
   state "recreated on first start", and the lab tells every agent to run on a throwaway database. But
   a trainer carrying a session database across this branch will hit it, and deleting the local `.db`
   file is the fix.
2. **`currentMultiplier` is the rate of the run as it stands, which a securing deposit beats.** The
   ticket's prose glosses it as "the rate the next deposit will earn at", and the page says "earning
   1,10× per euro"; a €50 into an empty week behind a two-week run is in fact paid 1.20×. The
   criterion only asks that the resource and the page *report the current multiplier*, and spec.md's
   own Implementation Decisions define it as the pure-function-of-the-ledger reading the code
   implements, so this is a wording tension in the source documents rather than a defect. Worth a
   sentence of copy if anyone touches it.
3. Small things a later ticket can sweep: `PointsByReason`'s javadoc still says base accrual is the
   only reason there is; `deposits` and `streaks` now import each other (no bean cycle, but the
   package arrow is bidirectional); and `AStreakBonusIsSpentAndKeptApiTest`'s comment claims its claim
   proves FIFO order, which its arithmetic does not distinguish.
4. One transient `UnknownContentTypeException` in `AStreakBuildsWeekByWeekApiTest` appeared in the run
   immediately after a mutation run with seven failures, and did not reproduce in three subsequent
   clean runs. Recording it in case anyone sees it again; I could not make it happen on purpose.
