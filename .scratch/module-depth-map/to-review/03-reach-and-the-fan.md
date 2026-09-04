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

**Status:** needs-review

- [x] A module's reach counts the distinct collaborating modules it calls, the adapters it drives, the persistent records it writes, and whether it establishes a transaction
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

## Review feedback - attempt 2

The three defects attempt 1 named are genuinely fixed, and I checked each one myself rather
than taking the log's word for it. Everything else on this branch stands up: 285 python
tests, 113 backend tests, `npm run typecheck` clean, `docs/module-depth-map.{json,html}`
byte-identical to a fresh run, 71 cards drawn with 0 console messages and 0 page errors,
and the nine feet on `SavingsAccountController` are whole circles now instead of the seven
and two halves in attempt 1. **Do not redo any of that.**

What sends it back is that fix 1 was cut too narrow. The same false statement about the
source still comes out of the tool, in a shape that is ordinary Java, and two more shapes
produce the same class of error through a different route. All three inflate reach, which
is the one direction the page and the README now explicitly promise reach never errs in
("Each leaves a fan shorter than the source, which is the direction this page is willing to
be wrong in"). A promise a reader can check and find false is worse than no promise.

The committed `docs/module-depth-map.json` is **not** wrong today — I checked: no reach in
it comes from the static-import branch at all, and the one live name collision below has a
reach of 0. All three are latent. They are still the defect this ticket was already sent
back for once.

### 1. The static-import guard misses a declaration in a nested class (blocks criterion 1)

`Rules.reach_of` in `scripts/module_depth_map/scoring.py:493` builds

    declares = {method.name for method in declared.methods} | {declared.name}

from the **top-level type's own** declarations. But `declared.called` is built by
`javasource._reached_in` from the **whole class body**, nested, local and anonymous types
included. So the guard does not see a declaration that sits one brace deeper, and the
original defect fires again.

Reproduce it — this is attempt 1's own repro with the declaration moved into a nested
helper class:

    // shop/Money.java
    package shop;
    public class Money {
        public static long of(long cents) { return cents; }
    }

    // shop/Desk.java
    package shop;
    import static shop.Money.of;
    public class Desk {
        static final class Coin {
            long of(long c) { return c; }
        }
        public long ring(long cents) { return 1; }
    }

    python3 scripts/module-depth-map.py --source <that directory> --graph /tmp/g.json --page /tmp/p.html

Graph says:

    shop.Desk 1 [('module', 'Money', 'calls of, imported statically from it')]

`Desk` calls nothing. Expected reach 0. This is not an exotic shape: a static import beside
a nested record or helper whose method shares the name is everyday Java, and this repo
already static-imports `AmountOfMoney.asMoney` in two services.

The negative twin test that was added,
`test_a_member_imported_statically_and_never_called_reaches_nothing`, only uses a top-level
declaration, so nothing in the suite covers this. What to do: collect the declared names
from the whole body (or excise nested bodies from `called` before reading it), and add the
nested-declaration case beside the existing twin.

### 2. A nested type is never consulted when a name is resolved (blocks criterion 1)

`resolve` in `scoring.py:452` calls `javasource.candidate_ids`, which tries single-type
imports, then the file's package, then on-demand imports — and never the module's **own
nested types**, which shadow all three in Java. A call written on a nested type's simple
name is therefore credited to a same-named top-level module.

Reproduce:

    // shop/Kind.java
    package shop;
    public class Kind {
        public static long of(String s) { return 0; }
    }

    // shop/Till.java
    package shop;
    public class Till {
        enum Kind {
            COIN;
            static long of(String s) { return 1; }
        }
        public long ring(String s) { return Kind.of(s); }
    }

    shop.Till 1 [('module', 'Kind', 'shop.Kind', 'called on Kind')]

`javac` resolves that `Kind` to `Till.Kind`. The graph draws a fan line to a card `Till`
never calls.

**This collision is already live in this repository**, and is latent only by luck:

    backend/.../web/SavingsAccountResponse.java:10:   record SavingsAccountResponse(...)
    backend/.../web/CustomerAccountsResponse.java:34:     record SavingsAccountResponse(...)

`CustomerAccountsResponse` nests a `SavingsAccountResponse` while a top-level
`web.SavingsAccountResponse` exists in the same package. Its reach is 0 today only because
that record makes no call on the nested name. The moment anyone writes one, the page draws
a line to the wrong card. `parsed.nested_names(declared)` is already computed and
available — consult it before `candidate_ids`.

### 3. A fully-qualified `new` is credited to a same-named local type (blocks criterion 1)

`javasource._reached_in:849` does `found.group(1).rsplit(".", 1)[-1]` on `_CONSTRUCTED`,
throwing the package qualifier away. That is the exact opposite of `_A_RECEIVER`, whose
`(?<![\w.$])` lookbehind deliberately **refuses** a fully-qualified receiver — and which
the README now lists as a documented omission. So the same spelling is refused in one
branch and misread in the other.

Reproduce:

    // shop/Receipt.java     package shop;  @Entity public class Receipt { public long id() { return 0; } }
    // other/Receipt.java    package other;        public class Receipt { public long id() { return 0; } }
    // shop/Till.java        package shop;         public class Till { public long ring() { return new other.Receipt().id(); } }

    shop.Till 1 [('record', 'Receipt', 'shop.Receipt', 'builds one: annotated with Entity')]

Wrong module, wrong kind, false evidence string, and reach inflated. Either resolve the
qualified name properly or refuse it the way `_A_RECEIVER` does and name it in the page's
list of omissions.

### 4. A C-style array field is dropped with no log line, and the code for it is dead

`javasource._field_in:810`. `_TRAILING_NAME` is `([A-Za-z_$][\w$]*)\s*$`, which cannot
match a header ending in `]`. So `private DepositRepository deposits[];` takes the
`name is None` branch and returns **with no debug line at all**, and the
`_brackets_after(rest[name.end():])` on the next line is unreachable — the slice it reads
is only ever whitespace — even though the comment above it says `int xs[]` is handled.
Verified: parsing `private int xs[];` yields no field and zero
`"member not read as a field"` lines. `_fields_of`'s docstring says "Both are logged"; this
one is not. A call through such a field then falls to the receiver-as-type-name branch,
resolves to nothing, and reach is understated with no trace.

This is the smallest of the four and errs in the safe direction, but it is a silent parse
gap in the file whose whole promise is that nothing is dropped silently.
`_declared_parameters` already has the right helper (`_brackets_after_the_name`).

### Everything I checked that was fine — do not redo this work

- `./.scratch/module-depth-map/lab.sh checks` → exit 0. Backend **113 tests, 0 failures**;
  `npm run typecheck` clean. `python3 -m unittest discover -t scripts -s
  scripts/module_depth_map/tests` → **285 OK**.
- Attempt 1's defect-1 repro, verbatim: now `shop.Desk 0 []`, with
  `DEBUG module_depth_map.scoring static import not read as a call name=Desk member=of
  from=shop.Money, because this module declares that name itself and a declaration is not a
  call`. The positive twin still works: a `Till` that really calls the statically imported
  `of` gets `shop.Till 1 [('module','Kind'…'calls of, imported statically from it')]`.
- Defect 2 fixed: `drawShape` reads `var excluded = module.excludedBy;` and branches on it.
- Defect 3 fixed and seen: `SavingsAccountController`'s outermost feet are at 2.50 and
  197.50 in a `0 0 200 46` viewBox, and the 4x screenshot
  `…review.2.card-SavingsAccountController.4x.png` shows **nine whole circles** where
  `…review.1.card-SavingsAccountController.png` showed seven and two halves.
- Defect 4 (the `_A_RECEIVER` omission) documented rather than followed, as allowed. The
  page's floor paragraph and the README both name three missed spellings. I confirmed a
  fully-qualified static call really does yield reach 0, so the admission is truthful.
- Two fresh runs of `python3 scripts/module-depth-map.py` are byte identical to each other
  **and** to the committed `docs/module-depth-map.json` and `docs/module-depth-map.html`.
- Playwright/chromium over the committed page, light and dark, 1024 and 1280: **71 cards
  each time, 0 console messages, 0 page errors, 0 failed requests**
  (`…review.2.browser.log`), `scrollWidth - clientWidth == 0` in all four. Per card, the
  count and order of `svg.fan line` / `svg.fan circle` equal `reach.reaches`, each `class`
  equals the entry's `kind`, each `<title>` equals `kind name — matched`, and every foot
  sits inside the viewBox allowing for its radius and stroke. **71 cards, 0 mismatches, 0
  clipped feet, in all four passes.**
- Criterion 6 holds on the graph: over all 71 modules, every non-transaction reach has a
  `moduleId` the graph contains and a non-empty evidence string; the transaction is the
  only kind with a null `moduleId`. (Findings 1-3 point a line at the *wrong* card, not at
  a card that is absent, which is why they are filed against criterion 1.)
- Criterion 5 is visible without labels: `DemoData` a 6.5% bar over a 7-line fan,
  `WithdrawalsService` a 25.8% bar over an 8-line fan, `SavingsAccountController` a 100%
  bar over a 100% fan, `RefusalsAsHttp` a 90% bar over nothing. See
  `…review.2.deposits.{light,dark}.png` and `…review.2.card-*.png`.
- Criterion 2 is real: mutating `reach_of` to grow with what a body calls reddens **16**
  tests including `test_padding_the_implementation_changes_neither_reach_nor_depth`.
  Hardcoding the span back to `SHAPE.width` reddens
  `test_the_widest_fan_on_the_page_is_drawn_whole_rather_than_clipped`; putting `drawShape`
  back to `cost !== null` reddens
  `test_nothing_decides_an_exclusion_by_looking_at_the_cost_that_is_missing`; disabling the
  new static-import guard reddens the three static-import tests.
- Refusals work: a configuration with `reach` deleted and one still claiming
  `module-depth-map-scoring/1` both exit **4**, write nothing, and log the reason
  (`WARNING module_depth_map.cli refused to run: … reach is missing, and it has to be an
  object. Nothing is scored with a rule nobody wrote`).
- The tool logs its flow. A full `--log-level DEBUG` run over the repo: 320 lines, **zero
  WARNING and zero ERROR**, `DEBUG module_depth_map.scoring reach read
  name=AccountsService modules=0 adapters=3 records=0 transaction=yes`,
  `INFO module_depth_map.graph furthest reach
  module=io.dataroots.savingstreak.web.SavingsAccountController reach=9 over
  interfaceCost=31 leverage=0.29`, `INFO module_depth_map.graph deepest
  module=io.dataroots.savingstreak.accounts.DemoData leverage=3.5 reach=7 interfaceCost=2`.
- Backend smoke test on the throwaway database (this branch touches no Java): deposit 12.50
  → 201 and `INFO i.d.s.deposits.DepositsService : deposit accepted depositId=1 …
  pointsEarned=12`; deposit of 0 → 400; oversized withdrawal → 400 with
  `WARN i.d.s.deposits.WithdrawalsService : withdrawal rejected … reason=There is not
  enough in that savings account to move EUR 9999.00. It holds EUR 12.50.` Zero ERROR lines
  in `…app.2.backend.log`.

### Two things still not re-litigated

- A data carrier called statically counts as a *module called*. Closed in attempt 1.
- The transaction is the one fan line with no `moduleId`. Closed in attempt 1.

### Still unrelated, still worth its own ticket

A refused deposit of `0` leaves no `io.dataroots.savingstreak` WARN line — only Spring's
`ExceptionHandlerExceptionResolver` DEBUG. `WithdrawalsService` logs its refusal;
`DepositsService` does not. Pre-existing, untouched here.

### One nit, not a blocker

`javasource._reached_in`'s docstring describes `called` as "a name with a call's brackets
after it and nothing in front of it". It also holds the module's own declaration names,
which is the trap fix 1 exists to work around. `scoring.py` explains that at length at the
point of use; the docstring at the point of production does not, and whoever fixes finding
1 will be reading this one.
