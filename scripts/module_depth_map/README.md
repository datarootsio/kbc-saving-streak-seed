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
- **It refuses rather than guessing.** No source directory (exit 2) and two files declaring
  the same module id (exit 3) both stop the run with the reason logged as a warning, because
  a page that drew one of those modules twice and the other not at all would be worse than
  no page. Nothing is written on either path, and the graph and the page are always written
  together or not at all.
- **The committed outputs are the ones this source produces.** `docs/module-depth-map.json`
  and `docs/module-depth-map.html` are checked in, and a test byte-compares them against a
  fresh run, so adding a Java class without rerunning the tool fails the suite instead of
  leaving a stale page for a reader to trust.

## What is on it so far

Every top-level type in the backend source, at class grain, grouped by its package. Types
declared inside another are listed on the module that holds them rather than becoming
modules of their own, each named by where it sits inside that module — `Body.Kind`, not a
second `Kind` a reader cannot tell from the first. Nothing is scored, ranked or judged yet.

## Arguments

    --source DIR     a directory of source to read (repeatable; default backend/src/main/java)
    --graph FILE     where to write the graph document
    --page FILE      where to write the page
    --log-level ...  DEBUG to see every file read and every module found

## Layout

    scripts/module-depth-map.py       the single command
    scripts/module_depth_map/
        cli.py                        arguments, and writing both outputs from one document
        graph.py                      source roots in, the graph document out
        javasource.py                 reading one Java file well enough to name its modules
        page.py                       the graph document rendered as one self-contained file
        tests/                        one package per property being established
