# 01: Read the current time from an injected clock

**What to build:** Nothing a customer can see changes. Two places in the application currently ask the
system what time it is — recording when a deposit happened, and when a reward was claimed. Both take
the moment from a clock the application supplies instead, so that a later ticket can move that clock
forward and make a rule measured in months demonstrable in a minute.

**Blocked by:** None (can start immediately).

**Status:** done

- [x] The moment a deposit records and the moment a claim records both come from an application-supplied clock rather than the system clock
- [x] With the clock fixed to a chosen moment, a newly recorded deposit and a newly issued claim both carry that moment
- [x] Moments are still kept to the millisecond, so listings that order by moment and then by identifier behave exactly as before
- [x] No new place in the application reads the system clock directly
- [x] Every existing test passes unchanged

## Verified

Reviewed on branch `ticket/01-read-time-from-an-injected-clock`, attempt 1. Every criterion above was
exercised, not read.

**Where the code is.** Be aware before merging: `lab-base..ticket/01-read-time-from-an-injected-clock`
contains only two commits — `5953e00` (logging) and `4b0388d` (this file). The clock itself
(`clock/ClockConfiguration.java`, the `Clock` constructor parameter on `DepositsService` and
`RewardsService`, and `clock/TimeComesFromTheClockApiTest.java`) is already in the `base` commit
`5c68ade`, an ancestor of `lab-base`, so `baseline.checks.log` already runs 62 tests including the clock
test. Nothing is missing — the work exists at the branch tip — but the branch diff on its own does not
show it, and a reviewer expecting to find the clock in the diff will not.

**The clock is injected and read, not asked for.** `grep -rE "Instant.now|LocalDate(Time)?.now|
System.currentTimeMillis|Clock.system"` over `backend/src/main` returns exactly two hits, both in
`ClockConfiguration` — the javadoc and the `Clock.systemUTC()` the bean returns. No entity carries
`@CreationTimestamp` and no repository generates a moment; every `Instant` in the main sources is
passed in. `PointsService.creditBasePointsFor` and `Redemption.issue` both take the moment as an
argument, so the deposit's single clock read dates the deposit and the points credit together.

**With the clock fixed, both records carry that moment.** `./mvnw test` on this branch, my own run:
62 tests, 0 failures, including `TimeComesFromTheClockApiTest` 4/4. That class stands up a second
application with `Clock.fixed(2019-11-05T09:41:17.123456789Z)` and drives it over HTTP. Its log, while
the host machine read 2026-09-02T08:47:

    i.d.s.deposits.DepositsService : deposit accepted depositId=1 savingsAccountId=2 fromCurrentAccountId=1 amount=1.00 pointsEarned=1 depositedAt=2019-11-05T09:41:17.123Z
    i.d.savingstreak.rewards.RewardsService : claim issued redemptionId=1 savingsAccountId=1 reward=CHARITY_DONATION pointsSpent=10 claimedAt=2019-11-05T09:41:17.123Z

Nanoseconds in, milliseconds out: the truncation is asserted against the recorded value rather than
assumed. `deposits_made_at_the_same_moment_are_listed_newest_identifier_first` uses the stopped clock to
make three deposits simultaneous — the hardest case for `findBySavingsAccountIdOrderByDepositedAtDescIdDesc`,
and one a real clock cannot be relied on to produce.

**Live against the running application** (API 8080, web 5173, throwaway database). A deposit of 12.34
answered `"depositedAt":"2026-09-02T08:51:18.169Z"` between a host reading of `08:51:18.103272Z` and
`08:51:18.397896Z` — the application's clock, to the millisecond. A claim of `CHARITY_DONATION` answered
`"claimedAt":"2026-09-02T08:51:24.199Z"`. Three deposits into savings account 2 came back
`08:52:30.834Z / .816Z / .795Z`, newest first, ids 4/3/2. Redemptions list unchanged.

**The flow logs itself.** From `logs/01-read-time-from-an-injected-clock.app.1.backend.log`, the two
requests above:

    DEBUG i.d.s.deposits.DepositsService : deposit takes its moment from the application clock savingsAccountId=1 clockReads=2026-09-02T08:51:18.169585Z recordedMoment=2026-09-02T08:51:18.169Z
    INFO  i.d.s.deposits.DepositsService : deposit accepted depositId=1 savingsAccountId=1 fromCurrentAccountId=1 amount=12.34 pointsEarned=12 depositedAt=2026-09-02T08:51:18.169Z
    DEBUG i.d.savingstreak.rewards.RewardsService : claim takes its moment from the application clock savingsAccountId=1 clockReads=2026-09-02T08:51:24.199292Z recordedMoment=2026-09-02T08:51:24.199Z
    INFO  i.d.savingstreak.rewards.RewardsService : claim issued redemptionId=1 savingsAccountId=1 reward=CHARITY_DONATION pointsSpent=10 claimedAt=2026-09-02T08:51:24.199Z

Both readings on the DEBUG line, so the moment can be traced to the clock and the truncation checked,
rather than the recorded moment being taken on trust. No voucher code reaches a log line.

**Nothing a customer can see changed.** `npm run typecheck` clean. Driven in chromium: signed in as Anke,
opened savings account 1, deposited 7.50 through the form — balance €19,84, "+7 points", and the history
showing `2/09/2026, 10:54  € 7,50  +7` above `2/09/2026, 10:51  € 12,34  +12`, with the claim rendered at
`2/09/2026, 10:51`. Fully styled, no `pageerror`, no console error other than the expected 400 below.
Browser output in `logs/01-read-time-from-an-injected-clock.app.1.browser.log`; the `ERR_ABORTED` lines
there are React StrictMode's double-mounted fetches being cancelled and predate this branch.

**Refusals still refuse, with their reason.** All seven deposit refusals and both claim refusals answered
as problem documents with the reason in `detail` and both balances untouched — a non-amount
(`"twelve" is not an amount of money...`), three decimal places, zero, negative, more than the current
account holds, a current account held by another customer, an unknown savings account (404), not enough
points, and an unknown reward. `0.00` through the web form rendered its reason under the form.

### Two things the next slice should pick up (neither blocks this one)

1. `ClockConfiguration.applicationClock` logs the clock it builds, not the clock the application
   injects. In the fixed-clock test application it printed
   `application clock configured clock=SystemClock[Z] reads=2026-09-02T08:47:00.488106Z` while every
   record that application wrote was dated 2019. The line's own comment says it exists so a reader of a
   wound-forward log can tell which clock is in there; today it would tell them the wrong one. Harmless
   while there is only one clock bean — it matters the moment ticket 03 hands out a movable one, so fix
   it there, or define the movable clock so this bean is not created at all.
2. No refusal anywhere in the application logs a WARN with its reason, as `CLAUDE.md` asks. The reason
   reaches the log only through Spring's own `ExceptionHandlerExceptionResolver` at DEBUG
   (`Resolved [io.dataroots.savingstreak.deposits.DepositRefused: A deposit has to be an amount of more
   than zero, and 0.00 is not.]`); `grep -c "WARN.*i\.d\.s" ` over the app log is 0. This is a repo-wide
   gap that predates this branch and this ticket's flow has no refusal of its own — reading a clock
   cannot fail — so it is not held against this ticket. Ticket 06 introduces refusals of its own and
   should not repeat it.

### Independently re-verified

A second reviewer repeated the above on a fresh application instance (own throwaway database, port
8080) rather than reading the evidence above. `./mvnw test`: 62 tests, 0 failures, `TimeComesFromTheClockApiTest`
4/4. `npm run typecheck` clean. `git diff 2d690aa..HEAD -- backend/src/test` adds only
`clock/TimeComesFromTheClockApiTest.java` — no existing test file was touched, so "passes unchanged"
is literal.

A deposit of 12.34 answered `"depositedAt":"2026-09-02T08:58:05.320Z"`, bracketed by host readings of
`08:58:05.272116Z` and `08:58:05.545570Z`; the DEBUG line behind it reads
`clockReads=2026-09-02T08:58:05.320936Z recordedMoment=2026-09-02T08:58:05.320Z`, so the read and the
truncation are both on the record. A `CHARITY_DONATION` claim answered `08:58:10.932Z` off the same
clock. Three deposits into savings account 2 listed 4/3/2, newest first. All seven deposit refusals,
the unknown-savings-account 404 and the not-enough-points claim answered as problem documents with
their reason in `detail`, and left the balances at `2461.66 / 12.34 / 6.00` — exactly the sum of what
was actually accepted.
