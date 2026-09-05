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

**Status:** done

- [x] Every refusal a module can answer with appears in the graph as part of its interface
- [x] Refusals count toward interface cost, and are also reported separately from the rest of it
- [x] The page shows refusals as their own band, so an honestly-wide interface is distinguishable from a merely wide one
- [x] Documented refusals are read from the source's own documentation rather than guessed at
- [x] A module documenting a refusal it cannot raise produces a finding naming both sides of the disagreement
- [x] A module raising a refusal it does not document produces a finding naming both sides of the disagreement
- [x] A module whose documentation and implementation agree produces no finding
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

## Verified

Reviewed on `ticket/05-refusals-as-their-own-band` at `1be10a0`, over the diff against
`ticket/04-the-deletion-test`. The three defects that sent attempt 1 back are fixed, and I
reproduced each of them on a fresh fixture tree rather than taking the implementer's word.

**The three attempt-1 defects, re-run on my own trees.** I rebuilt the reviewer's
`Overloaded`, `Built` and `DepositsPort` fixtures from scratch and ran
`graph.build([...], scoring.load())` over them:

- `Overloaded` (two private `refusing` helpers returning `Shut` and `String`): one refusal,
  `Shut`, `documented: True`, `checked: False`, **no finding in either direction**, and no
  `String` anywhere on the interface. The bogus refusal and the double false accusation are
  gone. `DEBUG refusal not held against the implementation module=Overloaded refusal=Shut
  documentedBy=go reason=1 throw(s) in this body name a type only javac could resolve, so
  what it raises was not read whole`. It reports `raised: False` rather than resolving the
  overload — a floor, argued in `_thrown_through`'s docstring, and it costs nothing here
  because no finding is made from it.
- `Built` (constructor `@throws` plus `throw new IllegalArgumentException`):
  `documented: True, documentedBy: ['Built'], raised: True, checked: True`, no finding. A
  record's compact constructor (`Compact`) behaves the same.
- `DepositsPort` (interface method with `@throws`, no body): `checked: False`, no finding,
  `reason=every member promising it is a signature with no body of its own, so the promise
  is to whoever implements it`.
- Both directions still fire on real disagreements: `Stale` gives *documented but never
  raised*, `Silent` gives *raised but never documented*, each naming both sides.
- The stale `graph.py` comment is rewritten as a rule with no claim about this week's
  source.

**The band, on the real source.** 13 refusals across 71 modules — exactly the 7 `@throws`
this repository writes plus the 6 types thrown but undocumented, checked one by one against
`grep -rn "@throws\|throw " backend/src/main/java`. `cost == costWithoutRefusals +
refusalCost` on every module, and `costWithoutRefusals` is byte-for-byte the parent
branch's `cost` for all 71 — the band is purely additive and shifted nothing else.

**All six findings are true.** I triggered two against the running application:
`POST /api/savings-accounts/1/withdrawals {"amount":"5.00","toCurrentAccountId":1}` on an
empty account returned HTTP 400 and logged
`WARN i.d.s.deposits.WithdrawalsService : withdrawal rejected savingsAccountId=1
toCurrentAccountId=1 amount=5.00 balance=0.00 reason=There is not enough in that savings
account to move EUR 5.00.` followed by `Resolved
[io.dataroots.savingstreak.deposits.WithdrawalRefused: ...]` — so `WithdrawalRefused`
really does reach a caller with nothing on the seam telling them to catch it. The
documented-and-kept side checks out too: `POST /api/dev/clock/advance {"days":0}` resolved
`ClockRefused`, which `ClockService.advanceBy` documents, and the tool rightly reports no
finding on it.

**The page.** Playwright over `docs/module-depth-map.html` at light+dark x 1024+1280: 71
cards each, `scrollWidth - clientWidth == 0` at every width, and **zero** console messages,
page errors or failed requests across nine page loads. I cross-checked every card against
the graph at all four combinations — band classes, both band widths against
`widestInterface`, bar and band titles, refusal names and order, the `found` class exactly
where a finding exists, the `unchecked` class exactly where `checked` is false, one finding
line per finding naming both sides — **0 problems**. Screenshots read, not just taken:
`SavingsAccountController` is a near-full blue bar with a 2-wide gold sliver (33 = 31 + 2,
merely wide) against `ScheduledJobs` at 14 = 8 + 6 (honestly wide), and the two are
distinguishable at a glance in both themes. On a fixture page carrying every shape at once
the unchecked refusal renders `underline dotted` in band ink, distinct from the alarm ink a
finding gets, in both themes.

**Parser probes for new false findings.** Fourteen shapes, none of which misfired: an
`@throws` mid-sentence or inside `{@link}` is prose; a tag in a field's javadoc does not
document the method under it; a class-level `@throws` does not document its first method; a
`throws` clause on a signature is not documentation; `@exception` is read as a synonym; a
one-line `/** @throws Shut ... */` is read; annotations between the javadoc and the method
do not break the link; an `@interface`'s `String[] value() default {"a"}` parses and
promises nothing; an interface's `default` method with a body **is** checked and agrees; a
generic `<T extends RuntimeException> T refusing(...)` is declined rather than guessed at;
an enum with constants reads its documented method correctly; a nested type's `throw`
counts as the module's. A refusal documented on both a bodiless signature and a method with
a body is checked on the body (`Mixed` gives a genuine finding).

**Known limitation, deliberate and logged.** `throws_not_read` is module-wide, so one throw
the tool cannot name suppresses *every* documented-but-never-raised finding on that module
(`Masked` fixture: a genuinely stale `@throws` goes unreported because the module also
writes `throw caught;`). This errs toward silence, which is the direction attempt 1
demanded, and the reason is logged at DEBUG per refusal. `refusalsNotChecked` is 0 on this
repository, so nothing is lost here today.

**Checks I ran myself.** `python3 -m unittest discover -t scripts -s
scripts/module_depth_map/tests` — 447 tests, OK. `.scratch/module-depth-map/lab.sh checks`
— backend 113 tests, 0 failures, BUILD SUCCESS; frontend typecheck clean. Two fresh runs
into separate directories: graph and page byte-identical to each other **and** to the
committed `docs/module-depth-map.{json,html}` (4 silent `cmp`s). The Vite page at :5173
still loads clean.

**Five mutations, all caught**, each applied to the committed tree and reverted: `checked`
always true (7 failures), constructors dropped from the documenters (5), every member
counted as having a body (5), refusals dropped out of `cost` (7), last-declaration-wins for
a throw-through (3). The pre-existing suites were strengthened rather than loosened —
`test_this_repository_is_read_whole` now asserts the band split on every module in the real
repository.

**Logging.** Full `--log-level DEBUG` run: 581 lines, **0 WARNING, 0 ERROR**. `INFO ...
refusals checked read=13 notChecked=0 modulesWithFindings=6 findings=6`, one INFO line per
finding naming both sides, `refusals read module=... refusals=... findings=N notChecked=N`
at DEBUG for all 71 modules, the rule once at DEBUG. Exactly 3 `throw not read as a refusal`
lines, and they are the three the source actually writes: `throw runtime;` and `throw
error;` in `ScheduledJobs`, `throw notAnAmountOfMoney;` in `SavingsAccountController`. No
`print()` anywhere in the tool.
