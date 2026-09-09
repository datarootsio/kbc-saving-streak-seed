# 04: A deposit says what it has earned in loyalty and when it next pays

Status: needs-info

**Blocked by:** 01 (an anniversary pays a tenth of the euros a deposit still holds).

**What to build:** A customer looking at a deposit can see what leaving the money alone has paid them
and what it is going to pay them next. Each deposit reports three things it did not before: the
loyalty bonus it has earned so far across all its anniversaries, the date of its next anniversary,
and what that next anniversary is currently worth at what the deposit holds today.

The next-anniversary figure is the point of the feature made visible. It falls when the customer
withdraws from that deposit, so the cost of a withdrawal is legible afterwards; it is nothing at all
for a deposit holding under €10; and a deposit that has been emptied reports no next anniversary,
because there is no promise left to make about money that has gone.

The total a deposit has earned now means its base points plus its streak bonus plus every loyalty
bonus paid on it, so that one figure still answers "what has this deposit been worth to me". The
three parts sum to the total, and the shared test view that records that invariant is updated to say
so. A deposit's total therefore grows on each anniversary, which is what a recurring reward means —
while what a deposit earned *when it landed* is unchanged, so every assertion made immediately after
a deposit still holds.

A newly made deposit reports the same three fields: nothing earned yet, and the date twelve months
out that it has just started counting towards.

Nothing is added to the account overview. There is no total loyalty bonus across deposits, and no
figure on the summary — the bonus is shown on the deposits that earned it, where the next
anniversary means something.

- [x] Each deposit in the history reports the loyalty bonus it has been paid, across every anniversary.
- [x] Each deposit in the history reports the date of its next anniversary and what that anniversary is currently worth.
- [x] A deposit's next-anniversary figure falls after a withdrawal from it.
- [x] A deposit holding nothing reports no next anniversary at all.
- [x] A deposit holding under €10 reports its next anniversary date with nothing to be earned on it.
- [x] The total a deposit has earned is its base points, its streak bonus and its loyalty bonuses, and the three always sum to it.
- [x] A deposit just made reports no loyalty bonus and a first anniversary twelve months out.
- [x] What a deposit earned when it landed is unchanged by this ticket, and the existing assertions about it still pass untouched.
- [x] The account overview gains nothing.

## Review feedback - attempt 1

The feature itself works. I drove all nine criteria against the running application and saw every
one of them, so the boxes above are left ticked and **nothing about the reported fields needs
rebuilding**. Two things in the diff are wrong in a way that has to be fixed before this merges,
and both are small.

### 1. The new `@Transactional(readOnly = true)` on the history GET does nothing, and its javadoc says it does

`SavingsAccountController.depositsInto` (`backend/src/main/java/io/dataroots/savingstreak/web/SavingsAccountController.java:113-114`)
is package-private, and this ticket added `@Transactional(readOnly = true)` above it plus a javadoc
paragraph asserting what that buys:

> In one read transaction … One transaction, and the two halves of every row describe the same
> instant of the ledger.

That guarantee does not hold. `@Transactional` is applied by a proxy, and Spring's
`AnnotationTransactionAttributeSource` is public-methods-only by default, so a non-public handler
resolves no transaction attribute and is never advised. This repository already states the rule
twice, in its own words, and made two methods public for no other reason:

- `loyalty/LoyaltyService.java:81-83` — "*{@code @Transactional} is applied by a proxy and a proxy
  cannot advise a method that is not public, so the annotation would be silently ignored and a
  half-finished sweep would commit.*"
- `points/PointsService.java:226-229` — the same warning again.

So `deposits.depositsInto(...)` and `loyalty.whenTheDepositsInAnAccountNextPay(...)` still each open
their own read, and a withdrawal committing between them still produces exactly the row the comment
promises cannot happen: `loyaltyBonusPoints` from one instant of the ledger beside
`nextAnniversaryPoints` worked out at another.

What to do: make `depositsInto` public (that is the shape `LoyaltyService.payLoyaltyBonuses` and
`PointsService` already use for the same reason) so the annotation takes effect and the paragraph
becomes true. If atomicity is judged not worth a public handler, then delete the annotation *and*
the paragraph rather than leaving a claim the code does not keep — but do not leave it as it is.

Out of scope, worth knowing while you are in there: `savingsAccount` (line 82-84),
`CustomerController.accountsOf` (line 114-116) and `CustomerController.moneyMovementsOf`
(line 144-146) carry the same dead annotation and pre-date this ticket. They assert nothing in
their javadoc, which is why they are not this ticket's problem; a cleanup across all four would be
its own change.

### 2. `theAnniversaryComingNextFor` reports the next anniversary on the calendar, not the next one that will be paid

`loyalty/LoyaltyAnniversary.java` computes `anniversariesPassedBy(landedAt, now) + 1`, and an
anniversary counts as passed the moment it arrives. Its javadoc claims:

> the anniversary reported as coming is exactly the one the sweep would pay next if the money
> stayed where it is

That is not so whenever an anniversary has arrived and the sweep has not run yet. The sweep is
`cron 0 30 3 * * *`, so on the anniversary day itself it runs *before* the anniversary moment for
any deposit made after 03:30. Money that landed 2026-01-15 at 09:30 is therefore, from 09:30 on
2027-01-15 until 03:30 on 2027-01-16, shown with `loyaltyBonusPoints: 0` beside
`nextAnniversaryOn: 2028-01-15` — the anniversary that is about to pay has vanished from the page
for eighteen hours and the total then appears to jump a year late.

Reproduced here without waiting for a cron: with the clock at 2029-09-10 and the 2029-09-09
anniversary of deposit 1 unpaid, `GET /api/savings-accounts/1/deposits` returned

    {"id":1,...,"loyaltyBonusPoints":75,"nextAnniversaryOn":"2030-09-09","nextAnniversaryPoints":25}

and the very next `POST /api/dev/jobs/payLoyaltyBonuses/run` logged
`loyalty bonus paid depositId=1 ... anniversary=2029-09-09T12:01:44.122Z ordinal=3 ... points=25`
— the anniversary the history had just skipped over.

The ticket only asks for "the date of its next anniversary", which the calendar reading does
satisfy, so this is a smaller miss than the first: **either** make the reported ordinal the lowest
one that has not been paid (falling back to `passed + 1`), **or** correct the javadoc to say it is
the next anniversary on the calendar and that a sweep may still owe an earlier one. Note that
`payIfItIsDue` writes no `LoyaltyBonusPaid` row when the bonus rounds to nothing, so a
paid-rows-only rule would pin a €9 deposit's date in the past; the two rules have to be combined if
you take the first option.

### What I ran, so you do not have to re-establish it

`cd backend && ./mvnw test` → 215 tests, 0 failures. `cd frontend && npm run typecheck` → exit 0.
Both new test classes ran and pass.

Driven over HTTP on a throwaway database with the clock moved through `/api/dev/clock/advance` and
the sweep run as `payLoyaltyBonuses`: a €500 deposit reports `loyaltyBonusPoints:0`,
`nextAnniversaryOn:"2027-09-09"`, `nextAnniversaryPoints:50`; +379 days and a sweep gives
`550 / 500 base / 50 loyalty` with the next anniversary `2028-09-09` worth 50; withdrawing €250
drops it to 25 on the same date with the 50 kept; emptying a deposit gives both anniversary fields
`null` together while `loyaltyBonusPoints:50` and `pointsEarned:550` stand; a €9 deposit gives
`2028-09-23` and `0`; two further sweeps take loyalty to 75 then 100 with `basePoints` never
moving. Four €100 deposits a week apart for Bram raised a multiplier, and after a year and a sweep
the rows read `base 100 streak 30 loyalty 10 sum 140 total 140`, `100/20/10 → 130`,
`100/10/10 → 120`, `100/0/10 → 110`: the three parts sum with all three non-zero, and the bonus is
a tenth of the euros rather than of euros × rate. A deposit made on 29 February 2032 reports
`2033-02-28`. `GET /api/savings-accounts/1` carries no loyalty and no anniversary field.
`GET /api/savings-accounts/999/deposits` → 404.

Logging is good and needs nothing. Every state above left a line from `io.dataroots.savingstreak`:

    i.d.s.deposits.DepositsService : deposits in an account that still hold money savingsAccountId=3 deposits=4
    i.d.savingstreak.loyalty.LoyaltyService : when each deposit in an account next pays savingsAccountId=3 asAt=2030-10-15T12:06:04.922115Z depositsStillHoldingMoney=4 worthNothingOnTheirNextAnniversary=0 nextAnniversariesWorthAltogether=40
    i.d.s.deposits.DepositsService : deposit history reported with what each deposit earned savingsAccountId=1 deposits=1 atTheRateTheyWerePaidAt=1 atTheOrdinaryRateForLackOfOne=0 paidALoyaltyBonus=1 loyaltyBonusPoints=100

An emptied account reads `depositsStillHoldingMoney=0` and the €9 deposit
`worthNothingOnTheirNextAnniversary=1`, so both readings of "nothing" are distinguishable from the
log alone.

### Two more things, neither of them yours to fix here

- `POST /api/savings-accounts/{id}/deposits` reads the whole account's anniversary map and picks one
  row out of it (`SavingsAccountController.java:147`), after the deposit's own transaction has
  committed. If a withdrawal emptied that deposit in between, `get(made.id())` is null and the 201
  reports both anniversary fields as null, against the comment's "It holds all of its money, so it
  is always in this answer". A race no trainer will hit; noted because the comment is absolute.
- `/api/customers/{id}/money-movements` per-movement `pointsEarned` now grows with loyalty (the
  €500 deposit reads 625 after four anniversaries). No file behind that endpoint is in this diff —
  it follows from ticket 03 adding `LOYALTY_BONUS` to the reasons a deposit earns under. Worth a
  decision by whoever owns the spec, not a change here.
- The page still renders "500 base + 0 bonus at 1,00×" under a total of 600, and
  `frontend/src/api.ts` does not yet carry the three new fields. That is ticket 05's job and is
  expected in this branch.
