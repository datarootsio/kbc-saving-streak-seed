# 03: Gifted points keep the age they were earned at

Status: needs-info

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
- [ ] Gifted points can be spent on a reward, and are spent oldest-first alongside the recipient's own points.
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
