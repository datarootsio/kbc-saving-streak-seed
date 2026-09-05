# Module depth map

A deterministic picture of what this application is made of. One command reads the source
and writes two files: a graph document naming every module, and a self-contained page that
is a pure rendering of that document.

Run it from the repository root:

    python3 scripts/module-depth-map.py

It writes `docs/module-depth-map.json` and `docs/module-depth-map.html`. Open the HTML file
directly — there is no server to start and nothing is fetched from the network.

Run its tests with the standard library's own runner, also from the repository root:

    python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests

## What it promises

- **The same source gives the same bytes.** No clock is read, no absolute path is written,
  and every collection is emitted sorted. Two runs over unchanged source are byte
  identical, and a test asserts it.
- **The page contains nothing the graph does not.** The page carries the graph document
  verbatim and draws itself from it, so there is no second place for a fact to come from.
- **Source it cannot read is named, not scored as empty.** Parsing is targeted pattern
  matching rather than a full Java grammar. Anything it cannot make sense of is logged as a
  warning with the reason, counted in the graph, and shown on the page, because a parse
  failure that looked like an empty module would be indistinguishable from a real finding.
  Silence has to mean nothing was missed, so a file is failed by name whenever the parser
  can tell it has stopped reading what the compiler would read:

  - it will not open at all — a dangling symlink, no read permission, deleted since the
    walk — or it is not UTF-8;
  - a block comment, text block, string or character literal is never closed, which would
    otherwise blank out the rest of the file and take every declaration in it with it;
  - its braces do not balance, in either direction: left open at the end, or closing more
    than were opened, which shifts later declarations to a depth the source does not have;
  - a type is declared somewhere the parser cannot place, rather than being hung off
    whichever module happened to come before it;
  - a reserved declaration keyword matched no declaration, caught by counting `class`,
    `interface` and `enum` against the declarations actually found;
  - a type declaration's body cannot be found — every kind of type Java declares has one,
    and a type read without a body would have its whole interface priced at zero;
  - a parameter cannot be read, or a member hands back something that is not spelled the
    way a type is. These two are where a misread shape would make an interface *cheaper*
    rather than absent, and a cheap interface is what this page calls deep, so each is
    named with its line instead.

  A directory that will not open is named the same way, because the modules under it would
  otherwise be missing while every count still added up. One bad file costs one card; both
  outputs are still written. `package-info.java` and `module-info.java` declare no type on
  purpose and are read rather than reported, and legal Java is never failed — an escaped
  `\"""` inside a text block is a quote, not the end of it, and a modifier written flush
  against a type-parameter list (`static<T> T first(T only)`) is a modifier rather than
  part of a return type nobody can read. That last one was a false alarm this tool used to
  raise, found by review rather than by the suite; there is a test for the shape now,
  because an alarm that cries wolf stops being read.

  One decision cannot be failed on, and it is where every fault found so far got in:
  deciding that a member which reads like a method is not one. A field, a constructor and
  a nested record all read like one and legitimately are not, so there is nothing to fail
  on — and a method wrongly declined leaves an interface quietly cheaper than the source
  makes it, which is what this page calls deep. So it is logged: run with
  `--log-level DEBUG` and `grep "member not read as a method"` for every member declined,
  its line and the reason. On this repository that is 56 lines — 43 constructors, 10
  fields with a value assigned to them (nine loggers and a `SecureRandom`), 3 nested
  records — short enough to read and check.
- **The rules that score a module live in a file, not in the analyser.** `scoring.json`
  beside this file holds the interface-cost weights, every exclusion rule, what makes a
  reached thing an adapter or a record, where the deletion test draws its line, and what
  each disagreement between a documented refusal and a raised one is called. Change a
  weight or a rule there, run the tool again, and the output moves; nothing in the analyser
  is edited, and no rule name or weight is written into it to fall back on. Every name a
  rule matches on is a **simple** name — `SpringBootApplication`, `JpaRepository`, `List` —
  because that is how the parser records what it read. Anything the *source* puts around
  such a name and this tool never records is refused rather than accepted as a rule that
  could never fire: the `@` on an annotation, a supertype's type arguments, a package
  prefix, a stray space. `"@SpringBootApplication"` and `"JpaRepository<Deposit, Long>"`
  are how the source writes the two rules shipped here, so they are the natural mistake,
  and the refusal names the form that does work. So is a kind the parser never reports, an
  empty list of names for a rule to match on, a name written twice in one list, and
  `--scoring ""`. The one list that may be empty is `typesEveryCallerAlreadyKnows`:
  charging a caller for every type they meet is a position somebody can hold, not a
  misspelling.
- **Nothing is excluded without a named rule.** Each excluded module carries the rule that
  excluded it and the fact about the module that matched, so "why was this ignored?" always
  has an answer a reader can point at and argue with.
- **It refuses rather than guessing.** No source directory (exit 2), two files declaring
  the same module id (exit 3) and a scoring configuration this tool cannot use (exit 4)
  all stop the run with the reason logged as a warning, before a byte is written — because
  a page that drew one of two clashing modules twice, or that scored with weights nobody
  wrote, would be worse than no page. Outputs it cannot write are exit 5, which is the one
  code with two paths: a destination it can see is unwritable is refused as a warning with
  nothing written, and a move that fails after another has already landed is logged at
  ERROR with the exception, described below.
- **The graph and the page are written together, or neither is and the run says so.** Both
  are rendered to bytes, written beside where they belong, and then moved into place. Two
  renames are not one step and this does not claim to be atomic: what it claims is that no
  failure is silent. Every way a move can fail that the tool can check for is checked
  before a byte is written — a destination that is already a directory, which the `open`
  does not catch, and two outputs sent to one file, which nothing catches at all because
  it succeeds: both would stage to one `.writing` file, the second's bytes would land
  under the first's name, and the run would report the wrong output as the one written.
  Nothing is left half written; a move that fails anyway is logged at ERROR with the
  exception, naming which file landed and which one is still the previous run's. Nothing
  here ends the run with a traceback.
- **The graph says which shape it is.** `schema` in the document is the contract an agent
  reading it is promised: it moved to `module-depth-map/2` when the document gained a
  top-level `scoring` object and gave every module an `interface` and an `excludedBy`, and
  to `module-depth-map/3` when every module gained a `reach` and a `depth`, and to
  `module-depth-map/4` when every module gained its `callers` and the `deletionTest`
  verdict read off them, and to `module-depth-map/5` when every module gained its
  `findings` and its `interface` gained a `refusals` band with the `refusalCost` and
  `costWithoutRefusals` the total is split into.
  The page checks it before drawing, and says so rather than drawing half a document, because
  reaching into a shape that is not there throws in the middle of one pass and reads as a
  page that ended early.
- **The committed outputs are the ones this source produces.** `docs/module-depth-map.json`
  and `docs/module-depth-map.html` are checked in, and a test byte-compares them against a
  fresh run, so adding a Java class without rerunning the tool fails the suite instead of
  leaving a stale page for a reader to trust.

## What is on it so far

Every top-level type in the backend source, at class grain, grouped by its package. Types
declared inside another are listed on the module that holds them rather than becoming
modules of their own, each named by where it sits inside that module — `Body.Kind`, not a
second `Kind` a reader cannot tell from the first.

Each module is drawn as a bar whose width is what its interface costs a caller — in two
bands, the refusals it can answer with and everything else — over a fan with one line out
to each thing it coordinates on that caller's behalf, and under both a verdict on what
deleting it would do — read off the fan, the bar and the number of modules
that go through it. Nothing is ranked, and nothing is proposed for change.

## What an interface costs

Everything a caller has to learn before they can use a module correctly:

- **each method they can reach.** Reachable is `reachableFromOutside` in the configuration:
  public, protected and package-private here, because a method the neighbours can call is a
  method somebody has to learn. A constructor is left out — it says how a module is built,
  which in this application is the framework's business rather than a caller's.
- **each parameter of each of those methods.** `Map<String, Long>` is one parameter,
  `String...` hands over a `String`, `int xs[]` is the same parameter as `int[] xs`, and
  the receiver a method may name (`void ring(Till this, long id)`) is not a parameter a
  caller passes at all.
- **each distinct type crossing the seam** in a parameter or a return, counted once per
  module however many methods hand it over, and weighted apart: a type whose name is in
  `typesEveryCallerAlreadyKnows` counts `typeEveryCallerAlreadyKnows`, and every other type
  counts `typeToLearn`. That is what makes a method handing back a domain type cost more
  than one handing back a primitive. That list is the whole of the difference, and it is
  all `mustBeLearned` in the graph means: this tool reads one source tree and never
  resolves a name, so it cannot and does not say where a type was declared — `ProblemDetail`
  is charged at `typeToLearn` for the same reason `RecordedDeposit` is, which is that
  nobody put it on the list. `List<Optional<Customer>>` is three types; `java.util.List`
  and `List` are one; a type variable — the `T` in `<T> T first(List<T> of)` — is a hole
  the caller fills rather than a type anybody learns, and is not counted; and `void` is
  not a type at all, so a method that hands nothing back puts nothing across the seam.

- **each refusal it can answer with**, at `refusal` apiece. These are the band described
  in its own section below: counted into the cost like everything else, and reported apart
  from it as well.

Every bar is drawn against one number, `scoring.widestInterface` in the graph, so two of
them can be compared by eye.

A record's components each give it an accessor, and a record that writes one of those
accessors out itself — to copy or to normalise what it hands back — is offering the one
method Java compiles, not two.

Prose invariants, ordering constraints, the bound on a type variable, the constructor a
caller writes `new` against, the methods a module inherits rather than declares, and the
members of a type declared inside a module are part of an interface and are not measured.
Inheritance is a limit of reading one file at a time: `MovableClock extends Clock` is read
for the five methods it writes down, and `Clock.millis()` is reachable through it without
being in this file to count. A nested type is a deliberate boundary rather than a limit —
it is named on the module that holds it instead of becoming one — and its members are
named nowhere. A bar is therefore a floor on what a caller must learn rather than the
whole of it, and the page says as much, in those words and naming each of them, rather
than letting the number read as complete.

A zero is that floor at its lowest, and three modules here sit on it with a constructor
and nothing else: `ClockRefused`, `JobFailed` and `JobRefused` each declare one, so a
caller writing `new JobFailed(what, cause)` has it and both types crossing it to learn.
Their cards say `nothing this bar counts, which is not the same as nothing to learn`, so a
zero drawn over an empty class — `SchedulingIsOn` declares nothing at all — reads the same
as a zero drawn over a constructor, which is what the two of them have in common.

Under each bar the page draws the counts the cost was added up from, one for every weight
in the configuration file — read from the document's own `scoring.weights`, so a weight
the page has no term for arrives as a term saying so rather than as part of a total with
nothing under it. The counts are checked against the cost they are printed under, and a
breakdown that does not come to it says so on the card: a number a reader is invited to
argue with has to be one they can add up.

## Refusals, and the band they are drawn in

A refusal a module can answer with is something a caller has to know before they call it:
it decides what they write around the call. So it is interface, and it is priced with the
rest of the interface. It is also carried as a **band of its own** — `refusalCost` beside
`costWithoutRefusals`, both adding up to `cost`, and a second colour on the bar — because
a module whose interface is wide *because it is honest about how it can fail* should be
distinguishable from one that is merely wide. Folded into a single number the two are
identical, and a module is then paid for saying nothing about its failure modes.

A refusal is charged `refusal` flat, whatever it is called, and it is *not* run through
`typesEveryCallerAlreadyKnows` the way a type crossing the seam is. That list is about
types a caller already holds: `List` handed back costs nothing because they know `List`
already. Knowing that `IllegalStateException` exists is not knowing that *this module*
answers with one, and that second thing is what the band counts — so a well-known refusal
costs the same as an invented one. `scoring.json` says so where the weight is argued for,
rather than borrowing the seam-type rule it does not follow.

Each refusal is read from the source's own words on both sides, and guessed at on neither:

- **documented** is the `@throws` (or `@exception`) written in the javadoc over a *member*
  a caller can reach — a method, or a constructor. A refusal named in prose, or inside a
  `{@link}`, is prose; a tag in an ordinary `/* */` block is a note to whoever edits the
  file; and a `@throws` over a private helper or a private constructor documents that,
  rather than the seam. An enum's constructor written with no access modifier is one of
  those private ones: the JLS makes it private and allows nothing else there, so what it
  promises is a note to whoever maintains the enum. A javadoc documents the member written
  under it, annotations and all — `@Transactional` sits between the two — and not the
  member after that one.
- **raised** is what the module's body throws, over that whole body, nested types and
  constructors included. Two spellings are read: `throw new X(...)`, and `throw f(...)`
  where `f` is a method this module declares, which is a throw of whatever `f` hands back.
  That second one is not a nicety — `ClockService` and `ScheduledJobs` both write
  `throw refusing(why)`, and left unfollowed each is reported as promising a refusal it
  never raises. Where a module declares that name more than once and the declarations hand
  back *different* types, nothing is read: which one a call meant is settled by the
  arguments and their types, which is javac's job. It cannot be done here even in
  principle, because this reads the masked source, where a literal has been blanked — so
  `refusing("shut")` and `refusing()` are the same characters by the time the reading gets
  to them, and an arity read off them would be a guess. Nothing is read either where that
  helper hands back a **type variable** — `private T make()` — whichever of the two places
  Java lets one be declared it came from, the method's own `<T>` or the enclosing type's.
  `T` is a letter standing in for whatever the caller filled it with, not a type anybody
  can write a `catch` for.

`throw this.f(...)` is `throw f(...)`. The prefix is one a writer may put on a call to
their own method and nothing else, and reading the two spellings differently would be
worse than untidy: the count of throws this tool could not name is module-wide, so a
single unread throw withdraws the *documented but never raised* check from every refusal
in that module. One keystroke would then have switched a module's promises off.

A constructor is read on **both** sides or it would be read on one. Its body is part of the
module's body, so `throw new IllegalArgumentException(...)` inside it is already counted as
raised; the choice was between reading its `@throws` too and no longer reading its throw.
This tool reads the `@throws`. The other answer says something false about the source —
`new AmountOfMoney(-1)` refuses, and a caller has that to learn — and validating in a
constructor is the sanctioned way to give a Java value an invariant, so the asymmetry
accused the commonest idiom there is of raising something nobody documented. What documents
such a refusal is named by the module's own name, which is what a constructor is called.

Names are matched simply, so `@throws java.lang.IllegalArgumentException` and
`throw new IllegalArgumentException` are one refusal rather than a disagreement about a
package prefix. Both sides are read as a union: a refusal only the documentation promises
is one a caller writes a `catch` for, and one only the body throws is one they meet anyway.

Where the two sides disagree the graph carries a **finding** on the module, naming both of
them — the refusal, which methods document it, and whether anything raises it. There are
two, named and argued for in `scoring.json` like every other rule here: **documented but
never raised**, and **raised but never documented**. A module whose documentation and
implementation agree carries no finding at all, and every finding is logged at INFO with
both sides, so the disagreement does not need the page to be opened. Findings are read for
every module, scored or not: a rule that declines to *price* a record has said nothing
about whether that record's javadoc tells the truth.

On this repository that turns up six, all in the same direction: `Deposit`,
`WithdrawalsService`, `ScheduledJobs` and the three controllers each throw something no
method a caller can reach documents. Every seam that *is* documented keeps its word —
`AccountsService`, `ClockService`, `DepositsService`, `PointsService`, `RewardsService`,
and `ScheduledJobs` on its own two — so the other direction is not demonstrated on this
source at all, and the fixtures in `tests/refusalsastheirownband` are where it is
established.

What the band leaves out is a floor in the same direction as everything else here, and the
page says so rather than implying the count is complete. A refusal thrown by a name rather
than by a type — `throw thrown`, `throw somethingElse.build()` — needs a type this tool
never resolves, so it is not read and not guessed at. A refusal one module raises by
calling another is the second module's, and is drawn there. And a prose sentence about
when something fails is part of the interface and is measured nowhere. So a band at
nothing says only that there was nothing here to read.

Which is why a *documented but never raised* finding is made only where the implementation
was there to read and all of it was read. Two things stop it:

- **A `@throws` on a method with no body.** An interface's method, an abstract one, a
  native one and every member of an `@interface` all promise something whoever implements
  them has to keep, and holding that against this module's own body would accuse every
  documented interface in a source of breaking a word it never gave. The annotation is
  answered by its kind rather than by the brace it writes: `String[] value() default {"a"}`
  is the one member header holding a brace that opens no body. The refusal is still on the band — a caller of the
  interface has it to learn — and no finding is made about it. Nothing in this repository
  writes one today; it is the shape a participant is most likely to add next, and
  `tests/refusalsastheirownband` pins it.
- **A `throw` in the body this tool could not name.** Every `throw` is counted, including
  the ones neither spelling above reads, and a module carrying one may be raising exactly
  what it promised. Three are written today: `throw notAnAmountOfMoney;` in
  `SavingsAccountController`, and `throw runtime;` and `throw error;` in `ScheduledJobs`.

Neither is a finding and neither is a module keeping its word, so they are counted apart,
as `refusalsNotChecked` in the graph and on the page, and each is logged at DEBUG with the
reason. On the card the refusal is drawn underlined rather than in alarm ink, and its title
says the tool could not read whether the module raises it. A machine that accused a module
which kept its word would stop being read, which is worth more than the stale comments it
would catch — so both sides are printed on the card either way, and a reader can check what
the tool would not.

## What a module reaches, and what depth is

A module's **reach** is the count of distinct things it coordinates that its caller
therefore does not:

- **another module it calls** — through a field it holds, by name for a static call, or
  through a member it imported statically;
- **an adapter it drives** — an interface whose implementation Spring Data generates, which
  is how this application reaches its database;
- **a persistent record it keeps** — a type marked as an entity, a row that outlives the
  call it was written in;
- **the transaction it establishes** — `@Transactional` on the module or on one of its
  methods.

**Depth is reach over interface cost**: the behaviour a caller can set in motion per unit
of interface they have to learn. Never implementation lines over interface lines — that
measure pays a module for padding, and under it the largest file in a repository is its
deepest module. Reach cannot be inflated by writing more lines, which is exactly why it is
the numerator: it counts distinct *names*, so the same call written ten more times, or a
hundred lines of local variables around it, moves nothing. Every module carries `depth`
with both numbers it was taken from, so the division can be checked by hand.

Nothing is reached that the graph does not also hold. A name in a body is followed to a
module the way the compiler would follow it — a type the module declares inside itself
first, then the file's own single-type imports, then its package, then any on-demand
import — and a name that resolves to no module here is not counted at all. A single-type
import *binds* rather than merely wins: `import shop.Holder.Row` makes `Row` mean
`Holder.Row` in that file and nothing else, so when that names no module here the name
reaches nothing rather than falling back to a `Row` in the file's own package. A static
import binds nothing at all: `import static q.Helper.of` introduces the member `of`, never
the name `Helper`, so a later `Helper.build()` means whatever `Helper` the package and the
imports say it means. That is what
keeps every line in a fan pointing at a card on the same page, and what stops a module
raising its own score by importing more of the JDK. The nested type comes first because
Java puts it first: `Kind.of(x)` written in a module that nests a `Kind` means that one
rather than the top-level `Kind` next door, and a nested type is not a module, so the name
reaches nothing at all. A type a module *inherits* shadows the same way, since Java hands
a subclass its supertypes' member types as surely as their methods, so the modules above
one are read with it — as are its own type parameters, because `class Till<Receipt>` holds
whatever its caller filled the hole with rather than the `Receipt` next door. A name
written out in full — `new other.Receipt()`, a field
declared `private final other.Receipt receipt`, the type a static import names — means the
module of that id and no other; the package in front of it is the answer rather than
something to cut off. The transaction is the one thing reached with no module behind it,
and it says so by carrying no module id.

Building a collaborator is coordinating it, whatever it turns out to be: `new B(a)` and
`B.of(a)` are the same module reached, spelled two ways, and counting only the second made
a fan — and the leverage figure over it — turn on which spelling somebody preferred.
`new B[10]` is not one of them: it builds an array of nulls and no `B` at all, and reading
it as a construction put a record in a fan under an evidence string saying the module had
written one. Nor is a qualified `new` a call: `new Holder.Row()` spells a name, a dot, a
name and a bracket exactly the way `Holder.row()` does, and calls nothing on `Holder` — it
builds the type nested inside it, which is not a module and reaches nothing. Read as both,
the enclosing module went into the fan under `called on Holder` for a file that calls
nothing on `Holder` anywhere.

A fan is a floor on what a module coordinates, the way a bar is a floor on what a caller
must learn. A collaborator handed in as an argument rather than held as a field, and a
record this module loads and changes rather than creates, are coordination this tool
cannot see; it leaves them out rather than guessing. A record's components are held, not
handed in: they are written in its header rather than its body, and they are read as the
fields they are.

Spellings of a call go missed for the same reason, and the page names the ones known so
that the edge of the floor can be seen rather than discovered:

- a call written out in full — `io.dataroots.savingstreak.accounts.AccountsService.of(x)`
  — because the name a call is read against is the one in front of the last dot, and this
  one has a package path in front of it. "By name for a static call" above means the name
  a file can spell after its imports, `AccountsService.of(x)`;
- a call through something reached through something else — `orders.repository.save(x)` —
  for the same reason: the field is not the name in front of the last dot;
- a call to a statically imported member whose name the module's own body also declares —
  a method, a constructor, a nested record's header, an anonymous class's method, anywhere
  in the body. A declaration is written the same way a call is, and which of the two it is
  is decided by what stands in front of the name: a type means a declaration, punctuation
  an expression can follow means a call. Where the two cannot be told apart the reading is
  "declaration", because counting a declaration as a call credits a module with reaching
  something it never called and prints evidence saying so.

Each of these leaves a fan shorter than the source, never longer. That is the direction
this tool is willing to be wrong in: a number a reader can check and find understated is
worth more than one they can check and find false.

Readings that go the other way are named on the page too, beside the omissions above —
the ones known, named rather than left to be found. Each needs something this tool does not
read — which declaration was in scope where a call was written, or what a type outside this
source tree declares — and each is admitted rather than guessed at or quietly left, because
a page promising a floor while holding a reading that is not one is worse than a page with
no promise on it. There is no count in front of that list, and there should not be: what
makes the floor's edge visible is the naming, and a number in front of it is a claim about
every reading nobody has found yet.

- a call written on **a name in an inner scope that borrowed a field's name** —
  `void go(Other repo)` in a module holding a `Repo repo` — is read against the field's
  type, so the fan draws a line to the `Repo` and can miss the `Other`. A parameter, a
  local, a `catch`'s variable and a nested class's own field can each be spelled that way.
  A call is followed through a field by the name it is written against, and telling that
  name from an inner one means knowing which declaration was in scope where the call was
  written, which this reading does not track;
- an **enum constant written with arguments** — `RED(1)` — which declares a constant with
  punctuation in front of it, exactly the way a call to `RED(1)` is written. It reaches
  nothing on its own; it can only be followed when the same file statically imports a
  member of that exact spelling, and it is then read as a call to it. A module's own
  constructor and the constructors of the types it nests are held out by name, and an enum
  constant is the shape left over;
- a name **something outside this source tree declares**. A module built on a framework
  class inherits that class's member types, and this tool cannot read a type the graph
  does not hold, so a name javac binds to one of them is followed to a module of that name
  here instead. Where the same is true of a *method*, the statically imported reading is
  refused outright for that module rather than guessed at — the cost is a real static call
  going uncounted, which is the direction the rest of this list errs in.

The three rules deciding what a reached thing *is* live in `scoring.json` beside the
weights, each with the sentence it is argued for, which the page prints. Change what counts
as an adapter and the fans change with it.

Each fan is drawn against one number, `scoring.widestReach` in the graph, so that two of
them can be compared by eye. A deep module reads as a short bar over a wide fan; a module
coordinating one thing per method reads as a bar as wide as its fan.

## The deletion test

Beside each scored module the page prints a verdict on one question: would deleting this
module concentrate complexity, or merely move it to the modules that were going through
it? It is mechanical, and it is read off three counts printed with it:

- **what it reaches** — the fan above it, described in the section before this one;
- **how many methods it presents** — the ones a caller can reach, the same ones the bar
  counts;
- **how many modules go through it** — its `callers`, which every module in the graph
  carries as a count and as the list of ids behind it. A caller is a module with a line in
  its fan to this one: reaching a module is going through it, and building one is reaching
  it, so `new B(a)` makes a caller of `B` exactly as `B.of(a)` does. A module never counts
  as its own caller, because reach never names the module it was read from.

A module that coordinates no more things than the methods it presents has concentrated
nothing for deletion to remove: a caller learns one call for each thing they could have
reached themselves. When two or more modules go through such a module, deleting it moves
that coordination to them rather than removing it, and the verdict is **pass-through**. A
module that coordinates more than it presents is concentrating it, and **earns its keep**.
The third verdict is the honest one: a module that concentrates nothing and that fewer
than two modules go through has nowhere for its complexity to move to, so the test says
**no finding** rather than either of the other two.

Both thresholds live in `scoring.json` beside the weights — `reachAtMost.perMethod`,
`reachAtMost.neverBelow`, and `callersAtLeast` — with the sentence each verdict is argued
for, which the page prints. Move a number and every verdict on the page moves with it;
nothing in the analyser names a verdict or draws the line, and a test asserts that by
grepping for the words. `reachAtMost.neverBelow` is the floor that says coordinating one
thing is coordinating nothing however few methods it is presented behind — without it a
module presenting no reachable method at all would be allowed nothing and would read as
concentrating something by reaching once.

A module no rule scores is given no verdict: it was never measured, and a mechanical
judgement on something the rules declined to price would be the score they declined to
give, wearing a word. Its three counts are still reported, because they are facts about
the source rather than judgements.

A verdict is only as good as the fan it is read from, and that fan is a floor. A module
coordinating things this graph does not hold reaches nothing here and reads as
coordinating nothing, so it can be reported as a pass-through on a count that is short —
`MovableClock` is the specimen on this repository, coordinating a JDK `Clock` and an
`AtomicLong` that no rule here can see. The counts are printed beside every verdict for
exactly that reason. And a verdict is an observation about deleting a module rather than a
proposal to delete it; nothing on the page is ranked, and no module on it is proposed for
change.

On this repository the test names four pass-throughs, and one of them is the module the
specification predicted from reading before the tool existed: `AccountsService`
coordinates five things behind eleven methods with five modules going through it, while
`DepositsService` — which calls it — coordinates eight behind three and earns its keep.
Every run logs each pass-through at INFO with the three counts and the callers behind
them, so the finding does not need the page to be read.


## What is drawn but never scored

Three kinds of thing are shallow by construction, and ranking them beside the modules that
are not would bury the finding. Each is excluded by a rule `scoring.json` names, and each
excluded module is still drawn — in the graph, in its package, with its interface read and
its cost absent rather than zero, per method as well as for the module, because a score
published in parts is still a score for a module the document says has none:

- **data carriers** — a record or an enum, whose interface is its content;
- **generated repositories** — an interface extending one of Spring Data's, whose
  implementation is derived from method names at run time rather than written here;
- **the entry point** — the class marked `@SpringBootApplication`, which exists to be
  started rather than called.

## Arguments

    --source DIR     a directory of source to read (repeatable; default backend/src/main/java)
    --graph FILE     where to write the graph document
    --page FILE      where to write the page
    --scoring FILE   the weights and exclusion rules to apply (default scoring.json beside the tool)
    --log-level ...  DEBUG to see every file read, every module found and every exclusion

## Layout

    scripts/module-depth-map.py       the single command
    scripts/module_depth_map/
        cli.py                        arguments, and writing both outputs from one document
        graph.py                      source roots in, the graph document out
        javasource.py                 reading one Java file well enough to name its modules
        scoring.py                    what an interface costs, and what is never scored
        scoring.json                  the weights and rules that decide both — edit this
        page.py                       the graph document rendered as one self-contained file
        tests/                        one package per property being established
