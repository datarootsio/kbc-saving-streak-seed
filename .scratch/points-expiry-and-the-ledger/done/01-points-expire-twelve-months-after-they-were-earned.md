# 01: Points expire twelve months after they were earned

**What to build:** A batch of points that is still unspent when its twelve-month anniversary has
passed stops counting towards the customer's balance and can never be spent. The Points module gains
the rule and a way to sweep it; nothing outside the module learns that batches exist.

Twelve months is calendar months in the zone the application already counts calendar things in, so a
batch earned on 15 January expires on 15 January the following year and one earned on 29 February
expires on 28 February. That boundary is unreachable through the HTTP seam — the clock endpoint moves
whole days, forwards only — so it gets the repo's first unit test, which says so in its own words. The batch records its own expiry — it is not emptied — so that what was left
in it when it went is still readable afterwards.

The sweep works oldest batch first. It changes no outcome, since it takes the whole of every batch
past its anniversary, and it is the rule the ledger's dated batches were built for: the oldest points
leave first whichever way they leave.

Status: done

- [x] A batch whose anniversary has passed no longer counts towards the points balance.
- [x] A batch whose anniversary has not yet passed is untouched by the same sweep.
- [x] Points that have expired cannot be spent, and a claim refused for want of them quotes the smaller balance.
- [x] A batch already spent down to nothing has nothing left to expire and is not reported as having expired anything.
- [x] Sweeping twice over the same batches takes nothing the second time.
- [x] Paying in again does not extend the life of points already earned.
- [x] A reward claimed before an anniversary is paid for out of the oldest batch, which then has nothing left to expire.
- [x] The sweep logs one INFO line carrying how many batches and how many points it took and the cut-off that decided them, and hands nothing back — its one caller runs on a schedule with nobody waiting on it.

## Verified

Built and checked with `02` and `03` as one body of work, against a real application on a throwaway
database with the clock wound a year forward.

- **The rule, at the rule.** `PointsExpiryTest` (6 tests) pins the boundary the HTTP seam cannot
  reach. Three mutations that the seven expiry API tests all survive fail it: `Period.ofMonths(12)`
  → `ofDays(365)` (5 assertions fail), the cut-off's `plusDays(2)` → `minusDays(2)` (4), and
  `dayOf` reading UTC (5). One of its tests walks four years hour by hour — 35 040 moments,
  including the leap day and both clock changes — asserting that no batch whose anniversary has
  arrived can fall outside the cut-off the sweep queries with.
- **Over HTTP.** Deposited €60 and €25.50, claimed a 40-point snack voucher, wound the clock 300
  days and swept: nothing taken (`batchesConsidered=0`). Wound 79 more and swept: `batches=2
  points=45` — the 20 left in the drawn-down batch plus the untouched 25 — leaving the 12-point
  batch earned at day 300 alone. Swept again: `batches=0 points=0`.
- **The claim after the sweep** was refused *"Cinema ticket costs 100 points, and you have 12."* —
  the balance that is left, not the points that expired.
- **The money ledger was byte-identical before and after** the sweep, and the deposit still reports
  the 60 points it earned. Expiry takes what is left of a batch; what a deposit earned cannot change.
- **A batch spent to nothing before its anniversary** is left as the spent batch it is rather than
  marked expired as well, so "what expired" is a figure that adds up.

### Mutation checks on the ledger's own guarantees

- Removing `and credit.expiredAt is null` from `remainingPointsOf` → 4 assertions fail.
- `Period.ofMonths(12)` → `ofMonths(24)` → 6 assertions fail.
