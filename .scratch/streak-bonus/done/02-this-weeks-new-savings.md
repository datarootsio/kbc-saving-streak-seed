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

**Status:** done

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

## Review feedback - attempt 3

Attempt 3 fixed all three defects it was sent back for, and I verified every one of them against the
running application — see "What is already right" below and **do not rework any of it**. Ten of the
eleven criteria are met and I saw them work. What sends this back is one defect: the clock rework
that made criterion 4 correct on the *advance* path gave up monotonicity on the *restart* path, so
`MovableClock` can still read backwards — the same invariant attempt 2 was blocked on, reached
through the other caller of `moveForwardTo`.

Set-up for every reproduction below: app on a throwaway database
(`SAVING_STREAK_DB=$(mktemp -d)/s.db`) with `--logging.level.io.dataroots.savingstreak=DEBUG`, Vite
on 5173, driven with Playwright (chromium, sync API) and curl.

### 1. Blocker: a restart recomputes the span, so the clock can read an hour backwards — and land the trainer back in the week they had just left

Expected: `MovableClock` never reads backwards. Its javadoc says so (MovableClock.java:17-19) and
this attempt's own javadoc claims the property now holds in general
(MovableClock.java:36-38): *"Moving it again cannot turn it round either: a move is refused unless it
adds at least one more day (see ClockService), and one calendar day is at least twenty-three hours,
so the span only ever grows."* `ClockOnStartUp` makes the same promise in its own words
(ClockOnStartUp.java:14-15): *"which is what makes a restart in the middle of an exercise a pause
rather than a rewind."*

Seen: both claims are false on the restart path. `moveForwardTo` has two callers, not one.
`ClockService.advanceBy` (ClockService.java:69) always adds at least a day, which is what the
javadoc's argument is about. `ClockOnStartUp.putTheClockBack` (ClockOnStartUp.java:63) calls it with
**the same day count**, from a **different real moment** — and `HowFarForward.thatMany`
(MovableClock.java:151-159) reads the calendar afresh, so the span it comes to can be an hour
*shorter* than the one the running application had been using. `ClockService` never sees this call,
so nothing refuses it. The inline comment at ClockOnStartUp.java:64-66 states the recompute out loud
(*"the same number of days is an hour more or less depending on which clock changes it now spans"*)
without noticing that "an hour less" is the one thing the class forbids.

Reproduce (no application needed — this is `HowFarForward.thatMany` and its two callers, run on
their own under Java 17). Real time crossed the autumn fall-back while the application was up:

```java
ZoneId Z = ZoneId.of("Europe/Brussels");
Duration span(Instant t, long days) {            // MovableClock.java:151-159
    return Duration.between(t, t.atZone(Z).plusDays(days).toInstant());
}
Instant tMove    = ZonedDateTime.of(2026,10,20,23,30,0,0,Z).toInstant(); // advance 7 days here
Instant tRestart = ZonedDateTime.of(2026,10,25,23,30,0,0,Z).toInstant(); // restart, after the fall-back
span(tMove, 7)     // PT169H  <- what the running application was adding
span(tRestart, 7)  // PT168H  <- what ClockOnStartUp puts back
```

I ran exactly that. The reading either side of the restart:

    reading before the restart = 2026-11-02T00:30+01:00[Europe/Brussels]   week 2026-11-02
    reading after  the restart = 2026-11-01T23:30+01:00[Europe/Brussels]   week 2026-10-26
    backwards? true, by PT1H     week flipped back? true

So a restart taking one second winds the application back an hour, and `SavingsWeek.containing`
flips from the week beginning `2026-11-02` to the one beginning `2026-10-26`. That is precisely the
failure this ticket has now been reworked twice to remove — *"a trainer pressing the button for the
next week and being shown the week they were already in"* (MovableClock.java:25-26) — only reached
by restarting rather than by advancing. Deposits made after the restart are also stamped up to an
hour before ones made before it, so `findBySavingsAccountIdOrderByDepositedAtAscIdAsc` returns the
history in an order the deposits did not happen in: the same damage review 2 named.

How often: I swept 420,480 (move-moment, restart-moment) pairs across 2026 for a seven-day move
(every 10 real minutes, restart gaps of 1/6/24/72/120/144/167/168 hours). The restart read backwards
in 8,418 of them (2.0%); of those, the week flipped back in 72. Dev profile only, and it needs the
real clock to have crossed a DST transition between the move and the restart — but the reason
`ClockOffset` is persisted at all is so that a restart mid-exercise is a pause, and twice a year it
is a rewind instead.

This is new on this branch, not pre-existing. Before this ticket the span was a fixed 86 400 s per
day, computed the same way on every reading, so a restart could not change it:
`span(tMove, 7) == span(tRestart, 7) == PT168H` always. I checked that too.

Please keep the calendar week and keep the per-move span — both are right and criterion 4 depends on
them. What is missing is that the restart path has to arrive at the *same* span the running
application had, not a fresh one. Review 2 already named the two ways: **persist the span (or the
target instant) beside the days**, so `ClockOnStartUp` restores what was written down rather than
recomputing it; or **clamp**, so a restored reading can never precede the last one recorded. A
comment explaining the trade is not enough on its own, because the trade gives away the one property
the class says it must have — and today two comments claim the property is held.

Whichever way it is fixed, `MovableClock.java:36-38` and `ClockOnStartUp.java:14-15` and the inline
comment at `ClockOnStartUp.java:64-66` all need to end up saying what the code does. Right now a
reader who trusts them is misled about the invariant.

Tests: `TheMovedClockNeverReadsBackwardsApiTest` covers only the within-session case (it stands the
real clock once, advances once, and never restarts). `TheClockStaysWhereItWasMovedApiTest` does
restart, but tolerates the hour through `TheMovedClock`'s earliest/latest window, so it stays green
either way. A test for this needs the real clock stood before a fall-back, an advance, the real clock
moved past the fall-back, and then a restart — `TheClockTheseTestsMove` already gives you everything
needed to write it.

### 2. Notes, not requirements

- `frontend/src/App.tsx:665` — `className="week"` on the third skeleton cell is the right fix for the
  grid, and it works (measured below). It also opts that cell into `.week::before`
  (index.css:519-544), so during loading the row shows a `--brand-soft` top rule on the third cell
  and nothing on the first two, which are bare `<div>`s. Visible in
  `20-skeleton-w700.png`. The implementer flagged this trade themselves; either exclude the skeleton
  from that rule or give the first two cells their real classes. Cosmetic, loading state only.
- `NewSavingsThisWeek.week()` is still read by nothing (`StreaksService` logs its own local `week`,
  the response drops it). Fine as ticket-03 scaffolding, as reviews 1 and 2 said; worth either using
  or removing in 03.
- `ClockConfiguration` still borrows the zone from `streaks.SavingsWeek`. Left as reviews 1 and 2
  left it; note that if ticket 03 revisits how weeks are counted it will silently change how the
  development clock moves.
- Deposit refusals still leave no WARN from `io.dataroots.savingstreak` — they surface only as
  `DepositRefused` resolved by the web layer at DEBUG, while the withdrawal path does log a WARN with
  its reason. Pre-existing on `ticket/01-points-reported-by-reason`, out of scope here, still worth
  someone picking up.
- The reversed-window guard in `depositsLandedBetween` is still untested, which is right: it is
  unreachable over the one test seam this repo has.

### What is already right and should not be reworked

Everything below was exercised against the running application on this attempt, and all of it passed.

- **The derivation, the zone and the boundaries.** Over curl on a throwaway database, savings account
  1: `0.00 / 50.00` → +12.50 → `12.50 / 37.50` → +40.00 → `52.50 / 0.00` → +0.50 → `53.00 / 0.00`.
  DEBUG line read out of the live log, with the week, the zone, both boundaries and every deposit
  counted:
  `this week's new savings derived from the ledger savingsAccountId=1 zone=Europe/Brussels clockReads=2026-09-07T17:41:50.076135Z week=2026-09-07/2026-09-13 weekStartsAt=2026-09-06T22:00:00Z weekEndsAt=2026-09-13T22:00:00Z deposits=3 counted=[deposit 1 EUR 12.50 at 2026-09-07T17:41:21.221Z (2026-09-07T19:41:21.221+02:00[Europe/Brussels]); …] newSavings=53.00 weeklyMinimum=50.00 stillNeeded=0.00`.
  The weeks tile half-open with no gap: `weekEndsAt=2026-10-25T23:00:00Z` for the week of 2026-10-19
  is byte-for-byte the next week's `weekStartsAt`, and that week is 169 hours long.
- **Gross counting.** Withdrawing 50.00 out of 52.50 moved money `52.5 → 2.5` and left the week at
  `52.50` with points at `52`. Withdrawing the *whole* balance on account 2 (deposit 75.00, withdraw
  75.00) left money `0` and the week still `75.00`.
- **Per-account isolation, including two accounts of the same customer.** Accounts 2 and 3 stayed at
  `0.00 / 50.00` while account 1 filled up; account 1 stayed put while account 2 took 75.00.
- **The boundary tests are load-bearing, not decorative.** I mutated
  `SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN` to `ZoneId.of("UTC")` and ran
  `WeeksRunMondayToSundayInBrusselsApiTest`: 3 of 5 red, every one of them
  `[the week starting on … read at 00:30 on that Monday] expected: 30.00 but was: 50.00` — the
  Monday-early deposit falling into the ending week, in the plain case and across both clock changes.
  Reverted; tree clean.
- **The two new clock tests are load-bearing too.** Replacing the per-move calendar span with a fixed
  `Duration.ofDays(n)` turns `AWeekOnTheMovedClockIsACalendarWeekApiTest` red with
  `expected: 2026-10-26T00:30 but was: 2026-10-25T23:30`. Putting attempt 2's per-reading calendar
  arithmetic back turns `TheMovedClockNeverReadsBackwardsApiTest` red with
  `expected: 2027-03-28T02:00:00Z but was: 2027-03-28T01:00:00Z`. Both reverted; tree clean.
- **The advance path is a real calendar week, and I reached the week that proves it.**
  `advance {days:35}` (total 42) → `2026-10-19T17:42:38Z` = Mon 19:42 CEST; deposit €60 → week
  `60.00`. `advance {days:7}` → `2026-10-26T18:42:38Z` = Mon 19:42 **CET** — an hour later in UTC
  than 7×86 400 s, same Brussels local time — and the week back to `0.00 / 50.00` with money `63.0`,
  points `112` and the deposit history untouched. The span is greppable in the log, as this attempt
  added: `clock advanced byDays=7 movedForwardByDays=49 movedForwardBy=PT1177H` against the previous
  `PT1008H`, i.e. 169 hours for that week.
- **Within a single position the reading cannot fall back**, which is what attempt 2 got wrong. At
  the exact moment review 2 named — real 2027-03-21 02:59 Brussels, +7 days — the new `instant()`
  reads `2027-03-28T01:59:00Z` and, a real minute later, `2027-03-28T02:00:00Z` (PT1M forward);
  attempt 2's expression reads PT-59M at the same pair. I also brute-forced 24,486,792
  (real-moment, gap, days, step) combinations of *successive advances*: the smallest span growth
  across a move is 1,380 minutes, so no `ClockService` advance can ever rewind the clock. Only the
  restart caller can — finding 1.
- **The clipping is fixed and stays fixed.** Range over every text node in each `dd`
  (figure right edge − cell content-box right edge) at 320, 360, 375, 383, 384, 390, 400, 420, 470,
  520, 560, 595, 600, 640, 700, 760, 800, 840, 860, 880, 896, 900, 940, 1000, 1100, 1280 and 1440px:
  **every cell is negative at every width** (worst −21px, at 896-900px) and
  `documentElement.scrollWidth − innerWidth == 0` throughout. Headroom probe forcing `€ 99.999,00`
  into all three cells at 320/384/400/470/600/700/896/900/1280/1440px: worst overflow −13px, still no
  side scroll. At 640px, the width attempt 1 rendered as `€ 2.002,5`, the screenshot reads
  `€ 62,84 of € 50,00` whole.
- **The two money balances are side by side again, from below where they used to be.** Two tracks
  from 384px (24rem) with `.week` spanning both, three from 896px (56rem). Measured: 383px → one
  341px track; 384px → `174.5px 174.5px` with the week at `w350` on row 2; 900px → three tracks of
  277px. The attempt-2 regression between 400px and 599px is gone — screenshot `10-w400.png` shows
  Saved and To spend two-up with the week beneath.
- **No empty square in the loading row.** With `page.route("**/api/savings-accounts/1", lambda r: None)`
  holding the request open, the skeleton cells measure `[cls='' x17 y205 w183] [cls='' x201 y205 w183]
  [cls='week' x17 y296 w366]` at 400px, and the same shape at 520/640/700/800px; at 900px all three
  are side by side at x33/x311/x590. Nothing at the fourth position, because there is no fourth
  position.
- **The page itself.** Signed in as `anke.peeters@example.be`, opened savings account 1, and through
  the form: +20.00 → `€ 20,00 of € 50,00 / € 30,00 more to go`; +12.34 → `€ 32,34 / € 17,16 more to
  go` (bar 66%); +0.50 → `€ 32,84 / € 17,16 more to go` with points unmoved at 144; +30.00 →
  `€ 62,84 / the week has what it asks for`, `week-bar full`, `width: 100%`. A 40.00 withdrawal moved
  Saved `€ 125,84 → € 85,84` and left the week at `€ 62,84`. Money is formatted exactly as the Saved
  cell formats it. Screenshots read at 400/640/900/1280px light and 700/1280px dark: styled, no blank
  frames, nothing clipped.
- **Refusals unchanged, word for word, with the week untouched.** Over curl: `0`, `-5.00`, `0.001`,
  `abc`, `99999.00` (400 each with their existing sentences), savings account 999 → 404
  `There is no savings account 999.`, over-withdrawal → 400 plus
  `WARN i.d.s.deposits.WithdrawalsService : withdrawal rejected savingsAccountId=1 toCurrentAccountId=1 amount=9999.00 balance=3.00 reason=…`.
  On the page, submitting `0` rendered the unchanged banner *"A deposit has to be an amount of more
  than zero, and 0 is not."* with the week and the points where they were. `advance {days:-7}` and
  `{days:0}` → 400 plus `WARN i.d.savingstreak.clock.ClockService : clock not advanced: …`.
- **One read transaction per account read is real.** With `org.springframework.orm.jpa` at DEBUG:
  `Creating new transaction with name [io.dataroots.savingstreak.web.SavingsAccountController.savingsAccount]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly`
  followed by five `Participating in existing transaction`, so `@Transactional` on the package-private
  handler does take effect. Review 1's note is genuinely addressed.
- **The €50 is one constant.** `WEEKLY_MINIMUM` in `NewSavingsThisWeek` is the only `50.00` in
  `backend/src/main`, and the page never names it — it reads `balances.weeklyMinimum`.
- **The hardened test really is hardened.** Every deposit in `ThisWeeksNewSavingsApiTest` goes through
  `depositAccepted`, which asserts `201` before a body is read (line 180), and
  `a_week_that_has_taken_in_more_than_it_asks_for_needs_nothing_further` now asserts
  `before.newSavingsThisWeek().add(80.00)` rather than an absolute figure.

### What I ran

- `cd backend && ./mvnw test` → BUILD SUCCESS, `Tests run: 128, Failures: 0, Errors: 0`. Matches the
  orchestrator's `checks.3.log`. New this attempt: `TheMovedClockNeverReadsBackwardsApiTest` 1,
  `AWeekOnTheMovedClockIsACalendarWeekApiTest` 1, on top of `ThisWeeksNewSavingsApiTest` 7,
  `WeeksRunMondayToSundayInBrusselsApiTest` 5, `TheWeekMovesWithTheDevelopmentClockApiTest` 1.
- `cd frontend && npm run typecheck` → exit 0 (Node 24.16.0 first on PATH; system node is 16).
- Start-up line: `application clock in use clock=MovableClock[SystemClock[Z] moved forward 0 calendar
  days in Europe/Brussels, which is PT0S] reads=2026-09-07T17:38:38.483230Z`.
- `0` ERROR lines and `0` `Completed 500` in the backend log; the only `WARN` from
  `io.dataroots.savingstreak` was the over-withdrawal I triggered. No `System.out`, `System.err` or
  `console.log` in `backend/src/main` or `frontend/src`.
- Browser console at
  `.scratch/streak-bonus/logs/02-this-weeks-new-savings.review.3.browser.log` (166 lines): no
  `pageerror`; the only `console:error` entries are the two deliberate 400s; the `net::ERR_ABORTED`
  entries are the page's pre-existing StrictMode `AbortController` cleanup. Vite's log holds only its
  start-up banner — no transform errors.
- Three mutations, all reverted, tree clean at the end (`git status --porcelain` empty).

## Response to the review - attempt 4

The one defect is fixed, and nothing in "What is already right" was touched: the derivation, the
zone, the boundaries, gross counting, the single constant, the logging, the layout, the loading row
and the hardened tests are all as attempt 3 left them.

1. **The restart puts back the span that was written down, so it cannot come to a shorter one.**
   `ClockOffset` now carries the span beside the days — the first of the two fixes review 2 and
   review 3 both named — and `ClockOnStartUp` restores it verbatim; no caller of `moveForwardTo`
   reads the calendar any more. The calendar is consulted in exactly one place,
   `ClockService.advanceBy`, and the single `Duration` it comes to is handed to both the record and
   the running clock, so the two cannot disagree. A reading is therefore the real clock plus a
   constant on both sides of a restart, which is monotone in the real clock by construction.

   `MovableClock` also refuses a backwards span itself now, rather than trusting its callers with
   the one promise the class is built on.

   The record is two figures, so a hand-edited one has two more ways to lie, and both are refused
   with a reason: a row that says how many days but not what they came to (the shape a database
   written by an older build has after the schema update), and a span that is not what a calendar
   makes of that many days — which covers a negative one. Both exercised against the running
   application, see "What I ran".

   `ARestartDoesNotWindTheMovedClockBackApiTest` is the test review 3 asked for, at the moments it
   named: the real clock is stood on the Tuesday before the last Sunday in October 2026, the clock
   is advanced a week (169 hours), real time is moved past that Sunday's fall-back with the
   application up, a deposit is made, and the application is restarted a second later. The reopened
   clock reads a second on rather than 59 minutes back, and the second deposit comes back after the
   first. Putting attempt 3's recompute back turns both tests red with
   `expected: 2026-11-01T23:30:01Z but was: 2026-11-01T22:30:01Z` — the reviewer's hour, to the
   second. Reverted; tree clean.

   The three comments the review said were misleading now say what the code does:
   `MovableClock`'s javadoc no longer argues from "a move only ever grows the span" but from "the
   span is given to this class, and the two places that give it one agree by construction";
   `ClockOnStartUp`'s says a restart is a pause *because* the span is put back as written, and names
   what a fresh one would cost; the inline comment that stated the recompute out loud is gone with
   the recompute.

2. **The note about the loading row.** The first two skeleton cells now carry `saved` and `earned`,
   the classes they will fill, so all three wear their own coloured rule instead of only the one that
   needed a class for the grid. Measured at 400/520/640/700/800/900px: three cells, no empty square,
   `::before` colours `rgb(0, 151, 219)` / `rgb(217, 130, 0)` / `rgb(79, 189, 234)`.

The other notes are left as reviews 1-3 left them: `ClockConfiguration` still borrows the zone,
`NewSavingsThisWeek.week()` is still ticket-03 scaffolding, the deposit-refusal WARN is out of scope,
and the reversed-window guard stays untested.

## Review feedback - attempt 4

**Attempt 4 fixed the blocker it was sent back for, and I proved it two independent ways. Do not
rework the clock.** Ten of the eleven criteria are met and I saw every one of them work. What sends
this back is one defect on the page and one on the upgrade path — the first is the blocker.

Set-up for every reproduction below: app on a throwaway database
(`SAVING_STREAK_DB=$(mktemp -d)/s.db`) with `--logging.level.io.dataroots.savingstreak=DEBUG`, Vite
on 5173, driven with Playwright (chromium, sync API) and curl.

### 1. Blocker: for the first half-second the week cell says the week is done when it is not

Expected: the cell says what the week has taken in and what it still asks for. Criterion 9, and the
ticket's own framing — *"this is where the customer first sees a week as something they are part-way
through"*.

Seen: the figure goes through `Rising`'s 900 ms tween (`App.tsx:1350-1375`) while `ThisWeek`
(`App.tsx:803`, used at `App.tsx:710`) reads `balances` directly, so the bar width and the sentence
jump to their final state on the first frame. The figure is therefore below the minimum while the bar
is full and the sentence says the week needs nothing. Every load of the page, and every deposit.

Reproduce, first paint. Sign in as `anke.peeters@example.be`, put more than €50 into savings
account 1 this week, then reload and sample the cell every 100 ms:

    page.eval_on_selector(".balances .week dd", "e => e.childNodes[0].textContent")
    page.eval_on_selector(".balances .week .week-note", "e => e.textContent")
    page.eval_on_selector(".balances .week-bar", "e => e.className + ' w=' + e.querySelector('i').style.width")

What I read, with the week actually at €62,84:

    t=   0ms  '€ 0,00'  'of € 50,00'  note='the week has what it asks for'  bar=week-bar full w=100%
    t= 100ms  '€ 21,00' 'of € 50,00'  note='the week has what it asks for'  bar=week-bar full w=100%
    t= 200ms  '€ 50,80' 'of € 50,00'  note='the week has what it asks for'  bar=week-bar full w=100%
    t= 600ms  '€ 62,84' 'of € 50,00'  note='the week has what it asks for'  bar=week-bar full w=100%

`.scratch/streak-bonus/logs/02-this-weeks-new-savings.review.4.midanimation-w900.png` is that frame:
**"This week € 21,00 of € 50,00"** under a completely full deep-blue bar reading **"the week has what
it asks for"**. A trainer demonstrating what a week still needs is shown a satisfied week for the
first half second of it.

Reproduce, after a deposit. Savings account 2 with an empty week, deposit `60.00` through the form:

    before    '€ 0,00'  note='€ 50,00 more to go'              bar=week-bar w=0%
    t=   0ms  '€ 11,20' note='the week has what it asks for'   bar=week-bar full w=100%
    t= 200ms  '€ 40,03' note='the week has what it asks for'   bar=week-bar full w=100%
    t= 400ms  '€ 54,10' note='the week has what it asks for'   bar=week-bar full w=100%

Full sample in `.scratch/streak-bonus/logs/02-this-weeks-new-savings.review.4.anim.log`.

This is new on this branch: the two cells that were already there animate the same way, but neither
has derived text beside the figure to contradict, so nothing on the pre-ticket page could disagree
with itself. Held to the standard this ticket has already been held to, it is worse than the empty
grey square attempt 2 was sent back for (review 2, finding 3) — that was one loading state in one
width band; this is a sentence stating the opposite of the figure beside it, on every load and every
deposit, at every width.

The fix is the implementer's choice: drive the bar and the note off the same tweened value the figure
is showing (pass `shown` out of `Rising`, or lift the tween so all three read one number), or leave
the week figure un-tweened so the three parts of the cell are always one statement. Either way the
three parts of that cell have to agree at every frame. Please check it by sampling the cell at 100 ms
intervals across a load and across a crossing deposit, not by looking at the settled screen.

### 2. A dev database written before this attempt loses its position, and the next advance stamps records backwards

Expected: what `ClockOffset` exists for — *"which is what makes a restart in the middle of an
exercise a pause rather than a rewind"* (`ClockOnStartUp.java:17-18`).

Seen: `ddl-auto=update` adds `moved_forward_by_seconds` to an existing row as NULL,
`ClockOffset.getMovedForwardBy()` (`ClockOffset.java:51`) returns empty, and
`whyThatIsNotAPositionTheClockGoesTo` (`ClockOnStartUp.java:108`) refuses the record, so the
application comes up at the real moment. I exercised the refusal and it does say why — see "What I
ran" — and that much is a deliberate, documented trade. The part that is not covered by the trade is
what happens next: `ClockService.advanceBy` counts from `clock.movedForwardByDays()`, which is now 0.
A trainer whose row said 365 days, whose ledger is dated a year out, restarts on this branch and then
posts `advance {"days":7}` to get back — the new total is 7, not 372, and the clock lands ~358 days
*behind* the records already written. Deposits made after that are stamped before ones already on the
ledger and `findBySavingsAccountIdOrderByDepositedAtAscIdAsc` returns the history in an order the
deposits did not happen in: the exact damage reviews 2 and 3 blocked on, reached by upgrading.

The refusal avoids an error of at most one hour, twice a year. It creates one of up to a hundred
years, once, on every dev database that had ever been advanced before this attempt — including one
written by `ticket/01-points-reported-by-reason`, where `ClockOffset` already had the days column.
This repo's own `data/saving-streak.db` has no `clock_offset` table at all (checked), so nothing here
hits it, which is why this is the second point rather than the first.

The codebase already has the pattern for the other answer: `DepositsOnStartUp` /
`giveEveryDepositWhatRemainsOfIt` backfills a column an older build did not write, and
`ADatabaseWrittenBeforeThisApiTest` is the precedent for testing that. `days * 86400` for a row with
no span is wrong by at most an hour and never by a year. Whichever way it goes, decide it out loud in
the javadoc rather than leaving a reader to work out that "refused" means "and then the next advance
rewinds you".

### 3. Notes, not requirements

- `ThisWeeksNewSavingsApiTest` runs on the machine's clock and asserts `after == before + amount`.
  The class documents that the run shares one week but not that a run can *cross* one: a suite
  executing across Monday 00:00 Europe/Brussels between the two reads resets
  `newSavingsThisWeek` and fails `before + 12.50` with no reproducible cause. Seconds of exposure a
  week. `WeeksRunMondayToSundayInBrusselsApiTest` already shows how to stand a class on a fixed
  clock.
- `Rising` can render a negative figure on its first frame: `now` inside the `requestAnimationFrame`
  callback is the frame's start time and can precede the `started = performance.now()` taken in the
  effect, so `eased` goes negative. I read `Saved € -0,04` and `To spend -1 points` on one sign-in.
  Pre-existing on `ticket/01-points-reported-by-reason` and not introduced here — but the week cell
  now uses `Rising` too, so it inherits it. Out of scope; worth someone picking up with finding 1,
  since both live in the same component.
- `TheMovedClock.earliestReadingOf` / `latestReadingOf` take the min and max over the two *endpoint*
  real moments only. `daysOnFrom` jumps by an hour at a DST discontinuity, so if one falls strictly
  between the two readings the true span can sit outside that window. The real gap is milliseconds,
  so this will not be seen; noting it because the class javadoc argues the window is a bracket "either
  way", and it is a bracket for the endpoints rather than for the interval.
- `ClockConfiguration` still borrows the zone from `streaks.SavingsWeek`, a lab affordance depending
  on a domain module. Left as reviews 1-3 left it; if ticket 03 revisits how weeks are counted it
  will silently change how the development clock moves.
- `NewSavingsThisWeek.week()` is still read by nothing. Fine as ticket-03 scaffolding, as reviews 1-3
  said; worth either using or removing in 03.
- Deposit refusals still leave no WARN from `io.dataroots.savingstreak` — they surface only as
  `DepositRefused` resolved by the web layer at DEBUG, while the withdrawal path does log a WARN with
  its reason. Pre-existing on `ticket/01-points-reported-by-reason`, out of scope here, still worth
  someone picking up.
- The reversed-window guard in `depositsLandedBetween` is still untested, which is right: it is
  unreachable over the one test seam this repo has.
- `ClockOnStartUp`'s `[23h, 25h] × days` bound is looser than it reads for a large day count
  (36 500 days admits an eight-year spread), but it can never reject a genuine record and it does
  reject a sign error. The implementer named this themselves. Leave it.

### What is already right and should not be reworked

Everything below I exercised myself on this attempt, against the running application.

- **The restart fix is correct, and I proved it without using the test.** I copied the running
  application's throwaway database, hand-edited the row to a span a recompute could not produce —
  `1|7|608400`, i.e. 169 hours, where seven calendar days from 2026-09-07 are 168 — and started a
  second instance on it. It came up on the written span:
  `clock put back where it was left movedForwardByDays=7 movedForwardBy=PT169H reading=2026-09-14T19:49:52.085943Z`
  against a real clock reading `18:49:52Z`. Exactly +169 h. Attempt 3 would have come up on PT168H.
  No caller of `moveForwardTo` reads the calendar any more.
- **The new test is load-bearing, at the reviewer's own hour.** I put attempt 3's recompute back
  (`clock.moveForwardTo(days, clock.howFarForwardThatManyDaysIs(days))` at `ClockOnStartUp.java:87`)
  and ran the suite: `ARestartDoesNotWindTheMovedClockBackApiTest` went 2/2 red, both with
  `expected: 2026-11-01T23:30:01Z but was: 2026-11-01T22:30:01Z`. Reverted; tree clean.
- **The advance path cannot read backwards either, and I checked it rather than taking the javadoc's
  word.** I ran `MovableClock.howFarForwardThatManyDaysIs`'s expression on its own under Java 17 over
  two years of real moments at 7-minute steps × 16 day-counts (1 … 36 500): the span never deviates
  from 24 h/day by more than ±60 minutes, which is what makes the argument work. Then, over the same
  sweep × 8 starting positions × 6 real gaps (0 min to 7 days) × 3 step sizes, **0 advances would
  read backwards**.
- **Hand-edited records are refused with their reason, and the application comes up at the real
  moment.** Three restarts, in
  `.scratch/streak-bonus/logs/02-this-weeks-new-savings.review.4.editedrecords.log`:
  span `NULL` → `WARN … the record says it was moved 7 days but not what span those days came to …`;
  span `-604800` → `WARN … the record says 7 days came to PT-168H, and no calendar makes that many
  days anything but PT161H to PT175H …`; span `9999999` → the same sentence with `PT2777H46M39S`.
  All three reported `movedForwardByDays=0` afterwards.
- **The derivation, the zone and the boundaries.** Over curl on the throwaway database, savings
  account 1: `0.00 / 50.00` → +12.50 → `12.50 / 37.50` → +40.00 → `52.50 / 0.00`. The DEBUG line
  carries everything the answer was made of:
  `this week's new savings derived from the ledger savingsAccountId=1 zone=Europe/Brussels clockReads=2026-09-07T18:48:42.402173Z week=2026-09-07/2026-09-13 weekStartsAt=2026-09-06T22:00:00Z weekEndsAt=2026-09-13T22:00:00Z deposits=2 counted=[deposit 1 EUR 12.50 at 2026-09-07T18:48:42.299Z (2026-09-07T20:48:42.299+02:00[Europe/Brussels]); deposit 2 EUR 40.00 at …] newSavings=52.50 weeklyMinimum=50.00 stillNeeded=0.00`
  — criterion 10, met.
- **Gross counting.** Withdrawing `50.00` out of `52.50` moved money `52.5 → 2.5` and left the week
  at `52.50` with points at `52`. On the page a `40.00` withdrawal moved Saved `€ 65,34 → € 25,34`
  and left the week at `€ 62,84`.
- **Per-account isolation, including two accounts of the same customer.** Accounts 2 and 3 stayed at
  `0.00 / 50.00` while account 1 filled to `52.50`.
- **The week follows the clock forward.** `advance {days:6}` → Sunday, week still `52.50`;
  `advance {days:1}` → `2026-09-14T18:48:56.628288Z`, week `0.00 / 50.00`, money `2.5`, points `52`
  and the deposit history untouched. Log:
  `clock advanced byDays=1 movedForwardByDays=7 movedForwardBy=PT168H wasReading=… nowReading=…`.
- **And backwards, live, not only in a test.** Standing the persisted position back at `0|0` and
  restarting brought the earlier week's figure back: `newSavingsThisWeek: 52.50`,
  `stillNeededThisWeek: 0.00`, `week=2026-09-07/2026-09-13`, with money and points unmoved at
  `2.5 / 52`. At `7|604800` the same database reads `0.00 / 50.00`, `week=2026-09-14/2026-09-20`.
  Nothing is stored that could have gone stale.
  (`.scratch/streak-bonus/logs/02-this-weeks-new-savings.review.4.clockback.log`)
- **The boundary tests are load-bearing.** I mutated `SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN` to
  `ZoneId.of("UTC")` and ran the suite: 5 failures, 3 of them in
  `WeeksRunMondayToSundayInBrusselsApiTest`, every one
  `[the week starting on … read at 00:30 on that Monday] expected: 30.00 but was: 50.00` — for
  2026-03-02 (plain), 2026-03-30 (spring change) and 2026-10-26 (autumn change). Reverted; tree
  clean.
- **The €50 is one constant.** `WEEKLY_MINIMUM` in `NewSavingsThisWeek` is the only `50.00` in
  `backend/src/main` other than Bram's seeded current-account balance, and the page never names it —
  it reads `balances.weeklyMinimum`. `"Europe/Brussels"` appears once in code, in `SavingsWeek`.
- **Points are unchanged.** `12.34 → 12`, `0.50 → 0`, `7.60 → 7`, `20.00 → 20`, `30.00 → 30`; every
  `deposit accepted` line still reads `pointsByReason={BASE_ACCRUAL=n}`.
- **The layout is fixed and stays fixed.** Range over every text node in each `dd`
  (figure right edge − cell content-box right edge) at 320, 360, 384, 400, 470, 520, 595, 600, 640,
  700, 760, 800, 860, 896, 900, 1000, 1280 and 1440 px with `Saved € 25,34` / `114 points` /
  `This week € 62,84 of € 50,00`: **every cell negative at every width** (worst −21 px, at 896-900 px)
  and `documentElement.scrollWidth − innerWidth == 0` throughout. One track below 384 px, two from
  384 px with `.week` spanning both, three from 896 px.
- **No empty square in the loading row, and all three cells wear their own rule.** With
  `page.route("**/api/savings-accounts/1", lambda r: None)` holding the request open, the cells
  measure `[saved x17 y205 w183] [earned x201 y205 w183] [week x17 y296 w366]` at 400 px, the same
  shape at 520/700 px, and three side by side at 900 px. `::before` colours
  `rgb(0, 151, 219)` / `rgb(217, 130, 0)` / `rgb(79, 189, 234)`. Attempt 3's cosmetic note is
  addressed. Screenshot: `…review.4.skeleton-w700.png`.
- **Refusals unchanged, word for word, with the week untouched.** Over curl: `0`, `-5.00`, `0.001`,
  `abc`, `99999.00` (400 each with their existing sentences), savings account 999 → 404
  `There is no savings account 999.`, over-withdrawal → 400 plus
  `WARN i.d.s.deposits.WithdrawalsService : withdrawal rejected savingsAccountId=1 toCurrentAccountId=1 amount=9999.00 balance=25.34 reason=…`.
  `advance {days:-7}` and `{days:0}` → 400 plus
  `WARN i.d.savingstreak.clock.ClockService : clock not advanced: The clock only moves forward…`.
  On the page, submitting `0` rendered the unchanged banner *"A deposit has to be an amount of more
  than zero, and 0 is not."* with the week and the points where they were.
- **The hardened test really is hardened.** Every deposit in `ThisWeeksNewSavingsApiTest` goes
  through `depositAccepted`, which asserts `201` before a body is read, and
  `a_week_that_has_taken_in_more_than_it_asks_for_needs_nothing_further` asserts
  `before.newSavingsThisWeek().add(80.00)`.

### What I ran

- `cd backend && ./mvnw test` → BUILD SUCCESS, `Tests run: 130, Failures: 0, Errors: 0`, twice
  (before the mutations and again after reverting them). Matches the orchestrator's `checks.4.log`.
- `cd frontend && npm run typecheck` → exit 0 (Node 24.16.0 first on PATH; system node is 16).
- Start-up line:
  `application clock in use clock=MovableClock[SystemClock[Z] moved forward 0 calendar days in Europe/Brussels, which is PT0S] reads=2026-09-07T18:46:30.241172Z`.
- Two mutations of `backend/src/main` (attempt 3's recompute; the zone as UTC), both reverted,
  `git status --porcelain` empty afterwards. One standalone Java 17 sweep of the span arithmetic,
  outside the repo.
- Five restarts of a second instance on port 8081, on a copy of the throwaway database:
  the written-span restore, three hand-edited records, and the position stood back at `0|0`.
  Logs: `…review.4.restart.log`, `…review.4.editedrecords.log`, `…review.4.clockback.log`.
- Backend log: `0` ERROR lines and `0` `Completed 500`. No `System.out` or `System.err` in
  `backend/src/main`, no `console.log` in `frontend/src`.
- Page driven with Playwright: signed in, deposited 20.00 / 12.34 / 0.50 / 30.00, withdrew 40.00,
  submitted `0`, then measured 18 widths, light and dark, plus the held-open loading state and the
  100 ms animation sampling above. Screenshots read at 400/700/900/1280 px light and 1280 px dark:
  styled, no blank frames, nothing clipped.
- Browser console at
  `.scratch/streak-bonus/logs/02-this-weeks-new-savings.review.4.browser.log` (68 lines): **no
  `pageerror`**; the only `console:error` is the deliberate 400; the five `net::ERR_ABORTED` entries
  are the page's pre-existing StrictMode `AbortController` cleanup. Vite's log holds only its
  start-up banner — no transform errors.

## Response to the review - attempt 5

Both findings are fixed. Nothing in "What is already right and should not be reworked" was touched:
the derivation, the zone, the boundaries, gross counting, the single constant, the DEBUG line, the
per-move span, the restart fix, the two-then-three column layout and the loading row are all as
attempt 4 left them, and the clock's own arithmetic is untouched.

1. **The three parts of the week cell are now one statement, on every frame.** The whole cell is
   drawn from the figure the rise is currently showing rather than the figure it is heading for:
   `Rising`'s `format` may now return a whole piece of the screen (its return type widened from
   `string` to `ReactNode`), and `ThisWeek` takes that shown figure and the weekly minimum and draws
   the amount, "of € 50,00", the bar and the sentence from it. What the week still asks for is the
   gap up to the minimum — the same subtraction the backend publishes as `stillNeededThisWeek`,
   taken against the figure actually on the screen, and equal to the backend's figure once the rise
   has arrived (checked: the cell settles on `€ 12,34 / € 37,66 more to go` against the resource's
   `newSavingsThisWeek: 12.34, stillNeededThisWeek: 37.66`).

   `.week-bar i`'s `transition: width 0.6s` had to go with it, and that is deliberate: the width is
   now redrawn every frame from the same figure, so a transition would leave the bar trailing a
   length the figure had stopped showing — the same contradiction from the other side. The reason is
   written above the rule.

   Measured the way the review asked, but per animation frame rather than every 100 ms: a recorder
   installed before the page loads (`page.add_init_script`) samples the figure, the note, the bar's
   class and its width on every `requestAnimationFrame` and the run then checks each frame for
   agreement — the sentence and the bar's fullness against whether the figure has reached the
   minimum, the bar's width against the figure's share of the minimum (±1%), and, below the minimum,
   that figure + "more to go" adds up to € 50,00. **332 frames across five scenarios, 0 of them
   contradicting**: a load of a week over the minimum (65 frames), the crossing deposit the review
   reproduced (104), a load of a week the clock had just reset (48+52) and a load of a part-way week
   (63). The crossing frames read `€ 45,56 / € 4,44 more to go / w=91.13%`, then
   `€ 49,63 / € 0,37 more to go / w=99.25%`, then `€ 54,89 / the week has what it asks for /
   week-bar full w=100%` — the words change on the frame the figure crosses, not before it. A 100 ms
   sampling of the same deposit is in `…app.5.anim.log` above the frame counts.

   The negative first frame the review noted in the same component is gone too, since the week cell
   now inherits it: `through` is clamped at zero, because a frame's timestamp can precede the moment
   the effect read. No frame in any of the runs showed a negative figure.

2. **A dev database written before the span was recorded keeps its position.** A row that says how
   many days but not what they came to is filled in rather than refused, with the fixed 86 400
   seconds a day — which is not an approximation: the build that wrote such a row read
   `realClock.instant().plus(days, DAYS)`, so that is exactly the span it was adding, put back to the
   second. It is then written into the row, so a row is short of its span once and never again. The
   `[23h, 25h] × days` check still applies to it (24 h a day passes), and a hand-edited span is
   refused exactly as before, word for word.

   Why this rather than the refusal, out loud in three javadocs (`ClockOnStartUp`'s class comment and
   `putTheClockBack`, plus `whatThoseDaysCameToBeforeSpansWereWrittenDown`, which is the one place the
   arithmetic lives): refusing costs the days as well, and the next advance counts from
   `clock.movedForwardByDays()` — a record of a year refused and then "advance seven days" lands the
   clock 358 days behind records already on the ledger. `ClockOffset`'s field comment and
   `MovableClock`'s javadoc paragraph about where spans come from now say what the code does.

   `ADatabaseWrittenBeforeTheSpanWasRecordedApiTest` is the test, built the way
   `ADatabaseWrittenBeforeThisApiTest` builds its older database: an application is started, the
   clock advanced 10 days, the application stopped, and `moved_forward_by_seconds` dropped from the
   file. Four tests: the days are put back (10, not 0); a deposit afterwards is dated exactly
   `Duration.ofDays(10)` on from the real moment; the row is left holding 864 000; and the next
   advance reaches 17 days with a reading later than both the reading before it and the deposit
   already written. Refuse the row instead (one line put back) and all four go red.

   Exercised live as well, on a second instance on port 8081 over a throwaway database (logs
   `…app.5.upgrade.log`, `…app.5.upgraderestart.log`, `…app.5.editedrecord.log`): row `1|10|864000`
   → column dropped → restart reports
   `clock position recorded before this release given the span its days came to movedForwardByDays=10
   movedForwardBy=PT240H` and `clock put back where it was left … reading=2026-09-17T19:21:05Z`
   against a real clock at `19:21:05Z`; `advance {days:7}` → `movedForwardByDays=17`,
   `movedForwardBy=PT408H`; the deposit that follows is dated `2026-09-24T19:21:05Z` and the history
   comes back with it ahead of the `2026-09-17` one. The row then held `17|1468800`. Hand-edited to
   `7|-604800` it is still refused with
   `WARN … the record says 7 days came to PT-168H, and no calendar makes that many days anything but
   PT161H to PT175H`, and the application comes up at the real moment.

3. **The notes.** The negative-first-frame note is fixed (above). The rest are left as reviews 1-4
   left them: `ThisWeeksNewSavingsApiTest` still runs on the machine's clock, `TheMovedClock`'s
   window is still a bracket for its endpoints, `ClockConfiguration` still borrows the zone,
   `NewSavingsThisWeek.week()` is still ticket-03 scaffolding, the deposit-refusal WARN is still out
   of scope, the reversed-window guard stays untested, and `ClockOnStartUp`'s `[23h, 25h]` bound
   stays as it is.

## Review feedback - attempt 5

**Both findings from review 4 are genuinely fixed and I verified both against the running
application — do not rework the week cell or the upgrade path.** The feature itself works: ten of
the eleven criteria are met and I saw every one of them. What sends this back is one defect, and it
lands on criterion 4: `ClockService.advanceBy` works the span out as a **total from the current real
moment** instead of as an increment on the span the clock is standing on, so once real time has
crossed a clock change since the previous advance, "advance seven days" is not seven calendar days
and can land the clock back in the week it started in.

Set-up for every reproduction below: app on a throwaway database
(`SAVING_STREAK_DB=$(mktemp -d)/s.db`) with `--logging.level.io.dataroots.savingstreak=DEBUG`, Vite
on 5173, driven with Playwright (chromium, sync API) and curl.

### 1. Blocker: a second `advance {days: 7}` can move the clock 167 hours and leave it in the week it was already in

Expected, per criterion 4 and per `MovableClock`'s own javadoc (MovableClock.java:21-26): *"'advance
seven days' is asked for in order to reach the next week, and a week is Monday to Sunday in
Brussels. Seven times 86400 seconds landing at 23:30 on the Sunday is a trainer pressing the button
for the next week and being shown the week they were already in."*

Seen: exactly that, reached the other way round. `ClockService.advanceBy` (ClockService.java:71) asks
`clock.howFarForwardThatManyDaysIs(movedForwardByDays)` for the span of the **new total**, and
`MovableClock.howFarForwardThatManyDaysIs` (MovableClock.java:131-139) counts those days through the
calendar from `realClock.instant()` — *now*. The span the clock is actually standing on is thrown
away. So the clock moves forward by

    calendar(realNow, D_old + d) − calendar(realThen, D_old)

which is `d` calendar days only while `realNow ≈ realThen`. This is the trap the same class's javadoc
names two paragraphs later (MovableClock.java:41-43): *"A fresh one is the trap: the same number of
days worked out against a different real moment is an hour shorter on the far side of a clock
change."* Review 3 blocked on that sentence being false on the restart path; attempt 4 fixed the
restart path by persisting the span, and this is the third caller — the advance path itself, which
recomputes from scratch every time.

Reproduce (no application needed — these are the two expressions above, run on their own under Java
17; I ran exactly this and pasted the output):

```java
ZoneId Z = ZoneId.of("Europe/Brussels");
Duration span(Instant realNow, long days) {                      // MovableClock.java:131-139
    return Duration.between(realNow, realNow.atZone(Z).plusDays(days).toInstant());
}
Instant t1 = ZonedDateTime.of(2026,10,23,12, 0,0,0,Z).toInstant(); // 1st advance: 7 days
Duration s1 = span(t1, 7);                                         // PT169H
Instant t2 = ZonedDateTime.of(2026,11, 1,23,30,0,0,Z).toInstant(); // real time has passed the fall-back
Instant before = t2.plus(s1);                                      // what the clock reads now
Duration s2 = span(t2, 14);                                        // 2nd advance: ClockService asks for the TOTAL
Instant after  = t2.plus(s2);
```

    real at 1st move        = 2026-10-23T12:00+02:00[Europe/Brussels]
    advance 7 -> span       = PT169H   reading = 2026-10-30T12:00+01:00   week 2026-10-26
    real later              = 2026-11-01T23:30+01:00[Europe/Brussels]
    reading before 2nd move = 2026-11-09T00:30+01:00   week 2026-11-09
    advance 7 more -> span  = PT336H   reading = 2026-11-15T23:30+01:00   week 2026-11-09
    moved forward by        = PT167H
    same week as before the move? true
    reads backwards?        false

So a trainer standing at Monday 00:30 who presses "next week" is put at Sunday 23:30 of the **same**
week: `newSavingsThisWeek` does not reset, the deposits of the week they were in are still counted,
and the headline demonstration this ticket exists for shows the week not resetting. Monotonicity is
not broken (the delta is at least `d`×24h − 2h), so this is not review 2's or review 3's defect
again — it is the criterion-4 defect review 1 named, surviving in a narrower window.

How wide the window is, measured rather than guessed: I swept 210,240 (first-advance real moment,
gap to the second advance) pairs across 2026-2027 — every real hour × gaps of
1/5/13/29/53/97/167/168/169/340/721/1441 hours, two advances of 7 days each. **8,842 pairs (4.21%)
moved the clock less than 7×24 h, and 36 (0.017%) landed it in the week it started in.** It needs
real time to have crossed a DST transition between the two advances, which is what `ClockOffset`
being persisted makes ordinary: advance in October, come back in November, advance again.

The fix review 2 and review 3 both named still applies, one level up — count the increment from
where the clock **is standing**, not from the real moment:

```java
Instant reading = clock.instant();
Duration movedForwardBy = clock.movedForwardBy()
        .plus(Duration.between(reading, reading.atZone(zone).plusDays(days).toInstant()));
```

That keeps everything the previous reviews said to keep: the calendar is still consulted once per
move, the one `Duration` still goes to both the record and the clock, a restart still puts back what
was written, and a reading is still the real clock plus a constant (so still monotone — each
increment is at least 23 h per day). Note for whoever does it that `ClockOnStartUp`'s
`[23h, 25h] × days` check then bounds an accumulated span rather than a single calendar span; with
±1 h per move that is still inside the bound, but say so in the javadoc rather than leaving it to be
rediscovered.

Tests: nothing covers two advances with real time moving in between.
`AWeekOnTheMovedClockIsACalendarWeekApiTest` stands the real clock once
(`realClock.standAt(...)`, line 104) and advances once; `ARestartDoesNotWindTheMovedClockBackApiTest`
does use `letThisMuchTimePass` across the fall-back but then restarts rather than advancing again.
`TheClockTheseTestsMove` already gives you everything the test needs: `standAt` the Friday above,
`advanceBy(7)`, `letThisMuchTimePass` to Sunday 2026-11-01 23:30 Brussels, `advanceBy(7)`, then
assert the reading is Monday 2026-11-16 00:30 Brussels and that `newSavingsThisWeek` on an account
with a deposit in the week of 2026-11-09 has reset to `0.00`.

### 2. Notes, not requirements

- I checked the claim that `@Transactional(readOnly = true)` on the package-private
  `SavingsAccountController.savingsAccount` is silently ignored (proxy-based transaction management
  skipping non-public methods). **It is not ignored** — with
  `--logging.level.org.springframework.transaction=TRACE` the live log holds
  `Creating new transaction with name [io.dataroots.savingstreak.web.SavingsAccountController.savingsAccount]: PROPAGATION_REQUIRED,ISOLATION_DEFAULT,readOnly`
  followed by `Getting transaction for [...savingsAccount]` and every module read
  `Participating in existing transaction` on one `SessionImpl(1809369043<open>)`, then one
  `Completing transaction for [...savingsAccount]`. Reviews 2-4 were right; nothing to do here.
  Log kept at `…review.5.tx.log`.
- `.week-bar i` losing its `transition: width 0.6s` is the right trade and the comment above the rule
  says why. Worth knowing it means the bar no longer animates for someone who has
  `prefers-reduced-motion` set — it snaps, which is what that setting wants.
- One frame of the rise can read `€ 49,99 … € 0,00 more to go` with the bar not yet full, because
  `euros.format(weeklyMinimum - shown)` rounds a sub-cent gap down to zero. Sub-cent, at most one
  frame, and my per-frame checker (±2 cents) did not flag it in 364 frames. Cosmetic.
- `ClockOnStartUp`'s `SHORTEST/LONGEST_A_CALENDAR_DAY_GETS` (23 h/25 h) is a second place that
  encodes what `SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN` implies, while `SavingsWeek` documents itself
  as the one place that decision is made. Change the zone to one with a two-hour transition and a
  legitimate span is refused on the next restart. Review 4 said leave it; still true, still worth
  knowing.
- `NewSavingsThisWeek.week()` is read by nothing (`grep -rn '\.week()' backend/src` finds no caller).
  Fine as ticket-03 scaffolding, as reviews 1-4 said; use it or drop it in 03.
- `ClockConfiguration` still borrows the zone from `streaks.SavingsWeek`. Left as reviews 1-4 left it.
- Deposit refusals still leave no WARN from `io.dataroots.savingstreak` — they surface as
  `DepositRefused` resolved by the web layer at DEBUG, while withdrawals do WARN with their reason.
  Pre-existing on `ticket/01-points-reported-by-reason`, out of scope here.
- `ThisWeeksNewSavingsApiTest` still runs on the machine's clock and asserts `after == before +
  amount`; a suite crossing Monday 00:00 Brussels between the two reads fails with no reproducible
  cause. Seconds of exposure a week. As review 4 said.

### What is already right and should not be reworked

All of this I exercised myself on this attempt, against the running application.

- **Review 4's blocker is fixed: the three parts of the week cell agree on every frame.** I installed
  a recorder before the page loaded (`page.add_init_script`) that samples the figure, the "of € 50,00"
  phrase, the sentence, the bar's class and the bar's width on every `requestAnimationFrame`, then
  checked each frame four ways: the sentence and the bar's fullness against whether the figure has
  reached €50, the bar's width against the figure's share of €50 (±1%), and, below €50, that
  figure + "more to go" adds up to €50. **364 frames across six scenarios, 0 contradicting.** The
  first-paint case review 4 reproduced now reads
  `€ 0,00 / "€ 50,00 more to go" / week-bar w=0%` on frame one and
  `€ 180,00 / "the week has what it asks for" / week-bar full w=100%` on the last, with everything in
  between agreeing. Scenarios: load of an empty week (52), the crossing deposit of 60.00 (66, 72),
  load of a week over the minimum (47, 57), after a withdrawal (74), a part-way deposit of 12.34
  (68, settling on `€ 12,34 / € 37,66 more to go / w=24.68%` against the resource's
  `newSavingsThisWeek: 12.34, stillNeededThisWeek: 37.66`), and a dark-mode load (52). No negative
  first frame in any of them.
- **Review 4's second finding is fixed: a database written before the span was recorded keeps its
  position, and the next advance counts from it.** On a copy of the running throwaway database I set
  the row to `1|10|NULL` (the shape `ddl-auto=update` leaves) and started a second instance on 8081:
  `clock position recorded before this release given the span its days came to movedForwardByDays=10
  movedForwardBy=PT240H`, then `clock put back where it was left movedForwardByDays=10
  movedForwardBy=PT240H reading=2026-09-17T19:28:27Z` against a real clock reading `19:28:30Z`. The
  endpoint reported `{"movedForwardByDays":10,"now":"2026-09-17T19:28:30.934315Z"}`, the row was
  completed to `1|10|864000`, and `advance {days:7}` reached
  `{"movedForwardByDays":17,"now":"2026-09-24T19:28:30.968946Z"}` — 17, not 7, so the "next advance
  lands a year behind the ledger" damage is gone. Hand-edited to `7|-604800` the row is still refused
  with `WARN i.d.savingstreak.clock.ClockOnStartUp : clock not put back: the record says 7 days came
  to PT-168H, and no calendar makes that many days anything but PT161H to PT175H, so it is left at
  the real moment` and the application comes up at the real moment. Logs: `…review.5.legacyrow.log`,
  `…review.5.editedrow.log`.
- **The derivation, the zone, the boundaries and the DEBUG line.** Over curl on the throwaway
  database, savings account 1: `0.00 / 50.00` → +12.50 → `12.50 / 37.50` → +40.00 → `52.50 / 0.00`.
  The line carries everything the answer was made of:
  `this week's new savings derived from the ledger savingsAccountId=1 zone=Europe/Brussels clockReads=2026-09-07T19:27:09.817411Z week=2026-09-07/2026-09-13 weekStartsAt=2026-09-06T22:00:00Z weekEndsAt=2026-09-13T22:00:00Z deposits=2 counted=[deposit 1 EUR 12.50 at 2026-09-07T19:27:08.708Z (2026-09-07T21:27:08.708+02:00[Europe/Brussels]); deposit 2 EUR 40.00 at 2026-09-07T19:27:08.928Z (2026-09-07T21:27:08.928+02:00[Europe/Brussels])] newSavings=52.50 weeklyMinimum=50.00 stillNeeded=0.00`
  — criterion 10, met (36 such lines in the session). `DepositsService` logs the window it was asked
  about beside it: `deposits that landed in a stretch of time savingsAccountId=1 from=2026-09-06T22:00:00Z until=2026-09-13T22:00:00Z deposits=2`.
- **The weeks tile half-open with no gap, and DST is in the zone rather than in the arithmetic.**
  Boundaries read out of the live log after walking the clock forward:
  `week=2026-10-19/2026-10-25 weekStartsAt=2026-10-18T22:00:00Z weekEndsAt=2026-10-25T23:00:00Z`
  (169 h) and then `week=2026-10-26/2026-11-01 weekStartsAt=2026-10-25T23:00:00Z` — byte-for-byte the
  previous week's end — and `week=2027-03-22/2027-03-28 weekStartsAt=2027-03-21T23:00:00Z
  weekEndsAt=2027-03-28T22:00:00Z` (167 h). Every boundary is Brussels local midnight.
- **The boundary tests are load-bearing, not decorative.** I mutated
  `SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN` to `ZoneId.of("UTC")` and ran
  `WeeksRunMondayToSundayInBrusselsApiTest`: 3 of 5 red, every one
  `[the week starting on … read at 00:30 on that Monday] expected: 30.00 but was: 50.00` — for
  2026-03-02 (plain), 2026-03-30 (spring change) and 2026-10-26 (autumn change), i.e. the
  Monday-early deposit falling into the ending week. Reverted; full suite green again afterwards and
  `git status --porcelain` empty. This is the only proof of criterion 6 available, since the endpoint
  only moves the clock forward from the real moment.
- **Gross counting.** Withdrawing the whole `52.50` off account 1 moved money `52.5 → 0` and left the
  week at `52.50` with points at `52`. On the page a `40.00` withdrawal moved Saved
  `€ 180,00 → € 140,00` and left the week at `€ 180,00`.
- **Per-account isolation, including two accounts of the same customer.** Anke holds savings accounts
  1 and 2. A `75.00` deposit into account 2 left account 1 at `52.50` and Bram's account 3 at
  `0.00 / 50.00`; account 1 filling up left both others where they were.
- **The week follows the clock forward in the ordinary case.** `advance {days:6}` → Sunday, week
  still `52.50`; `advance {days:1}` → `2026-09-14T19:27:25Z`, week `0.00 / 50.00` on both of Anke's
  accounts, money `0`/`75`, points `52`/`75` and the deposit history untouched. Across the autumn
  change: 42 days reads `2026-10-19T19:34:33Z` (Mon 21:34 CEST) and 49 days reads
  `2026-10-26T20:34:33Z` (Mon 21:34 CET) — an hour later in UTC, so that advance really was a
  calendar week. It is only the composition of two advances with real DST in between that fails
  (finding 1).
- **Advancing the clock backwards restores the earlier week's figure.** The endpoint refuses
  backwards (`advance {days:-7}` and `{days:0}` → 400 plus
  `WARN i.d.savingstreak.clock.ClockService : clock not advanced: The clock only moves forward. Ask
  for at least one day, not -7.`), so I stood the persisted position back by hand: with the clock at
  7 days and both weeks reading `0.00`, I copied the database, set `clock_offset` to `0|0` and
  started a second instance. It came up `clock put back where it was left movedForwardByDays=0
  movedForwardBy=PT0S` and account 1 read `newSavingsThisWeek: 52.50, stillNeededThisWeek: 0.00`,
  `week=2026-09-07/2026-09-13`, with money `0` and points `52` unmoved — the earlier week's figure
  back, nothing stored that could have gone stale. Log: `…review.5.clockback.log`.
- **The €50 is one constant.** `WEEKLY_MINIMUM` in `NewSavingsThisWeek` is the only `50.00` in
  `backend/src/main` (the other grep hit is Bram's seeded `1150.00`), the page never names it — it
  reads `account.balances.weeklyMinimum` — and `"Europe/Brussels"` appears once in code, in
  `SavingsWeek`.
- **The page.** Signed in as `anke.peeters@example.be`, opened savings account 1, deposited 60.00
  twice and 2000.00, withdrew 40.00, submitted `0`, and deposited 12.34 into account 2. The week cell
  reads `€ 2.180,00` / `of € 50,00` / a full bar / `the week has what it asks for`, formatted exactly
  as the Saved cell formats money. Screenshots read at 400/700/900/1280 px light and 1280 px dark:
  styled, no blank frames, nothing clipped. The refused `0` deposit rendered the unchanged banner
  *"A deposit has to be an amount of more than zero, and 0 is not."* with the week and the points
  where they were.
- **Nothing clips, at any width.** A Range over every text node in each `dd`
  (figure right edge − cell content-box right edge, so positive is hidden) with
  `Saved € 2.140,00` / `2.232 points` / `This week € 2.180,00 of € 50,00` at 320, 360, 384, 400, 470,
  520, 595, 600, 640, 700, 760, 800, 860, 896, 900, 1000, 1280 and 1440 px: **every cell negative at
  every width** (worst −6 px, at 320 px) and `documentElement.scrollWidth − innerWidth == 0`
  throughout. One track below 384 px, two from 384 px with `.week` spanning both, three from 896 px.
- **No empty square in the loading row, and all three cells wear their own rule.** With
  `page.route("**/api/savings-accounts/1", lambda r: None)` holding the request open:
  `[saved x17 y205 w183] [earned x201 y205 w183] [week x17 y296 w366]` at 400 px,
  `[saved x29 w321] [earned x351 w321] [week x29 y306 w642]` at 700 px, and three side by side at
  x33/x311/x590 at 900 px. `::before` colours `rgb(0, 151, 219)` / `rgb(217, 130, 0)` /
  `rgb(79, 189, 234)`. Screenshot `…review.5.skeleton-w700.png`.
- **Points are unchanged: one per whole euro.** Every `deposit accepted` line in the session reads
  `pointsByReason={BASE_ACCRUAL=n}` — `12.50 → 12`, `12.34 → 12`, `40.00 → 40`, `60.00 → 60`,
  `75.00 → 75`, `2000.00 → 2000`.

### What I ran

- `cd backend && ./mvnw test` → BUILD SUCCESS, `Tests run: 134, Failures: 0, Errors: 0`, twice
  (before the zone mutation and again after reverting it). Matches the orchestrator's `checks.5.log`.
- `cd frontend && npm run typecheck` → exit 0 (Node 24.16.0 first on PATH; system node is 16).
- Start-up line on the orchestrator's instance:
  `application clock in use clock=MovableClock[SystemClock[Z] moved forward 0 calendar days in Europe/Brussels, which is PT0S] reads=2026-09-07T19:24:49.849660Z`.
- Over curl against localhost:8080: the deposit/withdrawal/isolation sequence above, the clock
  advances (6, 1, 35, 7, 147 days) and the two clock refusals. Transcript at `…review.5.curl.log`.
- Four extra instances on port 8081 over copies of the throwaway database: the position stood back at
  `0|0`, the legacy `10|NULL` row and its follow-up advance, the hand-edited `7|-604800` row, and one
  with `org.springframework.transaction` at TRACE to settle the `@Transactional` question. Logs
  `…review.5.clockback.log`, `…review.5.legacyrow.log`, `…review.5.editedrow.log`, `…review.5.tx.log`.
  All four killed; port 8081 free; only the orchestrator's instance left running.
- One zone mutation of `backend/src/main`, reverted; `git status --porcelain` empty.
- Two standalone Java 17 runs of `MovableClock.howFarForwardThatManyDaysIs`'s expression outside the
  repo: the hand-picked case in finding 1, and the 210,240-pair sweep.
- Backend log `…app.5.backend.log`: `0` ERROR lines, `0` `Completed 500`, and the only WARNs from
  `io.dataroots.savingstreak` are the two clock refusals I triggered. No `System.out`/`System.err` in
  `backend/src/main`, no `console.*` in `frontend/src`.
- Page driven with Playwright (chromium, sync API): the scenarios above, 18 widths, light and dark,
  the held-open loading state at 400/700/900 px, and the per-frame recorder.
  Browser console at `…review.5.browser.log` (42 lines): **no `pageerror`**; the only `console:error`
  is the deliberate 400 from the refused `0` deposit; the `net::ERR_ABORTED` entries are the page's
  pre-existing StrictMode `AbortController` cleanup. Vite's log (`…app.5.frontend.log`) holds only
  its start-up banner — no transform errors.

## Response to the review - attempt 6

The one finding is fixed. Nothing in "What is already right and should not be reworked" was
touched: the derivation, the zone, the boundaries, gross counting, the single constant, the DEBUG
line, the week cell, the two-then-three column layout, the loading row and the upgrade path are all
as attempt 5 left them. The change is three files in `clock/` and one new test.

1. **An advance is now an increment on the span the clock is standing at, not a fresh total.**
   `MovableClock.howFarForwardThatManyDaysIs(total)` — which counted the whole span from
   `realClock.instant()` — is now `howFarForwardThatManyMoreDaysIs(days)`, which counts the days
   asked for from the moment the clock is *reading* and adds the answer to the span it is standing
   on. `ClockService.advanceBy` passes `days` rather than `alreadyMovedBy + days`. That is exactly
   the fix reviews 2, 3 and 5 named, one level up from where review 3 applied it.

   Everything the earlier reviews said to keep is kept: the calendar is consulted once per move
   (one call, inside `howFarForwardThatManyMoreDaysIs`), the one `Duration` still goes to both
   `ClockOffset` and `MovableClock.moveForwardTo`, a restart still puts back what was written, and a
   reading is still the real clock plus a constant — the span only ever grows, because an increment
   of `d` days is at least 23`d` hours, so the clock is still monotone. The position is read out of
   the `AtomicReference` once so that the span the increment is added to is the same span the reading
   it was counted from was made of.

   As the review asked, `ClockOnStartUp`'s `[23h, 25h] × days` check is now documented as bounding
   an accumulated span rather than one calendar answer, with the reason it still holds: an increment
   of `d` days is `24d` hours give or take one, and a move is at least a day, so across `D` recorded
   days there are at most `D` of those hours to gain or lose and the sum stays in `[23D, 25D]`.
   Observed: a 169 h move plus a 168 h move records `14|1213200` (PT337H), and a restart on that row
   is accepted — `clock put back where it was left movedForwardByDays=14 movedForwardBy=PT337H`.

   `MovableClock`'s class javadoc paragraph about where spans come from now says that a fresh answer
   is a trap on the move path as well as the restart path, and points at the new method.

   A DEBUG line beside the move writes the increment out on its own, so the arithmetic is readable
   without subtracting two totals:
   `clock move counted through the calendar from the reading days=7 reading=2026-10-18T19:56:59.868178Z thisMoveAdds=PT169H alreadyMovedBy=PT984H movedForwardBy=PT1153H`.
   The `clock asked to advance` line carries `alreadyMovedBy=` too, since the reading the days are
   counted from is the real moment plus that span.

   **Test.** `ASecondWeekOnTheMovedClockIsAlsoACalendarWeekApiTest` (in `weeklysavings/`, beside the
   one-move test) does what the review specified: real time stood at Friday 2026-10-23 12:00
   Brussels, `advance {days:7}` (reading Friday 2026-10-30 12:00, span PT169H), real time stood at
   Sunday 2026-11-01 23:30 Brussels — past the fall-back, with the application up — the reading now
   Monday 2026-11-09 00:30, a deposit of 30.00 into that week, then `advance {days:7}`. It asserts
   the reading is Monday 2026-11-16 00:30 Brussels, that the move added exactly 168 h (seven calendar
   days on from where the clock was reading), that the total is 14 days, and that
   `newSavingsThisWeek` is back to `0.00` with `stillNeededThisWeek` equal to the minimum — plus that
   the money, the points and the deposit's own timestamp did not move. **Put the old arithmetic back
   (both lines) and it is red:** `expected: 2026-11-16T00:30 but was: 2026-11-15T23:30`, which is the
   review's defect verbatim. Reverted; suite green.

   **Exercised live too.** With real time frozen the two arithmetics are mathematically identical —
   when real time has not moved, `calendar(realNow, total)` *is* the standing span plus the
   increment — so the endpoint alone cannot show the difference, which is why the review reproduced
   it outside the application. What it can show is a clock standing on a span that was not worked out
   at the current real moment, which is what a restart across a clock change leaves. So: a copy of
   the throwaway database with `clock_offset` hand-written to `1|7|608400` (7 days that came to 169 h
   — a span only a fall-back produces, and one this real moment would never compute), started on
   8081. It came up `clock put back where it was left movedForwardByDays=7 movedForwardBy=PT169H
   reading=2026-09-14T20:57:16Z` (Monday 22:57 Brussels), a 30.00 deposit read
   `newSavingsThisWeek: 30.00, stillNeededThisWeek: 20.00`, and `advance {days:7}` reached
   `{"movedForwardByDays":14,"now":"2026-09-21T20:57:20Z"}` — Monday 2026-09-21 22:57 Brussels, 168 h
   on, with `thisMoveAdds=PT168H alreadyMovedBy=PT169H movedForwardBy=PT337H` in the log and the
   account back to `0.00 / 50.00`. On the old arithmetic that move is PT336H, the reading is Sunday
   2026-09-21 21:57 and the week still reads 30.00. Logs `…app.6.twoadvances.log`,
   `…app.6.handwrittenspan.log`, `…app.6.restart.log`.

2. **The notes.** Left as reviews 1-5 left them, except the `[23h, 25h]` bound, which review 5 asked
   to have documented and now is. `NewSavingsThisWeek.week()` is still unread ticket-03 scaffolding,
   `ClockConfiguration` still borrows the zone from `streaks.SavingsWeek`, deposit refusals still
   leave no WARN of their own (pre-existing, ticket 01), `ThisWeeksNewSavingsApiTest` still runs on
   the machine's clock, and the sub-cent frame in the rise is still cosmetic. `.week-bar i` and the
   page were not touched at all.

## Verified

Reviewed on attempt 6, on `ticket/02-this-weeks-new-savings` (everything it adds on top of
`ticket/01-points-reported-by-reason`). **All eleven criteria met and seen working.** Attempt 6's one
change — an advance counted from where the clock is standing rather than from the real moment — is
correct, and I proved it three independent ways rather than taking the test's word for it.

### Checks

- `cd backend && ./mvnw test` → BUILD SUCCESS, `Tests run: 135, Failures: 0, Errors: 0`, twice
  (before my mutations and again after reverting them). Matches the orchestrator's `checks.6.log`.
- `cd frontend && npm run typecheck` → exit 0 (Node 24.16.0 first on PATH; system node is 16).
- `git status --porcelain` empty at the end.

### The blocker review 5 named is fixed, three ways

1. **The new test is load-bearing.** I put attempt 5's arithmetic back — `ClockService.java:83`
   passing the new total, and `MovableClock.howFarForwardThatManyMoreDaysIs` counting the whole span
   from `realClock.instant()` — and ran the clock suite:
   `ASecondWeekOnTheMovedClockIsAlsoACalendarWeekApiTest` went red with
   `expected: 2026-11-16T00:30 (java.time.LocalDateTime)`, which is review 5's defect verbatim. The
   other four clock tests stayed green under the same mutation, confirming review 5's point that
   nothing else covered this. Reverted; tree clean.
2. **Live, over the endpoint.** With real time frozen the two arithmetics coincide, so I reproduced
   the condition a restart across a clock change leaves: a copy of the throwaway database with
   `clock_offset` hand-written to `1|6|526800` (6 days that came to 146 h 20 m — a span the current
   real moment could not compute), started on port 8081. It came up
   `clock put back where it was left movedForwardByDays=6 movedForwardBy=PT146H20M
   reading=2026-09-13T22:25:35Z` = **Monday 2026-09-14 00:25 Brussels**, and account 1 read
   `newSavingsThisWeek: 30.00, stillNeededThisWeek: 20.00`. `POST /api/dev/clock/advance {"days":7}`
   logged `clock move counted through the calendar from the reading days=7 thisMoveAdds=PT168H
   alreadyMovedBy=PT146H20M movedForwardBy=PT314H20M` and landed on **Monday 2026-09-21 00:25
   Brussels**, with the account back to `0.00 / 50.00`. On the old arithmetic the same move is
   `calendar(realNow, 13 days)` = PT312H, landing **Sunday 2026-09-20 22:25 Brussels — the same
   week, no reset**; I computed both sides side by side to confirm. Log
   `…review.6.twoadvances.log`.
3. **Swept, not argued.** I ran `MovableClock.howFarForwardThatManyMoreDaysIs`'s expression on its
   own under Java 17 over two years of real moments at 37-minute steps × 8 first-move sizes
   (1…3650 days) × 6 real gaps (0 s to 6 months) × 3 second-move sizes — **4,318,320 combinations**:
   **0 spans that shrank** (so no advance can rewind the clock), **0 moves that were not exactly the
   days asked for counted through the Brussels calendar from the reading**, and **0 accumulated
   spans outside the `[23h, 25h] × days` bound** `ClockOnStartUp` checks. Attempt 6's new javadoc
   claim about that bound holds.

### The eleven criteria, as exercised

App on the orchestrator's throwaway database (`…app.6.backend.log`, `io.dataroots.savingstreak` at
DEBUG), Vite on 5173, driven with curl and Playwright (chromium, sync API).

1. **The resource reports it.** `GET /api/savings-accounts/1` →
   `{"newSavingsThisWeek":0.00,"weeklyMinimum":50.00,"stillNeededThisWeek":50.00}` on all three
   seeded accounts.
2. **A deposit raises it by its full amount, immediately.** +12.50 → `12.50 / 37.50`; +40.00 →
   `52.50 / 0.00`.
3. **A withdrawal leaves it unchanged, however large.** Withdrawing the *whole* `52.50` moved money
   `52.5 → 0` and left the week at `52.50` with points at `52`. On the page a `40.00` withdrawal
   moved Saved `€ 220,68 → € 180,68` and left the week at `€ 125,68`.
4. **Advancing into the next week returns it to zero, with no deposit and no job.**
   `advance {days:6}` → Sunday, week still `112.50`; `advance {days:1}` → `2026-09-14T20:04:02Z`,
   week `0.00 / 50.00`, money `60`, points `112` and the deposit history untouched. Across the
   autumn change: `advance {days:35}` → `2026-10-19T20:04:14Z` (Mon 22:04 CEST), deposit €45 → week
   `45.00`; `advance {days:7}` → `2026-10-26T21:04:14Z` (Mon 22:04 **CET**) with
   `thisMoveAdds=PT169H` in the log, and the week back to `0.00 / 50.00`. Plus the composed
   two-advance case above.
5. **The clock backwards restores the earlier week's figure.** The endpoint refuses backwards, so I
   copied the database, set `clock_offset` to `0|0` and started a second instance:
   `clock put back where it was left movedForwardByDays=0 movedForwardBy=PT0S`, and account 1 read
   `newSavingsThisWeek: 112.50, stillNeededThisWeek: 0.00`, `week=2026-09-07/2026-09-13`, with money
   `135` and points `187` unmoved — the DEBUG line naming all three deposits it summed. Nothing
   stored that could have gone stale. Log `…review.6.clockback.log`. Also covered by
   `WeeksRunMondayToSundayInBrusselsApiTest.the_clock_moved_back_reports_the_earlier_weeks_figure_again`.
6. **23:30 Sunday / 00:30 Monday in Brussels, including a DST change.** The endpoint only moves whole
   days from the real moment, so the only proof available is the test — and it is a real one. I
   mutated `SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN` to `ZoneId.of("UTC")` and ran
   `WeeksRunMondayToSundayInBrusselsApiTest`: **3 of 5 red**, every one
   `[the week starting on … read at 00:30 on that Monday] expected: 30.00` — for 2026-03-02 (plain),
   2026-03-30 (spring change) and 2026-10-26 (autumn change), i.e. the Monday-early deposit falling
   into the ending week. The test also asserts the two deposits share a UTC date, so it cannot pass
   against an application that simply used UTC weeks. Reverted; suite green again. Corroborated in
   the live log, where the weeks tile half-open with no gap and DST is in the zone rather than in the
   arithmetic: `week=2026-10-19/2026-10-25 weekStartsAt=2026-10-18T22:00:00Z
   weekEndsAt=2026-10-25T23:00:00Z` (169 h) followed by `week=2026-10-26/2026-11-01
   weekStartsAt=2026-10-25T23:00:00Z` — byte-for-byte the previous week's end.
7. **Each account reports only its own.** A `75.00` deposit into Anke's *second* savings account left
   account 1 at `125.68` and Bram's account 3 at `0.00 / 50.00`; account 1 filling up left both
   others where they were.
8. **The €50 is one constant.** `grep -rn '50\.00' backend/src/main` finds exactly two hits:
   `NewSavingsThisWeek.WEEKLY_MINIMUM` and Bram's seeded `1150.00`. `WEEKLY_MINIMUM` has three
   references, all inside that record. The page never names it — it reads
   `account.balances.weeklyMinimum`. `"Europe/Brussels"` appears once in code, in `SavingsWeek`.
9. **The page shows the week beside the balances, formatted the way money already is.** Signed in as
   `anke.peeters@example.be`, opened savings account 1, deposited 20.00 / 12.34 / 0.50 / 30.00,
   withdrew 40.00 and submitted `0`. The cell went `€ 0,00 of € 50,00 / € 50,00 more to go` (bar 0%)
   → `€ 20,00 / € 30,00 more to go` (40%) → `€ 32,34 / € 17,66 more to go` (64.68%) → `€ 32,84 /
   € 17,16 more to go` (65.68%, points unmoved at 219) → `€ 62,84 / the week has what it asks for`
   (`week-bar full`, 100%). `euros.format` is the same formatter the Saved cell uses. A Range over
   every text node in each `dd` (figure right edge − cell content-box right edge, positive is
   hidden) at 320, 360, 383, 384, 400, 470, 520, 595, 600, 640, 700, 760, 800, 860, 895, 896, 900,
   1000, 1280 and 1440 px with `Saved € 180,68` / `311 points` / `This week € 125,68 of € 50,00`:
   **every cell negative at every width** (worst −16 px, at 1000 px) and
   `documentElement.scrollWidth − innerWidth == 0` throughout. One track below 384 px, two from
   384 px with `.week` spanning the row, three from 896 px. Screenshots read at 400/700/900/1280 px
   light and 1280 px dark: styled, no blank frames, nothing clipped. The loading row measures
   `[saved x29 y215 w321] [earned x351 y215 w321] [week x29 y306 w642]` at 700 px — three cells, no
   empty square, each with its own coloured rule.
   **Review 4's blocker stays fixed:** a recorder installed with `page.add_init_script` sampled the
   figure, the sentence, the bar's class and its width on every `requestAnimationFrame` across five
   scenarios and I checked each frame four ways (sentence vs. whether the figure has reached €50, bar
   fullness likewise, bar width against the figure's share of €50 ±1.5%, and figure + "more to go"
   summing to €50). **269 frames, 0 contradicting**, and no negative first frame. First paint climbs
   `€ 0,00 / € 50,00 more to go / 0%` → `€ 2,58 / € 47,42 more to go / 5.16%` → … →
   `€ 62,84 / the week has what it asks for / full 100%`.
10. **DEBUG shows the boundaries, the zone and the deposits counted.** 44 such lines in the session,
    one per read, e.g.
    `this week's new savings derived from the ledger savingsAccountId=1 zone=Europe/Brussels clockReads=2026-10-26T21:10:50.331307Z week=2026-10-26/2026-11-01 weekStartsAt=2026-10-25T23:00:00Z weekEndsAt=2026-11-01T23:00:00Z deposits=8 counted=[deposit 6 EUR 20.00 at 2026-10-26T21:08:52.989Z (2026-10-26T22:08:52.989+01:00[Europe/Brussels]); …] newSavings=125.68 weeklyMinimum=50.00 stillNeeded=0.00`
    — the amounts add up by hand, and each deposit is written both as the application recorded it and
    as a customer in Brussels would read it. `DepositsService` logs the window it was asked about
    beside it (44 lines):
    `deposits that landed in a stretch of time savingsAccountId=1 from=2026-09-06T22:00:00Z until=2026-09-13T22:00:00Z deposits=0`.
11. **Points unchanged: one per whole euro.** Every `deposit accepted` line reads
    `pointsByReason={BASE_ACCRUAL=n}` — `12.50 → 12`, `12.34 → 12`, `0.50 → 0`, `7.60 → 7` (test),
    `20.00 → 20`, `30.00 → 30`, `40.00 → 40`, `60.00 → 60`, `75.00 → 75`.

### Refusals and the upgrade path

- Over curl, every existing sentence unchanged and the week untouched: `0`, `-5.00`, `0.001`, `abc`,
  `99999.00` (400 each), savings account 999 → 404 `There is no savings account 999.`,
  over-withdrawal → 400 plus
  `WARN i.d.s.deposits.WithdrawalsService : withdrawal rejected savingsAccountId=1 toCurrentAccountId=1 amount=9999.00 balance=180.68 reason=…`.
  `advance {days:0}`, `{days:-7}` and `{days:36501}` → 400 plus
  `WARN i.d.savingstreak.clock.ClockService : clock not advanced: …` naming each reason.
- On the page, submitting `0` rendered the unchanged banner *"A deposit has to be an amount of more
  than zero, and 0 is not."* with the week and the points where they were.
- **A database written before the span was recorded keeps its position** (review 4's second finding,
  fixed in attempt 5, re-checked here): row set to `10|NULL`, second instance on 8081 came up
  `clock position recorded before this release given the span its days came to movedForwardByDays=10
  movedForwardBy=PT240H` then `clock put back where it was left … reading=2026-09-17T20:14:32Z`, the
  endpoint reported `movedForwardByDays: 10`, the row was completed to `1|10|864000`, and
  `advance {days:7}` reached **17** days / `1468800` s (PT408H) — so the "next advance lands a year
  behind the ledger" damage is gone. Log `…review.6.legacyrow.log`.
- **A hand-edited span is still refused with its reason.** Row `7|-604800` →
  `WARN i.d.savingstreak.clock.ClockOnStartUp : clock not put back: the record says 7 days came to
  PT-168H, and no calendar makes that many days anything but PT161H to PT175H, so it is left at the
  real moment 2026-09-07T20:15:22.818427Z`, and the application came up at the real moment
  (`movedForwardByDays: 0`) with the row untouched. Log `…review.6.editedrow.log`.

### Logging and console

- Backend log: **0 ERROR lines, 0 `Completed 500`**. The only WARNs from
  `io.dataroots.savingstreak` are the refusals I triggered. No `System.out`/`System.err` anywhere in
  `backend/src/main`; no `console.log`/`console.error` in `frontend/src`.
- Browser console at `…review.6.browser.log`: **no `pageerror`**; the only `console:error` is the
  deliberate 400 from the refused `0` deposit; the `net::ERR_ABORTED` entries are the page's
  pre-existing StrictMode `AbortController` cleanup plus the deliberately held-open request. Vite's
  log holds only its start-up banner — no transform errors.

### /code-review over the range

Ran over `ticket/01-points-reported-by-reason..ticket/02-this-weeks-new-savings` (33 files, ~4.2k
insertions). **No correctness bug found.** It confirmed independently, with
`org.springframework.transaction.interceptor` at TRACE, that `@Transactional(readOnly = true)` on
the package-private `SavingsAccountController.savingsAccount` really does take effect. Its four
low-severity notes are all already recorded in this ticket by reviews 1–5 and deliberately left:
`ClockOnStartUp`'s `[23h, 25h]` bound as a second place the zone's DST size is encoded (dev profile
only), the sub-cent frame that can read `€ 0,00 more to go` before the bar fills (cosmetic; my
269-frame check did not reach it), `NewSavingsThisWeek.DECIMAL_PLACES` as a third statement of the
scale of money, and `NewSavingsThisWeek.week()` still having no caller. **For ticket 03:** use
`week()` or drop it, and note that `ClockConfiguration` borrows its zone from `streaks.SavingsWeek`,
so revisiting how weeks are counted silently changes how the development clock moves.

### Housekeeping

Three mutations of `backend/src/main` (attempt 5's advance arithmetic, in two files; the zone as
UTC), all reverted. Four second instances on port 8081 over copies of the throwaway database (the
position stood back at `0|0`, the hand-written `6|526800` span, the legacy `10|NULL` row, the
hand-edited `7|-604800` row), all killed; port 8081 free, only the orchestrator's instance left
running. One standalone Java 17 sweep outside the repo. `git status --porcelain` empty.
