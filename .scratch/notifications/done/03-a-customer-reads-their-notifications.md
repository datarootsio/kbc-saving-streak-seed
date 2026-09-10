# 03: A customer reads their notifications

Status: done

**Blocked by:** 02 (An anniversary coming soon says whether it is first in line).

**What to build:** The two endpoints, and the read mark behind them.

`GET /api/customers/{customerId}/notifications` returns `List<NotificationResponse>` newest first,
read and unread together, because the panel is a record rather than an inbox that empties.
`POST /api/customers/{customerId}/notifications/read` takes no body, sets `readAt` on every unread
notification belonging to that customer, and returns the same list so the caller needs one round
trip rather than two.

`NotificationResponse(Long id, NotificationReason reason, Long savingsAccountId, Long depositId,
BigDecimal amount, Long points, LocalDate occursOn, Instant raisedAt, Instant readAt)`. The reason
serialises as its enum name and the frontend writes the sentence, the way `WhatItEarned` already
composes a sentence from three numbers: every euro and every date in this application is formatted
Dutch-style in the browser, and rendering sentences in Java would fork that formatting into a second
place that will drift. Refusal messages stay as they are, because a refusal's wording is domain
logic and a notification's wording is not. `occursOn` is a `LocalDate` and not an `Instant`, for the
reason `PointsExpiringNext` gives: the backend picks the zone rather than the browser.

`readAt` is a moment and is set once. Marking an already-read notification leaves the original
moment alone, so the operation is idempotent on the entity in the same way `PointsCredit.expire` is,
and a second call changes nothing and reports the same list.

Both endpoints are customer-scoped, matching money-movements, redemptions and gifts. An unknown
customer is refused with the sentence `AccountsService.noSuchCustomer` already owns, mapped to 404
in `RefusalsAsHttp` alongside the others.

- [x] A customer's notifications come back newest first, read and unread together
- [x] A customer with nothing comes back as an empty list rather than an error
- [x] Marking read sets a moment on every unread notification and returns the updated list
- [x] Marking read twice leaves the first moment alone and changes nothing
- [x] A notification's figures match the sweep that raised it, field for field
- [x] Naming a customer who does not exist is refused with a readable reason and a 404
- [x] One INFO line when a customer marks read, carrying the customer and how many were marked
- [x] Every refusal is one WARN line carrying its kind and its reason

## Verified

Reviewed on `ticket/03-a-customer-reads-their-notifications`, diff range
`ticket/02-an-anniversary-coming-soon-says-whether-it-is-first-in-line..HEAD` — commits `e986fcc`
(the work) and `3ee6654` (the ticket move), 13 files, +824/-19. Backend only, which is right: the
bell, the panel and the account notice are tickets 04 and 05.

**Checks.** Stopped the lab app first (`lab.sh app-stop`, 8080 and 5173 confirmed free) so the
`THE-PHANTOM-RED-BUILD.md` harness defect could give neither a false red nor a false green, then
`lab.sh checks` → `Tests run: 287, Failures: 0, Errors: 0`, `BUILD SUCCESS`, and `tsc --noEmit`
clean. Output in `.scratch/notifications/logs/03-….review.1.checks.log`. All three new test classes
ran (`ACustomerReadsTheirNotificationsNewestFirstApiTest` 2, `MarkingNotificationsReadSetsTheMoment
OnceApiTest` 1, `NotificationsAreOnlyReadForACustomerWhoExistsApiTest` 3). Restarted the app with
`lab.sh app-start …03-….app.1b` on a fresh throwaway SQLite file.

**Driven over HTTP** against `http://localhost:8080`, every claim read back out of
`logs/03-….app.1b.backend.log`. *Grep tip:* logback abbreviates the logger, so `grep io.dataroots`
finds nothing — grep `i\.d\.s\.n\.`.

Set-up: Anke (customer 1) savings 1 ← EUR 100,00 (deposit 1) day 0, then EUR 400,00 (deposit 2) day
1; clock wound +341 days to 2027-08-17; Bram (customer 2) savings 3 ← EUR 1.000,00 day 341. Four
sweeps via `POST /api/dev/jobs/raiseNotifications/run`.

The final `GET /api/customers/1/notifications`, which settles five of the eight boxes at once:

    4 LOYALTY_BONUS_ABOUT_TO_PAY raisedAt 2027-08-17T14:26:31.640Z readAt 2027-08-17T14:27:54.190Z
    3 LOYALTY_BONUS_AT_RISK     raisedAt 2027-08-17T14:26:31.640Z readAt 2027-08-17T14:27:54.190Z
    2 BALANCE_THRESHOLD_REACHED raisedAt 2026-09-11T14:26:14.353Z readAt 2026-09-11T14:26:24.270Z
    1 BALANCE_THRESHOLD_REACHED raisedAt 2026-09-10T14:25:57.312Z readAt 2026-09-10T14:26:05.595Z

- **Newest first, read and unread together** — `raisedAt` descending, tie broken by `id` descending
  (4 before 3, both stamped by the one sweep). Rows 1 and 2 are read and still in the list.
- **Empty rather than an error** — before any sweep, `GET` for customers 1 and 2 both answered `[]`
  with HTTP 200, and `POST …/read` on an empty record answered `[]` with 200 and logged
  `notifications marked read customerId=1 notifications=0 asAt=… held=0`.
- **Marking read sets a moment on every unread one and returns the list** — the call that marked
  rows 3 and 4 gave both the identical moment `2027-08-17T14:27:54.190Z` and answered with all four
  rows in one round trip. Log: `notifications marked read customerId=1 notifications=2 asAt=2027-08-
  17T14:27:54.190197Z held=4`.
- **Marking read twice changes nothing** — rows 1 and 2 kept their original moments
  (`2026-09-10T14:26:05.595Z`, `2026-09-11T14:26:24.270Z`) through three later mark-read calls; the
  no-op call logged `notifications=0 held=4` and the response body was byte-identical to the GET.
- **Figures match the sweep, field for field** — `GET /api/savings-accounts/1/deposits` shows
  deposit 1 → `2027-09-10` / 10 points and deposit 2 → `2027-09-11` / 40 points; the notifications
  carry exactly those `occursOn`/`points`/`depositId`, with `amount` null. The balance rows carry
  `amount` 100 and 500 (the rungs the account landed on) with `depositId`, `points` and `occursOn`
  null. Per-reason nullability is total in both directions.
- **Unknown customer refused, 404, readable** — both endpoints, for ids `99`, `0` and `-1`:
  `{"status":404,"detail":"There is no customer 99."}`, and the same for `POST …/read`. A
  non-numeric id gives the usual 400 conversion failure, untouched.
- **One INFO line per mark-read** — four in the run, each carrying the customer and the count, e.g.
  `i.d.s.n.NotificationsService : notifications marked read customerId=1 notifications=2
  asAt=2027-08-17T14:27:54.190197Z held=4`.
- **One WARN line per refusal, with kind and reason** — four in the run, one per refusal, none
  duplicated: `notifications rejected customerId=99 kind=NO_SUCH_CUSTOMER reason=There is no
  customer 99.` The shape matches `claim rejected … kind={} reason={}` and `gift rejected … kind={}
  reason={}` in the sibling modules.

Also exercised beyond the ticket: cross-customer isolation (Bram marking read left Anke's two unread
rows unread, and neither list carried the other's rows); a repeat sweep still logs
`raised=0`, so the `Clock` injected into `NotificationsService` did not disturb ticket 01/02; POST
with an empty JSON body and with no `Content-Type` both answer 200. No `ERROR`, no 5xx and no stack
trace anywhere in the backend log; no `System.out` in the module or the web layer; Vite log clean.

**Four non-blocking findings** (from `/code-review` over the range, plus my own read), recorded so
the next agent has them:

1. *Test scope, medium.* `ACustomerReadsTheirNotificationsNewestFirstApiTest:148` holds a
   per-customer list (`app.notificationsOf(ANKE)`) against a per-account one
   (`sweep.whatWasSaidAbout(savingsAccount, ANKE)`, which filters to one savings account by design).
   Anke is seeded with two savings accounts, so `hasSameSizeAs` and the positional loop pass only
   because her second account stays silent for the whole class. The first row raised about it would
   make the loop compare mismatched rows and look like a contract bug. Filter the endpoint side by
   `savingsAccountId` too. The criterion itself is genuinely met — I checked it by hand above.
2. *Instant precision, low.* `markEverythingReadFor` answers from the unflushed entities, so the POST
   returns `readAt` at microsecond precision (`…54.190197Z`) while the row, and every later GET,
   holds milliseconds (`…54.190Z`). `raisedAt` already behaves this way, so it is consistent with the
   module rather than new, but `clock.instant().truncatedTo(MILLIS)` would make the answer and the
   record the same value.
3. *Javadoc contradiction, low.* These two endpoints are the only per-customer reads in
   `CustomerController` that do not use its `noSuchCustomer` factory, whose javadoc still claims to
   be "the refusal every endpoint here gives" and logged "in one place". The ticket asked for
   `RefusalsAsHttp`, so the code is right; one of the three javadocs (`CustomerController#noSuch
   Customer`, `GiftingService#giftsOf`, `NotificationsService#notificationsOf`) should be corrected.
   Practical cost today: `grep "request rejected"` no longer finds every unknown-customer 404.
4. *Greppability, low.* The INFO line uses `notifications=` for the marked count while the DEBUG line
   in the same class uses `notifications=` for the total. `marked=`, matching the sweep's `raised=`,
   would remove the ambiguity.

**Unrelated, but worth knowing before a demo:** the checked-in `data/saving-streak.db` still holds
the old `vibe_coded` `notification` table with NOT NULL columns this schema does not fill, so under
`ddl-auto=update` the first sweep against that file fails. That dates to ticket 01, not this branch;
start on a fresh file, as the lab does.
