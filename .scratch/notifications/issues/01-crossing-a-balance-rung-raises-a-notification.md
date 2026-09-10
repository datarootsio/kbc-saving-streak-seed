# 01: Crossing a balance rung raises a notification

Status: ready-for-agent

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

- [ ] A savings account whose balance passes a rung raises one `BALANCE_THRESHOLD_REACHED` naming
      that rung
- [ ] A deposit that vaults several rungs at once raises only the rung the balance landed on
- [ ] A second sweep over an unchanged balance raises nothing
- [ ] A withdrawal that drops the balance below a rung it had reached raises
      `BALANCE_THRESHOLD_LOST` naming that rung on the next sweep
- [ ] A balance that crosses a rung, falls back and crosses it again raises all three notifications
- [ ] An account that already stands on a rung and has never been notified is announced on the first
      sweep, so the seeded demo data produces something
- [ ] The rungs are written down in exactly one place and no rung literal appears anywhere else
- [ ] `raiseNotifications` appears in `GET /api/dev/jobs` with its schedule and runs at
      `POST /api/dev/jobs/raiseNotifications/run`
- [ ] The sweep moves no money, credits no points and secures no week
- [ ] One INFO line per sweep carrying `asAt`, `accountsConsidered` and `raised`, one INFO line per
      notification raised carrying its customer, reason, account, amount and id, and a DEBUG line
      per account passed over carrying the balance, the rung and the reason
