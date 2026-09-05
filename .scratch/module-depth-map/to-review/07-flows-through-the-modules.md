# 07: Flows through the modules

**What to build:** The three things this application actually does — money moving into a savings
account, money moving back out and drawing down the deposits it came from, and points being spent on a
reward — are each traceable on the page. Choosing one highlights the modules it passes through, in
order, so a reader can follow a business event across the application without stepping through it in a
debugger.

The paths are derived from the call graph the analyser already builds, not written out by hand, so a
flow that stops matching the code is a flow that fails rather than one that quietly misleads.

**Blocked by:** 03 (Reach, and the fan).

**Status:** needs-review

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
