# 02: An anniversary coming soon says whether it is first in line

Status: ready-for-agent

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

- [ ] A deposit within thirty days of its anniversary is announced, carrying the day and what that
      day is worth at what the deposit holds now
- [ ] A deposit further off than thirty days is not announced
- [ ] A deposit whose anniversary rounds down to no points is not announced
- [ ] A deposit holding no money is not announced
- [ ] The oldest deposit still holding money in an account is announced as `LOYALTY_BONUS_AT_RISK`
- [ ] A deposit standing behind an older one is announced as `LOYALTY_BONUS_ABOUT_TO_PAY`
- [ ] A deposit that becomes the oldest still holding money is announced again, as at risk, for the
      same anniversary
- [ ] A second sweep does not announce an anniversary already announced under the same reason
- [ ] An anniversary paid by the loyalty sweep earlier the same night is not announced as still
      coming
- [ ] The unique index exists in the database and holds when it is written to directly
- [ ] Nothing in this module multiplies by the loyalty rate; the figures come from
      `NextAnniversaryOfADeposit`
- [ ] Each deposit passed over is one DEBUG line carrying the deposit and the reason it was passed
      over
