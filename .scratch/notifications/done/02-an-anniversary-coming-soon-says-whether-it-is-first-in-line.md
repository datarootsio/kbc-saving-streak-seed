# 02: An anniversary coming soon says whether it is first in line

Status: done

**Blocked by:** 01 (Crossing a balance rung raises a notification).

**What to build:** The two loyalty reasons, added to the same sweep. A deposit whose next
anniversary falls within thirty days and is worth at least one point is announced; which of the two
reasons it gets is decided by where it stands in the withdrawal queue.

`AnAnniversaryComingSoon` holds `HOW_LONG_BEFORE_AN_ANNIVERSARY_IS_WORTH_SAYING = Period.ofDays(30)`
and nothing else, judged in `Europe/Brussels` against the moment the sweep is told. Ninety days is
unread wallpaper on a twelve-month cycle and seven is too late to act on.

What an anniversary is worth is never recomputed here. The sweep asks
`LoyaltyService.whenTheDepositsInAnAccountNextPay(savingsAccountId)` and uses the
`NextAnniversaryOfADeposit` it gets back — the same `on` and `points` the deposits table already
shows. A deposit absent from that map holds no money and is skipped; a deposit whose `points` is
zero is skipped, which is the same rounding the loyalty sweep logs as "a tenth of what it still
holds rounds down to no points". The rate lives in `LoyaltyRate` and must stay written down once, so
nothing in this module multiplies by a tenth.

The load-bearing decision is the split. `WithdrawalsService` drains the oldest deposit first, ordered
by `depositedAt` then `id`, and calls that ordering "a protection". This ticket makes that protection
visible to the person it protects: the oldest deposit still holding money in an account gets
`LOYALTY_BONUS_AT_RISK`, because the next euro withdrawn comes out of it; every other deposit near
its anniversary gets `LOYALTY_BONUS_ABOUT_TO_PAY`. They are mutually exclusive, so one deposit never
produces two notifications for one anniversary — and a deposit that later becomes the oldest, when
the deposits in front of it are emptied, does get announced again, this time as at risk. That
escalation is the notification worth having, which is why `reason` is part of the uniqueness key.

Uniqueness is one loyalty notification per `(deposit_id, reason, occurs_on)`, checked in Java and
backed by a real partial unique index `one_notification_per_deposit_and_anniversary` on those three
columns `where deposit_id is not null`, created at start-up by `NotificationsOnStartUp` exactly as
`LoyaltyOnStartUp` creates `one_bonus_per_deposit_per_anniversary` and for the same reason: SQLite's
dialect writes composite unique clauses nowhere under `ddl-auto=update`. The index is partial so the
balance rows from ticket 01 stay outside it — they cannot use an index, because a rung crossed twice
must be announced twice.

- [x] A deposit within thirty days of its anniversary is announced, carrying the day and what that
      day is worth at what the deposit holds now
- [x] A deposit further off than thirty days is not announced
- [x] A deposit whose anniversary rounds down to no points is not announced
- [x] A deposit holding no money is not announced
- [x] The oldest deposit still holding money in an account is announced as `LOYALTY_BONUS_AT_RISK`
- [x] A deposit standing behind an older one is announced as `LOYALTY_BONUS_ABOUT_TO_PAY`
- [x] A deposit that becomes the oldest still holding money is announced again, as at risk, for the
      same anniversary
- [x] A second sweep does not announce an anniversary already announced under the same reason
- [x] An anniversary paid by the loyalty sweep earlier the same night is not announced as still
      coming
- [x] The unique index exists in the database and holds when it is written to directly
- [x] Nothing in this module multiplies by the loyalty rate; the figures come from
      `NextAnniversaryOfADeposit`
- [x] Each deposit passed over is one DEBUG line carrying the deposit and the reason it was passed
      over

## Verified

Reviewed on `ticket/02-an-anniversary-coming-soon-says-whether-it-is-first-in-line`, diff range
`ticket/01-crossing-a-balance-rung-raises-a-notification..HEAD` — commits `3fc5593` (the work) and
`2371dc9` (the ticket move). 15 files, +1267/-40. Nothing outside that range was judged.

### Checks

`.scratch/notifications/lab.sh checks` with **nothing listening on 8080** (the app was stopped
first, precisely so the harness defect in `logs/THE-PHANTOM-RED-BUILD.md` could produce neither a
false red nor a false green): `Tests run: 281, Failures: 0, Errors: 0`, `BUILD SUCCESS`, and
`tsc --noEmit` clean. Output in `logs/02-….review.1.checks.log`. All five new anniversary test
classes ran.

### The feature, driven over HTTP against the running app

Backend-only ticket, so `curl` against `http://localhost:8080`, with every claim below read back out
of `logs/02-….app.1.backend.log`. Note for the next reader: logback abbreviates the logger, so grep
`i\.d\.s\.n\.` rather than `io.dataroots`.

Set-up: Anke savings 1 ← EUR 200,00 (deposit 1) on day 0, then EUR 300,00 (deposit 4) on day 10;
Anke savings 2 ← EUR 9,99 (deposit 2) on day 0 and EUR 100,00 (deposit 5) on day 345; Bram savings 3
← EUR 400,00 (deposit 3) on day 0. Clock wound to day 345 (2027-08-21), so the window reached
`anniversariesWorthSayingUpToAndIncluding=2027-09-20`.

- **Announced with the day and the figure, and the split** — one sweep, six rows:
  `notification raised customerId=1 reason=LOYALTY_BONUS_AT_RISK savingsAccountId=1 depositId=1 amount=null points=20 occursOn=2027-09-10 notificationId=2`
  and `… reason=LOYALTY_BONUS_ABOUT_TO_PAY … depositId=4 … points=30 occursOn=2027-09-20 notificationId=3`
  and `… LOYALTY_BONUS_AT_RISK … depositId=3 … points=40 occursOn=2027-09-10 notificationId=6`.
  Deposit 1 is the oldest still holding money in savings 1 and got at risk; deposit 4 stands behind
  it and got about to pay. The DEBUG line behind each says why:
  `a deposit's anniversary is worth saying depositId=4 … firstInLineForTheNextWithdrawal=1 reason=LOYALTY_BONUS_ABOUT_TO_PAY`.
  Deposit 4's anniversary is *exactly* thirty days out and was announced, so the boundary is
  inclusive as the ticket asks.
- **The figures are Loyalty's, unchanged** — the savings-account page shows "30 points due on
  20 september 2027" against deposit 4 (screenshotted), byte-for-byte the `points=30 occursOn=2027-09-20`
  in the notification. `grep` over the whole module finds no `LoyaltyRate`, no `divide`, no
  `multiply`, no tenth: the only occurrence of "a tenth" is a comment.
- **Further off than thirty days** —
  `deposit passed over for an anniversary notification depositId=5 occursOn=2028-08-21 worthSayingUpToAndIncluding=2027-09-20 reason=its anniversary is further off than thirty days`.
- **Rounds down to no points** —
  `deposit passed over … depositId=2 occursOn=2027-09-10 remainingAmount=9.99 reason=a tenth of what it still holds rounds down to no points`.
- **Holding no money** — after `POST /api/savings-accounts/1/withdrawals` for EUR 200,00 (allocated
  to deposit 1, which the log confirms), `depositsConsidered` fell from 5 to 4 and nothing further
  was said about deposit 1. Emptying savings 3 gave the account-level line
  `account passed over for anniversary notifications savingsAccountId=3 reason=none of its deposits holds money`.
- **The escalation** — that same sweep raised
  `notification raised … reason=LOYALTY_BONUS_AT_RISK savingsAccountId=1 depositId=4 … points=30 occursOn=2027-09-20 notificationId=8`:
  the same deposit and the same day as row 3, under the other reason, because
  `firstInLineForTheNextWithdrawal=4` once its shield was emptied.
- **A second sweep says nothing** — `notifications raised … raised=0`, with one
  `reason=this anniversary has already been announced occursOn=… announcedReason=…` per deposit.
- **Paid the same night** — clock to day 370, `POST /api/dev/jobs/payLoyaltyBonuses/run` moved Bram
  from 400 to 440 points, and the `raiseNotifications` run straight after logged
  `deposit passed over … depositId=3 occursOn=2028-09-10 worthSayingUpToAndIncluding=2027-10-15 reason=its anniversary is further off than thirty days`
  with `raised=0`. The settled anniversary was never announced as still coming.
- **The index** — start-up logged
  `the record of announced anniversaries was made unique per deposit, reason and anniversary index=one_notification_per_deposit_and_anniversary columns=[deposit_id, reason, occurs_on] over=[deposit_id is not null]`,
  and `sqlite_master` holds
  `CREATE UNIQUE INDEX … on notification (deposit_id, reason, occurs_on) where deposit_id is not null`.
  Written to directly with `sqlite3`, past the sweep and past the Java check: a duplicate
  `(4, LOYALTY_BONUS_AT_RISK, 2027-09-20)` was refused with
  `UNIQUE constraint failed: notification.deposit_id, notification.reason, notification.occurs_on`;
  the *other* reason for deposit 1 on the same day was accepted (the escalation survives); and a
  second `BALANCE_THRESHOLD_REACHED` on the same rung was accepted (the partial clause holds ticket
  01's rows outside it). The poked rows were deleted afterwards.
- **No side effects** — `/api/customers/1/accounts`, `/api/customers/2/accounts` and
  `/api/customers/1/money-movements` were byte-identical either side of a sweep that raised rows.
- **A refusal still says why** — `POST /api/dev/jobs/raiseNotification/run` → 400 and
  `WARN … job not run: There is no scheduled job called "raiseNotification".`
- No `ERROR` and no stack trace anywhere in the app log; the three `Exception` hits are Spring's
  DEBUG lines resolving that one refusal. No `System.out` in the module.
- Page regression (this ticket adds no UI): Playwright at 1280px and 320px, sign-in, dashboard and
  savings-account page all render fully styled with the state created above.
  `logs/02-….review.1.browser.log` has no `[pageerror]`; the `ERR_ABORTED` entries are StrictMode's
  aborted duplicate fetches and the data rendered.

### The judgment call, exercised

The implementer read the window as having **one** boundary: an anniversary whose day has already
gone but which Loyalty still reports as owed is announced. Driven on a fresh database — EUR 500,00
deposited, clock wound 366 days, no loyalty sweep run — the sweep raised
`LOYALTY_BONUS_AT_RISK … points=50 occursOn=2027-09-10` against a clock reading 2027-09-11. Passed:
the ticket's only stated skip reason is an upper bound; `NextAnniversaryOfADeposit`'s own contract
says "the date is therefore in the past exactly while a payment is outstanding"; and such a deposit's
money is exposed right now. It is why one ticket-01 test changed, and that test was *strengthened*
(a count of 1 became a count of 2 asserted by reason), not loosened.

### Recorded, not blocking

`/code-review` returned four findings, all low, none breaking a criterion here:

1. The split reads queue *position* only, never the size of the shield. A EUR 5 oldest deposit
   (skipped for rounding to no points) leaves a EUR 500 deposit behind it labelled
   `LOYALTY_BONUS_ABOUT_TO_PAY`, whose javadoc promises "nothing a single withdrawal does reaches it
   first" — untrue when the shield is EUR 5. The implementation is exactly what this ticket
   specifies; the absolute wording of the enum is the thing worth revisiting.
2. The stored `points` is never revised. Uniqueness on `(deposit_id, reason, occurs_on)` — which
   this ticket mandates — means a partial withdrawal that does not empty the deposit leaves the row
   reading the old figure for the rest of the window. "What the deposit holds now" therefore means
   "at the moment it was raised". The two criteria cannot both hold any other way.
3. `NotificationsService`'s comment claims "nothing in here reads a clock of its own", but
   `LoyaltyService.whenTheDepositsInAnAccountNextPay` takes no moment and reads the clock itself, so
   the anniversary day and the window boundary come from two readings microseconds apart. Harmless
   with the one caller there is, and the ticket names that signature.
4. A fall of more than one rung names the lowest rung no longer reached. Ticket-01 code, untouched
   by this diff, and already recorded on that ticket.
