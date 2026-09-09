# 01: A customer can give points to another customer

Status: done

**Blocked by:** None (can start immediately).

**What to build:** A customer can hand a number of points to another customer of the bank, and the
points arrive. The sender's balance falls by exactly what they gave, the recipient's rises by exactly
the same, and nothing is created, skimmed or destroyed on the way. The gift is final the moment it is
made: no acceptance step, no pending state, nothing for the recipient to do.

The points come off the sender's oldest batches first — the same draw a reward claim already makes —
and each slice drawn arrives in the recipient's pot as a batch of its own **dated at the moment the
original batch was earned**, not at the moment of the gift. A gift drawn from three batches of
different ages therefore arrives as three batches of different ages. This is the one decision in the
feature a customer would not have guessed, and it is load-bearing: with no limit on how often people
may give, a fresh twelve months per gift would let two customers pass the same points back and forth
forever and keep them alive indefinitely. Ticket 03 is what proves the clock is inherited; this
ticket is what makes it so.

This is the tracer bullet: the Gifting module, the record of what was given, the ledger's one new way
in, and the endpoint that sends one. Gifting is built in `RewardsService`'s image — a claim and a
gift are the same shape of act — and depends on Accounts, Points, the clock and a repository of its
own. Points gains one operation: move points from one customer to another, oldest first, against a
stated source reference, returning the slices it moved or nothing when the sender is short (a
boolean-shaped answer like `spend`'s, so the refusal's wording stays with Gifting in ticket 02).
`PointsReason` gains `GIFT_RECEIVED`, and it is deliberately kept out of the set of reasons a deposit
can have earned under, so no deposit breakdown grows a field and no existing assertion moves.

The gift row is saved before the move, because the recipient's batches carry its id — the opposite
order to `RewardsService`, which spends before it saves, so say so in the code. One transaction
throughout.

Logging is part of this: one INFO per gift with its id, both customers, the points and the moment;
the ledger's existing `points credited` line covers the credit side with `reason=GIFT_RECEIVED`; and
one DEBUG line naming the slices drawn — batch id, earned-at, taken, left in it — gathered into a
single line and **guarded by `isDebugEnabled`**, because rendering a batch is work and the string is
thrown away at INFO. That guard is not optional: a previous review made it blocking on
character-for-character this shape of code in `spend`.

- [x] `POST /api/customers/{customerId}/gifts`, naming the recipient by the contact details they bank under and the points to give, answers 201 with the gift: its id, a direction of `SENT`, both parties by id and name, the points, and the moment off the application's clock.
- [x] The recipient is found by contact details the way sign-in finds them: trimmed, matched case-insensitively.
- [x] The sender's points balance falls by exactly the gift and the recipient's rises by exactly the gift.
- [x] A gift is drawn from the sender's oldest points first.
- [x] A gift larger than any one of the sender's batches is drawn from as many as it needs.
- [x] Each slice drawn arrives as a batch of the recipient's dated at the moment the original batch was earned, so a gift drawn from batches of different ages arrives as batches of different ages.
- [x] Gifted batches are credited under a reason of their own, and that reason does not appear in any deposit's breakdown of what it earned.
- [x] One gift row is written per gift, carrying both customers, the points and the moment.
- [x] INFO says what went where; the slices drawn are readable at DEBUG; the DEBUG gathering is guarded by `isDebugEnabled`.

## Verified

Reviewed on attempt 1 against `agentic_engineered..ticket/01-a-customer-can-give-points-to-another-customer`
(commits `f0b0cfe`, `fe69691`). Every checkbox above was re-derived from a running application; the
implementer's pre-ticked marks were not taken as evidence.

**Checks, run again by the reviewer.** `cd backend && ./mvnw test` → BUILD SUCCESS, 219 tests, 0
failures, 0 errors. `cd frontend && npm run typecheck` (Node v24.16.0) → clean. No existing test was
modified or weakened: the only change under `src/test` is additive (`support/GiftView.java`, and
`give`/`giveNaming`/`customerIdOf`/`contactDetailsOf` appended to `AnApplicationWithAClockToMove`).
No frontend file is touched at all, which is right — the page is ticket 06.

**Driven over HTTP on a throwaway database** (backend at DEBUG, log at
`logs/01-a-customer-can-give-points-to-another-customer.app.1.backend.log`). Anke deposited 6.00,
the clock was wound 100 days, she deposited 9.00 again, and she gave 8 points to
`"  BRAM.DEVOS@EXAMPLE.BE "`:

- **201 and the shape.** `{"id":1,"direction":"SENT","senderId":1,"senderName":"Anke Peeters","recipientId":2,"recipientName":"Bram De Vos","points":8,"givenAt":"2026-12-18T15:46:48.135Z"}`.
  `givenAt` is on the wound-forward clock, not the machine's date — the moment comes off the
  application's clock.
- **The address is matched sign-in's way.** Shouted and padded with spaces, it still resolved to Bram.
  Extracting the sentence into `AccountsService.noCustomerBanksUnderThoseContactDetails()` left
  sign-in byte-identical: `POST /sign-in` with an unknown address still answers
  `"No customer banks here under that email address."`, and a valid padded, shouted address still
  signs in.
- **Conservation.** Sender 21 → 13, recipient 0 → 8. At the end of a longer session (5 further gifts,
  a whole-balance gift back, one sweep) the two pots held 30 + 5 = 35, which is 21 + 20 earned minus
  the 6 that expired. Nothing created, skimmed or destroyed.
- **Oldest first, across as many batches as it needed**, quoted from the log:
  `points moved oldest first fromCustomerId=1 toCustomerId=2 points=8 available=21 batchesWithSomethingLeft=3 slices=2 drawnOn=[batchId=1 reason=BASE_ACCRUAL earnedAt=2026-09-09T14:46:33.954Z taken=6 leftInIt=0] [batchId=2 reason=BASE_ACCRUAL earnedAt=2026-12-18T15:46:34.091Z taken=2 leftInIt=7]`
  — the oldest batch emptied, the next one dipped into, a third batch of the same age left alone.
- **Inherited dating, proved three ways.** In the recipient's API read (`pointsExpiringNext` became
  `6` on `2027-09-09`, twelve months after *she* earned them and months before he was given them);
  in the ledger's own DEBUG line
  `gift drawn from the sender's oldest points first giftId=1 senderCustomerId=1 recipientCustomerId=2 points=8 slices=2 drawnOn=[points=6 earnedAt=2026-09-09T14:46:33.954Z] [points=2 earnedAt=2026-12-18T15:46:34.091Z]`;
  and by winding to 2027-09-14 and running `expireOldPoints`, which took exactly the older slice:
  `points batch expired batchId=4 customerId=2 reason=GIFT_RECEIVED earnedAt=2026-09-09T14:46:33.954Z anniversary=2027-09-09T14:46:33.954Z pointsExpired=6`.
  One gift, two arriving batches, two different anniversaries.
- **A reason of its own, out of every deposit's breakdown.** The two arriving batches carry
  `reason=GIFT_RECEIVED` with `source_reference_id` 1 and 2 — which collide with deposit ids 1 and 2,
  the hardest case available. Both of the sender's deposits still report `pointsEarned` 6 and 9 with
  base-only breakdowns, and the recipient's own later 20.00 deposit reports 20 base and nothing else.
  The reason filter in `PointsCreditRepository#earnedBy` is what makes that hold.
- **One row per gift, and the pre-move save rolls back.** Read straight out of the SQLite file: after
  two successful gifts and **nine** refusals, the `gift` table held exactly two rows carrying
  `sender_customer_id`, `recipient_customer_id`, `points`, `given_at`. That includes the two
  `NOT_ENOUGH_POINTS` refusals, which are thrown *after* the row is saved — so the single transaction
  really does leave nothing behind, which is the one non-obvious safety property in the design.

**Refusals and edges, all exercised**, each answered in words and each leaving a WARN saying why:
unknown recipient 404, unknown sender 404 (`There is no customer 999.`), self-gift 400, `2.5` 400,
`abc` 400, `0` 400, `-5` 400, over-balance 400 (`That gift costs 99 points, and you have 13.`), a
23-digit figure 400. Also checked: `5.0` and a bare JSON `1` go through as whole numbers; `2.5` sent
as a JSON number is still refused; blank/missing fields and an empty body answer 400 in words. A
sender's **expired** batch is not giftable — the recipient holding 27 live and 6 expired points was
refused 33 with `you have 27` — and there is no cap or cooldown: a whole-balance gift of 27 and five
gifts of 1 in a row all returned 201. Sample WARN:
`gift rejected senderCustomerId=1 recipientAsGiven=bram.devos@example.be kind=NOT_ENOUGH_POINTS reason=That gift costs 99 points, and you have 13.`

**Logging.** `grep "Completed 500\|ERROR"` over the whole backend log: **0 hits**. One INFO per gift
(`gift given giftId=1 senderCustomerId=1 recipientCustomerId=2 points=8 givenAt=2026-12-18T15:46:48.135Z`),
the ledger's existing credit line carrying the new reason
(`points credited customerId=2 sourceReferenceId=1 reason=GIFT_RECEIVED points=8 batches=2`), a WARN
on every refusal, DEBUG for the inputs behind the decision (`senderBalance=21`, `available=21`).
`LoggerFactory.getLogger(GiftingService.class)` is the only new logger and there is no `System.out`
anywhere in the diff. The `isDebugEnabled` guard the previous review made blocking is in place in the
shape `spend` uses: `PointsService.java:501` reads the flag once into a local before the loop and
guards both the gathering and the emit; `GiftingService.java:112` and `:141` guard the extra
`balanceOf` query and the slice-string rendering.

**Page regression check.** No frontend code changed, but the page was driven with Playwright anyway
(browser log at `logs/01-a-customer-can-give-points-to-another-customer.app.1.browser.log`): signing
in as the recipient renders a fully styled home screen showing his 27 gifted-and-earned points as
spendable on the catalogue. No `pageerror`, no `console:error`; the two `requestfailed ERR_ABORTED`
lines are the browser being closed on in-flight requests, and Vite's `ECONNREFUSED` entries are all
from 16:44, before the backend finished starting.

**Nits left for later, none of them ticket 01's business.** "That gift costs 1 points" reads
ungrammatically in the singular; a blank recipient field answers 400 where an unknown one answers
404; a figure too large for a `long` is reported as a shortfall rather than as a bad figure. All
three are refusal wording, which ticket 02 owns — and the refusal plumbing shipped here only because
the endpoint cannot answer without it. `GiftDirection.RECEIVED` is unreachable until ticket 05, as
expected for a tracer bullet that settles the one response shape.
