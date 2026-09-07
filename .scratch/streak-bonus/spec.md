# Streak bonus: a weekly multiplier on the points a deposit earns

Status: ready-for-agent

## Problem Statement

A customer earns one point per whole euro they move into savings, and that is all they earn. The
figure is the same on the first deposit as on the fiftieth, and the same for somebody who pays in
every week as for somebody who paid in once last spring and has not been back. Saving regularly is
the habit the application exists to encourage, and nothing in it currently notices whether a
customer has the habit or not.

There is also nothing on the screen that a customer can be part-way towards. Points accumulate and
are spent on rewards, but there is no state of the account worth protecting — nothing that is lost by
skipping a week, and so no reason not to skip one.

## Solution

Weeks that see real saving build a streak, and a streak pays better.

A calendar week — Monday to Sunday — joins the streak once at least €50 of new savings has landed in
it. Deposits within the week accumulate towards that €50, and the deposit that carries the week over
the line is itself already paid at the higher rate. The first week of a streak pays 1.00×; each
further consecutive week adds 0.10×, up to 1.50× from the sixth week on, where it stays.

Miss a week — pay in nothing, or pay in less than €50 — and the streak is over: the next week that
secures €50 starts a new streak at 1.00×. The longest streak the customer has ever run is remembered
and shown, so that a lapse costs the rate without erasing the record.

Deposits smaller than the weekly minimum are not punished. They earn points at whatever the current
multiplier is; they simply do not secure the week on their own. Withdrawals earn nothing, as they
always have, and are invisible to the streak: they neither break it nor reduce a week's progress
towards €50.

## User Stories

1. As a customer, I want each whole euro I move into savings to earn at least one point, so that the rule I already understand still holds.
2. As a customer, I want to earn more points per euro when I have been saving every week, so that the habit is worth keeping.
3. As a customer, I want a week to count towards my streak once I have paid in €50, so that I know exactly what the week asks of me.
4. As a customer, I want several small deposits in one week to add up towards the €50, so that I do not have to save in one lump to qualify.
5. As a customer, I want the deposit that carries me past €50 to be paid at the new, higher rate, so that reaching the milestone pays off immediately rather than next week.
6. As a customer, I want my first streak week to pay the ordinary rate, so that the bonus is something I build rather than something I am given.
7. As a customer, I want each consecutive qualifying week to add 0.10× to my rate, so that continuing is visibly better than starting over.
8. As a customer, I want the rate to stop climbing at 1.50×, so that the scheme is one I can understand and the bank can afford.
9. As a customer, I want my rate to stay at 1.50× for as long as I keep qualifying, so that a long streak is never worse than a six-week one.
10. As a customer, I want a week I skipped to end my streak, so that the rate means what it says.
11. As a customer, I want a week where I paid in less than €50 to end my streak too, so that the minimum is a real threshold and not a suggestion.
12. As a customer, I want to start a fresh streak at 1.00× after a lapse, so that a missed week is a setback and not a lockout.
13. As a customer, I want my longest-ever streak remembered after a lapse, so that what I achieved is not erased.
14. As a customer, I want a deposit under €50 to still earn points at my current rate, so that saving a little is never worse than saving nothing.
15. As a customer, I want to see the multiplier my next deposit will earn at, so that I can decide whether to pay in now.
16. As a customer, I want to see how much more I need to pay in this week to secure it, so that I know what the week still asks of me.
17. As a customer, I want to see how many consecutive weeks my current streak runs to, so that I know what I stand to lose.
18. As a customer, I want to see my best-ever streak, so that I have a figure to beat.
19. As a customer, I want each deposit in my history to show what it earned as base points and what it earned as streak bonus, so that I can check the multiplier was applied.
20. As a customer, I want each deposit in my history to show the multiplier it was paid at, so that a past deposit explains itself without my having to reconstruct the week it fell in.
21. As a customer, I want the response to a deposit I have just made to tell me what it earned and at what rate, so that I see the effect before leaving the page.
22. As a customer, I want withdrawing money not to break my streak, so that using my savings is not punished.
23. As a customer, I want a withdrawal not to reduce a week's progress towards €50, so that money I have already paid in stays counted.
24. As a customer, I want a withdrawal not to take back a multiplier I was already paid, so that points I have earned are mine.
25. As a customer, I want my week to run Monday to Sunday in my own timezone, so that a Monday-morning deposit is not counted against last week.
26. As a customer, I want a deposit late on Sunday evening to count for the week that is ending, so that the last hours of a week are still usable.
27. As a customer, I want my points balance to be spendable on rewards exactly as before, so that the bonus adds to a scheme I already know rather than replacing it.
28. As a customer with more than one savings account, I want each account to keep its own streak, so that a streak on one is not quietly funded by the other.
29. As a trainer, I want to build a multi-week streak by advancing the development clock, so that the whole scheme can be demonstrated inside a session.
30. As a trainer, I want to demonstrate a lapse by advancing the clock past a week with no deposit, so that the reset is as showable as the climb.
31. As the next agent, I want the multiplier a deposit was paid at recorded rather than recomputed, so that changing the ladder later does not rewrite history.
32. As a reviewer, I want the log at DEBUG to show the week that was scanned, what had landed in it and the streak that came out, so that I can see why a deposit was paid what it was paid.

## Implementation Decisions

**One rule decides every case: a deposit is paid at the multiplier of the streak as it stands once
that deposit has been counted.** Every clause of the feature falls out of it, so it is the thing to
implement and the thing to test. If the deposit is what secures its week, the streak is one week
longer by the time the deposit is priced, and the deposit gets the higher rate — which is exactly
"the one that crosses €50 is already paid at the new multiplier". If the deposit does not secure the
week, the streak is whatever it already was, and the deposit is paid at that. If there is no live
streak, the length is zero and the rate is 1.00×. Implementing it as one function of "streak length
after this deposit" rather than as a handful of special cases is the difference between a rule a
reader can check and four rules a reader has to reconcile.

**The multiplier ladder is a function of streak length, capped, and written down once.** A length of
zero or one pays 1.00×; length *n* pays `min(1.00 + 0.10 × (n − 1), 1.50)`, so the sixth week and
every week after it pays 1.50×. The €50 weekly minimum, the 0.10× step and the 1.50× cap are named
constants owned by the streak module, not literals sprinkled through the arithmetic — a training
exercise that asks a participant to change the ladder should have one place to change it.

**A week is secured on gross deposits, not net.** Withdrawals do not count against a week's progress
towards €50, so €60 in and €20 out still secures the week. The spec says withdrawals never break the
streak, and the net reading contradicts it: a Thursday withdrawal would retroactively un-secure a
week that was already secured on Tuesday, which means either clawing back a multiplier already paid
or leaving the ledger disagreeing with the streak. Neither is worth the anti-gaming it buys, in a
scheme where the gaming costs the customer their own liquidity.

**A streak is alive only if the last secured week is this week or the one immediately before it.**
That makes the current multiplier a pure function of the calendar and the ledger, with no pending
state and nothing waiting on a week to end. Concretely: find the most recent secured week; if it is
older than last week, the streak is dead and the rate is 1.00× *now*, not at the end of the current
week. A customer who skipped last week does not carry last month's 1.40× into a Wednesday deposit.

**Nothing about the streak is stored; all of it is derived from the deposit ledger.** Current
multiplier, current streak length, best-ever streak and this week's progress are all computed by
scanning the account's deposits and grouping them into weeks. No streak table, no per-account state
row, no counter to keep in step. The deciding fact is the movable development clock: it winds time
both ways, and stored streak state would routinely find itself describing a week that is now in the
future. A derivation cannot disagree with the ledger it is derived from. The cost is a per-account
scan on read, which on SQLite with a training-sized history is not a cost.

**What a deposit was actually paid, though, is history and is recorded.** The derived figures answer
"what is my rate"; the ledger answers "what was this deposit paid, and why". The second must not move
when the ladder changes, so the multiplier applied and the bonus granted are written at the moment of
the deposit and never recomputed.

**Points are credited as two rows: the base accrual and the streak bonus.** `PointsReason` already
exists with a single value and was plainly built for this. A deposit credits `BASE_ACCRUAL` for the
whole euros exactly as it does today, plus a new `STREAK_BONUS` row for the uplift when the uplift is
non-zero. Folding the multiplied total into the single existing row would silently redefine the
"base points" the deposit history already reports, and would leave a customer looking at 9 points for
a €7 deposit with no way to see where the 9 came from. Two rows also mean FIFO spending, and the
expiry exercise that comes later, need no changes at all: they iterate credits and neither knows nor
cares why a credit exists.

**Rounding floors twice, and points stay whole.** Floor the amount to whole euros first — the
existing behaviour, unchanged — then multiply by the multiplier, then floor the product. €7.60 at
1.30× is 7 base points and a total of 9, so a bonus of 2. €0.90 at 1.50× is 0 euros and therefore 0
points, bonus included. The bonus row's points are the total minus the base, so the two rows always
sum to the figure the customer was told. Points remain `long`; fractional points would ripple into
spending, the balance, the API and the screen for no benefit a customer can see.

**Weeks are Monday to Sunday in `Europe/Brussels`, named once in the streak module.** The application
clock is UTC and carries no zone worth trusting for this, so the streak module holds the zone itself
and converts the instant it is given. A customer's week is their local week: a deposit at 00:30 on
Monday in Brussels is 23:30 Sunday in UTC, and counting it against the week that just ended is the
kind of bug that arrives as a complaint. Deriving weeks in a fixed named zone also keeps DST out of
the arithmetic, which a fixed offset would not.

**No schema change is needed and none should be invented.** The new `STREAK_BONUS` value is another
string in an existing enumerated column; deposits already record their amount and their moment, which
is everything the derivation reads. The application generates its schema from the entity model and
this feature leaves that model alone.

**The savings account resource gains the streak, and the deposit resource gains its own pricing.**
The account response carries the current multiplier, the current streak length in weeks, the
best-ever streak in weeks, and the new savings that have landed in the current week — enough for the
screen to say both what the next deposit earns and what this week still needs. A deposit response and
each entry in the deposit history carry the base points, the streak bonus points, the multiplier
applied, and the total. `pointsEarned` stays on the deposit response and means the total credited,
which is unchanged at 1.00× and therefore still true of every deposit made before this feature
existed. The history's per-deposit points lookup, which today filters to base accruals only, is
extended to report both reasons rather than being left to quietly mean something new.

**The frontend shows the streak on the savings account page and the split in the history.** The
multiplier, the streak length, the best streak and the remaining amount needed to secure the week
belong beside the balances; each deposit in the history shows its base points, its bonus and the rate
it was paid at. The page already formats money, points and dates and renders a refusal from the
problem document unchanged, so this is new content in an established layout rather than new
machinery. Leaving it backend-only would hide the one part of the feature a customer is meant to
respond to.

**Withdrawals are not touched.** They earn nothing, they do not reduce a week's progress, and they
take nothing back. No code in the withdrawal flow needs to learn what a streak is.

**Seeded demo data gains no fabricated streak.** The seeded customers keep the deposits they have.
Building a streak is what the movable development clock is for, and a seed that ships mid-streak
would make the derivation harder to reason about, not easier to demonstrate. Existing deposits are
not special-cased either: the derivation scans them like any others, so an account's history simply
contains whatever weeks it contains.

**Logging follows the house rule, with the derivation as the thing worth seeing.** One INFO line per
deposit carrying the values that decided the outcome — the week, the new savings in it, whether this
deposit secured it, the streak length, the multiplier, the base and bonus points. DEBUG for the
inputs behind it: the week boundaries in the chosen zone, the weeks the scan walked back through, and
where the streak was found to end. There is no new refusal in this feature, so there is no new WARN;
the deposit refusals that already exist are unchanged.

## Testing Decisions

**A good test here asserts what a customer can observe over HTTP, and nothing underneath it.** The
repo has exactly one test seam — the whole application booted for real against a real SQLite
database, driven the way the frontend drives it — and this feature adds no new one. The multiplier is
observable in three places a test can reach: the points balance on the account, the points reported
against a deposit, and the streak figures on the account resource. Asserting on credit rows or on the
streak derivation directly would bind the tests to the storage and to the "derived, not stored"
decision, which is exactly the decision a later exercise may want to revisit.

**Time is moved by calling the development clock endpoint over HTTP, like any other request.** That
is how the existing clock and job tests work, and it is what makes a multi-week streak testable at
all. A streak test is a sequence of deposits separated by clock advances, asserted on after each one.

**Tests assert on the change they caused, never on an absolute figure.** One database file serves the
whole run and the seeded accounts are shared, so a test reads the balance and the streak before its
own deposit and asserts on the delta. Every existing test in the repo does this and the new ones
follow; `DepositEarnsPointsApiTest` is the closest prior art for the shape, and the clock tests for
moving time.

**The cases worth their own test are the ones the one rule has to get right:** a week secured by a
single deposit and a week secured by several; the deposit that crosses €50 being paid at the new rate
while the earlier deposits in that week are not; a second, third and sixth consecutive week paying
1.10×, 1.20× and 1.50×; a seventh week still paying 1.50×; a skipped week resetting to 1.00× and the
best streak surviving it; a week of €30 resetting the streak as surely as a week of nothing; a
sub-minimum deposit earning at the current rate without securing the week; a withdrawal leaving both
the streak and a week's progress untouched; a deposit whose euros floor away earning nothing at any
multiplier; and a deposit late on Sunday and early on Monday, in Brussels terms, falling in the weeks
a customer would expect.

## Out of Scope

- **Retroactively topping up earlier deposits in a week that later secures.** A deposit keeps the
  points it was paid. Only the crossing deposit and the ones after it see the new rate. Paying the
  crossing deposit at the new rate is a deliberate generosity at the boundary; rewriting settled
  credits behind it is a different feature, and one that makes a customer's history mutable.
- **Reclaiming a bonus when money is withdrawn.** Nothing in this scheme is contingent on the money
  staying put; the loyalty-rate bonus is the exercise that introduces that idea.
- **Points expiry, the loyalty-rate bonus, peer-to-peer gifting and notifications.** All four are
  separate training extensions. In particular, no notification warns a customer that a streak is
  about to lapse, however obviously that feature wants to exist.
- **Per-customer or configurable thresholds.** €50, 0.10× and 1.50× are the same for everybody and
  are constants in the code, not settings.
- **A streak across a customer's savings accounts.** Each savings account has its own streak, because
  every figure in the application is already per-account.
- **Backfilling or migrating anything.** The derivation reads the deposits that are there.

## Further Notes

The multiplier at streak length zero and at streak length one are both 1.00×, which means the deposit
that starts a fresh streak shows no visible jump when it secures its week. That is correct and worth
knowing before somebody reads it as a bug: the first week of a streak pays the ordinary rate by
definition, so "already paid at the new multiplier" is a promise that only becomes visible from the
second week on.

Deriving the streak rather than storing it makes the feature robust to the development clock moving
backwards, which participants will do. It also means an account's streak figures are only ever as
correct as the week boundaries in `Europe/Brussels` — the one place where a future decision to
support customers in another zone would have to be made, and the reason that zone is a named constant
in one module rather than a value passed around.
