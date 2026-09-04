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
- [x] Changing a weight or an exclusion in the configuration file changes the output without any edit to the analyser
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
