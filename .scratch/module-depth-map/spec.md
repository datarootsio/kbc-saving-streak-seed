# Module depth map: a deterministic picture of what each module hides

Status: ready-for-agent

## Problem Statement

Saving Streak is a seed repo that participants and agents extend. Whether an extension lands well
depends almost entirely on one judgement: is the module it touches deep — a lot of behaviour behind a
small interface — or shallow, with an interface nearly as wide as what sits behind it. That judgement
is currently unavailable to anyone who has not read the whole codebase. There is no picture of the
application at all: no diagram anywhere in the repo, and nothing that says which modules are carrying
their weight.

Reading for it by hand does not scale and does not survive. `DepositsService` presents three methods
and coordinates four collaborators behind them; `AccountsService` presents eleven, two of which are
static string builders and one of which answers a question — does this savings account exist — that
its callers should not have to ask. Both facts are visible only to someone who opens both files and
holds them side by side. A participant arriving on the training day, or an agent picking up a ticket,
does neither.

The failure this causes is specific, not aesthetic. Someone extending a shallow module copies its
shape, because the surrounding code is the best available guidance about what good looks like here. A
seed repo teaches whatever its worst module models. And the two forms of payback the project cares
about — leverage for callers, locality for maintainers — are exactly the things nobody can see.

The obvious remedy makes things worse. A picture drawn by hand, or by an agent reading the code, is
one reader's opinion of the architecture rather than the architecture, and it is wrong the week after
it is drawn. What is needed is a measurement, repeatable by anyone, that produces the same answer
today and in six months given the same code.

## Solution

A deterministic command-line tool, checked into the repo, that reads the source and emits an
interactive page showing every module, what it hides, and what a caller has to learn to use it.

Run it and you get two files: a machine-readable graph of the modules and their interfaces, and a
single self-contained HTML page that renders that graph. Same input, same output, byte for byte — no
model in the loop at generation time, nothing that varies between runs or between machines. The page
opens in a browser with no server and no network.

The page's central claim is depth, and it is drawn rather than written. Each module appears as a
narrow bar — its **interface**, everything a caller must know — sitting above a fan of lines running
out to everything the module coordinates on the caller's behalf. A deep module is a short bar over a
wide fan: three parameters bought you four collaborators and a transaction. A pass-through is a bar as
wide as its fan, one line per method, coordinating nothing. The shape is the argument, and a reader
sees which modules are which before reading a single label.

Clicking a module opens what stands behind the shape: each method with what it costs a caller, each
collaborator it reaches, every refusal it can answer with, which modules call it, and a verdict from a
mechanical **deletion test** — would deleting this module concentrate complexity, or merely move it to
its callers?

Because the measure is fixed and public, it can be argued with. The scoring rules and every exclusion
live in a checked-in configuration file rather than inside the tool, so a reader who thinks a module
has been scored unfairly can point at the rule that did it.

## User Stories

1. As a developer new to this repo, I want one page showing every module and how they connect, so that I can orient myself without reading seventy files.
2. As a developer, I want to see at a glance which modules are deep and which are shallow, so that I know which code to trust as an example.
3. As a developer, I want depth drawn as a shape rather than stated as a number, so that I can read the codebase's structure faster than I can read its prose.
4. As a developer, I want to click a module and see its full interface, so that I can judge for myself whether the score is fair.
5. As a developer, I want to see which other modules call a given module, so that I can tell a widely-leveraged interface from a speculative one.
6. As a developer, I want each module's collaborators listed, so that I can see what one call actually sets in motion.
7. As a developer, I want every refusal a module can answer with shown as part of its interface, so that I know the failure modes before I call it.
8. As a developer, I want refusals shown as their own band rather than folded into the interface score, so that a module is not penalised for being explicit about how it can fail.
9. As a developer, I want the tool to tell me when a documented refusal and the code disagree, so that a stale comment is caught by a machine rather than by a customer.
10. As a developer, I want to trace a business event end to end through the modules it touches, so that I can understand a flow without stepping through it in a debugger.
11. As a developer, I want the deposit, the withdrawal-with-allocation and the reward claim available as traceable flows, so that the three things this application actually does are each explainable from the page.
12. As a developer, I want modules grouped by the package they live in, so that the picture matches the directory structure I navigate.
13. As a developer, I want data carriers and generated repository interfaces excluded from scoring, so that thirty things that are shallow by construction do not bury the real finding.
14. As a developer, I want excluded things still drawn in the graph, so that I can see the whole application rather than only the parts that were scored.
15. As a developer, I want the frontend shown alongside the backend, so that the picture is of the application rather than of half of it.
16. As a developer, I want a module that is large but presents almost nothing to be identifiable as such, so that size is never mistaken for depth.
17. As a maintainer, I want the tool to run in seconds from a single command, so that regenerating the page is never a reason not to.
18. As a maintainer, I want the same source to produce byte-identical output on every run and on every machine, so that a change in the page always means a change in the code.
19. As a maintainer, I want the output to contain no timestamps or absolute paths that change between runs, so that regenerating it produces a clean diff.
20. As a maintainer, I want the scoring rules in a configuration file rather than buried in the tool, so that I can change what counts without editing the analyser.
21. As a maintainer, I want every exclusion to be attributable to a named rule, so that "why was this ignored?" always has an answer.
22. As a maintainer, I want the tool to depend on nothing outside the Python standard library, so that it does not add a toolchain to a repo that has two already.
23. As a maintainer, I want the page to be a single self-contained file, so that it can be opened, mailed or committed without assets going missing.
24. As a maintainer, I want the tool to fail loudly when it cannot parse a file rather than silently scoring it as empty, so that a parse failure never looks like a shallow module.
25. As a maintainer, I want to know how much of the codebase the tool actually understood, so that I can judge how much weight the picture deserves.
26. As a reviewer, I want a mechanical deletion test verdict per module, so that "this is a pass-through" is a finding rather than an opinion.
27. As a reviewer, I want to regenerate the page from a branch and compare it against main, so that I can see what a change did to the shape of the codebase.
28. As a trainer, I want a page that teaches deep modules using this codebase as the example, so that the principle arrives attached to code the participants are about to edit.
29. As a trainer, I want the page to show a genuinely shallow module from our own repo, so that the lesson is not a strawman.
30. As a trainer, I want to project the page and click through it live, so that the explanation follows the questions in the room.
31. As a participant, I want to see the shape of the module I am about to extend, so that I can tell whether I am adding to a good interface or a bad one.
32. As a participant, I want to re-run the tool after my exercise, so that I can see what my change did to the module's depth.
33. As an agent picking up a ticket, I want a machine-readable graph of the modules alongside the page, so that I can locate a seam without re-deriving the architecture.
34. As an agent, I want the graph to name each module's interface and collaborators, so that I can decide where a change belongs before I open a file.
35. As an agent, I want the ranking presented as a dated observation rather than a backlog, so that I do not treat "shallow" as an instruction to start merging modules.
36. As a reader of the page, I want to be told plainly which parts of an interface the tool cannot measure, so that I do not mistake the score for the whole picture.
37. As a reader, I want the page readable in both light and dark themes, so that it is usable however I read.
38. As a reader on a laptop, I want the page usable at a normal screen width, so that I do not have to scroll sideways to see the diagram.

## Implementation Decisions

**Vocabulary.** The tool speaks the project's design vocabulary exactly: module, interface,
implementation, depth, seam, adapter, leverage, locality. It does not say "component", "service",
"API" or "boundary" in its output or its source.

**What a module is.** Anything with an interface and an implementation. Scoring runs at class grain,
because that is where interfaces actually live in this codebase, and modules are grouped visually by
their package so the package-level view comes for free. Three categories are drawn but never scored,
each excluded by a named rule: data carriers that only transport values across a seam, Spring Data
repository interfaces whose implementation is generated rather than written, and the application entry
point. Excluding them is not cosmetic — they are shallow by construction, and scoring them would bury
the finding under false positives.

**Depth is leverage, not a line-count ratio.** Depth measured as implementation lines over interface
lines is explicitly rejected: it rewards padding, and under it the frontend's single large component
would score as the deepest module in the repository. Depth is instead measured as the behaviour a
caller can exercise per unit of interface they must learn.

**Reach is the numerator.** A module's reach is the count of distinct things it coordinates that its
caller therefore does not: collaborating modules it calls, adapters it drives, persistent records it
writes, and whether it establishes a transaction. Reach cannot be inflated by writing more lines,
which is precisely why it replaces the line count.

**Interface cost is the denominator.** Everything a caller must learn, counted as: each method
reachable from outside the module, each parameter of each such method, and each distinct type that
crosses the seam in a parameter or a return. A method handing back a domain type costs more than one
handing back a primitive, because the caller has to learn the type as well as the call.

**Refusals are interface, reported separately.** A refusal a module can answer with is something the
caller must know, so it counts toward interface cost — but it is carried as its own band and shown
separately on the page, so that a module with a wide interface because it is honest about failure is
distinguishable from one that is merely wide.

**Declared refusals are cross-checked against thrown ones.** Documented refusals are read from
`@throws` and compared against what the implementation actually throws. A disagreement is reported as
a finding on the module. Prose invariants and ordering constraints are not parsed and not scored: they
are part of the interface, the tool cannot measure them, and the page says so rather than implying the
score is complete.

**The deletion test is mechanical.** For each module the tool computes reach and the number of calling
modules, and renders one of two verdicts: a module with reach of one or less and two or more callers
concentrates nothing and is reported as a pass-through, because deleting it would move complexity
rather than remove it; a module with substantial reach is reported as earning its keep. The verdict
states the numbers it was derived from, so it can be checked.

**The visual encodes leverage directly.** Each module is drawn as an interface bar whose width is its
interface cost, above a fan of lines to each thing it reaches. Deep reads as a short bar over a wide
fan; a pass-through reads as a bar as wide as its fan with one line per method. Because the shape is
built from reach rather than volume, a large implementation cannot make a module look deep.

**Flows are first-class.** Three business events are traceable on the page — a deposit, a withdrawal
and the deposits it draws down, and a reward claim — each highlighting the modules it passes through
in order. These are derived from the call graph the analyser already builds, not hand-listed.

**The frontend is drawn honestly.** It is analysed at file grain and scored by the same rules. Its
single large component is expected to score as low-leverage relative to its size, and that result is
shown rather than suppressed: it is the clearest available demonstration that depth means complexity
hidden behind a small interface, not merely a lot of code.

**Two outputs, one derived from the other.** The analyser emits a machine-readable graph document —
modules, interfaces, reach, callers, refusals, findings — and the page is a pure rendering of that
document with the data inlined. Nothing appears on the page that is not in the graph.

**Determinism is a requirement, not a hope.** Every collection is emitted in a stable sorted order,
no timestamps or absolute paths appear in the output, and the snapshot date is supplied as an explicit
argument rather than read from the system clock. Two runs over the same source produce identical
bytes.

**Configuration is checked in.** Exclusion rules, scoring weights and the flow definitions live in a
configuration file beside the tool. Changing what counts is an edit to that file, reviewable as a
diff, rather than a change to the analyser.

**Written in Python 3, standard library only,** kept beside the repo's existing scripts. This adds no
toolchain: the backend keeps Maven, the frontend keeps npm, and the analyser needs neither. Parsing is
by targeted pattern matching over the source, not a full language grammar — sufficient for the
conventional Java and TypeScript in this repo, and the reason unparseable files must be reported
rather than scored as empty.

**The page states its own limits.** It carries the snapshot date it was given, names itself an
observation rather than a backlog, and lists what was excluded and what could not be measured. This
matters because agents extend this repo: an undated ranked list of shallow modules reads as an
instruction to start merging things.

## Testing Decisions

**What makes a good test here.** The tool's interface is the graph document it emits from a directory
of source. Tests drive that seam and assert on the emitted graph — never on internal parsing helpers,
and never by scraping the rendered HTML. A test that reaches past the graph is testing the
implementation, and would have to change every time the parser is restructured.

**One seam, and it is the highest one available.** Source directory in, graph document out. The page
is a pure function of the graph, so testing the graph tests everything that matters about the page's
content; no second seam is introduced for rendering. This mirrors how the backend is already tested —
the existing suites drive the application over HTTP rather than calling services directly.

**Fixture codebases, not the real one, for scoring rules.** Small purpose-built source trees, each
containing a known shape: a deep module coordinating several collaborators behind few methods; a
pass-through with one method per collaborator; a data carrier; a generated repository interface; a
module whose documented refusals disagree with what it throws. Each fixture asserts the specific score
and verdict it was built to produce. Fixtures make the tests readable as a statement of the scoring
rules, and they do not break when the application changes.

**The real repository is tested for the properties that must hold on it,** not for specific numbers
that would make the suite fail every time a feature lands: every source file is parsed or explicitly
reported as unparseable, every module in the graph is either scored or excluded by a named rule, every
flow resolves to a real path through real modules, and every reference in the graph points at a module
the graph contains.

**Determinism is tested directly.** The analyser is run twice over the same source and the two graph
documents are compared byte for byte. This is the single most important test in the suite: it is the
property the whole approach rests on, and it is cheap to assert.

**Prior art.** The backend's API tests are the model to follow — one package per feature, named for
the behaviour rather than the class under test, driving the application from outside. The analyser's
tests follow the same naming: a package per property being established, named for the property.

**Run with the standard library's own test runner,** so the tool's tests need no dependency the tool
does not already have.

## Out of Scope

- **Any model in the generation loop.** The tool is deterministic. Interpretation of its output is a
  separate activity from producing it.
- **Enforcement.** No CI check, no pre-commit hook, no failing build when the page drifts from the
  code. The page is regenerated by running the tool; whether that is enforced is a later decision.
- **Editing the diagram.** The page renders the graph; it is not a drawing tool, and hand-edits to it
  would be destroyed by the next run.
- **Refactoring anything the tool finds.** Producing the observation is this spec's whole scope. Acting
  on it is not, and the page's framing exists to keep that line visible.
- **Prose invariants and ordering constraints as scored interface.** Acknowledged as part of an
  interface, not mechanically derivable, deliberately left unmeasured and declared as such.
- **Runtime information.** The analyser reads source. It does not trace execution, read logs, or
  measure performance.
- **Generalising beyond this repo.** Java-with-Spring and React-with-TypeScript as written here.
  Working on other repositories is not a goal, and no effort is spent on languages this repo lacks.
- **Test code, build output and dependencies.** The graph covers the application's own source.

## Further Notes

**Four decisions were taken on recommendation rather than confirmation.** The grilling session that
produced this spec reached agreement on purpose, output medium, determinism and the definition of a
module, and the developer then delegated the remainder. These four were settled by recommendation and
are worth a second look before implementation, because each is load-bearing: reach as the numerator
for leverage; refusals counted as interface cost but reported separately; class grain rather than
package grain; and `@throws` parsed while prose invariants are left unscored.

**One decision was reversed mid-design and the reversal is the reason this tool is shaped as it is.**
The first design measured depth as implementation lines over interface lines and drew each module as a
rectangle, tall for deep and flat for shallow. The project's design vocabulary rejects that framing
outright, on the grounds that it rewards padding the implementation. The rejection was load-bearing
rather than pedantic: under the line-count measure, the frontend's single 1,545-line component scores
as the deepest module in the repository, which is the exact opposite of what the page exists to teach.
Reach replaced the line count, and the fan replaced the rectangle.

**The expected first finding is already visible from reading.** `AccountsService` presents eleven
methods over a small implementation, including two static message builders and an existence check its
callers should not need. It is the specimen the page will lead with, and naming it here means the tool
can be checked against a prediction rather than merely admired.

**The repository is unusually well documented at its seams,** which is what makes the deterministic
approach viable. Refusals are recorded in `@throws`, and module javadoc states ordering constraints in
prose. The tool exploits the structured half and declines to guess at the rest.
