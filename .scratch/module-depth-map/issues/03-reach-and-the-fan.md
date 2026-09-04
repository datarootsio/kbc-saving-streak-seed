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

## Review feedback - attempt 3

Attempt 2's four findings are genuinely fixed. I ran all four repros verbatim and each now
answers what it should, so **do not redo that work** — the list of what I re-checked and
found good is at the bottom of this section, and it is nearly the whole ticket.

What sends it back is the same defect class a third time, in five more spellings.
Attempt 2's finding 3 was "a fully-qualified `new` is credited to a same-named local type";
the fix went into `_CONSTRUCTED` and into `resolve`'s dotted branch, and it is correct. But
**five other places still resolve a name against something the compiler would not have
used**, and each one draws a fan line to a card the module never calls, with the wrong
`kind` and an evidence string that is a false statement about the source. That falsifies the promise this branch put on the page itself
(`page.py:329`): *"Each leaves a fan shorter than the source, which is the direction this
page is willing to be wrong in."*

One of the five is not exotic: **it is one added line away from firing on this
repository's own `CustomerController`**, and I reproduced that on a copy of the real source
rather than on a fixture. Another (finding 5) fires on any method that names a parameter
after a field, which is ordinary Java.

`docs/module-depth-map.json` is **not wrong today**. I checked every one of its 59 reach
entries against the source mechanically and all 59 verify. All five findings are latent. They are still the thing this ticket has now been sent back for three
times.

### 1. A field type written out in full is credited to this package (blocks criterion 1)

`Rules.reach_of` in `scripts/module_depth_map/scoring.py:490-493` feeds the field's written
type through `javasource.names_in`, and `names_in`
(`scripts/module_depth_map/javasource.py:1419`) does
`word.rstrip(".").split(".")[-1]` — right for `typesCrossingTheSeam`, wrong here, because
it throws the package away and the simple name is then resolved against this file's own
package.

Reproduce — three files in a directory, then point the tool at it:

    // shop/Receipt.java
    package shop;
    import jakarta.persistence.Entity;
    @Entity public class Receipt { public long id() { return 0; } }

    // other/Receipt.java
    package other;
    public class Receipt { public long id() { return 0; } }

    // shop/Till.java
    package shop;
    public class Till {
        private final other.Receipt receipt = null;
        public long ring() { return receipt.id(); }
    }

    python3 scripts/module-depth-map.py --source <that dir> --graph /tmp/g.json --page /tmp/p.html

Graph says:

    shop.Till 1 [{"kind": "record", "moduleId": "shop.Receipt", "name": "Receipt",
                  "matched": "called through the field receipt, which holds a other.Receipt"}]

Expected reach 0 (`other.Receipt` is not an entity and nothing else here is reached). The
evidence string **contradicts its own moduleId in a single line**: it says the field holds
an `other.Receipt` and points at `shop.Receipt`. Wrong module, wrong kind, and a reader who
opens the file finds the page lying to them.

A nesting qualifier does the same thing, with no second package involved:

    // shop/Row.java     package shop;  @Entity public class Row { public long id(){return 0;} }
    // shop/Holder.java  package shop;  public class Holder { public static final class Row { public long id(){return 1;} } }
    // shop/Till.java    package shop;  public class Till {
    //                     private final Holder.Row row = null;
    //                     public long ring() { return row.id(); } }

    shop.Till 1 [{"kind": "record", "moduleId": "shop.Row",
                  "matched": "called through the field row, which holds a Holder.Row"}]

`Holder.Row` is not `shop.Row`. Expected 0.

### 2. A single-type import that names a nested type falls through to the package (blocks criterion 1)

`javasource.candidate_ids` (`scripts/module_depth_map/javasource.py:1393-1397`) puts the
imported type first, but when that id is not a module in this tree the caller falls through
to `package + "." + name`. In Java a single-type import **binds the simple name
absolutely** — there is no falling through. The `shadowed` fix attempt 3 added does not
cover this: it only knows types nested inside *this* module, never ones this module
imports.

Reproduce:

    // shop/Row.java     package shop;  @Entity public class Row { public static long of(long a){return 0;} }
    // shop/Holder.java  package shop;  public class Holder { public static final class Row { public static long of(long a){return 1;} } }
    // shop/Till.java    package shop;  import shop.Holder.Row;
    //                   public class Till { public long ring() { return Row.of(1); } }

    shop.Till 1 [{"kind": "record", "moduleId": "shop.Row", "matched": "called on Row"}]

`javac` binds that `Row` to `Holder.Row`. Expected reach 0.

**This shape is live in this repository.** `backend/src/main/java/io/dataroots/savingstreak/web/CustomerController.java:10-11`:

    import io.dataroots.savingstreak.web.CustomerAccountsResponse.CurrentAccountResponse;
    import io.dataroots.savingstreak.web.CustomerAccountsResponse.SavingsAccountResponse;

and a top-level `io.dataroots.savingstreak.web.SavingsAccountResponse` module exists in the
same package and in the graph. I copied `backend/src/main/java` to a scratch directory,
added exactly one line to `CustomerController.worthOf` —

    SavingsAccountResponse.of(account);

— and ran the tool over the copy. `CustomerController`'s reach went **3 to 4**, the new
entry being

    module io.dataroots.savingstreak.web.SavingsAccountResponse | called on SavingsAccountResponse

where `javac` binds that name to `CustomerAccountsResponse.SavingsAccountResponse`. It is
latent today only because the controller happens to reach those two names through `new`
(dropped: neither is a persistent record) and through `CurrentAccountResponse::of` method
references (`_A_RECEIVER` needs brackets, so a `::` reference is not matched).
`points.PointsCreditRepository.EarnedPoints` is the third nested import in the repository;
no top-level name collides with it, so it is harmless.

### 3. A static import from outside the source tree is credited to this package (blocks criterion 1)

`scripts/module_depth_map/scoring.py:541`:

    target = resolve(imported.type.rsplit(".", 1)[-1])

`imported.type` is already the fully-qualified declaring type. Cutting it back is the
identical mistake `resolve`'s dotted branch at `scoring.py:460` exists to prevent.

Reproduce:

    // shop/Money.java  package shop;  @Entity public class Money { public long id(){return 0;} }
    // shop/Till.java   package shop;  import static com.external.Money.of;
    //                  public class Till { public long ring() { return of(1); } }

    shop.Till 1 [{"kind": "record", "moduleId": "shop.Money", "name": "Money",
                  "matched": "calls of, imported statically from it"}]

`Till` calls a member of a type this tree does not hold. Expected reach 0. The one-line fix
is `resolve(imported.type)` — the dotted branch already answers correctly, and it is why
finding 1's own repro passes when written as `new other.Receipt()`.

### 4. A static import makes its holder outrank the file's own package (blocks criterion 1)

`javasource.candidate_ids` (`scripts/module_depth_map/javasource.py:1394-1396`) walks
`if not imported.on_demand and imported.type.rsplit(".", 1)[-1] == name`. A static import
introduces the **member**, not the holder type's simple name, so its holder must not
outrank the file's own package — but this loop treats it as a single-type import and puts
it first. Distinct from finding 3: this one fires on a plain receiver, with no static call
involved at all.

Reproduce:

    // q/Helper.java  package q;  public class Helper { public static long of(long a){return 0;}
    //                                                  public static long build(){return 1;} }
    // p/Helper.java  package p;  public class Helper { public static long build(){return 2;} }
    // p/M.java       package p;  import static q.Helper.of;
    //                public class M { public long go() { return Helper.build(); } }

    p.M 1 [{"kind": "module", "moduleId": "q.Helper", "matched": "called on Helper"}]

`javac` resolves that `Helper` to `p.Helper`. So the page draws a line to the wrong card
**and** loses the real reach to `p.Helper`. Fix: skip entries that carry a member in that
first loop.

This one has a coupling worth checking when you fix it: the repository's two live
`AmountOfMoney` reaches come through the *receiver* branch (`called on AmountOfMoney`) and
resolve through the package candidate, so skipping static imports there does not regress
`docs/module-depth-map.json`. Confirm that with a regenerate-and-`cmp` before you commit.

### 5. A parameter shadowing a field name is credited to the field's type

`Rules.reach_of` at `scripts/module_depth_map/scoring.py:483` builds
`held = {field.name: field for field in declared.fields}` from the **top-level type's**
fields, while `declared.receivers` is read over the **whole body**. So any parameter,
local, or nested-class field whose name shadows a top-level field name is attributed to
the field's type.

Reproduce:

    // p/Repo.java   package p;  public interface Repo extends CrudRepository<String, Long> { }
    // p/Other.java  package p;  public class Other { public void ping(){} }
    // p/M.java      package p;
    //   public class M { private final Repo repo = null;
    //                    public void go(Other repo) { repo.ping(); } }

    p.M 1 [{"kind": "adapter", "moduleId": "p.Repo",
            "matched": "called through the field repo, which holds a Repo"}]

`M` never touches its `Repo` field, and the real reach to `p.Other` is missed. `void
go(Other repo)` shadowing a field is ordinary Java.

Unlike 1-4 this is not a one-line fix — telling a parameter from a field needs scope
tracking the reach reading does not do. **Documenting it is an acceptable resolution**,
the way `a.b.C.d()` was: name it in the page's list of named edges and in the README's,
and say which direction it errs in. What is not acceptable is leaving it unnamed while the
page promises a fan is only ever shorter than the source, because this one is not.

### One framing fixes 1-4

Never let a name be resolved against something the compiler would not have used.
Concretely: (1) keep the package on a field's written type where reach reads it —
`names_in` is right for `typesCrossingTheSeam` and wrong here, so this wants its own
reading rather than a change to `names_in`; (2) a single-type import that resolves to no
module in this tree should yield **no** candidates rather than falling through to the
package; (3) pass `imported.type` to `resolve` whole; (4) a static import must not put its
holder type into `candidate_ids` at all.

### The test gap that let all five through

`scripts/module_depth_map/tests/reachandthefan/test_reach_is_the_numerator_of_depth.py`
covers the fully-qualified case for `new` only
(`test_a_new_written_out_in_full_is_not_credited_to_the_module_next_door`, line 603) and
covers shadowing only for a type the module nests itself
(`test_a_name_a_nested_type_shadows_is_not_followed_to_the_module_next_door`, line 560).
Nothing covers a qualified **field type**, an **imported nested type**, a static import
**from outside the tree**, a static import **whose holder shares a simple name with a type
in the file's own package**, or a **parameter shadowing a field name**. Each of the three wants the negative twin beside its existing
positive, and each should redden if the fix is undone — check that, because two of the
three are one `rsplit` away from coming back.

### Everything I checked myself and found good — do not redo this work

- `./.scratch/module-depth-map/lab.sh checks` → exit 0. Backend **113 tests, 0 failures,
  BUILD SUCCESS**; `npm run typecheck` clean.
  `python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests` → **296 OK**.
- **Attempt 2's four repros, run verbatim, all fixed.** (1) nested-declaration static
  import → `shop.Desk 0 []` with `DEBUG module_depth_map.scoring reach read name=Desk
  modules=0 adapters=0 records=0 transaction=no`. (2) nested-type shadowing → `shop.Till
  0 []` with `DEBUG module_depth_map.scoring name not followed name=Kind in=Till, because
  this module declares a type of that name inside itself and a nested type is not a
  module`; the positive control (no nested `Kind`) still reports
  `('module','Kind','shop.Kind','called on Kind')`. (3) qualified `new` → `shop.Till 0 []`,
  and the control where `other.Receipt` really is the entity reports
  `('record','Receipt','other.Receipt','builds one: annotated with Entity')`;
  `new java.util.ArrayList<>()` reaches nothing. (4) `private int xs[];` now parses as
  `('xs', 'int[]')` and `private DepositRepository deposits[];` as
  `('deposits','DepositRepository[]')`, and `static { }` leaves
  `DEBUG module_depth_map.javasource member not read as a field line=5 reason=nothing at
  the end of it reads as a name member=static` where it used to leave none.
- Two extra shapes I probed and found **correct**: a static import from another package
  whose simple name collides with one in this package resolves to the right module
  (`import static other.Money.asCents` in package `shop` with a `shop.Money` present →
  `other.Money`); a nested type **two** levels down still shadows
  (`nested=2`, both suppressed).
- **Every reach entry in the committed graph verifies against the source.** I wrote a
  checker that, for all 71 modules, greps the module's own `.java` for the thing each
  evidence string claims — a static call `X.m(`, a call through the named field plus the
  field's type, a `new X`, or `@Transactional`. **0 unverifiable entries out of 59.**
  Spot-checked `WithdrawalsService` (all 8) and `SavingsAccountController` (all 9) by hand
  against the file as well.
- Criterion 6 holds mechanically: over all 71 modules every non-transaction reach has a
  `moduleId` the graph contains and a non-empty `matched`, the transaction is the only kind
  with a null `moduleId`, and `reach.count == len(reach.reaches)` everywhere. (Findings
  1-3 point a line at the *wrong* card, not at an absent one, which is why they are filed
  against criterion 1 and criterion 6 stays ticked.)
- Determinism: two fresh `python3 scripts/module-depth-map.py` runs are byte identical to
  each other **and** to the committed `docs/module-depth-map.json` and
  `docs/module-depth-map.html` (`cmp` on all four).
- Playwright/chromium over the committed page (`file://docs/module-depth-map.html`), light
  and dark, 1024 and 1280: **71 cards each pass, 0 console messages, 0 page errors, 0
  failed requests** (`…review.3.browser.log` is empty of all three), and
  `scrollWidth - clientWidth == 0` in all four. Per card I cross-checked against the graph:
  the count and order of `svg.fan line` and `svg.fan circle` equal `reach.reaches`, each
  `class` equals the entry's `kind`, each `<title>` equals `kind name — matched`, every
  foot sits inside the viewBox allowing for radius **and computed stroke width**, and the
  bar is `unscored` with a `never scored — <rule>` title exactly for the excluded modules.
  **0 problems in all four passes.** Attempt 1's clipping is gone: the widest fan
  (`SavingsAccountController`, 9 feet) runs 2.50 … 197.50 in a `0 0 200 46` viewBox, and
  the 4x card screenshot shows **nine whole circles**.
- Criterion 5 is met and readable without labels. `DemoData`: bar 6.45% over a 7-line fan
  spanning 78% (`…review.3.card-DemoData.png`). `SavingsAccountController`: bar 100% over
  a fan spanning 100% (`…review.3.card-SavingsAccountController.png`). `RefusalsAsHttp`:
  bar 90% over nothing. `RewardController`: a narrow bar over one centred line. See
  `…review.3.deposits.light.png`.
- Criterion 2 and criterion 7 are real, not decorative.
  `WritingMoreLinesIsNotDepthTest` asserts the padded fixture is `>2x` the lines with
  `reach` and `depth` **identical**, and that the padded module still reads 2.5 against
  the pass-through's 0.5. `test_a_deep_module_coordinates_several_things_behind_one_method`
  pins `{"reach": 5, "interfaceCost": 2, "leverage": 2.5}` and
  `test_a_pass_through_coordinates_one_thing_per_method` pins
  `{"reach": 2, "interfaceCost": 4, "leverage": 0.5}` with
  `len(methods) == reach.count`.
- Five mutation checks, each applied and reverted (tree left clean): nested shadowing
  disabled → reddens `test_a_name_a_nested_type_shadows_is_not_followed_to_the_module_next_door`;
  `_declares_rather_than_calls` forced `False` → reddens 5 including both static-import
  twins; `_CONSTRUCTED` cut back with `rsplit(".", 1)[-1]` → reddens both
  written-out-in-full tests; the fan span hardcoded back to `SHAPE.width` → reddens
  `test_the_widest_fan_on_the_page_is_drawn_whole_rather_than_clipped`,
  `test_the_span_the_page_works_out_is_the_reach_as_a_share_of_the_scale` and
  `test_the_committed_page_is_byte_identical_to_a_fresh_run`; `drawShape` put back to
  `cost === null` → reddens `test_nothing_decides_an_exclusion_by_looking_at_the_cost_that_is_missing`.
- Refusals work and say why: a configuration with `reach` deleted exits **4**, writes
  neither output file (checked by removing both paths first and confirming neither is
  recreated), and logs `WARNING module_depth_map.cli refused to run: … reach is missing,
  and it has to be an object. Nothing is scored with a rule nobody wrote`.
- The tool logs its flow. A full `--log-level DEBUG` run over the repository: 322 lines,
  **0 WARNING, 0 ERROR**, `INFO module_depth_map.graph graph built roots=backend/src/main/java
  filesSeen=71 filesParsed=71 filesUnparsed=0 packages=8 modules=71 scored=35
  neverScored=36`, `INFO module_depth_map.graph furthest reach
  module=io.dataroots.savingstreak.web.SavingsAccountController reach=9 over
  interfaceCost=31 leverage=0.29`, `INFO module_depth_map.graph deepest
  module=io.dataroots.savingstreak.accounts.DemoData leverage=3.5 reach=7 interfaceCost=2`.
  The two nested-shadowing suppressions that fire live are logged with their reason, and I
  confirmed both are correct: `ScheduledJobs` really does declare
  `private record AJob(...)` at line 240 and no top-level `AJob` exists, and
  `CustomerAccountsResponse` really does nest a `CurrentAccountResponse` at line 22.
- Application smoke test on the throwaway database (this branch changes no Java or TS —
  `git diff --stat` touches only `docs/`, `scripts/` and `.scratch/`):
  `POST /api/savings-accounts/1/deposits` 12.50 → **201** with
  `INFO i.d.s.deposits.DepositsService : deposit accepted depositId=1 savingsAccountId=1
  fromCurrentAccountId=1 amount=12.50 pointsEarned=12`; amount 0 → **400**; withdrawal of
  9999.00 → **400** with `WARN i.d.s.deposits.WithdrawalsService : withdrawal rejected
  savingsAccountId=1 toCurrentAccountId=1 amount=9999.00 balance=12.50 reason=There is not
  enough in that savings account to move EUR 9999.00. It holds EUR 12.50.`;
  `GET /api/savings-accounts/9999` → **404**. **0 ERROR lines** in
  `…app.3.backend.log`. The Vite page at 5173 renders the sign-in card with only Vite's own
  connect messages and the React DevTools notice on the console
  (`…review.3.app.browser.log`, `…review.3.app.png`).

### Three things closed in earlier attempts, still not re-litigated

- A data carrier called statically counts as a *module called*. Closed in attempt 1.
- The transaction is the one fan line with no `moduleId`. Closed in attempt 1.
- `a.b.C.d()` — a fully-qualified static call — is not followed. I confirmed the admission
  is truthful (`other.Money.asCents(e)` yields reach 0) and it is named on the page and in
  the README. Accepted in attempt 2, still accepted.

### One more latent shape, not a blocker, worth a line in the code

`_declares_rather_than_calls` reads a name as a *call* when what stands in front of it is
punctuation, which puts **enum constants written with arguments** and a **constructor with
no modifiers** into `called` rather than `declares`. `Reward.java`'s `called` really is
`('CHARITY_DONATION', 'CINEMA_TICKET', 'FAMILY_CINEMA_PACK', 'Reward', 'SNACK_VOUCHER',
'name')`. It can only inflate reach through the static-import branch, so it needs a static
import whose member name equals an enum constant declared in the same body:

    // shop/Palette.java  package shop;  public class Palette { public static final long RED = 1; }
    // shop/Desk.java     package shop;  import static shop.Palette.RED;
    //                    public class Desk { enum Color { RED(1); Color(long v) { } }
    //                                        public long ring() { return 2; } }

    shop.Desk 1 [{"moduleId": "shop.Palette", "matched": "calls RED, imported statically from it"}]

`Reward` has no imports at all, so nothing fires today, and Java's naming conventions make
the collision unlikely. Not a blocker — but the module's own constructor is guarded by
`{declared.name}` for exactly this reason, and a nested type's constructor and an enum
constant are not.

### One CSS nit, cosmetic, no visible defect today

`.module .unscored` (`page.py:132`) was written for the `p.unscored` element
`drawInterface` adds at `page.py:538`, but it also matches the `div.bar.unscored` this
branch introduced at `page.py:449`. Measured in chromium on the committed page: an
excluded module's dashed bar computes `margin-top: 8px`, `font-style: italic`,
`font-size: 12.8px`, against `0px`/`normal`/`16px` on a scored bar. I checked whether it
shows: the bar's top offset inside its card is **69.78px on both**, so there is no visible
misalignment — the margin is collapsing. Worth a distinct modifier class anyway, because
the next person to touch either rule inherits the collision.

### Still unrelated to this branch, still worth its own ticket

A refused deposit of `0` leaves no `io.dataroots.savingstreak` WARN line — only Spring's
`ExceptionHandlerExceptionResolver` at DEBUG. `WithdrawalsService` logs its refusal;
`DepositsService` does not. Pre-existing, untouched here, flagged by all three reviews now.
