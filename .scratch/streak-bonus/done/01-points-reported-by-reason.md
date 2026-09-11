# 01: Prefactor — points earned by a deposit are reported as a breakdown by reason

**What to build:** Nothing a customer can see. The points ledger already records *why* a batch of
points was earned, but everything that reads it asks only for base accruals and hands back a single
number. Make the ledger answer with a breakdown by reason instead — for one deposit as it is credited,
and for a set of deposits when the history is listed — while base accrual remains the only reason
there is. This is the "make the change easy" step for the streak bonus: without it, the ticket that
introduces a second reason has to either redefine what the existing "base points" figure means or
grow a parallel lookup beside it.

**Blocked by:** None (can start immediately).

**Status:** done

- [x] Crediting a deposit's points returns what was credited *and under which reason*, rather than a bare total.
- [x] Looking up the points earned by a set of deposits returns, per deposit, the points per reason rather than base points only.
- [x] A deposit still earns one point per whole euro and nothing else; no reason beyond base accrual exists yet.
- [x] Every figure the API and the frontend show is byte-for-byte what it was before this ticket: the deposit response, the deposit history, the points balance and the rewards flow are all unchanged.
- [x] The existing test suite passes untouched — no test needs editing to accommodate this, which is the evidence that no behaviour moved.
- [x] Spending points, and the order they are spent in, is not touched.

## Verified

Reviewed on `ticket/01-points-reported-by-reason` at `daa2f9d`, against
`agentic_engineered..ticket/01-points-reported-by-reason` (2 commits, 6 files, +175/−41: five
backend files and this ticket, nothing under `src/test`, nothing under `web/` and nothing under
`frontend/`).

**The ledger now answers with a breakdown, and it is the same total as before.**
`PointsService.creditPointsFor(...)` returns `PointsByReason` instead of a `long`, and
`pointsEarnedBy(Collection<Long>)` returns `Map<Long, PointsByReason>` instead of
`Map<Long, Long>`; `DepositsService` is the only caller of either and takes `.total()` from both,
so `RecordedDeposit` and every response record are untouched. `grep` over the whole tree finds no
other reference to `creditBasePointsFor` or `basePointsEarnedBy`.

**Both checks in the lab, run again here.** `cd backend && ./mvnw test` → `Tests run: 113,
Failures: 0, Errors: 0, Skipped: 0` / `BUILD SUCCESS`. `cd frontend && npm run typecheck`
(with Node 24 first on PATH, as the lab says) → exit 0, no output. `git diff --stat
agentic_engineered..HEAD` lists no test file, which is the evidence criterion 5 asks for.

**Exercised over HTTP against the running app** (`logs/01-points-reported-by-reason.app.1.backend.log`,
throwaway DB, `io.dataroots.savingstreak` at DEBUG). `POST /api/customers/sign-in` for
`anke.peeters@example.be` → customer 1; deposits of `12.50`, `0.99`, `7.60` and `50` into savings
account 1 → `pointsEarned` 12, 0, 7, 50; `GET /api/savings-accounts/1/deposits` → the same four
figures newest first; `GET /api/savings-accounts/1` → `pointsBalance:69`. One point per whole euro
and nothing else, so criterion 3 holds.

**Refusals and edge cases, not just the happy path.** `amount:"0"` → 400 "A deposit has to be an
amount of more than zero, and 0 is not."; `"-5.00"` → the same wording; `"0.001"` → 400 "An amount
of money has at most two decimal places, and 0.001 has 3."; `"abc"` → 400 "\"abc\" is not an amount
of money…"; `"99999.00"` → 400 "There is not enough in that current account…"; savings account 999
→ 404 "There is no savings account 999." on both the deposit and the history. All byte-identical in
wording to before, and the log shows each resolved by the existing
`RefusalsAsHttp#depositRefused`. The empty-history path (`GET .../2/deposits` → `[]`) exercises the
new `in :reasons` query with an empty id list and logs `depositsAsked=0 depositsFound=0`. `EUR 0.99`
is the flooring edge: still credited, and logged as `pointsByReason={BASE_ACCRUAL=0}`, which keeps
"credited nothing" distinguishable from "never heard of".

**Spending, rewards and withdrawals still behave.** `POST .../1/redemptions CHARITY_DONATION` → 201,
`pointsSpent:10`, voucher `SS-DON-ZRX4CT`, balance 69 → 59; a claim against account 2 → 400 "Charity
donation costs 10 points, and this account has 0."; `POST .../1/withdrawals 5.00` → 201, allocated
FIFO against deposit 1, and the deposit history's points and the points balance were unchanged
afterwards. `POST /api/dev/clock/advance {"days":7}` then a `60.00` deposit → 60 points dated
`2026-09-14`, so the clock path is unaffected.

**The log says what the app did.** New lines from the points module, which had no logger at all
before this branch:

    INFO  i.d.savingstreak.points.PointsService : points credited savingsAccountId=1 depositId=1 pointsByReason={BASE_ACCRUAL=12} points=12
    DEBUG i.d.savingstreak.points.PointsService : points to credit worked out from the amount savingsAccountId=1 depositId=2 amountInEuros=0.99 wholeEuros=0 earnedAt=2026-09-07T15:33:34.240Z
    DEBUG i.d.savingstreak.points.PointsService : points earned by deposits looked up by reason reasons=[BASE_ACCRUAL] depositsAsked=6 depositsFound=6
    INFO  i.d.s.deposits.DepositsService        : deposit accepted depositId=1 savingsAccountId=1 fromCurrentAccountId=1 amount=12.50 pointsEarned=12 pointsByReason={BASE_ACCRUAL=12} depositedAt=2026-09-07T15:33:34.217Z

SLF4J throughout, no `System.out`, greppable in the house style, one line per lookup with counts
rather than one per deposit. `grep -E "ERROR|WARN"` over that log → nothing, and no 500: every
refusal resolved to a problem document. (Deposit and reward refusals leave no `WARN` from
`io.dataroots.savingstreak` — checked `git show agentic_engineered:.../DepositsService.java`, which
has no `log.warn` either, so that gap is pre-existing and this ticket adds no refusal.)

**The page still shows the same figures.** Driven with Playwright (chromium, console/pageerror/
requestfailed subscribed to `logs/01-points-reported-by-reason.review.1.browser.log`): signed in,
opened savings account 1, and read the screenshots. Fully styled, nothing blank. The Deposits table
reads `€ 3,40 → +3`, `€ 60,00 → +60`, `€ 50,00 → +50`, `€ 7,60 → +7`, `€ 0,99 → 0`, `€ 12,50 → +12`
— the API's figures exactly, with the zero row still rendered in its "no earnings" style. A €3.40
deposit made through the form moved "To spend" 119 → 122 and "Saved" € 126,09 → € 129,49, and
submitting `0` rendered the refusal banner "A deposit has to be an amount of more than zero, and 0
is not." unchanged. The only console error was the browser reporting that deliberate 400; the
`net::ERR_ABORTED` entries are the page's own `AbortController` cleanup under React StrictMode
(`App.tsx:281`, `App.tsx:629`) and pre-date this branch. No `pageerror`, and Vite's log holds only
its start-up banner.

### Three non-blocking observations for whoever writes ticket 04

- The reason filter did not disappear, it moved: `PointsService.EARNED_BY_A_DEPOSIT`
  (`EnumSet.of(BASE_ACCRUAL)`) is what the query is given. A `STREAK_BONUS` credit keyed on a
  deposit id will be omitted from `pointsEarnedBy` until that constant names it, with no compile
  error to prompt it. That is one named list in the module the ticket asked for rather than a
  parallel lookup, so the criterion is met — but the javadoc on
  `PointsCreditRepository.earnedBy` ("a second way of earning shows up in the answer instead of
  being filtered out of it") oversells it slightly.
- `EARNED_BY_A_DEPOSIT` is a mutable `EnumSet` in a `static final` field and is handed to the
  repository and to a log call, where `PointsByReason` takes care to hand out an unmodifiable map.
  Nothing mutates it today.
- `PointsByReason`'s canonical constructor is now public and unguarded: a null map, or a map with a
  null value, would NPE out of `EnumMap`. Unreachable through `of()` and `nothing()`.

`docs/module-depth-map.json` was deliberately not regenerated; confirmed against history — none of
the last 11 commits touching `backend/src` touches it, it has its own commit stream.
