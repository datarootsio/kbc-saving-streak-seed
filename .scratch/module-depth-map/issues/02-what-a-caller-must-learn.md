# 02: What a caller must learn

**What to build:** Each module on the page carries a bar whose width is the cost of its interface —
everything a caller has to learn in order to use it correctly. A reader comparing two modules can see
which one asks more of them before reading a word.

Three kinds of thing are drawn but never scored, because they are shallow by construction and scoring
them would bury the real finding: values that only carry data across a seam, repository interfaces
whose implementation is generated rather than written, and the application's entry point. Every one of
those exclusions is attributable to a named rule in a checked-in configuration file, so a reader who
disagrees with a score can point at the rule that produced it.

**Blocked by:** 01 (Modules on a page).

**Status:** needs-info

- [ ] Interface cost counts every method reachable from outside the module, every parameter of those methods, and every distinct type crossing the seam in a parameter or a return
- [x] A method handing back a domain type costs a caller more than one handing back a primitive
- [x] Each module's bar width on the page is its interface cost, and comparable between modules
- [x] Scoring weights and exclusion rules live in a configuration file beside the tool, not inside it
- [x] Data carriers, generated repository interfaces and the entry point are excluded from scoring
- [x] Excluded modules are still drawn in the graph and on the page, marked as excluded
- [x] Every exclusion in the graph names the rule that caused it, and no module is excluded without one
- [ ] Changing a weight or an exclusion in the configuration file changes the output without any edit to the analyser
- [x] Fixture source trees establish each scoring rule independently of the application's own code

## Review feedback - attempt 2

Most of this branch is right, and I want to say so before the list of what is not. I
verified the config-driven scoring end to end by hand-editing `scoring.json` myself, and
it does what the ticket asks. What sends the ticket back is a family of *silent* mis-reads
in the parser — the same family as the two bugs attempt 2 found in the inherited commit,
which were fixed in one direction only.

### What I confirmed working (do not redo this work)

- **Criterion 8, empirically.** I edited `scripts/module_depth_map/scoring.json` in place
  (`sed -i '' 's/"typeToLearn": 2/"typeToLearn": 5/'`) and reran: `widestInterface` moved
  31 -> 52, 18 module costs moved, the page's own prose changed from "counts 2" to
  "counts 5". I then hand-inserted a fourth rule
  `{"rule":"reviewer says so","because":"...","when":{"nameEndsWith":["Controller"]}}`:
  5 modules became `never scored - reviewer says so` with `matched: "name ends with
  Controller"`, `modulesScored` 35 -> 30, the scale recomputed 31 -> 30, and my wording
  appeared verbatim as a bullet on the page. Not one line of the analyser edited.
  Screenshot: `logs/02-what-a-caller-must-learn.review.2.reviewer-config.png`.
- **Criterion 7 on the real graph.** Every one of 71 modules is scored xor excluded; every
  exclusion names a rule the file holds and the fact that matched it; zero modules
  excluded by an unnamed rule.
- **Criterion 3.** Playwright, light and dark, 1024 and 1280: 71 cards, 35 bars, 36
  never-scored labels, no card with both or neither, one shared track (235.41 px at 1280),
  worst bar deviation from `cost x track / widest` **0.0000 px**, widest module fills 100%,
  no sideways scroll, **0 console messages, 0 page errors, 1 network request**.
- **Criterion 2.** Fixture `Primitive.value() -> long` costs 1, `Domain.value() -> Receipt`
  costs 3.
- **Both bug claims in the implementer's report are true.** I ran the inherited analyser
  from `678e35c` (`git archive 678e35c scripts/module_depth_map`) over a fixture:
  `@SpringBootApplication(scanBasePackages = {"shop"})` gave `excludedBy: None, cost: 2`
  (entry point scored), and `<T> T first(List<T> of)` priced `T` and `U` as domain types
  (cost 8 vs 4 now). Both are fixed at HEAD.
- **Determinism.** 3 runs under differing `TZ`/`PYTHONHASHSEED`/`LC_ALL` are byte-identical
  to each other and to committed `docs/`; 0 occurrences of `/Users/` or a date in either
  output; page self-contained (no `src=`/`href=`), stdlib only.
- **Clean bisect.** I reran the tool at all 8 commits with that commit's `scripts/` and
  compared to that commit's `docs/`: `same` at every one.
- **The tests bite.** 5 mutations, each caught by a test named for the property: reverting
  the annotation walk-back to the nearest-brace scan (1 failure), pricing type variables
  (4), giving an excluded module cost 0 (8), hardcoding `"data carrier"` in `graph.py` (1),
  `weights.setdefault(name, 1)` (1).
- **Refusals.** 10 broken configurations each exit 4, log the reason at WARNING, write
  nothing, leave no `.writing` file. Plus exit 2 (no source dir), exit 3 (duplicate module
  id), and an unparseable file reported rather than scored as empty.
- **Checks.** `cd backend && ./mvnw test` exit 0 (113 tests); `cd frontend && npm run
  typecheck` clean; `python3 -m unittest discover -s scripts/module_depth_map/tests -t .`
  160 tests OK.

### What must be fixed

**1. A legal parameter is silently dropped, so criterion 1 does not hold.**
`javasource.py` `_declared_parameters` / `_TRAILING_NAME`: a C-style array parameter is
discarded by the `continue` with no word said.

    public void take(int xs[], String name) {}      -> parameters ["String"],   cost 2
    public void alsoTake(int[] xs, String name) {}  -> parameters ["int[]","String"], cost 3

Two spellings of the same interface, two different costs, nothing in `source.unparsed` and
nothing logged. Reproduce: put both methods in one class under a scratch source root and
run the tool. This is the parser's own doctrine broken — everywhere else it fails a whole
file rather than lose something quietly.

**2. The brace-in-annotation fix was applied backwards only; forwards it is still broken.**
`_body_starts_at` (line ~293) and the pre-existing `_body_ends_at` both take "the first
brace after the name" as the body, so a brace inside a *record component's* annotation
hijacks the body boundary. `_annotations_before` was taught to walk back over annotation
arguments; nothing taught the forward scan the same thing.

    public record R(@Values({"a", "b"}) String s, long cents) implements Comparable<R> {
        public String pretty() { return s + cents; }
        public int compareTo(R other) { return 0; }
    }

Result: `methods: []`, `typesCrossingTheSeam: []`, `supertypes` empty — the entire
interface read as nothing, silently, `source.unparsed` empty. Add a nested type inside and
the whole file fails to parse instead ("a type this parser cannot place: Row on line 3 sits
1 brace(s) deep in nothing it can name"). This repo has 26 records; a `@Schema(allowableValues
= {...})` or `@Values({...})` on one component is an ordinary future edit. It also hollows
out criterion 6 — an excluded module is drawn "with its interface visible so a reader can
see what the rule declined to measure", and here there is nothing to see.

The root cause is that `mask_comments_and_literals` leaves annotation-argument braces in
the masked text. Blanking them during masking would fix `_body_starts_at`, `_body_ends_at`
and `_member_headers` at once, and would make the ~55 lines of reverse scanning in
`_annotations_before` / `_before_balanced` unnecessary.

**3. A rule the file names can silently exclude nothing.**
`scoring.py` `_strings` validates only "non-empty string". The parser stores **simple**
names in `declared.annotations` and `declared.supertypes`, so a qualified name in the
config can never match — and is accepted without a word:

    "when": {"annotatedWith": ["org.springframework.boot.autoconfigure.SpringBootApplication"]}

gives `SavingStreakApplication excludedBy = None, cost = 2`, `modules never scored
rule=entry point modules=0`, exit 0, no warning. The entry point is scored like anything
else. Same shape for `typesEveryCallerAlreadyKnows: ["java.lang.String", ...]` — `String`,
`List` and `Optional` all flip to `mustBeLearned: true` and `AccountsService` goes 30 -> 38.

Writing the qualified form is the natural mistake, because the suite proves the qualified
form works in the *source* (`test_the_repository_is_recognised_however_it_is_written` has
`extends org.springframework.data.jpa.repository.JpaRepository`) and nothing says the
config is different. `load()`'s own docstring says every check there exists because "the
alternative is a silent one ... a condition this tool does not understand that reads as
'always true'". This is the mirror image, and it is the one shape `load` lets through.
Refuse a name containing a dot, or match on simple names.

**4. A garbage method appears in the graph on a config edit criterion 8 invites.**
`_member_headers` never moves `start` past the `,` after a stepped-over member body, so an
enum constant with arguments following a constant with a body becomes a method:

    public enum Kind {
        A("x") { int n() { return 1; } },
        B("y");
        Kind(String s) {}
        public String label() { return null; }
    }

With the `data carrier` rule removed from the config — a supported edit, and the one
`test_taking_a_rule_out_of_the_file_scores_what_it_used_to_exclude` exercises — the graph
carries `{"cost": 1, "name": "B", "parameters": [], "returns": ",", "visibility":
"package-private"}` and `Kind` costs 2 rather than 1. `package-private` is reachable, so
every such constant inflates the cost by `weights.method`.

**5. This branch's own "written together or not at all" claim is false.**
`cli.py` `write_together`: the `os.replace` loop sits **outside** the `except OSError`
guard. Reproduce with `--page` pointing at a path that already exists as a directory:

    $ mkdir -p out/page.html && echo 'STALE GRAPH' > out/graph.json
    $ python3 -m scripts.module_depth_map --source src --graph out/graph.json --page out/page.html
    ...
    File ".../cli.py", line 158, in write_together
        os.replace(beside, path)
    IsADirectoryError: [Errno 21] Is a directory: '.../page.html.writing' -> '.../page.html'
    $ ls out/
    graph.json  page.html  page.html.writing     # graph.json now holds the FRESH document

The graph landed, the page did not, and `page.html.writing` was orphaned. That falsifies
three statements added on this branch: the docstring ("Write every one of these, or leave
all of them as they were"), the README ("the graph and the page are always written together
or not at all"), and the docstring's excuse that "what is left to fail between them is what
would have failed at the `open` above" — the `open` succeeded here. The exception also
escapes `cli.main` as a bare traceback: no WARN, no ERROR-with-exception, which CLAUDE.md
asks for on an unexpected failure. The existing test only exercises a failure during
*staging*; the replace loop has no test.

Related: a `.writing` file whose own `write` fails is never appended to `staged`, so
`_discard` cannot remove it — register the path before opening it.

### Nits worth fixing while you are in here

- `scoring.py` `_condition_met`: `nameEndsWith` is the unguarded fallthrough. Add a fifth
  entry to `CONDITIONS` without a branch and it silently becomes suffix matching. A dict of
  name -> predicate, or an explicit raise, makes the mismatch impossible.
- `page.py:218` decides "never scored" from `interface.cost === null` then dereferences
  `module.excludedBy.rule`. Because the renderer is one IIFE, any future null-cost-without-
  exclusion throws and abandons every package section after it — a truncated page, not an
  error. Branch on `module.excludedBy`, the field that carries the fact.
- `_normalised` never removes the space after a comma inside a type, so one document holds
  both spellings of one type: `tally` records `returns: "java.util.Map<String, Long>"` and
  `parameters: ["java.util.Map<String,Long>"]`. Its docstring says "none where it does not".
- `tests/thisrepository/test_this_repository_is_read_whole.py:116` re-implements the cost
  formula with `2 *` hardcoded for `typeToLearn`, so editing that weight in `scoring.json`
  reds the suite — the opposite of the criterion the ticket claims. Read
  `self.document["scoring"]["weights"]` instead.
- Same file, `:127` asserts exact method counts (11 and 3) against live application code,
  inside a class whose siblings say "nothing here is asserted against the application's own
  code". Adding a public method to `AccountsService` reds the suite. Assert the comparison,
  drop the literals.
- `test_the_rules_live_in_a_file_and_the_file_decides.py:237`'s `len(name) > 4` filter
  exempts `int`, `long`, `void`, `List`, `Map`, `Set` — the names most likely to be
  hardcoded. I checked: none of the 41 familiar names appears quoted in `graph.py`,
  `javasource.py`, `page.py` or `cli.py` today, so dropping the filter is free. That guard
  also skips `scoring.py`, which is where a hardcoded rule name would most plausibly land;
  I grepped it by hand and it is clean, but the guard does not cover it.

### Not this ticket, noted so it is not lost

The application itself is untouched by this branch (`git diff --name-only
ticket/01..ticket/02 -- backend frontend` is empty). I drove it anyway: deposit 12.34 ->
201 with `INFO i.d.s.deposits.DepositsService : deposit accepted depositId=1
savingsAccountId=1 fromCurrentAccountId=1 amount=12.34 pointsEarned=12`; deposit 0 -> 400
"A deposit has to be an amount of more than zero, and 0 is not."; savings account 9999 ->
404; over-priced claim -> 400 "Cinema ticket costs 100 points, and this account has 12.".
No stack traces. But those three refusals leave **no WARN line from
`io.dataroots.savingstreak`** — only Spring's DEBUG `ExceptionHandlerExceptionResolver`
lines — whereas `WithdrawalsService` and `SavingsAccountController` do log
`log.warn("withdrawal rejected ... reason={}")`. Pre-existing, and a gap against CLAUDE.md's
"WARN on every refusal with its reason".

## Review feedback - attempt 4

Attempt 3's five fixes are real and I could not break any of them; attempt 4's three finds
are real and each is guarded by a test that bites. I mutation-checked all nine, one at a
time, and every one reds the suite (list below). So this round is not "the same thing
again": the shapes named in the attempt-2 review are closed.

What sends it back is three new things, two of which are the same *family* — a legal
construct read as the wrong thing with no word said — and one of which is in the very
function attempt 2's defect 5 was about.

### 1. A record that overrides one of its own accessors has that method counted twice

`javasource.py` `_methods_of` synthesises an accessor for every record component and then
*also* reads every member header, with nothing checking whether one of those members is
that accessor. Java compiles exactly one `cents()`; the tool reports two.

    public record Coin(long cents, List<String> tags) {
        @Override public long cents() { return cents < 0 ? 0 : cents; }
        @Override public List<String> tags() { return List.copyOf(tags); }
    }
    public record Plain(long cents, List<String> tags) { }

A caller meets the identical interface on both. Run the tool over a source root holding
the two, with the `data carrier` rule taken out of `scoring.json` — the supported edit
criterion 8 invites, and the one
`test_taking_a_rule_out_of_the_file_scores_what_it_used_to_exclude` exercises:

    shop.Coin  cost=4 methods=4   cents[] -> long / cents[] -> long
                                  tags[] -> List<String> / tags[] -> List<String>
    shop.Plain cost=2 methods=2   cents[] -> long / tags[] -> List<String>
    widestInterface: 4            <- Coin also took the scale every other bar is drawn to

Nothing in `source.unparsed`, no WARNING, no `member not read as a method` line. Under the
committed config the cost is `None`, so the page hides it — but the graph still publishes
two `cents()` entries, which is the machine-readable output the spec exists to give agents
(stories 33-34), and it is the same list
`test_an_excluded_module_of_that_shape_is_still_drawn_with_what_it_offers` asserts on.
This is attempt-2 defect 4's exact shape ("a garbage method appears in the graph on a
config edit criterion 8 invites"), moved from enums to records.

Defensive copying or normalising in an accessor is the sanctioned way to write a record
with an invariant, and 11 of this repo's records already carry a body, so it is one line
away. The suite has
`test_a_record_that_writes_a_method_of_its_own_is_still_a_data_carrier`, but the method it
writes is `inEuros()` — a *different* name from the component. The one case that would
have caught this is the one not covered.

Either de-duplicate (a declared zero-arg method whose name is a component's name *is* that
accessor) or fail the file by name. Silently pricing one method twice is the one answer
this parser's own doctrine forbids.

### 2. `write_together` ends the run with a bare traceback, and can name the wrong file

Two faults in the function attempt 2's defect 5 was about, both falsifying statements this
branch added.

**(a) `IndexError` escapes `main`.** `os.makedirs` runs *before* `staged.append`, so when
the first output's directory cannot be made, `staged` is empty and the handler reads
`staged[-1][1]`:

    $ mkdir -p /tmp/t && echo hi > /tmp/t/blocker      # a plain file, not a directory
    $ python3 -m scripts.module_depth_map \
        --graph /tmp/t/blocker/sub/graph.json --page /tmp/t/page.html
    ...
      File ".../cli.py", line 219, in write_together
        "%s could not be written: %s" % (staged[-1][1], unwritable)
    IndexError: list index out of range
    $ echo $?
    1

No WARNING, no ERROR, no exit 5. That contradicts `write_together`'s own docstring ("a
traceback is a kind of silence: it names a line of Python rather than the two files"),
`main`'s comment ("Nothing in here is allowed to end the run with a traceback") and the
README ("Nothing here ends the run with a traceback"). Register the staged entry before
`makedirs`, or report the path from `outputs` rather than from `staged`.

**(b) The refusal names the output that did *not* fail.** Same cause, second output:

    $ mkdir -p /tmp/u && echo hi > /tmp/u/blocker && echo 'STALE' > /tmp/u/graph.json
    $ python3 -m scripts.module_depth_map \
        --graph /tmp/u/graph.json --page /tmp/u/blocker/sub/page.html
    WARNING ... refused to write: /tmp/u/graph.json could not be written: [Errno 20] Not a directory: ...
    exit 5

`graph.json` staged perfectly; `page.html` is what failed. Commit `becfdd7` retired the
false atomicity claim in favour of *saying which output landed* — and here it says the
wrong one. The existing test only asserts the substring "could not be written", so it
passes.

For the record, the parts of defect 5 that *are* fixed, verified by hand: `--page` (and
`--graph`) pointing at an existing directory is refused before a byte is written, exit 5,
WARNING, stale outputs untouched, no `.writing` left; and a genuine partial landing
(`chflags uchg` on `page.html`) logs at ERROR with the exception naming which file landed
and which is the previous run's, exit 5.

### 3. The page's cost breakdown cannot be reconciled with the cost it explains

`page.py` `drawInterface` counts only types with `mustBeLearned: true`, so the breakdown
ignores the `typeEveryCallerAlreadyKnows` weight — one of the four weights the file exists
to let a reader change. With that weight set to 3 and one class
`public int add(int a, int b)`:

    graph:  cost 6   (1 method + 2 parameters + 1 already-known type x 3)
    page:   "6 to learn: 1 method, 2 parameters, 0 types"      1 + 2 + 0 = 3
    bar title: "interface cost 6"

Three of the six come from a type the breakdown says there are none of. The prose two
boxes above does render the configured weight correctly ("...and 3 when every caller
already knows it"), which is what makes the mismatch visible on the same screen: the page
tells the reader already-known types cost 3 each, then hands them a breakdown with none in
it and a total that only adds up if there is one. The whole claim of the page is that a
score can be argued with; this breakdown is only honest at the single value 0, and nothing
on the page says so. Count already-known types too, or show the two counts separately.

### Smaller, worth fixing while you are in here

- **`sealed ... permits` is read as a supertype.** `_INHERITANCE` includes `permits`,
  which names *subtypes*. `_supertypes_in`'s docstring says it reports "what this type is
  built on: `extends X`, `implements Y, Z`". Consequence:
  `public sealed interface Payment extends Comparable<Payment> permits CardPayment, Repository`
  is excluded with `{"rule": "generated repository", "matched": "kind is interface,
  extends or implements Repository"}` — the rule is named, the fact it names is one the
  source contradicts, which is criterion 7's promise inside out. No sealed type in this
  repo today, so it is latent.
- **`scoring.load("")` silently falls back to the built-in file.**
  `path = path or DEFAULT_CONFIGURATION` treats an empty string as "not given":
  `python3 -m scripts.module_depth_map --scoring ""` logs `run started ... scoring=` and
  exits 0 with a fully scored page. `ConfigurationRefused`'s own docstring calls this
  "the worst of both: the output would look like a score somebody chose, while the file
  they chose it in was being ignored". Test `path is None`.
- **`write_together`'s docstring contradicts itself.** First line: "Write every one of
  these (path, bytes) where they belong, or write none of them." Body, six lines later:
  "'both or neither' cannot be promised outright and is not promised here." The README got
  this right; the summary line is stale.
- **No `interface read` line for the 36 modules that are never scored.** The DEBUG line in
  `scoring.interface_of` sits inside `if scored:`, so at `--log-level DEBUG` every record,
  enum, repository and the entry point leaves a `module excluded from scoring` line and
  nothing about the interface that was nevertheless read and put in the graph. The branch's
  own claim is that an excluded module is "drawn with its interface visible, so a reader
  can see what the rule declined to measure", and the page shows only the rule name — DEBUG
  is where a reviewer would go to check the rule declined something real rather than
  something the parser lost. Fault 1 above lives in exactly that blind spot.
- **`@ interface` written as two tokens is read as kind `interface`.** Legal per JLS 9.6.
  `javasource.parse("public @ interface A { String value(); }")` gives `kind == "interface"`,
  silently, so a rule written `{"kind": ["annotation"]}` cannot fire on it and the page
  prints INTERFACE. Nobody writes it; listed because it is the same silent-misread family.
- **`typesEveryCallerAlreadyKnows: []` is refused while `exclusions: []` is accepted.**
  `_strings` rejects an empty list, so "charge a caller for every type" — an arguable
  position, not a misspelling and not a rule that can never fire — is the one edit the
  configuration file will not take.
- **`_exclusions` reintroduces the fallthrough hazard `CONDITIONS` was made a dict to
  avoid.** Anything not in `BY_SIMPLE_NAME` is validated by an `else` as a list of Java
  kinds, so a fifth condition added with its own reader but no `BY_SIMPLE_NAME` entry
  refuses every legal value with a message about kinds. The comment at lines 98-101 spells
  out this exact trap for the table above it. A per-condition validator in `CONDITIONS`
  makes it impossible.
- **Dead branch.** "a type declaration whose body never closes" in `_declared_types` cannot
  be reached: `parse` has already refused any file whose braces do not balance, and
  `_body_starts_at` has already confirmed the next brace opens at `depth + 1`. It is listed
  in the module docstring as a real failure mode.
- **Nested types' members are never counted and the page does not say so.** A public
  `static class Row` inside a module has methods reachable from outside it; they cost
  nothing and are not in the "what a bar leaves out" list, unlike inherited methods. Zero
  effect on this repository today (the only nested types on scored modules are three
  constant-only `Kind` enums and one `private record AJob`), so this is a wording gap
  rather than a wrong number.

### The push-back on criterion 1 and inherited methods: the admission stands

Attempt 4 asked for this to be judged, so: **the page's admission satisfies criterion 1,
and I would not change it.** I read every supertype named in the backend source and
counted the ones that are modules this graph holds: **zero**. The five supertypes that
exist — `Clock`, `RuntimeException` (JDK), `CommandLineRunner`, `SmartInitializingSingleton`,
`JpaRepository` (Spring) — are all outside the source tree the tool is given, and the spec
puts "dependencies" out of scope and fixes parsing at "targeted pattern matching over the
source, not a full language grammar". The spec's own remedy for a part of an interface the
tool cannot measure is that the page says so (user story 36, and the explicit precedent of
prose invariants: "the tool cannot measure them, and the page says so rather than implying
the score is complete"). This is that, applied to the same kind of thing. There is no case
in this repository where the tool holds the file and declines to read it.

### What I confirmed working - do not redo this

- **All nine fixes bite.** I mutated each one back, one at a time, and reran the 190-test
  suite: method-named-`record` read as a nested type (1 failure), `int f()[]` losing its
  array (1), `record Sale(int... cents)` losing its array (1), `int xs[]` dropped (2),
  annotation-argument braces left in the masked text (4), a qualified name accepted in the
  config (5), enum constants read as members (2), the `isdir` pre-check removed (1), the
  `member not read as a method` DEBUG line removed (1). Tree restored clean after each.
- **Criterion 8, by hand, in the real file.** `typeToLearn` 2 -> 7: `widestInterface`
  31 -> 68, the widest module itself changed (`SavingsAccountController` ->
  `RefusalsAsHttp`), 18 module costs moved, the weight is inlined in the page. Then a rule
  of my own invention (`"reviewer 4 says so"`, `nameEndsWith: ["Service"]`): 6 modules
  newly excluded naming my rule with `matched: "name ends with Service"`, `modulesScored`
  35 -> 29, my wording verbatim in the graph and on the page, each excluded module still
  drawn with its methods visible. `git diff --stat` showed only `scoring.json`. Config
  restored.
- **Loud refusals, 16 of them.** Qualified `annotatedWith` / `extendsOrImplements` /
  `nameEndsWith` / `typesEveryCallerAlreadyKnows`, unknown kind, empty `when`, unknown
  condition, duplicate rule name, blank rule name, non-integer weight, negative weight,
  misspelled weight, unknown visibility, wrong schema, invalid JSON, missing file: every
  one exits 4 with a WARNING naming the reason, leaves a stale graph and page untouched,
  and leaves no `.writing` file.
- **Criterion 3, driven.** Playwright over `docs/module-depth-map.html`, light and dark,
  1024 and 1280: 71 cards, 35 bars, 36 never-scored labels, 0 cards with both or neither,
  one shared track (235.41 px at 1280, 209.41 at 1024), worst deviation from
  `cost x track / widest` 0.0000 px at 1280 and 0.0151 px at 1024,
  `SavingsAccountController` (cost 31 = widest) fills 100%, no sideways scroll,
  **0 console messages, 0 page errors, 1 network request**. Screenshots read, both themes
  render styled and legible. `AccountsService` reads "30 to learn: 11 methods, 13
  parameters, 3 types" — the specimen the spec predicted.
- **Determinism.** 3 runs under differing `TZ`/`PYTHONHASHSEED`/`LC_ALL` are byte-identical
  to each other and to the committed `docs/`; 0 occurrences of `/Users/` or a date in
  either; no `src=`/`href=` in the page; imports are stdlib only.
- **Parser probes that came back clean.** ~25 shapes of legal Java beyond the two faults
  above: `@interface` with array members and `default` values, a record's compact
  constructor, anonymous classes and array initialisers in fields, static/instance
  initialisers, lambdas and switch expressions in bodies, a static nested class three deep,
  a local class in a method body, `<T extends Comparable<? super T>>`, `List<String>[]`,
  `int[][]`, `Receipt[]`, generic varargs, `@SafeVarargs`, a `throws` clause,
  interface `private`/`static`/`default` methods, enum constants with bodies followed by
  more constants, a text block holding `class` and an escaped `\"""`, annotated parameters
  whose annotation argument holds braces, `int[] f()[]`, and the record/annotation shapes
  from attempt 2's defects 1, 2 and 4. Loud failures behave: an unclosed block comment and
  an extra closing brace are both named with a line and reported in `source.unparsed`.
- **Checks.** `cd backend && ./mvnw test` exit 0, 113 tests, 0 failures.
  `cd frontend && npm run typecheck` clean (node v24.16.0).
  `python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests` 190 OK.
- **The application, untouched by this branch** (`git diff --name-only
  ticket/01..ticket/02 -- backend frontend` is empty), driven anyway against the running
  instance: deposit 12.34 -> 201 (`INFO i.d.s.deposits.DepositsService : deposit accepted
  depositId=1 savingsAccountId=1 fromCurrentAccountId=1 amount=12.34 pointsEarned=12`),
  withdrawal 5.00 -> 201 with an allocation (`INFO i.d.s.deposits.WithdrawalsService :
  withdrawal accepted withdrawalId=1 ...`), claim -> 201 (`INFO
  i.d.savingstreak.rewards.RewardsService : claim issued redemptionId=1 ... pointsSpent=10`),
  and four refusals: deposit 0 -> 400, savings account 9999 -> 404, over-priced claim ->
  400, unknown reward -> 400. No WARN, no ERROR, no stack trace anywhere in
  `logs/02-what-a-caller-must-learn.app.4.backend.log`. The Vite page at :5173 loads with
  no page errors.

### Not this ticket, noted so it is not lost

- **`record` as a local variable name breaks `_declared_types`** — but `_TYPE`,
  `_declared_types` and `_RESERVED_DECLARATION` are byte-identical to ticket/01, so this
  is 01's, not 02's. `Object record = o; if (record instanceof String s) { ... }` gives the
  module a phantom nested type called `instanceof`; without the block —
  `return record instanceof String;` — the whole file fails with "a type declaration whose
  body this parser cannot find: instanceof on line 6", which also falsifies the README's
  "legal Java is never failed".
- **Refusals still leave no WARN from `io.dataroots.savingstreak`.** All four I triggered
  produced only Spring's DEBUG `ExceptionHandlerExceptionResolver` lines, whereas
  `WithdrawalsService` and `SavingsAccountController` do `log.warn(... reason={})`.
  Pre-existing, third review round to report it, and a gap against CLAUDE.md's "WARN on
  every refusal with its reason".

### Artifacts

`logs/02-what-a-caller-must-learn.review.4.browser.log`,
`.light.1024.png` / `.light.1280.png` / `.dark.1024.png` / `.dark.1280.png`,
`.bars.web.png` / `.bars.deposits.png`, `.app.png`, `.app.browser.log`.

## Review feedback - attempt 5

All three faults attempt 5 set out to close **are closed**, and so are all nine of the
smaller items. I checked each one by hand and mutation-checked the three big ones; the
list is under "Confirmed working" below and should not be redone. This is not a fifth
round of the same finding.

What sends it back is two things the page says that the source does not support. Both are
live on the committed page today, both are in the same family the last three rounds have
been about — a number or a label that is wrong with nothing said — and one of them is
wording this attempt newly put under all 35 bars.

### 1. `void` is counted as a type crossing the seam

`scoring.py` `_types_crossing_the_seam` folds `method.returns` in unconditionally, so a
method that hands nothing back still contributes a "type crossing the seam". `void` is
not a type in Java and nothing crosses a seam when a method returns it.

Live on the committed page: 9 modules carry `{"name": "void", "mustBeLearned": false}` in
`typesCrossingTheSeam`, and `AccountsService`'s card reads **"7 types every caller already
knows"** — one of those seven is `void`.

It only stays harmless because the committed weight is 0, which means it breaks the moment
a reader does the one thing `scoring.json` exists to let them do. Both of these are
supported edits — the second is one the README explicitly blesses ("charging a caller for
every type they meet is a position somebody can hold, not a misspelling"):

    $ printf 'package shop;\npublic class A { public void f() {} }\n' > src/shop/A.java

    # typeEveryCallerAlreadyKnows: 1
    graph: cost 2      page: "2 to learn: 1 method, 0 parameters,
                              0 types this application invented, 1 type every caller already knows"

    # typesEveryCallerAlreadyKnows: []
    graph: cost 3      page: "3 to learn: 1 method, 0 parameters,
                              1 type this application invented, 0 types every caller already knows"

A caller of `void f()` learns one method and no types. The tool charges them for a type,
and in the second case tells them this application invented it.

Reproduce: put that one class under a scratch source root, copy `scoring.json`, change the
one weight, and run `python3 -m scripts.module_depth_map --source <root> --scoring <copy>
--graph /tmp/g.json --page /tmp/p.html`.

No test is named for this. `test_a_type_variable_the_module_introduces_costs_nothing`
(`test_interface_cost_is_what_a_caller_must_learn.py:331`) happens to assert
`["long", "void"]` as a seam, but it is named for type variables and pins the behaviour by
accident; nothing exercises a non-zero `typeEveryCallerAlreadyKnows` against a `void`
method. Whatever you decide — leave `void` out of the seam, or state on the page that it
is in — a test named for the property has to say so.

This is criterion 1: "every distinct type crossing the seam in a parameter or a return"
is not what is counted.

### 2. Four Spring types are labelled "this application invented", on the committed page

`page.py:187` renders the `typeToLearn` weight as "when it is one this application
invented", and commit `d478309` put the same words under **every scored card** ("3 types
this application invented"). The rule behind `mustBeLearned` is only "not in
`typesEveryCallerAlreadyKnows`", which is not the same claim.

On the committed page, four of the 48 names charged at `typeToLearn` are declared nowhere
in the source the tool read:

    ClockConfiguration  cost 10   charged 2 each for ApplicationListener, ApplicationStartedEvent
    RefusalsAsHttp      cost 28   charged 2 each for ProblemDetail, ResponseEntity

`RefusalsAsHttp` is the second-widest bar on the page, and a reader is told this
application invented two Spring types in it. That is a statement about the source a reader
can check and find wrong — the same shape as criterion 7's promise inside out, and against
the spec's central claim that a score can be argued with.

Reproduce:

    python3 -c "
    import json; d=json.load(open('docs/module-depth-map.json'))
    declared={m['name'] for m in d['modules']}|{n.split('.')[-1] for m in d['modules'] for n in m['nested']}
    print(sorted({t['name'] for m in d['modules'] for t in m['interface']['typesCrossingTheSeam']
                  if t['mustBeLearned']} - declared))"
    ['ApplicationListener', 'ApplicationStartedEvent', 'ProblemDetail', 'ResponseEntity']

Three ways out, all cheap: say what is true ("a type this tool was not told every caller
knows"), or add the four names to `typesEveryCallerAlreadyKnows` and keep the wording, or
decide `typeToLearn` means "declared in the source read" and make the parser answer that.
The prose in `page.py`, the per-card term and `README.md` all carry the wording, so all
three move together. Note the paragraph wording predates this branch's attempt 5
(`678e35c`); the per-card term is new in `d478309`.

Because a reader can only re-weight a score they can trust, and both faults above are
produced by re-weighting or read straight off the page, criterion 8 is unticked too. The
mechanism works — I verified it end to end below — but at `typeEveryCallerAlreadyKnows`
anything other than 0 the output it produces is wrong.

### Also wrong, and worth fixing while you are in here

- **`--graph X --page X` destroys the previous run's file and then names the wrong one.**
  Both outputs stage to `X.writing`; the page's bytes overwrite the graph's, the first
  `os.replace` moves them into `X`, and the second fails `ENOENT`. Reproduced:

      $ echo STALE > /tmp/same/both.json
      $ python3 -m scripts.module_depth_map --source src \
          --graph /tmp/same/both.json --page /tmp/same/both.json
      ERROR ... the run wrote /tmp/same/both.json and then could not write the rest: ...
      $ head -1 /tmp/same/both.json
      <!doctype html>            # the PAGE, reported as the graph having landed

  Exit 5 and an ERROR, so not silent — but misattributed, which is the fault `becfdd7` and
  attempt 5's own `8036c92` were about. The pre-check loop at `cli.py:206` tests `isdir`
  and not whether two destinations are the same path.
- **A never-scored module publishes per-method costs.** `interface_of` emits
  `"cost": self._cost_of(method)` regardless of `scored`, so the committed graph has
  `SavingStreakApplication` with `interface.cost: null` beside `interface.methods[0].cost:
  2`. The docstring says an unscored module's cost is "absent rather than zero, because it
  was never counted, not counted to nothing" — the parts are counted and published. An
  agent reading the graph (spec stories 33-34) can sum a score for a module the graph says
  has none. `test_every_cost_is_a_whole_number_the_page_can_draw_a_bar_from` skips excluded
  modules, so nothing covers it.
- **A `package-info.java` reds the suite.** `test_this_repository_is_read_whole.py:30`
  asserts `on_disk == parsed | reported`; such a file declares no type, so it is in neither
  set. I added
  `backend/src/main/java/io/dataroots/savingstreak/package-info.java` and ran the suite:
  `AssertionError: Items in the first set but not the second:
  'io/dataroots/savingstreak/package-info.java'`. The README names that file as one the
  tool reads rather than reports, so a supported input is treated as a lost one.
  (Regenerating `docs/` does not fix it — the file is in neither set either way.)
- **The graph's `SCHEMA` is still `module-depth-map/1`** although the document gained a
  top-level `scoring` object and `modules[].interface` / `modules[].excludedBy`.
  `page.py:165` reads `document_.scoring.modulesScored` unguarded, so a genuine v1
  document throws inside the one IIFE and renders a blank page rather than an error —
  the failure mode `page.py`'s own comment about branching on `excludedBy` was written to
  avoid. `scoring.load` refuses a configuration on an exact schema mismatch: the tool
  versions its input contract and not its output.
- **`_strings(..., may_be_empty=True)` still refuses a non-list with the wrong reason.**
  `typesEveryCallerAlreadyKnows: "String"` is refused with "it has to be a list with
  something in it", on the one field where emptiness is explicitly allowed.
- **`_only(document, ...)` runs before the schema check** (`scoring.py:340` vs `:341`), so
  a `module-depth-map-scoring/2` file that adds a key is refused with "names X, which this
  tool does not read" instead of being told the tool is too old.
- **`term.weight` in `page.py`'s breakdown is dead at runtime.** `breakdown.map` reads only
  `of`, `one` and `many`; the "one term per weight" invariant is held up entirely by
  `re.findall(r'weight: "(\w+)"', rendered)` over the rendered script text, so a term whose
  `of()` counts the wrong thing still passes. Building the terms from
  `Object.keys(document_.scoring.weights)` would make it structural. (Fault 1 above is
  exactly a term counting the wrong thing and this test not noticing.)
- **`scoring._named_types_in` re-implements how a Java type is spelled** — its own
  `[<>,\[\]\s]+` split and keyword list — while `javasource` already owns `_A_TYPE`,
  `_normalised`, `_without_groups` and `_split_on_commas`. `"final"` in its skip list is
  already dead, because `_modifiers_in` strips modifiers long before a type reaches it.
- **`graph.build`'s `rules = rules or scoring.load()`** is a second home for the `--scoring`
  default and can raise `ConfigurationRefused` where `cli.main` guards only
  `DuplicateModules` around `graph.build`.
- **`write_together(*outputs)` is variadic and unpacked into two names**
  (`written_graph, written_page = ...`), so a third output would end the run with a
  `ValueError` traceback — against the comment six lines above it. Latent.

### Confirmed working - do not redo this

- **Fault 1 (record accessor counted twice) is fixed, and the fix bites.** Fixture
  `record Coin(long cents, List<String> tags)` with both accessors written out, against
  `Plain` without them, `data carrier` rule removed: both read `cost=2 methods=2`
  (`cents -> long`, `tags -> List<String>`), and the DEBUG line
  `record writes its own accessor for a component name=cents line=3` says so. A
  `cents(int scale)` and a `static cents(String, int)` are still counted beside the
  accessor (`Scaled cost=8 methods=3`). Mutating the dedup off reds 4 tests, three of them
  in `ARecordThatWritesAnAccessorOffersOneOfItTest`. The claim about `69a7b49` is true: the
  old test read methods into a dict keyed by name, which collapsed the repeat.
- **Fault 2 (`write_together`) is fixed in both directions.** `--graph <plainfile>/sub/g.json`
  → exit 5, `WARNING ... /…/sub/graph.json could not be written: [Errno 20] Not a
  directory`, no traceback, nothing written. `--page <plainfile>/sub/p.html` with a good
  `--graph` → the WARNING names **page.html**, and the stale `graph.json` still reads
  `STALE`. A directory at either destination is refused before a byte is written; a forced
  partial landing (`chflags uchg page.html`) logs at ERROR naming which file landed, exit
  5; a `.writing` that cannot be opened is still registered and discarded. Mutating the
  path back to `staged[-1][1]` reds 2 tests (1 error, 1 failure).
- **Fault 3 (the breakdown) is fixed.** I rebuilt the page with
  `typeEveryCallerAlreadyKnows: 3` and read it in Chromium: every one of the 30 bars'
  four printed counts matches the document and their weighted sum matches the cost —
  **0 mismatches**, and 0 mismatches on the committed page's 35 bars too.
  `AccountsService` reads "51 to learn: 11 methods, 13 parameters, 3 types this
  application invented, 7 types every caller already knows" = 11+13+6+21. Dropping the
  already-known term reds 2 tests.
- **All nine smaller items are closed.** `sealed interface Payment extends
  Comparable<Payment> permits CardPayment, Repository` → `supertypes ['Comparable']`,
  `excludedBy None` (was excluded as a generated repository). `@ interface A` → kind
  `annotation`. `--scoring ""` → exit 4, "could not be opened: No such file or directory".
  `typesEveryCallerAlreadyKnows: []` → exit 0, widest 31 → 44. `CONDITIONS` carries a
  validator per condition (`BY_SIMPLE_NAME` and the `else` are gone). At DEBUG over the
  backend: 71 `interface read` lines, **36 of them `cost=none, never scored`**, 36
  exclusions, 56 `member not read as a method` — and that 56 splits 43 constructors / 10
  fields / 3 nested records, exactly as the README claims. The "body never closes" branch
  is now a comment. The page and README both name a nested type's members among what a bar
  leaves out.
- **Criterion 8's mechanism, by hand, twice.** `typeEveryCallerAlreadyKnows` 0 → 3 moved
  `widestInterface` 31 → 51 and the page's own prose from "counts 0" to "counts 3". A rule
  of my own invention (`"reviewer 5 says so"`, `kind: [class]` + `nameEndsWith:
  [Controller]`) excluded 5 modules with `matched: "kind is class, name ends with
  Controller"`, moved `modulesScored` 35 → 30, changed the widest module from
  `SavingsAccountController` to `AccountsService`, and put my sentence verbatim on the page
  and in the graph. `git status` clean throughout — I edited a copy, never the analyser.
- **20 malformed configurations, every one a loud refusal.** Qualified `annotatedWith` and
  qualified `typesEveryCallerAlreadyKnows`, unknown kind, empty `when`, unknown condition,
  duplicate rule name, blank rule name, missing `because`, non-integer / boolean / negative
  / misspelled weight, unknown visibility, empty `nameEndsWith`, empty `kind`, wrong schema,
  unknown top-level key, invalid JSON, missing file, `--scoring ""`: all exit 4 with a
  WARNING naming the reason, and the stale graph and page are byte-unchanged with no
  `.writing` left.
- **Criteria 2, 3, 5, 6, 7, 9.** `Domain.value() -> Receipt` costs 3 against
  `Primitive.value() -> long` at 1. Playwright over the committed page and my re-weighted
  one, light and dark, 1024 and 1280 (8 loads): 71 cards, 35 bars, 36 never-scored, 0 cards
  with both or neither, one shared track (235.41 px at 1280, 209.41 at 1024), worst bar
  deviation from `cost x track / widest` 0.0000 px at 1280 and 0.0151 at 1024,
  `SavingsAccountController` (31 = widest) fills 100 %, no sideways scroll, **0 console
  messages, 0 page errors, 0 failed requests**. Screenshots read: both themes render styled
  and legible. Every one of the 71 modules is scored xor excluded, every exclusion names a
  rule the file holds and the fact that matched. Fixture trees carry every scoring rule;
  only the determinism and `thisrepository` suites touch the application's source.
- **Parser probes: ~90 shapes of legal Java, no new misread found.** Records (accessor
  overridden, compact and canonical constructors, generic components, varargs components
  with and without a written accessor, a static factory sharing a component's name, an
  annotated component whose argument holds braces, nested and generic records),
  `sealed`/`non-sealed`/`permits` over several lines and with qualified names, annotation
  types (`@interface`, `@ interface`, `@` on its own line, array members with `default`,
  nested ones), interface `private`/`static`/`default` methods, enum constants with bodies
  followed by more constants and with array arguments, fields initialised with lambdas /
  anonymous classes / nested array initialisers, static and instance initialisers, switch
  expressions and pattern matching, `<T extends Comparable<T> & Runnable>`,
  `List<String>[]`, `int[][]`, `int f()[]`, `int xs[]`, receiver parameters, `throws`,
  class literals, text blocks holding `class` and an escaped `\"""`, `extends Map<String,
  Long>`, `@JsonSubTypes({@Type(A.class)})` (reports `JsonSubTypes` only). I also ran the
  tool over `backend/src/test/java` — 31 files it has never seen — 31 parsed, 0 unparsed,
  and 0 methods with a keyword for a name or an unspellable type. Unreadable files are all
  named with a line and a reason and land in `source.unparsed`.
- **Determinism and the committed outputs.** Three runs under differing
  `TZ`/`PYTHONHASHSEED`/`LC_ALL` are byte-identical to each other and to committed
  `docs/module-depth-map.json` and `.html`; 0 occurrences of `/Users/`, no `src=`/`href=`
  in the page, stdlib imports only.
- **Checks.** `cd backend && ./mvnw test` exit 0, 113 tests, 0 failures.
  `cd frontend && npm run typecheck` clean (node v24.16.0).
  `python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests` 205 OK.

### Not this ticket

- **The application is untouched by this branch.** `git diff --name-only
  ticket/01-modules-on-a-page..HEAD -- backend frontend` is empty; I checked rather than
  took the claim. Driven anyway against the running instance: deposit 12.34 → 201 (`INFO
  i.d.s.deposits.DepositsService : deposit accepted depositId=1 savingsAccountId=1
  fromCurrentAccountId=1 amount=12.34 pointsEarned=12`), withdrawal 5.00 → 201 with an
  allocation, claim → 201 (`INFO i.d.savingstreak.rewards.RewardsService : claim issued
  redemptionId=1 ... pointsSpent=10`), and four refusals: deposit 0 → 400, savings account
  9999 → 404, over-priced claim → 400, unknown reward → 400. No ERROR and no stack trace
  anywhere in `logs/02-what-a-caller-must-learn.app.5.backend.log`. The Vite page at :5173
  renders with 0 page errors.
- **Refusals still leave no WARN from `io.dataroots.savingstreak`** for the deposit, claim
  and unknown-reward paths — only Spring's DEBUG `ExceptionHandlerExceptionResolver` lines
  — whereas `WithdrawalsService` does log `withdrawal rejected ... reason=`. Pre-existing,
  fourth review round to report it, a gap against CLAUDE.md's "WARN on every refusal with
  its reason", and nothing this branch touches.
- **`record` as a local variable name** still gives a phantom nested type called
  `instanceof` (`Object record = o; if (record instanceof String s) {}`). `_TYPE`'s
  `record` alternative is unchanged from ticket/01 in substance, so this is 01's.

### Artifacts

`logs/02-what-a-caller-must-learn.review.5.browser.log`,
`.module-depth-map.{light,dark}.{1024,1280}.png`, `.mine.{light,dark}.{1024,1280}.png`
(the re-weighted page with my own rule), `.app.png`, `.app.browser.log`.
