# 03: What expires next is on the screen

**What to build:** A customer is told how many points expire next and the day they do, on the
customer overview and on each savings account — the same figures in both places, because the points are
the customer's. Points that vanish with no warning are indistinguishable from points that have gone
missing.

The figure is the points in every batch reaching its anniversary on the earliest such day, and the day
is that anniversary rather than the run of the job that will actually retire it: the anniversary is the
promise made to the customer.

The day is decided in the backend and travels as a plain date. Which calendar day a moment falls on
depends on the zone it is read in, and a page turning an instant into a date picks the zone of whatever
machine is drawing the screen — a customer in London would be shown a deadline a day early.

A customer with nothing to lose reports nothing rather than zero-on-no-date.

Blocked by: 01

Status: done

- [x] `GET /api/customers/{id}/accounts` reports the points expiring next and the day they do, as a plain date.
- [x] `GET /api/savings-accounts/{id}` reports the same two figures for the customer who holds it.
- [x] A customer with no unspent points reports no expiry rather than zero.
- [x] The figures move when a sweep takes the batch they were about to name.
- [x] The frontend shows the two figures beside the points balance, shows nothing where there is nothing to expire, reads the same day in every timezone a browser could be in, and never claims more points are going than the balance climbing beside it.

## Verified

### The API

`GET /api/customers/1/accounts` and `GET /api/savings-accounts/1` report the same pair, because the
twelve months run against the customer's points rather than against one account's saving:

    "pointsBalance": 12, "pointsExpiringNext": 12, "pointsExpiringNextOn": "2028-07-05"

Two deposits on one afternoon are two batches expiring on one day and **both** are counted (85 for a
€60 and a €25.50 deposit, not 60). Claiming the 40-point voucher moved the figure to 45 without
moving the day — spending draws from the batch that was about to go. A customer who has earned
nothing reports `null` for both, not `0` on no date; a deposit of €0.99 that earned nothing leaves
them still `null`, because a batch with nothing in it has nothing to lose.

**The day is the backend's, and travels as a plain date.** It was an `Instant` first, and that was
wrong: `dateOnly.format(new Date(on))` renders the *browser's* calendar day, so a batch whose
anniversary is `2027-01-14T23:30Z` reads as **15 januari 2027** in Brussels and **14 januari 2027**
in London, New York and UTC. The backend already decides the day in `Europe/Brussels`; it now sends
it. `PointsExpiryTest` pins that the day is read in that zone and not in UTC.

### The page, driven with Playwright and looked at

Script, console log and screenshots under `logs/` (`drive.py`, `checks.log`, `overview-*.png`,
`account-*.png`). **31/31 assertions, 0 `pageerror`.**

- The deadline reads `12 points expire on 5 juli 2028` under the points balance on the overview and
  under "Your points" on the account page — the same sentence in both, styled (`display: block`,
  12.48px, `rgb(217,130,0)`, the warm colour reserved for points) rather than raw.
- **The same day in every timezone**, asserted by loading the page in `Europe/Brussels`, `UTC`,
  `America/New_York` and `Pacific/Auckland`: all four read `expire on 5 juli 2028`.
- **It never outruns the balance climbing beside it.** The balance rises to its figure over 900ms,
  and a deadline taken from the settled figure said "12 points expire" under "3 points" for most of
  a second — which reads as a fault. It is now drawn inside the rise and held against the figure on
  screen, the same bargain `ThisWeek` strikes; 51 sampled animation frames, none of them claiming
  more points were going than the balance showed.
- Nothing at all where there is nothing to expire, on both screens.
