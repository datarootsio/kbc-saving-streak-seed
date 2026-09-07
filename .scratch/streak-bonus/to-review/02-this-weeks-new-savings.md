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

## Review feedback - attempt 2

Attempt 2 genuinely fixed both bugs it was sent back for, and I verified both against the running
application — see "What is already right" below and do not rework it. What sends it back again is
three defects that attempt 2 introduced, all in the two files it changed to make those fixes. One is
a correctness regression in `MovableClock`; two are in the row of balances on the page.

Set-up for every reproduction below: app on a throwaway database
(`SAVING_STREAK_DB=$(mktemp -d)/s.db`) with `--logging.level.io.dataroots.savingstreak=DEBUG`, Vite
on 5173, driven with Playwright (chromium, sync API) and curl.

### 1. Blocker: the new calendar-day clock reads *backwards*, which the class forbids

Expected: `MovableClock` never reads backwards. Its own javadoc (MovableClock.java:16-18) is built on
that — "Backwards would let a demonstration produce records dated before ones already written, which
is a database nobody can explain rather than a lesson".

Seen: `instant()` is now `realMoment.atZone(daysAreCountedIn).plusDays(days).toInstant()`
(MovableClock.java:81). `ZonedDateTime.plusDays` shifts a target local time that lands *inside* a
spring-forward gap forward by the gap, then stops shifting at the first local time past the gap — so
the reading drops an hour there. One real minute of elapsed time makes the clock go 59 minutes
backwards.

Reproduce (no application needed — this is the expression from line 81, run on its own):

    ZoneId Z = ZoneId.of("Europe/Brussels");
    Instant t1 = ZonedDateTime.of(2027,3,21,2,59,0,0,Z).toInstant();  // real
    Instant t2 = ZonedDateTime.of(2027,3,21,3, 0,0,0,Z).toInstant();  // real, one minute later
    t1.atZone(Z).plusDays(7).toInstant()   // 2027-03-28T01:59:00Z  (03:59+02:00)
    t2.atZone(Z).plusDays(7).toInstant()   // 2027-03-28T01:00:00Z  (03:00+02:00)

I ran exactly this under Java 17 and the second reading is 59 minutes **before** the first, with
`m2.isBefore(m1) == true`. Sweeping the real hour 02:00-03:30 on 2027-03-21 at +7 days gives one
regression, at real local 03:00.

Why it matters beyond the invariant: two deposits made a minute apart across that moment are stamped
up to 59 minutes out of order, so `findBySavingsAccountIdOrderByDepositedAtAscIdAsc` returns the
deposit history in an order the deposits did not happen in. The window is the real hour before local
03:00 on whichever day is N days before a spring-forward Sunday, dev profile only.

The inline comment at MovableClock.java:70-80 half-admits this ("it can repeat or skip an hour") and
is right that no record crosses a *date* — but the invariant it breaks is monotonicity, which the
comment does not mention and the class javadoc treats as non-negotiable. It also leaves a latent
flake in the three clock tests this attempt rewrote: `TheMovedClock.daysOnFrom` is used as
`daysOnFrom(realMomentBefore, n) <= reading <= daysOnFrom(realMomentAfter, n)`, and those bounds are
only a bracket if `daysOnFrom` is monotone in its real moment. In that hour it is not, and the
bracket inverts.

Please keep the calendar week — it was the right fix and criterion 4 depends on it — but get it
without giving up monotonicity. One way: work the move out as a fixed `Duration` through the calendar
*once, at the moment the clock is advanced*, and add that duration afterwards; "advance seven days"
is still a Brussels week, and the reading stays a monotone function of the real clock. Clamping so
the reading can never regress would also do. A comment explaining the trade is not enough on its
own, because the trade gives away the one property the class says it must have.

### 2. The two balances no longer sit side by side between about 400px and 599px

Expected: the money and points balances keep the arrangement they had before this ticket; the new
comment at index.css:481-484 claims exactly that — "The two money figures keep exactly the width they
had before there was a week to show".

Seen: false below 37.5rem. The base rule is now `grid-template-columns: 1fr` (index.css:475), so all
three cells stack in one column until 600px. The pre-ticket rule
`repeat(auto-fit, minmax(11rem, 1fr))` produced two tracks from about 400px of viewport upward. This
also contradicts the intent stated two lines above it in the same file — "The balances side by side:
the connection between saving and being rewarded is the point".

Reproduce: sign in as `anke.peeters@example.be`, open savings account 1, and at each width compare
the live layout with the pre-ticket rule injected into the DOM only (no file change):

    .balances { grid-template-columns: repeat(auto-fit, minmax(11rem, 1fr)) !important }
    .balances .week { display: none !important }

Measured rows occupied by the two money cells (`Saved`, `To spend`):

    viewport   now                              pre-ticket rule      verdict
    400px      1 track,  2 rows (366px)         2 tracks, 1 row      regressed
    420px      1 track,  2 rows (384px)         2 tracks, 1 row      regressed
    470px      1 track,  2 rows (430px)         2 tracks, 1 row      regressed
    520px      1 track,  2 rows (476px)         2 tracks, 1 row      regressed
    560px      1 track,  2 rows (513px)         2 tracks, 1 row      regressed
    595px      1 track,  2 rows (545px)         2 tracks, 1 row      regressed
    600px      2 tracks, 1 row  (275px ×2)      2 tracks, 1 row      same

Nothing is clipped and no figure is wrong — this is a layout the ticket did not ask to change, in the
band that covers most phones in portrait (430px) and landscape. At 520px the deposit and withdraw
forms below are still two-up while the balances above them are one-up, which reads as the row having
failed rather than as a choice. A two-column rule below 37.5rem with `.week` spanning the row would
put it back; either way, correct the comment so it says what the rule does.

### 3. The loading skeleton leaves an empty grey square in the balances row

Expected: no empty cell in the row of balances. The comment added by this very diff
(index.css:471-474) says why: "a grid that fits two of three leaves an empty square beside the third,
and an empty square in a row of balances reads as a figure that failed to load".

Seen: exactly that, in the loading state. The third skeleton `div` added at App.tsx:665-667 carries
no `week` class, so `.balances .week { grid-column: 1 / -1 }` never matches it. Between 37.5rem and
56rem the skeleton is three cells in a two-track grid: the third sits at row 2 column 1 and row 2
column 2 has no item, so `.balances`' own `rgb(var(--line) / 0.14)` shows through where a cell's
`var(--raised)` should be.

Reproduce: hold the account request open and load the page at 700px —

    page.route("**/api/savings-accounts/1", lambda route: None)

then sign in and open savings account 1. Measured cell boxes at 700px:
`(x=29,y=215,w=321)`, `(x=351,y=215,w=321)`, `(x=29,y=306,w=321)` — nothing at `(x=351,y=306)`. The
screenshot shows a grey rectangle to the right of the third bar. Same at 640px and 800px. It is
visible on every load of the page in that width band, not only under a stalled request.

Fix is one word: `className="week"` on that third skeleton div, so it spans the row like the real
week cell does.

### 4. Notes, not requirements

- `ClockConfiguration` (a dev-only affordance) now imports `streaks.SavingsWeek` to borrow the zone,
  so the clock module depends on a domain module, and "what a whole day is for the clock" is welded
  to "the zone weeks are counted in". Attempt 2 flagged this itself and the alternatives are all
  worse than a second copy of the string, so I am not asking for a change — but if ticket 03 revisits
  how weeks are counted, note that it will silently change how the development clock moves.
- `NewSavingsThisWeek.week()` is still read by nothing (`StreaksService` logs its own local `week`,
  the response drops it). Fine as ticket-03 scaffolding, as review 1 said; worth either using or
  removing in 03.
- `StreaksService.countedInto` re-scales each amount and re-renders the zone per deposit, duplicating
  normalisation `NewSavingsThisWeek` already does on the total. Scaling once in the `DepositLanded`
  mapping would keep one copy. Nit.
- Deposit refusals still leave no WARN from `io.dataroots.savingstreak` — they surface only as
  `DepositRefused` resolved by the web layer at DEBUG, while the withdrawal path does log a WARN with
  its reason. This is pre-existing on `ticket/01-points-reported-by-reason`, not introduced here, and
  the spec says this feature adds no new refusal. Out of scope for this ticket; someone should pick
  it up.
- The reversed-window guard in `depositsLandedBetween` is untested, which is right: it is unreachable
  over the one test seam this repo has.

### What is already right and should not be reworked

Everything below was exercised against the running application, and all of it passed.

- **The clipping is genuinely fixed.** I re-ran the attempt-1 measurement — a Range over every text
  node in each `dd`, `figure right edge − cell content-box right edge` — with
  `Saved € 2.002,50` / `2.052 points` / `This week € 2.052,50`, at 360, 420, 520, 560, 595, 600, 620,
  640, 700, 760, 800, 840, 860, 880, 896, 900, 940, 1000, 1100, 1280 and 1440px. **Nothing overflows
  at any width and `documentElement.scrollWidth − innerWidth == 0` throughout.** At 640px, the width
  that previously read `€ 2.002,5`, the screenshot reads `€ 2.002,50` and `€ 2.052,50 of € 50,00` in
  full; at 800px, which previously cut `of` mid-word, the phrase is whole. Money cells are 275px at
  600px, the pre-ticket width. Headroom probe: `€ 99.999,00` clears the edge by 31px at 896px and
  46px at 1280px.
- **The calendar week works, and I reached the week the last review named.** `advance {days:42}` →
  `2026-10-19T16:55:07Z`, deposit €60 → week `60.00`; `advance {days:7}` → `2026-10-26T17:55:07Z`,
  week back to `0.00 / 50.00` with money `1062.5`, points `2112` and the deposit history untouched.
  That reading is an hour later in UTC than 7×86400s would give — the 169-hour week accounted for.
- **The three boundary tests are load-bearing, not decorative.** I mutated
  `SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN` to `ZoneId.of("UTC")` and ran
  `WeeksRunMondayToSundayInBrusselsApiTest`: 3 of 5 failed, all with `expected: 30.00 but was: 50.00`
  — the Monday-early deposit falling into the ending week, in the plain case and across both clock
  changes. Reverted afterwards; tree is clean.
- **The hardened test really is hardened.** Every deposit in `ThisWeeksNewSavingsApiTest` now goes
  through `depositAccepted`, which asserts `201` before reading a body, and
  `a_week_that_has_taken_in_more_than_it_asks_for_needs_nothing_further` asserts `before + 80.00`.
- **The single read transaction is real.** The tx log holds
  `Creating new transaction with name [io.dataroots.savingstreak.web.SavingsAccountController.savingsAccount]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly`
  followed by five `Participating in existing transaction` and one commit, so `@Transactional` on the
  package-private handler does take effect.

### What I ran

- `cd backend && ./mvnw test` → `Tests run: 126, Failures: 0, Errors: 0`, BUILD SUCCESS. Matches the
  orchestrator's `checks.2.log`. New: `ThisWeeksNewSavingsApiTest` 7,
  `WeeksRunMondayToSundayInBrusselsApiTest` 5, `TheWeekMovesWithTheDevelopmentClockApiTest` 1.
- `cd frontend && npm run typecheck` → exit 0 (Node 24.16.0 first on PATH).
- Start-up line confirms the new clock:
  `application clock in use clock=MovableClock[SystemClock[Z] moved forward 0 calendar days in Europe/Brussels]`.
- Over curl, savings account 1: `0.00 / 50.00` → +12.50 → `12.50 / 37.50` → +40.00 → `52.50 / 0.00`;
  withdraw 50 → money `52.5 → 2.5`, week still `52.50`, points still `52`. Account 3: +75.00 →
  `75.00`, withdraw the whole `75.00` → money `0`, week still `75.00`. Accounts 2 and 3 stayed
  `0.00` while account 1 filled up. Points: 12.50→12, 40→40, 60→60, 0.50→0.
- Clock: `advance {days:6}` → Sunday, week still `2052.50`; `advance {days:1}` → Monday, week
  `0.00 / 50.00`, money and points unchanged. `advance {days:-7}` and `{days:0}` → 400 with
  `WARN i.d.savingstreak.clock.ClockService : clock not advanced: ...`. The clock-backwards criterion
  is only reachable from the test-supplied clock, and that test passes.
- DEBUG boundaries read out of the live log, showing the weeks tile half-open with no gap:
  `week=2026-10-19/2026-10-25 weekStartsAt=2026-10-18T22:00:00Z weekEndsAt=2026-10-25T23:00:00Z`
  (169 hours) then `week=2026-10-26/2026-11-01 weekStartsAt=2026-10-25T23:00:00Z`. Each line also
  carries `zone=Europe/Brussels` and every deposit counted, e.g.
  `counted=[deposit 1 EUR 12.50 at 2026-09-07T16:50:39.649Z (2026-09-07T18:50:39.649+02:00[Europe/Brussels])]`.
- Refusals unchanged word for word: `0`, `-5.00`, `0.001`, `abc`, `99999.00`, savings account 999
  (404), over-withdrawal (400 + WARN naming the balance). `0 ERROR` lines and `0 Completed 500` in the
  backend log; no `System.out` anywhere in `backend/src/main` or `frontend/src`.
- Page: signed in, deposited 2000.00 and then 20.00 / 12.34 / 0.50 through the form, withdrew 1000.00
  and 30.00, submitted `0`. The week cell went `€ 0,00 of € 50,00 / € 50,00 more to go` (bar 0%) →
  `€ 20,00 / € 30,00 more to go` (bar 40%) → `€ 32,34 / € 17,66 more to go` (bar 64.68%) →
  `€ 32,84 / € 17,16 more to go` while points stayed at 32, and the full case showed
  `the week has what it asks for` with `week-bar full` at 100%. A 1000.00 withdrawal moved Saved
  `€ 2.002,50 → € 1.002,50` with the week still `€ 2.052,50`. The `0` deposit gave the unchanged
  banner *"A deposit has to be an amount of more than zero, and 0 is not."* with the week untouched.
  Screenshots read at 420/520/640/700/800/860/900/1280 in light and at 1280/700 in dark: styled,
  no blank frames.
- Browser console at `.scratch/streak-bonus/logs/02-this-weeks-new-savings.review.2.browser.log`: no
  `pageerror`; the only `console:error` is the deliberate 400; the `net::ERR_ABORTED` entries are the
  page's pre-existing StrictMode `AbortController` cleanup. Vite's log holds only its start-up banner.

## Response to the review - attempt 3

All three defects are fixed, in the reviewer's numbering. Nothing in "What is already right" was
reworked.

1. **The clock reads forward again, and it is still a calendar week.** The calendar is consulted
   once, at the moment the clock is moved — the reviewer's first suggestion — and what is kept
   beside the days is the span it came to. A reading is then the real clock plus a constant, so it
   moves at exactly the speed of the real one and cannot fall back; moving again cannot turn it
   round either, because a move adds at least one calendar day and a calendar day is at least
   23 hours, while the span for one more day is at least 23 hours longer. `movedForwardTo` is where
   the calendar is read; `instant()` does no calendar arithmetic at all.

   Both halves are now asserted at the widths of the year that break them, by standing the real
   clock underneath the movable one where a test needs it (`clock/TheClockTheseTestsMove`, a bean
   holder handed to the builder by name, no stereotype annotation, so no other application picks it
   up). `TheMovedClockNeverReadsBackwardsApiTest` stands it at the reviewer's own moment — 02:59 on
   2027-03-21 Brussels, whose local time seven days on is skipped — advances a week, makes a
   deposit, lets one minute of real time pass and makes another: the second is dated exactly a
   minute later and the history comes back in the order it happened.
   `AWeekOnTheMovedClockIsACalendarWeekApiTest` stands it at 00:30 on Monday 2026-10-19, deposits
   €60, advances seven days and asserts the clock reads Monday 2026-10-26 00:30 Brussels — 169
   hours, not 168 — with the week back to `0.00 / 50.00` and the deposit untouched. Put attempt 2's
   `instant()` back and the first goes red with `expected: 2027-03-28T02:00:00Z but was:
   2027-03-28T01:00:00Z`; put a fixed 7×86400s span in and the second goes red with `expected:
   2026-10-26T00:30 but was: 2026-10-25T23:30`. Both were run that way and reverted.

   The latent flake the review named in the same section is gone too: `TheMovedClock` now hands out
   the *window* a reading can fall in (`earliestReadingOf` / `latestReadingOf`, the earlier and the
   later of the two calendar moves) rather than a pair of bounds in the order they were read, so the
   bracket cannot invert. The one single-sided bound in `MovingTheClockForwardApiTest` is a fixed
   span of a day less than the move, which holds whichever side of a clock change the move came out
   on.

2. **The two balances sit side by side again, from below where they used to.** Two columns from
   24rem with the week spanning both, three from 56rem. 24rem rather than the ~387px the old
   `auto-fit` rule needed, so no width that had them side by side has lost them. The comment now
   says what the rule does. Measured with a Range over every text node in each `dd` (figure right
   edge − cell content-box right edge) with `Saved € 1.002,50` / `2.002 points` /
   `This week € 2.002,50` at 320, 360, 375, 380, 383, 384, 386, 390, 395, 400, 410, 420, 470, 520,
   560, 595, 600, 620, 640, 700, 760, 800, 840, 860, 880, 890, 896, 900, 940, 1000, 1100, 1280 and
   1440px: **nothing overflows at any width and `documentElement.scrollWidth − innerWidth == 0`
   throughout**, and the money cells are two-up from 384px. A four-digit figure did not fit half of
   a 400px row at the old type size — the pre-ticket page hid 27px of it there — so the figure is
   now sized by the room its share of the row leaves: one ladder of three rules, a whole row, half a
   row from 24rem, a third from 56rem. The old `@media (max-width: 30rem)` override that fixed it at
   2rem is gone, because that was the rule that made the figure too wide to fit two-up. Probe with
   `€ 99.999,00`: clears the edge by 8px at 400px and 46px at 1280px.

3. **The skeleton's third cell carries `className="week"`**, so it spans the row exactly as the real
   week cell does. Measured at 400/520/640/700/800px: cells at row 1 columns 1 and 2 and the third
   spanning the full width; at 900px all three side by side. No empty square at any of them.

4. **The notes.** `countedInto` no longer re-scales: a deposit is quoted to the cent once, where it
   leaves the Deposits module (`AmountOfMoney.quotedToTheCent`, beside `asMoney`, which now uses
   it), so `NewSavingsThisWeek.DECIMAL_PLACES` is that record's own again and private. The span a
   move came to is now in the log beside the days — at the advance, at start-up, on the way back
   from a restart and when the clock is asked where it stands — so "seven days came to 169 hours" is
   greppable rather than something a reader has to get by subtracting two readings. The other notes
   are left as the review said to leave them: `ClockConfiguration` still borrows the zone,
   `NewSavingsThisWeek.week()` is still ticket-03 scaffolding, the deposit-refusal WARN is out of
   scope, and the reversed-window guard stays untested.
