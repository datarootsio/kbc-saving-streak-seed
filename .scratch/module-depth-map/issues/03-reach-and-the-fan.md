# 03: Reach, and the fan

**What to build:** Beneath each module's interface bar, a fan of lines out to everything that module
coordinates on its caller's behalf — the other modules it calls, the adapters it drives, the records
it writes, the transaction it establishes. This is where the page starts making its argument: a deep
module reads as a short bar over a wide fan, and a pass-through reads as a bar as wide as its fan,
one line per method, coordinating nothing.

Depth is measured as leverage — behaviour a caller can exercise per unit of interface they must learn
— and never as implementation lines over interface lines. That framing is rejected by this project's
design vocabulary because it rewards padding: under it, the largest file in the repository would score
as its deepest module. Reach cannot be inflated by writing more lines, which is exactly why it is the
numerator.

**Blocked by:** 02 (What a caller must learn).

**Status:** needs-info

- [ ] A module's reach counts the distinct collaborating modules it calls, the adapters it drives, the persistent records it writes, and whether it establishes a transaction
- [x] Adding lines to an implementation without adding coordination does not change its reach, and a fixture establishes this
- [x] Depth is reported as reach relative to interface cost, and appears in the graph document as its own value
- [x] The fan beneath each module is drawn from its reach, with one line per thing reached
- [x] A module with several collaborators behind few methods is visibly distinguishable from one with a method per collaborator, without reading any label
- [x] Every line in a fan resolves to something the graph document also contains
- [x] Fixture source trees establish a deep module and a pass-through, each asserting the depth it was built to produce

## Review feedback - attempt 1

Most of this ticket is done and done well, and the numbers on the committed page are correct today.
Three defects introduced by this branch send it back; the first is the one that matters, and it is
five lines of Java to reproduce.

### 1. A module can be credited with reaching something it never calls (blocks criterion 1)

`Rules.reach_of` in `scripts/module_depth_map/scoring.py` follows a static import like this:

    for imported in imports:
        if imported.member in declared.called:

`javasource._reached_in` builds `called` with `_A_CALL`, which matches *any* name with a `(` after
it inside the type body — and the body it reads includes the module's own **method and constructor
declarations**. So a module that statically imports a member, and happens to declare a method of the
same simple name, is credited with reaching the module the import came from even when it never calls
it.

Reproduce it. Put these three files in a directory and point the tool at it:

    // shop/Money.java
    package shop;
    public class Money {
        public static long of(long cents) { return cents; }
    }

    // shop/Desk.java
    package shop;
    import static shop.Money.of;
    public class Desk {
        public long of(long cents) { return cents; }
        public long ring(long cents) { return 1; }
    }

    python3 scripts/module-depth-map.py --source <that directory> --graph /tmp/g.json --page /tmp/p.html

The graph then says:

    shop.Desk reach 1 [('module', 'Money', 'calls of, imported statically from it')]

`Desk` calls nothing. It declares `of` and returns `1`. Expected reach 0; got 1, with an evidence
string that is a false statement about the source — the exact failure `javasource.py`'s own header
warns about ("made the graph assert something about the source that a reader could check and find
wrong"). It also draws a fan line, and it moves the leverage figure the page invites the reader to
check by hand.

The committed `docs/module-depth-map.json` is **not** affected: no reach in it comes from the
static-import branch (`AmountOfMoney` is already found as a receiver, and the `...Refused.Kind`
constants resolve to a nested type rather than a module). So this is latent rather than shipped — but
it is untested, and nothing in the suite would catch it coming back.

What to do: count a statically imported member only when it is genuinely called with no receiver in
front of it and is **not** a name the module itself declares. `declared.methods` already carries the
declared names; constructors are the module's own name. Add a fixture to
`scripts/module_depth_map/tests/reachandthefan/` alongside
`test_a_member_imported_statically_reaches_the_module_it_came_from` — the negative twin of it —
and check it reddens if you undo the fix.

### 2. `drawShape` does the one thing the file next to it says not to do

`page.py`'s new `drawShape` decides `var scored = module.interface.cost !== null;` and then, in the
else branch, reads `module.excludedBy.rule`. Seventy lines below, `drawInterface` carries this
comment as a rule:

    // Branching on the fact that carries the exclusion, not on the absent cost that
    // follows from it. Reading the rule off `excludedBy` after deciding on `cost === null`
    // would throw for a module that had one without the other, and the renderer is one
    // pass: a throw abandons every package section after it, which reads as a page that
    // ends early rather than as a page that failed.

`drawShape` re-introduces exactly that. It is safe today only because cost is null iff `excludedBy`
is set. Branch on `module.excludedBy`, the same way `drawInterface` does.

### 3. The fan is clipped on the one card the scale is anchored to

In `drawFan`, the module whose reach equals `widestReach` gets `span === SHAPE.width`, so its
outermost feet land at x=0 and x=200 — the viewBox edges — and the `r=2` end circles are sliced in
half. On the committed page that is `SavingsAccountController` (reach 9 = `widestReach`, computed
feet 0.0 … 200.0). Seen at 4x device scale in
`.scratch/module-depth-map/logs/03-reach-and-the-fan.review.1.card-SavingsAccountController.png`:
seven round feet and two half-feet, on the specimen card the page leads with. Inset the span by the
foot radius, or pad the viewBox.

### Also worth fixing, or worth a sentence in the README

`_A_RECEIVER`'s `(?<![\w.$])` lookbehind makes a fully-qualified static call invisible:
`io.dataroots.savingstreak.accounts.AccountsService.noSuchSavingsAccount(x)` yields no receiver at
all. The README's new section says a module it calls is found "by name for a static call", and that
spelling is not among the omissions the page lists as its documented floor. Either follow it or say
it is not followed — the page's honesty about being a floor is the reason its numbers can be
trusted.

### Everything else checked out — do not redo this work

- `cd backend && ./mvnw test` → 113 tests, 0 failures. `npm run typecheck` clean.
  `python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests` → 277 OK.
- Two fresh runs of `python3 scripts/module-depth-map.py` are byte identical to each other and to the
  committed `docs/module-depth-map.json` and `docs/module-depth-map.html`. No module's `interface`
  changed from `ticket/02`, so nothing already established regressed.
- Playwright over the committed page, chromium, light and dark, 1024 and 1280: 71 cards each time,
  **0 console messages, 0 page errors, 0 failed requests**, and `scrollWidth - clientWidth == 0` in
  all four. Cross-checked every card against the graph: for all 71, the count of `svg.fan line` and
  `svg.fan circle` equals `reach.count`, each line's `class` equals the entry's `kind` in order, and
  each `<title>` equals `kind name — matched`. **71 cards, 0 mismatches.**
- Criterion 5 is met and visible: `SavingsAccountController` draws a 100% bar over a 100% fan;
  `WithdrawalsService` a 25.8% bar over a fan spanning 89%; `DemoData` a 6.5% bar over 78%.
  See `...review.1.web.light.png` and `...review.1.deposits.light.png`.
- Criterion 2 is real, not decorative. Mutating `reach_of` so reach grows with what a body calls
  reddens 12 tests including `test_padding_the_implementation_changes_neither_reach_nor_depth`;
  hardcoding the fan span reddens 2 in `TheFanIsDrawnFromTheReachTest`; reverting `_LEADING_WORD`
  reddens `test_a_modifier_flush_against_a_type_parameter_list_is_read_rather_than_failed`. The
  parser regression `ticket/02` recorded is genuinely fixed, with the test that was missing.
- Refusals work: a configuration with `reach` deleted, and one still claiming
  `module-depth-map-scoring/1`, both exit 4, write nothing, and log the reason
  (`reach is missing, and it has to be an object. Nothing is scored with a rule nobody wrote`).
  Changing `reach.persistentRecord.when` to `@Table` really does change the fans.
- The tool logs its own flow: `DEBUG module_depth_map.scoring reach read name=WithdrawalsService
  modules=2 adapters=3 records=2 transaction=yes`, and `INFO module_depth_map.graph deepest
  module=io.dataroots.savingstreak.accounts.DemoData leverage=3.5 reach=7 interfaceCost=2`. Zero
  WARNING and zero ERROR over 71 files.

### Two judgement calls left as they are, noted so the next reviewer need not re-litigate them

- A data carrier called statically counts as a *module called*. `SavingsAccountController` reaches
  `DepositResponse`, `WithdrawalResponse`, `ClaimedRewardResponse` and `Reward`, all excluded from
  scoring; its reach would be 5 rather than 9 without them. Defensible, and it does not change the
  finding, but the number is generous.
- The transaction is the one fan line with no `moduleId`. Drawn dashed, named in the key, evidence in
  the `<title>`, and asserted to be the only kind that can be null. Accepted as satisfying criterion 6.

### Unrelated to this branch, worth its own ticket

A refused deposit of `0` leaves no `io.dataroots.savingstreak` WARN line in the backend log — only
Spring's `ExceptionHandlerExceptionResolver` DEBUG line. `WithdrawalsService` logs its refusal;
`DepositsService` does not. Pre-existing, untouched here.
