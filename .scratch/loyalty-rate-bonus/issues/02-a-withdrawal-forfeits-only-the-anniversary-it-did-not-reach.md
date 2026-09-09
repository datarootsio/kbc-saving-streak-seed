# 02: A withdrawal forfeits only the anniversary it did not reach

Status: needs-review

**Blocked by:** 01 (an anniversary pays a tenth of the euros a deposit still holds).

**What to build:** Taking money out costs the customer the coming year's bonus on the money they
took, and nothing else. Bonuses already paid on earlier anniversaries are theirs for good and are
never clawed back, however much they withdraw afterwards.

A deposit emptied before its anniversary pays nothing on it — that is the forfeit. A deposit partly
drawn down pays on what is left in it, so somebody who spends half their savings keeps half the
year's bonus rather than losing all of it. This is the generous half of the rule and it is
deliberate: the strict reading, where any touch at all forfeits the whole year, would let a €1
withdrawal destroy a hundred points on a €1,000 deposit.

Withdrawals already come out of the oldest deposit first, so a withdrawal that only partly covers
the savings draws down the oldest, most-nearly-vested money and leaves the newer deposits' clocks
running whole. That ordering is what makes the forfeit fall where a customer would expect it, and
this ticket is where it is pinned down as behaviour rather than left as an accident of how
withdrawals happen to be recorded.

This is expected to need no new production code — a drawn-down deposit simply has fewer euros in it
when its anniversary arrives. The work is saying so in tests that would fail if that stopped being
true. If it turns out something is needed, that is this ticket's finding rather than a reason to
widen it.

- [x] A deposit emptied before its first anniversary earns nothing on it.
- [x] A deposit emptied after its first anniversary keeps the bonus it was already paid, and earns nothing on its second.
- [x] A deposit drawn halfway down before its anniversary earns a tenth of what is left in it.
- [x] A withdrawal that only partly covers the savings comes out of the oldest deposit, and the newer deposits' anniversaries pay in full.
- [x] A withdrawal after an anniversary has been paid takes back none of those points, and the customer's balance is unchanged by it.
- [x] A deposit drawn below €10 earns nothing on its anniversary while the remaining euros stay in the account.
- [x] Withdrawing rewrites no earlier bonus record: the batch a past anniversary paid is still there afterwards at the figure it was paid and dated at the moment it was earned, and a sweep run after the money left neither pays it again nor claws it back.

Criterion 7 was reworded on the reviewer's first option. As first written it asked that "what a past
anniversary was worked out from is still readable afterwards", and what it was worked out from is
the euros that were in the deposit — a figure this seam serves through nothing, since the paid
record and its repository are the Loyalty module's own. Deriving €500 back out of "50 points" is not
reading the record, so the criterion is now worded as what this seam can observe: the batch survives
the withdrawal at its figure and its earned-at moment, and nothing rewrites it. **The read itself is
carried by ticket 04**, which puts a deposit's loyalty figures on the API; asserting on the euros
belongs there.

## Finding: an anniversary is judged when the sweep runs, not when it falls

Recorded here under the ticket's own sentence — "if it turns out something is needed, that is this
ticket's finding rather than a reason to widen it" — and raised by the reviewer of attempt 3. No
production code was changed for it.

A withdrawal forfeits an anniversary that has already fallen but has not yet been swept, even though
the money did serve the full twelve months. `LoyaltyService` reads what the deposit holds at the
moment the sweep judges it, and `DepositRepository.stillHoldingMoneyThatLandedBefore` (`where
deposit.remainingAmount > 0`) drops a deposit at zero out of the query before the rule is applied at
all. So a €1,000 deposit that turns one year old at 09:00 and is emptied at 12:00 the same day is
paid nothing by the small-hours sweep that follows: 100 points lost on money that stayed the whole
year. Normally that window is a few hours; it is the whole of any stretch the nightly job does not
run, and it is unbounded for a deposit carrying several unpaid past anniversaries at once — the case
`ADepositMadeBeforeTheSchemeExistedIsPaidEveryAnniversaryAtOnceApiTest` exists for — which loses all
of them to one withdrawal.

This is a recorded trade-off rather than a defect of this ticket: the spec's Implementation Decisions
already choose this reading out loud ("a tenth of the whole euros still in that deposit at the moment
the anniversary is judged"), and criteria 2 and 5 are both worded around a bonus that *was already
paid*. Nothing in this branch would fail if that window widened. Whether the sweep should instead
judge on what the deposit held at the anniversary is a decision for whoever owns the spec.

Two smaller notes from the same review, neither changed and neither blocking: the DEBUG line's
`drawnDown` list has no bound, so draining an account built from years of weekly deposits emits one
long line where `PointsService` would write one line per row; and the class javadoc's arrangement
argument is about the figure a wrong sweep would report, not about the sweep's query — which the
javadoc now says itself.

## Review feedback - attempt 1

The behaviour is right. I drove all seven scenarios over HTTP against a running application on a
throwaway database and every number came out as the ticket describes, and I mutation-tested all
three new test classes myself rather than taking the implement log's word for it — each one really
does fail when the rule it guards is broken. Criteria 1-6 are ticked on that evidence.

It is going back for four things, none of them a wrong answer for a customer and all of them cheap.
Three are places where the new code says something that is not true, in a repository whose tests
carry their reasoning in prose and are reviewed on whether they explain themselves; one is the
last criterion, which is ticked but is not actually read by anything.

### 1. Criterion 7's second half is not proven by anything (this is why it is unticked)

`EmptyingADepositForfeitsOnlyTheAnniversaryItDidNotReachApiTest:145-149` asserts
`pointsExpiringNextOf == 50` and `pointsExpiringNextOnOf == paidInOn.plusYears(2)`. That is good
evidence for the *first* half of the criterion — the bonus batch survives the withdrawal at the
figure it was paid and at the moment it was earned — and I confirmed the same thing independently:
across five withdrawals in my session, two of which emptied a deposit that had already been paid,
the backend log contains three `insert into loyalty_bonus_paid` statements and **zero** `update` or
`delete` against that table.

But the criterion's second half is "what a past anniversary was **worked out from** is still
readable afterwards", and what it was worked out from is the euros. That figure lives in
`loyalty_bonus_paid.whole_euros_it_was_worked_out_from` (visible in the insert in the log) and is
readable through no endpoint: `LoyaltyBonusPaid` and its repository are package-private, and
`anniversariesAlreadyPaidFor` deliberately projects only `depositId` and `anniversaryOrdinal`.
Deriving €500 back out of "50 points" is not the same as reading the record.

The implement log flags this honestly and says the field arrives in ticket 04. Agreed — so do one
of these two things rather than leaving a ticked box with nothing behind it:

- state in the ticket that this criterion is carried by ticket 04 and reword it to what this seam
  can observe (the batch survives at its figure and its earned-at moment, and nothing rewrites the
  record), or
- if it is to stay as worded, it needs the read that ticket 04 adds, and it belongs there.

Either way, do not tick it while the only thing asserted is a figure derived from it.

### 2. A false claim in the one test that exists to pin down criterion 4

`AWithdrawalComesOutOfTheOldestDepositApiTest:122-125`. The final assertion's description says:

    a tenth of the whole 500 the newer deposit still holds, in full — a withdrawal
    spread across the deposits would have left it holding 400 and paying 40

The second clause does not hold. Work the alternative through: under a newest-first allocation the
€100 comes out of the newer deposit (500 → 400) and the older keeps its €100. The day-379 sweep
then pays 10 on the older deposit (its anniversary has arrived and it still holds the money), and
the day-580 sweep pays 40 on the newer — 600 + 10 + 40 = **650**, which is exactly what line 125
asserts. So the assertion whose description claims to rule newest-first out is the one assertion in
the class that cannot tell the two allocations apart.

All the discriminating power is in the earlier assertion at lines 109-114. I confirmed that by
mutation: adding `Collections.reverse(oldestFirst)` to `WithdrawalsService` made the class fail at
line 114 with `expected: 600L but was: 610L` and nowhere else. The test is load-bearing today, but
a reader who trusts line 123-124 would believe the last assertion protects the ordering and could
drop line 114 without the test going red for the reason the prose promises.

Fix one of: reword lines 123-124 to say what that assertion actually shows (that the untouched
deposit pays a tenth of the whole 500 when its own anniversary comes round, months after the
other's), or assert the two anniversaries' payments separately so the second one discriminates too.

### 3. "Two-thirds of a year" is not 200 days

`AWithdrawalComesOutOfTheOldestDepositApiTest:29` and `:93` both describe
`DAYS_BETWEEN_THE_TWO_DEPOSITS = 200` as "two-thirds of a year". It is a little over half
(200/365 = 0.55). Nothing depends on it — every window still clears its anniversary by a wide
margin — but it is stated twice as a fact and it is wrong.

### 4. The new DEBUG line renders unconditionally, against this repo's own stated convention

`WithdrawalsService:68`, `:80` and `:96`. The line itself is welcome and I read it working — see the
Verified notes below — but `drawnDown` is built whatever the log level: one five-value string
concatenation with two `asMoney` calls per allocated deposit at line 80, and `String.join(" ",
drawnDown)` evaluated as an argument at line 96, all discarded when the application runs at INFO.

This repository has already decided this question in both directions and written down why:

- `streaks/WeekAndStreakDerivation.java:84` wraps exactly this shape in `if (log.isDebugEnabled())`
  — "Guarded, because rendering the deposits is work".
- `points/PointsService.java:274` deliberately does not guard, and says why: "there is nothing to
  render here, only getters".

This new line renders, so it falls on the guarded side of the repo's own split. Wrap the line-80
build and the line-96 call in `if (log.isDebugEnabled())`, or, if you think a withdrawal is rare
enough that the work does not matter, say so in the comment the way `PointsService` does — the
comment currently argues the opposite way, that a withdrawal may be "spread over a long list of
deposits".

### Also worth a look, not blocking

`EmptyingADepositForfeitsOnlyTheAnniversaryItDidNotReachApiTest:87` reads `paidInOn` from
`theDateTheClockReads()` just *before* the two deposits, then anchors the expected expiry date on it
at line 149. If that read and the deposit straddle local midnight in Europe/Brussels the expected
day is off by one and the test fails for a reason unrelated to loyalty. `stays.depositedAt()` is
already in hand and is the moment the expiry is actually derived from. The same pattern exists in
`pointsexpiry/WhatExpiresNextIsReportedApiTest:70`, so it is inherited rather than new — but it is
avoidable here.

### What I ran, so you can reproduce it

Checks, both clean: `cd backend && ./mvnw test` → `Tests run: 209, Failures: 0, Errors: 0` /
`BUILD SUCCESS`; `cd frontend && npm run typecheck` → clean.

Application on a throwaway database with `io.dataroots.savingstreak` at DEBUG, driven with curl
(seeded Anke: current account 1, savings 1 and 2; Bram: current account 2, savings 3). Day numbers
are `POST /api/dev/clock/advance`; the sweep is `POST /api/dev/jobs/payLoyaltyBonuses/run`.

| day | what I did | what came back |
|---|---|---|
| 0 | €500 → S1, €500 → S2 (Anke), €100 → S3 (Bram) | Anke 1000 pts, Bram 100 pts |
| 30 | withdraw €500 from S2 | S2 €0, Anke still 1000 — nothing clawed back |
| 200 | €500 → S3 (Bram) | Bram 600 pts, S3 €600 |
| 210 | withdraw €100 from S3 | allocated to `depositId=3`, the **oldest**; S3 €500 |
| 379 | sweep | Anke 1000 → **1050** (S1 only); Bram **600, unchanged** |
| 379 | withdraw €500 from S1, sweep again | Anke **1050** both times — no clawback, no second payment |
| 379 | `expireOldPoints` | Anke **50**, `expiringNext=50 on=2028-09-09` |
| 379 | fresh €500 → S1, €500 → S2 | Anke 1050 |
| 409 | withdraw €250 from S1, €491 from S2 | S1 €250, S2 €9, Anke still 1050 |
| 580 | sweep | Bram 500 → **550** (the untouched newer deposit paid 50 in full) |
| 758 | sweep | Anke 1050 → **1075** (+25 on the €250; nothing on the €9; nothing on the emptied deposits' second anniversary) |

The 600-at-day-379 line is criterion 4's real test: had the withdrawal come out of the newer
deposit, `depositId=3` would still hold €100 and would have paid 10.

Log lines behind it, all from the backend log:

    withdrawal drew the oldest deposits down first savingsAccountId=3 withdrawalId=2
      drawnDown=[depositId=3 landedAt=2026-09-09T08:02:07.152Z took=100.00 leftInIt=0.00]

    loyalty bonus paid depositId=1 customerId=1 anniversary=2027-09-09T08:02:07.104Z ordinal=1
      remainingAmount=500.00 wholeEuros=500 rate=0.10 points=50 recordId=1

    loyalty bonus paid depositId=5 customerId=1 anniversary=2028-09-23T08:03:10.483Z ordinal=1
      remainingAmount=250.00 wholeEuros=250 rate=0.10 points=25 recordId=3

    deposit passed over for a loyalty bonus depositId=6 customerId=1
      reason=a tenth of what it still holds rounds down to no points
      anniversary=2028-09-23T08:03:10.505Z ordinal=1 remainingAmount=9.00 wholeEuros=9
      theLeastABonusIsPaidOn=10

    deposit passed over for a loyalty bonus depositId=4 customerId=2
      reason=this anniversary has already been paid anniversary=2028-03-28T08:02:14.543Z ordinal=1

I also drove a withdrawal spanning two deposits (€550 out of S3 holding €500 + €100), which is what
the new log line is for, and it reads correctly — oldest drained first:

    withdrawal drew the oldest deposits down first savingsAccountId=3 withdrawalId=6
      drawnDown=[depositId=4 landedAt=2027-03-28T08:02:14.543Z took=500.00 leftInIt=0.00]
      [depositId=7 landedAt=2028-10-06T08:04:04.067Z took=50.00 leftInIt=50.00]

Refusals all WARN with their reason: more than the account holds ("There is not enough in that
savings account to move EUR 9999.00. It holds EUR 50.00."), zero, negative, and another customer's
current account. Zero `ERROR` lines and zero stack traces in the whole run.

Mutations I applied and reverted, to check the tests are load-bearing:

- `Collections.reverse(oldestFirst)` in `WithdrawalsService` → `AWithdrawalComesOutOfTheOldestDeposit`
  fails at line 114, `expected: 600L but was: 610L`.
- `getRemainingAmount()` → `getAmount()` in `DepositsService:422` → `PartlyDrawingADepositDown`
  fails, `expected: 1025L but was: 1100L`. `EmptyingADeposit` still passes on this alone.
- plus dropping `remainingAmount > 0` from `DepositRepository:108` → `EmptyingADeposit` fails,
  `expected: 1050L but was: 1100L`.

Page: signed in as Anke in Chromium and read the screenshots. Overview and the savings-account-1
history both render fully styled, showing €250,00 / €9,00 / €2.221,00, "1.075 points to spend" and
"50 points expire on 9 september 2028". Browser log has zero `pageerror` and zero console errors;
the two `ERR_ABORTED` request-failures are React StrictMode's double-mount and appear identically in
attempt 1's review of ticket 01. Separately, the deposit rows still read "500 base + 0 bonus at
1,00×" beside a total of 525 — that is ticket 05's job and not this ticket's, noted only so the next
reader does not think it is new.

## Review feedback - attempt 2

All four points from attempt 1 are fixed, and I proved each of them rather than taking the implement
log's word for it. The behaviour is right and I drove all seven criteria over HTTP against a running
application on a throwaway database; the numbers are in "What I ran" below and every checkbox above
is left ticked on that evidence. Nothing a customer sees is wrong.

It is going back for one thing only: three statements in the new tests that are not true. This is
the same defect class attempt 1 sent it back for (its point 2), and all three sentences were already
in the file at that point — attempt 1 simply did not catch them, and attempt 2 did not touch them.
That makes this a three-sentence edit and nothing else. No production code needs to change, no
assertion needs to move, and the behaviour needs no further work.

Why it matters here rather than being waved through: the previous round's point 2 was one wrong
sentence in an assertion description, and the reason given for sending it back was that this is "a
repository whose tests carry their reasoning in prose and are reviewed on whether they explain
themselves". Two of the three below tell a future reader that a test detects something it
demonstrably does not, which is exactly how a load-bearing assertion gets deleted by someone who
trusted the comment above it.

### 1. `EmptyingADepositForfeitsOnlyTheAnniversaryItDidNotReachApiTest:130-134`

The comment reads:

    // Nor does a sweep run after the withdrawal reconsider an anniversary it has already paid.

and the assertion under it is described as "a sweep run after the money left neither pays again nor
claws anything back".

The outcome is true and it is criterion 7's second half, so the box stays ticked. The stated
mechanism is not: by that line both of Anke's deposits hold €0, so
`DepositRepository.stillHoldingMoneyThatLandedBefore` excludes them and the sweep never looks at
either anniversary. It cannot "reconsider" one. I saw this directly in the running application's
log — the second sweep of my session, run immediately after the withdrawal:

    loyalty bonuses paid asAt=2027-09-23T08:26:01.623483Z landedBefore=2026-09-25T08:26:01.623483Z
      depositsConsidered=0 anniversariesPaid=0 points=0

`depositsConsidered=0`. Nothing was passed over for having been paid, because nothing was considered
at all — there is no `reason=this anniversary has already been paid` line anywhere in that sweep.

Mutation, to show what the assertion is and is not sensitive to. Replace the guard at
`LoyaltyService:137` with `if (false)`:

    - if (alreadyPaid.contains(new AnAnniversary(deposit.id(), ordinal))) {
    + if (false) {

then `./mvnw test`. All three of this ticket's classes stay green. The only classes that go red are
ticket 01's `AnAnniversaryPaysATenthOfTheDepositsEurosApiTest` and
`EachAnniversaryPaysAgainFromTheDayTheMoneyLandedApiTest`, both with `expected: 200 OK but was: 500
INTERNAL_SERVER_ERROR` (the unique constraint firing). So idempotence is genuinely covered — by
ticket 01, not here.

Fix: say what this sweep actually shows. Something like "a deposit holding nothing is outside the
sweep's query, so the sweep run after the money left has nothing to pay and nothing to take back;
that an anniversary already paid is not paid a second time is ticket 01's, asserted there." Do not
leave a comment claiming this class guards the already-paid guard.

### 2. `EmptyingADepositForfeitsOnlyTheAnniversaryItDidNotReachApiTest:35-36` (class javadoc)

    one balance is then the whole of the arithmetic: a sweep that wrongly paid the
    emptied deposit would show up as 100 where the test expects 50.

A sweep that wrongly *considered* the emptied deposit would pay a tenth of what it holds, which is a
tenth of nothing, so the balance would still read 1050 and the test would still pass. Reaching 100
needs the sweep to read the deposit's original amount as well, which is a second, unrelated fault.

Verified both halves by mutation, each on its own:

- drop `deposit.remainingAmount > 0` from the query at `DepositRepository:108` → all three of this
  ticket's classes stay green (`Tests run: 3, Failures: 0`, `BUILD SUCCESS`);
- `getRemainingAmount()` → `getAmount()` at `DepositsService:422` → this class stays green, while
  `PartlyDrawingADepositDownPaysOnWhatIsLeftInItApiTest` fails with `expected: 1025L but was: 1100L`.

Fix: either name the fault the arrangement really catches (a sweep paying an emptied deposit *on
what it originally held* would read 1100), or drop the "would show up as 100" clause and justify the
one-customer arrangement on its own terms.

### 3. `PartlyDrawingADepositDownPaysOnWhatIsLeftInItApiTest:32-33` (class javadoc)

    One balance is then the whole of the arithmetic — a deposit paid on what it started
    with rather than on what is left in it would show up as 75 where this test expects 25.

75 is wrong. Both deposits started at €500, so paying on what they started with is 50 + 50 = 100
points of bonus, and the balance reads 1100 against the 1025 the test expects. 75 is the difference
between 1100 and 1025, not a figure anything reports.

Reproduced: `getRemainingAmount()` → `getAmount()` at `DepositsService:422`, then
`./mvnw test -Dtest=PartlyDrawingADepositDownPaysOnWhatIsLeftInItApiTest`:

    PartlyDrawingADepositDownPaysOnWhatIsLeftInItApiTest
      .a_deposit_half_drawn_down_pays_half_and_one_drawn_under_ten_euros_pays_nothing:98
    expected: 1025L
     but was: 1100L

Fix: 100 where this test expects 25, or 1100 where it expects 1025 — whichever reads better beside
the surrounding prose.

### What I confirmed is fixed, so nobody redoes it

- **Attempt 1 point 1 (criterion 7).** Reworded to the reviewer's first option and now genuinely
  observed. After `expireOldPoints` at day 379 the API reported `pointsBalance=50`,
  `pointsExpiringNext=50`, `pointsExpiringNextOn=2028-09-09` — twelve months after the 2027-09-09
  anniversary, i.e. two years after the money landed on 2026-09-09. Across six withdrawals in my
  session the backend log holds two `insert into loyalty_bonus_paid` statements and **zero** `update
  loyalty_bonus_paid` and **zero** `delete from loyalty_bonus_paid`. Nothing rewrites the record.
- **Attempt 1 point 2 (the false newest-first claim).** Fixed, and the replacement assertion really
  does discriminate. Reversing the allocation (`Collections.reverse(oldestFirst)` in
  `WithdrawalsService`) fails the class at line 118, `expected: 600L but was: 610L`. Setting that
  first assertion to 610 as well, so execution reaches the new one, fails at line 134 with
  `expected: 50L but was: 40L` and the description that names newest-first. Two independent
  assertions now rule the allocation out, and the class javadoc says out loud that the end total
  cannot.
- **Attempt 1 point 3 ("two-thirds of a year").** Gone; the file now says "a little over half a
  year" and "over six months", which is right for 200 days.
- **Attempt 1 point 4 (the unguarded DEBUG line).** Guarded, and I checked it at both levels on
  separate throwaway databases. At DEBUG the line renders, including across two deposits:

      withdrawal drew the oldest deposits down first savingsAccountId=3 withdrawalId=6
        drawnDown=[depositId=4 landedAt=2027-03-28T08:25:42.635Z took=500.00 leftInIt=0.00]
                  [depositId=7 landedAt=2028-04-11T08:27:49.329Z took=50.00 leftInIt=50.00]

  At INFO on a second instance, after the same two-deposit withdrawal,
  `grep -c "drew the oldest deposits down first"` is **0** while `withdrawal accepted` is still
  there and the not-enough-money refusal still WARNs with its reason. Nothing is rendered for a log
  nobody is reading.
- **The non-blocking midnight note.** `paidInOn` now comes off `stays.depositedAt()` in
  `SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN`, which is the same zone `PointsExpiry:75` derives the
  reported expiry date in, so the two cannot straddle a Brussels midnight. Referencing that constant
  from a test is established here (`PointsExpiryTest:35`, `LoyaltyAnniversaryTest:35`, and four
  `weeklysavings` and `clock` classes).

### What I ran, so you can reproduce it

Checks, both clean on the committed tree: `cd backend && ./mvnw test` → `Tests run: 209, Failures: 0,
Errors: 0`, `BUILD SUCCESS`; `cd frontend && npm run typecheck` → clean, no output.

Application on a throwaway database with `io.dataroots.savingstreak` at DEBUG, driven with curl.
Seeded Anke: current account 1, savings 1 and 2. Bram: current account 2, savings 3. Day numbers are
`POST /api/dev/clock/advance`; the sweeps are `POST /api/dev/jobs/{payLoyaltyBonuses,expireOldPoints}/run`.

| day | what I did | what came back |
|---|---|---|
| 0 | €500 → S1, €500 → S2 (Anke); €100 → S3 (Bram) | Anke 1000 pts, Bram 100 pts |
| 30 | withdraw €500 from S2 | S2 €0, Anke still 1000 — nothing clawed back |
| 200 | €500 → S3 (Bram) | Bram 600 pts, S3 €600 |
| 210 | withdraw €100 from S3 | allocated to `depositId=3`, the **oldest**; S3 €500 |
| 379 | sweep | Anke 1000 → **1050** (S1 only); Bram **600, unchanged** |
| 379 | withdraw €500 from S1; sweep again | Anke **1050** both times — no clawback, no second payment |
| 379 | `expireOldPoints` | Anke **50**, `expiringNext=50 on=2028-09-09` |
| 379 | fresh €500 → S1, €500 → S2 | Anke 1050 |
| 409 | withdraw €250 from S1, €491 from S2 | S1 €250, S2 €9, Anke still 1050 |
| 580 | sweep | this sweep paid Bram **50** — a tenth of the whole 500 the untouched newer deposit holds |
| 580 | €100 → S3, then withdraw €550 | drained `depositId=4` in full, then €50 of `depositId=7`; the paid 50 untouched |
| 758 | sweep | Anke 1050 → **1075**: +25 on the €250, nothing on the €9, nothing on either emptied deposit's second anniversary |

Criterion 4's discriminating fact is the pair of sweeps, not the end total: **600 at day 379** (had
the €100 come out of the newer deposit, `depositId=3` would still hold it and would have paid 10) and
**50 paid by the day-580 sweep** (under newest-first the newer deposit would hold 400 and pay 40).

Log lines behind the numbers, all from `io.dataroots.savingstreak` in the backend log:

    withdrawal drew the oldest deposits down first savingsAccountId=3 withdrawalId=2
      drawnDown=[depositId=3 landedAt=2026-09-09T08:25:32.678Z took=100.00 leftInIt=0.00]

    loyalty bonus paid depositId=1 customerId=1 anniversary=2027-09-09T08:25:32.624Z ordinal=1
      remainingAmount=500.00 wholeEuros=500 rate=0.10 points=50 recordId=1

    loyalty bonus paid depositId=4 customerId=2 anniversary=2028-03-28T08:25:42.635Z ordinal=1
      remainingAmount=500.00 wholeEuros=500 rate=0.10 points=50 recordId=2

    loyalty bonus paid depositId=5 customerId=1 anniversary=2028-09-23T08:26:42.755Z ordinal=1
      remainingAmount=250.00 wholeEuros=250 rate=0.10 points=25 recordId=3

    deposit passed over for a loyalty bonus depositId=6 customerId=1
      reason=a tenth of what it still holds rounds down to no points
      anniversary=2028-09-23T08:26:42.807Z ordinal=1 remainingAmount=9.00 wholeEuros=9
      theLeastABonusIsPaidOn=10

Refusals, all WARN with their reason and none of them leaving a `drawnDown` line behind: more than
the account holds ("There is not enough in that savings account to move EUR 9999.00. It holds EUR
250.00."), zero, negative, another customer's current account, no such savings account, a missing
amount, and "10,00". Zero ` ERROR ` lines and zero stack traces in the whole run.

Page, for completeness — this ticket changes nothing on it. Signed in as Anke in Chromium with
Playwright and read the screenshots. The overview and the savings-account-1 history both render
fully styled, showing € 250,00 / € 9,00, "1.075 points to spend" and "50 points expire on 9
september 2028", and deposit totals of 525 and 550 that include the loyalty bonuses. Zero
`pageerror` and zero console errors; the `ERR_ABORTED` request-failures are React StrictMode's
double-mount and appear identically in the earlier reviews. The deposit rows still read "500 base +
0 bonus at 1,00×" beside those totals — that is ticket 04's and 05's job, noted only so the next
reader does not think it is new.

## Review feedback - attempt 3

All three defects attempt 2 named are genuinely fixed, and I checked each replacement claim by
mutation rather than reading it — the figures the new prose names are the figures the tests really
produce. The behaviour is right and I drove all seven criteria over HTTP against a running
application on a throwaway database. **Every checkbox above stays ticked**: none of them is
unproven, and nothing a customer sees is wrong. No production code needs to change.

It is going back for two statements that are still not true, one of them in a sentence attempt 3
touched the line above. This is the third round on the same defect class, and I am applying the
standard attempt 2 set out when it rejected the "attempt 1 missed it" defence: this is
a repository whose tests carry their reasoning in prose and are reviewed on whether they explain
themselves, so an untrue sentence in the new tests goes back even when the edit is one clause.
Both fixes together are two clauses and no assertion moves.

### 1. `EmptyingADepositForfeitsOnlyTheAnniversaryItDidNotReachApiTest:125` — "the day after being paid"

The comment reads:

    // And now the second one is emptied too, the day after being paid. This is the half of the
    // rule that says a bonus already paid is the customer's: the money going does not unmake the
    // year it stayed for.

No day passes there. The only `app.daysPass(...)` calls in the whole method are at lines 105, 115
and 172; between the sweep at line 117 and this withdrawal at line 128 the clock does not move at
all. `grep -n "daysPass\|runJob\|app.withdraw"` over the file is the whole proof:

    105:        app.daysPass(DAYS_WELL_INSIDE_THE_YEAR);
    106:        app.withdraw(emptiedEarly, ANKE, "500.00");
    115:        app.daysPass(DAYS_WELL_PAST_A_YEAR - DAYS_WELL_INSIDE_THE_YEAR);
    117:        app.runJob(THE_LOYALTY_SWEEP);
    128:        app.withdraw(leftAlone, ANKE, "500.00");

I confirmed it against the running application by driving the same sequence: the sweep ran at
`2027-09-23T08:52:37.366Z` and the withdrawal landed at `2027-09-23T08:52:37.800Z` — 434
milliseconds later, the same second of the same day.

Two things make this worth a round rather than a shrug. First, the file contradicts itself: the
class javadoc at line 27 already says the right thing — "is emptied **the moment afterwards**".
Second, that ordering is the single most load-bearing fact in the method. Sweep-then-withdraw is
why the bonus survives; withdraw-then-sweep would forfeit it outright, because
`LoyaltyService` works the anniversary out from what the deposit holds when the sweep judges it and
`DepositRepository:108` excludes a deposit at zero from the query altogether (see point 3 below,
which is the same mechanism). A reader told a day passed will believe this test covers a window it
never enters.

Fix: say "the moment after being paid", matching the javadoc — or, if a day is wanted there, add the
`daysPass` and re-derive the figures. The sentence's substance ("a bonus already paid is the
customer's") is correct and should stay.

### 2. `WithdrawalsService:84` — "five values and two amounts"

    // work — five values and two amounts formatted per deposit the withdrawal reaches —

The rendered entry at lines 89-91 interpolates **four** values, two of which are the amounts:

    "[depositId=" + deposit.getId() + " landedAt=" + deposit.getDepositedAt()
        + " took=" + asMoney(taken) + " leftInIt=" + asMoney(deposit.getRemainingAmount()) + "]"

`depositId`, `landedAt`, `took`, `leftInIt`. In fairness the number came from attempt 1's own review
prose ("one five-value string concatenation"), so it is inherited rather than invented — but it is
now a statement in production code that miscounts the code beside it. Say "four values, two of them
amounts", or drop the count and keep the reason, which is sound and was worth making.

### 3. Not blocking, and not a reason to widen this ticket — but write it down

The ticket says: "If it turns out something is needed, that is this ticket's finding rather than a
reason to widen it." Here is the finding, offered under that sentence.

**An anniversary that has fallen but not yet been swept is forfeited by a withdrawal**, even though
the money did serve the full twelve months. The anniversary is judged when the sweep runs, not when
it falls: `LoyaltyService` reads the deposit's remaining amount at sweep time, and
`DepositRepository:108` (`where deposit.remainingAmount > 0 and deposit.depositedAt < :until`) drops
a deposit at zero out of the query before the rule is ever applied. So a customer whose €1,000
deposit turns one year old at 09:00 and who moves it all out at 12:00 that day is paid nothing by
the 03:30 sweep the next morning — 100 points lost on money that stayed the whole year.

Normally that window is a few hours. It is the whole outage for any stretch the nightly job does not
run, and it is unbounded for the case `ADepositMadeBeforeTheSchemeExistedIsPaidEveryAnniversaryAtOnceApiTest`
exists for: a deposit carrying several unpaid past anniversaries loses **all** of them to one
withdrawal.

This is not a failed criterion here. Criteria 2 and 5 are both worded around a bonus that "was
already paid" / "has been paid", and the spec's Implementation Decisions already choose this
reading out loud — "a tenth of the whole euros **still in that deposit at the moment the anniversary
is judged**". So it is a recorded trade-off, not a bug, and it should not be fixed in this ticket.
But nothing in this branch would fail if the window widened, and the ticket's own title — "forfeits
only the anniversary it did not reach" — reads as though it could not happen. Worth one sentence in
the spec or the ticket saying the anniversary is judged when swept, and worth a decision by whoever
owns the spec about whether the sweep should judge on what the deposit held at the anniversary
instead. It is the most valuable thing this round turned up; do not lose it.

### 4. Also not blocking

`WithdrawalsService:72` — `drawnDown` has no bound. One rendered entry per deposit the withdrawal
touches, all joined into a single DEBUG line. Draining an account built from years of weekly
deposits emits one multi-kilobyte line, and the comment at :82 justifies the single line by "a
withdrawal spread over a long list of deposits", which is exactly the case where one line reads
worst. The repo's precedent for per-item detail (`PointsService:279`) writes a line per row. Fine as
it stands for a training application; noted only so the next reader has seen the trade-off named.

### What I confirmed is fixed, so nobody redoes it

I mutation-tested every claim attempt 3 rewrote. All mutations were reverted; the tree is clean.

- **Attempt 2 point 1** (the "reconsider an anniversary it has already paid" mechanism). Fixed, and
  the new mechanism is what the application really does. Driving the same sequence, the sweep run
  straight after the withdrawal logged:

      loyalty bonuses paid asAt=2027-09-23T08:52:38.026770Z landedBefore=2026-09-25T08:52:38.026770Z
        depositsConsidered=0 anniversariesPaid=0 points=0

  `depositsConsidered=0` — the deposits are outside the query, exactly as the comment now says, and
  there is no `reason=this anniversary has already been paid` line anywhere in that sweep. The
  pointer the comment adds is also accurate: `AnAnniversaryPaysATenthOfTheDepositsEurosApiTest` runs
  the sweep three times (lines 72, 81, 90) with no withdrawal in it, so the money is still in the
  deposit and the already-paid guard really is exercised there.
- **Attempt 2 point 2** (the "would show up as 100" javadoc). Fixed, and both halves of the
  replacement are true. Combining the two faults it names — dropping `deposit.remainingAmount > 0`
  from `DepositRepository:108` **and** `getRemainingAmount()` → `getAmount()` in
  `DepositsService.depositsStillHoldingMoneyThatLandedBefore` — fails the class with
  `expected: 1050L but was: 1100L`, the figure the javadoc now names. And the honest admission it
  adds is correct: dropping the `remainingAmount > 0` filter **alone** leaves all three of this
  ticket's classes green (`Tests run: 3, Failures: 0, Errors: 0`, `BUILD SUCCESS`), because a tenth
  of nothing is nothing.
- **Attempt 2 point 3** (the "75" javadoc). Fixed. `getRemainingAmount()` → `getAmount()` alone
  fails `PartlyDrawingADepositDownPaysOnWhatIsLeftInItApiTest` with `expected: 1025L but was:
  1100L` — precisely the pair of figures the new sentence names.
- **Attempt 1 point 2** (the false newest-first claim). Both assertions in
  `AWithdrawalComesOutOfTheOldestDepositApiTest` really do discriminate, and each names the right
  number. Allocating newest-first (`new ArrayList<>(...)` + `Collections.reverse(oldestFirst)` in
  `WithdrawalsService:50`) fails line 118 with `expected: 600L but was: 610L` — the "would have paid
  10" its description promises. Setting that first expectation to 610 so execution reaches the
  second fails line 134 with `expected: 50L but was: 40L`, under the description that names 40.
- **Attempt 1 point 4** (the unguarded DEBUG line). Guarded, and I checked it at both levels. At
  DEBUG it renders across two deposits, oldest drained first:

      withdrawal drew the oldest deposits down first savingsAccountId=3 withdrawalId=6
        drawnDown=[depositId=4 landedAt=2027-03-28T08:52:36.892Z took=500.00 leftInIt=0.00]
                  [depositId=7 landedAt=2028-10-06T08:53:52.798Z took=50.00 leftInIt=50.00]

  On a second instance started at INFO on its own throwaway database, after the same two-deposit
  withdrawal, `grep -c "drew the oldest deposits down first"` is **0** and `grep -c "withdrawal
  allocated"` is **0**, while `withdrawal accepted` is still there and the not-enough-money refusal
  still WARNs with its reason. Nothing is rendered for a log nobody is reading.

### What I ran, so you can reproduce it

Checks, both clean on the committed tree, matching `…checks.3.log`: `cd backend && ./mvnw test` →
`Tests run: 209, Failures: 0, Errors: 0, Skipped: 0`, `BUILD SUCCESS`; `cd frontend && npm run
typecheck` → clean, exit 0.

Application on a throwaway database with `io.dataroots.savingstreak` at DEBUG, driven with curl.
Seeded Anke: current account 1, savings 1 and 2. Bram: current account 2, savings 3. Day numbers are
`POST /api/dev/clock/advance`; the sweeps are `POST /api/dev/jobs/{payLoyaltyBonuses,expireOldPoints}/run`.

| day | what I did | what came back |
|---|---|---|
| 0 | €500 → S1, €500 → S2 (Anke); €100 → S3 (Bram) | Anke 1000, Bram 100 |
| 30 | withdraw €500 from S2 | `allocations=[{depositId:2}]`, S2 €0, Anke still 1000 |
| 200 | €500 → S3 (Bram) | Bram 600, S3 €600 |
| 210 | withdraw €100 from S3 | `allocations=[{depositId:3}]` — the **oldest**; S3 €500 |
| 379 | sweep | Anke 1000 → **1050** (S1 only); Bram **600, unchanged** |
| 379 | withdraw €500 from S1 | Anke still **1050** — nothing clawed back |
| 379 | sweep again | Anke **1050**; `depositsConsidered=0` |
| 379 | `expireOldPoints` | Anke **50**, `expiringNext=50 on=2028-09-09` |
| 379 | fresh €500 → S1, €500 → S2 | Anke 1050 |
| 409 | withdraw €250 from S1, €491 from S2 | S1 €250, S2 €9, Anke still 1050 |
| 580 | sweep | Bram +**50** at `remainingAmount=500.00` — the untouched newer deposit paid in full |
| 758 | sweep | Anke 1050 → **1075**; `depositsConsidered=3 anniversariesPaid=1 points=25` |

Criterion 4's discriminating facts are the two sweeps, not the end total: **600 at day 379** (had the
€100 come out of the newer deposit, `depositId=3` would still hold it and would have paid 10) and
**50 paid by the day-580 sweep** (under newest-first the newer deposit would hold 400 and pay 40).

Log lines behind the numbers, all from `io.dataroots.savingstreak`:

    withdrawal drew the oldest deposits down first savingsAccountId=3 withdrawalId=2
      drawnDown=[depositId=3 landedAt=2026-09-09T08:52:36.140Z took=100.00 leftInIt=0.00]

    loyalty bonus paid depositId=1 customerId=1 anniversary=2027-09-09T08:52:36.098Z ordinal=1
      remainingAmount=500.00 wholeEuros=500 rate=0.10 points=50 recordId=1

    loyalty bonus paid depositId=4 customerId=2 anniversary=2028-03-28T08:52:36.892Z ordinal=1
      remainingAmount=500.00 wholeEuros=500 rate=0.10 points=50 recordId=2

    loyalty bonus paid depositId=5 customerId=1 anniversary=2028-09-23T08:52:38.479Z ordinal=1
      remainingAmount=250.00 wholeEuros=250 rate=0.10 points=25 recordId=3

    deposit passed over for a loyalty bonus depositId=6 customerId=1
      reason=a tenth of what it still holds rounds down to no points
      anniversary=2028-09-23T08:52:38.492Z ordinal=1 remainingAmount=9.00 wholeEuros=9
      theLeastABonusIsPaidOn=10

    deposit passed over for a loyalty bonus depositId=4 customerId=2
      reason=this anniversary has already been paid anniversary=2028-03-28T08:52:36.892Z ordinal=1

Criterion 7, checked in the SQL rather than inferred: across six withdrawals — two of them emptying
a deposit that had already been paid — the log holds **3** `insert into loyalty_bonus_paid`, **0**
`update loyalty_bonus_paid` and **0** `delete from loyalty_bonus_paid`. Nothing rewrites the record.
The euros it was worked out from are in the insert
(`whole_euros_it_was_worked_out_from`) but are served by no endpoint, which is what the reworded
criterion says and what ticket 04 carries.

Refusals, all WARN with their reason and none leaving a `drawnDown` line behind: more than the
account holds ("There is not enough in that savings account to move EUR 9999.00. It holds EUR
250.00."), zero, negative, another customer's current account, no such savings account (404), a
missing amount, and "10,00". Zero ` ERROR ` lines and zero stack traces in the whole session.

Page, for completeness — this ticket changes nothing on it. Signed in as Anke in Chromium with
Playwright, console/pageerror/requestfailed subscribed before navigating
(`…review.3.browser.log`). Overview and the savings-account-1 history both render fully styled — I
read the screenshots — showing € 250,00 / € 9,00, "1.075 points to spend", "50 points expire on 9
september 2028", and deposit totals of 525 and 550 that include the loyalty bonuses. **0** pageerror,
**0** console errors, **0** console warnings; the 12 `requestfailed` entries are all `ERR_ABORTED`
from React StrictMode's double-mount and appear identically in the earlier reviews. Two things that
look wrong and are not: the deposit rows still read "500 base + 0 bonus at 1,00×" beside those
totals (ticket 04's and 05's job), and a screenshot taken under ~900 ms catches "Saved" mid-tween at
€ 249,97 — `Rising` in `App.tsx:1867` animates the figure, and it settles to € 250,00, which is what
`/api/savings-accounts/1` returns.
