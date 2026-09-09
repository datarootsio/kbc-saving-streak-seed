# 01: A customer can give points to another customer

Status: needs-info

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

- [ ] `POST /api/customers/{customerId}/gifts`, naming the recipient by the contact details they bank under and the points to give, answers 201 with the gift: its id, a direction of `SENT`, both parties by id and name, the points, and the moment off the application's clock.
- [x] The recipient is found by contact details the way sign-in finds them: trimmed, matched case-insensitively.
- [x] The sender's points balance falls by exactly the gift and the recipient's rises by exactly the gift.
- [x] A gift is drawn from the sender's oldest points first.
- [x] A gift larger than any one of the sender's batches is drawn from as many as it needs.
- [x] Each slice drawn arrives as a batch of the recipient's dated at the moment the original batch was earned, so a gift drawn from batches of different ages arrives as batches of different ages.
- [x] Gifted batches are credited under a reason of their own, and that reason does not appear in any deposit's breakdown of what it earned.
- [x] One gift row is written per gift, carrying both customers, the points and the moment.
- [x] INFO says what went where; the slices drawn are readable at DEBUG; the DEBUG gathering is guarded by `isDebugEnabled`.

## Review feedback - attempt 1

Sent back for one defect. Almost all of this ticket is right and verified end to end — read the
"what is already proven" list below before you change anything, so you do not redo work or disturb
behaviour that is correct.

### Blocking: a single POST to the new endpoint can 500 or kill the JVM

`wholePositivePointsIn` in
`backend/src/main/java/io/dataroots/savingstreak/gifting/GiftingService.java` parses the typed figure
with `new BigDecimal(typed)` and then renders **the parsed `BigDecimal`** into the refusal text.
`BigDecimal` accepts an arbitrary exponent for almost nothing, so a scale of 10^9 costs nothing to
parse and a fortune to render. Three payloads, each reachable from one request with any valid
recipient address:

| `points` | route | what happens |
| --- | --- | --- |
| `1e999999999` | `longValueExact()` throws, catch at line 194 calls `figure.toBigInteger()` | uncaught `ArithmeticException` → **HTTP 500** |
| `1.5e-999999999` | `stripTrailingZeros().scale() > 0`, line 186 calls `figure.toPlainString()` | renders 10^9 characters → **`OutOfMemoryError`** |
| `-1e-999999999` | `signum() <= 0`, line 179 calls `figure.toPlainString()` | renders 10^9 characters → **`OutOfMemoryError`** |

Reproduce the 500 against a running application — it is safe, because it throws rather than
allocating:

    curl -i -X POST http://localhost:8080/api/customers/1/gifts \
      -H 'Content-Type: application/json' \
      -d '{"recipientContactDetails":"bram.devos@example.be","points":"1e999999999"}'

What came back on attempt 1:

    {"timestamp":"2026-09-09T14:57:02.402+00:00","status":500,"error":"Internal Server Error","path":"/api/customers/1/gifts"}

and in the backend log, with **no `gift rejected` WARN anywhere near it**:

    ERROR ... Servlet.service() ... threw exception [Request processing failed:
      java.lang.ArithmeticException: BigInteger would overflow supported range] with root cause
    java.lang.ArithmeticException: BigInteger would overflow supported range
        at java.base/java.math.BigDecimal.toBigInteger(BigDecimal.java:3542)
        at io.dataroots.savingstreak.gifting.GiftingService.wholePositivePointsIn(GiftingService.java:194)
        at io.dataroots.savingstreak.gifting.GiftingService.give(GiftingService.java:108)

**Do not fire the two `OutOfMemoryError` payloads at a shared application** — an OOME in a Tomcat
worker destabilises the JVM for every other request. They were proved standalone instead, in a
program written from the source and mirroring the method's control flow exactly, under
`java -Xmx256m`:

    1.5e-999999999 -> parsed ok, signum=1 scale=1000000000 strippedScale=1000000000
      *** ESCAPED THE METHOD: java.lang.OutOfMemoryError : Java heap space
    -1e-999999999  -> parsed ok, signum=-1 scale=999999999 strippedScale=999999999
      *** ESCAPED THE METHOD: java.lang.OutOfMemoryError : Java heap space
    1e999999999    -> parsed ok, signum=1 scale=-999999999 strippedScale=-999999999
      *** ESCAPED THE METHOD: java.lang.ArithmeticException : BigInteger would overflow supported range

Why this is blocking rather than a nit, and why it is this ticket's and not ticket 02's: the ticket
says the figure is carried as typed so that `2.5` and `abc` "come back as refusals in words rather
than being coerced into something plausible". A figure that kills the process is not a refusal in
words. And the lab's own rule — a refusal you triggered that left no line saying why is a missing
acceptance criterion — is broken here: this refusal logs no WARN at all, only an ERROR stack trace.
Criterion 1 is unticked for this reason and nothing else.

The `abc` branch at line 173 already does the right thing: it echoes the trimmed **input string**
`typed`, not the parsed value. The smallest honest fix is to make the other three branches do the
same, or to bound precision and scale before anything renders the parsed figure. Whatever you
choose, keep the wording of the ordinary cases exactly as it is — `2.5`, `abc`, `0`, `-5` and the
over-balance sentence were all checked on attempt 1 and read well.

### Also worth fixing while you are in there (none of these blocked the pass)

- The javadoc miscounts the refusals. `GiftRefused`'s class summary and its `Kind` javadoc say
  "four", and `GiftingService#give`'s javadoc says "Four things are refused and nothing else" and
  then lists five. Five kinds are declared; `NO_SUCH_CUSTOMER` is the one the prose drops.
- `GiftingService#give`'s javadoc claims "every one of them that does not need the gift's identifier
  is settled before anything at all is written". The shortfall check needs no gift id — it is a pure
  read of the sender's batches — and it nevertheless runs *after* `gifts.save(...)`. The rollback
  makes that safe (verified below), but the sentence describes the opposite of what the
  save-before-move order actually concedes. Say what it really does.
- `PointsService#movePoints` validates only `points <= 0`, while its javadoc asserts that both
  customers exist and differ. Harmless today because `GiftingService` is the only caller and checks
  both, but `spend` and the loyalty credit both refuse bad input, so this is asymmetric with its
  neighbours: a future second caller passing an unknown `toCustomerId` would silently create
  ownerless credits, and `from == to` would silently re-partition one pot.
- Nothing in the diff covers any refusal path with a test — not the five kinds, not the points-figure
  parser, not the HTTP status mapping. Ticket 02 owns their wording and their suite, so that is not
  a criterion here, but it is exactly the gap that let the blocking bug through: one test over the
  parser would have caught all three payloads above.
- `That gift costs 1 points` reads ungrammatically in the singular. Ticket 02's wording call.
- A blank recipient field answers 400 (`A gift needs the email address of the customer it is going
  to.`) where an unknown one answers 404. Ticket 02's call, noted so it is not lost.

### What is already proven — do not redo it, and try not to disturb it

All verified on attempt 1 against a running application on a throwaway database, backend at DEBUG.
Criteria 2 to 9 are ticked on this evidence.

- **Checks.** `cd backend && ./mvnw test` → 219 tests, 0 failures, 0 errors. `cd frontend && npm run
  typecheck` (Node v24.16.0) → clean. No existing test was modified or weakened; the only `src/test`
  changes are additive. No frontend file is touched, which is right — the page is ticket 06.
- **The 201 and its shape.** `{"id":1,"direction":"SENT","senderId":1,"senderName":"Anke Peeters",
  "recipientId":2,"recipientName":"Bram De Vos","points":8,"givenAt":"2026-12-18T15:46:48.135Z"}`,
  where `givenAt` sits on a clock wound 100 days forward rather than on the machine's date.
- **The address is matched sign-in's way.** `"  BRAM.DEVOS@EXAMPLE.BE "` resolved to Bram. Extracting
  the sentence into `AccountsService.noCustomerBanksUnderThoseContactDetails()` left sign-in
  byte-identical: an unknown address still answers `No customer banks here under that email
  address.`, and a padded, shouted valid address still signs in.
- **Conservation.** 21 → 13 for the sender, 0 → 8 for the recipient. Over a longer session (five more
  gifts, a whole-balance gift back, one expiry sweep) the two pots held 30 + 5 = 35, which is
  21 + 20 earned minus the 6 that expired. Nothing created, skimmed or destroyed.
- **Oldest first, over as many batches as needed**, from the log — the oldest batch emptied, the next
  dipped into, a third batch of the same age as the second left alone:
  `points moved oldest first fromCustomerId=1 toCustomerId=2 points=8 available=21
  batchesWithSomethingLeft=3 slices=2 drawnOn=[batchId=1 reason=BASE_ACCRUAL
  earnedAt=2026-09-09T14:46:33.954Z taken=6 leftInIt=0] [batchId=2 reason=BASE_ACCRUAL
  earnedAt=2026-12-18T15:46:34.091Z taken=2 leftInIt=7]`
- **Inherited dating, proved three ways.** The recipient's `pointsExpiringNext` became `6` on
  `2027-09-09` — twelve months after *she* earned them, months before he was given them. The gift's
  own DEBUG line reads `drawnOn=[points=6 earnedAt=2026-09-09T14:46:33.954Z] [points=2
  earnedAt=2026-12-18T15:46:34.091Z]`. And winding to 2027-09-14 and running `expireOldPoints` took
  exactly the older slice: `points batch expired batchId=4 customerId=2 reason=GIFT_RECEIVED
  earnedAt=2026-09-09T14:46:33.954Z anniversary=2027-09-09T14:46:33.954Z pointsExpired=6`.
- **A reason of its own, out of every deposit's breakdown.** The arriving batches carry
  `reason=GIFT_RECEIVED` with `source_reference_id` 1 and 2, which collide with deposit ids 1 and 2 —
  the hardest case available. Both of the sender's deposits still report base-only breakdowns of 6
  and 9, and the recipient's own later 20.00 deposit reports 20 base and nothing else.
- **One row per gift, and the pre-move save really does roll back.** Read straight out of the SQLite
  file: after two successful gifts and **nine** refusals the `gift` table held exactly two rows,
  carrying both customer ids, the points and the moment. That includes the two `NOT_ENOUGH_POINTS`
  refusals, which are thrown *after* the row is saved.
- **Ordinary refusals all answer in words with a WARN behind them**: unknown recipient 404, unknown
  sender 404, self-gift 400, `2.5` 400, `abc` 400, `0` 400, `-5` 400, over-balance 400
  (`That gift costs 99 points, and you have 13.`). Sample: `gift rejected senderCustomerId=1
  recipientAsGiven=bram.devos@example.be kind=NOT_ENOUGH_POINTS reason=That gift costs 99 points,
  and you have 13.`
- **Edges that hold**: `5.0` and a bare JSON `1` go through as whole numbers; `2.5` sent as a JSON
  number is still refused; blank and missing fields and an empty body all answer 400 in words; a
  sender's *expired* batch is not giftable (27 live plus 6 expired was refused 33 with `you have
  27`); and there is no cap or cooldown — a whole-balance gift of 27 and five gifts of 1 in a row
  all returned 201.
- **Logging is otherwise complete and in the surrounding style.** One INFO per gift
  (`gift given giftId=1 senderCustomerId=1 recipientCustomerId=2 points=8
  givenAt=2026-12-18T15:46:48.135Z`), the ledger's existing credit line carrying the new reason
  (`points credited customerId=2 sourceReferenceId=1 reason=GIFT_RECEIVED points=8 batches=2`), and
  DEBUG for the inputs behind the decision. `LoggerFactory.getLogger(GiftingService.class)` is the
  only new logger and there is no `System.out` in the diff. The `isDebugEnabled` guard a previous
  review made blocking is in place in the shape `spend` uses: `PointsService.java:501` reads the flag
  once into a local before the loop and guards both the gathering and the emit, and
  `GiftingService.java:112` and `:141` guard the extra `balanceOf` query and the slice rendering.
- **Page regression check.** No frontend code changed, but the page was driven with Playwright
  anyway: signing in as the recipient renders a fully styled home screen showing his gifted points as
  spendable on the catalogue, with no `pageerror` and no `console:error`.

Apart from the `1e999999999` request above, which was fired deliberately, the backend log for the
whole session contains no 500 and no ERROR.

## Review feedback - attempt 2

Sent back for one defect, again in `wholePositivePointsIn`, and again about a figure that cannot be
refused in words. **Almost everything in this ticket is right and was verified end to end on this
attempt** — read "what is already proven" below before changing anything, and change nothing else.

Attempt 1's blocking defect is genuinely and correctly fixed. That half is done: every refusal
sentence now quotes the trimmed input string and nothing renders the parsed `BigDecimal`. I fired
all three killer payloads at a running application and they answer 400 in words in ~10ms with a
WARN behind each. That work is not in question and must not be undone.

### Blocking: an unbounded `points` string pins a CPU for minutes on one POST

The fix stopped *rendering* the parsed figure. It never bounded *parsing* it. `new
BigDecimal(typed)` at `GiftingService.java:175` and especially `figure.stripTrailingZeros()` at
`:195` are both quadratic in the digit count, and nothing anywhere limits how many digits arrive.

Measured live against a running application, `POST /api/customers/1/gifts` with a real sender id, a
real recipient address, and `points` = `"1"` followed by N zeros:

| digits | body | time to answer |
| --- | --- | --- |
| 30,000 | 30 KB | 0.32 s |
| 100,000 | 100 KB | 3.50 s |
| 120,000 | 120 KB | 4.68 s |

Exactly 4x per doubling, confirmed standalone on JDK 17.0.15 (`stripTrailingZeros` alone: 100k
digits 2.76s, 200k 11.0s). Extrapolated, a ~1 MB `points` value pins one Tomcat worker and one core
for **about five and a half minutes** before the request ever reaches the database.

The 1 MB body really does get through — this is not theoretical. There is no request-size limit
configured anywhere in `backend/src/main/resources` (Tomcat's `maxPostSize` covers only form
encoding, not JSON), and a 1,048,642-byte body was accepted and answered rather than refused with a
413. Reproduce the acceptance cheaply, without burning the CPU, using non-numeric text of the same
size — `BigDecimal` fails on the first character, so it comes back instantly:

    python3 -c "import json;open('big.json','w').write(json.dumps({'recipientContactDetails':'bram.devos@example.be','points':'a'*1048576}))"
    curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/api/customers/1/gifts \
      -H 'Content-Type: application/json' --data-binary @big.json

That answers `400` in 0.09s and echoes a 1,048,635-character `detail`. Swap the `a`s for digits and
the same request becomes minutes of CPU. `GET /api/customers` publishes both the sender ids and the
recipient addresses needed, and this application has no authentication, so it is one POST from
anybody who can reach the port. A handful in parallel make the training application unusable, with
only a `gift rejected ... kind=NOT_ENOUGH_POINTS` WARN arriving minutes later to show for it.

**Why this blocks rather than being noted.** It is attempt 1's defect family reached by the other
route, and it fails the standard that review already set: a figure that cannot be answered promptly
is not "a refusal in words", which is the entire reason the ticket carries the figure as typed. I
had initially passed this on the reasoning that the same hazard was pre-existing and worse on the
deposit path, and **that reasoning was wrong** — I measured both at the same payload size and the
new endpoint is far worse, because the deposit path has no `stripTrailingZeros` call:

| 120,000-digit figure | time |
| --- | --- |
| `POST /api/savings-accounts/1/deposits` (pre-existing) | 0.35 s |
| `POST /api/customers/1/gifts` (this branch) | **4.68 s** |

So this branch introduces the worst input-handling hazard in the application rather than inheriting
it.

**The fix should be one line and needs no new wording.** A whole number of points can never need
more than 19 characters, so an early length check on `typed` — before `new BigDecimal` — routed into
the **existing** `NOT_ENOUGH_POINTS` sentence keeps every sentence verified below byte-identical and
stays clear of ticket 02, which owns refusal wording. The same bound also fixes the response
echoing the whole typed string back (the 120 KB probe got a 120 KB 400 body). Please add a case to
`APointsFigureIsAnsweredInWordsApiTest` covering a long digit string, since that class is already
exactly the right home for it.

### Also worth fixing while you are in there (neither blocked the pass on its own)

- **`GiftingService.give` NPEs on a null recipient.** `accounts.customerIdentifiedBy` calls
  `contactDetails.trim()` (`AccountsService.java:43`) with no null check, so a null
  `recipientContactDetails` is a 500 with no `gift rejected` WARN — not one of the five refusals
  `give`'s javadoc promises. Unreachable over HTTP today because `CustomerController.give` guards
  null and blank first, but `give` is the module's public entry point and later tickets add callers.
  Treat null or blank as `NO_SUCH_RECIPIENT` inside `give`.
- **A comment in `PointsService` (around :553) overclaims.** It says the credit line is "the same
  line a deposit's own credit and a loyalty bonus write, in the same words". It is not: a deposit
  writes `depositId= multiplier= pointsByReason=`, the loyalty bonus `depositId= reason= points=
  earnedAt=`, and the gift `sourceReferenceId= reason= points= batches=`. They all grep on `points
  credited`, but the keys differ and the gift's is the only one of the three carrying no date — so a
  reviewer at INFO alone cannot see the inherited earned-at that is the whole point of the feature.
  Either soften the sentence or put the oldest slice's `earnedAt` on the line.

### What is already proven — do not redo it, and try not to disturb it

All verified on **attempt 2** against a running application on a throwaway database at DEBUG, over a
ten-gift session with the clock wound forward and an expiry sweep run. Criteria 2 to 9 are ticked on
this evidence; criterion 1 is unticked for the blocking item above and nothing else.

- **Checks.** `cd backend && ./mvnw test` → **223 tests, 0 failures, 0 errors** (219 before, 4 new).
  `cd frontend && npm run typecheck` → clean on Node v24.16.0. The `src/test` changes are additive
  only — no line removed from any existing test file — and no frontend file is touched, which is
  right: the page is ticket 06.
- **Attempt 1's three killers are fixed.** `1e999999999` → 400 `That gift costs 1e999999999 points,
  and you have 90.`; `1.5e-999999999` → 400 `Points are whole, and …`; `-1e-999999999` → 400 `A gift
  has to be more than zero points, and …`. All ~10ms, all with a WARN. Thirteen further hostile
  figures I tried (`1e2147483647`, `0e999999999`, `1E+2147483646`, `9223372036854775808`, `+91`,
  `0x10`, `Infinity`, `NaN`, `1_000`, …) all answered in words under 30ms.
- **The 201 and its shape.** `{"id":1,"direction":"SENT","senderId":1,"senderName":"Anke Peeters",
  "recipientId":2,"recipientName":"Bram De Vos","points":90,"givenAt":"2027-01-27T16:14:10.423Z"}`,
  with `givenAt` on the wound-forward clock rather than the machine's date.
- **The address is matched sign-in's way.** `"   ANKE.PEETERS@EXAMPLE.BE  "` resolved to Anke.
  Extracting the sentence into `AccountsService` left sign-in unchanged: unknown address still 404
  `No customer banks here under that email address.`, rendered in the styled error panel on the
  page; a padded, shouted valid address still signs in 200.
- **Conservation.** 90 → 0 and 0 → 90 on the first gift; 90 → 20 and 0 → 70 on the gift back. Across
  the session: 130 earned, 60 expired, 40 spent on a reward, exactly 30 left in the two pots.
- **Oldest first, across as many batches as needed.** `points moved oldest first fromCustomerId=2
  toCustomerId=1 points=70 available=90 batchesWithSomethingLeft=2 slices=2 drawnOn=[batchId=3
  reason=GIFT_RECEIVED earnedAt=2026-12-18T16:13:22.835Z taken=60 leftInIt=0] [batchId=4
  reason=GIFT_RECEIVED earnedAt=2027-01-27T16:13:22.887Z taken=10 leftInIt=20]`
- **Inherited dating, proved three ways.** Every `GIFT_RECEIVED` row in the SQLite file carries its
  source batch's `earned_at`, never a gift's `given_at`. The recipient's `pointsExpiringNext` became
  60 on **2027-12-18**, the anniversary of the day the *sender* earned them — a month before the
  gift existed. And winding to 2027-12-19 and running `expireOldPoints` took exactly the inherited
  slices: `points batch expired batchId=5 customerId=1 reason=GIFT_RECEIVED
  earnedAt=2026-12-18T16:13:22.835Z anniversary=2027-12-18T16:13:22.835Z pointsExpired=52`. The
  sweep one day earlier took nothing. A reset clock would have kept these alive to 2028-01-27.
- **A reason of its own, out of every deposit's breakdown.** Gift batches carry
  `source_reference_id` 1 and 2, colliding with deposit ids 1 and 2 — the hardest case available.
  Both of Anke's deposits still report base-only 60 and 30, and Bram's later 40.00 deposit reports
  40 base and nothing else while holding 28 gifted points. (`PointsCreditRepository.earnedBy` is the
  only query in the codebase over that column and it filters by reason, so the collision is safe by
  construction; no aggregate anywhere sums `PointsCredit.points`.)
- **One row per gift, and the pre-move save really rolls back.** After 10 gifts and 26 refusals the
  `gift` table held exactly 10 rows with both customer ids, the points and the moment. Three of
  those refusals were `NOT_ENOUGH_POINTS`, thrown *after* `gifts.save(...)`.
- **Ordinary refusals answer in words with a WARN.** Unknown recipient 404, unknown sender 404
  (`There is no customer 999.`), self-gift 400, `2.5` 400, `abc` 400, `0` 400, `-5` 400,
  over-balance 400 (`That gift costs 71 points, and you have 70.`), JSON number `2.5` 400. 26
  refusals produced 26 `gift rejected` WARNs, each with its kind and reason.
- **Edges.** `5.0` and a bare JSON `3` go through as whole numbers; a sender's *expired* points are
  not giftable (10 live plus 52 expired refused 11 with `you have 10`); five gifts of 1 back to back
  and a whole-balance gift all 201, so there is no cap or cooldown; a gift moves no money (the money
  ledger holds only the deposit) and leaves streak and multiplier untouched.
- **Gifted points are really spendable.** Driven with Playwright: signed in as the recipient, the
  home screen renders fully styled with 60 points to spend, and claiming the coffee voucher took 40
  of them and issued `SS-SNK-ZB2SQ3`. No `pageerror`; the one `console:error` is the 404 from the
  deliberate unknown-address sign-in.
- **Logging is complete and in the surrounding style.** `gift given giftId=1 senderCustomerId=1
  recipientCustomerId=2 points=90 givenAt=2027-01-27T16:14:10.423Z` (ten of them), the ledger's
  `points credited customerId=2 sourceReferenceId=1 reason=GIFT_RECEIVED points=90 batches=2`, and
  the guarded `gift drawn from the sender's oldest points first giftId=1 … drawnOn=[points=60
  earnedAt=2026-12-18T16:13:22.835Z] [points=30 earnedAt=2027-01-27T16:13:22.887Z]`. The
  `isDebugEnabled` guard a previous review made blocking is present in `spend`'s shape:
  `PointsService` reads the flag once into a local before the loop and guards both the gathering and
  the emit; `GiftingService.java:115` and `:144` guard the extra `balanceOf` query and the slice
  rendering. No `System.out` in the diff.
- **All four of attempt 1's "worth fixing" items were done**: the javadoc now counts five refusals
  and names `NO_SUCH_CUSTOMER`, `give`'s javadoc says what the save-before-move order really
  concedes, and `movePoints` refuses `fromCustomerId == toCustomerId` with a WARN and no longer
  claims to check that both customers exist.

Across the whole session, apart from the deliberately hostile requests, the backend log contains
**zero ERROR lines and zero 500s**.
