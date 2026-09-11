# 07: Flows through the modules

**What to build:** The three things this application actually does — money moving into a savings
account, money moving back out and drawing down the deposits it came from, and points being spent on a
reward — are each traceable on the page. Choosing one highlights the modules it passes through, in
order, so a reader can follow a business event across the application without stepping through it in a
debugger.

The paths are derived from the call graph the analyser already builds, not written out by hand, so a
flow that stops matching the code is a flow that fails rather than one that quietly misleads.

**Blocked by:** 03 (Reach, and the fan).

**Status:** done

- [x] A deposit, a withdrawal with the deposits it draws down, and a reward claim are each available as a flow
- [x] Choosing a flow highlights every module it passes through, in the order it passes through them
- [x] Flows are derived from the call graph rather than listed by hand
- [x] Each flow is defined by its entry point in the checked-in configuration file, not by a hardcoded path through the modules
- [x] A flow whose path can no longer be resolved fails loudly rather than rendering a shorter path
- [x] Every module named in a flow is a module the graph contains
- [x] Clearing the selection returns the page to showing all modules equally

## Review feedback - attempt 1

Reviewed on `ticket/07-flows-through-the-modules` (2 commits over `ticket/06-behind-the-shape`:
`a6e823d`, `6d0780d`). Everything mechanical about this branch is in good shape — the checks
pass, the output is deterministic, the committed page matches the source, the failure paths
are loud, and the page works in both themes with a clean browser console. One acceptance
criterion is not met, and it is the one the ticket exists for.

### 1. Blocker: a flow is the entry *module's* whole transitive reach, not the event's path

`_walked_from` in `scripts/module_depth_map/graph.py` takes `flow.module` and recurses over
`module["reach"]["reaches"]` without ever using `flow.method`. The method is validated by
`_why_the_flow_cannot_start` and then discarded, so what gets numbered on the page is
everything the entry class transitively touches — not what the named call does.

What this puts on the page, in the committed `docs/module-depth-map.json`:

- `"a deposit"` numbers `CustomerRepository` step 5 and `CustomerAccounts` step 7. A deposit
  reaches neither. `CustomerAccounts` is built only by `AccountsService.accountsOf`
  (`backend/src/main/java/io/dataroots/savingstreak/accounts/AccountsService.java:172`) and
  `CustomerRepository` is touched only by `customers()`, `customerIdentifiedBy` and
  `accountsOf`. `DepositsService.deposit` calls `accounts.pairingFor`,
  `accounts.withdrawFrom` and `accounts.balanceOfCurrentAccount`, and none of those three
  goes near the customers table.

  Proved at runtime, not by reading. `POST /api/savings-accounts/1/deposits
  {"fromCurrentAccountId":1,"amount":25.00}` against the running app, with
  `org.hibernate.SQL` at DEBUG
  (`.scratch/module-depth-map/logs/07-flows-through-the-modules.app.1.backend.log:100-110`),
  ran exactly these statements and no others:

      select sa1_0.customer_id from savings_account sa1_0 where sa1_0.id=?
      select ca1_0.customer_id from current_account ca1_0 where ca1_0.id=?
      select ca1_0.id,ca1_0.balance,... from current_account ca1_0 where ca1_0.id=?
      insert into deposit (...)
      insert into points_credit (...)
      update current_account set balance=?,...

  No `customer` table is read. The page says a deposit passes through two modules it does
  not.

- Two flows that differ only in `method` produce byte-identical paths. Reproduce with a
  doctored configuration:

      # second flow: same module, method "depositsInto"
      python3 scripts/module-depth-map.py --scoring /tmp/twomethods.json \
        --graph /tmp/two.json --page /tmp/two.html

  Both `"a deposit"` and `"listing the deposits already made"` come back as the same
  14 modules in the same order. Whatever the method is for, it is not deciding the path.

This also contradicts what the branch itself writes down. `scoring.py`'s `Flow` docstring and
`scripts/module_depth_map/README.md` both argue that "a flow named by its module alone would
be a claim about everything that module does" — which is precisely what the walk produces.

### 2. Blocker: the numbered order is not the order the event passes through them

`_walked_from` iterates `reach.reaches`, which `scoring.py` (around line 969) sorts by
`(kind, name)`: adapters first, then modules, then records, alphabetically within each.
That ordering is deterministic, but it is not the order of the event, and the page states
that it is — `page.py` tells the reader a chosen flow "numbers them in the order it passes
through them", and the chosen-flow paragraph reads "traced from there through 14 modules,
in this order:".

Two concrete contradictions, both readable off the source and the app log:

- `"a deposit"` numbers `DepositRepository` step 2 and `AccountsService` step 3. In
  `DepositsService.deposit` the accounts are asked first (`refuseUnlessOneCustomersOwnAccounts`,
  then `takeTheMoneyOrRefuse`) and `deposits.save(...)` happens after. The SQL trace quoted
  above shows three `savings_account`/`current_account` selects before the `insert into
  deposit`. The page has the order backwards.
- `"a reward claimed"` numbers `RedemptionRepository` step 2, `AccountsService` step 3,
  `PointsService` step 8. `RewardsService.claim` asks Accounts first, spends points second
  and saves the redemption last.

A reader who follows the numbered list to "understand a flow without stepping through it in
a debugger" — the ticket's own stated purpose — gets a wrong story. The second paragraph of
the flows section does disclose the real rule ("depth first, each module's reach in the order
the card lists it"), and the README says "in the order the flow enters it", so the tool is
half-honest with itself; the reader-facing headline and the ordered list are not.

The implementer's session notes raise this as open question 2 ("depth-first pre-order as 'the
order it passes through them' ... it is a reading, not the only one"). It is not a reading the
criterion supports, and the fix is a decision somebody has to make rather than something a
reviewer should pick: either give the walk method-grain reach so the path is the call's, or
change what the page and this criterion claim. Please settle it before the next attempt
rather than re-wording only the prose.

### 3. Minor: the README's schema history stops at /5

`graph.py` bumps `SCHEMA` to `module-depth-map/6` and explains why in its own comment, but
`scripts/module_depth_map/README.md:108-115` still ends its list of bumps at
`module-depth-map/5`. Every previous bump is recorded there, so this is now the one place in
the repo that states the current contract and states it wrongly — an agent reading the README
for the document shape will not expect the top-level `flows` list.

### 4. Minor: `aria-pressed` on a button that never un-presses

`chooseFlow` sets the clicked button's `aria-pressed` to `"true"` and every other one to
`"false"`. Pressing the already-pressed button re-applies the same state. `aria-pressed`
announces a toggle, so a screen-reader user pressing it again to clear the choice gets no
state change and has to go find the separate "Clear" button.

### What was verified and is fine — do not redo it

- `cd backend && ./mvnw test` — BUILD SUCCESS, 113 tests, exit 0.
- `cd frontend && npm run typecheck` — clean.
- `python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests` — 562 tests, OK.
- Determinism: two runs into scratch paths, `cmp` on both outputs — byte identical. Both also
  `cmp`-equal to the committed `docs/module-depth-map.json` and `docs/module-depth-map.html`.
- Playwright over `file://docs/module-depth-map.html`, with `console`, `pageerror` and
  `requestfailed` subscribed (log at
  `.scratch/module-depth-map/logs/07-flows-through-the-modules.review.1.browser.log`): not one
  console message, page error or failed request across every interaction below.
- 4 chooser buttons; before any choice 0 highlighted / 0 aside / 0 badges / 0 `aria-pressed=true`.
  Choosing each flow in turn gave 14 on / 57 aside, 15 / 56, 14 / 57, with a matching badge
  count and exactly one `aria-pressed="true"` each time. "Clear" returned 0 / 0 / 0 / 0 and
  emptied the list. Screenshots read: on-flow cards carry the accent border and a step badge,
  off-flow cards are faded, both legible in light and in dark (`color_scheme="dark"`).
- Criterion 5 exercised on all three named failures, with a doctored configuration
  (`--scoring` pointed at a copy with one flow's module renamed to `DepositsCoordinator`, one
  flow's method to `record`, and one entry point moved to `accounts.Customer`): all three
  flows carried `resolved: false` and `path: []`, all three buttons rendered `disabled`, all
  three reasons rendered in the alarm block above the chooser, and each was logged twice —

      WARNING module_depth_map.graph flow not traced flow=a deposit
        entryPoint=io.dataroots.savingstreak.deposits.DepositsCoordinator.deposit
        reason=no module in this graph is called ... It is drawn with no path rather than a shorter one
      WARNING module_depth_map.cli the flow a withdrawal, and the deposits it draws down is
        drawn with no path through the modules: ...WithdrawalsService presents no method called
        record that a caller can reach ... it presents withdraw, withdrawalsFrom

- `"flows": []` renders "This configuration names no flow, so there is nothing here to trace.";
  a configuration with no `flows` key is refused with exit code 4.
- Criterion 6 checked directly against `docs/module-depth-map.json`: no dangling `moduleId` or
  `reachedFrom`, no module repeated inside a flow, steps numbered 1..n.
- Criterion 4 checked directly: every flow in `scoring.json` carries exactly
  `{flow, because, entryPoint}` and the only `io.dataroots.savingstreak.*` strings anywhere in
  that section are the three entry-point modules.
- The behind-the-shape panel from ticket 06 still opens with a flow chosen (`dialog[open]=1`,
  heading `CustomerAccounts`), and the flow highlighting survives closing it.

## Verified - attempt 2

Reviewed `ticket/06-behind-the-shape..ticket/07-flows-through-the-modules` (`68580de`,
`2bc3013` on top of attempt 1's `a6e823d`, `6d0780d`). **Both attempt-1 blockers are genuinely
fixed**, I reproduced each from the scenario that found it, and I exercised all seven criteria
myself against the running application rather than taking either previous session's word for
them. **Status: done.**

The branch touches no Java and no TypeScript — `git diff --name-only` is `docs/module-depth-map.{json,html}`,
`scripts/module_depth_map/{README.md,cli.py,graph.py,javasource.py,page.py,scoring.json,scoring.py}`
and one new test package — so the `io.dataroots.savingstreak` logging rule has no new surface. I
drove the running app anyway, because the app's SQL is the only ground truth available for the one
criterion that is about order.

### The two attempt-1 blockers, reproduced from their original scenarios

**Blocker 1 — a flow was the entry module's whole transitive reach.** Fixed. The two probes that
found it now both come out right.

- The doctored configuration with three flows on one module (`--scoring` pointed at a copy whose
  `flows` are `DepositsService.deposit`, `.depositsInto`, `.moneyBalanceOf`) gives **three
  different paths**, where attempt 1 gave three byte-identical ones:

      a deposit            -> DepositsService AccountsService SavingsAccountRepository
                              CurrentAccountRepository DepositRefused AmountOfMoney Deposit
                              DepositRepository PointsService PointsCredit
                              PointsCreditRepository RecordedDeposit
      listing the deposits -> DepositsService DepositRepository PointsService
                              PointsCreditRepository RecordedDeposit
      the money balance    -> DepositsService DepositRepository AmountOfMoney

- `CustomerRepository` and `CustomerAccounts` are **off** all three shipped flows, as the
  attempt-1 SQL trace said they should be. I checked this the other way round too: for every
  module on every flow, which of its fan entries are *not* on that flow. The complete answer is
  `AccountsService` → `{CustomerAccounts, CustomerRepository}` on the deposit and the withdrawal,
  `AccountsService` → `{CurrentAccountRepository, CustomerAccounts, CustomerRepository}`,
  `PointsService` → `{PointsCredit}` and `Redemption` → `{ClaimedReward}` on the claim. Every one
  of those is correct: a claim reads only `select count(*) from savings_account` (log below), and
  the last two are the disclosed floor cases.
- Screenshot read, not just taken:
  `.scratch/module-depth-map/logs/07-flows-through-the-modules.review.2.accounts-package.light.png`
  shows `CustomerAccounts` and `CustomerRepository` faded while `AccountsService` carries badge 2,
  `SavingsAccountRepository` 3 and `CurrentAccountRepository` 4.

**Blocker 2 — the numbered order was the fan's sort order.** Fixed, and the fix is the harder of
the two options the previous review named: the order is now the order the source *evaluates* the
calls, argument sites before the call that encloses them. I did not check this by reading. I ran
the three business events against the running API with `org.hibernate.SQL` at DEBUG and lined the
statements up against the step numbers. Every repository step is in the right place, and the exact
complaint from attempt 1 — `DepositRepository` numbered ahead of `AccountsService` — is reversed.

`POST /api/savings-accounts/1/deposits {"fromCurrentAccountId":1,"amount":60.00}` → 201,
`{"id":1,"amount":60.00,"pointsEarned":60,...}`:

    select sa1_0.customer_id from savings_account ...     -> step 3  SavingsAccountRepository
    select ca1_0.customer_id from current_account ...     -> step 4  CurrentAccountRepository
    select ca1_0.id,ca1_0.balance,... from current_account   (withdrawFrom; AccountsService is step 2)
    insert into deposit (...)                            -> step 8  DepositRepository
    insert into points_credit (...)                      -> step 11 PointsCreditRepository
    update current_account set balance=? ...

`POST /api/savings-accounts/1/withdrawals {"toCurrentAccountId":1,"amount":25.00}` → 201,
one allocation against deposit 1:

    select savings_account.customer_id                   -> step 3
    select current_account.customer_id                   -> step 4
    select deposit ... order by deposited_at, id         -> step 7  DepositRepository
    insert into withdrawal (...)                         -> step 9  WithdrawalRepository
    insert into withdrawal_allocation (...)              -> step 11 WithdrawalAllocationRepository
    select current_account ...                              (depositInto; AccountsService is step 2)
    select withdrawal_allocation ... order by id            (inside `recorded`, before steps 12/13)

`POST /api/savings-accounts/1/redemptions {"reward":"SNACK_VOUCHER"}` → 201,
`{"id":1,"pointsSpent":40,"voucherCode":"SS-SNK-5KVSYQ",...}`:

    select count(*) from savings_account ...             -> step 3  SavingsAccountRepository
    select points_credit ... remaining_points>0 ...      -> step 6  PointsCreditRepository
    insert into redemption (...)                         -> step 9  RedemptionRepository

`RedemptionRepository` is now **step 9, last**, where attempt 1 had it at step 2. And the
non-repository steps line up with the source's evaluation order too: `deposits.save(new Deposit(...))`
numbers `Deposit` 7 and `DepositRepository` 8, and `recorded(withdrawal)` numbers
`RecordedWithdrawalAllocation` 12 before `RecordedWithdrawal` 13, which is the order
`new RecordedWithdrawal(..., allocations.find...().stream().map(...).toList())` evaluates them in.

The refusal path is in the right order as well: a zero-amount deposit
(`{"fromCurrentAccountId":1,"amount":0}` → 400, "A deposit has to be an amount of more than zero,
and 0 is not.") still ran both `savings_account` and `current_account` selects **before** refusing,
which is what steps 2-4 ahead of `DepositRefused` 5 and `AmountOfMoney` 6 claim.

Attempt-1 minors 3 and 4 are fixed too: the README now records `module-depth-map/6` **and** `/7`
with the reason for each, and pressing the already-chosen flow button clears the choice (measured
below).

### Every acceptance criterion, exercised

Playwright, chromium, sync API, over `file://docs/module-depth-map.html`, at 1280x1000 in
`color_scheme` light **and** dark. `console`, `pageerror` and `requestfailed` subscribed before
navigating on every run, written to
`.scratch/module-depth-map/logs/07-flows-through-the-modules.review.2.browser.log` — **0 console
messages, 0 page errors, 0 failed requests** across 185 lines of driving, in both themes.
`…app.2.frontend.log` is Vite's clean start-up and nothing else.

- **Three flows available.** 4 chooser buttons: `a deposit`, `a withdrawal, and the deposits it
  draws down`, `a reward claimed`, all enabled with `aria-pressed="false"`, plus `Clear`.
- **Highlights every module it passes through, in order.** Before any choice: 71 cards plain, 0
  highlighted, 0 aside, 0 badges, 0 pressed. Choosing each flow in turn: **12 on / 59 aside**,
  **13 / 58**, **9 / 62**, with a matching badge count, exactly one `aria-pressed="true"`, and
  **0 cards wearing both classes** (I switched deposit→claim→deposit twice to try to provoke it).
  The ordered list reads out the call that put each module there, e.g. *"AccountsService —
  DepositsService.refuseUnlessOneCustomersOwnAccounts writes accounts.pairingFor, called through
  the field accounts, which holds a AccountsService"*. Each badge's `title` names the step, the
  matched sentence and the method it was written in. Order corroborated against live SQL above.
- **Derived from the call graph rather than listed by hand.** Proven by the three-flows-on-one-module
  probe above, and by `grep` over `scoring.json`: the only
  `io.dataroots.savingstreak.*` strings in the `flows` section are the three entry-point modules.
- **Defined by its entry point in the configuration.** Every flow is exactly
  `{flow, because, entryPoint:{module, method}}`; `_only()` refuses any other key. Moving an entry
  point moves the whole path (probe above).
- **A flow that cannot be resolved fails loudly.** All **four** failure modes exercised with a
  doctored configuration — module renamed to `DepositsCoordinator`; method renamed to `record`; an
  entry point on an interface (`DepositRepository.findBySavingsAccountId`, no body); a call that
  reaches nothing (`AccountsService.noSuchSavingsAccount`). All four: `resolved: false`,
  `path: []`, `modules: 0`, a WARN from `module_depth_map.graph` **and** one from
  `module_depth_map.cli`, the reason rendered in an alarm block above the chooser, and the button
  `disabled` — Playwright's `.click()` on it **times out** rather than going through, and 0 cards
  are highlighted after. Screenshot read:
  `…review.2.broken.png`. Two of the WARNs:

      WARNING module_depth_map.graph flow not traced flow=entered through an interface
        entryPoint=io.dataroots.savingstreak.deposits.DepositRepository.findBySavingsAccountId
        reason=... declares findBySavingsAccountId but writes no body for it here, so there are
        no calls to follow — a flow is entered through the module that implements the call, not
        through one that only promises it. It is drawn with no path rather than a shorter one
      WARNING module_depth_map.cli the flow a call that reaches nothing is drawn with no path
        through the modules: calling noSuchSavingsAccount on ...AccountsService reaches no other
        module this graph holds, so this flow passes through the one module it starts at and is
        not a path through the application

  `"flows": []` renders *"This configuration names no flow, so there is nothing here to trace."*
  with 0 buttons; a configuration with no `flows` key is refused with **exit code 4** and
  `refused to run: ... flows is missing, and it has to be a list`.
- **Every module named in a flow is one the graph contains.** Checked programmatically over
  `docs/module-depth-map.json`, all 34 steps: no dangling `moduleId`, no dangling `reachedFrom`,
  no module repeated inside a flow, steps numbered 1..n, **every step's `reachedFrom` numbered
  earlier in the same path**, and **every path edge present in the fan of the module it came
  from** (see finding 3 below for where that check actually lives).
- **Clearing returns every module to an equal footing.** Both ways: `Clear` → 71 plain / 0 on / 0
  aside / 0 badges / 0 pressed and `.chosen` emptied to 0 bytes; and pressing the chosen button
  again → the same, which is attempt-1 minor 4.

The ticket-06 panel still works with a flow chosen: `dialog[open]` is 1, heading
`AccountsService`, and after Escape the flow highlighting is intact (12/59). Clicking a module in
the ordered list scrolls to its card (`scrollY` 0 → 9393).

### Checks

- `cd backend && ./mvnw test` — **exit 0, `Tests run: 113, Failures: 0, Errors: 0`, BUILD SUCCESS.**
  Matches the orchestrator's run in `…checks.2.log`.
- `cd frontend && npm run typecheck` — **exit 0**, no output.
- `python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests` — **573 tests, OK**
  (562 on attempt 1; 4 rewritten, 15 new). Not in the lab's list, so I ran it: the new package is
  `tests/flowsthroughthemodules/`, one class per property in the existing convention, and the two
  invariant tests the implementer points at are real — `test_a_module_is_never_numbered_before_the
  _module_that_called_it` and `test_every_step_is_a_reach_the_module_it_came_from_already_has`.
- **Determinism:** two fresh runs into separate scratch paths are byte-identical to each other and
  `cmp`-equal to the committed `docs/module-depth-map.json` **and** `docs/module-depth-map.html`.
  A run takes **0.25s**. `run finished graphBytes=210791 pageBytes=301191 filesParsed=71
  filesUnparsed=0 modules=71 scored=35 neverScored=36 flows=3 flowsTraced=3`.
- **The graph diff is confined.** Against `ticket/06-behind-the-shape:docs/module-depth-map.json`,
  `modules`, `packages`, `scoring` and `source` are **byte-identical**; only `schema` moved and
  `flows` is new. So no card's fan, score, verdict or finding changed — the walk is genuinely a
  second reading placed beside reach, not an edit to reach.
- **The tool's own logging.** One INFO per flow with the values that decided it
  (`flow traced flow=a deposit entryPoint=...DepositsService.deposit modules=12 through=...`), one
  INFO summary (`flows read flows=3 traced=3 notTraced=0`), a WARN per refusal carrying the reason,
  and DEBUG for the inputs behind each decision (`calls read module=... method=deposit sites=24
  modules=7 ownMethods=3`) — **26 such lines for the whole run**, so nothing inside a tight loop.
  No `print()` anywhere in the analyser. Greppable and in the surrounding style.

### What a merger should know before merging

None of this blocks the ticket — every criterion above is met and exercised on this repository's
own source — but eight things are wrong in a way the next person should not have to rediscover.
Six of them are the tool's prose asserting behaviour the code does not have, which matters more
here than it would elsewhere: this page's whole authority is that a reader can check it.

1. **`this.field.method(...)` produces no call site at all, and the fan and the path disagree
   about it.** `_calls_in` (`javasource.py:1444`) reads `_A_RECEIVER_CALL` over the raw masked
   body, whereas `reach_of` reads receivers over `plain = _THROUGH_THIS.sub("", body)`
   (`javasource.py:1305`), which strips the `this.` first. So reach sees `deposits` in
   `this.deposits.save(...)` and the flow reading sees nothing: the lookbehind `(?<![\w.$])`
   rejects `deposits` because a `.` precedes it, and the bare-call branch then skips `save(`
   because `before` ends with `.`. Reproduce in five lines:

       # scripts/
       python3 -c "import sys; sys.path.insert(0,'.'); from module_depth_map import javasource as j; \
         print(j._calls_in('{ this.ledger.record(c); }'), j._calls_in('{ ledger.record(c); }'))"
       # -> ()   vs   (CallSite('ledger.record'),)

   End to end, on a two-file fixture (`Till.ring` calling `Ledger.record`): with `ledger.record(c)`
   the fan is `['shop.Ledger']` and the path is `['Till','Ledger']`; with `this.ledger.record(c)`
   the fan is still `['shop.Ledger']` but the path is `[]` and the flow reports
   *"reaches no other module this graph holds"* — a reason that is false. Both docstrings claim the
   opposite: `CallSite` says "`this.deposits.save(...)` writes `deposits`, because `this.` is a
   prefix a writer may put on a call and nothing more", and `_calls_in` repeats it. This is exactly
   the "a fan drawn from one reading and a path drawn from another" fault that
   `_resolves_names_for` says the tool cannot afford. Latent today only because
   `grep -rE 'this\.[A-Za-z_]\w*\.[A-Za-z_]\w*\(' backend/src/main/java` returns **0**; the next
   Java file to write that spelling loses a step, or a whole flow, with no warning.
2. **A construction is a dead end: `new X(...)` enters `X` as a step but never follows `X`'s
   constructor.** `graph.py:668` only recurses when `call["method"] is not None`, and `landing()`
   records `method=None` for a `builds` site (`scoring.py:1097`); only `Method` gained `calls` in
   this diff — `Constructor` has no `calls` attribute at all. Reproduced on a fixture where
   `Till.ring` does `new Receipt(cents)` and `Receipt`'s constructor calls `Auditor.note(cents)`:
   `Receipt`'s fan holds `shop.Auditor`, and the path is `['Till','Receipt']` — `Auditor` is
   missing. `CallSite`'s docstring reads as claiming otherwise ("the walk steps into the module
   either way") and the page's floor paragraph does not list constructor bodies among the things a
   path cannot follow. No module is lost on the three shipped flows — I checked every constructed
   module on them and none has a fan edge that is off its flow — so this is latent here too.
3. **`README.md:662` claims a runtime cross-check that does not exist.** "So the two readings are
   separate, and `graph.py` holds them against each other: every edge on a path has to be an edge
   in the fan the same source produced." Nothing in `_trace_the_flows`, `_walked_from` or
   `_calls_reader` reads `reach` at all. The invariant is held by exactly one unittest —
   `test_every_step_is_a_reach_the_module_it_came_from_already_has`, in
   `TheFlowsThisRepositoryShipsTest`, over this repo's own backend source. Point the tool at any
   other tree and a path edge with no fan edge is written to the document and drawn on the page
   with nothing objecting. An agent reading the README will think the document is self-consistent
   by construction when it is spot-checked by one test on one input.
4. **`scoring.py:68-73` still describes attempt 1.** The module docstring says the modules a flow
   passes through "are walked out of the reach every module already has, in the order the walk
   enters them" — the thing this attempt exists to stop doing — and closes with "says which of the
   three it was" when there are now four failure modes. This is the file that documents the
   configuration format.
5. **The same stale claim is shipped inside `docs/module-depth-map.html`.** `page.py:1561` ("The
   path is the document's own — walked out of the graph's reach") and `page.py:1751` ("is walked
   out of the graph's own reach before the page is written") are both inlined verbatim into the
   committed page — `grep -c "walked out of the graph" docs/module-depth-map.html` is **2**. The
   reader-facing prose on the page is correct; only these two source comments are not.
   `test_flows_through_the_modules.py:186` has the same slip ("read from the fans").
6. **`_calls_in` drops matches with no word, while its siblings log every decline.** Its three
   `continue`s (a qualified `new Holder.Row()`, a `new Deposit(...)` reaching the bare-call branch,
   a declaration mistaken for a call) are silent, whereas `_receivers_in:1589` and
   `_constructed_in:1612` log `name not read as a receiver name=%s reason=%s` and
   `new not read as building one type=%s reason=%s` for the same shapes, and the file's own comment
   promises the reading is "said out loud rather than dropped in silence". So when a flow comes
   back one step shorter than expected, DEBUG on `module_depth_map.javasource` shows the reach
   reading's declines and says nothing about the flow reading's — the one output that can go
   quietly short is the one with no trace behind it.
7. **`_flows` normalises the entry point but not the name** (`scoring.py:1517`). `module` and
   `method` are `.strip()`ped; `flow` is not, so `"a deposit"` and `"a deposit "` both pass the
   duplicate-name refusal and render two identical-looking buttons — the exact outcome the refusal
   message exists to prevent. One `.strip()` before `named`.
8. **`_written_among` is built twice per run** — `_measure_depth:720` and `_trace_the_flows:501` —
   as is `declared_by_id`, and `_calls_reader` then rebuilds `nested_by_id`, which is the third
   element of the `written_among` tuple it is already handed. The docstring says "Built here rather
   than twice" while `build()` calls it from two places. Harmless at 0.25s; worth one pass from
   `build()`.

One pre-existing observation, outside this ticket and recorded because you will notice it if you
grep: **the application logs no WARN when it refuses.** I triggered four refusals over HTTP — a
zero-amount deposit, a cross-customer deposit, a deposit larger than the balance, and a claim the
account cannot afford — and all four came back 400 with the right sentence but left **no
`io.dataroots.savingstreak` WARN** saying why; only Spring's `ExceptionHandlerExceptionResolver`
logged the reason, at DEBUG. `RefusalsAsHttp` has no logger, and `DepositsService`/`RewardsService`
throw without logging (`WithdrawalsService` does log `withdrawal rejected ... reason=`). No Java
changed on this branch or the six before it, so this is not this ticket's doing, but it is a real
gap against the repository's logging rule and belongs in a ticket of its own.
