# 02: This week's new savings, counted Monday to Sunday in Brussels

**What to build:** A customer can see how much they have paid into a savings account so far this
week, and how much more the week still needs. A week runs Monday to Sunday in `Europe/Brussels`, and
the figure is the gross total of deposits that landed in it — withdrawals do not reduce it. The
weekly minimum of €50 is named once here, and this is where the customer first sees a week as
something they are part-way through.

Nothing yet pays a bonus. This ticket establishes the week and proves it behaves at the boundaries:
a Sunday-late deposit counts for the week that is ending, a Monday-early one for the week starting,
both judged in Brussels terms rather than in the clock's UTC.

The savings account resource carries the new savings landed in the current week; the savings account
page shows it beside the balances as progress towards the €50 the week asks for. The figure is
derived from the deposit records on read — there is no stored weekly total to keep in step.

**Blocked by:** None (can start immediately).

**Status:** needs-review

- [x] The savings account resource reports the new savings that have landed in the current week.
- [x] A deposit raises that figure by its full amount, immediately.
- [x] A withdrawal leaves the figure unchanged, however large it is.
- [x] Advancing the development clock into the next week returns the figure to zero without any deposit or job having run.
- [x] Advancing the clock backwards restores the earlier week's figure — the derivation reads the ledger and holds no state that could go stale.
- [x] A deposit at 23:30 on Sunday, Brussels time, counts towards the week that is ending; one at 00:30 on Monday, Brussels time, counts towards the week beginning — including across a daylight-saving change.
- [x] Each savings account reports only its own new savings; a deposit into one account does not move the figure on another, including another held by the same customer.
- [x] The €50 weekly minimum exists as a single named constant, not as a literal at each place it is compared against.
- [x] The savings account page shows the week's progress towards €50 beside the money and points balances, formatted the way money already is on that page.
- [x] DEBUG logging shows the week boundaries the derivation used, in the zone it used, and the deposits it counted into the week.
- [x] Points earned by a deposit are unchanged: one per whole euro.

## Review feedback - attempt 1

The backend derivation is right and I could not break it. What sends this back is the page: adding a
third cell to `.balances` truncates money — both the new weekly figure and the "Saved" balance that
was already there — across roughly 600px–860px of viewport width. Everything else below is either a
second real bug with a narrow trigger, or a note.

Set-up for every reproduction below: app on a throwaway database
(`SAVING_STREAK_DB=$(mktemp -d)/s.db`) with `--logging.level.io.dataroots.savingstreak=DEBUG`, Vite
on 5173, driven with Playwright (chromium, sync API).

### 1. Blocker: the third balance cell truncates money between 600px and 860px

Expected: the week's figure shown "formatted the way money already is on that page", and the two
balances that were already there unchanged.

Seen: from the new `@media (min-width: 37.5rem)` breakpoint up to about 860px, the row is three
columns narrower than a four-digit euro figure, `.balances > div { overflow: hidden }` (index.css:491)
hides the excess, and the customer is shown a **different number** rather than a wrapped or shrunk
one.

Reproduce: sign in as `anke.peeters@example.be`, open savings account 1, deposit `2000.00` so the
balance is `€ 2.002,50`, then set the viewport to 620px. The screen reads:

    Saved            To spend          This week
    € 2.002,5        2.052 points      € 2.000,0

`€ 2.002,50` renders as `€ 2.002,5` and `€ 2.000,00` as `€ 2.000,0` — the trailing cent digit is
clipped off both, so the page states a wrong amount. At 800px the money fits but the sentence does
not: it reads `€ 2.000,00 of` with `of` cut mid-word and `€ 50,00` orphaned on the line below.

Measured with a Range over each `dd`'s figure (`figure right edge − cell content-box right edge`, so
a positive number is what is hidden):

    viewport   cell width   Saved over   To spend over   This week over
    595px      497px        -318px       -352px          0px
    600px      135px         +46px        +12px          +65px
    640px      147px         +45px         +6px          +64px
    700px      165px         +35px        -8px           +54px
    760px      184px         +16px        -27px          +35px
    800px      196px          +4px        -39px          +23px
    840px      209px         -9px         -52px          +10px
    900px      229px        -29px         -72px            0px

This is new, not pre-existing. I put the pre-ticket rule back in the live DOM only (no file change)
and re-measured: with `repeat(auto-fit, minmax(11rem, 1fr))` and three cells the columns are still
183px at 600px, so the `repeat(3, minmax(0, 1fr))` rule is not the cause — the cause is that the row
now holds three cells instead of two. With the third cell hidden and the old rule, the cell is 275px
at 600px and nothing overflows. So the previous page never clipped here and this one does.

The implementer's log says the layout was "Verified at 420/520/760px and in dark mode". The largest
balance in that session was `€ 92,50`, which fits in 183px; the clipping only appears once a figure
runs to four digits, which the seeded current accounts (€ 2.480,00 and € 1.150,00) make easy to
reach. Please check this with a four-digit balance, at 600, 640, 700, 760 and 800px, before ticking
the criterion again.

Any of these would fix it, and the choice is the implementer's: raise the three-column breakpoint to
where a four-digit figure fits (measured above: about 54rem), or go two-up before three-up, or let
the figure shrink (`.balances dd` is `clamp(1.85rem, 6vw, 2.5rem)`) / wrap instead of being hidden.
A figure that is cut off is worse than a figure that is small.

### 2. Bug: `advance {days: 7}` is 168 hours, not a Brussels calendar week

Expected, per the criterion: advancing the development clock into the next week returns the figure to
zero.

Seen in the ordinary case, and it works — I verified it live and it is the reason this criterion is
only *conditionally* unticked. `POST /api/dev/clock/advance {"days":6}` on Monday 2026-09-07 left the
week at `90.00`, one more day took it to `0.00` with `stillNeededThisWeek` back to `50.00`, and the
money, points and deposit history were untouched.

But `MovableClock.instant()` is `realClock.instant().plus(movedForwardByDays.get(), ChronoUnit.DAYS)`
(MovableClock.java:51), and `Instant.plus(n, DAYS)` is exactly n×86400s, while `SavingsWeek` counts
calendar weeks in `Europe/Brussels`. Where those disagree, "advance a week" does not advance a week.
Concretely, in the week that ends with the autumn transition: real now = Mon 2026-10-19 00:30
Brussels = `2026-10-18T22:30Z`; +168h = `2026-10-25T22:30Z`, which in Brussels (now +01:00) is Sun
2026-10-25 23:30 — still inside the week that began on Mon 2026-10-19, whose `endsAt` I read from the
live log as `2026-10-25T23:00:00Z`. The figure does not reset, the trainer's headline demo shows the
week not resetting, and `TheWeekMovesWithTheDevelopmentClockApiTest` (line 84) goes red.

The window is one hour per year and only in the autumn direction (I checked the spring direction:
+168h from Mon 00:30 lands on Mon 01:30 of the following week, which is fine). It is nonetheless the
exact path the criterion names, so either make the advance a calendar-day move in the zone weeks are
counted in, or make the test independent of where the real clock is standing — a comment explaining
why 7×24h is good enough is not enough on its own, because the demo still misbehaves.

### 3. `a_week_that_has_taken_in_more_than_it_asks_for_needs_nothing_further` can pass without doing anything

`ThisWeeksNewSavingsApiTest.java:135` deposits `80.00` from Bram's current account and then asserts
`newSavingsThisWeek >= 80.00` and `stillNeededThisWeek == 0.00`. The response status is never
asserted. Both assertions are absolute figures on an account the whole run shares, so a refused
deposit still passes green. Bram's seeded current account is the deliberately shallow one
(`DemoData.java:49`, `1150.00`) precisely so that deposits can be refused, and the suite's Testing
Decisions say tests "assert on the change they caused, never on an absolute figure" — which the three
sibling tests in the same file do, with a before/after delta. Today the drawdown on Bram's current
account across the run is only this 80.00, so the test is fragile rather than actually false, but it
is the one test in the new file that does not follow the rule the others follow.

Same file, line 151: `deposit(...).getBody()` is dereferenced with no status check, so a refusal
surfaces as an NPE instead of naming the refusal.

### 4. Notes, not requirements

- `SavingsAccountController.savingsAccount` (line ~62) assembles the response from three separate
  read-only transactions. The money/points pair already had that window; the week makes a torn read
  visibly self-contradictory ("Saved € 0,00" beside "This week € 60,00" with a full bar). One
  `@Transactional(readOnly = true)` on the handler would close it.
- `StreaksService.countedInto` uses a bare `setScale(2, HALF_UP)`, which restates
  `NewSavingsThisWeek.DECIMAL_PLACES`. Note that `AmountOfMoney` is package-private in `deposits`, so
  it genuinely cannot be reused from `streaks` — this is a nit, not a missed reuse.
- `NewSavingsThisWeek.week()` is read by nothing (StreaksService logs its own local `week`). Fine as
  scaffolding for ticket 03; worth knowing it is unread today.
- `DepositsService.depositsLandedBetween` does not check that `from` precedes `until`; a reversed pair
  reads as "nothing landed" rather than as a programming error.
- The local `WithdrawalView` record at the bottom of `ThisWeeksNewSavingsApiTest` is a third copy of
  that shape, but the other two are in existing tests and `support/` has no shared one, so this
  follows the house pattern rather than breaking it. Leave it.

### What is already right and should not be reworked

Verified working, so please keep it: the derivation itself, the zone, the boundaries, gross counting,
the single constant and the logging. Details in the section below — everything there was exercised
against the running application.

### What I ran

- `cd backend && ./mvnw test` → BUILD SUCCESS, `Tests run: 126, Failures: 0, Errors: 0`, twice.
  13 new tests: `ThisWeeksNewSavingsApiTest` 7, `WeeksRunMondayToSundayInBrusselsApiTest` 5,
  `TheWeekMovesWithTheDevelopmentClockApiTest` 1.
- `cd frontend && npm run typecheck` → exit 0 (Node 24.16.0 first on PATH).
- Over curl on a fresh throwaway database, savings account 1: `0.00 / 50.00` → deposit `12.50` →
  `12.50 / 37.50` → deposit `40.00` → `52.50 / 0.00`; withdraw `50.00` → money `52.5 → 2.5`, week
  still `52.50`, points still `52`. Accounts 2 and 3 stayed at `0.00` throughout. Whole-week
  withdrawal on account 3: deposit `75.00` → `75.00 / 0.00`, withdraw the whole `75.00` → money
  `0`, week still `75.00`, points still `75`.
- Clock: `advance {days:6}` → Sunday, week `90.00`; `advance {days:1}` → Monday, week `0.00 / 50.00`,
  money and points and deposit history unchanged. `advance {days:-7}` → 400 with
  `The clock only moves forward. Ask for at least one day, not -7.` and
  `WARN i.d.savingstreak.clock.ClockService : clock not advanced: ...`, which is why the
  clock-backwards criterion is only reachable from the test-supplied clock.
- DST boundaries read out of the live DEBUG log: autumn week
  `weekStartsAt=2026-10-18T22:00:00Z weekEndsAt=2026-10-25T23:00:00Z` (169 hours), spring week
  `weekStartsAt=2027-03-21T23:00:00Z weekEndsAt=2027-03-28T22:00:00Z` (167 hours). Both are Brussels
  local midnight; a fixed seven-day span would have been an hour out in each direction.
- Refusals unchanged word for word: `0`, `-5.00`, `0.001`, `abc`, `99999.00`, savings account 999
  (404), over-withdrawal (400 + WARN with the reason). No `ERROR` line and no `Completed 500` in the
  backend log; no `System.out` anywhere.
- Page: signed in, deposited 20 then 40 through the form, withdrew 30, submitted `0`. The week cell
  went `€ 0,00 of € 50,00 / € 50,00 more to go` → `€ 20,00 ... / € 30,00 more to go` (bar 40%) →
  `€ 60,00 ... / the week has what it asks for` (bar `week-bar full`, 100%), and the withdrawal moved
  "Saved" 60,00 → 30,00 while the week stayed at `€ 60,00`. Refusal banner rendered unchanged. No
  `pageerror`; the only `console:error` was the deliberate 400, and the `net::ERR_ABORTED` entries
  are the page's pre-existing StrictMode `AbortController` cleanup. Vite's log holds only its
  start-up banner. Layout is fine at 420px, 560px and 900px+ and in dark mode — it is only the
  600px–860px band that clips.

## Response to the review - attempt 2

Every point above is addressed. What changed, in the reviewer's own numbering:

1. **The clipping.** `.balances` is now two across with the week beneath it across the whole row from
   37.5rem, and three across only from 56rem. Measured with a Range over every text node in each
   `dd` (`figure right edge − cell content-box right edge`) at 360, 420, 520, 560, 595, 600, 620,
   640, 700, 760, 800, 840, 860, 880, 896, 900, 940, 1000, 1100, 1280 and 1440px, with
   `Saved € 2.002,50` / `2.052 points` / `This week € 2.052,50`: nothing overflows at any of them and
   the page never scrolls sideways. The two money cells are 275px at 600px, which is the width the
   reviewer measured for the pre-ticket page. In the three-column band the figure is sized by the
   room a third of the row leaves (`clamp(1.6rem, 3.9vw, 2.5rem)`, back to 2.5rem by ~1030px), and a
   probe with `€ 99.999,00` in the cell clears the edge by 31px at 896px and 46px at 1280px.
   A second cause was found and fixed: the figure and "of € 50,00" had no break opportunity between
   them, so the phrase could only leave the cell and be hidden — which it was at 1280px too, by 7px.
2. **`advance {days: 7}`.** `MovableClock` now moves through the calendar of the zone the application
   counts its days in rather than adding 86400 seconds a day, so a week is a week. Exercised live
   over the endpoint: 42 days on reads `2026-10-19T16:42:08Z` (Mon 18:42 Brussels), a €60 deposit
   makes the week `60.00`, and 7 more days reads `2026-10-26T17:42:08Z` — Mon 18:42 Brussels, an hour
   later in UTC than a fixed span, and the week is back to `0.00 / 50.00` with money, points and the
   deposit history untouched. Three existing tests asserted the fixed span to the millisecond and now
   say what a moved clock reads, through one shared helper.
3. **The test that could pass without doing anything.** Both that test and its sibling now go through
   a `depositAccepted` helper that asserts `201` before anything is read, and the week figure is
   asserted as the change the test caused. The three other set-up deposits in the file go through it
   too.
4. **The notes.** One `@Transactional(readOnly = true)` on the handler — confirmed in the log as
   `Creating new transaction with name [...SavingsAccountController.savingsAccount]` with every module
   read participating in it. `countedInto` quotes cents from `NewSavingsThisWeek.DECIMAL_PLACES`.
   `depositsLandedBetween` refuses a reversed window with a WARN saying why. `NewSavingsThisWeek.week()`
   and the local `WithdrawalView` are left as they are, as the review suggested.
