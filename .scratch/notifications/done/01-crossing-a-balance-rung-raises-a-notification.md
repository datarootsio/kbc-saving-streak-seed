# 01: Crossing a balance rung raises a notification

Status: done

**Blocked by:** None (can start immediately).

**What to build:** A module of its own at `io.dataroots.savingstreak.notifications`, holding the
entity, the ladder and a nightly sweep that so far knows only about balances. `NotificationsService`
is the only way in; everything else in the package is package-private except `NotificationReason`
and the projection `RaisedNotification`, which the web layer will name in ticket 03.

`BalanceThresholds` holds the rungs — 100, 500, 1000, 2500, 5000, 10000 as `BigDecimal`, ascending —
and offers `theRungStoodOnWith(BigDecimal balance)` returning the highest rung not above the balance
or empty, and `theRungBelow(BigDecimal rung)`. This is the only place the ladder is written down.

The load-bearing decision is that a balance notification is raised on **a change of rung, not on a
crossing event**. A savings balance is derived by summing `remainingAmount` across deposits every
time it is asked for; no previous balance is stored anywhere, so there is nothing to compare a
crossing against. Instead the sweep works out the rung the account stands on now, works out the rung
it was last known to stand on by reading the newest balance-reason notification for that account
(`REACHED(t)` means it stood on `t`; `LOST(t)` means it stood on the rung below `t`; no notification
at all means it stood on none), and raises only on a difference. A higher rung raises
`BALANCE_THRESHOLD_REACHED` naming the rung landed on — one notification, even when a single deposit
vaults four rungs. A lower rung raises `BALANCE_THRESHOLD_LOST` naming the rung left behind. The same
rung raises nothing, which is what stops a balance resting at EUR 1.001 announcing itself nightly.

`NotificationsAreRaisedNightly` is package-private, holds `EVERY_NIGHT_AT_FOUR = "0 0 4 * * *"`,
reads the injected `Clock` and hands the moment to `NotificationsService.raiseNotifications(Instant
now)`, which is `@Transactional` and returns `void`. Four o'clock is deliberate: expiry runs at
03:00 and loyalty at 03:30, so this sweep reads state the night has already settled. The method name
is the job name, because that is what a trainer types into `POST /api/dev/jobs/{name}/run`.

The entity carries `id`, `customerId`, `reason`, `savingsAccountId`, `depositId`, `amount`, `points`,
`occursOn`, `raisedAt` and `readAt`, with static factories `balanceRungReached` and `balanceRungLost`
as the only ways to build one, so a balance row cannot be given a `depositId`. The two loyalty
factories and the loyalty columns arrive in ticket 02; add the columns now and leave them null.

- [x] A savings account whose balance passes a rung raises one `BALANCE_THRESHOLD_REACHED` naming
      that rung
- [x] A deposit that vaults several rungs at once raises only the rung the balance landed on
- [x] A second sweep over an unchanged balance raises nothing
- [x] A withdrawal that drops the balance below a rung it had reached raises
      `BALANCE_THRESHOLD_LOST` naming that rung on the next sweep
- [x] A balance that crosses a rung, falls back and crosses it again raises all three notifications
- [x] An account that already stands on a rung and has never been notified is announced on the first
      sweep, so the seeded demo data produces something
- [x] The rungs are written down in exactly one place and no rung literal appears anywhere else
- [x] `raiseNotifications` appears in `GET /api/dev/jobs` with its schedule and runs at
      `POST /api/dev/jobs/raiseNotifications/run`
- [x] The sweep moves no money, credits no points and secures no week
- [x] One INFO line per sweep carrying `asAt`, `accountsConsidered` and `raised`, one INFO line per
      notification raised carrying its customer, reason, account, amount and id, and a DEBUG line
      per account passed over carrying the balance, the rung and the reason

## Verified

Reviewed on branch `ticket/01-crossing-a-balance-rung-raises-a-notification`, diff
`agentic_engineered..HEAD` (commits `74dfd96`, `42b569a`; 20 files, +1622/-11). Nothing outside that
range was considered.

**Checks I ran myself.** `cd backend && ./mvnw test` — `Tests run: 272, Failures: 0, Errors: 0`,
`BUILD SUCCESS`, with the eight new classes in `io.dataroots.savingstreak.notifications` among them
(`BalanceThresholdsTest` plus seven `*ApiTest`). `cd frontend && npm run typecheck` (node v24.16.0) —
clean. I did not reproduce the polluted first gate run described in
`logs/…checks.1.note.md`; the suite is `webEnvironment = RANDOM_PORT`, so it passed for me with the
application still holding 8080.

**Driven over HTTP against the running application** (`…app.1.backend.log`, `io.dataroots.savingstreak`
at DEBUG; log lines below are quoted from it). Job registry first:

    GET /api/dev/jobs
    → {"name":"raiseNotifications","definedBy":"NotificationsAreRaisedNightly","schedule":"cron 0 0 4 * * *"}
    POST /api/dev/jobs/raiseNotifications/run → 200

Then, each step a `POST` to `/api/savings-accounts/{id}/deposits` or `/withdrawals` followed by a run
of the job:

- **A sweep over the seeded state (three accounts, all at EUR 0,00)** — `notifications raised
  asAt=… accountsConsidered=3 raised=0`, and one DEBUG line per account: `account passed over for a
  balance notification savingsAccountId=1 balance=0.00 rung=null reason=it stands on the rung it
  already stood on`.
- **Deposit EUR 1.100,00 into savings 1** (clears 100, 500 and 1.000 in one movement) → exactly one
  row: `notification raised customerId=1 reason=BALANCE_THRESHOLD_REACHED savingsAccountId=1
  depositId=null amount=1000.00 points=null occursOn=null notificationId=1`, `raised=1`. Criteria 1,
  2 and 6 (`rungLastSaid=null fromNotificationId=null` — an account never told anything before).
- **Sweep again, balance unchanged** → `raised=0`, with `rungNow=1000.00 rungLastSaid=1000.00
  fromNotificationId=1` and the pass-over line. Criterion 3.
- **The cent boundary, on savings 2** — EUR 99,99 → `balance=99.99 rungNow=null`, `raised=0`; a
  further EUR 0,01 → `balance=100.00 rungNow=100.00`, `BALANCE_THRESHOLD_REACHED amount=100.00`. A
  balance exactly on a rung stands on it; a cent short stands on nothing.
- **Withdraw EUR 500,00 from savings 1** (1.100 → 600) → `BALANCE_THRESHOLD_LOST … amount=1000.00`,
  and the sweep after it `raised=0` (`rungNow=500.00 rungLastSaid=500.00`). Criterion 4.
- **Deposit EUR 500,00 back** (600 → 1.100) → `BALANCE_THRESHOLD_REACHED … amount=1000.00`. Three
  rows for that account, reached/lost/reached, all naming EUR 1.000. Criterion 5.
- **Off the ladder** — withdrawing down to EUR 100,00 and then to EUR 0,00 each raised one `LOST`
  and then nothing on the repeat sweep; `rungNow=null rungLastSaid=null` reads back correctly for a
  balance below the lowest rung.
- **The sweep moves nothing.** I snapshotted `GET /api/customers/{id}/accounts` and
  `/money-movements` around a sweep that raised a notification for Bram's account
  (`reason=BALANCE_THRESHOLD_REACHED savingsAccountId=3 amount=500.00`): both responses byte-identical
  before and after — money balance, current-account balance, points, expiry date, `newSavingsThisWeek`,
  `currentStreakWeeks`, `currentMultiplier` and the whole ledger. Criterion 9.
- **The refusal path.** `POST /api/dev/jobs/raiseNotification/run` (name mistyped) → 400 with the
  jobs list, and `WARN … job not run: There is no scheduled job called "raiseNotification".`

**The rows themselves.** `select * from notification` on the throwaway database shows the six rows I
caused, `deposit_id`, `points`, `occurs_on` and `read_at` null on every one, `reason` stored as its
name. The loyalty columns exist and are empty, as the ticket asked.

**The ladder is written down once.** `grep` over the new production code finds a rung literal only in
`BalanceThresholds.THE_RUNGS`; `2500`/`10000` appear in no other file under `backend/src/main`.
Criterion 7.

**Visibility** is as specified: only `NotificationsService`, `NotificationReason` and
`RaisedNotification` are public; `BalanceThresholds`, `Notification`, `NotificationRepository` and
`NotificationsAreRaisedNightly` are package-private. Logging is SLF4J via
`LoggerFactory.getLogger(Thing.class)` in both new classes, no `System.out` anywhere in the diff.

**The page** is untouched by this ticket and still works. Playwright (chromium, console/pageerror/
requestfailed subscribed to `…review.1.browser.log`): the dashboard and a savings-account page both
render fully styled and show the state I created, at 1280px and at 320px. No `[pageerror]`; the only
console lines are Vite's connect and the React DevTools hint. The `[requestfailed] … ERR_ABORTED`
entries are StrictMode's duplicate fetches being aborted on remount — the data rendered, so the
retained request succeeded. No bell and no notice yet, correctly: those are ticket 03.

### Two things the next implementer should know (neither blocks this ticket)

1. **A fall of more than one rung names the rung above where the balance landed, not the rung it was
   standing on.** Verified live: from EUR 1.100,00 (announced as `REACHED 1000.00`) down to EUR 100,00
   raises `BALANCE_THRESHOLD_LOST … amount=500.00` — a rung the customer was never told they reached.
   The ticket says two things that conflict here ("naming the rung left behind" vs. "`LOST(t)` means
   it stood on the rung below `t`"), and the implementer chose the reading that keeps the record
   decodable and the sweep idempotent; naming EUR 1.000 would decode as "standing on EUR 500" and
   announce the same fall again the next night, which breaks criterion 3 and spec story 23. The
   reasoning is written out in `NotificationsService`'s class comment. Every acceptance criterion
   describes a single-rung fall, and for those the two readings coincide (I saw `LOST 1000.00` for
   1.100 → 600). Ticket 03 will turn these rows into sentences on the page, so that is the moment to
   decide whether the wording should change; if it should, it is a spec question, not a defect here.
2. **The seeded demo data produces nothing on a first sweep.** The second half of criterion 6 is
   false in this repository: `DemoData` opens savings accounts with no deposits, so every savings
   balance starts at EUR 0,00 and the first sweep on a fresh database logs `accountsConsidered=3
   raised=0` (I saw exactly that). The behaviour the criterion is really about — an account already
   standing on a rung and never notified is announced on its first sweep — is implemented and I saw it
   twice. A trainer wanting the feature to demonstrate itself has to deposit money first, or
   `DemoData` has to seed deposits, which no ticket asks for.

Three smaller notes, all cosmetic and none worth a round trip: `RaisedNotification.of`'s comment
claims the package-private factory is "the only way to make one", which a record's public canonical
constructor contradicts; `RaisedNotification.savingsAccountId` is a boxed `Long` although it is never
null, and `TheNotificationSweep` compares it with `==` (safe only because the other operand is
primitive); and `AnApplicationWithAClockToMove.theApplicationsOwn(Class)` is a public escape hatch
past the HTTP seam on shared test support, which should be narrowed or removed once ticket 03 gives
`notificationsOf` an endpoint.
