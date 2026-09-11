# 03: Advance the clock in the development profile

**What to build:** A trainer or a participant can move the application's clock forward by a number of
days. A deposit made afterwards records a moment that far ahead, so a rule measured in months can be
reached during a coffee break rather than during a calendar year. The move survives a restart, so an
application that is stopped halfway through an exercise does not rewind whoever was using it back to
day zero. The control exists only in the development profile and appears nowhere in the web page.

**Blocked by:** 01 (Read the current time from an injected clock).

**Status:** done

- [x] The clock can be moved forward by a number of days over the API while the development profile is active
- [x] A deposit made after a move records a moment that many days ahead of the real one
- [x] How far the clock has been moved can be read back, so somebody mid-exercise can tell where in time they are
- [x] The move is still in effect after the application is restarted
- [x] Moving the clock backwards, or by a nonsensical number of days, is refused with a reason
- [x] Neither control exists when the development profile is not active

## Verified

Reviewed on branch `ticket/03-advance-the-clock-in-the-development-profile`, attempt 2, over
`ticket/02-deposits-carry-what-remains-of-them..a7538a5` (commits `4c04a2e`, `29600af`, `a7538a5`).
Every criterion above was exercised against a running application, not read.

**Checks, my own run.** `cd backend && ./mvnw -o test` — 88 tests, 0 failures, 0 errors, 0 skipped
(16 of them new, in `io.dataroots.savingstreak.movingtheclock`: `MovingTheClockForwardApiTest` 8,
`TheClockCannotBeMovedOutsideDevelopmentApiTest` 4, `TheClockStaysWhereItWasMovedApiTest` 2,
`AClockRecordNobodyWroteApiTest` 2). The four new classes also pass on their own
(`-Dtest='MovingTheClockForwardApiTest,TheClockStaysWhereItWasMovedApiTest,AClockRecordNobodyWroteApiTest,TheClockCannotBeMovedOutsideDevelopmentApiTest'`
— 16 tests, 0 failures), so none of them depends on the order the suite ran in.
`cd frontend && npm run typecheck` — exit 0, no output.

**The clock moves, and everything dated afterwards moves with it.** Against the lab's application on
:8080 (dev profile, throwaway database), the machine reading `2026-09-02T11:01:30Z`:

    POST /api/dev/clock/advance {"days":400}  -> 200 {"movedForwardByDays":400,"now":"2027-10-07T11:01:30Z"}
    POST /api/savings-accounts/1/deposits     -> 201 {"id":2,...,"depositedAt":"2027-10-07T11:01:30.893Z"}
    POST /api/dev/clock/advance {"days":30}   -> 200 {"movedForwardByDays":430,...}
    POST /api/savings-accounts/1/redemptions  -> 201 {...,"claimedAt":"2027-11-06T11:01:30.918Z"}

A deposit made just before the move is still dated `2026-09-02T11:01:30.847Z`, so the listing holds
both a real moment and a moved one. Claims read the same clock as deposits. The backend log carries
the whole decision:

    ClockService : clock asked to advance days=400 alreadyMovedByDays=0 reading=2026-09-02T11:01:30.873202Z
    ClockService : clock advanced byDays=400 movedForwardByDays=400 wasReading=2026-09-02T11:01:30.873202Z nowReading=2027-10-07T11:01:30.876043Z
    DepositsService : deposit takes its moment from the application clock savingsAccountId=1 clockReads=2027-10-07T11:01:30.893557Z recordedMoment=2027-10-07T11:01:30.893Z

**Read back.** `GET /api/dev/clock` answers `{"movedForwardByDays":431,"now":"2027-11-07T…"}` and
leaves the clock where it is (`ClockService : clock asked where it is standing movedForwardByDays=431`,
DEBUG). Repeated calls report the same offset with a later `now`, so the clock is still running rather
than stopped where it was put.

**The move survives a restart.** Own instance on :8082 against a fixed file. Advanced 365 then 14,
`GET /api/dev/clock` -> 379, deposit dated `2027-09-16T11:03:27.838Z`. Killed the JVM; the SQLite row
read directly is `clock_offset(1, 379)` — one row, fixed id. Started a second application against the
same file:

    ClockOnStartUp     : clock put back where it was left movedForwardByDays=379 reading=2027-09-16T11:03:46.958998Z
    TomcatWebServer    : Tomcat started on port 8082
    ClockConfiguration : application clock in use clock=MovableClock[SystemClock[Z] moved forward 379 days] reads=2027-09-16T11:03:46.989597Z

The restore precedes the port binding, so no request can be served against a rewound clock.
`GET /api/dev/clock` -> 379, and a deposit made after the restart is dated `2027-09-16T11:03:58.073Z`
while the machine read `2026-09-02T11:03:58Z`.

**Refusals, each with its reason and each leaving the clock alone.** Every one is a 400 problem
document carrying the reason in `detail` — the field the frontend renders unchanged — and each leaves
a WARN naming the reason:

| request | detail |
|---|---|
| `{"days":-30}` | `The clock only moves forward. Ask for at least one day, not -30.` |
| `{"days":0}` | `The clock only moves forward. Ask for at least one day, not 0.` |
| `{"days":9223372036854775807}` | `The clock moves at most 36500 days (a hundred years) past the real one, and it has already moved 430, so 9223372036854775807 more is too far.` |
| `{"days":36500}` (from 430) | same sentence, naming 36500 |
| `{}` and `{"days":null}` | `Moving the clock needs a number of days to move it by.` |
| `{"days":"a fortnight"}`, empty body | `Failed to read request` (Spring's, same as any malformed body on this API) |

`GET /api/dev/clock` after all of them: still 431. The `Long.MAX_VALUE` case does not overflow —
`days > CAP` short-circuits before the sum is taken. The cap boundary is exact: advancing to precisely
36500 was accepted (`nowReading=2126-08-09T…`), one day past it refused, and a deposit at 100 years out
still records cleanly (`depositedAt=2126-08-09T11:07:50.275Z`).

**A record nobody could have written.** With the application down I edited `clock_offset` by hand.
`-400` and `999999` are both refused out loud and the application comes up at the real moment:

    ClockOnStartUp : clock not put back: the record says it was moved -400 days, which is not a move forward of at most 36500 days, so it is left at the real moment 2026-09-02T11:04:22.783086Z
    ClockOnStartUp : clock not put back: the record says it was moved 999999 days, which is not a move forward of at most 36500 days, so it is left at the real moment 2026-09-02T11:08:49.142808Z

`GET /api/dev/clock` -> 0, the next deposit is dated at the real moment, and the clock still moves
afterwards (`+5` -> 5).

**Neither control exists without the profile.** Started a second application with
`-Dspring-boot.run.profiles=prod` on :8081. Its start-up line is
`application clock in use clock=SystemClock[Z]` — the movable clock is not built — and there is no
`ClockOnStartUp` line at all, so nothing reads the record either. `GET /api/dev/clock` -> 404,
`POST /api/dev/clock/advance` -> 404 (`NoResourceFoundException` in the log, i.e. no route rather
than a route that refused), while `GET /api/customers` -> 200: it is the clock that is missing, not
the application. Spring's own count says the same — `11 mappings in 'requestMappingHandlerMapping'`
without the profile against `13 mappings` with it. (`./mvnw spring-boot:run` activates `dev` from `backend/pom.xml`, which
is why the lab's start command gets a movable clock.)

**Nowhere in the web page.** `grep -rn "api/dev\|[Cc]lock" frontend/src` returns nothing. Drove
:5173 with Playwright: signed in as Anke, opened savings account 1, and searched the rendered text for
`clock`, `Clock`, `advance`, `Advance`, `time machine`, `days forward` — all absent. The page renders
fully styled (screenshots read, not just captured), and a deposit made *from the form* while the clock
stood 431 days on is listed as `7/11/2027, 12:05  € 2,00  +2`, with the pre-move deposit still at
`2/09/2026, 13:01`. The browser log holds no `[console:error]` and no `[pageerror]`; the four
`[requestfailed] … net::ERR_ABORTED` lines are the page's own `AbortController` cancelling in-flight
fetches on navigation, and appear identically in the ticket 01 and 02 browser logs.

**Logging.** SLF4J throughout, no `System.out` anywhere in `src/main`. DEBUG carries the inputs behind
each decision, INFO one line per move, WARN every refusal with its sentence, and the start-up line
names the clock actually injected. Concurrency is visible in it too: eight parallel `+1` requests
produced eight INFO lines whose `alreadyMovedByDays` each equals the previous line's result — 431 to
439, nothing lost.

**Known limitations, none of them this ticket's criteria.**
- The clock is forward-only with no reset, so a mistyped `36500` cannot be undone over the API. The
  recovery is to stop the application, set `clock_offset.moved_forward_by_days` to 0 (or delete the
  row) and start again — `ClockOnStartUp` accepts 0. Worth a decision before a training day; the
  ticket does not ask for a reset.
- A fractional `days` (`{"days":1.5}`) is coerced to 1 by Jackson rather than refused. This is the
  repo's existing behaviour for every numeric field, not something this slice introduced —
  `{"fromCurrentAccountId":1.9}` on the pre-existing deposit endpoint is accepted as 1 in the same way.
  `{"days":0.4}` truncates to 0 and is refused with a reason.
- `clock_offset` is created by `ddl-auto=update` even without the `dev` profile, because the entity is
  not profile-scoped. Nothing outside `dev` reads or writes it (no `ClockOnStartUp` line in the
  non-dev log), so the table is inert.
- `AClockRecordNobodyWroteApiTest` covers only the backwards hand-edit, not the too-far one. Same
  branch in `ClockOnStartUp`, and I exercised the `999999` case live above.

### Non-blocking notes for whoever merges

`/code-review` over `ticket/02-deposits-carry-what-remains-of-them..a7538a5` raised fourteen points
and none of them fails a criterion above. Recording the ones worth acting on so they are not
rediscovered, in the shape tickets 01 and 02 used.

1. **The time machine is bound to a concrete `MovableClock`, not to the `Clock` bean everything else
   reads.** `ClockService` and `ClockOnStartUp` inject `MovableClock`; `DepositsService` and
   `RewardsService` inject `Clock`. In the lab and in the dev profile those are the same object, which
   is why every criterion above holds. They are *not* the same object in an application that supplies
   its own `Clock` — `TimeComesFromTheClockApiTest` does exactly that, and my own test run prints
   `application clock in use clock=FixedClock[2019-11-05T09:41:17.123456789Z,Z]` from an application
   whose `/api/dev/clock/advance` would answer 200 and persist an offset while every record it wrote
   stayed in 2019. Nothing asserts that the `Clock` bean *is* the movable one, and because the
   dependency is by concrete type no `@Primary` override can reach it. This is the successor to ticket
   01's note 1, which this ticket did close: the start-up line now names the injected clock
   (`FixedClock[…]` above) rather than the bean it offered.
2. **A fractional `days` is rounded rather than refused.** `POST /api/dev/clock/advance
   {"days":1.5}` answers 200 and moves the clock one day; the log reads `clock asked to advance
   days=1`, so the 1.5 never reaches the application. `{"days":0.4}` truncates to 0 and comes back
   *"The clock only moves forward. Ask for at least one day, not 0."* — a sentence about a number
   nobody typed. Jackson's `ACCEPT_FLOAT_AS_INT` does this on the way in, and
   `AdvanceClockRequest`'s javadoc rests on the claim that "there is nothing to say about a number of
   days beyond whether it is one", which the 0.4 case falsifies in the app's own words. It also sits
   against the repo's own documented rule for the analogous quantity, `DepositsService`:178-183 —
   *"Refused rather than rounded. Rounding would move an amount nobody typed, and a bank that quietly
   decides what a figure was meant to say is worse than one that asks."* Not held against criterion 5:
   every case the criterion names (backwards, zero, absurdly far, no number at all) is refused with
   its reason, and the same silent truncation applies to `fromCurrentAccountId` on the pre-existing
   deposit endpoint (`{"fromCurrentAccountId":1.9}` is accepted as 1), so it is a repo-wide binding
   wart rather than something this slice introduced. One integrality check, or taking the days as text
   the way `amount` is taken, closes it.
3. **The controller's own refusal logs nothing.** `POST /api/dev/clock/advance {}` answers 400 with
   *"Moving the clock needs a number of days to move it by."* and leaves no line at any level in
   `io.dataroots.savingstreak`; `DevelopmentClockController` has no logger. Same for a malformed body
   (Spring's *"Failed to read request"*). This is the repo-wide gap ticket 01's review declined to hold
   against a ticket, and it is *narrower* now than it was: `grep -rn "log.warn\|log.error"
   backend/src/main` returns exactly two hits, both added by this ticket, so ticket 03 is the first
   slice in the repo to WARN on a refusal at all. Every controller-layer refusal in the app is still
   silent (`SavingsAccountController.deposit`, `.claim`, `CustomerController.signIn`). Worth closing
   repo-wide rather than in this branch alone.
4. **`ClockOnStartUp`'s WARN is prose, not `key={}` pairs.** It reads *"clock not put back: the record
   says it was moved -400 days, which is not a move forward of at most 36500 days, so it is left at
   the real moment 2026-09-02T11:04:22.783086Z"* — greppable by its prefix and carrying the reason,
   which is what the rule is for, but with no `movedForwardByDays=` or `reading=` to grep by field,
   unlike every other line the same class emits.
5. **The total cap has behaviour but no test.** `the_clock_cannot_be_moved_absurdly_far` passes
   `Long.MAX_VALUE`, which short-circuits on `days > MOST_DAYS_THE_CLOCK_CAN_BE_MOVED` and never
   reaches `alreadyMovedBy + days > …`; delete that second clause and the suite still goes green. The
   clause does work — I advanced to exactly 36500 (accepted, `nowReading=2126-08-09T…`) and then asked
   for one more day (refused, *"…it has already moved 36500, so 1 more is too far."*) — so this is a
   coverage gap, not a behaviour gap. A test that advances 36,000 and then asks for 1,000 closes it.
6. **A refused start-up record is invisible on the endpoint that exists to answer "where am I?"** The
   bad row stays in the file and `GET /api/dev/clock` answers `movedForwardByDays=0`, which a
   participant cannot tell from a fresh database; the only signal is the one WARN in the start-up log.
   Either surface it on the endpoint or clear the rejected row so state and record agree.
7. **`howFarItHasMoved()` is not synchronised while `advanceBy()` is**, so a `GET` interleaved with a
   `POST /advance` can pair a pre-move `movedForwardByDays` with a post-move `now`. Dev-only, one
   person with curl; cheap to close by reading the pair inside the same lock.
8. **`moveForwardTo` enforces nothing.** It is a bare `AtomicLong.set`, so `MovableClock`'s
   "forward only" invariant lives in its two callers rather than in the clock. Ticket 04 adds another
   dev affordance in this package and would get no guard.
9. **Smaller things.** `now` on `/api/dev/clock` is untruncated (`…40.732429Z`) while every recorded
   moment is truncated to milliseconds, so `HowFarTheClockHasMoved`'s "what a deposit made next will
   be dated" is off in the sub-millisecond digits. `request == null` in
   `DevelopmentClockController.advance` is unreachable (`@RequestBody` is required, so an absent body
   is a 400 from the message converter — verified) and is copied from `SavingsAccountController.claim`,
   which carries the same dead branch. `startAnApplicationAgainstTheFile()`/`boundTo()` is now
   copy-pasted into six test classes, comment included; `ApiIntegrationTest` already owns
   `aDatabaseFileThatDoesNotExistYet` and is the obvious home for both.
   `TheClockCannotBeMovedOutsideDevelopmentApiTest.the_same_paths_are_there_when_the_development_profile_is`
   drives `advance` against the run's *shared* application and is safe only because the literal `0` is
   refused before anything is written — a positive number there would wind the shared database forward
   for the rest of the run. And `clock_offset` is created by `ddl-auto=update` even without the `dev`
   profile (the entity is not profile-scoped); nothing outside `dev` reads or writes it — the non-dev
   log has no `ClockOnStartUp` line at all — so the table is inert.
