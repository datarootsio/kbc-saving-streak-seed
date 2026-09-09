# 04: A deposit says what it has earned in loyalty and when it next pays

Status: done

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

## Attempt 2 — what was done about the feedback

Both points are addressed. The reported fields were not rebuilt, as the feedback said they need not
be; the nine criteria above are still driven and still hold.

### 1. The `@Transactional` on the history GET — it was already taking effect

This one turns out not to be a defect, and the modifier is unchanged. The public-methods-only rule
the feedback rests on is no longer Spring's: since Spring Framework 6.0 the attribute source is
built as `AnnotationTransactionAttributeSource(false)` by
`AbstractTransactionManagementConfiguration.transactionAttributeSource()` — `publicMethodsOnly =
false` — because a CGLIB proxy *can* override a package-private method of a class in its own
package. Verified in the bytecode of spring-tx 6.2.19 (`iconst_0` into the one-arg constructor) and
then at runtime: with `depositsInto` package-private exactly as it was,
`org.springframework.orm.jpa` at DEBUG logs

    Creating new transaction with name [io.dataroots.savingstreak.web.SavingsAccountController.depositsInto]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly

and both halves of the row then report `Participating in existing transaction` —
`DepositsService.depositsInto` and `LoyaltyService.whenTheDepositsInAnAccountNextPay` alike. The
javadoc paragraph was therefore true as written; a withdrawal cannot commit between the two reads.
The same log shows `savingsAccount`, `CustomerController.accountsOf` and `moneyMovementsOf` are
advised too, so the annotation the feedback calls dead on those three is live as well.

What did need fixing is that a reader had no way to know this, with two comments in the repository
asserting the opposite. The paragraph on `depositsInto` now says the annotation is honoured on a
package-private method, why, and which log line proves it. The comments on
`LoyaltyService.payLoyaltyBonuses` and `PointsService` are the ones that are now wrong, and the two
methods are public for a reason that expired in Spring 6.0 — left alone as out of this ticket's
scope, and worth their own change.

### 2. `theAnniversaryComingNextFor` reported the calendar's next, not the next that pays

Fixed by the first of the two options offered, so the field means what the ticket's title says.

- `LoyaltyAnniversary.theAnniversaryComingNextFor` is renamed
  `theAnniversaryAfterTheOnesThatHaveArrived` and its javadoc no longer claims to be what the sweep
  would pay next. It is the calendar reading and says so.
- `LoyaltyService.whenTheDepositsInAnAccountNextPay` now crosses that with the record of what has
  been paid — the same `whatHasAlreadyBeenPaidFor` set the sweep uses, so the promise and the payment
  are one rule — and reports the earliest anniversary the deposit is owed and has not been paid.
- The two rules are combined as the feedback warned they must be: the unpaid-rows walk runs only when
  the deposit is worth something, so a EUR 9 deposit, whose anniversaries are never written down, is
  promised the calendar's next date rather than being pinned in its first year.

Driven over HTTP on a throwaway database. A EUR 500 deposit a year and a fortnight old with the sweep
not yet run reports `loyaltyBonusPoints:0, nextAnniversaryOn:"2027-09-09",
nextAnniversaryPoints:50` — the anniversary that is about to pay, which used to read `2028-09-09`.
The sweep then moves it to `2028-09-09`. Three years on and unswept it reports its *first*
anniversary, `2027-09-09`, and after the sweep `loyaltyBonusPoints:150` with the next on
`2030-09-09`. The EUR 9 deposit three years on and unswept reports `2030-09-09` worth `0`, not
`2027-09-09`. The window is asserted in
`TheHistorySaysWhatEachDepositHasBeenPaidAndWhenItNextPaysApiTest`, which now reads the history
between `daysPass` and the sweep.

The read's DEBUG line gained `depositsOwedAnAnniversaryTheSweepHasNotPaid=`, so a date in the past on
the page is explainable from the log rather than looking like an off-by-a-year.

## Verified

Reviewed as the whole of `ticket/03-a-loyalty-bonus-is-an-ordinary-batch-of-points..ticket/04-a-deposit-says-what-it-has-earned-in-loyalty-and-when-it-next-pays`
(3 commits, 15 files, +1009/-79, backend only) and driven against a running application on a
throwaway database. Both of attempt 1's findings are settled: one is fixed, and the other was
mistaken. Decision: **done**.

### Attempt 1's first finding was wrong, and this is how to check it

Attempt 1 held that `@Transactional(readOnly = true)` on the package-private
`SavingsAccountController.depositsInto` is silently ignored, on the strength of the
public-methods-only rule that `LoyaltyService.payLoyaltyBonuses` and `PointsService` each state in
a comment. That rule stopped being Spring's in Spring Framework 6.0, and this application runs on
spring-tx 6.2.19. Confirmed two ways, independently of the implementer's own evidence:

    javap -c -p AbstractTransactionManagementConfiguration.class
      # transactionAttributeSource():
      #   0: new AnnotationTransactionAttributeSource
      #   4: iconst_0                       <- publicMethodsOnly = false
      #   5: invokespecial <init>:(Z)V

and at runtime, a second instance started on port 8081 with `org.springframework.orm.jpa` at DEBUG
and `depositsInto` package-private exactly as it stands
(`...04-....review.2.txcheck.backend.log`, one `GET /api/savings-accounts/1/deposits`):

    JpaTransactionManager : Creating new transaction with name
      [io.dataroots.savingstreak.web.SavingsAccountController.depositsInto]:
      PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly
    JpaTransactionManager : Opened new EntityManager [SessionImpl(484458599<open>)] for JPA transaction
    ... Participating in existing transaction        (x6)
    DepositsService  : deposit history reported with what each deposit earned savingsAccountId=1 ...
    DepositsService  : deposits in an account that still hold money savingsAccountId=1 deposits=1
    LoyaltyService   : when each deposit in an account next pays savingsAccountId=1 ...
    JpaTransactionManager : Initiating transaction commit

One transaction encloses both `DepositsService.depositsInto` and
`LoyaltyService.whenTheDepositsInAnAccountNextPay`, on the same `EntityManager`. The javadoc
paragraph the implementer defended is true as written, and the modifier is rightly unchanged. A
reviewer's earlier finding is not privileged, and this one does not survive the source.

### Attempt 1's second finding is fixed, and the fix is regression-tested

Driven over HTTP on the running application, clock moved with `/api/dev/clock/advance` and the
sweep run as `POST /api/dev/jobs/payLoyaltyBonuses/run`:

| what I did | what came back |
|---|---|
| `POST` EUR 500 into savings 1 | `loyaltyBonusPoints:0, nextAnniversaryOn:"2027-09-09", nextAnniversaryPoints:50` |
| +379 days, **sweep not run** | `nextAnniversaryOn:"2027-09-09"` still, worth 50, loyalty 0 — the anniversary that is owed, not 2028's |
| sweep | `loyaltyBonusPoints:50, pointsEarned:550, basePoints:500, next 2028-09-09 / 50` |
| withdraw EUR 250 | next `25` on the **same** date, loyalty 50 and total 550 kept |
| empty savings 2 (EUR 500 out) | `nextAnniversaryOn:null, nextAnniversaryPoints:null` together, `loyaltyBonusPoints:50, pointsEarned:550` |
| `POST` EUR 9 | `nextAnniversaryOn:"2028-09-23", nextAnniversaryPoints:0` |
| +1100 days, **sweep not run** | savings 1: `nextAnniversaryOn:"2028-09-09"` worth 25 — the *earliest* owed and unpaid, three years back, not the calendar's 2031 |
| same read, the EUR 9 deposit | `nextAnniversaryOn:"2031-09-23"` worth 0 — the calendar's next, **not** pinned at its unrecordable 2028-09-23 |
| sweep | `loyaltyBonusPoints:125, pointsEarned:625, next 2031-09-09 / 25` |

The two rules really are combined the way the feedback warned they had to be: the unpaid-rows walk
is gated on the deposit being worth something, so a EUR 9 deposit is not pinned in its first year.
The gate is safe for a reason worth writing down: `remainingAmount` is only ever subtracted from, so
a deposit worth something today was worth something at every anniversary it has passed, and a
missing row can therefore only mean "the sweep has not run".

Every criterion above was exercised, not inferred:

- **three parts sum, all three non-zero** — four EUR 100 deposits a week apart for Bram raised the
  multiplier to 1.30x; a year on and a sweep, the history reads `100/30/10 = 140`, `100/20/10 = 130`,
  `100/10/10 = 120`, `100/0/10 = 110`. The bonus is a tenth of the euros, not of euros x rate.
- **unchanged when it landed** — `basePoints` and `streakBonusPoints` never moved across four sweeps
  while `pointsEarned` went 500 -> 550 -> 625 -> 650.
- **overview gains nothing** — `GET /api/savings-accounts/1` returns no loyalty and no anniversary
  field; the new test asserts it against the raw body rather than a record that would have to be
  changed first to notice.
- **refusal** — `GET /api/savings-accounts/999/deposits` -> 404 `"There is no savings account 999."`
- **February clamping** — clock wound to 2032-02-29, a deposit there reports `2033-02-28`.

### Logging

Every state above left a line from `io.dataroots.savingstreak` at DEBUG in
`...04-....app.2r.backend.log`; no WARN and no ERROR, and `Completed 500` appears zero times. The
line that makes a past date explainable rather than an off-by-a-year is the new counter, and it read
1 exactly when the page showed a date in the past:

    LoyaltyService : when each deposit in an account next pays savingsAccountId=1
      asAt=2027-09-23T12:37:17.953656Z depositsStillHoldingMoney=1
      worthNothingOnTheirNextAnniversary=0 depositsOwedAnAnniversaryTheSweepHasNotPaid=1
      nextAnniversariesWorthAltogether=50

Both readings of "nothing" are distinguishable from the log alone: an emptied account reads
`depositsStillHoldingMoney=0`, the EUR 9 deposit `worthNothingOnTheirNextAnniversary=1`. The history
read's own line carries the other end of it:
`deposit history reported ... paidALoyaltyBonus=4 loyaltyBonusPoints=40`.

### Checks and the page

`cd backend && ./mvnw test` -> 215 tests, 0 failures, BUILD SUCCESS
(`...04-....review.2.checks.log`). `cd frontend && npm run typecheck` -> exit 0. The new
`TheHistorySaysWhatEachDepositHasBeenPaidAndWhenItNextPaysApiTest` runs (1 test) and
`LoyaltyAnniversaryTest` is now 10; no existing assertion was weakened, only the documented
base+streak+loyalty invariant widened.

The page is not in this diff and does not need to be, but it still renders: Playwright, chromium,
signed in as Anke and opened savings account 1
(`...review.2.overview.png`, `...review.2.savings1.png`, console at
`...review.2.browser.log`). Fully styled, EUR 250,00 saved and 1.209 points matching the API
exactly, no `pageerror`; the only `requestfailed` entries are `ERR_ABORTED` duplicates from React's
double-invoked effects, and the same requests succeed. Note for whoever reads a screenshot of this
app: the figures count up on load, so a frame captured immediately after `networkidle` shows
part-way values (I first read "EUR 97,97" and 474 points before the animation settled) — wait for it
before believing a number.

### Not blocking, worth someone's time

- `POST /api/savings-accounts/{id}/deposits` still assembles its 201 from two transactions
  (`SavingsAccountController.java:151-158`): the deposit commits, then the loyalty read opens its
  own. A withdrawal emptying that deposit in between makes `get(made.id())` null and both
  anniversary fields null, against the inline comment's "It holds all of its money, so it is always
  in this answer". Attempt 1 raised this and ruled it out of scope; it stays out of scope, but it is
  now a one-word fix, because a package-private handler *is* advised.
- The javadoc on `NextAnniversaryOfADeposit` and `LoyaltyService.whenTheDepositsInAnAccountNextPay`
  describes the past-dated case as "those few hours" / "a day just gone". On a clock a trainer wound
  forward without sweeping it is years, as the 2028-09-09 reading above shows. Behaviour is right;
  the wording understates it, and ticket 05 needs to render it without a label that reads as a
  contradiction.
- The comments at `LoyaltyService.payLoyaltyBonuses` and `PointsService` that say a proxy cannot
  advise a non-public method are now demonstrably stale, and neither method needs to be public for
  visibility either — both are called from inside their own package. The new javadoc on
  `depositsInto` names them as wrong, which is the honest interim state; a three-site cleanup is its
  own change.
- The new API test does not cover the EUR 9 deposit past its first anniversary, which is the half of
  the combined rule that is easiest to break. I verified it by hand (2031-09-23, not 2028-09-23); an
  assertion would keep it verified.
- `depositsInto`'s 404 leaves no WARN, unlike `withdrawalsFrom` two methods below which does. That
  line pre-dates this ticket (`git show ticket/03:...SavingsAccountController.java` has it
  unchanged), so it is not this branch's omission.
