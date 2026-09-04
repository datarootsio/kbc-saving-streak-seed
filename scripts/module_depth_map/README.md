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
  beside this file holds the interface-cost weights and every exclusion rule. Change a
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
  to `module-depth-map/3` when every module gained a `reach` and a `depth`. The
  page checks it before drawing, and says so rather than drawing half a document, because
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

Each module is drawn as a bar whose width is what its interface costs a caller, over a fan
with one line out to each thing it coordinates on that caller's behalf. Nothing is ranked,
and nothing is proposed for change.

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
module the way the compiler would follow it — through the file's own single-type imports
first, then its package, then any on-demand import — and a name that resolves to no module
here is not counted at all. That is what keeps every line in a fan pointing at a card on
the same page, and what stops a module raising its own score by importing more of the JDK.
The transaction is the one thing reached with no module behind it, and it says so by
carrying no module id.

A fan is a floor on what a module coordinates, the way a bar is a floor on what a caller
must learn. A collaborator handed in as an argument rather than held as a field, and a
record this module loads and changes rather than creates, are coordination this tool
cannot see; it leaves them out rather than guessing.

The three rules deciding what a reached thing *is* live in `scoring.json` beside the
weights, each with the sentence it is argued for, which the page prints. Change what counts
as an adapter and the fans change with it.

Each fan is drawn against one number, `scoring.widestReach` in the graph, so that two of
them can be compared by eye. A deep module reads as a short bar over a wide fan; a module
coordinating one thing per method reads as a bar as wide as its fan.

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
