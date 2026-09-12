# 01: An anniversary pays a tenth of the euros a deposit still holds

Status: done

**Blocked by:** None (can start immediately).

**What to build:** Money that stays in savings earns again. Every twelve months a deposit's money is
still there, that deposit pays a loyalty bonus of a tenth of the whole euros still sitting in it —
one point per euro, a tenth of that, rounded down — and the points land in the customer's pot like
any other points. A €500 deposit left alone pays 50 points a year after it landed, 50 more a year
after that, and 50 more the year after.

The clock belongs to the deposit and recurs. Each anniversary is twelve calendar months multiplied
out and added once to the moment the money landed, in the zone the application already counts
calendar things in, so the anniversaries never drift and the third falls exactly two years after the
first. A deposit made on 29 February has its anniversaries clamped to the 28th in the years without
a 29th, which is the answer somebody reading a calendar would give.

What an anniversary is worth is decided from what is still in the deposit at that moment, which is
where the forfeit comes from without a rule of its own: a deposit drawn down to nothing is worth
nothing on its anniversary, and one drawn halfway down is worth half. A deposit holding under €10 is
worth nothing at all, because a tenth of nine euros rounds down to no points — the same way €0.99
has always earned no base point.

A sweep pays these, nightly, taking the moment from the application's clock rather than the
machine's, and named for what it does so that a trainer can type it into the development jobs
endpoint and watch a year's loyalty arrive in an afternoon. It pays every anniversary a deposit has
passed and has not been paid for yet, each credited as its own batch dated at its own anniversary —
so winding the clock three years forward and running the job once pays three bonuses, and a deposit
made before this scheme existed is paid every anniversary it has already served.

The rule, the recurring clock, the record of what has been paid and the job that pays it are one new
module's business. The points ledger gains one more reason a deposit can have earned under and one
more way in; it learns nothing about anniversaries. Deposits answers one new factual question —
which deposits still hold money and landed before a given moment, and how much is in them — and
keeps its opinion about points to itself, which is none.

Running the sweep a second time pays nothing more. What has been paid is written down against the
deposit and the anniversary it was paid for, so a second pass over the same rows cannot pay twice —
the same way an expired batch carries the moment it went. The record also keeps the euros the bonus
was worked out from, because the deposit's remaining amount moves on afterwards and that figure is
otherwise gone.

- [x] A deposit left untouched for twelve months earns a tenth of its whole euros, and the points count towards the customer's balance.
- [x] A sweep run inside the twelve months pays nothing, and says out loud that it looked.
- [x] A deposit left untouched pays again on its second and third anniversaries, each counted from the day it landed rather than from the last payment.
- [x] A deposit whose anniversaries have all passed unpaid is paid every one of them in a single sweep, each dated at the anniversary it is for.
- [x] A deposit holding under €10 earns nothing on its anniversary.
- [x] A deposit holding nothing earns nothing, and is not considered by the sweep at all.
- [x] Running the sweep twice pays nothing the second time.
- [x] The bonus is a tenth of the euros' own points, never a tenth of what the streak multiplier paid.
- [x] The sweep can be run out of turn through the development jobs endpoint, by name, and reports itself in the list of jobs that can be run.
- [x] The sweep takes its moment from the application's clock, so a wound-forward clock is what decides which anniversaries have arrived.
- [x] Paying a bonus moves no money: no ledger entry, no change to any balance in euros.
- [x] Paying a bonus secures no week and changes no streak.
- [x] The anniversary arithmetic gets a unit test of its own for the calendar cases the HTTP seam cannot reach — twelve months multiplied out, February clamping, and the ordinal of a given date.
- [x] The sweep logs one INFO line per run carrying the moment it judged against, the cut-off its query used, how many deposits it considered, how many anniversaries it paid and the points; and one DEBUG line per deposit carrying the anniversary, its ordinal, what remained, the whole euros and the points, or the reason it was passed over.

## Verified

Reviewed on `ticket/01-an-anniversary-pays-a-tenth-of-what-a-deposit-still-holds`, two commits on
top of `agentic_engineered` (`240005e` the feature, `cd67f64` the ticket move). Every checkbox below
was exercised against a running application, not read off the tests.

**Checks, re-run by the reviewer.** `cd backend && ./mvnw test` → `Tests run: 206, Failures: 0,
Errors: 0` (BUILD SUCCESS). `cd frontend && npm run typecheck` → clean. No checkstyle or lint plugin
exists in `backend/pom.xml`, so those two are the whole gate.

**The rule, driven over HTTP** against the orchestrator's application (throwaway database, DEBUG on
`io.dataroots.savingstreak`), all log quotes from
`.scratch/loyalty-rate-bonus/logs/01-...app.1.backend.log`:

- `GET /api/dev/jobs` returns `payLoyaltyBonuses / LoyaltyBonusesArePaidNightly / cron 0 30 3 * * *`
  beside `expireOldPoints`. Startup logged `scheduling is on jobs=2 names=[payLoyaltyBonuses,
  expireOldPoints]`. The jobs list is discovered reflectively by `ScheduledJobs`, so nothing had to
  be registered by hand.
- Deposited EUR 500 (deposit 1, `2026-09-09T07:27:48.189Z`, 500 base points). Sweep run immediately
  and again at day 300 paid nothing and said it had looked:
  `loyalty bonuses paid asAt=2027-07-06... landedBefore=2026-07-08... depositsConsidered=0
  anniversariesPaid=0 points=0`.
- Sweep at day 379 paid 50 and balance went 500 → 550:
  `loyalty bonus paid depositId=1 customerId=1 anniversary=2027-09-09T07:27:48.189Z ordinal=1
  remainingAmount=500.00 wholeEuros=500 rate=0.10 points=50 recordId=1`, and the batch was dated at
  the anniversary rather than at the run — `points credited ... reason=LOYALTY_BONUS points=50
  earnedAt=2027-09-09T07:27:48.189Z` while the sweep ran on 2027-09-23.
- Same sweep again: balance unchanged at 550, with `deposit passed over for a loyalty bonus
  depositId=1 ... reason=this anniversary has already been paid anniversary=2027-09-09... ordinal=1`
  and `anniversariesPaid=0 points=0`.
- Withdrew EUR 250 from that deposit; its second anniversary paid 25, not 50:
  `ordinal=2 remainingAmount=250.00 wholeEuros=250 rate=0.10 points=25`, dated
  `2028-09-09T07:27:48.189Z` — exactly two years after the money landed, so nothing drifted.
- A EUR 9.99 deposit was read by the sweep and declined with the reason spelled out:
  `reason=a tenth of what it still holds rounds down to no points ... remainingAmount=9.99
  wholeEuros=9 theLeastABonusIsPaidOn=10`.
- A deposit emptied by a full withdrawal (Bram, deposit 3) was never considered:
  `depositsConsidered=2` in a sweep where three deposits existed, and no line mentions deposit 3.
- One sweep on a clock wound three years past a EUR 1000 deposit paid all three anniversaries at
  once, each at its own date — `ordinal=1 ... anniversary=2029-09-23`, `ordinal=2 ...
  anniversary=2030-09-23`, `ordinal=3 ... anniversary=2031-09-23`, `points=100` each — summarised as
  `depositsConsidered=3 anniversariesPaid=6 points=375`. Running `expireOldPoints` afterwards then
  left exactly 125 points (the 2031 bonuses) out of the 375, which is only possible if each batch
  carried its own anniversary as its earned-at; `pointsExpiringNext` reported `25 on 2032-09-09`.
- A deposit one day short of its first anniversary was handed over by the query's two days of slack
  and declined by the rule, with the reason logged: `reason=still inside its first year
  depositedAt=2031-10-08T07:30:20.809Z nextAnniversary=2032-10-08T07:30:20.809Z
  remainingAmount=77.00`.
- Streak independence: three weekly deposits climbed the ladder to 1.20, so deposit 8 earned 600
  points on EUR 500. Its anniversary paid 50, not 60 — `depositId=8 ... remainingAmount=500.00
  wholeEuros=500 rate=0.10 points=50`. The EUR 50 deposits paid 5 each.
- No euros moved and no week was secured across roughly twenty anniversaries paid: money movements
  stayed at exactly the four deposits and withdrawals I made myself, the savings and current
  balances were unchanged either side of every sweep, and `newSavingsThisWeek=0.00`,
  `currentStreakWeeks=0`, `currentMultiplier=1.00` throughout.
- Idempotency at the database and not only in Java: two `payLoyaltyBonuses` runs fired
  simultaneously against eight deposits with seven unpaid anniversaries between them produced
  `anniversariesPaid=7 points=222` on one and `anniversariesPaid=0 points=0` on the other, and the
  balance moved by exactly 222.
- The guarantee behind that is real and visible at startup. The generated DDL is
  `create table loyalty_bonus_paid (... primary key (id))` with no unique clause — the community
  SQLite dialect drops a composite `@UniqueConstraint` — and `LoyaltyOnStartUp` then runs
  `create unique index if not exists one_bonus_per_deposit_per_anniversary on loyalty_bonus_paid
  (deposit_id, anniversary_ordinal)` and logs `the record of paid anniversaries was made unique per
  deposit and anniversary`. The implementer's account of this is accurate; I confirmed both halves in
  the startup log.

**Nothing broke that was already there.** No `ERROR` and no stack trace from
`io.dataroots.savingstreak` anywhere in the log; the only two resolved exceptions are 400s from my
own malformed curl bodies. The page was driven with Playwright and screenshotted
(`...review.1.overview.png`): it renders fully and styled, and the pot it shows includes the
bonuses. The browser log holds only Vite's connect lines and two `net::ERR_ABORTED` on
`/api/customers/1/accounts` and `/redemptions`, which are the pre-existing `AbortController` in
`frontend/src/App.tsx` firing under React `StrictMode` — nothing on this branch touches the frontend.
A 100-point reward claimed cleanly with loyalty points in the pot.

**Conventions.** `LoyaltyAnniversary` mirrors `PointsExpiry` line for line — same
`Period.ofMonths(12)` constant, same `SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN`, same `plusDays(2)`
cut-off slack, same `anniversaryOf` naming. `LoyaltyOnStartUp` is the same
`SmartInitializingSingleton` shape as `DepositsOnStartUp`. SLF4J throughout, no `System.out`, log
lines in the surrounding `thing happened key=value` style, and `creditLoyaltyBonus` WARNs before it
refuses.

**Two notes for whoever picks up 02-05, neither of them a defect here.**

1. `backend/src/test/java/io/dataroots/savingstreak/support/DepositView.java` still says base plus
   streak bonus "always sum to" `pointsEarned`. That is now false once an anniversary has been paid —
   `GET /api/savings-accounts/1/deposits` returned deposit 8 as `basePoints=500,
   streakBonusPoints=100, pointsEarned=650`. Ticket 04 explicitly owns updating that invariant, so
   leaving it was the right call on scope, but the comment is stale in the meantime.
2. A deposit permanently worth no bonus (under EUR 10) writes no record, so every nightly sweep
   re-evaluates it and logs one DEBUG line per unpaid anniversary — a line a year, for ever. The
   implementer weighed this against a table of zero rows and chose the log; it is DEBUG only and
   reversible.

### Findings from the second-pass code review, and what I made of each

A `/code-review` over `agentic_engineered..ticket/01-...` reported after the pass was recorded. It
raised five points. None of them is an unmet criterion of this ticket, and all five are written down
here because two of them are work somebody must do next.

1. **The deposit total no longer equals its stated parts, and the page shows it.** Confirmed, and it
   is user-visible: `GET /api/savings-accounts/1/deposits` reports deposit 8 as `basePoints=500,
   streakBonusPoints=100, pointsEarned=650`, and the history page renders `700` above
   `500 base + 100 bonus at 1,20×` (screenshot `...review.1.history.png`; the 700 is 600 plus a
   second anniversary paid later in my session). Four places document the invariant that is now
   false: `RecordedDeposit`'s javadoc, `web/DepositResponse.java:14`, `frontend/src/api.ts:218` and
   `support/DepositView.java:13`. **Not a defect in this ticket.** Ticket 01 asks for exactly this
   ("The points ledger gains one more reason a deposit can have earned under"), and the spec is
   explicit that the reason "joins the set of reasons a deposit can have earned under, so every
   existing per-deposit breakdown carries it with no change to any caller". Ticket 04 owns the third
   field and the invariant, ticket 05 the page. Whoever picks up 04 should expect to fix all four
   comments and the `WhatItEarned` line in `frontend/src/App.tsx:1505`.
2. **`Deposit.getCustomerId()` unboxes a nullable `Long`, and the sweep is the first unfiltered
   reader.** Real in structure, unreachable on data this application creates, and worth hardening
   anyway. `customerId` is `private Long` because `DepositsOnStartUp.sayWhoseSavingEveryDepositWas()`
   can leave a legacy row null when the savings account has no holder, and every pre-existing reader
   filters `where deposit.customerId = :customerId` so it can never see such a row.
   `depositsStillHoldingMoneyThatLandedBefore` reads every customer's rows, so one null would throw
   an NPE and roll back the whole nightly sweep for everybody. It cannot happen here:
   `SavingsAccount.customer` is `@ManyToOne(optional = false)`, nothing anywhere deletes a savings
   account or a deposit, and a deposit is refused unless its savings account exists — so the
   `leftAlone` branch that leaves a null behind cannot fire. The cheap fix, for whoever is next in
   this module: add `and deposit.customerId is not null` to the query in
   `DepositRepository.stillHoldingMoneyThatLandedBefore`, or return `Long` and WARN past such a row.
3. **A money-movement row's `pointsEarned` grows years after the euros moved.** Confirmed, same root
   cause as (1) — the `INTO_SAVINGS` row reports `PointsByReason.total()`. The spec's "nothing to the
   ledger of money that moved" means no new entry, and none is created; I verified the ledger stayed
   at exactly the movements I made. `PayingABonusMovesNoMoneyAndSecuresNoWeekApiTest` asserts only
   the row count, so if the growing figure on an existing row is wanted it is currently untested
   either way. Worth a decision in ticket 04, not a blocker here.
4. **A back-dated bonus can make `pointsExpiringNextOn` a date in the past.** Confirmed observable —
   the page read "25 points expire on 23 september 2032" while the application clock read 2034. But
   this is what the spec chose with its eyes open ("a batch for an anniversary long past may
   therefore be credited already beyond its own twelve months and be swept away by the expiry job
   the same night ... the honest outcome of both rules holding at once"), and any batch the expiry
   sweep has not yet collected shows the same way, loyalty or not. Not new behaviour and not a
   criterion of this ticket.
5. **A unique-index violation would roll back the whole sweep rather than one anniversary.** I tried
   the scenario rather than reasoning about it: two `payLoyaltyBonuses` runs fired simultaneously
   over eight deposits with seven unpaid anniversaries returned 200 and 200, logged
   `anniversariesPaid=7 points=222` and `anniversariesPaid=0 points=0`, and moved the balance by
   exactly 222. SQLite's single writer serialises the two transactions, so the loser reads the
   committed rows rather than colliding with them. If it ever did collide the next run recovers, so
   this is a robustness note at most.
