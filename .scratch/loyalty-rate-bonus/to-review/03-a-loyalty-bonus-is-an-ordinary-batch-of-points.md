# 03: A loyalty bonus is an ordinary batch of points

Status: needs-review

**Blocked by:** 01 (an anniversary pays a tenth of the euros a deposit still holds).

**What to build:** Loyalty bonus points are worth exactly what every other point is worth. They can
be spent on any reward, they are spent in the same oldest-first order as the rest, they count
towards the balance the moment they are paid, and they run out twelve months after the anniversary
that paid them — not twelve months after the sweep happened to notice. They appear in what the
customer is told expires next, so the warning covers everything they stand to lose.

The one interaction worth stating plainly: a deposit keeps paying its anniversaries after its own
base points have expired. Expiry is a rule about points, and this is a reward for money staying put,
so a deposit two, three or ten years old is still on its clock even though everything it originally
earned has long since gone. A bonus credited for an anniversary far in the past may therefore arrive
already beyond its own twelve months and be swept away the same night, which is both rules holding at
once rather than either misbehaving.

Like 02, this is expected to need no new production code: the bonus is credited as an ordinary dated
batch and the ledger already treats every batch the same way. The work is the tests that say the
ledger has no special case here, and would fail the day somebody added one.

- [x] A loyalty bonus can be spent on a reward.
- [x] A loyalty bonus is spent in oldest-first order alongside base points and streak bonus, with no regard to which reason earned it.
- [x] A loyalty bonus counts towards the points balance from the moment it is paid.
- [x] A loyalty bonus expires twelve months after the anniversary that paid it, and a sweep before that leaves it alone.
- [x] A loyalty bonus appears in the points the customer is told expire next, on the day its own twelve months are up.
- [x] A deposit whose base points have already expired still pays its second anniversary.
- [x] A bonus paid for an anniversary already more than twelve months past is credited and then expires, and both events are readable in the log.
- [x] A claim refused for want of points quotes a balance that includes whatever loyalty bonus the customer holds.

## Review feedback - attempt 1

All eight acceptance criteria above are genuinely met, and I verified every one of them by hand
over HTTP against a running application, reading the backend log for each. **The three new tests
are correct and should be kept exactly as they are — do not rewrite them.** The boxes stay ticked
because the behaviour they claim is real; the one thing standing between this branch and a merge
is the production hunk, which deviates from a convention this repository states out loud twice.

### 1. The new `drawnOn` gathering must be guarded by `log.isDebugEnabled()` (blocking)

`backend/src/main/java/io/dataroots/savingstreak/points/PointsService.java`, in `spend`, builds a
`List<String>` of rendered per-batch strings inside the allocation loop with no guard:

```java
List<String> drawnOn = new ArrayList<>();
for (PointsCredit batch : oldestFirst) {
    ...
    drawnOn.add("batchId=" + batch.getId() + " reason=" + batch.getReason()
            + " earnedAt=" + batch.getEarnedAt() + " taken=" + taken
            + " leftInIt=" + batch.getRemainingPoints());
}
```

This repository has an explicit rule for this exact shape of line, and it is written down in the
file being edited. `PointsService.java:274`, thirty lines above the change, says of the expiry
sweep's per-batch line:

> One line per batch, and deliberately not guarded by isDebugEnabled the way the streak walk's
> are: **there is nothing to render here, only getters**, and this is the only record of which
> batch went and what it was worth when it did.

And `deposits/WithdrawalsService.java:71` states the other side of the same distinction, for a
gathering that is character-for-character the sibling of this one:

> Gathered rather than logged here, so that a withdrawal spread over a long list of deposits is
> still one line in the log. **Guarded, because rendering a deposit is work** — four values per
> deposit the withdrawal reaches, two of them amounts to format — and the string is thrown away
> when the application runs at INFO. The same reasoning as the streak walk's derivation line, and
> the opposite of the points sweep's per-batch line, which passes getters and renders nothing.

The new code renders — five values concatenated per batch — so by the repository's own stated test
it falls on the guarded side and is not guarded. It is also the only one of these gatherings on a
per-request path: the expiry sweep's unguarded line runs once a night, whereas `spend` runs on
every reward claim. `grep -rn isDebugEnabled backend/src/main/java` shows the three existing
guards (`WithdrawalsService:71`, `WeekAndStreakDerivation:84`, `WeekAndStreakDerivation:179`); this
should be the fourth.

What to do: hoist `boolean sayWhichBatchesItCameOffOf = log.isDebugEnabled();` before the loop the
way `WithdrawalsService:71` does — asked once, for the reason its comment gives (a level changed
mid-spend would otherwise print a list missing its first batches) — guard the `drawnOn.add(...)`
with it, and guard the `log.debug("points spent oldest first ...")` call with it too.

### 2. Match the sibling's list format (blocking, same hunk)

`WithdrawalsService` wraps each element in square brackets and joins on a space:

```java
drawnDown.add("[depositId=" + ... + "]");
...
log.debug("... drawnDown={}", ..., String.join(" ", drawnDown));
```

which reads `drawnDown=[depositId=1 ...] [depositId=2 ...]`. Passing the raw `List<String>` here
instead makes Java render it as `drawnOn=[batchId=5 reason=LOYALTY_BONUS ... leftInIt=0, batchId=6
reason=BASE_ACCRUAL ... leftInIt=55]` — one pair of brackets around the whole thing and a comma
between elements, so the element separator and the field separator are both whitespace-ish and the
line is harder to split by eye than its sibling. Wrap each element in `[...]` and emit
`String.join(" ", drawnOn)`.

### 3. `unexpiredBatches=` under-describes what it counts (non-blocking, same hunk)

Both new lines are fed `oldestFirst.size()`, and `oldestFirst` comes from
`PointsCreditRepository.unspentOldestFirst`, whose query is
`where credit.customerId = :customerId and credit.remainingPoints > 0 and credit.expiredAt is null`
— so the figure is the batches that are unexpired **and still hold something**, not the unexpired
ones. A batch spent to zero is filtered out and not counted. The label `unexpiredBatches=` and the
refusal's `across {} unexpired batches` both promise more than they deliver. The surrounding code
is careful here (`batchesConsidered=`, `pointsLeft=`, `depositsConsidered=`), so please match it:
something like `batchesWithSomethingLeft=` / `across {} batches with anything left in them`.

Nothing else needs changing. In particular:

- The DEBUG level on `points not spent` is correct and is **not** a missing WARN. `RewardsService`
  is the sole caller of `spend` and already warns with the customer-facing reason; I read both
  lines in the log, in order:
  `DEBUG ... points not spent customerId=2 points=40 reason=only 30 left across 1 unexpired batches`
  then
  `WARN ... claim rejected customerId=2 reward=SNACK_VOUCHER kind=NOT_ENOUGH_POINTS reason=Coffee or snack voucher costs 40 points, and you have 30.`
- Adding the line at all was the right call, despite the ticket saying no production code was
  expected. Criterion 2 is a decision about ordering and nothing in the log recorded it before;
  after the change the ordering is readable in one line (quoted in full below). Keep the line —
  just guard and format it.

### How to reproduce what I saw

Application on a throwaway database per the lab, then, against `localhost:8080` (note the claim
body field is `reward`, not `rewardCode`):

```
POST /api/savings-accounts/1/deposits   {"amount":"100.00","fromCurrentAccountId":1}
POST /api/dev/clock/advance             {"days":7}
POST /api/savings-accounts/1/deposits   {"amount":"50.00","fromCurrentAccountId":1}
POST /api/dev/clock/advance             {"days":379}
POST /api/dev/jobs/payLoyaltyBonuses/run
POST /api/dev/jobs/expireOldPoints/run
POST /api/customers/1/redemptions       {"reward":"SNACK_VOUCHER"}     -> 400, refused
POST /api/customers/1/redemptions       {"reward":"CHARITY_DONATION"}  -> 201
POST /api/savings-accounts/1/deposits   {"amount":"90.00","fromCurrentAccountId":1}
POST /api/customers/1/redemptions       {"reward":"SNACK_VOUCHER"}     -> 201
```

`GET /api/customers/1/accounts` after each step reads
`155 -> 170 -> 15 -> 5 -> 95 -> 55`, with `pointsExpiringNextOn` going
`2028-09-09 -> 2028-09-16 -> 2028-09-30`. The full evidence for all eight criteria, including the
second customer's four-anniversary run, is in
`.scratch/loyalty-rate-bonus/logs/03-a-loyalty-bonus-is-an-ordinary-batch-of-points.review.1.md`.

## Review feedback - attempt 2

The behaviour is right and I proved all eight criteria by hand. `./mvnw test` is 212/0/0,
`npm run typecheck` is clean, and every criterion above is backed by a log line I read and quote in
`.scratch/loyalty-rate-bonus/logs/03-a-loyalty-bonus-is-an-ordinary-batch-of-points.review.2.md`. The
three review points from attempt 1 were all addressed correctly: the gathering is guarded, the list
wears `[...]` and joins on a space, and both lines say `batchesWithSomethingLeft` /
`batches with anything left in them`. I confirmed the guard genuinely suppresses by running a second
application at default INFO — `grep -c "points spent oldest first\|points not spent"` returns 0
there while the WARN refusal keeps its reason.

**Do not change any behaviour, any assertion, or any log line.** What sends this back is five
sentences of prose that are not true. This repository's comments are load-bearing — attempt 1 was
bounced over the justification in one of them — so a comment that states a checkable falsehood about
a deliberate ordering or a stated convention costs the next reader more than no comment would. All
five are text-only edits.

### 1. The nightly order is stated backwards, and the two new tests now contradict each other (blocking)

`backend/src/test/java/io/dataroots/savingstreak/loyaltybonus/ALoyaltyBonusExpiresTwelveMonthsAfterItsAnniversaryApiTest.java:87`
says:

> // The same night's expiry sweep, run in the order the nightly jobs run in.

but the test runs `THE_LOYALTY_SWEEP` first (line 84) and `THE_EXPIRY_SWEEP` second (line 90). The
nightly order is the other way round:

- `backend/src/main/java/io/dataroots/savingstreak/points/OldPointsExpireNightly.java:47` —
  `EVERY_NIGHT_AT_THREE = "0 0 3 * * *"`
- `backend/src/main/java/io/dataroots/savingstreak/loyalty/LoyaltyBonusesArePaidNightly.java:48` —
  `EVERY_NIGHT_AT_HALF_PAST_THREE = "0 30 3 * * *"`

and that file's own Javadoc (line 19) says so out loud, and says the order is load-bearing:

> After the points-expiry sweep, half an hour behind it. [...] the order matters in one visible
> case: a bonus paid for an anniversary more than twelve months past is credited already beyond its
> own twelve months, and **running second means it survives the night** and is swept up by the
> following one rather than appearing and vanishing inside the same run.

So the comment claims this test mirrors a production ordering while doing the opposite of it. Worse,
the sibling added by the same ticket,
`ADepositKeepsPayingAfterItsOwnPointsHaveExpiredApiTest.java:25`, states the same sentence and gets
it right — "run in the order the nightly jobs run in, expiry and then loyalty" — and does run expiry
first. Two files in one package, one sentence, opposite sequences.

The assertions hold under either order here (the bonus is dated day 365 and the sweep runs on day
379, so its own twelve months are nowhere near up, and the deposit's own 500 are past theirs either
way), so this is prose, not a broken test. Either reverse the two `runJob` calls so the sentence
becomes true, or say what the test actually does — but do not leave the two files disagreeing.

### 2. `PointsService`'s new comment claims a distinction that does not exist (blocking)

`backend/src/main/java/io/dataroots/savingstreak/points/PointsService.java:406`:

> // getters and renders nothing; this gathering is also the only one of the three on a
> // per-request path, since a spend runs on every claim rather than once a night.

The three the same sentence names are this gathering, the withdrawal's drawn-down list and the
expiry sweep's per-batch line. Two of those three are on a per-request path, not one:
`WithdrawalsService`'s gathering runs on `POST /api/savings-accounts/{id}/withdrawals`. Both
`WeekAndStreakDerivation` guards are per-request too. Only the expiry sweep's line is nightly.

I inherited this sentence from my own attempt-1 feedback, which was loose in the same way, so this
is my mistake as much as yours — but the clause is false as written and it sits in the one comment
whose job is to explain the repository's guarded/unguarded rule. Delete the clause, or narrow it to
what is true: the expiry sweep's unguarded line runs once a night, whereas `spend` runs on every
reward claim. The rest of that comment is correct and should stay.

### 3. "A fortnight" is used for seven days (blocking, one line)

`ALoyaltyBonusIsSpentLikeAnyOtherPointsApiTest.java:129`:

> // The 10 that went were the older deposit's bonus, dated a fortnight before the newer one's

The two deposits are one `app.aWeekPasses()` apart (`AnApplicationWithAClockToMove:112-114`, which
advances `DAYS_IN_A_WEEK`), so their anniversaries, and the batches dated at them, are seven days
apart. I read it off the running application: deposits on 2026-09-09 and 2026-09-16, bonuses dated
2027-09-09 and 2027-09-16, and `pointsExpiringNextOn` moving 2028-09-09 to 2028-09-16. Say a week.

The class Javadoc's "a fortnight" at line 36 is about a different pair — the surviving bonus against
the fresh deposit that follows it, which really is fourteen days — so leave that one alone. As it
stands the two sentences disagree about the same test.

### 4. "A fortnight" is used for twenty-seven days (blocking, two lines)

`ADepositKeepsPayingAfterItsOwnPointsHaveExpiredApiTest.java:48`:

> A fortnight past the second anniversary — twenty-four calendar months is 730 or 731 days

with the constant set to 758. That is twenty-seven or twenty-eight days past, not fourteen; 758 is
two of the 379-day "fortnight past a year" steps, so the slack doubled and the word did not.
`ALoyaltyBonusExpiresTwelveMonthsAfterItsAnniversaryApiTest.java:56` has the same value under the
same word: "And a fortnight past them, with the same room either side of the calendar's variation."

The margin is generous either way and no boundary is at risk — I re-derived all of them (365, 731,
1096, 1461 days from a 2026-09-09 start) and 379, 700, 758 and 1500 all sit on the correct side with
room. The point of the prose on these constants is that a reader can check the margin without
recomputing the calendar, and "a fortnight" for four weeks defeats that.

### 5. "This same day" is twenty-seven days out (blocking, one line)

`ADepositKeepsPayingAfterItsOwnPointsHaveExpiredApiTest.java:93-94`:

> // The second year. The expiry sweep runs first, as it does every night, and takes the first
> // anniversary's bonus: twelve months after that anniversary is this same day.

The sweep runs on day 758; twelve months after the first anniversary is day 730 or 731. The
constant's own Javadoc two lines up gets this right — "past the first anniversary's own twelve
months, which fall on that same day", where "that same day" is the second anniversary — but the
inline comment's "this same day" reads as the day the sweep runs and is four weeks off.

### What I ran, so you can reproduce it

Application on a throwaway database per the lab, then over HTTP against `localhost:8080` (claim body
field is `reward`, not `rewardCode`). Anke is customer 1 / savings 1, Bram is customer 2 / savings 3.

```
POST /api/savings-accounts/1/deposits   {"amount":"100.00","fromCurrentAccountId":1}
POST /api/savings-accounts/3/deposits   {"amount":"500.00","fromCurrentAccountId":2}
POST /api/dev/clock/advance             {"days":7}
POST /api/savings-accounts/1/deposits   {"amount":"50.00","fromCurrentAccountId":1}
POST /api/dev/clock/advance             {"days":379}
POST /api/dev/jobs/payLoyaltyBonuses/run     -> C1 155->170, C2 500->550   (criterion 3)
POST /api/dev/jobs/expireOldPoints/run       -> C1 15, C2 50: bonus only   (criterion 5)
POST /api/customers/1/redemptions       {"reward":"SNACK_VOUCHER"}    -> 400, quotes 15 (criterion 8)
POST /api/customers/1/redemptions       {"reward":"CHARITY_DONATION"} -> 201 (criterion 1)
POST /api/savings-accounts/1/deposits   {"amount":"90.00","fromCurrentAccountId":1}
POST /api/customers/1/redemptions       {"reward":"SNACK_VOUCHER"}    -> 201 (criterion 2)
POST /api/dev/clock/advance {"days":330} + expireOldPoints  -> C2 still 50 (criterion 4, inside)
POST /api/dev/clock/advance {"days":30}  + expireOldPoints  -> C2 0        (criterion 4, past)
POST /api/dev/jobs/payLoyaltyBonuses/run     -> C2 50 from an empty pot    (criterion 6)
POST /api/dev/clock/advance {"days":721} + payLoyaltyBonuses -> C2 150
POST /api/dev/jobs/expireOldPoints/run       -> C2 50                      (criterion 7)
```

The evidence for criterion 2 is one line, and it is worth keeping in mind while you edit: a later
run of mine produced a spend that reaches four batches whose ids are out of date order, so nothing
but `earnedAt` can explain the sequence:

```
points spent oldest first customerId=1 points=40 available=69 batchesWithSomethingLeft=6
  drawnOn=[batchId=13 reason=LOYALTY_BONUS earnedAt=2030-09-09... taken=10 leftInIt=0]
          [batchId=22 reason=LOYALTY_BONUS earnedAt=2030-09-16... taken=5 leftInIt=0]
          [batchId=24 reason=LOYALTY_BONUS earnedAt=2030-09-30... taken=9 leftInIt=0]
          [batchId=19 reason=BASE_ACCRUAL  earnedAt=2031-09-05... taken=16 leftInIt=14]
```

Full log lines and the page screenshots are in
`.scratch/loyalty-rate-bonus/logs/03-a-loyalty-bonus-is-an-ordinary-batch-of-points.review.2.md`.
