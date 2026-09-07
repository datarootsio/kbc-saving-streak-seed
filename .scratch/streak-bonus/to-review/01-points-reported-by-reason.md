# 01: Prefactor — points earned by a deposit are reported as a breakdown by reason

**What to build:** Nothing a customer can see. The points ledger already records *why* a batch of
points was earned, but everything that reads it asks only for base accruals and hands back a single
number. Make the ledger answer with a breakdown by reason instead — for one deposit as it is credited,
and for a set of deposits when the history is listed — while base accrual remains the only reason
there is. This is the "make the change easy" step for the streak bonus: without it, the ticket that
introduces a second reason has to either redefine what the existing "base points" figure means or
grow a parallel lookup beside it.

**Blocked by:** None (can start immediately).

**Status:** needs-review

- [x] Crediting a deposit's points returns what was credited *and under which reason*, rather than a bare total.
- [x] Looking up the points earned by a set of deposits returns, per deposit, the points per reason rather than base points only.
- [x] A deposit still earns one point per whole euro and nothing else; no reason beyond base accrual exists yet.
- [x] Every figure the API and the frontend show is byte-for-byte what it was before this ticket: the deposit response, the deposit history, the points balance and the rewards flow are all unchanged.
- [x] The existing test suite passes untouched — no test needs editing to accommodate this, which is the evidence that no behaviour moved.
- [x] Spending points, and the order they are spent in, is not touched.
