# 02: Deposits carry what remains of them

**What to build:** Nothing a customer can see changes. Each deposit gains a record of how much of it
is still sitting in the savings account, which is the full amount at the moment it is made. The
account's money balance becomes the sum of what remains across its deposits rather than the sum of
what was originally put in. The two figures are identical today; they diverge once a withdrawal can
reduce one of them, which is what this prepares for.

**Blocked by:** None (can start immediately).

**Status:** done

- [x] A deposit records a remaining amount, equal to the amount deposited when it is made
- [x] A savings account's money balance is derived from what remains across its deposits
- [x] Every balance the API reports is unchanged for every scenario that already has a test
- [x] An application started against a database written before this change reports the same balances afterwards, with deposits already recorded carrying a remaining amount equal to their original amount
- [x] Every existing test passes unchanged

## Verified

Reviewed as the diff `ticket/01-read-time-from-an-injected-clock..ticket/02-deposits-carry-what-remains-of-them`
(one commit, `eb7a704`): `Deposit` gains `remainingAmount`, `DepositRepository` gains a guarded
back-fill, new `DepositsOnStartUp` runs it, `DepositsService.moneyBalanceOf` sums it, plus two new
test packages. No existing test file and no frontend file is touched.

**Builds.** `./mvnw test` from a clean checkout of the branch: 72 tests, 0 failures, 0 errors across
12 classes, including the two new ones (`ADatabaseWrittenBeforeThisApiTest` 7,
`MoneyBalanceIsWhatRemainsApiTest` 3). `npm run typecheck` in `frontend/` is clean.

**A deposit records what remains of it.** Three deposits over HTTP into Anke's savings account 1
(12.50, 7.25, 0.99). The application log says so at DEBUG, per deposit:

    i.d.s.deposits.DepositsService : deposit records what remains of it depositId=1 amount=12.50 remainingAmount=12.50

and the rows behind them, read straight out of the SQLite file the running instance holds open,
are `(1, 12.5, 12.5) (2, 7.25, 7.25) (3, 0.99, 0.99)` — `amount` and `remaining_amount` equal.

**The balance is summed from what remains.** `GET /api/savings-accounts/1` answered `20.74`, and the
log names the figures it was made of:

    i.d.s.deposits.DepositsService : money balance summed from what remains savingsAccountId=1 deposits=3 balance=20.74

Cents survive: 12.50 + 7.25 + 0.99 came back as 20.74, not 20.7399999. The deposit history still
reports each deposit's original `amount`, which is right — history is what was put in, the balance is
what is left.

**A database written before this change.** Reproduced by hand rather than trusting the test that
claims it. Copied the running instance's database, ran `alter table deposit drop column
remaining_amount` on the copy (leaving three deposits and no column, i.e. the file the previous
release wrote), and started the application against it on port 8099. Its log, in order:

    11:23:44.921 DEBUG org.hibernate.SQL          : update deposit set remaining_amount=amount where remaining_amount is null
    11:23:44.925 INFO  i.d.s.deposits.DepositsOnStartUp : deposits recorded before this release given what remains of them deposits=3 remainingAmount=theirOwnAmount
    11:23:44.939 INFO  o.s.b.w.embedded.tomcat.TomcatWebServer : Tomcat started on port 8099

— the fill completed 14 ms *before* the port opened, which is the point of using
`SmartInitializingSingleton` over a `CommandLineRunner`. Balances afterwards were identical to
before: savings 1 `20.74`, savings 2 `0`, current account `2459.26`, points `19`; the deposit
history came back byte-for-byte the same three entries; and the rows now read
`(1, 12.5, 12.5) (2, 7.25, 7.25) (3, 0.99, 0.99)`.

**Idempotent, and safe alongside a live instance.** Started a *second* application against that same
file while the first was still up and serving. It booted normally and reported
`no deposit was missing what remains of it deposits=0` — the re-run changed nothing and did not
collide with the live writer.

**Nothing a customer can see changed.** Drove the page in Chromium with the console, page errors and
failed requests captured to
`.scratch/seed-repo-lab-readiness/logs/02-deposits-carry-what-remains-of-them.app.1.browser.log`.
Signed in as Anke, opened savings account 1: fully styled, `€ 20,74` saved / `22 points`. Deposited
`3.33` through the form — balance moved to `€ 24,07`, the history gained a `€ 3,33 / +3` row, and the
backend logged `deposit accepted depositId=4 ... amount=3.33 pointsEarned=3`. Then a refusal:
`999999.00` rendered unchanged in the red panel as *"There is not enough in that current account to
move EUR 999999.00. It holds EUR 2455.93."* The browser log has no `pageerror` and no `console:error`
other than the 400 from that deliberate refusal; the `ERR_ABORTED` entries are React StrictMode's
double-mount aborts and appear identically in ticket 01's browser log. The Vite log shows no
transform or build errors.

### Non-blocking notes for whoever merges

None of these fails a criterion; recording them so they are not rediscovered.

- `DepositsService.moneyBalanceOf` feeds `getRemainingAmount()` straight into `BigDecimal::add` with
  no null guard. The column is nullable and only the once-per-JVM start-up fill guarantees a value,
  so a NULL arriving after start-up (an older jar writing the same file, a restored backup) would
  turn both balance endpoints into a 500 rather than degrading to `getAmount()`. Deliberate and
  documented in the entity's Javadoc; worth revisiting if the file ever has two writers.
- The start-up statement is an unconditional `UPDATE`, which takes a SQLite write lock before
  evaluating its `WHERE`, so the steady-state boot is not read-only despite the Javadoc's "all but
  the first do nothing". Empirically harmless — the concurrent second instance above booted fine —
  but a count-first guard would make the claim literally true.
- In `ADatabaseWrittenBeforeThisApiTest`, the static `stillMissingWhenTheServerCameUp` is written by
  *both* application starts and never reset. The first (empty, current-schema) start writes 0, so if
  the reopened application's listener ever stopped firing, the class's central assertion would pass
  on the stale value. Setting it back to null before the reopen would close that. The behaviour it
  asserts is real regardless — the 14 ms ordering above was observed outside the test.
