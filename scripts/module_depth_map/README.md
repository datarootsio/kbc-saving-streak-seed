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
    `interface` and `enum` against the declarations actually found.

  A directory that will not open is named the same way, because the modules under it would
  otherwise be missing while every count still added up. One bad file costs one card; both
  outputs are still written. `package-info.java` and `module-info.java` declare no type on
  purpose and are read rather than reported, and legal Java is never failed — an escaped
  `\"""` inside a text block is a quote, not the end of it.
- **The rules that score a module live in a file, not in the analyser.** `scoring.json`
  beside this file holds the interface-cost weights and every exclusion rule. Change a
  weight or a rule there, run the tool again, and the output moves; nothing in the analyser
  is edited, and no rule name or weight is written into it to fall back on.
- **Nothing is excluded without a named rule.** Each excluded module carries the rule that
  excluded it and the fact about the module that matched, so "why was this ignored?" always
  has an answer a reader can point at and argue with.
- **It refuses rather than guessing.** No source directory (exit 2), two files declaring
  the same module id (exit 3), and a scoring configuration this tool cannot use (exit 4)
  all stop the run with the reason logged as a warning — because a page that drew one of
  two clashing modules twice, or that scored with weights nobody wrote, would be worse than
  no page. Nothing is written on any of those paths, and the graph and the page are always
  written together or not at all: both are rendered to bytes, written beside where they
  belong, and moved into place.
- **The committed outputs are the ones this source produces.** `docs/module-depth-map.json`
  and `docs/module-depth-map.html` are checked in, and a test byte-compares them against a
  fresh run, so adding a Java class without rerunning the tool fails the suite instead of
  leaving a stale page for a reader to trust.

## What is on it so far

Every top-level type in the backend source, at class grain, grouped by its package. Types
declared inside another are listed on the module that holds them rather than becoming
modules of their own, each named by where it sits inside that module — `Body.Kind`, not a
second `Kind` a reader cannot tell from the first.

Each module carries a bar whose width is what its interface costs a caller. Nothing is
ranked, and nothing is proposed for change.

## What an interface costs

Everything a caller has to learn before they can use a module correctly:

- **each method they can reach.** Reachable is `reachableFromOutside` in the configuration:
  public, protected and package-private here, because a method the neighbours can call is a
  method somebody has to learn. A constructor is left out — it says how a module is built,
  which in this application is the framework's business rather than a caller's.
- **each parameter of each of those methods.** `Map<String, Long>` is one parameter, and
  `String...` hands over a `String`.
- **each distinct type crossing the seam** in a parameter or a return, counted once per
  module however many methods hand it over, and weighted apart depending on whether it is
  one every Java caller already knows or one this application invented. That is what makes
  a method handing back a domain type cost more than one handing back a primitive.
  `List<Optional<Customer>>` is three types; `java.util.List` and `List` are one; a type
  variable — the `T` in `<T> T first(List<T> of)` — is a hole the caller fills rather than
  a type anybody learns, and is not counted.

Every bar is drawn against one number, `scoring.widestInterface` in the graph, so two of
them can be compared by eye.

Prose invariants, ordering constraints and the bound on a type variable are part of an
interface and are deliberately not measured. A bar is therefore a floor on what a caller
must learn rather than the whole of it, and the page says as much rather than letting the
number read as complete.

## What is drawn but never scored

Three kinds of thing are shallow by construction, and ranking them beside the modules that
are not would bury the finding. Each is excluded by a rule `scoring.json` names, and each
excluded module is still drawn — in the graph, in its package, with its interface read and
its cost absent rather than zero:

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
