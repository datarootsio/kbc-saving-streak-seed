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

## Review feedback - attempt 4

All five of attempt 3's findings are genuinely fixed. I ran every repro verbatim rather than
taking the log's word for it, including the live `CustomerController` one on a copy of the real
source, and each now answers what it should. **Do not redo that work** — the full list of what I
re-checked and found good is at the bottom of this section, and it is nearly the whole ticket.
`docs/module-depth-map.json` is unchanged from attempt 3 and is still correct: I verified all 59
reach entries against the source myself, mechanically and then by hand for two modules.

What sends it back is the same defect class a fourth time, in four more spellings, plus one
asymmetry that is **live on the committed page**, and the fact that **attempt 4 put a counting
claim on the page that those four spellings falsify**.
`page.py:343` now ends the fan paragraph with:

    "It is the one place a fan can be longer than the source rather than shorter."

mirrored at `README.md:253-262` ("One reading goes the other way") and in `reach_of`'s docstring
(`scoring.py:437`, "One reading errs the other way, and it is the reason this paragraph says
'almost'"). It is not one place. It is at least five. Each of the four below inflates reach, draws
a fan line to a card the module never calls, and prints an evidence string that is a false
statement about the source — and one of them says a module "builds one" record when it builds
none, which is criterion 1 read literally.

Findings 1-4 are latent: I checked the whole of `backend/src/main/java` and there is no generic type
declaration, no `new X[...]`, and no `this.m(...)` call site, and every one of the 19
`extends`/`implements` in the application points at a Spring or JDK type rather than at another
module in the tree. That is why the committed graph is unaffected by them. But `extends` is used 19 times,
and the first participant who writes `class MyService extends SomeInTreeBase` opens findings 1
and 2 on this repository. **Finding 6 is not latent** — it is visible on
`SavingsAccountController`, the specimen card the page leads with and the one the fan scale is
anchored to.

### 1. A static import outranks a method the module inherits (blocks criterion 1)

`Rules.reach_of` at `scripts/module_depth_map/scoring.py:545` builds

    declares = set(declared.declares) | {declared.name}

from **this file's body only**. In Java a method inherited from a supertype shadows a static
import of the same name just as a declared one does. `declared.supertypes` is already parsed
(`javasource.py:620`, `DeclaredType.supertypes` at `:260`) and is never consulted here.

Reproduce — three files in a directory, then point the tool at it:

    // shop/Money.java  package shop; public class Money { public static long of(long c){return c;} }
    // shop/Base.java   package shop; public class Base  { public long of(long c){return c;} }
    // shop/Desk.java   package shop; import static shop.Money.of;
    //                  public class Desk extends Base { public long ring(){ return of(1); } }

    python3 scripts/module-depth-map.py --source <that dir> --graph /tmp/g.json --page /tmp/p.html

    shop.Desk 1 [('module', 'shop.Money', 'Money', 'calls of, imported statically from it')]

`javac` binds that `of` to `Base.of`. Expected reach 0.

The same false entry comes out through `this.`, which makes it wider than inheritance:
`_THROUGH_THIS` (`javasource.py:106`, applied at `:894`) strips `this.` **before** `_A_CALL` runs,
so `return this.of(1);` in the same `Desk` is read as a bare `of(1)` and produces the identical
line. I ran both; both give reach 1.

### 2. A nested type inherited from a supertype is not treated as shadowing (blocks criterion 1)

`shadowed` in `reach_of` (`scoring.py:460`) comes only from `parsed.nested_names(declared)` —
types this module nests **itself**. Member types are inherited, and an inherited member type
shadows both the package and every import.

Reproduce:

    // shop/Row.java     package shop; @Entity public class Row { public static long of(long a){return 0;} }
    // shop/Holder.java  package shop; public class Holder { public static class Row { public static long of(long a){return 1;} } }
    // shop/Till.java    package shop; public class Till extends Holder { public long ring(){ return Row.of(1); } }

    shop.Till 1 [('record', 'shop.Row', 'Row', 'called on Row')]

`javac` binds that `Row` to `Holder.Row`. Expected reach 0. Wrong module, wrong kind, false
evidence. It reproduces the same way with `interface Holder { class Row {...} }` and
`implements Holder`.

This also makes `README.md:218-221` only half true: "The nested type comes first because Java
puts it first" is stated without the qualifier "as long as this module declares it itself".

### 3. A class type parameter is followed to a same-named module (blocks criterion 1)

`resolve` (`scoring.py:452-489`) never consults `declared.type_parameters`, which the parser
already produces (`javasource.py:621`, `DeclaredType.type_parameters` at `:262`). The tool already
knows a type variable is not a type to follow — `_types_crossing_the_seam` excludes them when
counting what a caller must learn — and forgets it here.

Reproduce:

    // shop/Receipt.java  package shop; @Entity public class Receipt { public long id(){return 0;} }
    // shop/Till.java     package shop;
    //   public class Till<Receipt> { private final Receipt held = null;
    //                                public String ring(){ return held.toString(); } }

    shop.Till 1 [('record', 'shop.Receipt', 'Receipt', 'called through the field held, which holds a Receipt')]

Expected reach 0. Method-level type parameters are safe — I checked. Folding
`declared.type_parameters` into `shadowed` is a one-line fix.

### 4. `new X[n]` is reported as "builds one" (blocks criterion 1)

`_CONSTRUCTED` (`javasource.py:71`) is `(?<![\w.$])new[ \t\r\n]+([A-Za-z_$][\w$.]*)` and never
looks at what follows the name, so an array creation is read as a constructor call.
`reach_of` (`scoring.py:568-573`) then turns it into a `record` reach.

Reproduce:

    // shop/Receipt.java  package shop; @Entity public class Receipt { public long id(){return 0;} }
    // shop/Till.java     package shop; public class Till { public Object ring(){ return new Receipt[10]; } }

    shop.Till 1 [('record', 'shop.Receipt', 'Receipt', 'builds one: annotated with Entity')]

`new Receipt[10]` builds **zero** `Receipt`s. This is the one finding that is not a
name-resolution subtlety: criterion 1 says reach counts "the persistent records it writes", and
this counts a record nothing wrote. Guard: refuse a `_CONSTRUCTED` match whose next non-space
character is `[`.

### 5. The enum-constant shape from attempt 3 is now load-bearing, not a nit

Attempt 3 filed this as "not a blocker, worth a line in the code", and the line was duly added to
`_declares_rather_than_calls`'s docstring. It still fires, and I re-confirmed it:

    // shop/Palette.java  package shop; public class Palette { public static final long RED = 1; }
    // shop/Desk.java     package shop; import static shop.Palette.RED;
    //                    public class Desk { enum Color { RED(1); Color(long v) { } }
    //                                        public long ring() { return 2; } }

    shop.Desk 1 [('module', 'shop.Palette', 'Palette', 'calls RED, imported statically from it')]

`Desk` calls nothing. It was acceptable while nothing on the page counted the overstating
readings. Attempt 4's "the one place" sentence counts them, so this one now has to be either
fixed or named alongside the parameter reading.

### 6. `new B(...)` and `B.of(...)` are counted differently, and this one is LIVE on the page

`reach_of`'s `constructed` loop (`scripts/module_depth_map/scoring.py:568-573`) calls `note()`
**only** when the constructed target matches the `persistentRecord` rule. So building a non-entity
collaborator reaches nothing, while a static call on that same collaborator reaches it. A pure
spelling change moves the fan and the leverage figure.

    // shop/B.java              package shop; public class B { public B(long a){} public static B of(long a){return new B(a);} }
    // shop/ViaFactory.java     package shop; public class ViaFactory     { public B make(long a){ return B.of(a); } }
    // shop/ViaConstructor.java package shop; public class ViaConstructor { public B make(long a){ return new B(a); } }

    shop.ViaFactory     1 [('module', 'shop.B', 'B', 'called on B')]
    shop.ViaConstructor 0 []

Identical coordination, different reach, different leverage.

**This is self-inconsistent on the specimen card the page leads with, today.**
`SavingsAccountController` is credited with reaching `DepositResponse`, `WithdrawalResponse` and
`ClaimedRewardResponse` — three of its nine reaches, each `called on X` through an `X.of(...)`
static factory — while
`backend/src/main/java/io/dataroots/savingstreak/web/SavingsAccountController.java:57` writes
`new SavingsAccountResponse(...)` and is credited with nothing for it. Same file, same kind of
thing, counted or not purely on how it was spelled. And `SavingsAccountController` is the module
`scoring.widestReach` is anchored to, so its reach of 9 is the scale every other fan on the page
is drawn against.

Attempt 1 closed "a data carrier called statically counts as a *module called*" as a defensible
judgement call. This is its mirror and has never been litigated. It is also not among the
omissions the page and the README undertake to name exhaustively: `README.md:232-247` lists three
missed spellings of a call, and `page.py:325-335` names a collaborator handed in as an argument
and a record loaded rather than created. A `new` of an in-tree module that is not an entity is
neither. Either count it, or name it as a fourth omission.

### 7. A record's components are never read as fields (nit)

`_fields_of` (`scripts/module_depth_map/javasource.py:805`) reads only members of the type
**body**; a record's components live in its header.

    // shop/Register.java package shop; public class Register { public long tally(long n){ return n; } }
    // shop/Basket.java   package shop; public record Basket(Register register, long items) {
    //                        public long total(){ return register.tally(items); } }

    shop.Basket 0 []

`register` is then resolved as if it were a type name and finds nothing. A component is *held*,
not "handed in as an argument rather than held", so this is an understatement the page and README
do not name. Latent here: every record in `backend/src/main/java` with a body reaches through a
static factory's parameter rather than through a component.

### What to do

Either fix the four and leave the sentence, or keep the sentence honest. Both are acceptable; a
mixture is not. Every fix below uses data the parser already hands over, and all four err towards
saying less than the source rather than more:

1. fold this module's in-tree supertypes' declared method names into `declares`, or simply skip
   the static-import credit entirely when the module has a supertype the graph holds;
2. fold the nested names of in-tree supertypes into `shadowed`;
3. fold `declared.type_parameters` into `shadowed`;
4. refuse a `_CONSTRUCTED` match followed by `[`;
5. count a `new` of any in-tree module as reaching it — the `constructed` loop already resolves
   the target, it just declines to `note()` unless the target is an entity — or name the gap.

If any of them is judged too costly, the honest alternative is to amend `page.py:343`,
`README.md:253-262` and `reach_of`'s docstring so the page stops claiming a count it cannot keep —
say "some readings go the other way" and name them, rather than "the one place".

### The test gap that let these through

`scripts/module_depth_map/tests/reachandthefan/test_reach_is_the_numerator_of_depth.py` has
negative twins for a qualified `new`, a qualified field type, an imported nested type, a static
import from outside the tree, a static import's holder versus the file's own package, and a
parameter borrowing a field name. Nothing covers **a supertype's method**, **a supertype's nested
type**, **a class type parameter**, **an array creation**, or **a `new` of a non-entity module**. Each wants a twin beside its
existing positive, and each should redden when the guard is undone — check that, because two of
them are one character of regex away from coming back.

### Everything I checked myself and found good — do not redo this work

- `./.scratch/module-depth-map/lab.sh checks` → exit 0. Backend **113 tests, 0 failures, BUILD
  SUCCESS**; `npm run typecheck` clean. `python3 -m unittest discover -t scripts -s
  scripts/module_depth_map/tests` → **303 OK**.
- **Attempt 3's five repros, run verbatim, all resolved.** (1a) qualified field type
  `other.Receipt` → `('module','other.Receipt','called through the field receipt, which holds a
  other.Receipt')`, which is the *right* answer, not an unfixed defect: `other.Receipt` is a module
  in that fixture tree and `receipt.id()` is a real call on it. The variant with the field typed
  `com.external.Receipt`, genuinely outside the tree, gives **0**. (1b) `Holder.Row` field → **0**.
  (2) imported nested type → **0**. (3) static import from outside the tree → **0**.
  (4) static import versus the file's own package → `('module','p.Helper','called on Helper')`,
  the right card, with the real reach recovered. (5) parameter shadowing a field → unchanged and
  now admitted on the page.
- **The live `CustomerController` shape is genuinely fixed.** I copied `backend/src/main/java` to a
  scratch directory, added `SavingsAccountResponse.of(account);` to `worthOf`, and ran the tool:
  reach stays **3** (`AccountsService`, `DepositsService`, `PointsService`) with no line to
  `web.SavingsAccountResponse`. Attempt 3 saw it go to 4.
- **Every reach entry in the committed graph verifies against the source.** I wrote my own checker
  that, for all 71 modules, greps the module's `.java` for what each evidence string claims — a
  static call `X.m(`, a call through the named field plus that field's declared type, a `new X`
  plus `@Entity` on the target, a static import plus a bare call, `@Transactional`. **59 of 59
  verify**; the one my regex flagged (`DepositRepository`'s
  `giveEveryDepositWhatRemainsOfIt is annotated with Transactional`) is true in the source, with
  `@Transactional` sitting above `@Modifying` and `@Query`. `WithdrawalsService` (all 8) and
  `SavingsAccountController` (all 9) also checked by hand against the files.
- **Criterion 6 holds mechanically** and is now enforced by the suite as well as by me: every
  non-transaction reach carries a `moduleId` the graph contains and a non-empty `matched`, the
  transaction is the only kind with a null `moduleId`, and `reach.count == len(reach.reaches)`
  everywhere. Findings 1-4 point a line at the *wrong* card, never at an absent one, which is why
  they are filed against criterion 1 and criterion 6 stays ticked.
- **Determinism.** Two fresh `python3 scripts/module-depth-map.py` runs are byte identical to each
  other **and** to the committed `docs/module-depth-map.json` and `docs/module-depth-map.html`
  (`cmp` on all four). `git diff HEAD~2 HEAD -- docs/module-depth-map.json` is empty.
- **Playwright/chromium over the committed page** (`file://docs/module-depth-map.html`), light and
  dark, 1024 and 1280: **71 cards each pass, 0 console messages, 0 page errors, 0 failed
  requests**, `scrollWidth - clientWidth == 0` in all four. Per card, cross-checked against the
  graph: the count and order of `svg.fan line` and `svg.fan circle` equal `reach.reaches`, each
  `class` equals the entry's `kind` (line and circle both), each `<title>` equals
  `kind name — matched`, and every foot sits inside the viewBox allowing for its radius **and**
  computed stroke width. **71 cards, 0 mismatches, 0 clipped feet.** Attempt 1's clipping stays
  gone: `SavingsAccountController`'s nine feet run 2.50 … 197.50 in a `0 0 200 46` viewBox, and the
  4x card screenshot shows nine whole circles.
- **Criterion 5 is met and readable without labels.** Measured in the browser, bar width as a share
  of its track against fan span as a share of the card: `DemoData` 6.5% over 75.8% (7 lines),
  `WithdrawalsService` 25.8% over 86.7% (8), `DepositsService` 32.3% over 65% (6),
  `SavingsAccountController` 100% over 97.5% (9), `CustomerController` 35.5% over 32.5% (3),
  `RewardController` 9.7% over a single centred line. Screenshots
  `…review.4.card4x-{SavingsAccountController,DemoData,WithdrawalsService,RewardController}.png`.
- **Attempt 3's CSS nit is fixed and I confirmed it in chromium**: `DepositRepository`'s
  `div.bar.unscored` computes `font-style: normal`, `margin-top: 0px`, `font-size: 16px` and a
  dashed border, in both themes, while the `never scored` paragraph beside it is still italic.
- **Criteria 2 and 7 are real, not decorative.** `test_a_deep_module_coordinates_several_things_behind_one_method`
  pins `{"reach": 5, "interfaceCost": 2, "leverage": 2.5}`;
  `test_a_pass_through_coordinates_one_thing_per_method` pins
  `{"reach": 2, "interfaceCost": 4, "leverage": 0.5}` with `len(methods) == reach.count`;
  `test_padding_the_implementation_changes_neither_reach_nor_depth` asserts the padded fixture is
  more than twice the lines with `reach` and `depth` identical.
- **Mutation checks I ran myself**, each applied to the real tree and reverted, tree left clean:
  `written_names_in` → `names_in` in `reach_of` reddens the 3 field tests;
  `resolve(imported.type)` → `resolve(imported.type.rsplit('.',1)[-1])` reddens
  `test_a_member_imported_statically_from_outside_this_tree_reaches_nothing`; undoing the
  `candidate_ids` binding and putting static imports back into its first loop reddens
  `test_an_import_of_a_nested_type_binds_the_name_it_spells_and_nothing_else` and
  `test_a_static_import_does_not_put_its_holder_in_front_of_the_files_own_package`.
- **Refusals work and say why.** A configuration with `reach` deleted and one still claiming
  `module-depth-map-scoring/1` both exit **4**, write neither output file (confirmed by removing
  both paths first), and log `WARNING module_depth_map.cli refused to run: … reach is missing, and
  it has to be an object. Nothing is scored with a rule nobody wrote`. Changing
  `reach.persistentRecord.when` to `@Table` really does change four modules' fans.
- **The tool logs its own flow.** A full `--log-level DEBUG` run over the repository: 322 lines,
  **0 WARNING, 0 ERROR**, `INFO module_depth_map.graph graph built roots=backend/src/main/java
  filesSeen=71 filesParsed=71 filesUnparsed=0 packages=8 modules=71 scored=35 neverScored=36`,
  `INFO module_depth_map.graph furthest reach
  module=io.dataroots.savingstreak.web.SavingsAccountController reach=9 over interfaceCost=31
  leverage=0.29`, `INFO module_depth_map.graph deepest
  module=io.dataroots.savingstreak.accounts.DemoData leverage=3.5 reach=7 interfaceCost=2`.
- **Shapes I probed and found correct**, so nobody need re-probe them: a javadoc `{@link Bank#of}`,
  a `//` comment and a string literal all reach nothing; a text block holding `Bank.of(1)` reaches
  nothing; a plain on-demand import resolves across packages; a single-type import of an
  out-of-tree type correctly silences a same-named package-mate; a local class declared inside a
  method shadows; a field typed by this module's own nested type shadows; `new other.Receipt()`
  where `other.Receipt` really is the entity reports `record other.Receipt`; a generic field
  `List<other.Receipt>` reaches `other.Receipt` rather than `shop.Receipt`.
- **Application smoke test** on the throwaway database (this branch touches no Java or TS —
  `git diff --name-only` over the range returns only `docs/`, `scripts/` and `.scratch/`):
  deposit 12.50 → **201** with `INFO i.d.s.deposits.DepositsService : deposit accepted depositId=1
  savingsAccountId=1 fromCurrentAccountId=1 amount=12.50 pointsEarned=12`; deposit 0 → **400**;
  withdrawal 9999.00 → **400** with `WARN i.d.s.deposits.WithdrawalsService : withdrawal rejected
  … reason=There is not enough in that savings account to move EUR 9999.00. It holds EUR 12.50.`;
  `GET /api/savings-accounts/9999` → **404**; a reward claim → **201** with
  `INFO i.d.savingstreak.rewards.RewardsService : claim issued redemptionId=1 savingsAccountId=1
  reward=CHARITY_DONATION pointsSpent=10`. **0 ERROR lines** in `…app.4.backend.log`. The Vite page
  at 5173 renders the sign-in card with only Vite's own connect messages and the React DevTools
  notice on the console (`…review.4.app.browser.log`, `…review.4.app.png`).

### Three things closed in earlier attempts, still not re-litigated

- A data carrier called statically counts as a *module called*. Closed in attempt 1.
- The transaction is the one fan line with no `moduleId`. Closed in attempt 1.
- `a.b.C.d()` — a fully-qualified static call — is not followed, and is named on the page and in
  the README as an omission. Accepted in attempt 2, still accepted.

### One judgement call worth closing explicitly, so a fifth reviewer need not re-open it

A type argument inside a field's type counts as reached: a field `Optional<Deposit> maybe` with
`maybe.isPresent()` gives `('record','shop.Deposit','called through the field maybe, which holds a
Optional<Deposit>')`. The evidence string is **true**, so this is a judgement call rather than a
false statement, and `reach_of` argues for it at the point of use (`List<AScheduledJob>` is held to
reach the jobs). One closing sentence in the README would settle it for good.

### Still unrelated to this branch, still worth its own ticket

A refused deposit of `0` leaves no `io.dataroots.savingstreak` WARN line — only Spring's
`ExceptionHandlerExceptionResolver` at DEBUG — and so does a refused reward claim
(`SNACK_VOUCHER` with 12 points, 400, "Coffee or snack voucher costs 40 points, and this account
has 12"). `WithdrawalsService` logs its refusal; `DepositsService` and `RewardsService` do not.
Pre-existing, untouched here, flagged by all four reviews now.

## Review feedback - attempt 5

All seven of attempt 4's findings are resolved — six fixed, one (the enum constant) named on the
page, which attempt 4 sanctioned as one of its two acceptable outcomes. I ran every repro verbatim
myself rather than taking the log's word for it, and each answers what it should. **Do not redo any
of that work**; the full list of what I re-checked and found good is at the bottom of this section,
and it is nearly the whole ticket. The committed `docs/module-depth-map.json` is **correct**: I
verified all 76 reach entries against the source mechanically, and the whole graph is byte-identical
to a fresh run.

One thing sends it back, and it is small. Attempt 4 sent this ticket back because
`page.py` claimed "It is the one place a fan can be longer than the source" when there were five.
Attempt 5 replaced that with a new counted claim — `page.py:338`, mirrored at `README.md:268` and in
`reach_of`'s docstring (`scoring.py:456`):

    "Three readings go the other way, and they are named here rather than left to be found."

There is a fourth, and it is not one of the three. That is the same falsifiable-counting-claim
defect, one attempt later.

### 1. A qualified `new` of a nested type is read as a static call on the enclosing module (blocks criterion 1)

`javasource._A_RECEIVER` runs over the same body `_CONSTRUCTED` does, and `new Holder.Row()`
contains the substring `Holder.Row(`, which is exactly the shape of a static call. So the enclosing
module goes into the fan with the evidence string `called on Holder` — and no method is called on
`Holder` anywhere in the file.

Reproduce — two files in a directory, then point the tool at it:

    // shop/Holder.java
    package shop;
    public class Holder {
        public static class Row { public Row() {} }
        public long tick() { return 1; }
    }

    // shop/Till.java
    package shop;
    public class Till { public Object ring() { return new Holder.Row(); } }

    python3 scripts/module-depth-map.py --source <that dir> --graph /tmp/g.json --page /tmp/p.html

Graph says:

    shop.Till 1 [('module', 'shop.Holder', 'called on Holder')]

and the run logs `DEBUG module_depth_map.scoring reach read name=Till modules=1 adapters=0
records=0 transaction=no`. Expected reach 0. `Holder.Row` is a nested type, and this tool's own
stated rule is that a nested type is not a module and reaches nothing — `resolve`'s dotted branch
(`scoring.py:508`) returns `None` for `Holder.Row`, and `README.md:211-214` says so in as many
words. The receiver reading then credits the outer name anyway, with a sentence a reader can check
against the file and find wrong. It is an overstatement, so it is not covered by the "each leaves a
fan shorter than the source" paragraph, and it is none of the three readings the page undertakes to
name (no borrowed field name, no enum constant, no type from outside the tree).

It is **not live**: `grep -rn "new [A-Z][A-Za-z0-9_]*\.[A-Z]" backend/src/main/java` returns
nothing, and `CustomerController` — which does build a nested `SavingsAccountResponse` — writes it
by simple name after a single-type import, which attempt 4's binding fix handles correctly. So the
committed page is unaffected. It is one spelling change away on three live nested types
(`CustomerAccountsResponse.{CurrentAccountResponse,SavingsAccountResponse}`,
`PointsCreditRepository.EarnedPoints`).

**Two resolutions, both acceptable — please pick one and make the page match it.**

- Refuse an `_A_RECEIVER` match whose receiver is immediately preceded by `new` (the
  `constructed` reading already has that name and already declines it), and add the negative twin
  beside the existing qualified-`new` tests in
  `scripts/module_depth_map/tests/reachandthefan/test_reach_is_the_numerator_of_depth.py`; **or**
- name it as a fourth overstating reading, exactly the way attempt 3's parameter-shadowing finding
  and attempt 4's enum constant were named.

**Whichever you pick, stop counting them on the page.** "Three readings go the other way" is the
third counted claim this branch has made about a regex reader of Java, and the third one a reviewer
has falsified. Write "Readings that go the other way — the ones known, named here rather than left
to be found" and list them. That ends this loop permanently and costs nothing a reader values: the
list is what makes the floor's edge visible, not the number in front of it.

### 2. A multi-declarator field with an initialiser is dropped silently, and the file promises it never is

`javasource._field_in:856` does `before = text.split("=", 1)[0]`, so only the first name survives.

    private Repo a = null, b = null;   ->  fields [('a', 'Repo')], and no log line at all
    private Repo a, b;                 ->  no field, and DEBUG module_depth_map.javasource member
                                           not read as a field line=2 reason=nothing before the
                                           name reads as a type member=private Repo a, b

Verified by parsing both with `javasource.parse`. `_fields_of`'s docstring says of the two ways a
member can be declined that "Both are logged", and `_field_in`'s says "several names declared at
once — is declined with a line". The initialised form is neither logged nor read. A later
`b.save(x)` then falls to the receiver-as-type-name branch, resolves to nothing, and the fan loses
a collaborator with no trace.

This errs in the safe direction and is not live here. It is the same defect attempt 2 filed as its
finding 4 (`private int xs[];`), in the file whose whole promise is that nothing is dropped
silently — so it wants either the missing log line or the second name read.

### 3. A call with explicit type arguments is read as a declaration, and the reason logged for it is false

`javasource._declares_rather_than_calls:997` answers "declaration" when the character in front of
the name is `>`. A call written with explicit type arguments puts `>` immediately in front of it.

Reproduce:

    // p/Money.java  package p; public class Money { public static long of(long c) { return c; } }
    // p/M.java      package p;
    //   import static p.Money.of;
    //   public class M {
    //       public long go() {
    //           java.util.List<String> empty = java.util.List.<String>of();
    //           return of(1) + empty.size();
    //       }
    //   }

    p.M 0 []

with `DEBUG module_depth_map.scoring static import not read as a call name=M member=of
from=p.Money, because this module's body declares that name itself and a declaration is not a call`.
`M` declares no `of`. Delete the `<String>` and the same file gives
`p.M 1 [('module', 'p.Money', 'calls of, imported statically from it')]`, so the type-argument list
is the whole of the difference.

The page is only understated by this, so it is not a blocker — but the docstring's justification
("where the two readings are genuinely ambiguous this answers declaration") does not cover it: a
dotted receiver followed by a type-argument list is unambiguously a call, and the DEBUG line the
tool prints about it is a false statement about the source. Not triggered by this repository today.

### 4. The borrowed-name reading is described more narrowly than it fires (nit)

`page.py:347` and `README.md:271` both say "a parameter or a local variable that borrows a field's
name". A `catch` parameter and a **nested class's own field** do it too, and neither reads as
either of those. Both verified:

    // p/M.java  package p;
    //   public class M {
    //       private final Repo repo = null;                 // Repo extends JpaRepository
    //       static final class Inner {
    //           private final Other repo = null;
    //           void go() { repo.ping(); }
    //       }
    //       public long size() { return 1; }
    //   }

    p.M 1 [('adapter', 'p.Repo', 'called through the field repo, which holds a Repo')]

`M` never touches its `Repo`. Same answer for `catch (RuntimeException repo) { repo.getMessage(); }`.
"a name in an inner scope that borrows a field's name" covers all four.

### Everything I checked myself and found good — do not redo this work

- `./.scratch/module-depth-map/lab.sh checks` → **exit 0**. Backend **113 tests, 0 failures, BUILD
  SUCCESS**; `npm run typecheck` clean (log:
  `.scratch/module-depth-map/logs/03-reach-and-the-fan.review.5.checks.log`).
  `python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests` → **317 OK**.
- **All seven of attempt 4's repros, run verbatim.** (1a) static import vs an inherited method →
  `shop.Desk 0 []`; (1b) the same through `this.of(1)` → `0`; (2) inherited nested type →
  `shop.Till 0 []`; (3) class type parameter → `0`; (4) `new Receipt[10]` → `0`; (6) `new B(a)` and
  `B.of(a)` now agree — `ViaConstructor 1 [('module','shop.B','builds one')]` against
  `ViaFactory 1 [('module','shop.B','called on B')]`; (7) record components →
  `shop.Basket 1 [('module','shop.Register','called through the field register, which holds a
  Register')]`. (5), the enum constant, still answers `shop.Desk 1 [… 'calls RED, imported
  statically from it']` — documented, as permitted.
- **Supertype resolution probed seven more ways, all correct**: a generic supertype
  (`extends Base<Long>`), a qualified one (`extends other.Base`), an imported one, a nested one
  (`extends Holder.Base`), an interface with a `default` method, and a transitively opaque chain all
  suppress the static-import reading; the control — a supertype declaring no such name — still
  leaves it read (`shop.Desk 1 [… 'calls of, imported statically from it']`). A supertype cycle
  (`A extends B`, `B extends A`) is walked once and does not hang.
- **The `new` counting probed eight more ways, all correct**: `new` inside a `//` comment, a
  javadoc `{@code}`, a string literal and a text block all reach nothing; `new Receipt[]{ }` and
  `new Receipt[2][3]` reach nothing; `new B<String>()` and an anonymous subclass `new B(){...}`
  reach `shop.B`; `new Receipt()` where a single-type import binds `Receipt` to an out-of-tree type
  reaches nothing.
- **Every reach entry in the committed graph verifies against the source.** I wrote my own checker
  that, for all 71 modules, greps the module's own `.java` for what each evidence string claims — a
  `new X(`, a static-style call `X.m(`, a call through the named field plus that field's declared
  type, a static import plus a bare call, `@Transactional`. **76 of 76 verify, 0 problems.**
  The two riskiest by hand: `SavingsAccountController` has no import of the nested
  `SavingsAccountResponse`, so its line 57 `new SavingsAccountResponse(...)` really does bind to the
  top-level module it is credited with; `CustomerController` *does* import the nested one, and is
  correctly credited with nothing for its line 96.
- **Criterion 6 holds mechanically**: `reach.count == len(reaches)` on all 71, every non-transaction
  entry has a `moduleId` the graph contains and a non-empty `matched`, and the transaction is the
  only kind with a null `moduleId`. Finding 1 points a line at a card that *exists*, which is why it
  is filed against criterion 1 and criterion 6 stays ticked.
- **No regression on ticket/02.** Compared the committed graph against
  `ticket/02-what-a-caller-must-learn:docs/module-depth-map.json`: same 71 module ids, **zero**
  modules with a changed `interface`, **zero** with a changed `excludedBy`. `reach` is new
  everywhere, which is this ticket.
- **Determinism.** Two fresh `python3 scripts/module-depth-map.py` runs are byte identical to each
  other **and** to the committed `docs/module-depth-map.json` and `docs/module-depth-map.html`
  (`cmp` on all four). No `/Users` path and no timestamp anywhere in either output.
- **Playwright/chromium over the committed page** (`file://docs/module-depth-map.html`), light and
  dark, 1024 and 1280: **71 cards each pass, 0 console messages, 0 page errors, 0 failed requests**
  (`…review.5.browser.log`), `scrollWidth - clientWidth == 0` in all four. Per card, cross-checked
  against the graph: the count and order of `svg.fan line` and `svg.fan circle` equal
  `reach.reaches`, each `class` equals the entry's `kind` on both line and circle, each `<title>`
  equals `kind name — matched`, and the bar carries `unscored` exactly for the excluded modules.
  **0 mismatches in all four passes.** The card prose was checked too: for all 71, the
  `reaches N: …` sentence and the leverage figure match the graph.
- **Attempt 1's clipping stays fixed, measured rather than eyeballed.** Against each circle's *own*
  computed stroke width, the worst overhang past the viewBox over all 71 cards is **0.0000 user
  units** — flush by design, since `SHAPE.ink` budgets exactly for it. The new widest fan is
  `WithdrawalsService` (11 feet, 2.50 … 197.50 in a `0 0 200 46` viewBox); at 4x with the outermost
  feet cropped and magnified (`…review.5.foot-left.png`, `…review.5.foot-right.png`) both are whole
  circles clear of the card edge.
- **Criterion 5 is met and readable without labels**, measured as bar width over its track against
  fan span over the card: `WithdrawalsService` 25.8% over 97.5% (11 lines), `DemoData` 6.5% over
  62.0% (7), `DepositsService` 32.3% over 70.9% (8), `CustomerController` 35.5% over 35.5% (4),
  `AccountsService` 96.8% over 44.3% (5), `RefusalsAsHttp` 90.3% over nothing, `RewardController`
  9.7% over one centred line. See `…review.5.package-deposits.{light,dark}.png` and
  `…review.5.card4x-*.png` — the deep/shallow contrast reads at a glance in both themes.
- **Criteria 2, 3 and 7 are real, not decorative.** `test_a_deep_module_coordinates_several_things_behind_one_method`
  pins `{"reach": 5, "interfaceCost": 2, "leverage": 2.5}`;
  `test_a_pass_through_coordinates_one_thing_per_method` pins
  `{"reach": 2, "interfaceCost": 4, "leverage": 0.5}` with `len(methods) == reach.count`;
  `test_padding_the_implementation_changes_neither_reach_nor_depth` asserts the padded fixture is
  more than twice the lines with `reach` and `depth` identical; every module carries `depth` with
  both terms it was taken from.
- **Eight mutation checks, each applied to the real tree and reverted, tree left clean** — every one
  of attempt 5's fixes has a test that reddens without it: array-creation guard disabled → 2 red;
  `new` of a module not counted → 5; inherited `declares` dropped → 1; record components not read →
  2; the bare call read over the `this.`-stripped body again → 1; type parameters dropped from
  `shadowed` → 1; inherited nested dropped → 1; the opaque guard dropped → 1.
- **Refusals work and say why.** A configuration with `reach` deleted and one still claiming
  `module-depth-map-scoring/1` both exit **4**, write neither output file (confirmed by removing
  both paths first), and log `WARNING module_depth_map.cli refused to run: … reach is missing, and
  it has to be an object. Nothing is scored with a rule nobody wrote`.
- **The tool logs its own flow.** A full `--log-level DEBUG` run over the repository: 341 lines,
  **0 WARNING, 0 ERROR**, `INFO module_depth_map.graph graph built roots=backend/src/main/java
  filesSeen=71 filesParsed=71 filesUnparsed=0 packages=8 modules=71 scored=35 neverScored=36`,
  `INFO module_depth_map.graph furthest reach module=io.dataroots.savingstreak.deposits.WithdrawalsService
  reach=11 over interfaceCost=8 leverage=1.38`, `INFO module_depth_map.graph deepest
  module=io.dataroots.savingstreak.accounts.DemoData leverage=3.5 reach=7 interfaceCost=2`, and each
  of the nineteen out-of-tree supertypes leaves its own `supertype not read … because this graph
  holds no module of that name`. No `System.out`/`print()` anywhere in the tool; every module has
  its own `logging.getLogger("module_depth_map.*")`.
- **Application smoke test** on the throwaway database (this branch touches no Java and no TS —
  `git diff --name-only` over the range returns only `docs/`, `scripts/` and `.scratch/`):
  `POST /api/savings-accounts/1/deposits` 12.50 → **201** with `INFO i.d.s.deposits.DepositsService :
  deposit accepted depositId=1 savingsAccountId=1 fromCurrentAccountId=1 amount=12.50 pointsEarned=12`;
  amount 0 → **400**; withdrawal 9999.00 → **400** with `WARN i.d.s.deposits.WithdrawalsService :
  withdrawal rejected savingsAccountId=1 toCurrentAccountId=1 amount=9999.00 balance=12.50
  reason=There is not enough in that savings account to move EUR 9999.00. It holds EUR 12.50.`;
  `POST …/redemptions` `SNACK_VOUCHER` → **400** ("Coffee or snack voucher costs 40 points, and this
  account has 12"); `CHARITY_DONATION` → **201** with `INFO i.d.savingstreak.rewards.RewardsService :
  claim issued redemptionId=1 savingsAccountId=1 reward=CHARITY_DONATION pointsSpent=10`;
  `GET /api/savings-accounts/9999` → **404**. **0 ERROR lines** in `…app.5.backend.log`. The Vite
  page at 5173 renders the sign-in card with only Vite's own connect messages and the React DevTools
  notice (`…review.5.app.browser.log`, `…review.5.app.png`).

### Four things closed in earlier attempts, still not re-litigated

- A data carrier called statically counts as a *module called*. Closed in attempt 1.
- The transaction is the one fan line with no `moduleId`. Closed in attempt 1.
- `a.b.C.d()` — a fully-qualified static call — is not followed, and is named on the page and in the
  README as an omission. Accepted in attempt 2.
- A type argument inside a field's type counts as reached, and the evidence string for it is true.
  Closed in attempt 4.

### Still unrelated to this branch, still worth its own ticket

A refused deposit of `0` leaves no `io.dataroots.savingstreak` WARN line — only Spring's
`ExceptionHandlerExceptionResolver` at DEBUG — and so does a refused reward claim.
`WithdrawalsService` logs its refusal; `DepositsService` and `RewardsService` do not. Pre-existing,
untouched here, flagged by all five reviews now.
