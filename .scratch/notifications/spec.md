# Notifications: a rung crossed, and an anniversary worth protecting

Status: ready-for-agent

## Problem Statement

Everything this application knows about a customer, it knows silently. A savings balance climbs
past a round number and nothing marks the moment. A deposit comes within a fortnight of the
anniversary that pays it a tenth of the euros it still holds, and the only way to learn that is to
open the savings account page, scroll to the deposits table and read the small print under a points
figure. The rules are all there and none of them ever speak first.

The loyalty-rate bonus makes this sharpest. A deposit's anniversary pays on what the deposit
**still holds at that moment** — there is no separate forfeit rule, and `LoyaltyService` says so
outright: "There is no rule of its own for forfeiting." A customer who put EUR 500 away last
January is eleven and a half months into a promise worth fifty points, and a EUR 500 withdrawal
today quietly costs them all fifty. Nothing warns them. Worse, the rule that decides *whose* bonus
a withdrawal eats is invisible: withdrawals drain the oldest deposit first, which the code calls
"a protection", and a customer who cannot see the queue cannot be protected by it.

There is a second, subtler absence. This is a training application, and a trainer's whole loop is
to wind the clock forward a year and run the job that cares about the year. Two nightly sweeps
already exist — points expire at 03:00, loyalty pays at 03:30 — and both leave their evidence in
the log and in figures scattered across three pages. Nothing accumulates a *record addressed to the
customer*: a list, in one place, of the moments the rules decided something was worth saying. The
clock is movable, the jobs registry discovers `@Scheduled` methods on its own, the points ledger is
a run of dated batches. Every seam is in place and the rule is missing.

## Solution

**A notification is a record of a moment a rule decided was worth saying.** It has a customer, a
reason, the figures that produced it, the moment it was raised and — once the customer has looked
at it — the moment it was read. It is stored, not derived, for the same reason a `LoyaltyBonusPaid`
row is stored: what happened is a row, and a condition that has passed can no longer be computed
from the present. Nothing is ever deleted, and there is no dismiss button; a training application
whose whole point is showing a rule fire should not let you throw away the evidence that it did.

**Four reasons, and no more.** A savings balance reaching a rung on a fixed ladder
(`BALANCE_THRESHOLD_REACHED`) or falling back off it (`BALANCE_THRESHOLD_LOST`); a deposit's next
anniversary falling within thirty days and worth at least one point, either shielded behind older
deposits (`LOYALTY_BONUS_ABOUT_TO_PAY`) or standing first in line for the next withdrawal
(`LOYALTY_BONUS_AT_RISK`). Points about to expire and a reward newly affordable are real
notifications and are deliberately not here; `NotificationReason` is an enum precisely so a later
feature adds a value without touching any of the machinery. Anything already visible as an ordinary
ledger row — a bonus that has been paid — is not a notification.

**The two loyalty reasons are split by position in the withdrawal queue, and can never both be
true.** A deposit whose anniversary is near is either the oldest one still holding money in its
account, in which case the next euro withdrawn comes out of it and its bonus is the one at risk, or
it stands behind an older deposit that would be drained first. That is not a new rule invented for
this feature: it is `WithdrawalsService`'s oldest-deposit-first allocation, which that class already
names as "a rule rather than an accident of storage", made visible to the person it protects.

**A nightly job does the raising, and a trainer can run it on demand.** `NotificationsAreRaised`
`Nightly.raiseNotifications` runs at 04:00, half an hour after loyalty and an hour after expiry, so
it observes settled state: an anniversary that arrived last night has already been paid by the time
the sweep looks, and is never announced as still coming. Annotating the method `@Scheduled` is the
whole registration — `ScheduledJobs` walks the context for them — so it appears in
`GET /api/dev/jobs` and answers to `POST /api/dev/jobs/raiseNotifications/run` without a line of
wiring.

**A bell in the top bar carries the count, and the page carries the notice.** The bell shows how
many are unread and opens a panel listing everything newest-first, read ones dimmed; opening the
panel marks everything it showed as read, in one call. Separately, the newest unread notification
about a particular savings account renders as an inline notice on that account's page, next to the
figures it is about, so the warning is where the withdrawal form is rather than behind an icon.

**The backend sends figures and the frontend writes the sentence.** Every euro and every date in
this application is formatted Dutch-style in the browser — "EUR 50,00", "9 september 2028" — and
rendering sentences in Java would fork that formatting into a second place that will drift. A
notification travels as its reason and its numbers. Refusal messages are the exception to this and
stay as they are, because a refusal's wording is domain logic; a notification's wording is not.

## User Stories

1. As a customer, I want to be told when my savings balance passes a round figure, so that progress
   I have been making slowly is marked at the moment it happens.
2. As a customer, I want to be told when my balance falls back below a figure I had passed, so that
   a withdrawal's cost to my standing is not silent.
3. As a customer, I want only the rung I have landed on announced when a single deposit vaults
   several, so that one deposit does not produce four notifications.
4. As a customer, I want a balance resting just above a rung to announce itself once and not every
   night, so that the panel is a record rather than a diary.
5. As a customer, I want a balance that crosses a rung, falls back and crosses it again to say so
   each time, so that real movement is never suppressed as a duplicate.
6. As a customer, I want to be told when one of my deposits is within thirty days of the anniversary
   that pays it, so that I know money is about to earn again before it does.
7. As a customer, I want that notification to say what the anniversary is worth at what the deposit
   holds today, so that the figure is one I can act on rather than a promise.
8. As a customer, I want to be warned differently when the deposit about to pay is the one a
   withdrawal would empty first, so that I can see which bonus is actually exposed.
9. As a customer, I want a deposit standing behind an older one to be described as coming rather
   than at risk, so that the warning means something when I get it.
10. As a customer, I want a deposit that will pay nothing to say nothing, so that a EUR 9 deposit
    never warns me about a bonus of zero points.
11. As a customer, I want an anniversary announced once rather than on each of the thirty nights it
    is near, so that one occasion is one notification.
12. As a customer, I want a deposit that becomes the oldest one still holding money to tell me its
    bonus is now first in line, so that the escalation reaches me even though the anniversary has
    not changed.
13. As a customer, I want a count of what I have not read, so that I can tell at a glance whether
    anything happened.
14. As a customer, I want opening the panel to mark everything in it read, so that clearing the
    count takes one action rather than one per row.
15. As a customer, I want notifications I have already read to stay in the panel, dimmed, so that
    the record of what the rules decided survives my having seen it.
16. As a customer, I want the newest unread notification about a savings account shown on that
    account's page, so that a warning about a bonus is beside the withdrawal form that would cost me
    it.
17. As a customer, I want an empty panel to say plainly that nothing has happened yet, so that
    emptiness is not mistaken for a page that failed to load.
18. As a customer, I want every figure and date written the way the rest of the application writes
    them, so that a euro in a notification looks like a euro everywhere else.
19. As a trainer, I want to wind the clock forward a year and run one named job to make
    notifications appear, so that a twelve-month rule is demonstrable inside a training day.
20. As a trainer, I want the job listed by name at `GET /api/dev/jobs` with its schedule, so that I
    do not have to read the source to find what to type.
21. As a trainer, I want the sweep to run after the expiry and loyalty sweeps, so that it never
    announces a bonus that the same night has already paid.
22. As a trainer, I want the seeded demo customers to produce notifications on the first run, so
    that the feature demonstrates itself without fixtures.
23. As an operator, I want running the sweep twice in a row to raise nothing the second time, so
    that a retry is safe.
24. As an operator, I want the uniqueness of an announced anniversary enforced in the database and
    not only in Java, so that two sweeps racing cannot write the same notification twice.
25. As a reviewer, I want one INFO line per sweep carrying what it considered and what it raised, so
    that I can see the shape of a run without reading every line.
26. As a reviewer, I want each raised notification logged with its customer, reason, subject and
    figures, so that I can check by hand that the rule fired on the values it claims.
27. As a reviewer, I want each deposit and account the sweep passed over logged at DEBUG with the
    reason it was passed over, so that silence is explained rather than assumed.
28. As a reviewer, I want marking notifications read logged, so that a count dropping to zero has a
    line behind it.
29. As a reviewer, I want refusals logged at WARN with their reason, so that a 4xx is traceable.
30. As a customer using the application at 320px wide or in a dark colour scheme, I want the bell,
    the panel and the notice to be legible, so that the feature works in the conditions the rest of
    the application already supports.

## Implementation Decisions

### A module of its own

`io.dataroots.savingstreak.notifications`. `NotificationsService` is the only way in and the whole
of the rule; `Notification`, `NotificationRepository`, `BalanceThresholds`, `AnAnniversaryComingSoon`,
`NotificationsAreRaisedNightly` and `NotificationsOnStartUp` are package-private.
`NotificationReason` and the projection `RaisedNotification` are public because the web layer names
them.

The module reads from Deposits (`moneyBalanceOf`, `depositsStillHoldingMoneyIn`) and from Loyalty
(`whenTheDepositsInAnAccountNextPay`) and from Accounts (`holderOfSavingsAccount`). Nothing reaches
into it. It writes no points, moves no money and secures no week.

### The rules

**The ladder is fixed and written down once.** `BalanceThresholds` holds
`THE_RUNGS = [100, 500, 1000, 2500, 5000, 10000]` as `BigDecimal`, ascending, and offers
`theRungStoodOnWith(BigDecimal balance)` — the highest rung not above the balance, or empty — plus
`theRungBelow(BigDecimal rung)`. Per-customer targets were rejected: there is no screen on which to
set one, and a visible shared ladder is what a training application wants to demonstrate.

**A balance notification is raised on a change of rung, not on a crossing event.** The sweep reads
the account's balance, works out the rung it stands on now, and works out the rung it was last known
to stand on from the newest balance-reason notification for that account — `REACHED(t)` means it
stood on `t`, `LOST(t)` means it stood on the rung below `t`. A higher rung now raises
`BALANCE_THRESHOLD_REACHED` for the rung it has landed on; a lower rung now raises
`BALANCE_THRESHOLD_LOST` for the rung it has left; the same rung raises nothing. An account that has
never been notified and already stands on a rung is announced, which is what makes the seeded demo
data produce something on the first run.

This is deliberately a comparison of state rather than a record of crossings. Watching for crossings
would need the previous balance stored somewhere, and the previous balance is not a thing this
application keeps — a savings balance is derived by summing `remainingAmount` across deposits every
time it is asked for.

**An anniversary is worth saying thirty days out.** `AnAnniversaryComingSoon` holds
`HOW_LONG_BEFORE_AN_ANNIVERSARY_IS_WORTH_SAYING = Period.ofDays(30)` and nothing else. The window is
judged in `Europe/Brussels`, the zone every calendar rule in this application already uses, against
the day the sweep is told. Ninety days was rejected as unread wallpaper on a twelve-month cycle and
seven as too late to act on.

**What an anniversary is worth comes from Loyalty and is never recomputed here.** The sweep asks
`LoyaltyService.whenTheDepositsInAnAccountNextPay(savingsAccountId)` and uses the
`NextAnniversaryOfADeposit` it gets back, whose `on` and `points` are exactly the figures the
deposits table already shows. Nothing is raised when the map has no entry for a deposit — that is
Loyalty's way of saying the deposit holds no money — and nothing is raised when `points` is zero,
which is the same rounding rule the loyalty sweep logs as "a tenth of what it still holds rounds
down to no points". Reimplementing the tenth here was rejected outright: the rate is written down in
`LoyaltyRate` and must stay written down once.

**Which of the two loyalty reasons applies is decided by the withdrawal queue.** The deposit that
`WithdrawalsService` would drain first is the oldest one still holding money in the account, ordered
by `depositedAt` then `id`. That deposit gets `LOYALTY_BONUS_AT_RISK`; every other deposit near its
anniversary gets `LOYALTY_BONUS_ABOUT_TO_PAY`. They are mutually exclusive by construction, so one
deposit never produces two notifications for one anniversary.

### What is written down

`Notification` is the entity: `id`, `customerId`, `reason` (`@Enumerated(EnumType.STRING)`),
`savingsAccountId`, `depositId` (null for balance reasons), `amount` (`BigDecimal`, the rung, null
for loyalty reasons), `points` (`Long`, what the anniversary pays, null for balance reasons),
`occursOn` (`LocalDate`, the anniversary day, null for balance reasons), `raisedAt` (`Instant`) and
`readAt` (`Instant`, null until read). Money is `BigDecimal` at two decimal places, as everywhere
else in this application; there are no cents anywhere in this codebase and there are none here.

Nullability is per-reason and total: a balance notification has `amount` and no `depositId`,
`points` or `occursOn`; a loyalty notification has `depositId`, `points` and `occursOn` and no
`amount`. Static factories `balanceRungReached`, `balanceRungLost`, `anniversaryComingFor` and
`anniversaryAtRiskFor` are the only ways to make one, so an inconsistent combination cannot be
constructed.

`readAt` is a moment rather than a boolean, and it is set once. Re-reading an already-read
notification leaves the original moment alone, which is what makes marking-read idempotent on the
entity in the same way `PointsCredit.expire` is.

**Uniqueness is enforced two ways for loyalty and one way for balance, because the two families
genuinely differ.** A loyalty notification is at most one per `(deposit_id, reason, occurs_on)`,
checked in Java against what the sweep has already found and backed by a real partial unique index
`one_notification_per_deposit_and_anniversary` created at start-up by `NotificationsOnStartUp`,
exactly as `LoyaltyOnStartUp` creates `one_bonus_per_deposit_per_anniversary` and for the same
reason: SQLite's dialect writes composite unique clauses nowhere under `ddl-auto=update`. The index
is partial, `where deposit_id is not null`, so balance rows are outside it.

Balance thresholds cannot use an index, because crossing EUR 1.000, falling back and crossing again
must produce three notifications and an index would refuse the third. Their uniqueness is the rung
comparison above, which raises nothing when the rung has not moved. Adding a raised-on date to the
key so both families could share one index was rejected: it would make an unchanged balance announce
itself on a new day, which is precisely the diary this feature must not become.

### The sweep

`NotificationsAreRaisedNightly` is package-private, holds the cron string as
`EVERY_NIGHT_AT_FOUR = "0 0 4 * * *"`, takes the moment from the injected `Clock` and hands it to
`NotificationsService.raiseNotifications(Instant now)`. The method is named `raiseNotifications`
because the method name is what a trainer types into the development jobs endpoint. It returns
`void`; a sweep hands nothing back, as neither of the other two does.

Four o'clock is load-bearing. Points expire at 03:00 and loyalty pays at 03:30; running an hour
after the first and half an hour after the second means the sweep reads balances and next
anniversaries that the night has already settled, so an anniversary that arrived last night has been
paid and moved on and is never announced as still coming.

`raiseNotifications` is one `@Transactional` method. It walks every savings account, and for each
one reads the balance, decides the rung, then reads the deposits still holding money and their next
anniversaries. It collects what it will write, writes it with one `saveAll`, and logs. The caller
says when, as it does for every other moment in this application; nothing inside reads the clock.

### API contract

`GET /api/customers/{customerId}/notifications` returns `List<NotificationResponse>`, newest first,
read and unread together. `POST /api/customers/{customerId}/notifications/read` takes no body,
marks every unread notification belonging to that customer as read at the current moment, and
returns the same list so the caller needs one round trip rather than two.

`NotificationResponse(Long id, NotificationReason reason, Long savingsAccountId, Long depositId,
BigDecimal amount, Long points, LocalDate occursOn, Instant raisedAt, Instant readAt)`. The reason
serialises as its enum name. `occursOn` is a `LocalDate` and not an `Instant`, for the reason
`PointsExpiringNext` gives: the backend picks the zone rather than the browser.

Both endpoints are customer-scoped, matching money-movements, redemptions and gifts. A notification
is addressed to the person who reads it, and carries `savingsAccountId` so the panel can name the
pot and the page can find the notice that concerns it. An unknown customer is refused with the
sentence `AccountsService.noSuchCustomer` already owns.

### The page

`TopBar` gains a `Bell` — a button with the unread count as a badge, rendered only when the count is
above zero, with an `aria-label` that says how many are unread. It opens `NotificationsPanel`, a
list newest-first; each row is an icon, a sentence and a relative-free absolute date, and read rows
carry a dimmed modifier. Opening the panel fires the read call once and drops the badge.

`SavingsAccountPage` gains a `Notice` above the transfer area: the newest unread notification whose
`savingsAccountId` is this account, rendered inline. It reuses the standing `.explanation` idiom for
the two calm reasons and, for `LOYALTY_BONUS_AT_RISK`, the `[role='alert']` treatment that
`Refusal` already uses — a red left border and a tinted ground — because that is the one of the four
that is a warning.

Notifications load with the accounts on boot and re-load on the callbacks that already exist
(`onChanged`, `onClaimed`, `onGiven`), and after a development clock advance or job run. No
interval: this application has no polling anywhere, and adding its first `setInterval` for a nightly
rule would be out of proportion.

The frontend owns every word. `WhatHappened` maps a reason and its figures to a sentence, the way
`WhatItEarned` already composes "7 base + 2 bonus at 1,30x + 30 loyalty" from three numbers.

### Logging

One INFO line per sweep, carrying what it looked at and what it did:
`notifications raised asAt={} accountsConsidered={} depositsConsidered={} raised={}`.

One INFO line per notification raised:
`notification raised customerId={} reason={} savingsAccountId={} depositId={} amount={} points={} occursOn={} notificationId={}`.

DEBUG for the inputs behind a decision, all sharing a prefix so a reviewer can grep the silence:
`account passed over for a balance notification savingsAccountId={} balance={} rung={} reason=it stands on the rung it already stood on`, and
`deposit passed over for an anniversary notification depositId={} reason=<one of: its anniversary is further off than thirty days | a tenth of what it still holds rounds down to no points | this anniversary has already been announced | it holds no money>`.

One INFO line when a customer reads: `notifications marked read customerId={} notifications={}`.

WARN on every refusal with its reason. ERROR with the exception for unexpected failures. SLF4J via
`LoggerFactory.getLogger(Thing.class)`, never `System.out`.

## Testing Decisions

### The seam

Almost every test drives the application over HTTP, at the one seam this repo has, in a package of
its own at `io.dataroots.savingstreak.notifications`. A notification is only meaningful once it has
been raised by a real sweep against a real balance, and the sweep is reachable at
`POST /api/dev/jobs/raiseNotifications/run`.

The ladder and the thirty-day window get the repo's second and third unit tests, because the HTTP
seam cannot cheaply reach the rung arithmetic at every boundary — a balance exactly on a rung,
a cent below it, and a balance above the top rung. Prior art is `LoyaltyAnniversaryTest`.

### Prior art

`AnApplicationWithAClockToMove` is the harness: it winds the clock and runs a named job, and it is
what `TheLoyaltySweepIsAJobThatCanBeRunOnDemandApiTest` uses. Notification tests need their own
application context for the same reason the expiry tests do — the clock is wound and the state is
swept, so the fixture cannot be shared with tests that assume today.

### What gets tested

Named as sentences, in a package of their own:

1. A balance that passes a rung raises one notification naming that rung
2. A balance that vaults several rungs at once raises only the rung it landed on
3. A balance that stays above a rung raises nothing on the second sweep
4. A balance that falls back below a rung raises that it was lost
5. A balance that crosses, falls back and crosses again says so all three times
6. A deposit within thirty days of its anniversary is announced with what that day is worth
7. A deposit further off than thirty days is not announced
8. A deposit whose anniversary would round down to no points is not announced
9. The oldest deposit still holding money is announced as at risk rather than as coming
10. A deposit standing behind an older one is announced as coming rather than as at risk
11. A deposit that becomes the oldest still holding money is announced again, as at risk
12. An anniversary already announced is not announced again on the next sweep
13. Running the sweep twice in a row raises nothing the second time
14. An anniversary paid by the loyalty sweep the same night is not announced as still coming
15. A customer reads their notifications newest first, read and unread together
16. Marking notifications read sets the moment once and leaves an already-read one alone
17. A notification for a customer who does not exist is refused with a readable reason
18. Raising notifications moves no money, credits no points and secures no week

## Out of Scope

- **Points about to expire, and a reward newly affordable.** Both are real notifications and both
  belong to the modules that own those rules. `NotificationReason` is an enum so that adding them
  later touches no machinery.
- **A warning inside the withdrawal form, before the withdrawal is confirmed.** Telling a customer
  what the withdrawal they are typing would cost them is a better product and a different feature;
  it needs a figure computed against an amount not yet submitted, which is a new endpoint.
- **Per-customer thresholds.** The ladder is shared and fixed. There is no screen on which to set a
  target and inventing one is a feature of its own.
- **Dismissing or deleting a notification.** Two states, unread and read, is one state machine;
  three is a fork with no gain, and a training application should not let you discard the evidence
  that a rule fired.
- **Email, push, or any delivery outside the application.** There is no mail infrastructure here and
  a training application does not want one.
- **Polling, server-sent events or websockets.** Notifications load when everything else loads.
- **Backfilling notifications for history.** The first sweep announces the rung an account stands on
  today and the anniversaries coming in the next thirty days. It does not walk backwards inventing
  moments that were never announced.
- **Notifying anyone but the account holder.** Gifts have a sender and a recipient; balance rungs
  and anniversaries have exactly one person they concern.
- **A per-notification link that navigates to the thing it is about.** The panel names the pot; the
  page carries its own notice. Navigation from a row is polish, not the rule.

## Further Notes

- This repo has no `CONTEXT.md` and no `docs/adr/` yet, so nothing here contradicts a recorded
  decision. If either is created, the split of the two loyalty reasons by withdrawal-queue position
  is the call in this feature most worth recording: it makes `WithdrawalsService`'s
  oldest-deposit-first ordering a customer-visible rule for the first time, and any later change to
  that ordering silently changes which bonus this feature calls at risk.
- The sweep walks every savings account on every run. With two seeded customers and three accounts
  that is nothing, and with a real population it would be the first thing to make incremental.
  Noted, not built.
- "Vest" is not this codebase's word and is not used anywhere in this spec. An anniversary *arrives*
  and a deposit *pays*; the only uses of "vesting" in the repository are two clock comments. The
  same goes for "forfeit": there is no forfeit rule, only a bonus worked out on what a deposit still
  holds, so this feature warns about exposure rather than about a scheduled loss.
- `BALANCE_THRESHOLD_LOST` is raised by the nightly sweep and not by the withdrawal that caused it,
  so a customer who withdraws at noon learns at four the next morning. Raising it synchronously in
  `WithdrawalsService` was considered and left alone: it would put a second producer in a second
  module and split the one place this feature logs.
