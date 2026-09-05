# 05: Refusals as their own band

**What to build:** Every refusal a module can answer with is shown as part of what a caller must know
— because it is — but carried as its own band on the module rather than folded invisibly into one
number. A module whose interface is wide because it is honest about how it can fail should be
distinguishable from one that is merely wide, and a reader should be able to see a module's failure
modes before deciding to call it.

The tool also checks the documented refusals against the ones the implementation actually raises, and
reports a finding wherever the two disagree. A stale comment about how something fails is then caught
by a machine rather than by whoever called it.

**Blocked by:** 02 (What a caller must learn).

**Status:** needs-info

- [x] Every refusal a module can answer with appears in the graph as part of its interface
- [x] Refusals count toward interface cost, and are also reported separately from the rest of it
- [x] The page shows refusals as their own band, so an honestly-wide interface is distinguishable from a merely wide one
- [ ] Documented refusals are read from the source's own documentation rather than guessed at
- [x] A module documenting a refusal it cannot raise produces a finding naming both sides of the disagreement
- [x] A module raising a refusal it does not document produces a finding naming both sides of the disagreement
- [ ] A module whose documentation and implementation agree produces no finding
- [x] Fixture modules establish each direction of disagreement and the agreeing case

## Review feedback - attempt 1

Most of this ticket is in good shape and I want to say so before the problems, because
almost nothing here needs redoing. What is wrong is two readings of a refusal that fire
on a module whose two sides agree, and it is the one thing this feature cannot afford:
the whole argument for it is that a machine catches a stale comment, and a machine that
accuses a module that kept its word stops being read.

What I checked and found good, so you do not re-do it:

- `python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests` — 426 tests, OK.
- `.scratch/module-depth-map/lab.sh checks` — backend 113 tests, BUILD SUCCESS; frontend
  typecheck clean.
- Two fresh runs into separate directories: graph and page byte-identical to each other
  **and** to the committed `docs/module-depth-map.{json,html}`.
- The band renders. Playwright over `docs/module-depth-map.html` at light+dark x 1024+1280,
  71 cards each, `scrollWidth - clientWidth == 0` at every width, and **zero** console
  messages, page errors or failed requests. I cross-checked every card against the graph
  — band classes, both band widths against `widestInterface`, refusal names and order, the
  `found` class exactly where a finding exists, one finding line per finding naming both
  sides — 0 problems at all four combinations. Screenshots read, not just taken.
- The seven `@throws` this repository actually writes are all read, attributed to the right
  method, and the six findings on this source are all genuine. I triggered two of them
  against the running application: `POST /api/savings-accounts/1/withdrawals` with an empty
  account returned HTTP 400 and logged
  `WARN i.d.s.deposits.WithdrawalsService : withdrawal rejected savingsAccountId=1 ... reason=There is not enough in that savings account to move EUR 5.00.`
  — so `WithdrawalRefused` really does reach a caller who has nothing on the seam telling
  them to catch it. The finding is true.
- The rule is in the file, not the analyser: repricing `refusal` to 7 moved `ScheduledJobs`
  to cost 29 = 8 + 21, and rewording `raisedNeverDocumented.finding` rewrote all six
  findings and the page. A configuration with no `refusals` section exits 4 and writes
  nothing, with `WARNING ... refusals is missing, and it has to be an object`.
- The logging is there and greppable: `refusals checked read=13 modulesWithFindings=6 findings=6`
  at INFO, one INFO line per finding with both sides, `refusals read module=... refusals=... findings=N`
  at DEBUG for all 71 modules, the rule once at DEBUG. 0 WARNING and 0 ERROR on a clean run.
- I mutated three things on a copy — dropping `declared.raises` from the union, dropping the
  intervening-text check in `_documented_before`, and dropping refusals out of `cost` — and
  the suite caught all three (23, 16 and 19 failures).

### 1. A module whose two sides agree gets two false findings, and a `String` priced as a refusal

`_raised_in` in `scripts/module_depth_map/javasource.py` resolves `throw f(...)` by keying
`returns` on the method **name alone**, ignoring its parameters, so two helpers spelled the
same collide and whichever was declared last wins. Reproduce with a four-file tree
(`Shut` is the usual `extends RuntimeException`):

    public class Overloaded {
        /**
         * Lets one through.
         *
         * @throws Shut if it is shut
         */
        public void go(long id) {
            if (id < 0) { throw refusing("no"); }
        }

        private Shut refusing(String why) { return new Shut(why); }

        private String refusing(int code) { return "code " + code; }
    }

    python3 -c 'import sys; sys.path.insert(0,"scripts"); from module_depth_map import graph, scoring; d=graph.build([graph.java_root("<tree>")], scoring.load()); print([ (m["name"], m["interface"]["refusals"], m["findings"]) for m in d["modules"] ])'

Expected: one refusal, `Shut`, documented and raised, no finding. What I got:

    refusal {'name': 'Shut', 'documented': True, 'documentedBy': ['go'], 'raised': False}
    refusal {'name': 'String', 'documented': False, 'documentedBy': [], 'raised': True}
    FINDING documented but never raised | Shut | documentedBy ['go'] | raised False
    FINDING raised but never documented | String | documentedBy [] | raised True

Three things wrong at once. `String` is not a refusal and is not even a `Throwable`, yet it
is on the interface and priced at 2 (`cost` 6, `refusalCost` 4). The refusal the module
genuinely raises is reported as never raised. And a module that kept its word carries a
finding in each direction — which is the criterion "a module whose documentation and
implementation agree produces no finding", failing.

This also breaks the invariant `_raised_in`'s own docstring states: "what is counted here
is a floor on what a module raises, in the same direction every other reading in this file
errs in". This is not a floor. It invents a refusal that does not exist and erases one that
does. `ClockService` and `ScheduledJobs` both write `throw refusing(why)` today, so the
shape that collides is one line away from the two seams this reading exists to serve.

The parser already records `method.parameters`, so the fix is available: key on the name
*and* the parameter list, and where a name is ambiguous read nothing rather than guessing —
which is the direction the rest of the file leans.

### 2. A refusal documented on a constructor is read on one side and not the other

`_method_in` deliberately returns `None` for constructors, so a constructor's `@throws` is
never read as documentation. `_raised_in` reads over the whole body, so a `throw new X(...)`
in that same constructor **is** read into `raises`. The two sides of one refusal are
therefore read asymmetrically, and the most common Java validation idiom there is gets
accused. Reproduce:

    public class Built {
        private final long cents;

        /**
         * Builds one.
         *
         * @throws IllegalArgumentException if the amount is negative
         */
        public Built(long cents) {
            if (cents < 0) { throw new IllegalArgumentException("negative"); }
            this.cents = cents;
        }

        public long cents() { return cents; }
    }

Expected: documentation and implementation agree, no finding. What I got:

    refusal {'name': 'IllegalArgumentException', 'documented': False, 'documentedBy': [], 'raised': True}
    FINDING raised but never documented | IllegalArgumentException | documentedBy [] | raised True

`test_a_module_no_rule_scores_still_has_its_refusals_read` currently asserts exactly this
shape for a record's compact constructor (`{"Locked": (False, True)}`), so the wrong
behaviour is pinned by a test rather than caught by one.

Either side is a defensible answer, but the two have to match. Read a constructor's
`@throws` as the module documenting itself, or stop reading a constructor's `throw` into
`raises` — and say in `scoring.py` and the README which of the two you chose and why.
Whichever you pick, add a fixture for it.

### 3. An interface or abstract method is always "documented but never raised"

`_refusals_of` compares documented refusals against `declared.raises`, which comes from
method *bodies*. An interface method has no body, so every `@throws` on one becomes a
finding. Reproduce:

    public interface DepositsPort {
        /**
         * Moves money.
         *
         * @throws Shut if it will not move
         */
        void deposit(long id);
    }

    FINDING documented but never raised | Shut | documentedBy ['deposit'] | raised False

Nothing in this repository triggers it today — no interface here writes `@throws` — so this
is not why the ticket is going back. But it is the shape a participant is most likely to add
next, and this is a page a training day projects. It may well be the answer you want (the
page's caveat about "a spelling this tool cannot follow" half covers it), in which case say
so where a reader will find it: name it in the page's "what this band leaves out" paragraph
and in the README section, and pin it with a fixture. As it stands neither
`tests/refusalsastheirownband` nor the prose mentions an interface at all.

### 4. A comment in `graph.py` contradicts the committed output and the README

`scripts/module_depth_map/graph.py:561`, justifying `findingsByKind`, says: "one module can
be in both disagreements at once — this repository's `ScheduledJobs` was, on two different
refusals". The committed `docs/module-depth-map.json` has `documentedNeverRaised` at 0
findings and `ScheduledJobs` carrying exactly one, and the README you wrote in this same
branch says the other direction "is not demonstrated on this source at all". The claim went
stale at f020730. The reason for three denominators is still sound — write it as the rule it
is rather than as an observation about this week's source, or the next reader checks it
against the graph and finds it false. In a ticket about a machine catching stale prose this
one is worth fixing.

### What I left ticked

Everything except "every refusal a module can answer with appears in the graph as part of
its interface" (broken by the bogus `String` and the lost `Shut` in 1) and "a module whose
documentation and implementation agree produces no finding" (broken by 1 and 2). The band,
its pricing, its separate reporting, the page, the config-driven rule, the fixtures for the
three directions and the logging are all real and all verified — they do not need doing
again.

## Review feedback - attempt 2

The three defects attempt 1 sent this back for are genuinely fixed, and I checked all three
myself rather than taking the log's word. Almost none of this needs redoing, and I have
listed what I verified below so you do not repeat it. What sends it back is one line: two
spellings of the same Java statement give different answers, and the difference silently
switches off the *documented but never raised* check for a whole module.

### 1. `throw this.refusing(why)` is not read, and one of them disables a whole module's check

`_raised_in` is handed the raw body:

    scripts/module_depth_map/javasource.py:1224
        raises, throws_not_read = _raised_in(body, methods)
        plain = _THROUGH_THIS.sub("", body)

`_THROUGH_THIS` (line 133) exists precisely to normalise `this.`, and it is applied on the
very next line — but to `plain`, which `_raised_in` never sees. So:

    python3 -c 'import sys; sys.path.insert(0,"scripts")
    from module_depth_map import javasource
    for spelling in ("throw refusing(\"no\");", "throw this.refusing(\"no\");"):
        src = "package lab;\npublic class X {\n    public void go() { %s }\n    private Shut refusing(String why) { return new Shut(why); }\n}\n" % spelling
        t = javasource.parse(src, "X.java").types[0]
        print(spelling, "->", t.raises, t.throws_not_read)'

    throw refusing("no");        -> ('Shut',) 0
    throw this.refusing("no");   -> ()        1

Two spellings of one statement, two different answers. The first is read; the second is
counted as a throw only javac could resolve.

The consequence is not local, and it is why this is worth a round trip. `scoring.py`
`_refusals_of` computes `checked = raised or (not declared.throws_not_read and ...)`, and
`throws_not_read` is module-wide. So a *single* `this.`-qualified throw anywhere in a module
turns off the *documented but never raised* check for every refusal in it. Reproduce with a
module whose stale `@throws` has nothing to do with the qualified throw:

    public class Hidden {
        /**
         * Stale: nothing here raises Shut.
         *
         * @throws Shut if shut
         */
        public void stale() { }

        public void other() { throw this.boom(); }

        private IllegalStateException boom() { return new IllegalStateException("x"); }
    }

Expected: a *documented but never raised* finding on `Shut`, naming both sides. What I got:

    Hidden  refusals=[('Shut', True, ['stale'], False, False)] findings=[]

No finding. The card draws `Shut` underlined as a promise the tool could not check, and the
DEBUG line says `1 throw(s) in this body name a type only javac could resolve`. That reason
is untrue here: the tool can name this throw, it just did not normalise `this.` first.

I want to be precise about why this is a defect rather than the declared floor. The floor
you wrote is sound and I am not asking you to remove it — a throw the tool genuinely cannot
name may be raising exactly what was promised, so withholding the finding is right. But
`throw this.refusing(why)` is not such a throw. It is the readable spelling, wearing a
prefix the file already strips one line later, and it drags the floor down over the whole
module with it. `ClockService` and `ScheduledJobs` both write `throw refusing(why)` today;
`this.` is one keystroke away, and the direction it suppresses — *documented but never
raised* — is the one with zero live examples on this source, so nothing else would catch
the regression.

Nothing in this repository writes `throw this.` today (`grep -rn "throw this\."
backend/src/main/java` gives 0), so the committed `docs/module-depth-map.{json,html}` are
not wrong. This is latent, not live.

The fix looks like one line — hand `_raised_in` the `this.`-stripped text — but read the
comment directly under it before you do: it explains why `called` must be read over the
body *as written* and not over `plain`, so the two readings genuinely want different inputs
and the order they are computed in matters. Whichever way you take it, pin it with a fixture
that writes both spellings in one tree and asserts they agree, and say in `_raised_in`'s
docstring that a call through `this` is the same call.

### 2. An enum's constructor is read as reachable when Java says it is private

`_visibility` (`scripts/module_depth_map/javasource.py:1772`) falls back to
`package-private` for anything that is not an `interface` or `annotation` member. An enum
constructor written without an access modifier is implicitly **private** (JLS 8.8.3), and
`package-private` is in `reachableFromOutside` in the committed `scoring.json`. That was
harmless while `_visibility` only classified methods; this branch makes constructors
documenters, so it now matters. Reproduce:

    public enum Colour {
        RED("r"), BLUE("b");

        private final String code;

        /**
         * Builds one.
         *
         * @throws IllegalStateException never - no caller can reach this
         */
        Colour(String code) { this.code = code; }

        public String code() { return code; }
    }

Expected: nothing. No caller can reach that constructor, so what it promises is a note to
whoever maintains the enum — which is exactly the rule `_documenters_of` already states
("A private constructor is a factory's business and nobody else's"). What I got:

    Colour  refusals=[('IllegalStateException', True, ['Colour'], False, True)]
            findings=[('documented but never raised', 'IllegalStateException')]

The refusal is on the module's interface and carries a finding about a promise made to
nobody. This one is smaller than (1) — no enum in this repository documents a constructor,
so nothing changes on the committed page — but it is the same shape of wrong that attempt 1
was sent back for, and the rule you want is already written down two files away. Teach
`_visibility` that an enum's constructor with no access modifier is private, and pin it.

### 3. Worth a decision, not a fix: refusals are priced flat

`scoring.json`'s own words say a refusal "is priced like a type crossing the seam, because
that is what learning one amounts to". A type crossing the seam is run through
`typesEveryCallerAlreadyKnows` and costs 0 when it is on that list; a refusal is charged
`weights.refusal` flat, whatever it is. So `RuntimeException` returned from a method costs
a caller 0 while `IllegalStateException` thrown by one costs 2. Both may be the answer you
want — the list is about types a caller already knows, and knowing `IllegalStateException`
exists is not the same as knowing this module can answer with it — but the file currently
argues for one rule and the analyser applies another. Either reword the `because` so it
does not invoke the seam-type rule, or run refusals through the same list. Not a blocker;
say which you chose.

### What I checked and found good, so you do not re-do it

- **All three attempt-1 defects, reproduced on my own fresh trees, all fixed.**
  `Overloaded` gives one refusal `Shut`, `checked: False`, **no finding either way**, and no
  `String` on the interface. `Built` and a record's compact `Compact` both give
  `documented: True, documentedBy: ['Built'/'Compact'], raised: True, checked: True`, no
  finding. `DepositsPort` (interface method, no body) gives `checked: False`, no finding,
  reason logged. Both directions still fire on real disagreements (`Stale`, `Silent`). The
  stale `graph.py` comment is rewritten as a rule.
- **The band, on the real source.** 13 refusals across 71 modules — exactly the 7 `@throws`
  this repository writes plus the 6 types thrown but undocumented, checked one by one
  against `grep -rn "@throws\|throw " backend/src/main/java`. `cost == costWithoutRefusals +
  refusalCost` on every module, and `costWithoutRefusals` is identical to the parent
  branch's `cost` for all 71 — the band is purely additive and shifted nothing else.
- **All six findings on this source are true.** Triggered against the running application:
  `POST /api/savings-accounts/1/withdrawals {"amount":"5.00","toCurrentAccountId":1}` on an
  empty account returned HTTP 400 and logged `WARN i.d.s.deposits.WithdrawalsService :
  withdrawal rejected savingsAccountId=1 ... reason=There is not enough in that savings
  account to move EUR 5.00.` then `Resolved [...WithdrawalRefused: ...]`. The kept-promise
  side too: `POST /api/dev/clock/advance {"days":0}` resolved `ClockRefused`, which
  `ClockService.advanceBy` documents, and the tool rightly reports no finding.
- **The page.** Playwright at light+dark x 1024+1280: 71 cards each, `scrollWidth -
  clientWidth == 0` at every width, **zero** console messages, page errors or failed
  requests across nine loads. I cross-checked every card against the graph at all four
  combinations — band classes, both band widths against `widestInterface`, bar and band
  titles, refusal names and order, the `found` class exactly where a finding exists, the
  `unchecked` class exactly where `checked` is false, one finding line per finding naming
  both sides — **0 problems**. Screenshots read: `SavingsAccountController` (33 = 31 + 2, a
  full blue bar with a gold sliver) reads as merely wide beside `ScheduledJobs` (14 = 8 + 6)
  as honestly wide, in both themes. The unchecked refusal renders `underline dotted` in band
  ink, distinct from alarm ink, in both themes.
- **Fourteen further parser probes, none misfired:** `@throws` mid-sentence or inside
  `{@link}` is prose; a field's javadoc does not document the method under it; a class-level
  `@throws` does not document its first method; a `throws` clause is not documentation;
  `@exception` is read; a one-line `/** @throws Shut ... */` is read; annotations between
  javadoc and method do not break the link; `String[] value() default {"a"}` parses and
  promises nothing; an interface `default` method with a body **is** checked and agrees; a
  generic `<T extends RuntimeException> T refusing(...)` is declined rather than guessed;
  an enum with constants reads its documented method; a nested type's `throw` counts as the
  module's; a refusal documented on both a bodiless signature and a method with a body is
  checked on the body.
- **Checks.** `python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests` —
  447 tests, OK. `.scratch/module-depth-map/lab.sh checks` — backend 113 tests, BUILD
  SUCCESS; frontend typecheck clean. Two fresh runs into separate directories: graph and
  page byte-identical to each other **and** to the committed
  `docs/module-depth-map.{json,html}` (4 silent `cmp`s).
- **Five mutations, all caught**, each reverted after: `checked` always true (7 failures),
  constructors dropped from the documenters (5), every member counted as having a body (5),
  refusals dropped out of `cost` (7), last-declaration-wins for a throw-through (3). The
  pre-existing suites were strengthened rather than loosened.
- **Logging.** Full `--log-level DEBUG` run: 581 lines, **0 WARNING, 0 ERROR**. `refusals
  checked read=13 notChecked=0 modulesWithFindings=6 findings=6` at INFO, one INFO line per
  finding with both sides, `refusals read module=... notChecked=N` at DEBUG for all 71
  modules. Exactly 3 `throw not read as a refusal` lines, and they are the three the source
  writes: `throw runtime;` and `throw error;` in `ScheduledJobs`, `throw notAnAmountOfMoney;`
  in `SavingsAccountController`. No `print()` anywhere in the tool.

### What I left ticked

Everything except "a module documenting a refusal it cannot raise produces a finding naming
both sides of the disagreement", which point 1 breaks whenever the module also writes a
`this.`-qualified throw. The band, its pricing, its separate reporting, the page, the
config-driven rule, the constructor reading, the bodiless-signature reading, the fixtures
and the logging are all real and all verified — none of them needs doing again.

## Review feedback - attempt 3

All three things attempt 2 sent this back for are genuinely fixed, and I reproduced each of
them on my own trees rather than taking the log's word. What sends it back is one more
instance of the shape attempt 1 sent it back for: a module whose two sides agree gets a
finding in each direction, and something that is not a refusal is priced onto its band.
The fix is small and the intent is already written in the code's own docstring.

### 1. A class-level type variable reaches the band, and accuses a module that kept its word

`_thrown_through` (`scripts/module_depth_map/javasource.py:1344`) rejects a candidate whose
*own* declaration is generic:

    if not candidates or any(method.type_parameters for method in candidates):
        return None

`method.type_parameters` is the method's own list. A type variable declared on the
**enclosing type** is not in it, so `private T make()` inside `class Box<T extends
RuntimeException>` looks like an ordinary method returning `T`, and `T` is read as a
refusal. Reproduce with a one-file tree:

    public class Box<T extends RuntimeException> {
        /**
         * Does it.
         *
         * @throws RuntimeException if it will not
         */
        public void go() { throw make(); }

        private T make() { return null; }
    }

    python3 -c 'import sys; sys.path.insert(0,"scripts"); from module_depth_map import graph, scoring; d=graph.build([graph.java_root("<tree>")], scoring.load()); [print(m["name"], (m["interface"] or {}).get("cost"), (m["interface"] or {}).get("refusals"), [f["finding"]+" | "+f["refusal"] for f in m["findings"]]) for m in d["modules"]]'

Expected: one refusal, `RuntimeException`, documented and raised, no finding. What I got:

    Box cost=5 (1 + 4)
       refusal {'name': 'RuntimeException', 'documented': True, 'documentedBy': ['go'], 'raised': False, 'checked': True}
       refusal {'name': 'T', 'documented': False, 'documentedBy': [], 'raised': True, 'checked': True}
       FINDING documented but never raised | RuntimeException | documentedBy ['go'] | raised False
       FINDING raised but never documented | T | documentedBy [] | raised True

At the parser seam directly:

    class-level <T>    type_parameters=('T',) raises=('T',) throws_not_read=0
    method-level <T>   type_parameters=()     raises=()     throws_not_read=1

Three things wrong at once, and they are the same three attempt 1 listed. `T` is not a
refusal, is not a `Throwable`, and cannot be caught by name, yet it is on the interface and
priced at 2 (`cost` 5 = 1 + 4). The refusal the module genuinely raises through `make()` is
reported as never raised. And a module whose documentation and implementation agree carries
a finding in each direction — which is the criterion "a module whose documentation and
implementation agree produces no finding", failing.

This is an implementation gap rather than a design question, because `_thrown_through`'s own
docstring already states the rule it does not apply: "the declarations of `f` ... agree on a
type variable or on nothing, neither of which is a refusal a caller could ever catch by
name". The method-level half is implemented and the class-level half is not. Threading
`declared.type_parameters` down through `_reached_in` → `_raised_in` → `_thrown_through` and
rejecting a return whose simple name is one of them is the direction the rest of the file
leans: where a name is ambiguous, read nothing rather than guess.

Nothing in `backend/src/main/java` is a generic class today, so the committed
`docs/module-depth-map.{json,html}` are not wrong. This is latent, not live — but so was
attempt 2's `throw this.`, and this page is one a training day projects at people who are
about to add code to this repository. `tests/refusalsastheirownband` has no `<T>` fixture on
the class side at all; pin whichever answer you take with one.

### 2. A falsifiable count in the README is wrong

`scripts/module_depth_map/README.md:297` says of the throws this tool cannot name:
"`SavingsAccountController` and `ScheduledJobs` each write one today." `ScheduledJobs`
writes two — `throw runtime;` and `throw error;` — and the parser agrees:

    SavingsAccountController       throws_not_read=1
    ScheduledJobs                  throws_not_read=2

The DEBUG run says the same thing: exactly 3 `throw not read as a refusal` lines, two of
them in `ScheduledJobs`. Attempt 1 sent this ticket back for a comment in `graph.py` that
had gone stale against the committed output; this is the same thing one file over, in a
document whose whole subject is a machine catching claims that stopped being true.

### 3. The page prints a number with no noun

`scripts/module_depth_map/page.py:348` concatenates a bare count:

    + document_.scoring.refusals.refusalsRead + " are read across this page."

which renders, on the committed page, as "... is wide for some other reason. 13 are read
across this page." Thirteen what — modules, bars, refusals? Every neighbouring count on that
same card goes through the `count(n, one, many)` helper — `count(..., "module", "modules")`,
`count(..., "refusal is", "refusals are")` — and this one does not, so it also has no
singular: on a source with one refusal it says "1 are read". I read this in the rendered
page at 1280 in both themes, not only in the source.

### What I checked and found good, so you do not re-do it

- **All three attempt-2 defects, reproduced on my own trees, all fixed.** `throw refusing`,
  `throw this.refusing`, `throw this . refusing` and `throw this\n.refusing` all give
  `('Shut',) 0`; `throw holder.refusing(...)`, `throw super.refusing(...)`,
  `throw this.holder.refusing(...)`, `throw (Shut) this.refusing(...)` and `throw thrown;`
  all decline and count as unread, which is the declared floor; `throw thisRefusing(...)` is
  not mistaken for a `this.` prefix. The reviewer's `Hidden` shape now reports the finding it
  was swallowing, and `Bolt` and `Bolted` draw byte-identical cards. `_visibility` answers
  `private` for an enum constructor with no modifier and is unchanged for enum methods, class
  constructors and everything else; `Colour` now carries no refusal and no finding. The flat
  pricing decision is made explicitly and argued in `scoring.json`, the README and the page.
- **The band, on the real source.** 13 refusals across 71 modules — the 7 `@throws` this
  repository writes (checked against `grep -rn "@throws\|@exception" backend/src/main/java`,
  each attributed to the right method) plus the 6 types thrown and undocumented.
  `cost == costWithoutRefusals + refusalCost` and `refusalCost == 2 × len(refusals)` on all
  71, and `costWithoutRefusals` equals the parent branch's `cost` for all 71 — the band is
  purely additive. Every finding on the page derives exactly from the refusal flags: 0
  mismatches.
- **The six findings on this source are true.** Exercised against the running application:
  `POST /api/savings-accounts/1/withdrawals {"amount":"5.00","toCurrentAccountId":1}` → 400,
  `WARN i.d.s.deposits.WithdrawalsService : withdrawal rejected savingsAccountId=1 ... reason=There is not enough in that savings account to move EUR 5.00.`
  then `Resolved [...WithdrawalRefused: ...]`; `POST /api/savings-accounts/1/deposits
  {"amount":"not-money",...}` → 400, `Resolved [...ResponseStatusException: 400 BAD_REQUEST]`.
  The kept-promise side too: `ClockRefused`, `DepositRefused`, `RewardRefused` and
  `JobRefused` all resolved and all documented, and the tool rightly reports no finding.
- **Both finding directions fire on a body the tool can read.** An interface `default`
  method that throws agrees and gets nothing; the same method with an empty body gets
  *documented but never raised*. An `abstract` method and an annotation's
  `String[] value() default {"a"}` are `checked: false` with no finding, reason logged.
- **Documentation is read, not guessed.** Ten probes: `@throws` mid-sentence or inside
  `{@link}` is prose; a tag in an ordinary `/* */` is not documentation; a `throws` clause is
  not documentation; `@exception` is read; `@throws java.lang.X` is `X`; an annotation
  between javadoc and method does not break the link; a javadoc documents the member under
  it and not the next one.
- **The rule is in the file, not the analyser.** Repricing `refusal` to 7 moved
  `ScheduledJobs` to 29 = 8 + 21; rewording `raisedNeverDocumented.finding` rewrote every
  finding and 8 places in the page; a configuration with no `refusals` section exits 4,
  writes nothing, and says `refusals is missing, and it has to be an object`. No finding
  string is spelled anywhere in the `.py` files.
- **The page.** Playwright (chromium, sync API) over `docs/module-depth-map.html` at
  light+dark × 1024+1280: 71 cards each, `scrollWidth - clientWidth == 0` at every width, and
  **zero** console messages, page errors or failed requests across all loads. I cross-checked
  every card against the graph at all four combinations — refusal names and order, the `found`
  class exactly where a finding names that refusal, the `unchecked` class exactly where
  `checked` is false, one finding line per finding, and both band widths against
  `widestInterface` — **0 problems**. Screenshots read, not just taken: `ScheduledJobs`
  (14 = 8 + 6, a wide gold band) reads as honestly wide beside `SavingsAccountController`
  (33 = 31 + 2, a gold sliver) as merely wide, in both themes. The Vite page at
  `localhost:5173` still loads clean (only Vite/React informational console lines).
- **Checks.** `python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests` —
  451 tests, OK. `cd backend && ./mvnw test` — 113 tests, BUILD SUCCESS. `cd frontend &&
  npm run typecheck` — clean. Two fresh runs into separate directories: graph and page
  byte-identical to each other **and** to the committed `docs/module-depth-map.{json,html}`.
- **Logging.** Full `--log-level DEBUG` run: 581 lines, **0 WARNING, 0 ERROR**.
  `refusals checked read=13 notChecked=0 modulesWithFindings=6 findings=6` at INFO, one INFO
  line per finding naming both sides, `refusals read module=... findings=N notChecked=N` at
  DEBUG for all 71 modules, and each not-checked refusal logged with which of the two reasons
  it was. No `print()` anywhere in the tool.
- **Three mutations, all caught, each reverted:** reverting `_THROWN_THROUGH` to the old
  pattern failed both new `this.` tests; reverting `_visibility` to always answer
  `package-private` failed `test_an_enum_constructor_with_no_modifier_promises_nobody_anything`;
  dropping refusals out of `cost` failed 7 tests across the suite. The pre-existing suites
  were strengthened rather than loosened — the band-width check now runs per band and asserts
  the two come to the bar, and the real-repository property test asserts the split.

### What I left ticked

Everything except "every refusal a module can answer with appears in the graph as part of
its interface" and "a module whose documentation and implementation agree produces no
finding", both of which point 1 falsifies with a four-line file. The band, its pricing, its
separate reporting, the page, the config-driven rule, the constructor reading, the
bodiless-signature reading, the `this.` reading, the enum-constructor reading, the fixtures
for the three directions and the logging are all real and all verified — none of them needs
doing again.

## Review feedback - attempt 4

The three defects attempt 3 named are genuinely fixed and I reproduced each on my own fresh
trees. What sends this back is two more instances of the shape attempts 1, 2 and 3 each sent it
back for — **a module that kept its word gets accused** — plus the third instance of the stale-prose
defect. Both accusations are latent on this source, as attempt 2's `throw this.` and attempt 3's
`<T>` were; both are four-line files away.

### 1. A nested type's `@throws` is invisible while its `throw` counts as the module's

`_documenters_of` (`scripts/module_depth_map/scoring.py:627`) reads the documented side off
`declared.methods` and `declared.constructors`. `_declared_types` fills both **only for the module
itself** — `methods = () if owner is not None else _methods_of(...)`. The raised side,
`declared.raises`, is read over the whole body, nested types included. So a nested type's `throw`
is counted as the module raising it, and the nested type's own `@throws` documenting that exact
refusal is never read. Reproduce with a two-file tree (`Shut` is the usual `extends
RuntimeException`):

    public class Till {
        private final Inner inner = new Inner();

        public void open() { inner.deep(); }

        public static class Inner {
            /**
             * Goes deep.
             *
             * @throws Shut when shut
             */
            public void deep() { throw new Shut("x"); }
        }
    }

Expected: documentation and implementation agree, no finding. What I got:

    refusal {'name': 'Shut', 'documented': False, 'documentedBy': [], 'raised': True, 'checked': True}
    FINDING raised but never documented | Shut | documentedBy [] | raised True

The refusal is documented on the very method that throws it, and the module is accused of throwing
something nobody wrote down.

This is attempt 1's point 2 one level over. That one was about constructors, and the answer it got
is the right one, written into the README: *"A constructor is read on **both** sides or it would be
read on one."* The same sentence settles this. The README already commits to the raised half —
"**raised** is what the module's body throws, over that whole body, **nested types** and
constructors included" (`README.md:227`) — so the tool advertises the symmetry it does not
implement. Read a nested type's `@throws` as the module documenting itself, or stop reading a
nested type's `throw` into `raises`; whichever you pick, say which in `scoring.py` and the README
and pin it with a fixture. `tests/refusalsastheirownband` has no nested-type fixture on the
documented side at all.

Nothing in `backend/src/main/java` is affected today: all 7 `@throws` this repository writes sit on
top-level methods (`ScheduledJobs`'s two are on `runNow`; its nested `AJob` at line 240 documents
nothing), so the committed `docs/module-depth-map.{json,html}` are not wrong. Latent, not live —
but this repository has seven nested types already, and the page is one a training day projects at
people about to add an eighth.

### 2. A tagless javadoc does not shield its member from an older tagged one

`_refusals_documented_in` (`scripts/module_depth_map/javasource.py:708`) drops every javadoc block
carrying no `@throws` from the offset list it searches. `_documented_before` then bisects to the
nearest **remaining** block and accepts it whenever `masked[ends_at:at]` is blank — but comments are
blanked in the masked text, so the intervening javadoc it walked past is invisible and the gap looks
empty. A member's own javadoc therefore does not stop the search reaching an older one.

Reproduce at the parser seam. All three of these give `go` `documented_refusals == ('Shut',)`, and
`go`'s own javadoc mentions no refusal at all:

    /** @throws Shut when shut. */

    /** Opens it. */
    public void go() { }

    ---

    /** @throws Shut when shut. */
    // public void old() { throw new Shut("x"); }

    /** Opens it. */
    public void go() { }

    ---

    /** @throws Shut when shut. */
    /* just a note */
    /** Opens it. */
    public void go() { }

    python3 -c 'import sys; sys.path.insert(0,"scripts")
    from module_depth_map import javasource
    t = javasource.parse(open("<file>").read(), "X.java").types[0]
    print([(m.name, m.documented_refusals) for m in t.methods])'

End to end on the middle one, as a one-class module:

    refusal {'name': 'Shut', 'documented': True, 'documentedBy': ['go'], 'raised': False, 'checked': True}
    FINDING documented but never raised | Shut | documentedBy ['go'] | raised False

Two things wrong. `go` is credited with documenting a refusal its own javadoc never mentions, which
is the criterion "documented refusals are read from the source's own documentation rather than
guessed at" failing — this is guessed at. And a module that promised nothing carries a finding
naming a method that promised nothing, which is "a module whose documentation and implementation
agree produces no finding" failing.

Note what does **not** trigger it, so you fix the right thing: an intervening javadoc'd **field**
is fine (`[('go', ())]`), and an intervening javadoc'd **method** is fine — attempt 2 probed both
and they still pass. The member occupies an offset and stops the search. What does not stop it is a
javadoc block with no `@throws` in it, because that block was never put in the list.

The docstring's claim that "this member documents no refusal" and "this member has no javadoc" are
the same answer holds for the member being asked about, but not for the nearest-block search that
runs underneath it: an empty block still has to occupy its offset so the search stops there. Keeping
every javadoc's `ends_at` in the list and storing `()` for the tagless ones is the direction the
rest of the file leans — where a name is ambiguous, read nothing rather than guess.

Latent here too: I checked all 13 refusals on this source and every one of the 7 documented ones is
attributed to the right method.

### 3. `new AmountOfMoney(-1)` does not refuse, and has never existed

The paragraph justifying the decision to read a constructor's `@throws` — the decision attempt 1
asked for and attempt 2 made — rests on a named example from this repository.
`scripts/module_depth_map/README.md:252`:

    The other answer says something false about the source —
    `new AmountOfMoney(-1)` refuses, and a caller has that to learn — and validating in a
    constructor is the sanctioned way to give a Java value an invariant [...]

`backend/src/main/java/io/dataroots/savingstreak/deposits/AmountOfMoney.java` is a `final class`
whose **only** constructor is

    private AmountOfMoney() {
    }

— no arguments, empty body, throws nothing. `new AmountOfMoney(-1)` does not compile and never did:
`git log --all -S "new AmountOfMoney(" -- backend/src` returns nothing on any branch. The class's
own javadoc says the opposite of what the README claims for it: *"It answers with the reason rather
than refusing, because who refuses differs"*. The tool's documentation picks, as its example of a
constructor that refuses, the one class in this repository that deliberately does not.

Reproduce:

    grep -rn "AmountOfMoney(" backend/src         # one hit: private AmountOfMoney() {
    git log --all --oneline -S "new AmountOfMoney(" -- backend/src   # no output

The same sentence is written into four shipped files and a test:

    scripts/module_depth_map/scoring.py:46
    scripts/module_depth_map/scoring.py:573        (_documenters_of's own docstring)
    scripts/module_depth_map/README.md:252
    scripts/module_depth_map/javasource.py:302     (_constructors_of's own docstring)
    scripts/module_depth_map/tests/refusalsastheirownband/test_refusals_are_their_own_band.py:1059

so the docstrings of the two functions that implement the constructor rule, and of the test class
that pins it, all justify themselves with something that is not there. Attempt 1 sent this ticket
back (point 4) for a `graph.py` comment gone stale against the committed output; attempt 3 sent it
back (point 2) for a README count that said "one" where the parser said "two". This is the same
defect a third time and larger than either.

**The honest replacement.** No constructor in this repository refuses at all — I checked all 43 the
parser sees, `documented_refusals` empty on every one and none of them throws:

    python3 -c 'import sys,pathlib; sys.path.insert(0,"scripts")
    from module_depth_map import javasource
    for p in sorted(pathlib.Path("backend/src/main/java").rglob("*.java")):
        for t in javasource.parse(p.read_text(), p.name).types:
            for c in t.constructors: print(t.name, c.name, c.documented_refusals)'

So the constructor rule is exactly where the bodiless-`@throws` bullet already is two paragraphs
later, and that bullet is worded honestly: *"Nothing in this repository writes one today; it is the
shape a participant is most likely to add next, and `tests/refusalsastheirownband` pins it."* Say
that. `Deposit` is not a substitute — its `IllegalArgumentException` comes from `reduceBy`, a
method, not from either of its two constructors. Do not guess another example; whatever replaces
this will be checked.

### 4. Minor: `refusalsNotChecked` does not count what README:304 sends a reader to it for

    Neither is a finding and neither is a module keeping its word, so they are counted apart,
    as `refusalsNotChecked` in the graph and on the page

An unnameable `throw` is counted only in `DeclaredType.throws_not_read`, which is never emitted —
`grep throws_not_read docs/module-depth-map.json` finds nothing. It reaches `refusalsNotChecked`
only indirectly, by withholding a check from some documented refusal, and only when that refusal is
not also raised. `ScheduledJobs` has `throws_not_read = 2` while the graph's
`scoring.refusals.refusalsNotChecked` is `0`, so a reader who follows that sentence to the graph
finds the count is not there. Either publish the number or reword the claim to say what is actually
carried.

### What I checked and found good, so you do not re-do it

Nothing below needs doing again. Six of the eight criteria are proven; the two I unticked are
falsified by points 1 and 2 above and by nothing else.

- **All three attempt-3 defects fixed, reproduced on my own trees.** The reviewer's `Box`
  (`class Box<T extends RuntimeException>`, `private T make()`, `throw make()`) now gives
  `cost=3 (1 + 2)`, one refusal `RuntimeException` `documented=True raised=False checked=False`,
  **no findings**, and no `T` on the band or in the price. `Boxed` (method-level `<T>`) is
  identical. Six further shapes, all right: `Pair<K, V extends RuntimeException>` declines;
  `throw this.make()` in a generic class declines; a `<T>` on a **nested** class declines;
  `<T extends Comparable<T>>` parses as `('T','E')`; a generic interface and a generic record
  behave; and `BoxStale<T>` — a generic class with a genuinely stale `@throws` — still fires
  **both** findings, so the decline did not switch the check off. At the parser seam, class-,
  method- and nested-level `<T>` all give `raises=() throws_not_read=1`.
- **The `_thrown_through` shape change is safe.** `private <U> Shut refusing(U why)` is now read
  rather than declined, which is correct — a generic method handing back a concrete refusal throws
  that refusal. Ambiguity still declines. The only way a hole could slip through is a type variable
  introduced somewhere neither the type's nor the candidates' `<...>` covers, and for a top-level
  module the JLS allows no such place.
- **The README count is right now**: parser gives `ScheduledJobs 2`, `SavingsAccountController 1`;
  the DEBUG run writes exactly 3 `throw not read as a refusal` lines. **The page count has its
  noun**: "13 refusals are read across this page" on this source, and a one-refusal tree I built
  renders "1 refusal is read across this page". **The `graph.py` comment attempt 1 flagged is now a
  rule, not a stale observation.**
- **The band, on the real source.** 13 refusals over 71 modules — the 7 `@throws` this repository
  writes, each attributed to the right method, plus the 6 raised-and-undocumented. On all 71:
  `cost == costWithoutRefusals + refusalCost`, `refusalCost == 2 x len(refusals)`, and every finding
  names a refusal on that module's band. Against `ticket/04-the-deletion-test`'s committed graph,
  `costWithoutRefusals` equals the parent's `cost` for **all 71** and no reach or deletion-test
  verdict moved — the band is purely additive.
- **All six findings on this source are true, driven against the running application.**
  `POST /api/savings-accounts/1/withdrawals {"amount":"5000.00","toCurrentAccountId":1}` -> 400 and
  `WARN i.d.s.deposits.WithdrawalsService : withdrawal rejected savingsAccountId=1 ... amount=5000.00`
  then `Resolved [io.dataroots.savingstreak.deposits.WithdrawalRefused: ...]`. All three
  controllers' `ResponseStatusException` too: `GET /api/savings-accounts/9999/deposits` -> 404;
  `POST .../withdrawals {"amount":"nope"}` -> 400 with
  `WARN i.d.s.web.SavingsAccountController : withdrawal rejected savingsAccountId=1 amount=nope reason="nope" is...`;
  `GET /api/customers/9999/accounts` -> 404; `POST /api/dev/clock/advance {}` -> 400. The kept-word
  side too: `ClockRefused` (days=0 and days=100000), `JobRefused` and `DepositRefused` (amount 0.00)
  all resolved, all documented, no finding on any. Deposit 12.34 -> 201, redemption -> 201.
- **Both finding directions and the unchecked case render.** A five-module tree (`Kept`, `Stale`,
  `Silent`, `Port` as a bodiless interface, `Shut`): `Kept` plain in band ink, titled "documented by
  go, and raised by this module"; `Port` `refusal unchecked`, `underline dotted`, band ink, titled
  "whether this module raises it is not something this tool could read"; `Silent` and `Stale`
  `refusal found` in alarm ink with one finding line each naming both sides. Identical in light and
  dark, zero console output. Worth having, because `refusalsNotChecked` and `documentedNeverRaised`
  are both 0 on the real source.
- **The page.** Playwright (chromium, sync API, console/pageerror/requestfailed all subscribed) over
  `docs/module-depth-map.html` at light+dark x 1024+1280: 71 `li.module` cards each,
  `scrollWidth - clientWidth == 0` at every width, and **zero** console messages, page errors or
  failed requests across every load. All 71 cards cross-checked against the graph at all four
  combinations — refusal names and order, band classes `["learn","refuse"]`, both band widths
  against `widestInterface`, bar title, the `found` class exactly where a finding names that
  refusal, the `unchecked` class exactly where `checked` is false, finding-line count —
  **0 problems**. Screenshots read, not just taken: `ScheduledJobs` (14 = 8 + 6, a wide gold band)
  reads as honestly wide beside `SavingsAccountController` (33 = 31 + 2, a gold sliver) as merely
  wide, in both themes.
- **The rule is in the file, not the analyser.** With my own configs: `refusal` at 5 moved
  `ScheduledJobs` to 23 = 8 + 15; rewording `raisedNeverDocumented.finding` rewrote every graph
  finding and 8 places in the page; deleting the `refusals` section exits 4, writes nothing, and
  says `refusals is missing, and it has to be an object. Nothing is scored with a rule nobody
  wrote`. No finding string is spelled anywhere in the `.py` files.
- **Checks.** `python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests` — 455
  tests, OK. `cd backend && ./mvnw test` — 113 tests, BUILD SUCCESS. `cd frontend && npm run
  typecheck` — clean. Two fresh runs into separate files: graph and page byte-identical to each
  other **and** to the committed `docs/module-depth-map.{json,html}` (4 silent `cmp`s).
- **Logging.** Full `--log-level DEBUG` run: 581 lines, **0 WARNING, 0 ERROR**.
  `refusals checked read=13 notChecked=0 modulesWithFindings=6 findings=6` at INFO, one INFO line
  per finding naming both sides, `refusals read module=... refusals=... findings=N notChecked=N` at
  DEBUG for all 71 modules, the rule once at DEBUG, and `interface read name=... refusals=N
  cost=... refusalCost=...` per module. No `print()` in the tool.
- **Three mutations, all caught, each reverted.** Dropping the class's own `type_parameters` out of
  `_thrown_through`'s `holes` — 3 failures. Restoring the bare concatenation in `page.py` — 2
  failures including the byte-identical-page test. Dropping refusals out of `cost` — 7 failures
  across four suites.

### What I left ticked

Everything except "documented refusals are read from the source's own documentation rather than
guessed at" (broken by 2, which credits a method with a promise its javadoc never made) and "a
module whose documentation and implementation agree produces no finding" (broken by 1 and 2). The
band, its pricing, its separate reporting, the page, the config-driven rule, the constructor
reading, the bodiless-signature reading, the `this.` reading, the enum-constructor reading, the
type-variable reading, the fixtures for the three directions and the logging are all real and all
verified — none of them needs doing again.
