# 03: Gifted points keep the age they were earned at

Status: done

**Blocked by:** 01 (a customer can give points to another customer).

**What to build:** Gifted points are worth exactly what every other point is worth, and they carry
their original age with them. A point earned last March expires next March whichever pot it is
sitting in when the twelve months are up. So a gift moves points without touching the expiry rule,
and a chain of gifts cannot keep points alive: two customers passing the same points back and forth
watch them expire on the anniversary of the day they were originally earned, however many hops they
made in between.

Everything else about them is unremarkable. They count towards the balance the moment they arrive,
they are spendable on any reward, they are spent oldest-first alongside the recipient's own points
with no regard to which reason earned them, they appear in what the recipient is told expires next,
and they can be given onward to a third customer — carrying the same original age when they go.

Two consequences are honest rather than defects, and both should be readable in the log rather than
smoothed over:

- Giving away points whose anniversary passed earlier today hands over points that night's expiry
  sweep will take. Both rules are holding at once.
- A recipient's pot can hold points older than any deposit they have ever made. That is what having
  been given something second-hand looks like.

Like ticket 03 of the loyalty feature, this is expected to need little or no new production code —
ticket 01 already dates the batches, and the ledger already treats every batch the same way. The work
is the tests that say there is no special case here, and that would fail the day somebody added one.
The clock-moving harness is the seam; a fresh application per class, since these tests wind the clock
a year forward.

- [x] Gifted points expire twelve months after they were originally earned, not twelve months after they were given, and the expiry sweep run before that date leaves them alone.
- [x] Points given back and forth between two customers do not outlive their twelve months.
- [x] Gifted points near the end of their twelve months appear in the points the recipient is told expire next, on the day their own twelve months are up.
- [x] Gifted points count towards the recipient's balance from the moment the gift goes through.
- [x] Gifted points can be spent on a reward, and are spent oldest-first alongside the recipient's own points.
- [x] Received points can be given onward to a third customer, and carry their original age when they go.
- [x] A gift of points whose anniversary has already passed is credited and then swept away, and both events are readable in the log.
- [x] A claim refused for want of points quotes a balance that includes whatever gifted points the customer holds.

## Review feedback - attempt 1

Sent back for one thing. Most of this ticket is done and done well; do not redo it. What follows
says exactly what was checked and what is still open.

### What is already proven, and how (no work needed here)

The central claim of the attempt is correct: the rule is already built.
`PointsService.movePoints` creates every arriving batch with
`PointsCredit.giftReceivedFor(toCustomerId, giftId, taken, batch.getEarnedAt())` — the source
batch's own earned-at — and the sweep judges every batch by that field regardless of reason. There
was nothing to write.

The dating rule is genuinely guarded. I mutated production code to date the arriving batch at the
application clock instead of `batch.getEarnedAt()` and all five new classes failed, each on the
assertion that carries the rule (`GiftedPointsExpireTwelveMonthsAfterTheyWereEarnedApiTest:107`,
`PointsGivenBackAndForthDoNotOutliveTheirTwelveMonthsApiTest:106`,
`AGiftOfPointsPastTheirAnniversaryIsSweptTheSameNightApiTest:95`,
`ReceivedPointsCanBeGivenOnwardApiTest:92`, `GiftedPointsAreSpentLikeAnyOtherPointsApiTest:106`),
as did ticket 01's `AGiftIsDrawnFromTheSendersOldestPointsFirstApiTest`. Reverted.

`./mvnw test` is 246/246 green, `npm run typecheck` clean, and I drove the whole chain live over
HTTP on a throwaway database (transcript:
`.scratch/peer-to-peer-gifting/logs/03-gifted-points-keep-the-age-they-were-earned-at.review.1.curl.log`,
application log: `...03-gifted-points-keep-the-age-they-were-earned-at.app.1.backend.log`):

- 40 points earned 2026-09-09, given away 300 days later; the recipient's pot answered
  `"pointsBalance":40,"pointsExpiringNext":40,"pointsExpiringNextOn":"2027-09-09"` and
  `points credited customerId=2 sourceReferenceId=1 reason=GIFT_RECEIVED points=40 batches=1
  oldestEarnedAt=2026-09-09T18:42:51.457Z`. A sweep on 2027-09-08 took nothing
  (`batchesConsidered=1 batches=0 points=0`); two days on it took the lot
  (`points batch expired batchId=2 customerId=2 reason=GIFT_RECEIVED earnedAt=2026-09-09...
  anniversary=2027-09-09... pointsExpired=40`), 66 days into his ownership. A third sweep changed
  nothing.
- A three-hop chain (Anke -> Bram -> Anke -> Bram) with the batch still expiring on its original
  2029-01-18, and `gift drawn from the sender's oldest points first giftId=4 ... slices=2
  drawnOn=[points=30 earnedAt=2027-12-19...] [points=12 earnedAt=2028-01-18...]` showing an
  already-gifted batch being drawn on with its date intact.
- The overdue-gift case, both events one line each: the `points credited ... reason=GIFT_RECEIVED
  ... oldestEarnedAt=2028-01-18...` and then `points batch expired batchId=10 customerId=2
  reason=GIFT_RECEIVED earnedAt=2028-01-18... anniversary=2029-01-18... pointsExpired=12` on the
  very next sweep.
- `"Cinema ticket costs 100 points, and you have 90."` with only 30 points of his own.

### Blocking: the spend order is only half guarded (AC5, unticked)

**Expected.** The criterion, and the ticket's prose behind it, ask that gifted points are spent
"oldest-first alongside the recipient's own points with no regard to which reason earned them", and
the ticket says the work is "the tests that say there is no special case here, and that would fail
the day somebody added one".

**What I saw instead.** A special case can be added and nothing fails. I changed
`PointsCreditRepository.unspentOldestFirst` to order gifted batches ahead of the recipient's own:

    order by case when credit.reason = io.dataroots.savingstreak.points.PointsReason.GIFT_RECEIVED
      then 0 else 1 end asc, credit.earnedAt asc, credit.id asc

That is exactly the special case the criterion forbids — a gifted point now jumps the queue even
when the recipient's own points are older — and `./mvnw test` reported
`Tests run: 246, Failures: 0, Errors: 0` and `BUILD SUCCESS`. Reverted; the tree is clean.

**Why the test misses it.** In `GiftedPointsAreSpentLikeAnyOtherPointsApiTest` the gift is 100 days
*older* than Bram's own batch for the whole test, so "oldest first" and "gifts first" predict the
same figure in every assertion — the class javadoc's claim of "with no regard to which reason
earned it" is not what the arrangement can distinguish. No other test in the suite puts a gift
*younger* than points the recipient already holds:
`AGiftMovesPointsFromOneCustomerToAnotherApiTest` leaves the gift the older batch and asserts no
spend order at all.

**How to close it.** Cheaply, inside the existing class: after the three claims have emptied the
gift, let Bram earn nothing more but instead arrange a second gift that is *younger* than a batch
of his own — e.g. have him deposit early, let a hundred days pass, then have Anke deposit and gift
— and claim once. `pointsExpiringNext` must then answer his own older batch on his own day, not
the gift. Either extend this test or add a sibling class named for that direction. Whatever you
write, check it by making the mutation above and confirming your new assertion goes red.

### Also worth fixing while you are in there (not blocking on their own)

- `PointsGivenBackAndForthDoNotOutliveTheirTwelveMonthsApiTest:108-113`. The comment says "a sweep
  run now finds points inside their twelve months and leaves them alone". It does not: the sweep
  asks `unspentBatchesEarnedBefore(now - 12 months + 2 days)`, so at hops on days 60 to 360 the
  batch is outside the query window and every in-loop sweep logs `batchesConsidered=0` — it never
  reaches the anniversary comparison the comment credits it with. The balance assertion still earns
  its place by catching points that vanished early; the sentence describing it should say what
  actually happens, because comments in this repository are load bearing.
- `ReceivedPointsCanBeGivenOnwardApiTest:112-116`. The onward hop returns the points to the
  customer who earned them, so the load-bearing date assertion — Anke's `pointsExpiringNextOn` —
  cannot tell "the second hop carried the original date" from "her own drawn-down batch
  resurfaced". Asserting the onward gift from the *recipient's* side too (`giftsOf(BRAM)` showing
  it `SENT`, which the harness already offers) would pin the hop down without a third customer.
- `AGiftOfPointsPastTheirAnniversaryIsSweptTheSameNightApiTest`. The criterion says both events are
  readable in the log and its javadoc names the two lines, but nothing asserts them; renaming
  either line fails no test. There is no log-assertion idiom anywhere in `src/test`, so this is not
  a convention you skipped — I read both lines out of the running application by hand and ticked
  the criterion on that. Left as a note, not a request.

### On AC6 and the third customer

Ticked as it stands. The criterion asks for an onward gift to a *third* customer and the
application seeds two, with no endpoint that creates one; the spec's own test list drops the word.
What the criterion is about — a sender giving away points he never earned, and the original date
surviving the hop — is asserted and I watched it happen live. If anybody wants a literal third
pot, that is a `DemoData` change and its own ticket, not a change to this test.

## Verified - attempt 2

Reviewed on `ticket/03-gifted-points-keep-the-age-they-were-earned-at`, everything it adds on top of
`agentic_engineered`. The diff is six test files under
`backend/src/test/java/io/dataroots/savingstreak/giftingpoints/` and this ticket; `git diff
agentic_engineered..HEAD -- backend/src/main frontend/src` is empty, so no production code changed.
One new class, `AGiftYoungerThanTheRecipientsOwnPointsIsSpentAfterThemApiTest`; six added assertions
in `ReceivedPointsCanBeGivenOnwardApiTest`; comment-only corrections in
`PointsGivenBackAndForthDoNotOutliveTheirTwelveMonthsApiTest`. No existing assertion was removed or
loosened.

### Checks

`cd backend && ./mvnw test` -> `Tests run: 247, Failures: 0, Errors: 0` / `BUILD SUCCESS`, run twice
(before and after the mutations below). `cd frontend && npm run typecheck` -> clean, exit 0.

### The blocking point from attempt 1 is closed

Attempt 1 was sent back because the spend order was only half guarded: the reviewer ordered gifted
batches to the front of `PointsCreditRepository.unspentOldestFirst` and the whole suite stayed green.
I applied that same mutation:

    order by case when credit.reason = io.dataroots.savingstreak.points.PointsReason.GIFT_RECEIVED
      then 0 else 1 end asc, credit.earnedAt asc, credit.id asc

`./mvnw test` now reports `Tests run: 247, Failures: 1` and the one failure is the new class:

    [the claim came out of his own older batch, not out of the younger gift]
    expected: 10L but was: 50L
      at AGiftYoungerThanTheRecipientsOwnPointsIsSpentAfterThemApiTest.java:124

I also ran the mirror special case — gifted batches ordered *last* (`then 1 else 0`) — which is the
other way AC5 could be broken. One failure, and a different class caught it:
`GiftedPointsAreSpentLikeAnyOtherPointsApiTest.java:119`, `[the forty came off the gift, so what goes
next is what is left of it] expected: 20L but was: 50L`. The two directions are now each guarded by
exactly one class, which is what the criterion needed.

### Five mutations in total, all red

Every mutation was reverted; `git status --porcelain` is empty and the suite is green again.

| Mutation | Result |
| --- | --- |
| `unspentOldestFirst` orders `GIFT_RECEIVED` first | 1 failure: the new class, line 124 |
| `unspentOldestFirst` orders `GIFT_RECEIVED` last | 1 failure: `GiftedPointsAreSpentLikeAnyOtherPointsApiTest:119` |
| `unspentOldestFirst` ordered `earnedAt desc` (newest first) | 6 failures, incl. both spend classes |
| arriving batch dated `clock.instant()` instead of `batch.getEarnedAt()` | 6 failures, each on the assertion carrying the rule |
| sweep `continue`s on `reason == GIFT_RECEIVED` | 5 failures |
| `remainingPointsOf` excludes `GIFT_RECEIVED` | 12 failures across 9 classes |

The dating mutation fails each class on the sentence that names the rule, e.g.
`GiftedPointsExpireTwelveMonthsAfterTheyWereEarnedApiTest:107` `[twelve months after she earned them,
not twelve months after he was given them] expected: 2027-09-09 but was: 2028-07-06`, and
`PointsGivenBackAndForthDoNotOutliveTheirTwelveMonthsApiTest:110` `[after hop 1 they still go on the
day they were earned, not a year on from the hop] expected: 2027-09-09 but was: 2027-11-08`.

### Driven live over HTTP

Backend-only ticket, so curl against the running application on a throwaway database at DEBUG.
Transcript: `.scratch/peer-to-peer-gifting/logs/03-gifted-points-keep-the-age-they-were-earned-at.review.2.curl.log`;
application log: `...03-gifted-points-keep-the-age-they-were-earned-at.app.2.backend.log`.

**The inherited clock and the sweep either side of it (AC1, AC3, AC4, AC8).** Anke deposited EUR
40.00 on 2026-09-09 for 40 points; 300 days later she gave all 40 to Bram, who has never deposited a
euro. His pot answered `{'pointsBalance': 40, 'pointsExpiringNext': 40, 'pointsExpiringNextOn':
'2027-09-09'}` — her anniversary, not a year from the gift — and a `CINEMA_TICKET` claim was refused
with `"Cinema ticket costs 100 points, and you have 40."`. A sweep on 2027-09-08 took nothing and
said why:

    points batch still inside its twelve months batchId=2 customerId=2
      earnedAt=2026-09-09T19:12:55.055Z anniversary=2027-09-09T19:12:55.055Z pointsLeft=40
    points expired asAt=2027-09-08T19:13:14.341958Z batchesConsidered=1 batches=0 points=0

Two days on, the same sweep took the lot, 66 days into his ownership:

    points batch expired batchId=2 customerId=2 reason=GIFT_RECEIVED
      earnedAt=2026-09-09T19:12:55.055Z anniversary=2027-09-09T19:12:55.055Z pointsExpired=40

A third sweep changed nothing, and his gift list still showed the row after the points had gone.

**The spend order in the hard direction (AC5).** Bram deposited EUR 50.00 on 2027-09-10; a hundred
days later Anke deposited EUR 60.00 and gave him all 60, so the gift was the *younger* age in his
pot. The figures matched the new test exactly: `110` / `50` on 2028-09-10 (his day) -> claim
`SNACK_VOUCHER` -> `10` on his day -> claim `CHARITY_DONATION` -> `{'pointsBalance': 60,
'pointsExpiringNext': 60, 'pointsExpiringNextOn': '2028-12-19'}` (her day). Gifts-first would have
answered 50 on his day throughout. The log names the batch each claim came off of:

    points spent oldest first customerId=2 points=40 available=110 batchesWithSomethingLeft=2
      drawnOn=[batchId=3 reason=BASE_ACCRUAL earnedAt=2027-09-10T19:14:27.820Z taken=40 leftInIt=10]
    points spent oldest first customerId=2 points=10 available=70 batchesWithSomethingLeft=2
      drawnOn=[batchId=3 reason=BASE_ACCRUAL earnedAt=2027-09-10T19:14:27.820Z taken=10 leftInIt=0]

Both claims came out of `BASE_ACCRUAL`, never out of the gift. A sweep on 2028-09-11 — the far side
of *his* own anniversary — then took nothing, because the batch whose year was up was the one he had
spent; the pot still held all 60 on 2028-12-19.

**The onward hop and the chain (AC2, AC6).** Bram gave those 60 points he never earned on to Anke:
her pot answered `pointsExpiringNextOn: 2028-12-19`, the original earn date rather than a year from
either hop, and the hop is pinned from his side too — `GET /api/customers/2/gifts` led with
`(3, 'SENT', 60, 'Anke Peeters')` above `(2, 'RECEIVED', 60, ...)`. Three more hops at 30-day
intervals, with a sweep after each: the balance stayed 60 and `pointsExpiringNextOn` stayed
2028-12-19 every time. Past 2028-12-19 one sweep emptied both pots. Five hops bought the points
nothing.

**The overdue gift (AC7).** Anke held 90 points dated 2029-03-10 and no sweep was run for 379 days;
on 2030-03-24 her pot still read `{'pointsBalance': 90, 'pointsExpiringNext': 90,
'pointsExpiringNextOn': '2030-03-10'}` — a day already behind her. She gave all 90 to Bram, who was
credited in full on that same past day, and the very next sweep took them out of his pot. Both
events are one line each:

    points credited customerId=2 sourceReferenceId=9 reason=GIFT_RECEIVED points=90 batches=2
      oldestEarnedAt=2029-03-10T20:15:26.918Z
    points batch expired batchId=15 customerId=2 reason=GIFT_RECEIVED
      earnedAt=2029-03-10T20:15:26.918Z anniversary=2030-03-10T20:15:26.918Z pointsExpired=30
    points batch expired batchId=16 customerId=2 reason=GIFT_RECEIVED
      earnedAt=2029-03-10T20:15:26.929Z anniversary=2030-03-10T20:15:26.929Z pointsExpired=60
    points expired asAt=2030-03-24T20:15:26.986409Z batchesConsidered=2 batches=2 points=90

The gift record outlived the points: his list still held all nine rows afterwards.

**A refusal quoting a mixed pot (AC8).** With 30 points of his own and 60 gifted, the `CINEMA_TICKET`
refusal read `"Cinema ticket costs 100 points, and you have 90."`, logged as
`claim rejected customerId=2 reward=CINEMA_TICKET kind=NOT_ENOUGH_POINTS reason=Cinema ticket costs
100 points, and you have 90.` — the WARN-with-a-reason the lab asks for.

**Logging.** Every flow left `io.dataroots.savingstreak` lines: `gift given giftId=... givenAt=...`
at INFO, `points credited ... reason=GIFT_RECEIVED ... oldestEarnedAt=...` at INFO, and at DEBUG
`gift judged against the sender's pot`, `points to move judged against the sender's batches`,
`points moved oldest first ... drawnOn=[batchId=13 reason=GIFT_RECEIVED earnedAt=2029-03-10... ]`
(an already-gifted batch drawn on with its date intact), `points batches considered for expiry` and
`points batch still inside its twelve months`. No `ERROR` and no stack trace in the whole run; the
only exception-resolver lines are the two expected reward refusals.

**The page.** Nothing to drive: the gift page is ticket 06 and is still in `issues/`, and this branch
touches no frontend file. I loaded http://localhost:5173/ under Playwright anyway and signed in as
Bram — the home screen renders fully styled with his two claims and `0 points to spend`, and the
console held only Vite's connect lines, React's devtools notice and two StrictMode `ERR_ABORTED`
aborts. Browser log:
`.scratch/peer-to-peer-gifting/logs/03-gifted-points-keep-the-age-they-were-earned-at.review.2.browser.log`.
The `ECONNREFUSED` proxy errors in `...app.2.frontend.log` are all stamped 21:05:14, the moment Vite
started ahead of the backend, and predate every request in this review.

### The two "also worth fixing" notes from attempt 1

Both done. `PointsGivenBackAndForthDoNotOutliveTheirTwelveMonthsApiTest` no longer claims the in-loop
sweep reaches the anniversary comparison; it now says the batch is younger than the cut-off the sweep
gathers candidates by and that the balance beside it is what catches points ending early. (Worth
noting for the next reader: the day-before sweep in `GiftedPointsExpireTwelveMonths...` *does* reach
the comparison — `batchesConsidered=1 batches=0` with a `still inside its twelve months` line — so
that half of AC1 is guarded live as well as by assertion.) `ReceivedPointsCanBeGivenOnwardApiTest`
now pins the onward hop from the sender's side with `giftsOf(BRAM)`. The third note (no log-assertion
idiom for AC7) was left as a note by attempt 1's reviewer and is still a note: I read both lines out
of the running application by hand, quoted above.

### AC6 and the third customer

Unchanged from attempt 1 and not reopened: the application seeds two customers and no endpoint
creates a third, so the onward hop returns to the original earner. What the criterion is about — a
sender giving away points he never earned, and the original date surviving the hop — is asserted from
both sides and I watched it happen live. A literal third pot is a `DemoData` change and its own
ticket.

### One note for whoever touches these tests next (not blocking, nothing to redo)

A second, independent correctness pass over the same range agreed with this sign-off and found no
test that would pass with the rule broken. It added two mutations I had not run, both caught: dating
*every* arriving slice at the oldest drawn batch rather than at its own goes red in
`ReceivedPointsCanBeGivenOnwardApiTest`, and it confirmed `daysPass` is calendar-day arithmetic in
Europe/Brussels through `MovableClock`, so the 360- and 366-day sums here cannot drift across a DST
change. It raised one accuracy point worth writing down, because comments are load bearing in this
repository:

- `AGiftYoungerThanTheRecipientsOwnPointsIsSpentAfterThemApiTest:54`. The javadoc on
  `SNACK_VOUCHER_COSTS` reads "less than his own batch, so the first claim comes out of it". That is
  the wrong mechanism: the claim comes out of his batch because it is the *oldest*, not because 40 is
  less than 50 — under the gifts-first special case this very class exists to catch, 40 < 50 would
  not put the claim there at all. What the 40 actually buys is that his batch survives the claim with
  the readable 10 left in it. One sentence, in the one class whose job is to tell oldest-first from
  gifts-first. Not blocking: the assertion below it is correct and is the one that goes red under the
  mutation.
