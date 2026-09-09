# 03: A loyalty bonus is an ordinary batch of points

Status: needs-info

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
