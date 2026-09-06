# Module depth map

A deterministic picture of what this application is made of — both halves of it. One
command reads the source, backend and frontend together, and writes two files: a graph
document naming every module, and a self-contained page that is a pure rendering of that
document.

Run it from the repository root:

    python3 scripts/module-depth-map.py

It reads `backend/src/main/java` and `frontend/src`, and writes
`docs/module-depth-map.json` and `docs/module-depth-map.html`. Open the HTML file
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
  matching rather than a full grammar, on both sides. Anything it cannot make sense of is
  logged as a warning with the reason, counted in the graph, and shown on the page, because
  a parse failure that looked like an empty module would be indistinguishable from a real
  finding. Silence has to mean nothing was missed, so a file is failed by name whenever the
  parser can tell it has stopped reading what the compiler would read. On the Java side:

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

  The TypeScript side is failed on the same shapes, spelled its own way, and the section
  on the frontend below names each of them: braces that do not balance, a block comment or
  a template literal or a regular expression that is never closed, an `export` it has no
  reading of, a parameter with no type written on it, and a return type it cannot tell from
  the body that follows it.

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
  reached thing an adapter or a record, where the deletion test draws its line, what each
  disagreement between a documented refusal and a raised one is called, and what under a
  source root is not the application's own source and is never read. Change a
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
  misspelling. That one is written **per language** — `{"java": [...], "typescript":
  [...]}` — because what a caller already knows is a fact about the language they are
  calling from: `string` is free to a TypeScript caller and is not a name a Java one ever
  meets. Every language this tool reads has to be named and nothing else may be, so a
  missing list is a refusal rather than a language quietly charged for every type it
  meets.
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
  `costWithoutRefusals` the total is split into, and to `module-depth-map/6` when the
  document grew a top-level `flows` list — the business events the configuration asks to
  be traced, each with the path walked for it or the reason there is none — and to
  `module-depth-map/7` when that walk became a walk of the calls the entry method makes
  rather than of the entry module's reach, so every step gained the `calledFrom` and the
  `call` that put the flow there, and to `module-depth-map/8` when the document stopped
  being a document about the backend: `source` gained the `languages` it was read in and
  the paths a named rule declined to read at all, `scoring` gained the `largest` module and
  the numbers it was measured at, and a module's `language` became a thing a reader has to
  look at rather than a constant, and to `module-depth-map/9` when the two facts a page of
  two languages cannot render without went in: `source.readAt` says, for each language
  read, what a module of it is and why — in that language's own words, so the page
  describes this run rather than this tool — and `scoring.typesEveryCallerAlreadyKnows`
  became one list per language.
  The page checks it before drawing, and says so rather than drawing half a document, because
  reaching into a shape that is not there throws in the middle of one pass and reads as a
  page that ended early.
- **The committed outputs are the ones this source produces.** `docs/module-depth-map.json`
  and `docs/module-depth-map.html` are checked in, and a test byte-compares them against a
  fresh run, so adding a Java class without rerunning the tool fails the suite instead of
  leaving a stale page for a reader to trust.

## What is on it so far

Every top-level type in the backend source, at class grain, grouped by its package, and
every file of the frontend source, at file grain, grouped by the directory it sits in.
Types declared inside another are listed on the module that holds them rather than
becoming modules of their own, each named by where it sits inside that module —
`Body.Kind`, not a second `Kind` a reader cannot tell from the first.

Each module is drawn as a bar whose width is what its interface costs a caller — in two
bands, the refusals it can answer with and everything else — over a fan with one line out
to each thing it coordinates on that caller's behalf, and under both a verdict on what
deleting it would do — read off the fan, the bar and the number of modules
that go through it. Clicking a module opens everything standing behind its shape, which
is what the rest of this file is about. Above the cards, three flows — a deposit, a
withdrawal with the deposits it draws down, and a reward claimed — each highlight the
modules that business event passes through, in order. Nothing is ranked, and nothing is
proposed for change.

## The frontend, at the grain its interfaces are written at

A module is anything with an interface and an implementation, and where that sits is a
fact about the language rather than a preference. A Java module is a class, because that
is where a Java interface is written. A **TypeScript module is a file**, because that is
what an import names: whoever writes `import { fetchDeposits } from './api'` gets whatever
that file exports, and nothing else in it is reachable from anywhere. So the file is the
thing with an interface and an implementation, its card says `file` where a Java card says
`class`, and its id is the path it sits at — `frontend/src/App` — which is the same string
an import next door resolves to. A fan line is then checkable against an import statement
rather than against this tool's opinion of one.

Everything after the grain is the same measure. What a caller must learn is what the file
exports; what the file coordinates is what it imports and then uses; depth is the second
over the first; the deletion test runs on it; the refusal band is read from its JSDoc and
its throws. There is no rule anywhere that asks which language a module is written in
before scoring it, and `tests/thefrontendhonestly` writes one shape in both languages and
asserts the two come out at the same numbers, because "the frontend is measured the same
way" is worth nothing if only the frontend is checked.

What a TypeScript file offers a caller is read from `export` and from nothing else:

- **an exported function** — `export function`, `export async function`, `export default
  function`, and an arrow or function expression bound to an exported `const` — is a
  method, under the name the source gives it.
- **its parameters** are the types written on them. A destructured parameter is one
  parameter however many names the caller's object is taken apart into, and `this` is a
  receiver rather than something a caller passes.
- **a type it declares** — `type`, `interface`, `enum`, and an exported `class` — is named
  on the module and not measured, which is the same answer the Java side gives a type
  declared inside a module. Its members are read nowhere.
- **an exported value that is not a function** is named nowhere and priced nowhere, which
  is the same answer the Java side gives a public field. A bracket after the `=` is asked
  whether an arrow follows it before it is read as a parameter list, so `export const
  total = (1 + 2)` is a value rather than a function of a parameter called `1 + 2`. So is
  a re-export, whose signature is written in the file it came from, and so is
  `export default` of an expression — `connect(App)`, `42`, `<div />` — where what it
  costs a caller is whatever the expression evaluates to and nothing here evaluates it.
  All of them are logged: run with `--log-level DEBUG` and
  `grep "export not read as a method"` for every one, with its line and the reason.
- **a type variable a generic introduces** is a hole the caller fills with a type they
  already hold, so `export function first<T>(items: T[]): T` charges for the method and
  the parameter and for no type called `T` — the same answer the Java side gives
  `<T> T first(List<T> of)`. What the variable is bounded by is written where the caller's
  own type goes rather than across the seam, and is charged in neither language.

What it coordinates is read from the imports and the body together. TypeScript has no
package scope at all, so an import is the *only* way one file names another: a name means
what an import bound it to, or the file declares it, or it is a global. That makes the
reading shorter than Java's rather than longer — there is nothing to fall back to and so
nothing to guess at. Three spellings reach a module:

- **a name the file imports and then calls** — `fetchDeposits(id)`, and
  `useState<Customer>(null)` with its type arguments written out;
- **a name it imports and then builds** — `new SignInFailed(...)`;
- **a component it writes as a JSX element** — `<Card />`, which is what React compiles to
  a call building a `Card` and is coordination exactly as `new` is. Only a capitalised
  name can be one, which is React's own rule and the whole of what tells `<div>` from
  `<Deposits>`, and only in a `.tsx` file, because `<X>` in a `.ts` file is a type argument
  list and never a tag.

A dependency reaches nothing: `react` is somebody else's source, this graph does not hold
it, and a module cannot raise its own reach by installing more packages any more than a
Java module can by importing more of the JDK.

Two readings on this side are looser than the Java side's, and both are named on the page
rather than left to be found. **JSX text is read as source, and only for what it can
add**: a `.tsx` file is prose and code inside the same braces, and telling them apart
needs a JSX parser whose mistakes would blank real code rather than merely add to a fan —
so a word in a paragraph with a bracket after it can be read as a call, and it draws a
line only where the file also imports something of exactly that name. What is *not* read
out of prose is every statement: an `import`, an `export` and a declaration are statements,
TypeScript cannot write one inside JSX text, and the three sweeps that look for those words
step over a run of it. Nothing is blanked to do that — a run of prose missed is read
exactly as before, and a run of code wrongly taken for prose costs a sweep its reading
rather than costing the file its place on the page — so the mistake a JSX parser would
make is not one this can make. Without it, the lowercase word `import` in one sentence of
visible copy (`<p>You can import your statements here.</p>`, which `tsc --strict` compiles
without a word) failed the whole file for "an import that names no module", and the word
`export` in top-level JSX did the same to an entry point.

And **a quote in prose is not a string**: a `'`
opens a string only where a matching one follows on the same line and only where it is not
written against the end of a word, because JavaScript strings do not span lines, nothing
JavaScript compiles puts a string against an identifier, and JSX prose is full of
apostrophes. Neither half can be wrong about legal source. Without the first, one
`account's` in a paragraph blanked out the rest of the file and took every module after it
along; without the second, the same apostrophe on a line that also carried a real string
paired with that string's opening quote and blanked the code between them.

A file this reading cannot make sense of is failed by name, exactly as a Java one is:
braces that do not balance, a block comment or a template literal or a regular expression
that is never closed, an `export` it has no reading of — the one reserved word that must
open something readable, since it is the whole of what a caller can reach — a parameter
with no type written on it, and a return type that cannot be told from the body after it.
The last two are where a misread shape would leave an interface *cheaper* than the source
makes it, and a cheap interface over a fan is what this page calls deep.

And legal TypeScript is never failed, which matters more here than on the Java side: a
failed file is a whole module off the page and every fan line into it gone with it, while
a module reaching nothing is exactly what a shallow module looks like. That is a rule this
reading is held to rather than anything it can prove about itself — the parsing here is
targeted pattern matching over the source, not a grammar — so it is kept the only way it
can be. Every shape found to break it is written down as a fixture, and every one of them
so far was found by somebody reading the source and trying it rather than by the suite,
which is why each is a test and not only a fix. Every shape this reading used to refuse,
or read wrongly, on source `tsc` compiles without a word is a fixture in
`tests/thefrontendhonestly` now:

- a **destructured parameter** — `function Banking({ customer, onSignOut }: Props)`, which
  is how all forty of this frontend's components take their props;
- **`export default`** followed by an arrow (read as a method called `default`), or by a
  call, a literal or an element (named and skipped);
- a **`const` initialised with a bracket group** that is not an arrow — `(1 + 2)`;
- a **JSDoc `@throws` above a `const`-bound arrow**, which was dropped, so the module was
  then reported for raising a refusal it had documented;
- a **generic function's own type variables**, charged as types a caller had to learn, and
  those a **function type written into a signature** opens for itself —
  `pick: <T>(items: T[]) => T`;
- a **return type written as an object literal**, read as the empty string;
- a **return type written as a function type** — `(): [boolean, () => void]`,
  `(): Promise<() => void>`, `(): () => void` — where the `>` of the `=>` was counted as a
  bracket closing. The same miscount collapsed a **parameter list around an arrow inside a
  generic**, so `ring(m: Map<string, () => string>, n: number)` came back as one parameter
  rather than two, and swallowed the front of a body into a **return type written on the
  same line as it**, which then charged a caller for learning a type called `return`;
- a **side-effect import that is not the last one** — `import './index.css'` third of the
  stock Vite four, with no semicolons — whose clause ran over the newline and took the
  statement under it;
- an **apostrophe in JSX prose on a line that also carries a string**, which paired with
  the string's opening quote and blanked the code between them, braces included;
- **`export abstract class`**, whose body is right there in the file;
- **every declarator after the first** of an `export const a = 1, b = () => {}`;
- a **parameter typed by the binding above it** — `const ring: Ring = (a) => a`, where
  TypeScript reads the parameter's type off the annotation;
- a **type predicate** — `asserts x is number` — where the parameter's own name was read
  as a type to learn;
- a **self-closing JSX tag carrying a prop expression** — `<li key={i} />` inside a
  `.map()`, which is the single most common line in React — where the `}` closing the
  container let the following `/` open a regular expression, and the scan ran to the next
  slash on the line and blanked the brace that closed the enclosing `{`. `<span>{done} /
  {total}</span>` went the same way;
- a **`<` written inside a declarator's value** — `export const flag = 1 < 2, b = (x:
  number): number => x` — counted as a bracket nothing closed, so the comma separating the
  two declarators was never seen and `b` was left out with no line in any log saying so;
- a **written type wrapped onto a second line** — `const held: Till |\n  Receipt = ...` —
  recorded as `Till |`, so a call through it reached one of the two names the source wrote
  and the card printed a type that is not one;
- a **string literal type** — `a: 'one' | 'two'` — printed as `' ' | ' '`, which is the
  masked text rather than the source's;
- **one type spelled two ways** — `string|null` and `string | null`, the union half of the
  rule that had already closed up `Record<string,number>`;
- an **overload set**, charged for the implementation signature TypeScript never lets a
  caller call;
- a **`type` or `class` exported with something other than a name after the keyword** —
  `export type { Deposit } from './api'`, `export type * from './other'`,
  `export default class { }` — where the next word was recorded as a type this module
  declares, so the card named a type called `{` that a reader can go looking for and will
  never find. `function* load()`'s star was offered to every keyword, which is where the
  `from` in the second one came from;
- a **`<` written against a value in a `.tsx` file** — `if (0 < Max && n > 1)` against an
  imported `Max` — read as an element being built, because a digit is not a name and `0`
  is not a closing bracket;
- **`{ id: number }` beside `{id: number}`**, and `(a: A) => B` beside `(a: A)=>B`, left
  standing as two types apiece, while `{ [k: string]: number }` was printed with its index
  signature closed up against one brace and not the other — a spelling in neither the
  source nor any normal form;
- **`async` read as a call**, in `export const go = async () => {}`, which draws a fan
  line in any file that also imports something of that name;
- a **`new Thing` written with no brackets**, whose span ran on to the next call's and
  reversed the order a flow walks;
- a **wrapped type argument list in a declaration list** — `new Map<\n  string,\n
  number\n>(), q = ...` — whose comma was read as a declarator separator, so the declined
  log named an export called `number` the file does not contain;
- the words **`import` and `export` written as English prose inside JSX** — `<p>You can
  import your statements here.</p>` in a component, and `<main>Use the export button</main>`
  at the top level of an entry point — each of which failed its whole file for a statement
  the source does not hold;
- a **type-only default import** — `import type Customer from './api'` — recorded as a type
  this module declares, which also took the fan line off a value import of the same name;
- a **JSDoc `@throws` above a declaration with no value** — a block over `let pending`,
  which landed on the next `export function` and found that module documenting a refusal it
  never raises;
- an **exported arrow whose written type carries a generic default** — `export const ring:
  <T = string>(a: T) => T = (a) => a` — where the `=` inside the `<>` was taken for the
  assignment, so a function a caller can import left the interface entirely;
- a **concise arrow's body** — `export const load = () => get('x')` — read as no body at
  all, so a flow walked out of the method stopped one module short of where the fan drawn
  off the module said it went.

Three exports are still failed by name on purpose, and they are the whole of the exception
to the sentence above: `export declare`, `export namespace` and `export module`, the last
being the older spelling of the second. What any of them describes is not a signature this
reading can price — one says the implementation lives somewhere this tool was not pointed
at, and the other two open a container of declarations rather than being one — so naming
the file and stopping is the honest answer, where guessing would put a made-up interface
on the page. All three are `.d.ts`-shaped writing, and `.d.ts` is on the list of paths
never read at all. `export =` and `import x = require(...)` are failed too, and neither is
legal here: `tsc` refuses both against this repository's own configuration, because they
are CommonJS and this frontend is ECMAScript modules.

Readings that were wrong in the other direction are here too, because a fan line whose
evidence a reader can check and find false is worse than a fan line missing:

- `new api.Thing()` writes exactly the characters a call on `api` writes, and drew a fan
  line whose evidence read "called on api" over a file that calls nothing on it. The Java
  reading declines that spelling and calls it the one failure it exists to make
  impossible; this one is now built the same way. `<Icons.Chevron />` is the same shape
  written as an element and gets the same answer — the name read is `Icons.Chevron`, which
  names no module here, so it reaches nothing — and that floor is named on the page beside
  the other floors rather than left where a reader checking a fan against the source would
  find a gap nobody accounted for;
- `new SignInFailed()` on a *named* import went the same way one level along: the name has
  brackets after it and nothing but `new` in front, so it read as a bare call as well as a
  construction, and the fan said "calls SignInFailed, which this file imports from it"
  about a file that calls nothing. The construction reading already reports it, and says
  "builds one", which is what the source does;
- a **member signature in a type declaration** is not a call. `export interface Api { save
  (id: string): void }` in a file that also writes `import { save } from './repo'` drew a
  fan line to `./repo` over a file whose truthful reach is nothing at all — invented
  outright rather than misattributed. What is written after the brackets settles it, the
  way what is written in front of them settles it in Java: a declaration says what it
  hands back, or opens a body, and a call says neither;
- a **reserved word is not a receiver**. A construct this reading masks leaves the
  characters it stood for blank, so `return /x/.test(s)` read as `return`, a gap and
  `.test(`, and reported a call on a collaborator called `return`.

And a module reaching nothing because of *what it is called* was worse than either. A
module's own name is a binding inside its own source in Java — a class's name is in scope
in the file that declares it — and is nothing of the sort in TypeScript, where the name is
the basename of the file. Read the Java way, a file called `format.ts` was taken to
declare `format`, its own `import { format } from './util'` was never followed, and it
reached nothing at all while a byte-identical file under any other name reached `util`.
Each language now says which it is, in `THE_NAME_IS_A_BINDING`.

Every one was found by review rather than by the suite, which is why each is written down
as a test rather than only fixed: an alarm that cries wolf stops being read, and a page
that accused a module which kept its word would stop being read with it.

Seven helpers are one function read by both sides rather than two — `after_balanced` and
`in_evaluation_order`, which always were, and `split_on_commas`, `spans_between_commas`,
`normalised`, `line_of` and `ends_an_arrow`, which are now. Matching a bracket to its partner, evaluating a call's
arguments before the call, splitting a list on the commas that separate it, spelling a
type, counting a line, and knowing that the `>` of an `=>` closes nothing are facts about
punctuation rather than about either language, and both readings have to answer every one
of them the same way or "measured by the same rules" is not true of the page. Three of
them were hand-copied here once; the copies drifted, and two of the misreadings above are
what the drift cost.

## What a line count is worth here

Nothing, and the page says so with a section of its own, because the one thing it can be
misread as saying is that a big module is a deep one. `scoring.largest` in the graph names
the longest module in the document and prints the numbers it was measured at beside its
line count — read off the module's own entry, so the page states them rather than working
anything out.

On this repository that is `frontend/src/App`: 1,545 lines, one export, one thing
coordinated. It is drawn as the narrowest bar over one of the narrowest fans on the page,
and it is the clearest demonstration available of what the whole page is for. Under a
measure of implementation lines over interface lines it would be the deepest module in the
repository; under this one its length went into nothing at all. That result is shown
rather than smoothed over: drawing the frontend as one tidy box instead would be a picture
that lies by omission.

`frontend/src/api` is the other half of the same lesson, in the other direction: 272 lines
presenting eleven methods over seven domain types, which is the widest bar on the page,
and coordinating nothing this graph holds.

## What is not read at all

`sourcesNotRead` in `scoring.json` names what is under a source root and is not the
application's own source, and it is a rule with a sentence behind it like every other
exclusion here. Test code is written to check a module rather than to be one; build output
is derived from source that has already been read; an installed dependency is somebody
else's source, which nobody in this repository can act on and which arrives by the tens of
thousands of files. Each would put modules on the page that nobody here wrote.

A **directory** is matched by its own name and never walked into — `node_modules`, `dist`,
`build`, `target`, `coverage`, `__tests__` — which is what keeps a `node_modules` to one
line in the graph instead of everything inside it. A source root's own name is matched the
same way, so `--source frontend/node_modules` is one declined path and no modules rather
than twenty-six dependency modules scored and drawn as this application's own source. Every name on that list is one nothing
but a tool ever writes; a bare `test` or `tests` is deliberately not among them, because
that is a legal Java package name and a rule that quietly took a package of the
application's own source off the page would be worse than one that misses a directory
somebody can add to the list. A **file** is
matched on what its name ends with: `.test.ts`, `.spec.tsx`, `.d.ts`, `Test.java`,
`Tests.java`, `IT.java`. Every path skipped is recorded in `source.notRead` with the rule
and the fact that matched it, logged at INFO, and printed on the page — apart from the
files that could not be *parsed*, because "this is not the application's source" and "this
file would not read" are different findings and painting them the same colour would have a
`node_modules` reading as an alarm.

Where the tool is pointed matters more than the rule does, and both defaults are chosen so
that nothing has to be skipped: `backend/src/main/java` is not `src/test/java`, and
`frontend/src` is not `frontend/node_modules`. On this repository the rule matches nothing,
and the page says so in as many words.

A default that is not there is skipped rather than refused for, and is named in
`source.rootsNotRead`, logged at INFO and printed on the page above the rule. A directory
an operator *typed* is refused for, as it always was: they said to read it, and a page
drawn from what was left answers a question nobody asked. A default is a directory this
tool chose, and refusing for one meant a repository with only a backend in it got neither
output written over a frontend nobody said was there. Every default missing is still a
refusal, because then there is no source at all.

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
  `typesEveryCallerAlreadyKnows` for the language the module is written in counts
  `typeEveryCallerAlreadyKnows`, and every other type counts `typeToLearn`. That is what makes a method handing back a domain type cost more
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
`typesEveryCallerAlreadyKnows` the way a type crossing the seam is. Those lists are about
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


## Behind the shape

The shape on a card makes a claim. Clicking the card — or tabbing to the module's name and
pressing Enter — opens the panel where a reader checks it, holding everything the graph
document has about that one module:

- **where it lives**: its kind, its language, its package, the path it was read from and
  how many lines that file has;
- **what it costs a caller**: the interface cost, split into what a caller must learn
  besides the refusals and the refusals themselves, and the reach it is read against —
  then every method a caller can reach, written as the source writes it, with the cost the
  document gave that one method, and any refusal the method documents; then every type
  crossing the seam, each marked as one to learn or one every caller already knows; then
  every refusal the module can answer with, with both sides of it;
- **what it coordinates on a caller's behalf**: one line per thing reached, with its kind
  in the same ink the fan draws it in, the sentence saying how it was read, and the id of
  the module it points at;
- **which modules go through it**: every caller by id;
- **the deletion test**: the verdict, the three counts it was read off, and the rule it
  was argued for with;
- **findings against it**: each disagreement between a documented refusal and a raised
  one, with both sides and the reason — or, in as many words, that there are none.

A module no rule scores gets the rule that excluded it and the fact that matched, in place
of every number: no cost, no leverage, no verdict, and no split of its types into ones to
learn and ones a caller already knows. Even a zero there would be the score the rules
declined to give, wearing a number.

**Nothing in the panel is worked out while the page is drawn.** Every value in it is read
straight off the document the page carries — a method's own `cost`, the verdict's own
three counts, `reach.count`, `callers.count`, `deletionTest.methods` — so what a reader
sees and what a later tool reads out of `module-depth-map.json` are the same facts rather
than two measurements that agree today. Two tests hold that: one resolves every `module.`
path the panel reads against a real document and fails on a key the document does not
have, because a misspelled key is not an error in a browser but the word `undefined`; the
other refuses every use of `.length` in the panel except asking whether a list is empty,
which is how counting gets into a renderer that promised not to count.

The panel is a `<dialog>` opened with `showModal`, so Escape, the focus that goes into it
and the focus that comes back out are the browser's own rather than this page's. The card
takes the focus before the panel opens, because that is the note the browser makes about
where to hand focus back to: without it, a click on the body of a card focuses nothing,
and closing the panel drops the keyboard on a hidden element.

A few smaller things about the panel that are easier to get wrong than to notice:

- **It opens at the top, every time.** The scrolling body outlives every open — it is
  emptied and refilled rather than rebuilt — and a browser keeps the scroll offset of an
  element it has hidden, so the place a reader left one module's panel would otherwise be
  where the next module's panel opens. The heading is sticky, so nothing about that looks
  wrong: a reader would be reading the second module's findings believing they were at the
  top of its interface. `openBehind` sets `scrollTop` back to 0 once the panel is open,
  because an element with no layout box has no scroll position to move.
- **A click on the backdrop closes it; a drag that merely ends there does not.** A click's
  target is the nearest common ancestor of where the pointer went down and where it came
  up, so a selection dragged out of the panel and released beside it reports the dialog
  itself — and this panel is full of caller ids and method signatures a reader will want
  to copy. The press and the release are read separately, and both have to have landed on
  the backdrop, which is the same rule the browser's own light dismiss uses.
- **A click on a card opens the panel; a drag across a card's text does not.** The same
  problem one element up, and worse there: a card is text a reader wants — module names,
  the cost line, `pass-through — coordinates 5 things behind 11 methods` — and the whole
  card is the control, so a drag across it reported the card and opened a modal over the
  page, taking the half-made selection with it, because opening the panel moves the focus
  and a focus move collapses a selection. The card's two ends are both inside itself, so
  the rule is where the press and the release landed rather than what they landed on: a
  release more than a few pixels from the press is a drag, and so is one that left text
  selected inside the card however short it was. The press arms and the click decides,
  rather than the opening moving to `mouseup` the way the dismissal did, because the
  keyboard's Enter on the name button is a click with no mouse events behind it at all —
  it is told apart by `detail`, which counts the pointer clicks that raised the event and
  is 0 for that one.
- **Taking a whole word out of a card is a double-click, and no single click can see
  one.** When the first of the two arrives nothing has been selected, the pointer has not
  moved and `detail` is 1, so every rule above says "a click" — and opening there put a
  modal over the reader double-clicking a module name: the second click landed on
  whatever the panel had just put under the pointer, selecting the panel's own text, or,
  for a card in an outer column, on the backdrop, which dismissed the panel again. Neither
  the word nor the panel. So a click *asks* for the opening and the opening waits out the
  half-second a second click has to arrive in, and the second press calls it off — half a
  beat of delay before a card opens, against the browser's own way of taking a word off a
  page not working on this one. The keyboard's Enter is not a gesture that can grow and
  opens straight away.
- **Nothing that asks for a context menu opens or closes anything.** `mousedown` and
  `mouseup` fire for every button on the mouse, so without a guard a right-press beside
  the panel dismissed it and the context menu the reader asked for opened over a page the
  panel had just left — while a right-click *inside* the panel, which the browser handles
  itself, correctly left it open. The button is only the visible half of that rule: on
  macOS the same request is Ctrl+click, delivered as the primary button with `ctrlKey`
  set, so `button` alone let it straight through and the two ends disagreed again. Both
  ends read one `opensAContextMenu`.
- **A cost of 0 is caveated wherever it is printed.** The card and the panel say
  "nothing this bar counts, which is not the same as nothing to learn" from one string,
  because the panel is where a reader goes to check the card and so is the last place that
  should make the stronger claim.
- **The panel says so when two numbers in it disagree.** The card warns when the depth it
  draws was taken over some other reach than its fan, and when a verdict was read off some
  other reach or some other number of callers than the card was drawn from. The panel
  carries both guards, because it prints the depth's reach in one section and lists the
  reach itself in the next, and prints the verdict's three counts under both — and a
  reader who opened the panel to check the card has nowhere further to open. Both numbers
  in each guard are the document's own; nothing is counted to make the comparison.

Where there is no modal dialog to open — a browser old enough that `<dialog>` is an unknown
element — the panel falls back to setting and clearing the `open` attribute, and the
stylesheet carries both halves of the rule a browser knowing `<dialog>` would have supplied
itself: `dialog.behind { display: block; }` and `dialog.behind:not([open]) { display:
none; }`. Without the second the fallback has nothing tying the attribute to whether the
panel is on the page, and the empty shell would sit in the page from load with no close
ever taking it away; without the first an unknown element falls back to `display: inline`,
and the panel opens as a run of inline text spliced into the page flow with its own width
inert. Where `<dialog>` is known the pair says what the browser was going to say anyway —
the closed rule is the more specific of the two, so it still wins.

## Flows through the modules

Three things this application actually does, each traceable across the modules it passes
through: a deposit, a withdrawal and the deposits it draws down, and a reward claimed.
Choosing one on the page highlights every module on it and numbers it in the order the
flow enters it, while every other module fades — so what one business event sets in motion
can be told apart from what it does not, without stepping through it in a debugger.
Clearing the choice puts every module back on an equal footing.

**A flow is its entry point, and nothing else.** Each one is defined in `scoring.json` by
the single call a caller makes to enter it — one module, written in full, and one method
on it a caller can reach:

    {
      "flow": "a deposit",
      "because": "...",
      "entryPoint": {
        "module": "io.dataroots.savingstreak.deposits.DepositsService",
        "method": "deposit"
      }
    }

The method is part of the entry point rather than decoration on it. All three of these
events are reachable through modules a single caller holds, so a flow named by its module
alone would be a claim about everything that module does — and a method renamed out from
under a flow is exactly the drift a flow should fail on rather than quietly survive.

**The path is walked, never written down, and it is walked at method grain.** From the
entry point the tool reads the calls that method's body makes, in the order Java evaluates
them, and follows each one into the body it names — then the calls *that* body makes, and
so on until it runs out of bodies this source holds. A call on the module's own method is
followed and is not a step of its own, because the flow is already in that module:
`DepositsService.deposit` does all three of its refusals through private helpers, and a
walk that read only the public method would report a deposit as reaching nothing at all. A
module already entered is not entered again — the graph has cycles in it, and a flow is a
path through modules rather than a transcript of calls — so it keeps the step it was first
entered at, while the *method* is followed anyway, since `pairingFor` and `withdrawFrom` on
one module go different places. Every module a flow names is a module the graph contains,
by construction rather than by care.

The grain is the whole of it. Walked over the fans instead — which is how this was first
written, and it was wrong — a flow is its entry module's entire transitive reach:
`DepositsService.deposit` never goes near the customers table, but
`AccountsService.accountsOf` does, so a deposit walked that way passed through
`CustomerRepository` and `CustomerAccounts`, two modules a deposit does not touch, and two
flows differing only in their method came back byte-identical. The order was wrong for the
same reason: a fan is sorted by kind and then by name, so `DepositRepository` was numbered
ahead of the `AccountsService` that a deposit asks first. A fan answers what a module
coordinates and has to be a set of distinct things, so that no module can raise its depth
by writing more calls; a set has no order and no idea which method wrote it, and a path
needs both. So the two readings are separate, and `graph.py` holds them against each other:
every edge on a path has to be an edge in the fan the same source produced.

**A path is a floor, in the same direction a fan is.** Only names this graph holds are
followed, so a call into the JDK or into a framework is no step, and neither is a call this
reading cannot follow to a module: a call on the result of another call (`a.b().c()`), a
call on a parameter or a local rather than on a field, a method inherited from a type
outside this source. Nothing about which branch runs is modelled — a refusal a call can
answer with is on the flow whether or not a given deposit trips it, because this is a
reading of the source and not a trace of one run. Overloads are read as one: which of two
same-named methods a call meant is javac's answer and not this tool's, so every method of
that name is read.

**A walk that cannot be made ends in no path at all, never in a shorter one.** Four things
can stop it, and each is recorded on the flow, logged as a warning by the run, and printed
on the page in place of the path:

- no module of that id is in the graph, so there is nowhere for the flow to start;
- the module is there and presents no method of that name a caller can reach, so nothing
  in it is the call the flow is entered by — the reason names what it does present;
- the method is presented and no body is written under it here, so there are no calls to
  follow: a flow is entered through the module that implements the call, not through one
  that only promises it;
- the call reaches no other module this graph holds, so the flow passes through the one
  module it starts at and is not a path through the application.

A flow half walked is the one output worse than no flow: every module on it is real, the
path is followable, and the event it claims to trace stopped happening that way some
commits ago. So the button for such a flow cannot be pressed, and the reason sits above the
chooser where the greyed-out button would send a reader looking for it.

Nothing about a flow is worked out while the page is drawn. The path arrives in the graph
document as a numbered list, each step naming the module it was reached from, the method
the call was written in (`calledFrom`), the call as the source wrote it (`call`), and the
same sentence the fan was read with — and the page reads that list, so the highlighting on
the cards and what a later tool reads out of `module-depth-map.json` are the same facts.

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

    --source DIR     a directory of source to read (repeatable; default backend/src/main/java and frontend/src)
    --graph FILE     where to write the graph document
    --page FILE      where to write the page
    --scoring FILE   the weights, exclusion rules and flows to apply (default scoring.json beside the tool)
    --log-level ...  DEBUG to see every file read, every module found and every exclusion

## Layout

    scripts/module-depth-map.py       the single command
    scripts/module_depth_map/
        cli.py                        arguments, and writing both outputs from one document
        graph.py                      source roots in, the graph document out
        languages.py                  the seam every reading is asked through, and who answers it
        javasource.py                 reading one Java file well enough to name its modules
        typescriptsource.py           reading one TypeScript file well enough to name the module it is
        scoring.py                    what an interface costs, and what is never scored
        scoring.json                  the weights, the rules and the flows — edit this
        page.py                       the graph document rendered as one self-contained file
        tests/                        one package per property being established

A source root is a directory rather than a language: which language a file under it is read
with is decided by its own ending, the way `.java` already decided it when there was only
one. So a directory holding both is read as both, nothing has to be said on the command
line, and `languages.py` is the one place that knows there is more than one reading — the
graph and the scoring rules never name a language, which is what makes "one measure over
the whole application" structural rather than a claim.
