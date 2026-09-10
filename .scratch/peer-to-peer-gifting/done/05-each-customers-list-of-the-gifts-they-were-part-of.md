# 05: Each customer's list of the gifts they were part of

Status: done

**Blocked by:** 01 (a customer can give points to another customer).

**What to build:** Each customer can read the gifts they have been part of — sent and received
together in one list, newest first, each row marked with its direction and naming the other person by
name and what the gift was worth. One list rather than two endpoints, because the whole story of a
customer's gifting reads chronologically and direction is a property of who is reading it; this is
the idiom the money-movement ledger already set with its own direction.

The record outlives the points. A gift stays in both customers' lists after its points have been
spent, given onward, or expired — what somebody did is not undone by what later happened to the
points they did it with.

Reading the list for a customer who does not exist is a 404, as every other per-customer read is.

The test harness gains one view record for a gift, beside the existing money-movement and deposit
views. Nothing else about the harness changes: it already knows both seeded customers by name, their
contact details and their balances.

- [x] `GET /api/customers/{customerId}/gifts` answers the gifts this customer sent and received, newest first.
- [x] Each row carries the gift's id, its direction for this customer, the other party by id and name, the points, and the moment it happened.
- [x] A gift appears in the sender's list marked sent and in the recipient's list marked received, naming the other person in each.
- [x] A customer who has been part of no gifts gets an empty list, not a 404.
- [x] A gift still appears in both lists after its points have been spent.
- [x] A gift still appears in both lists after its points have expired.
- [x] Reading the list for a customer who does not exist answers 404.

## Review feedback - attempt 1

The behaviour this ticket asks for is real and I watched all seven criteria work — see the
verification notes at the end of this section. The checkboxes are left ticked because each one is
genuinely proven. What sends this back is one defect in the new tests: an assertion that asserts a
guarantee the application does not make, in the one place this repository has already written down
how to avoid exactly that. It is a one-line fix.

### Blocking: `givenAt` is asserted against a bound the application does not guarantee

`backend/src/test/java/io/dataroots/savingstreak/giftingpoints/BothPartiesReadTheSameGiftFromTheirOwnEndApiTest.java:86-104`

```java
Instant beforeSheGave = app.theClockReads();
GiftView given = app.give(ANKE, BRAM, THE_FIRST_GIFT);
Instant afterSheGave = app.theClockReads();
...
assertThat(asSheReadsIt.givenAt())
        .as("the moment it happened, off the application's clock")
        .isBetween(beforeSheGave, afterSheGave);
```

The two sides of that comparison are at different precisions:

- `givenAt` is truncated to whole milliseconds by the service —
  `backend/src/main/java/io/dataroots/savingstreak/gifting/GiftingService.java:160`:
  `Instant givenAt = clockReads.truncatedTo(ChronoUnit.MILLIS);`
- `app.theClockReads()` is not truncated. It reads `GET /api/dev/clock`, which reports the clock
  verbatim at sub-millisecond precision. Observed on the wire during this review:
  `{"movedForwardByDays":0,"now":"2026-09-10T06:14:10.235354Z"}`.

So the guarantee the application actually makes is
`givenAt ∈ [floor_to_ms(beforeSheGave), afterSheGave]`, not `[beforeSheGave, afterSheGave]`.
Whenever the clock read behind `beforeSheGave` and the gift's own `clock.instant()` fall in the same
millisecond and `beforeSheGave` has a non-zero sub-millisecond part, `givenAt` sorts *below* the
lower bound and `isBetween` (inclusive both ends) fails while the product is entirely correct.

This repository already knows about this trap and guards it, with a comment naming it —
`backend/src/test/java/io/dataroots/savingstreak/movingtheclock/AClockRecordNobodyWroteApiTest.java:92-100`:

```java
Instant realMomentBefore = Instant.now();
...
assertThat(made.depositedAt())
        // A moment is kept to the millisecond, so it can sit up to a millisecond below the
        // real moment this test read just before making it.
        .isAfterOrEqualTo(realMomentBefore.minusMillis(1))
        .isBeforeOrEqualTo(realMomentAfter);
```

And the direct prior art in this very package sidesteps it by asserting only the safe upper bound —
`backend/src/test/java/io/dataroots/savingstreak/giftingpoints/AGiftMovesPointsFromOneCustomerToAnotherApiTest.java:81-84`:
`assertThat(gift.givenAt()).isNotNull().isBeforeOrEqualTo(app.theClockReads());`

**How I found it:** by reading the assertion against `GiftingService.give` and against the precision
of the `/api/dev/clock` response quoted above, not by seeing it fail. I ran
`BothPartiesReadTheSameGiftFromTheirOwnEndApiTest` five times in a row and it passed every time, and
the measured gap between two consecutive gift POSTs in the running application was 12 ms
(`gift takes its moment from the application clock` DEBUG lines at 08:16:09.121 and 08:16:09.133),
so the window is narrow. Narrow is not the same as closed, and an assertion that encodes a
guarantee the system does not make is wrong whether or not it has bitten yet.

**What to do:** pin the lower bound to what the application actually promises — either
`.isAfterOrEqualTo(beforeSheGave.truncatedTo(ChronoUnit.MILLIS)).isBeforeOrEqualTo(afterSheGave)`,
which is exact, or match the existing idiom with
`.isBetween(beforeSheGave.minusMillis(1), afterSheGave)`. Do not weaken it to `isNotNull()`; the
two-sided check is worth keeping, it just needs the right bound.

### Not blocking, but worth fixing while you are in here

- `AGiftOutlivesThePointsItMovedApiTest.java:101-106` asserts both pots are globally empty after the
  sweep. Anke deposits `"50.00"` and the clock is wound past that deposit's first loyalty
  anniversary, which is worth points to her; the assertion holds today only because
  `payLoyaltyBonuses` is a scheduled job this test never runs and a clock jump cannot trigger. The
  criterion actually needs "no batch from either gift survives", so the global-zero claim is
  stronger than the property and would break if anyone ever chained the loyalty sweep into this
  test, even with the behaviour unchanged. The gift-list assertions at `:112-138` are the ones
  carrying criterion 6; consider narrowing the balance ones.
- `AGiftOutlivesThePointsItMovedApiTest.java:98-99` computes the wind-forward from
  `sheEarnedThemOn`, which is read *before* the deposit is made. If the Brussels date rolls over
  between that read and the deposit's own clock read, the target lands on the anniversary instant
  rather than past it. It still passes on the real milliseconds that elapse before the sweep, but
  the margin is milliseconds rather than the day that was intended. The sibling test
  `GiftedPointsExpireTwelveMonthsAfterTheyWereEarnedApiTest.java:82-83` pins its computed date with
  an `assertThat(app.pointsExpiringNextOnOf(ANKE)).isEqualTo(...)` so a rollover fails loudly; one
  such assertion after the deposit would bring this test in line.
- `AGiftListIsOnlyReadForACustomerWhoExistsApiTest.java:53-66` asserts only that the body is a JSON
  array. That is deliberate and the javadoc explains why size is not asserted on the shared
  database, and I think the call is right — noting it so the next reviewer does not re-litigate it.

### What was verified and is not in question

Everything below was exercised by hand against the running application on a throwaway database, so
none of it needs redoing; only the assertion above needs changing.

- `cd backend && ./mvnw test` — `Tests run: 256, Failures: 0, Errors: 0`, BUILD SUCCESS. No flake on
  this machine, including `ADepositRecordedBeforeRatesWereApiTest`, which the implementer reported
  hitting.
- `cd frontend && npm run typecheck` — clean.
- On a fresh database, `GET /api/customers/1/gifts` and `/2/gifts` both answered `200 []` — an empty
  list, not a 404.
- `GET /api/customers/999/gifts`, `/0/gifts` and `/-1/gifts` each answered
  `404 {"detail":"There is no customer 999."}` (and 0, -1), each leaving
  `WARN ... request rejected customerId=999 reason=There is no customer 999.`
- Anke deposited 60.00, gave Bram 40. Her list: `{"id":1,"direction":"SENT",...,"recipientName":"Bram De Vos","points":40,...}`;
  his: the same `id":1` as `"direction":"RECEIVED"` with `"senderName":"Anke Peeters"`. Both rows
  carry both parties by id and name, the points and `givenAt`.
- Clock +1 day, Bram gave 10 back. Her list read `RECEIVED`(id 2) above `SENT`(id 1); his read
  `SENT`(id 2) above `RECEIVED`(id 1) — newest first, out of the same two rows, direction decided by
  the reader.
- Bram spent all 30 points he held on three charity donations; balance 0 and both gifts still in
  both lists.
- Clock +366 days and `POST /api/dev/jobs/expireOldPoints/run` — `points expired ... batches=2 points=30`,
  both balances 0, `pointsExpiringNext` null, and both gifts still in both lists with their figures
  and directions intact.
- Two gifts 12 ms apart ordered id-desc within the same reading (id 4 above id 3), so the
  newest-first tie-break holds.
- Log evidence: every read left `DEBUG ... GiftingService : gifts listed customerId=1 gifts=2 people=2`
  (counts rising 0 → 1 → 2 → 4 across the session); every gift left
  `INFO ... GiftingService : gift given giftId=1 senderCustomerId=1 recipientCustomerId=2 points=40 givenAt=...`.
  Zero `ERROR` lines and no stack traces in the backend log. This matches the repository's own
  listing idiom (`MoneyMovementsService` logs `money movements listed ...` at DEBUG).

### Response - attempt 2

All three points above are addressed in `3cfa310`, which changes tests only; no production code
moved.

- **Blocking, `givenAt`:** the lower bound is now
  `.isAfterOrEqualTo(beforeSheGave.truncatedTo(ChronoUnit.MILLIS)).isBeforeOrEqualTo(afterSheGave)`
  — the exact promise the service makes — with a comment saying why the two sides are read at
  different precisions. The two-sided check is kept.
- **Global-zero balances:** the end-of-sweep assertions are narrowed to his pot, which both gifts
  passed through and nothing else ever did, so "his pot is empty" *is* "no batch either gift
  created survives". Her global zero is gone; the class javadoc is updated to match.
- **Wind-forward computed before the deposit:** an
  `assertThat(app.pointsExpiringNextOnOf(ANKE)).isEqualTo(sheEarnedThemOn.plusYears(1))` now sits
  straight after the deposit, as `GiftedPointsExpireTwelveMonths...` does, so a Brussels date
  rolling over between the read and the deposit fails there rather than silently shrinking the
  margin.
- The `AGiftListIsOnlyReadForACustomerWhoExistsApiTest` note is left as it stands, as the reviewer
  intended.

## Verified

Reviewed on attempt 2 against `agentic_engineered..ticket/05-each-customers-list-of-the-gifts-they-were-part-of`.
The whole of that range is three new API test classes (391 lines, no production code): the endpoint,
`GiftingService.giftsOf`, the repository ordering and the `GiftView` harness record all landed under
ticket 01 (`f0b0cfe`) and are on the base branch already. So what this ticket adds is the proof of
the seven criteria, and that is what was judged.

**Checks, run myself.** `cd backend && ./mvnw test` → `Tests run: 256, Failures: 0, Errors: 0`,
BUILD SUCCESS. `cd frontend && npm run typecheck` → exit 0. The three new classes were then re-run
three more times in a row, all green, because the attempt-1 blocker was a timing assertion.

**The attempt-1 blocker is genuinely fixed, not merely answered.**
`BothPartiesReadTheSameGiftFromTheirOwnEndApiTest:103-110` now reads
`.isAfterOrEqualTo(beforeSheGave.truncatedTo(ChronoUnit.MILLIS)).isBeforeOrEqualTo(afterSheGave)`.
That is the exact promise: `GiftingService:159-160` records
`clock.instant().truncatedTo(ChronoUnit.MILLIS)`, truncation is monotone, so
`truncate(gift) >= truncate(beforeSheGave)` always holds while the untruncated bound could sort
above it. The running application prints both halves on one line —
`gift takes its moment from the application clock senderCustomerId=1 clockReads=2027-09-12T07:03:53.494154Z recordedMoment=2027-09-12T07:03:53.494Z`
— so the mismatch the previous reviewer reasoned about is visible in the log. The two-sided check is
kept. The two non-blocking notes are addressed too: the end-of-sweep balance claim is narrowed to
Bram's pot with the reasoning in the javadoc, and the wind-forward target is pinned by
`assertThat(app.pointsExpiringNextOnOf(ANKE)).isEqualTo(sheEarnedThemOn.plusYears(1))` right after
the deposit.

**Driven by hand over HTTP** against the running application on a throwaway database (backend-only
ticket: the frontend has no gifting screen yet — the only `gift` in `frontend/src` is the
unknown-reward fallback icon). Full transcript in
`.scratch/peer-to-peer-gifting/logs/05-each-customers-list-of-the-gifts-they-were-part-of.review.2.curl.log`.

- Empty, not a 404: `GET /api/customers/1/gifts` and `/2/gifts` on the fresh database → `200 []`.
- Unknown customer: `/999/gifts`, `/0/gifts`, `/-1/gifts` and `/9223372036854775807/gifts` each →
  `404 {"detail":"There is no customer 999."}` (and so on). `/abc/gifts` → Spring's `400`, as every
  other per-customer read answers it.
- Anke deposited EUR 60,00 and gave Bram 40. Her row:
  `{"id":1,"direction":"SENT","senderId":1,"senderName":"Anke Peeters","recipientId":2,"recipientName":"Bram De Vos","points":40,"givenAt":"2026-09-10T07:02:45.095Z"}`;
  his: the same `id":1` as `"direction":"RECEIVED"`. Both parties by id and name, the points and the
  moment on both rows.
- Clock +1 day, Bram gave 10 back. Her list read `RECEIVED`(2) over `SENT`(1); his read `SENT`(2)
  over `RECEIVED`(1) — the same two records, direction decided by the reader, newest first.
- Bram spent all 30 points he held on three charity donations (balance 0) — both gifts still in both
  lists, unchanged.
- Clock +366 days and `POST /api/dev/jobs/expireOldPoints/run` → both balances 0,
  `pointsExpiringNext` null, both gifts still in both lists with their figures, names and directions
  intact.
- Two gifts 15 ms apart sorted id-desc within the same reading (4 above 3), so the tie-break holds.

**Logs read, not just response bodies** (`...05-....app.2.backend.log`). Every read left
`DEBUG i.d.savingstreak.gifting.GiftingService : gifts listed customerId=1 gifts=4 people=2`
(the count rising 0 → 1 → 2 → 4 across the session); every gift left
`INFO ... : gift given giftId=4 senderCustomerId=1 recipientCustomerId=2 points=2 givenAt=2027-09-12T07:03:53.509Z`;
every 404 left `WARN i.d.savingstreak.web.CustomerController : request rejected customerId=999 reason=There is no customer 999.`
The sweep explained itself over inherited dates —
`points batch expired batchId=3 customerId=1 reason=GIFT_RECEIVED earnedAt=2026-09-10T07:02:45.051Z anniversary=2027-09-10T07:02:45.051Z pointsExpired=10`.
No ERROR-level line, no stack trace and no 5xx anywhere in the log; the Vite log holds only its
start-up banner.

**The new tests were checked for bite, not only for green.** Two mutations applied to production
code locally and reverted (nothing committed):
`OrderByGivenAtDescIdDesc` → `...AscIdAsc` turns both list tests red (`expected: 2L but was: 1L` on
the top-of-list identifiers), and flipping the `SENT`/`RECEIVED` ternary in
`GiftingService.giftsOf` turns them red on `expected: "SENT" but was: "RECEIVED"`. `git status` is
clean and the three classes are green again after the revert.

Note for whoever reads this next: the repository's `/code-review` skill was launched over the range
and never reported back (it appears to have attached to the same agent that stalled on attempt 1),
so as the orchestrator allowed, the diff review here was done by reading all 391 added lines
directly against the ticket and the sibling tests in `giftingpoints/`.
