# 02: A withdrawal forfeits only the anniversary it did not reach

Status: needs-info

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
- [ ] Withdrawing changes no earlier bonus record: what a past anniversary was worked out from is still readable afterwards.

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
