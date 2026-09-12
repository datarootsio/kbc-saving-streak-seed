# Loyalty-rate bonus: a recurring 10% for money that stays put

Status: ready-for-agent

## Problem Statement

A customer is paid for moving money into savings and never paid for leaving it there. The points
land the moment the deposit does — one per whole euro, more if the week was part of a streak — and
from that moment the deposit is worth nothing further no matter how long it sits. Money paid in last
January and untouched since has earned exactly what money paid in yesterday earned.

So the application rewards the act of saving and is indifferent to the result of it. A customer who
pays in €500 and takes it back out a fortnight later is treated identically to one who pays in €500
and leaves it alone for three years, and the second customer is the one the bank actually wants.
Worse, the two rewards the application does pay both run out: base points and streak bonus each
expire twelve months after they were earned, so a long-standing saver who never claims anything
watches their pot drain while their money sits there doing what the bank asked of it.

There is also nothing in the application that a customer is part-way towards over a horizon longer
than a week. The streak asks something of them every seven days, which is the right question for a
habit and the wrong one for a balance: nothing currently says "this money is worth more to you in
March than it is today".

## Solution

Money that stays put earns again, every year, for as long as it stays.

Each deposit runs a clock of its own from the day it landed. On every twelve-month anniversary that
the money is still in the account, the deposit pays a **loyalty bonus** of 10% of its base points —
the whole euros still sitting in that deposit, one point per euro, a tenth of that, rounded down. A
€500 deposit left alone pays 50 points on its first anniversary, 50 more on its second, and 50 more
on its third. The clock is recurring and never resets: the anniversaries are counted from the day
the deposit landed, so the tenth is exactly nine years after the first.

Taking the money out forfeits the bonus, and only the bonus that had not been paid yet. Bonuses
already paid on earlier anniversaries are the customer's and are never clawed back. What a deposit
pays on an anniversary is worked out from what is still in it at that moment, so a deposit drawn
down to nothing pays nothing — that is the forfeit — and a deposit drawn halfway down pays half.
Withdrawals already come out of the oldest deposit first, so a withdrawal that only partly covers
the savings takes the oldest, most-nearly-vested money first and leaves the newer deposits' clocks
running intact.

The bonus is an ordinary batch of points once paid: spendable on any reward, spent oldest-first
alongside everything else, and subject to the same twelve-month expiry as every other batch. It is
shown on the deposit that earned it, next to that deposit's base points and streak bonus, together
with the date of its next anniversary and what that anniversary is currently worth — so a customer
can see what leaving the money alone is going to pay them, and what taking it out would cost.

The sweep that pays these runs nightly on the application's own clock, and can be run out of turn
through the development jobs endpoint, so twelve months of loyalty can be demonstrated in an
afternoon.

## User Stories

1. As a customer, I want each whole euro I leave in savings for a year to earn me a further tenth of a point, so that leaving my money alone is worth something.
2. As a customer, I want the bonus to be 10% of what the deposit's euros earned, so that the figure is one I can work out in my head.
3. As a customer, I want every deposit to run its own twelve-month clock from the day it landed, so that each deposit is rewarded for its own age rather than for the account's.
4. As a customer, I want a deposit to pay again on every anniversary it survives, so that the second and third years are worth as much as the first.
5. As a customer, I want the anniversaries counted from the day I paid in, so that the clock never drifts and my third anniversary falls on the day I expect.
6. As a customer, I want the anniversary of a deposit made on 29 February to fall on 28 February in the years that have no 29th, so that the date is the one a calendar would give me.
7. As a customer, I want my bonus paid without my having to ask for it, so that money left alone earns without any action from me.
8. As a customer, I want the bonus paid within a day of the anniversary, so that what I am owed shows up promptly.
9. As a customer, I want bonuses I have already been paid to be mine for good, so that a later withdrawal cannot take back points I have already earned.
10. As a customer, I want to forfeit only the anniversary I did not reach, so that taking money out costs me the coming year and not the years I have already served.
11. As a customer, I want a deposit I have emptied to earn no further bonus, so that the scheme pays for money that is actually there.
12. As a customer, I want a deposit I have partly drawn down to still earn on what is left in it, so that spending some of my savings does not cost me the whole of the year's bonus.
13. As a customer, I want a withdrawal to come out of my oldest deposit first, so that my newer deposits keep their clocks running.
14. As a customer, I want to see how much loyalty bonus each deposit has earned me so far, so that I can tell which of my savings are paying me.
15. As a customer, I want to see the date of each deposit's next anniversary, so that I know when it next pays.
16. As a customer, I want to see what each deposit's next anniversary is currently worth, so that I know what I would be giving up by withdrawing.
17. As a customer, I want a deposit's next anniversary figure to fall when I withdraw from it, so that the cost of a withdrawal is visible after I make one.
18. As a customer, I want a deposit I have emptied to show no next anniversary at all, so that I am not shown a promise on money that has gone.
19. As a customer, I want a deposit's loyalty bonus shown next to its base points and its streak bonus, so that I can see the whole of what one deposit has earned me and why.
20. As a customer, I want the total a deposit has earned to include its loyalty bonuses, so that one figure still answers "what has this deposit been worth to me".
21. As a customer, I want loyalty bonus points to be spendable on any reward, so that they are worth the same as every other point I hold.
22. As a customer, I want loyalty bonus points spent in the same oldest-first order as the rest, so that spending works the one way I already understand.
23. As a customer, I want loyalty bonus points to count towards my points balance the moment they are paid, so that the balance is the whole of what I have.
24. As a customer, I want loyalty bonus points to last twelve months from the anniversary that paid them, so that they follow the same rule as every other point.
25. As a customer, I want loyalty bonus points to appear in what I am told expires next, so that the warning covers everything I stand to lose.
26. As a customer whose base points from a deposit have already expired, I want that deposit to keep paying its anniversaries, so that my money is rewarded for staying even after its original points have run out.
27. As a customer, I want a deposit holding under €10 to be plainly worth no bonus rather than mysteriously worth none, so that rounding down is a rule I can see rather than a bug I suspect.
28. As a customer, I want the loyalty bonus worked out from the euros I put in rather than from my streak rate, so that the 10% means the same thing whatever week I paid in.
29. As a customer with several deposits, I want each one to pay on its own anniversary, so that a year of saving pays out across the year rather than on one day.
30. As a customer who paid in before this scheme existed, I want the anniversaries my money has already served to be paid, so that I am not penalised for having saved early.
31. As a customer, I want a loyalty bonus to leave my money where it is, so that being paid in points never moves my euros.
32. As a customer, I want a loyalty bonus not to count as a week's saving, so that my streak still measures money I actually paid in.
33. As a customer, I want a loyalty bonus not to appear in the ledger of money that moved, so that the ledger stays a record of euros.
34. As a customer, I want a new deposit to tell me the date of its first anniversary, so that I know what I have just started.
35. As a trainer, I want to wind the clock forward a year and run the loyalty sweep by hand, so that I can demonstrate a twelve-month reward in an afternoon.
36. As a trainer, I want to wind the clock forward three years and see three anniversaries paid at once, so that the recurring clock is demonstrable rather than merely described.
37. As a trainer, I want to run the sweep twice and see nothing paid the second time, so that I can trust the job is safe to run whenever I like.
38. As a reviewer, I want each anniversary payment logged with the deposit, the anniversary, what remained in it and what that paid, so that I can check the arithmetic by hand.
39. As a reviewer, I want a deposit the sweep declined to pay logged with the reason it was passed over, so that a sweep that pays nothing can be told from a sweep that saw nothing.
40. As an operator, I want the sweep to take its moment from the application's clock, so that a demonstration on a wound-forward clock behaves as the real thing would.

## Implementation Decisions

### A module of its own

A new **Loyalty** module owns this rule end to end: the recurring twelve-month clock, the 10%, the
record of what has been paid, and the nightly job that pays it. Neither Deposits nor Points is the
right home — Deposits has no opinion about points and Points cannot see deposits — and this rule
needs both plus a clock. Loyalty orchestrates them the way Deposits already orchestrates Streaks
and Points when a deposit lands.

The 10% lives in one named constant inside the module, as `€50` and `1.50×` each do. The anniversary
arithmetic lives in one place beside it, and is the only thing that answers "when is this deposit's
*n*-th anniversary".

### The rule

- A deposit's *n*-th anniversary is twelve months multiplied by *n*, added once to the moment it
  landed, in the zone the application already counts calendar things in. Counted from the original
  deposit date every time rather than by adding twelve months repeatedly, so that February clamping
  cannot compound across years.
- What an anniversary pays is a tenth of the whole euros **still in that deposit at the moment the
  anniversary is judged**, rounded down. With nothing withdrawn this is exactly a tenth of the
  deposit's base accrual. A deposit emptied before the anniversary pays nothing, which is how the
  forfeit is expressed; a deposit half drawn down pays half. This is the one place the spec's
  "stays untouched" is read as "how much of it stayed", and it is deliberate: the strict reading
  would let a €1 withdrawal destroy a 100-point bonus on a €1,000 deposit and would turn
  oldest-deposit-first allocation into a trap rather than a protection.
- Rounding is floor, matching the ledger's existing "points stay whole, rounded down" rule. A
  deposit holding under €10 earns nothing on its anniversary, in the same way €0.99 has always
  earned no base point.
- The streak multiplier does not enter the calculation. The bonus is a tenth of the euros' base
  points, never a tenth of euros times rate, so it never compounds with a streak.
- Expiry of the deposit's earlier points is irrelevant to it. Expiry is a rule about points; this is
  a reward for money staying put, so a deposit whose base points expired at twelve months still
  pays on its second, third and tenth anniversaries.

### What is written down

Loyalty keeps its own record: one row per anniversary paid, carrying the deposit, which anniversary
it was (the ordinal year), the moment that anniversary fell, the whole euros it was computed on, and
the points credited. A unique constraint on the deposit and the ordinal makes the sweep idempotent
by construction, exactly as an expired batch's recorded moment makes the expiry sweep idempotent.
The row is also the audit trail: it is the only place the euros behind a past bonus survive, since
the deposit's remaining amount moves on afterwards.

Deriving the same fact from the points ledger was rejected: it would require Points to say out loud
which anniversaries it has seen, which is precisely the kind of question it refuses to answer. A
counter on the deposit was rejected because it discards the audit trail.

### Points

`PointsReason` gains `LOYALTY_BONUS`, and it joins the set of reasons a deposit can have earned
under, so every existing per-deposit breakdown carries it with no change to any caller. Points
gains one way in: credit a stated number of points to a customer against a deposit under this
reason, earned at a stated moment. The bonus is credited as an ordinary dated batch — spendable,
spent oldest-first, expiring twelve months after the anniversary that paid it, and included in what
expires next — with no special case anywhere in the ledger.

Each batch is dated at **its own anniversary**, not at the moment the sweep ran. A batch for an
anniversary long past may therefore be credited already beyond its own twelve months and be swept
away by the expiry job the same night. That is the honest outcome of both rules holding at once, and
it is visible in the log.

### Deposits

Deposits gains one public read: the deposits that still hold money and landed before a given moment,
as a record carrying the deposit, the customer, what remains in it, and when it landed. Deposits
answers the fact; Loyalty owns the rule. `DepositLanded` is the wrong shape for this (no customer,
no remaining amount), so this is a sibling record rather than a change to it.

Nothing about withdrawal changes. Withdrawals already draw the oldest deposit down first and already
record what came out of which deposit; the forfeit is what falls out of a deposit having fewer euros
in it when its anniversary arrives.

### The sweep

A nightly scheduled job in the Loyalty module, named for what it does so it can be typed into the
development jobs endpoint, taking its moment from the injected clock rather than the machine's. It
runs after the points-expiry sweep, so a night that both pays an anniversary and expires an old
batch does them in the order a customer would describe. One transaction for the sweep, as the expiry
sweep has.

The sweep pays **every** anniversary a deposit has passed and not yet been paid for, each as its own
batch dated at its own anniversary. This is what makes the recurring clock demonstrable: winding the
clock three years forward and running the job must produce three bonuses, not one. It is also what
covers deposits made before the scheme existed.

The sweep asks the database for deposits that still hold money and landed at least twelve months
before the moment it is judging, then applies the rule to each. Deposits holding nothing are outside
the query: money cannot come back into a deposit, so a deposit at zero will never pay again.

### API contract

`DepositResponse` and the deposit history rows gain three fields:

- the loyalty bonus points that deposit has been paid so far, across all its anniversaries;
- the date of its next anniversary, absent when the deposit holds no money;
- what that next anniversary is worth at what the deposit currently holds.

The existing total a deposit has earned now means base plus streak bonus plus loyalty bonuses paid,
so that one figure still answers "what has this deposit been worth". The three parts sum to it, and
the invariant recorded on the shared test view is updated to say so. A deposit's total therefore
grows on each anniversary — which is what a recurring reward means — while what it earned *when it
landed* is unchanged, so every existing assertion made immediately after a deposit still holds.

Nothing is added to the account overview, and nothing to the ledger of money that moved: no euros
move when a bonus is paid.

### Logging

One INFO line per sweep, carrying the moment it judged against, the cut-off its query used, how many
deposits it considered, how many anniversaries it paid and the total points — so a balance that grew
overnight is explainable from that line alone. One DEBUG line per deposit considered, carrying the
anniversary, the ordinal, what remained, the whole euros and the points, or the reason it was passed
over (already paid, still inside its year, nothing left in it, under €10). WARN on any refusal. The
existing `points credited` INFO line covers the credit itself.

## Testing Decisions

A good test here says what the rule is from outside and would survive the rule being implemented a
different way. It drives the application over HTTP, moves the clock through the endpoint a trainer
would use, triggers the sweep by the name a trainer would type, and asserts on balances, deposit
history rows and refusals. It never reads the database, never names a table or a column, and never
asks the Loyalty module a question the API does not expose — the record of what has been paid is
storage and no test asserts on it directly.

### The seam

One seam, and it already exists: **the HTTP API of a whole application on a throwaway database whose
clock the test may move** (`AnApplicationWithAClockToMove`). It already offers everything this
feature needs — deposit, withdraw, days passing, running a named job, a points balance, what expires
next, and a savings account's deposit history. The only change to the harness is that the shared
deposit view grows the three new fields, alongside the response it mirrors. No new seam is proposed,
and no test reaches below this one.

A fresh application per test class, as the streak and expiry tests already do: these tests wind the
clock years forward, which nothing sharing a clock or a database could survive.

### Prior art

`PointsExpireTwelveMonthsAfterTheyWereEarnedApiTest` is the model — a rule stated in one test, both
halves asserted, and the sweep run twice to show the second run takes nothing. The streak-bonus tests
are the model for a sequence of deposits separated by time passing, and for asserting a per-deposit
breakdown from the history. Refusal tests follow `ClaimIsRefusedApiTest`.

### What gets tested

Named as sentences, in a package of their own:

1. An anniversary pays a tenth of the deposit's euros, and a sweep run inside the twelve months pays nothing.
2. A second and third anniversary each pay again, counted from the day the deposit landed.
3. Emptying a deposit before its anniversary pays nothing, and the bonuses paid on earlier anniversaries stand.
4. Partly drawing a deposit down pays on what is left in it.
5. A withdrawal that only partly covers the savings comes out of the oldest deposit and leaves the newer deposits' anniversaries whole.
6. Running the sweep twice pays nothing the second time.
7. A loyalty bonus is spendable on a reward and is spent alongside the rest, oldest-first.
8. A loyalty bonus expires twelve months after the anniversary that paid it, and shows up in what expires next.
9. A deposit whose base points have already expired still pays its second anniversary.
10. A deposit holding under €10 pays nothing on its anniversary.
11. A deposit made before the scheme existed is paid every anniversary it has already served, at once.
12. The history shows what each deposit has been paid, when it next pays, and what that is worth; an emptied deposit shows no next anniversary.
13. A loyalty bonus does not secure a week, does not change a streak, and does not appear in the ledger of money that moved.

The anniversary arithmetic — twelve months multiplied out, February clamping, the ordinal of a given
date — is also worth a unit test beside the rule, in the shape of the existing `PointsExpiryTest`.
It is the one piece of this whose interesting cases are calendar cases that an API test would need a
decade of clock-winding to reach.

## Out of Scope

- **Telling a customer what a withdrawal just cost them in future bonus.** The history's next-anniversary figure falls after a withdrawal, which is the honest signal; a warning at the moment of withdrawal, or a confirmation step, is a separate feature.
- **Any rate other than 10%, or any tiering by amount or by age.** One rate, one constant.
- **Compounding the loyalty bonus with the streak multiplier**, or paying loyalty on points rather than on euros.
- **Interest on the money itself.** This scheme pays points, never euros.
- **Anything on the account overview.** No new figure, no "your loyalty bonus so far" total across deposits; the bonus is shown on the deposits that earned it.
- **Notifying a customer that an anniversary was paid.** The sweep pays and logs; it does not tell anybody.
- **Rescuing an already-expired batch, or extending expiry for loyal savers.** The bonus is a new batch under the existing expiry rule, not a change to that rule.
- **Re-crediting a bonus if money is paid back in.** A new deposit is a new deposit with a clock of its own; a drawn-down deposit's euros never return to it.
- **Backdating streak multipliers or repricing past deposits.** Nothing already recorded is rewritten.

## Further Notes

- This repo has no `CONTEXT.md` and no `docs/adr/` yet, so nothing here contradicts a recorded decision. Two decisions in this spec are the kind that would earn an ADR if that directory existed: reading a partial withdrawal as a partial forfeit, and letting a deposit keep earning after its own base points have expired.
- The generous reading in the first of those is the single largest judgement call in the spec. The strict reading — any touch forfeits the whole year — is a one-line change to the rule if it turns out to be wanted, and the record of what was paid is shaped so that either reading is auditable after the fact.
- The scheme has an open-ended tail by design: a deposit left alone for ten years is paid ten times, and at 10% a year the points paid on a deposit will exceed its base accrual in the tenth year. That is a business question about the rate rather than a defect in the rule, and the rate is one constant.
- The sweep's query grows with the number of deposits that still hold money, which for a training application is a handful of rows. A real one would want the next anniversary indexed rather than derived per row on every sweep; noted, not built.
