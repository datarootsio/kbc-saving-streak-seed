# 04: A gift moves points and nothing else

Status: needs-info

**Blocked by:** 01 (a customer can give points to another customer).

**What to build:** Points move; nothing else does. Being generous with points never touches anybody's
euros, never secures a week, never changes a streak, and never appears in the ledger of money that
moved — that ledger stays a record of euros. Nor does a gift show up in any deposit's breakdown of
what it earned: a deposit's breakdown is a statement about that deposit, and points somebody was
given did not come from it.

Nothing is added to the account overview either — no "points given away" total, no "points received"
total. The balance already includes received points and ticket 05's list is the record. This follows
the loyalty bonus's precedent of showing a thing where it happened rather than adding a figure to the
front page.

This is the negative space around the feature, and it is worth stating as tests because every item on
it is a thing a plausible implementation could get wrong by being helpful. Expected to need no new
production code if ticket 01 was built as specified; if any of these fail, the fix is in ticket 01's
code, not a new special case.

- [x] Both customers' current-account and savings balances are unchanged by a gift.
- [ ] A gift secures no week and leaves both customers' streaks and multipliers exactly as they were.
- [x] A gift does not appear in either customer's ledger of money that moved.
- [ ] A gift does not appear in any deposit's breakdown of what it earned, for either customer, and the total a deposit says it earned is unchanged.
- [x] Nothing new appears on the account overview.

## Review feedback - attempt 1

Three of the five criteria hold, and I proved each of them by breaking the production rule on
purpose and watching a named assertion go red (every mutation reverted; `git status --porcelain` is
empty and no production file differs from `agentic_engineered`). Two do not: criterion 4 is stated
by a test whose javadoc claims out loud to cover exactly the case it does not cover, and criterion 2
is stated only in the one direction. A third point, about the overview helpers, is not a criterion
of its own but would let a broken front page pass.

### Defect 1: a gift added to a deposit's breakdown as a *new field* is not caught

`a_gift_is_no_part_of_what_any_deposit_of_either_customers_earned` says of itself:

> Every entry is compared whole rather than field by field, which is the assertion that keeps
> meaning something when the deposit view grows a field: a gift that showed up anywhere in one of
> these entries fails this test without anybody having to remember to look for it there.

That is not true, and the session log repeats the claim ("so a new field on the deposit view is
covered without anybody remembering to look").

Reproduce it. In `backend/src/main/java/io/dataroots/savingstreak/web/DepositResponse.java`, add a
component to the record and a value in the factory:

```java
record DepositResponse(Long id, BigDecimal amount, long pointsEarned, long basePoints,
                       long streakBonusPoints, long loyaltyBonusPoints, long giftedPoints,
                       ...
        return new DepositResponse(..., deposit.loyaltyBonusPoints(), 99L, ...);
```

Every deposit the API sends now carries `"giftedPoints":99` — a gift figure inside a deposit's
breakdown, which is the thing this criterion and the spec ("no deposit response grows a field")
forbid. Then run `cd backend && ./mvnw test`:

    tests: 251 failures: 0 errors: 0

Nothing in the repository notices. All four tests in
`AGiftMovesPointsAndNothingElseApiTest` pass. The reason is that `DepositView` is a record filled in
by Jackson, which ignores JSON properties it has no component for — so `containsExactly(his)`
compares only the ten fields the view already knew about, and a field added to the response is
invisible to it. `containsExactly` is worth keeping (it does catch the figures moving — see below),
but it is not the assertion the javadoc says it is.

The fix is the idiom the same author used correctly one test further down, and it already exists in
the harness: `AnApplicationWithAClockToMove#theAccountOverviewAsItIsSent` reads the body as text
precisely because "a view binds the fields it knows about and says nothing about the ones it does
not", and there is prior art at
`backend/src/test/java/io/dataroots/savingstreak/loyaltybonus/TheHistorySaysWhatEachDepositHasBeenPaidAndWhenItNextPaysApiTest:214`.
Add a sibling helper — `theDepositHistoryAsItIsSent(long savingsAccountId)` over
`GET /api/savings-accounts/{id}/deposits` returning `String` — and assert that both customers'
deposit history, lowercased, contains no "gift"/"given"/"received", alongside the existing
whole-entry comparison. Then correct or delete the javadoc sentence quoted above, because it is
currently telling the next reader something the test does not do.

### Defect 2: the streak half of criterion 2 is only ever asserted from the floor

`a_gift_secures_no_week_and_changes_neither_streak` deliberately arranges a fortnight with nothing
paid in, so both customers reach the gift with `currentStreakWeeks == 0` and `currentMultiplier ==
1.00`, and then asserts `after == before`. At zero and 1.00 that can only catch a run being
*created*. The criterion says "leaves both customers' streaks and multipliers exactly as they were",
which is a two-directional promise, and the other direction is untested.

Reproduce it. In `StreaksService.weekAndStreakOf`, return
`new StreakOfSecuredWeeks(0, derived.streak().bestWeeks())` for any customer who appears in the
`gift` table — a gift now wipes the run of weeks and the rate of everybody it touches, at both ends.
`./mvnw test` → 251 tests, 0 failures; all four tests in this class stay green. A customer losing a
live streak because a friend sent them points is the more damaging half of this promise and nothing
in the repository notices.

This class owns its own clock, so the fix is cheap: build a real run first (deposit, `aWeekPasses`,
deposit, `aWeekPasses`, deposit) so both customers arrive at the gift with `currentStreakWeeks >= 2`
and a multiplier above 1.00, then make the gift and re-assert the same five figures. Keep the
from-the-floor case as well — it is the one that catches a week being secured.

### Defect 3: the overview helpers never insist the read succeeded

`theOverviewMentionsNoGift` asserts only that three words are absent from a body, and neither
`theCustomerOverviewAsItIsSent` (new, `AnApplicationWithAClockToMove:222`) nor the existing
`theAccountOverviewAsItIsSent` checks the status. `TestRestTemplate.getForObject(..., String.class)`
hands back the error body rather than throwing, and an RFC 9457 problem body contains none of
"gift", "given" or "received":

    $ curl -s http://localhost:8080/api/savings-accounts/999
    {"type":"about:blank","title":"Not Found","status":404,"detail":"There is no savings account 999.",...}

So if the overview endpoint started answering 404 or 500, the last two assertions of
`nothing_about_a_gift_appears_on_either_customers_account_overview` would pass over a broken front
page. Every other helper in this harness insists on its status for exactly this reason — see the
javadoc on `deposit`, `withdraw`, `runJob` and `give`. Assert the status, or at minimum that the
body contains `"pointsBalance"`, before asking what it does not contain.

### What I confirmed does hold (each mutation applied alone, then reverted)

| Mutation (production code, gift-caused) | Result |
| --- | --- |
| Gift debits the sender's current account by EUR 1 | RED — `a_gift_moves_no_euros_...:111` "nothing came out of the sender's current account to pay for it" expected 2359 but was 2358 |
| Gift reduces a deposit's `remaining_amount` (savings balance) | RED — `...:105` "a gift is paid in points, so the sender's savings are untouched" expected 119 but was 118 |
| A gift adds EUR 60 to the week (securing it) in `StreaksService` | RED — `a_gift_secures_no_week_...` "the sender paid nothing into this week" expected 0.00 but was 60.00 |
| A gift bumps only `currentStreakWeeks`/`bestStreakWeeks` upwards | RED — "the sender is on the same run of weeks as before, which is none" expected 0 but was 1 |
| Every gift appended to `/money-movements` as a EUR 0.00 entry | RED — "the sender's ledger is a record of euros that moved, and none did", the extra `SENT ... amount=0.00` entry named |
| Gift points credited into the newest deposit's `loyaltyBonusPoints` and `pointsEarned` | RED — "and in none of his deposits, which say exactly what they said before" (60 vs 67) |
| `pointsReceivedAsGifts` total added to `CustomerAccountsResponse` | RED — "no gift figure on the sender's own overview" |
| `pointsGivenAway` total added to `SavingsAccountResponse` | RED — "no gift figure on the sender's savings account" |
| **`giftedPoints` field added to `DepositResponse`** | **GREEN — nothing failed, in the whole suite (defect 1)** |
| **A gift wipes `currentStreakWeeks` for both parties (`StreaksService` returns `new StreakOfSecuredWeeks(0, best)`)** | **GREEN — nothing failed, in the whole suite (defect 2)** |

### What the running application showed (backend on 8080, throwaway DB, DEBUG)

Deposited EUR 60 for Anke and EUR 20 for Bram, snapshotted ten reads for both customers, then
`POST /api/customers/1/gifts {"recipientContactDetails":"bram.devos@example.be","points":"25"}` →
`201`. Diffing every read before against after, the *only* changes were `pointsBalance` (60→35,
20→45), `pointsExpiringNext` (the same figure from the other end) and the two gift lists. Identical:
both current-account balances, both savings balances, both deposit histories, both money-movement
ledgers, `newSavingsThisWeek`, `stillNeededThisWeek`, `currentStreakWeeks`, `bestStreakWeeks`,
`currentMultiplier`. Full transcript in
`.scratch/peer-to-peer-gifting/logs/04-a-gift-moves-points-and-nothing-else.review.1.curl.log`.

The log is the negative evidence. The whole gift request ran three selects, `insert into gift`,
`insert into points_credit` and one `update points_credit` — no `deposit`, `withdrawal`,
`current_account` or `savings_account` written, no `deposit accepted` line, no week or streak line:

    DEBUG i.d.s.gifting.GiftingService : gift judged against the sender's pot senderCustomerId=1 recipientCustomerId=2 points=25 senderBalance=60
    DEBUG i.d.s.points.PointsService   : points moved oldest first fromCustomerId=1 toCustomerId=2 points=25 available=60 batchesWithSomethingLeft=1 slices=1 drawnOn=[batchId=1 reason=BASE_ACCRUAL earnedAt=2026-09-10T02:20:26.531Z taken=25 leftInIt=35]
    INFO  i.d.s.points.PointsService   : points credited customerId=2 sourceReferenceId=1 reason=GIFT_RECEIVED points=25 batches=1 oldestEarnedAt=2026-09-10T02:20:26.531Z
    INFO  i.d.s.gifting.GiftingService : gift given giftId=1 senderCustomerId=1 recipientCustomerId=2 points=25 givenAt=2026-09-10T02:20:36.845Z

Three refusals (500 points on a balance of 35, a gift to yourself, `2.5`) each answered 400 with the
sentence in `detail`, each left a `WARN gift rejected ... kind=... reason=...` line, and a re-diff of
all ten reads afterwards was empty. Signed in as both customers with Playwright: both home screens
render fully and styled, Bram shows "45 points to spend" with his week still "€ 20,00 of € 50,00 /
no week secured yet", and neither overview carries any gift figure — screenshots
`logs/review04-home-anke.png` and `logs/review04-home-bram.png`, browser console in
`logs/04-a-gift-moves-points-and-nothing-else.review.1.browser.log` (no `pageerror`, no console
error; the two `ERR_ABORTED` lines per load are React StrictMode's double-invoked fetch and appear
in every earlier review's browser log too).

### Where these came from

Defect 1 I found by mutation and the repository's `/code-review` agreed with it independently,
adding the note that `application.properties` sets no Jackson property so
`FAIL_ON_UNKNOWN_PROPERTIES` is Spring Boot's default `false`, and pointing at a second piece of
prior art —
`backend/src/test/java/io/dataroots/savingstreak/deposithistory/EachDepositInTheHistoryExplainsItselfApiTest.java:159`
reads the same endpoint as a `JsonNode`. Defects 2 and 3 that review raised and I then reproduced;
both mutations and the `curl` above are mine.

### Not blocking, for the record

- The overview test asserts on the words "gift", "given" and "received" in the sent JSON. A total
  named with none of them (`pointsFromOthers`) would slip past. The author flagged this trade
  themselves and it is the right one for a training application — noted so the next reviewer does
  not re-open it.
- `cd backend && ./mvnw test` → 251 tests, 0 failures, twice on the clean tree.
  `cd frontend && npm run typecheck` → clean.
