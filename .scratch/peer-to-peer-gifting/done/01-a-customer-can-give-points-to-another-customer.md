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
deposit path, and **that reasoning does not hold for this hazard** — I measured both at the same
payload size and the new endpoint is far worse, because the deposit path has no `stripTrailingZeros`
call:

| 120,000-digit figure | time |
| --- | --- |
| `POST /api/savings-accounts/1/deposits` (pre-existing) | 0.35 s |
| `POST /api/customers/1/gifts` (this branch) | **4.68 s** |

So the CPU-exhaustion hazard is introduced by this branch rather than inherited from its neighbours,
and that is what makes it this ticket's to close.

Be precise about which hazard is which, because there are two and only one of them is yours. The
**length** hazard above (a long digit string, scale 0) is this branch's. A separate **scale** hazard
(a small string with a huge exponent, `1.5e-999999999`) is what attempt 1 blocked, and gifting now
answers it correctly in ~10ms. Do not go looking for the scale hazard here — it is fixed. See the
out-of-scope note at the end for where it still lives.

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

### Out of scope for this ticket, recorded so it is not lost

**Do not fix this as part of ticket 01.** The scale hazard attempt 1 blocked gifting for is still
live one module over, on code this branch does not touch:
`POST /api/savings-accounts/{id}/deposits` and the withdrawal equivalent carry the amount as text
(`web/DepositRequest.java`), parse it with `new BigDecimal(amount.trim())`
(`web/SavingsAccountController.java:193` — a 14-character token, so Jackson's default number-length
limit never fires), and then render **the parsed figure** through `amount.toPlainString()` at
`deposits/AmountOfMoney.java:38` and `:44`. `{"amount":"1.5e-999999999"}` or `{"amount":"-1e-999999999"}`
therefore renders on the order of 10^9 characters and takes the heap with it.

I confirmed the mechanism at a bounded scale rather than firing the real thing at a shared
application: `{"amount":"1.5e-100000"}` on a deposit came back as a **100,070-character** refusal
(`An amount of money has at most two decimal places, and 0.0000…0015 has 100001.`), which is the
parsed figure being rendered, exactly as attempt 1 described for gifting. Do not fire the
nine-digit-exponent version at a running instance — an `OutOfMemoryError` in a Tomcat worker
destabilises the JVM for every other request.

This is pre-existing and not a regression from this ticket (`AmountOfMoney.java` and
`SavingsAccountController.java` have an empty diff against `agentic_engineered`), so it does not
affect this verdict, and gifting is now the better-behaved of the two paths. It wants a ticket of its
own: the fix is the mirror of the one asked for above, and it would change deposit and withdrawal
refusal *wording*, which a gifting slice should not own.

## Review feedback - attempt 3

Sent back for one defect, in `wholePositivePointsIn` again, and again a figure that cannot be
refused in words. **Everything else in this ticket is right and was verified end to end on this
attempt, from a fresh throwaway database** — read "what is already proven" below before changing
anything, and change nothing else.

Attempt 2's blocking defect is genuinely and correctly fixed, and impressively so. The length bound
does exactly what was asked: the 1 MB digit string that was extrapolated at five and a half minutes
now answers 400 in **0.016s**, and a 4 MB one in 0.023s. That work is not in question and must not
be undone.

### Blocking: `stripTrailingZeros()` is the one BigDecimal call left outside a `try`, and a 14-character `points` value answers HTTP 500

This is attempt 1's defect family reached by a **third** route, and neither previous fix closes it.
The length bound cannot: the payload is 14 characters. Quoting `typed` instead of the parsed figure
cannot: nothing is rendered, the throw happens before any sentence is built.

`GiftingService.java:242` is `if (figure.stripTrailingZeros().scale() > 0) {`. It is the only
`BigDecimal` call in the method that is not inside a `try` — the `try/catch (ArithmeticException)`
begins at `:246`, one line too late. `stripTrailingZeros()` decrements the scale once per zero it
strips and `BigDecimal.checkScale` throws `ArithmeticException: Overflow` when the scale walks past
`Integer.MIN_VALUE`. So a positive figure with a huge positive exponent **and at least two trailing
zeros** faults instead of being refused.

Reproduce against a running application — it is safe, because it throws rather than allocating:

    curl -i -X POST http://localhost:8080/api/customers/1/gifts \
      -H 'Content-Type: application/json' \
      -d '{"recipientContactDetails":"bram.devos@example.be","points":"100e2147483647"}'

Three payloads, each 500 on attempt 3, measured live:

| `points` | chars | answer |
| --- | --- | --- |
| `100e2147483647` | 14 | **HTTP 500** |
| `1000e2147483646` | 15 | **HTTP 500** |
| `100.00e2147483647` | 17 | **HTTP 500** |

What came back:

    {"timestamp":"2026-09-09T15:54:22.451+00:00","status":500,"error":"Internal Server Error","path":"/api/customers/1/gifts"}

and in the backend log, with **no `gift rejected` WARN anywhere near it** — the log went from 0
ERROR lines to 9 on those three requests, and the only WARNs in that second are the three
*neighbouring* payloads that were correctly refused:

    ERROR ... Servlet.service() ... threw exception [Request processing failed:
      java.lang.ArithmeticException: Overflow] with root cause
    java.lang.ArithmeticException: Overflow
        at java.base/java.math.BigDecimal.checkScale(BigDecimal.java:4522)
        at java.base/java.math.BigDecimal.createAndStripZerosToMatchScale(BigDecimal.java:4964)
        at java.base/java.math.BigDecimal.stripTrailingZeros(BigDecimal.java:3091)
        at io.dataroots.savingstreak.gifting.GiftingService.wholePositivePointsIn(GiftingService.java:242)
        at io.dataroots.savingstreak.gifting.GiftingService.give(GiftingService.java:145)

**Be precise about the boundary, because the near misses are why two reviews missed this.** The
figures the last two attempts probed (`1e2147483647`, `1E+2147483646`, `0e999999999`,
`1e999999999`) all survive, and I confirmed each of them answers 400 in words on this attempt. What
is needed is *two or more trailing zeros* to strip on top of an already-bottomed-out scale.
Standalone on JDK 17.0.15:

    100e2147483647     parsed scale=-2147483647  *** stripTrailingZeros THREW ArithmeticException: Overflow
    1000e2147483646    parsed scale=-2147483646  *** THREW
    100.00e2147483647  parsed scale=-2147483645  *** THREW
    10e2147483647      parsed scale=-2147483647  stripped scale=-2147483648  OK  (one zero: lands exactly on MIN_VALUE)
    1e2147483647       parsed scale=-2147483647  stripped scale=-2147483647  OK  (no zeros to strip)
    120e2147483647     parsed scale=-2147483647  stripped scale=-2147483648  OK  (one zero)
    1200e2147483646    parsed scale=-2147483646  stripped scale=-2147483648  OK  (two zeros, but two headroom)

**Why this blocks rather than being noted, and why it is this ticket's.** It fails the standard the
previous two reviews already set on this exact method: the ticket carries the figure as typed so
that `2.5` and `abc` "come back as refusals in words rather than being coerced into something
plausible", and `100.00e2147483647` is precisely a `2.5`-shaped figure — something with digits after
the point — that answers as a fault in the application instead. It also breaks the lab's own rule:
a refusal you triggered that left no line saying why is a missing acceptance criterion, and this one
leaves an ERROR stack trace and no WARN at all. And it is introduced by this branch rather than
inherited: `grep -rn stripTrailingZeros backend/src/main/java/` returns **exactly one call site in
the whole main tree**, `GiftingService.java:242`. The deposit path does not have it, so the same
reasoning attempt 2 used to make the length hazard this ticket's applies here unchanged.

Criterion 1 is unticked for this and nothing else.

**The fix is one line and needs no new wording.** Move `:242` inside a `try` that catches
`ArithmeticException`, or bound `figure.scale()` before stripping. Either way the answer is already
written: a whole positive figure too large to count in points is the `NOT_ENOUGH_POINTS` sentence at
`:246-:249`, which quotes `typed` and is already verified. Please add `100e2147483647` to
`APointsFigureIsAnsweredInWordsApiTest`'s `@ValueSource` — that class is exactly the right home, and
one case there would have caught this.

While you are in the method: `stripTrailingZeros()` is the last unguarded `BigDecimal` call, so this
should be the end of the family. `new BigDecimal(typed)` is guarded, `signum()` and `scale()` cannot
throw, `longValueExact()` is guarded, and the length bound covers the cost of parsing.

### Also worth fixing while you are in there (neither blocked the pass on its own)

- **A megabyte of letters is answered as a shortfall.** `points` = `"a" * 1048576` comes back
  `That gift costs aaaaaaaa...aaa... points, and you have 5.` The length gate fires before the
  parse, so a long non-numeric string gets the `NOT_ENOUGH_POINTS` sentence rather than "is not a
  number". This is a direct consequence of attempt 2's own prescription and the implementer flagged
  it honestly, so it is not being pressed here — but the sentence quotes something nobody typed as a
  figure and the trailing `...` makes it neither the input nor a number. Ticket 02's wording call;
  recorded so it is not lost.
- **A blank recipient still answers 400 at the endpoint and 404 from the module.**
  `CustomerController.give` refuses null/blank with 400 `A gift needs the email address of the
  customer it is going to.`, while `GiftingService.give:139` refuses the identical input as
  `NO_SUCH_RECIPIENT`, which `RefusalsAsHttp` maps to 404. Harmless over HTTP today because the
  controller wins, but tickets 02 and 06 both add callers. Raised on attempt 1 as ticket 02's call
  and still open.

### What is already proven — do not redo it, and try not to disturb it

All verified on **attempt 3** against a running application on a throwaway database
(`SAVING_STREAK_DB` under `/var/folders/.../tmp.ezmgt1DJzj`; `data/saving-streak.db` untouched since
Sep 8) with `io.dataroots.savingstreak`, the web layer and Hibernate's SQL at DEBUG. Criteria 2 to 9
are ticked on this evidence.

- **Checks, run again by me.** `cd backend && ./mvnw test` → **224 tests, 0 failures, 0 errors,
  BUILD SUCCESS**. `cd frontend && npm run typecheck` → clean. `git diff --numstat
  agentic_engineered..HEAD -- 'backend/src/test/*'` is **five files, 497 added, 0 removed** — the
  test changes are purely additive and no existing test was weakened. The frontend diff is
  **empty**, which is right: the page is ticket 06.
- **Attempt 2's blocking defect is fixed, and thoroughly.** `points` = `1` followed by N zeros:
  30,000 digits 0.0049s (was 0.32s), 120,000 digits 0.0063s (was 4.68s), 1,048,576 digits in a
  1,048,643-byte body **0.016s** (was ~5.5 min extrapolated), 4,194,304 digits 0.023s. Every one is
  400 with a WARN, and `detail` is capped at 108 characters instead of echoing the body back. The
  bound's edges behave: 64 digits quoted in full, 65 quoted with `...`, and the existing 20-digit
  case still reads `That gift costs 99999999999999999999 points, and you have 5.` byte-identically.
- **Attempt 1's three killers are still answered in words**: `1e999999999` → 400 `That gift costs
  1e999999999 points, and you have 32.` (0.008s); `1.5e-999999999` → 400 `Points are whole, and …`;
  `-1e-999999999` → 400 `A gift has to be more than zero points, and …`.
- **The 201 and its shape.** `{"id":1,"direction":"SENT","senderId":1,"senderName":"Anke Peeters",
  "recipientId":2,"recipientName":"Bram De Vos","points":70,"givenAt":"2027-03-28T15:48:35.152Z"}`,
  with `givenAt` on a clock wound 200 days forward rather than the machine's date.
- **The address is matched sign-in's way.** `"  BRAM.DEVOS@EXAMPLE.BE "` resolved to Bram and
  `" ANKE.PEETERS@EXAMPLE.BE  "` to Anke. Extracting the sentence into
  `AccountsService.noCustomerBanksUnderThoseContactDetails()` left sign-in unchanged.
- **Conservation.** Anke 110 → 40 and Bram 0 → 70 on the first gift. Across an eleven-gift session:
  150 points earned in total (Anke 60+30+20, Bram 40), and after nine gifts one way, a gift back and
  a reward claim, the pots held exactly what was earned minus the 60 that expired and the 40 spent.
  Nothing created, skimmed or destroyed.
- **Oldest first, across as many batches as needed.** Anke held three batches (60 @ 2026-12-18,
  30 and 20 @ 2027-03-28) and a gift of 70 emptied the oldest and dipped into the next, leaving the
  third — same-age — batch alone:
  `points moved oldest first fromCustomerId=1 toCustomerId=2 points=70 available=110
  batchesWithSomethingLeft=3 slices=2 drawnOn=[batchId=1 reason=BASE_ACCRUAL
  earnedAt=2026-12-18T16:48:24.207Z taken=60 leftInIt=0] [batchId=2 reason=BASE_ACCRUAL
  earnedAt=2027-03-28T15:48:24.268Z taken=10 leftInIt=20]`
- **Inherited dating, proved four ways.** (1) Straight out of SQLite, the gift of 70 arrived as
  **two batches of different ages**, both dated when *Anke* earned them and neither at the gift:
  `id=4 customer_id=2 points=60 GIFT_RECEIVED earned=2026-12-18T16:48:24Z` and `id=5 customer_id=2
  points=10 GIFT_RECEIVED earned=2027-03-28T15:48:24Z`, against `given_at=1806248915152`
  (2027-03-28T15:48:35Z). (2) Bram's `pointsExpiringNext` became `60` on **2027-12-18** — the
  anniversary of the day the sender earned them, three months before he was given them. (3) Winding
  to 2027-12-17 and running `expireOldPoints` took **nothing**; winding two more days and running it
  again took **exactly the inherited slice**: `points batch expired batchId=4 customerId=2
  reason=GIFT_RECEIVED earnedAt=2026-12-18T16:48:24.207Z anniversary=2027-12-18T16:48:24.207Z
  pointsExpired=60`. A reset clock would have kept those alive to 2028-03-28. (4) After a round trip
  Anke → Bram → Anke, the 45 that came back still expire on **2028-03-28**, the anniversary of when
  *she* originally earned them, not a year after the gift back — so a chain of gifts cannot keep
  points alive, which is the whole point of the decision.
- **A reason of its own, out of every deposit's breakdown.** Gift batches carry `source_reference_id`
  1, 2 and 3, colliding with deposit ids 1, 2 and 3 — the hardest case available. Anke's deposits
  still report base-only 60 and 30, and Bram's later 40.00 deposit reports 40 base and nothing else
  while he holds 70 gifted points. `GIFT_RECEIVED` was added to the `points_credit.reason` check
  constraint by the entity model, as the spec says.
- **One row per gift, and the pre-move save really rolls back.** After 11 gifts and 26 refusals the
  `gift` table held exactly 11 rows carrying both customer ids, the points and the moment
  (`given_at=1806248915152` matching the response to the millisecond). Three of those refusals were
  `NOT_ENOUGH_POINTS` thrown *after* `gifts.save(...)` and the row count did not budge. **The three
  new 500s also rolled back cleanly** — no gift row, no balance change, and an ordinary gift
  succeeded immediately afterwards.
- **Ordinary refusals answer in words with a WARN.** Unknown recipient 404, unknown sender 404
  (`There is no customer 999.`), self-gift 400, `2.5` 400, `abc` 400, `0` 400, `-5` 400,
  over-balance 400 (`That gift costs 999 points, and you have 40.`), blank / null / missing fields
  and an empty body 400, JSON number `2.5` 400. Over the session the endpoint served 44 POSTs: 11
  gifts with a `gift given` INFO each, **26 refusals with 26 `gift rejected` WARNs** each carrying
  its kind and its reason, the 4 missing-field cases the controller refuses in its own words before
  the module is reached, and the 3 faults above. Every refusal I triggered inside Gifting left a
  line saying why.
- **Edges.** `5.0` and a bare JSON `3` go through as whole numbers; five gifts of 1 back to back and
  a whole-balance gift of 27 all 201, so there is no cap or cooldown; an **expired** batch is not
  giftable (Bram's swept batch 4 was excluded — `available=90 batchesWithSomethingLeft=11` with
  batch 4 absent from `drawnOn`); received points give onward carrying their age; gifted points are
  spendable and spent oldest-first alongside the recipient's own (`SS-SNK-TD92HE` issued for 40
  points, all of them gifted); and a gift moves no money — Anke's money-movement ledger holds only
  her three deposits and her streak, multiplier and savings balances are untouched by eleven gifts.
- **Logging is complete and in the surrounding style.** `gift given giftId=1 senderCustomerId=1
  recipientCustomerId=2 points=70 givenAt=2027-03-28T15:48:35.152Z`; the ledger's credit line now
  carrying the inherited date at INFO, as attempt 2 asked — `points credited customerId=2
  sourceReferenceId=1 reason=GIFT_RECEIVED points=70 batches=2
  oldestEarnedAt=2026-12-18T16:48:24.207Z`; DEBUG on the inputs behind the decision (`gift judged
  against the sender's pot … senderBalance=110`); and the guarded slice line `gift drawn from the
  sender's oldest points first giftId=1 … drawnOn=[points=60 earnedAt=2026-12-18T16:48:24.207Z]
  [points=10 earnedAt=2027-03-28T15:48:24.268Z]`. The `isDebugEnabled` guard a previous review made
  blocking is in place in `spend`'s exact shape: `PointsService.java:518` reads the flag once into a
  local before the loop and guards both the gathering (`:536`) and the emit (`:547`);
  `GiftingService.java:149` and `:178` guard the extra `balanceOf` query and the slice rendering.
  `LoggerFactory.getLogger(GiftingService.class)` is the only new logger and there is no
  `System.out` in the diff.
- **Both of attempt 2's "worth fixing" items were done**: a null recipient no longer NPEs in `give`
  (it is `NO_SUCH_RECIPIENT`, refused before `accounts.customerIdentifiedBy` trims), and the
  `PointsService` credit-line comment no longer claims the three writers log the same keys.
- **Page regression check.** No frontend code changed, but the page was driven with Playwright
  anyway. Signing in as Bram renders a **fully styled** home screen showing his 45 gifted points as
  spendable on the catalogue and "45 points expire on 28 maart 2028"; screenshot at
  `.scratch/peer-to-peer-gifting/logs/review3-02-home-bram.png`. No `pageerror` and no
  `console:error`; the two `requestfailed … ERR_ABORTED` in
  `01-a-customer-can-give-points-to-another-customer.app.3.browser.log` are React StrictMode's
  double render aborting its own in-flight fetches, and the data plainly loaded.

Apart from the three `100e2147483647`-family requests above, which were fired deliberately, the
backend log for the whole session contains **zero ERROR lines and zero 500s**.

### Out of scope for this ticket, recorded so it is not lost

Unchanged from attempt 2 and still true: the **scale** hazard on
`POST /api/savings-accounts/{id}/deposits` and its withdrawal equivalent, which render the parsed
figure through `AmountOfMoney.toPlainString()`, is pre-existing, has an empty diff against
`agentic_engineered`, and wants a ticket of its own because the fix would change deposit and
withdrawal refusal *wording*. **Do not fix it as part of ticket 01.** Note that it is a different
hazard from the one blocked above: `stripTrailingZeros` is called at exactly one site in the whole
main tree and that site is this branch's.

## Verified

Passed on attempt 4. All nine criteria met, exercised end to end against a running application on a
throwaway database (`SAVING_STREAK_DB` under `/var/folders/.../tmp.5QBFiP7uh9`; the trainer's
`data/saving-streak.db` untouched since Sep 8) with `io.dataroots.savingstreak`, the web layer and
Hibernate's SQL at DEBUG. Everything below is something I ran myself in this session.

### Attempt 3's blocking defect is fixed, and the family is closed

`stripTrailingZeros()` is now inside a `try` that routes `ArithmeticException` into the existing
`NOT_ENOUGH_POINTS` sentence (`GiftingService.java:253`). All three payloads that were HTTP 500s
answer 400 in words with a WARN behind each:

    100e2147483647     400 0.023s  That gift costs 100e2147483647 points, and you have 40.
    1000e2147483646    400 0.005s  That gift costs 1000e2147483646 points, and you have 40.
    100.00e2147483647  400 0.005s  That gift costs 100.00e2147483647 points, and you have 40.

The near misses that made this look covered for two rounds still answer in words
(`1e2147483647`, `10e2147483647`, `120e2147483647`, `1200e2147483646`, `1E+2147483646`), as do
attempt 1's three killers (`1e999999999` → shortfall; `1.5e-999999999` → `Points are whole, and
1.5e-999999999 is not a whole number.`; `-1e-999999999` → `A gift has to be more than zero
points`). Attempt 2's length bound is intact: 1,048,577 digits → 400 in **0.027s** with a 213-byte
body, 4 MB of digits in 0.033s, and the 64/65-character edge still quotes 64 in full and 65 with
`...`.

I did not take the fix on trust. A further **37 hostile figures** fired at the running application
(`1e-2147483648`, `1e2147483648`, `0.00e2147483647`, `1.0000000000e2147483647`, `10.0e2147483647`,
`+100e2147483647`, `00100e2147483647`, `1000000e2147483642`, `9223372036854775808`, `.5`, `5.`,
`1_000`, `0x10`, `Infinity`, `NaN`, `1,5`, `--5`, `1e`, `e5`, …) all answered 400 or 201 in under
17ms, none faulted. And a standalone program mirroring the fixed control flow verbatim, run under
`java -Xmx256m` over **1033 systematically generated figures** — a sweep of the boundary-scale
window (exponents 2147483647 down to 2147483582, 2^30 ± 1, 1e9, 536870912, 19/20/21) crossed with
0–8 trailing zeros and unscaled precisions 1–60, plus digits-after-the-point and negative-exponent
variants — reported `probed=1033 escaped=0 slowest=2ms`. A parallel `/code-review` fuzzed 905 inputs
of its own construction and also found zero uncaught throwables.

Why this should be the end of it: the 64-character bound is what makes it airtight, not just cheap.
`longValueExact`'s `precision() - scale` can only overflow an `int` when `-scale > 2147483647 -
precision`, so with precision capped at 64 the required exponent is always far above 2^30, where
`bigTenToThe` throws `ArithmeticException` immediately instead of attempting the inflated
`BigInteger` allocation. Every `BigDecimal` call in `wholePositivePointsIn` is now total
(`signum`, `scale`) or guarded (`new BigDecimal`, `stripTrailingZeros`, `longValueExact`).

### The nine criteria

Anke earned 110 points in three batches (60 @ 2026-09-09, then the clock wound +100 days, 30 and 20
@ 2026-12-18); Bram earned 40. One gift of 70 was the tracer.

1. **201 and its shape.** `POST /api/customers/1/gifts` →
   `{"id":1,"direction":"SENT","senderId":1,"senderName":"Anke Peeters","recipientId":2,
   "recipientName":"Bram De Vos","points":70,"givenAt":"2026-12-18T18:30:33.720Z"}` — `givenAt` on
   the wound-forward clock, not the machine's 2026-09-09. Over **92 POSTs** to the endpoint the log
   holds **zero ERROR lines and zero 500s**.
2. **Address matched sign-in's way.** `"  BRAM.DEVOS@EXAMPLE.BE "` resolved to Bram;
   `" ANKE.PEETERS@EXAMPLE.BE  "` was caught as a self-gift naming Anke. Extracting the sentence
   into `AccountsService.noCustomerBanksUnderThoseContactDetails()` left sign-in byte-identical —
   the string is unchanged from `agentic_engineered`, an unknown address still answers 404 `No
   customer banks here under that email address.`, and a padded, shouted valid address still signs
   in 200.
3. **Conservation.** 110 → 40 for Anke, 0 → 70 for Bram on the tracer. Across a 17-gift session
   with a round trip, an expiry sweep and a reward claim: **150 earned − 60 expired − 40 spent = 50**,
   and the two pots held 0 + 50. Nothing created, skimmed or destroyed.
4. **Oldest first**, from the log — the oldest batch emptied, the next dipped into, and a third
   batch *of the same age as the second* left alone:
   `points moved oldest first fromCustomerId=1 toCustomerId=2 points=70 available=110
   batchesWithSomethingLeft=3 slices=2 drawnOn=[batchId=1 reason=BASE_ACCRUAL
   earnedAt=2026-09-09T17:30:22.530Z taken=60 leftInIt=0] [batchId=2 reason=BASE_ACCRUAL
   earnedAt=2026-12-18T18:30:22.902Z taken=10 leftInIt=20]`
5. **Across as many batches as needed.** Two slices for the 70; three slices for a later
   whole-balance gift of 27 (`slices=3`, batches 2, 11 and 3 all drained to `leftInIt=0`).
6. **Inherited dating, proved four ways.** (1) Straight out of SQLite the gift of 70 arrived as two
   batches of *different ages*, both dated when Anke earned them: `id=4 customer_id=2 points=60
   GIFT_RECEIVED earned=2026-09-09 17:30:22` and `id=5 customer_id=2 points=10 GIFT_RECEIVED
   earned=2026-12-18 18:30:22`, against `given_at=1797618633720` (2026-12-18T18:30:33.720Z).
   (2) Bram's `pointsExpiringNext` became `60` on **2027-09-09** — the anniversary of the day the
   *sender* earned them, three months before he was given them. (3) Bram gave 65 onward to Anke: it
   drew his inherited 2026-09-09 batch first and arrived back in her pot still dated 2026-09-09, so
   after a full round trip `pointsExpiringNextOn` was **2027-09-09**, not a year after the gift back.
   (4) Winding to 2027-09-08 and running `expireOldPoints` took **nothing**; two more days and it
   took **exactly the inherited slice**:
   `points batch expired batchId=10 customerId=1 reason=GIFT_RECEIVED
   earnedAt=2026-09-09T17:30:22.530Z anniversary=2027-09-09T17:30:22.530Z pointsExpired=60`
   A reset clock would have kept those alive to 2027-12-18. A chain of gifts cannot launder expiry.
7. **A reason of its own, out of every deposit's breakdown.** `GIFT_RECEIVED` is in the
   `points_credit.reason` check constraint, written by the entity model. I engineered the hardest
   collision available: gift id 4 gave Bram a batch with `source_reference_id=4`, and Bram's own
   deposit is id 4 — his breakdown still reports `basePoints=40` and nothing else, and no deposit
   response grew a field (keys identical to base). Anke's three deposits still report base-only 60,
   30 and 20.
8. **One row per gift, and the pre-move save really rolls back.** Read out of the SQLite file: after
   **17 gifts and 75 refusals** the `gift` table held exactly **17 rows**, each carrying both
   customer ids, the points and the moment (`given_at=1797618633720` matching the response to the
   millisecond) — matching 17 `gift given` INFOs exactly. **Ten** of those refusals were
   `NOT_ENOUGH_POINTS`, which is thrown *after* `gifts.save(...)`, and the row count never budged.
9. **Logging.** 17 × `gift given giftId=17 senderCustomerId=1 recipientCustomerId=2 points=5
   givenAt=2027-09-10T17:38:06.329Z`; the ledger's credit line carrying the inherited date at INFO
   (`points credited customerId=2 sourceReferenceId=1 reason=GIFT_RECEIVED points=70 batches=2
   oldestEarnedAt=2026-09-09T17:30:22.530Z`); DEBUG on the inputs behind the decision (`gift judged
   against the sender's pot … senderBalance=110`, `points to move judged against the sender's
   batches … available=110 batchesWithSomethingLeft=3`); and the guarded slice line `gift drawn from
   the sender's oldest points first giftId=1 … drawnOn=[points=60
   earnedAt=2026-09-09T17:30:22.530Z] [points=10 earnedAt=2026-12-18T18:30:22.902Z]`. **Every
   refusal I triggered inside Gifting left a line saying why: 67 `gift rejected` WARNs, each with
   its kind and its reason.** The remaining 8 of the 92 POSTs are request-shape 400s the controller
   refuses in its own words before the module is reached. The `isDebugEnabled` guard a previous
   review made blocking is in place in `spend`'s exact shape — `PointsService.java:518` reads the
   flag once into a local before the loop and guards both the gathering and the emit;
   `GiftingService.java:149` and `:178` guard the extra `balanceOf` query and the slice rendering.
   `LoggerFactory.getLogger(GiftingService.class)` is the only new logger and there is no
   `System.out`, `System.err` or `printStackTrace` anywhere in the diff.

### Refusals and edges

Unknown recipient 404, unknown sender 404 (`There is no customer 999.`), self-gift 400, `2.5` 400,
`abc` 400, `0` 400, `-5` 400, over-balance 400 (`That gift costs 31 points, and you have 30.`),
blank / null / missing recipient 400, missing points 400, empty object 400, no body 400, JSON number
`2.5` 400. `5.0`, `5.` and a bare JSON `3` go through as whole numbers. An **expired** batch is not
giftable — after the sweep took Anke's 60, `available=29` with the swept batch absent from the pool,
and a gift of 40 was refused `you have 32`. No cap or cooldown: five gifts of 1 back to back and a
whole-balance gift of 27 all 201; a gift of 1 with an empty pot answers `That gift costs 1 points,
and you have 0.` A 100,000-character recipient address answers 404 in 3.8ms with a 151-byte body
(the address is deliberately not echoed). A gift moves no money — Anke's money-movement ledger holds
only her deposits, and her streak (0) and multiplier (1.00) are untouched by seventeen gifts.

**Under contention**, 12 parallel gifts of 5 against a pot of 30 gave exactly **6 × 201 and 6 × 400**,
with the two pots totalling 80 before and 80 after. No overdraft, no lost or duplicated point, no 500.

### Checks, run by me

- `cd backend && ./mvnw test` → **229 tests, 0 failures, 0 errors, BUILD SUCCESS** (224 on attempt 3,
  5 new parameterised cases). The three payloads that were 500s are now in
  `APointsFigureIsAnsweredInWordsApiTest`'s `@ValueSource` alongside the two near misses, as the last
  review asked.
- `cd frontend && npm run typecheck` → clean on Node v24.16.0.
- `git diff --numstat agentic_engineered..HEAD -- 'backend/src/test/*'` is **five files, 507 added,
  0 removed** — purely additive, no existing test weakened. The **frontend diff is empty**, which is
  right: the page is ticket 06.
- `/code-review` over `agentic_engineered..HEAD` found **no blocking defect**; Standards and Spec
  both pass.

### Page regression check

No frontend code changed, but the page was driven with Playwright anyway with the console, pageerror
and requestfailed handlers subscribed first. Signing in as Bram renders a **fully styled** home
screen showing his 50 points to spend, "50 points expire on 18 december 2027", and the coffee
voucher `SS-SNK-PASXTU` he bought largely with gifted points. Screenshots at
`.scratch/peer-to-peer-gifting/logs/review4-01-signin.png` and `review4-02-home-bram.png`, both read
and neither blank. **Zero `pageerror` and zero `console:error`** in
`01-a-customer-can-give-points-to-another-customer.app.4.browser.log`; the two `requestfailed …
ERR_ABORTED` are React StrictMode's double render aborting its own in-flight fetches and the data
plainly loaded. Vite's log holds only the `ECONNREFUSED` proxy errors from before the backend
finished starting.

### For whoever merges, and for tickets 02 and 06

None of these blocked the pass; all three are recorded so they are not lost.

- **A long non-numeric `points` value is answered as a shortfall.** The length gate fires before the
  parse, so 100 letters comes back `That gift costs aaaa…(64 chars)… points, and you have 5.` — a
  sentence quoting something nobody typed as a figure. Direct consequence of attempt 2's own
  prescription. Ticket 02's wording call, open since attempt 3.
- **A blank recipient answers 400 at the endpoint and 404 from the module.** `CustomerController.give`
  refuses null/blank with `A gift needs the email address of the customer it is going to.`;
  `GiftingService.give` refuses the identical input as `NO_SUCH_RECIPIENT`, which `RefusalsAsHttp`
  maps to 404 — an answer about an address that was never supplied. Harmless over HTTP today because
  the controller wins, but tickets 02 and 06 both add callers. Open since attempt 1.
- **The sender is taken from the path with no ownership check**, so `POST /api/customers/7/gifts`
  moves customer 7's points from an unauthenticated request. Same shape as `/redemptions`, so
  app-wide and not introduced here — but this is the first endpoint that moves value to a
  caller-named destination, which changes what the gap is worth. Wants a ticket of its own.
- **Out of scope and unchanged from attempts 2 and 3:** the *scale* hazard on
  `POST /api/savings-accounts/{id}/deposits` and its withdrawal equivalent, which render the parsed
  figure through `AmountOfMoney.toPlainString()`. Pre-existing, empty diff against
  `agentic_engineered`, and it wants a ticket of its own because the fix would change deposit and
  withdrawal refusal *wording*. `stripTrailingZeros` is called at exactly one site in the whole main
  tree and that site is now guarded, so it is a different hazard from the one this attempt closed.
