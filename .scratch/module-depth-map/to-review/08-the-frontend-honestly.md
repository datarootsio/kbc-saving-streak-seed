# 08: The frontend, honestly

**What to build:** The frontend appears on the page alongside the backend, analysed by the same rules
at file grain, so the picture is of the application rather than of half of it.

Its result is shown rather than smoothed over. The largest file in this repository presents almost
nothing to a caller while containing a great deal, and under the measure this page uses it scores as
low-leverage for its size. That outcome is the clearest demonstration available of what the page
exists to teach: depth is complexity hidden behind a small interface, not a lot of code. Drawing the
frontend as one tidy box would be a picture that lies by omission.

**Blocked by:** 03 (Reach, and the fan).

Status: needs-review

- [x] Frontend source is analysed at file grain and appears on the page beside the backend modules
- [x] Interface cost, reach and depth are computed for frontend modules by the same rules as for backend modules
- [x] What a frontend module exports, and what it reaches, are both derived from the source
- [x] A large frontend module presenting a small interface is not reported as deep on account of its size
- [x] The page makes the frontend's shape visible rather than collapsing it into a single unscored box
- [x] Frontend source the tool cannot parse is reported loudly and named, as backend source is
- [x] Test code, build output and dependencies are excluded from the graph

## Review feedback - attempt 1

Four of the seven criteria are genuinely met and I saw them work. Three are not, and the reason is
the same in every case: the TypeScript reader refuses legal, ordinary TypeScript, and where it does
read it, it does not read it by the same rules the Java side uses. Every point below I reproduced
myself against this branch; each carries the exact source and the exact output.

What is right, so you do not redo it: the page draws a `frontend/src` package section beside the
nine Java packages with three file-grain cards (`App`, `api`, `main`), bars and fans included;
`App` at 1545 lines draws a bar of 1 over a fan of 1 and is not the deepest module on the page, and
500 lines of padding added to a fixture moved no number at all; the page's "What a line count is
worth here" section reads `scoring.largest` straight off the graph; determinism holds byte for byte
and no backend module's numbers moved; the `sourcesNotRead` rule and its page section work. Keep all
of that.

### 1. A destructured parameter fails the whole file (blocks "reported loudly and named", "derived from the source")

The tool's own docstring in `_parameters_in` (typescriptsource.py:962) and README.md:184 both promise
this works, using this exact example. It does not.

    $ cat /tmp/src/destructured.tsx
    type Props = { customer: string; onSignOut: () => void }

    export function Banking({ customer, onSignOut }: Props) {
      return <div>{customer}</div>
    }
    $ python3 scripts/module-depth-map.py --source /tmp/src --graph /tmp/g.json --page /tmp/p.html
    WARNING module_depth_map.graph could not parse source file root=src language=typescript
      path=destructured.tsx reason=a parameter of Banking on line 3 could not be read:
      '{ customer, onSignOut }: Props' has no type written on it

The parameter plainly has a type written on it. `_first_at_depth_zero(written, 0, ":")` returns None
the moment it meets the `{` at depth 0, so neither a colon nor an `=` is found and the file is
failed.

Why this is not a corner case: every one of App.tsx's ~40 components (`SignIn`, `Banking`, `TopBar`,
`Home`, `DepositForm`, …) is written this way. It is latent only because none of them is exported
today. Export any one of them — the single most likely edit a participant makes to this repo — and
all 1545 lines of App.tsx leave the graph, taking `main`'s fan line with them. This repository's own
standard, in README.md's parse-failure section, is that a false alarm is a bug: "an alarm that cries
wolf stops being read; there is a test for the shape now." There is no such test on the TypeScript
side.

### 2. `export default <expression>` fails the whole file (same criteria)

    $ printf 'export default () => { return 1 }\n' > /tmp/src/d.ts
    WARNING ... path=d.ts reason=the export on line 1 could not be read: it is followed by '(',
      which this tool has no reading of

Same for `export default connect(App)` and `export default 42`. This is the second most common React
default-export idiom. `_decline` (typescriptsource.py:687) exists for exactly this — legal but not
measurable — and is used for an exported class and for a re-export, but not here, so the module and
every fan line into it vanish instead of the export being named and skipped.

### 3. A parenthesised const initialiser fails the file with a reason that is false

    $ printf 'export const total = (1 + 2)\n' > /tmp/src/paren.ts
    WARNING ... path=paren.ts reason=a parameter of total on line 1 could not be read:
      '1 + 2' has no type written on it

`_binding_from` (typescriptsource.py:769) sends any `const` initialised with `(` to `_arrow_from`,
which reads the expression as a parameter list. There is no parameter called `1 + 2` in that source.
Confirm an `=>` follows the bracket group before treating it as an arrow.

### 4. A renamed or default import that is called never reaches anything (blocks "what it reaches ... derived from the source")

    $ cat /tmp/src/api.ts
    export async function fetchDeposits(id: number): Promise<number> { return id }
    $ cat /tmp/src/uses.ts
    import { fetchDeposits as fd } from './api'
    export function load(id: number): Promise<number> { return fd(id) }

    src/uses reach []          <- should be [api]

The static-import branch in `scoring.py` (~line 920, and `statically[imported.member]` again around
line 1116) matches `imported.member` against the names the body calls, but a body writes
`imported.local`. Only the case where the two are the same string works, which is why the committed
`frontend/src` happens to look right. `import App from './App'` called as `App()` is missed the same
way, member being `default` — `main` reaches `App` today only because it also writes `<App />` as a
JSX element. The same defect truncates any flow through a renamed import.

### 5. A JSDoc `@throws` above a const-bound arrow is dropped, and the module is then falsely accused

Two files, identical but for `function` versus `const … =>`:

    /** @throws Refused when it will not */
    export function viaFunction(a: number): number { if (a === 0) { throw new Refused() } ... }
    -> methods [('viaFunction','number',['Refused'])]  refusals [('Refused', documented=True, raised=True)]  findings []

    /** @throws Refused when it will not */
    export const viaConst = (a: number): number => { if (a === 0) { throw new Refused() } ... }
    -> methods [('viaConst','number',[])]  refusals [('Refused', documented=False, raised=True)]
       findings ['raised but never documented']

The const form produces a finding against a module that documented its refusal. `_arrow_from` /
`_binding_from` hand `_documented_before` a position after the `=`, and its `[\s\w$]*` gap test
rejects the `=` in `export const f = `. The page's own words: "A machine that accused a module which
kept its word would stop being read." This one does.

### 6. A generic function's own type variables are charged as types the caller must learn (blocks "the same rules as for backend modules")

    $ printf 'export function first<T>(items: T[]): T { return items[0] }\n' > /tmp/src/g.ts
    src/generic cost 4  types [('T', mustBeLearned=True)]     <- should be cost 2, T excluded

TypeScript `Method` objects are built without `type_parameters` (typescriptsource.py:726);
`_past_type_parameters` skips the `<T>` without recording it. The Java side records them precisely so
`_types_crossing_the_seam` can filter them out. The card names a type `T` a reader can go looking for
and will never find, and the inflated cost is the denominator leverage is divided by.

### 7. A return type written as an object literal is read as the empty string (same criterion)

    $ printf 'export function f(): { id: number } { return { id: 1 } }\n' > /tmp/src/o.ts
    src/objret methods [('f', '', [])]     <- returns is ''

`_returns_and_body` (typescriptsource.py:930) advances past the first brace group. The caller is
charged zero types for a return they must learn whole — an interface cheaper than the source makes
it, which is the direction this module's own docstring says it must never be wrong in.

### 8. `typesEveryCallerAlreadyKnows` is one shared list, so the Java rules changed too

`Error`, `Date`, `Array`, `Record`, `Response`, `object`, `any` and the TypeScript primitives were
added to the single list both languages read. A Java module whose seam is spelled with a domain type
of one of those names now gets it free:

    package shop;
    public class Till { public Response ring(Response given) { return given; } }
    -> Till cost 2, types [('Response', mustBeLearned=False)]

No number on this repository moved — I diffed every module's cost, reach, leverage, callers and
verdict against the ticket/07 graph and confirmed zero changes, so this is latent rather than a
regression. But "measured by the same rules" was implemented as "measured by one shared list", and
nothing in `scoring.json` says the list is per-language. Either say so in the file, or split it.

### 9. Smaller things, worth fixing while you are in here

- The page lede reads "both halves of it, java" on a single-language run. `--source` is documented as
  repeatable, and any run of one root — or a run in which every TypeScript file failed to parse —
  renders a sentence that is false. Let the prose follow `document_.source.languages.length`.
  Reproduce: `python3 scripts/module-depth-map.py --source backend/src/main/java --graph /tmp/g.json
  --page /tmp/p.html`, then read the first paragraph.
- `page.py` (~line 749) guards `largest.leverage === null` but not `largest.interfaceCost === null`.
  When the longest file is a module an exclusion rule never scores, the new section renders
  "costing null units of interface".
- The `sourcesNotRead` directory rule is applied only to subdirectories found during the walk, never
  to the source root's own basename. `python3 scripts/module-depth-map.py --source
  frontend/node_modules` walks straight in and scores 26 dependency modules
  (`run finished ... filesParsed=26 ... modules=26 scored=26`).
- `scoring.py:89` imports `javasource` and never uses it, and `graph.py:277` still catches
  `javasource.ParseFailure` — so `languages.py`'s claim that "neither the graph nor the scoring rules
  ever name one" is not quite true. Cosmetic, but the docstring is load-bearing prose here.
- `_refuse_unreadable_imports` takes a `depth_of` argument it never uses, and its docstring says a
  dynamic import is read when `_AN_IMPORT`'s lookahead deliberately excludes `import(`.

### How to know you are done

Every point above is a fixture the suite does not have. `tests/thefrontendhonestly` is otherwise
good work — the both-languages-same-numbers test and the padding test are exactly right — so add to
it rather than starting over. In particular, the Java side has
`LegalJavaIsNotFailedForBeingWrittenTightlyTest`; the TypeScript side needs its counterpart, holding
at minimum: a destructured parameter, `export default` followed by an arrow / a call / a literal, a
parenthesised const initialiser, a JSDoc `@throws` above a const-bound arrow, a generic function, and
an object-literal return type.

## Verified (what I ran, and what came back)

Checks, both green, run by me on this branch:

- `cd backend && ./mvnw test` -> exit 0, 113 tests.
- `cd frontend && npm run typecheck` -> exit 0 (needed Node 24 on PATH; system node is v16).
- `python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests` -> **634 tests, OK**.
  Every defect above is latent: the suite passes with all of them present.

Determinism and no-regression, run by me:

- Two fresh runs into a scratch directory are byte identical for both the graph and the page
  (`cmp` clean); neither output contains an absolute path.
- `python3 scripts/module-depth-map.py` over the default roots leaves `git status` clean, so the
  committed `docs/module-depth-map.{json,html}` are what a fresh run writes.
- Diffing every module's cost, reach, leverage, callers, findings and verdict against
  `ticket/07-flows-through-the-modules:docs/module-depth-map.json`: **no backend module moved**;
  exactly three modules added (`frontend/src/App`, `frontend/src/api`, `frontend/src/main`).

The page, driven with Playwright (chromium, console/pageerror/requestfailed subscribed; log at
`.scratch/module-depth-map/logs/08-the-frontend-honestly.review.1.browser.log`):

- `file://docs/module-depth-map.html` at 1024, 1280 and 1440 wide, light and dark: renders styled,
  `scrollWidth == clientWidth` at every width, **zero console errors, zero pageerrors, zero failed
  requests**.
- H2s in order: "What was not read at all", "What the bars measure", …, "What a line count is worth
  here", …, "Flows through the modules", then **`frontend/src`** ahead of the nine
  `io.dataroots.savingstreak*` packages. `frontend/src` holds 3 cards, each labelled FILE.
- `App` — "1 to learn: 1 method … reaches 1 … no finding", nested Celebration, SavingsAccountView.
  `api` — "49 to learn: 11 methods, 20 parameters, 7 types to learn … refuses with 2: Error,
  SignInFailed", the widest bar on the page over no fan at all. Screenshots:
  `…review.1.frontend-section.png`, `…review.1.frontend-1440-dark.png`.
- Clicking `App` opens the panel: "file · typescript · frontend/src", "frontend/src/App.tsx · 1545
  lines", "MODULE api — calls claimReward, which this file imports from it". Escape closes it.
- Flow buttons still work after the frontend was added: "a deposit" marks 12 cards, "a withdrawal…"
  13, "a reward claimed" 9; Clear resets to 0.
- The "What a line count is worth here" section reads: "The longest module here is frontend/src/App,
  at 1545 lines of typescript. It presents 1 method, costing 1 unit of interface, over a fan of 1
  thing — leverage 1." Every value in it is `scoring.largest` in the graph document; nothing is
  computed in the browser.

The tool's own logs, at DEBUG over the real repository:

    INFO module_depth_map.graph largest module=frontend/src/App language=typescript lines=1545 interfaceCost=1 reach=1 leverage=1.0
    INFO module_depth_map.graph graph built roots=backend/src/main/java,frontend/src languages=java,typescript filesSeen=74 filesParsed=74 filesUnparsed=0 pathsNotRead=0 modules=74 scored=38 neverScored=36
    INFO module_depth_map.graph widest interface module=frontend/src/api cost=49 methods=11 typesToLearn=7
    INFO module_depth_map.graph shallowest module=frontend/src/api leverage=0.0 reach=0 interfaceCost=49
    DEBUG module_depth_map.typescriptsource parsed file path=App.tsx module=frontend/src/App lines=1545 exports=1 typesDeclared=2 imports=27
    DEBUG module_depth_map.typescriptsource export not read as a method line=54 export=SignInFailed reason=an exported class is named on this module rather than measured …

No WARNING or ERROR from `module_depth_map` over the real source. Logging follows CLAUDE.md: SLF4J's
Python counterpart throughout, no `print`, one INFO per business event with the values behind it,
WARNING with a reason on every refusal, DEBUG for the inputs. That part is right.

Exclusions and parse failures, exercised by me on a scratch tree:

    INFO  … source not read root=src path=node_modules rule=not the application's own source matched=a directory named node_modules
    INFO  … source not read root=src path=dist … matched=a directory named dist
    INFO  … source not read root=src path=__tests__ … matched=a directory named __tests__
    INFO  … source not read root=src path=thing.test.ts … matched=a name ending in .test.ts
    INFO  … source not read root=src path=types.d.ts … matched=a name ending in .d.ts
    WARNING … could not parse source file root=src language=typescript path=broken.ts reason=a block comment opened on line 1 is never closed
    WARNING … could not parse source file root=src language=typescript path=broken2.tsx reason=braces do not balance: 1 unclosed at end of file
    WARNING module_depth_map.cli the page is drawn from 2 of 4 source files: 2 could not be read …

All eight paths are named on the generated page with the rule and what matched. `--source frontend`
with 42 packages under `node_modules` finishes in 0.32s, pruning it as one path.

The running application (backend :8080, Vite :5173, throwaway db) — this branch changes no
application source, and I drove it only to check the graph's findings against real behaviour: an
unknown address gives "No customer banks here under that email address" (that is `api.ts`'s
`SignInFailed`, the refusal the graph reports as "raised but never documented"), and
`anke.peeters@example.be` signs in and shows both savings accounts and the rewards catalogue. Backend
log confirms both:

    DEBUG … RequestResponseBodyMethodProcessor : Read "application/json;charset=UTF-8" to [SignInRequest[contactDetails=nobody@example.be]]
    DEBUG … i.d.s.deposits.DepositsService : money balance summed from what remains savingsAccountId=1 deposits=0 balance=0.00

Screenshots `…review.1.app-refused.png` and `…review.1.app-home.png`.

Cross-language check I wrote myself rather than trusting the branch's own test: the same shape in
Java and in TypeScript — two methods, one documented refusal, three collaborators — comes out at
cost 8, refusalCost 2, reach 3, leverage 0.38, verdict "earns its keep" on **both** sides, with the
same methods, refusals and reaches. That much of "the same rules" is real; points 6, 7 and 8 above
are where it stops being.


## Review feedback - attempt 2

**All nine points from attempt 1 are genuinely fixed.** I reproduced every one of them against this
branch and every one now behaves; the list is under "What I confirmed fixed" at the bottom so you do
not redo any of it. Determinism, the no-regression diff against ticket/07, both lab checks and the
672-test suite are all green, and the page itself is right.

What sends this back is that the TypeScript reader still refuses or misreads legal, ordinary
TypeScript in shapes the suite does not cover. Attempt 1 sent this back for three of those; there are
nine more, four of which take a whole module off the page. I reproduced **every** item below myself
against this branch with the exact source shown — none is a code reading, all are runs.

`README.md:252` says "And legal TypeScript is never failed, which matters more here than on the Java
side: a failed file is a whole module off the page and every fan line into it gone with it." That
sentence is the standard this is being held to, and it is the sentence that is not yet true.

### File-fatal: the whole module leaves the page

**1. A `function` whose return type contains `=>`.**

    $ printf 'export function useToggle(): [boolean, () => void] { return [true, () => {}] }\n' > /tmp/src/x.ts
    $ python3 scripts/module-depth-map.py --source /tmp/src --graph /tmp/g.json --page /tmp/p.html
    WARNING module_depth_map.graph could not parse source file root=src language=typescript
      path=x.ts reason=the return type of useToggle on line 1 could not be read: nothing closes it

All of these fail, and `frontend/node_modules/.bin/tsc --noEmit --strict` compiles every one without
a word — I checked that too:

    export function f(): () => void { return () => {} }
    export function f(): (x: number) => number { return x => x }
    export function f(): (() => void) { return () => {} }
    export function f(): Array<() => void> { return [] }
    export function f(): Promise<(x: number) => number> { return null as any }
    export async function f(): Promise<() => void> { return () => {} }
    export function useDebounce(fn: () => void, ms: number): () => void { return fn }

The `const`-bound arrow form works, and `test_a_return_type_written_as_a_function_is_read_rather_
than_declined` (test_the_frontend_is_drawn_honestly.py:974) holds it. That test is the whole coverage
for this shape and it only exercises the arrow path; the `function` path is untested and broken.
`README.md:266-267` already lists this shape as fixed — "a return type … **written as a function
type**, whose `=>` was counted as a bracket closing". It is fixed in one of the two places it lives.

**2. A side-effect import that is not the last import, in a file without semicolons.** This is the
stock Vite `react-ts` scaffold ordering:

    $ cat /tmp/src/main.tsx
    import { StrictMode } from 'react'
    import { createRoot } from 'react-dom/client'
    import './index.css'
    import App from './App.tsx'
    ...
    WARNING ... path=main.tsx reason=the import on line 4 could not be read: it names no module
      in either of the two forms this tool reads

`_IMPORT_FROM` (typescriptsource.py:112) has a `([^;]*?)` clause that spans newlines, so a
side-effect import swallows the statement after it. This repository's own `frontend/src/main.tsx`
survives only because `import './index.css'` happens to be written last. Move that one line up and
`frontend/src/main` leaves the page, taking the only fan line into `App` with it. Reordering two
imports is about as ordinary an edit as a participant can make.

**3. An apostrophe in JSX prose written on the same line as a real string.**

    $ cat /tmp/src/x.tsx
    function label(k: string): string { return k }
    export function Note(): JSX.Element {
      return <p>Don't miss it</p> && {label('key')}
    }
    WARNING ... path=x.tsx reason=braces do not balance: a closing brace with nothing open on line 4

`_mask_quoted` (typescriptsource.py:1570) pairs the apostrophe in `Don't` with the `'` that opens
`'key'`, blanking `</p> && {label(` — the `{` disappears and the `}` does not. The reason the tool
gives is false about the file. This is the same hazard `README.md:238-242` says the JSX-prose rule
exists to handle, one step further along.

**4. `export abstract class`.**

    $ printf 'export abstract class Shape { abstract area(): number }\n' > /tmp/src/x.ts
    WARNING ... reason=the export on line 1 is written `export abstract`, which this tool has no
      reading of: what it describes is declared somewhere this tool was not pointed at

`abstract` is on `_NOT_READ_HERE` (typescriptsource.py:237) beside `declare` and `namespace`, but it
does not belong there: the body is right in the file, and a plain `export class Shape {}` is merely
`_decline`d and named on the module. The reason printed is not true of the source, and one abstract
class anywhere in a file takes the file off the page.

### Read wrongly, with nothing reported

**5. The same return type on one line as its body is silently corrupted, and invents a type called
`return`.**

    $ printf 'export function makeFetcher(): (url: string) => Promise<Response> { return async u => fetch(u) }\n' > /tmp/src/x.ts
    -> no warning at all; the graph says:
       methods:              [{"name": "makeFetcher", "returns": "(url: string) => Promise<Response> { return"}]
       typesCrossingTheSeam:  Promise, Response, string, and **return (mustBeLearned: true)**
       interface cost 3

Attempt 1's point 6 in the same words: the card names a type a reader can go looking for and will
never find, and the inflated cost is the denominator leverage is divided by.

**6. A parameter list collapses when a generic argument holds an `=>`, so the interface is cheaper
than the source.** The same shape in the two languages, everything else equal:

    // shop/Till.java
    public void ring(Map<String, Supplier<String>> m, int n) { }
    -> Java: cost 5, parameters ['Map<String, Supplier<String>>', 'int']       <- two parameters

    // src/x.ts
    export function ring(m: Map<string, () => string>, n: number): void {}
    -> TS:   cost 2, parameters ['Map<string, () => string>, n: number']       <- one parameter

Take the `=>` out and TypeScript gets it right (cost 3, two parameters), so this is the arrow and
nothing else. `_parameters_in`'s own docstring: "a misread parameter leaves an interface cheaper than
the source makes it." This is also the plainest failure of "by the same rules as for backend
modules": the Java reader handles a nested generic and the TypeScript one does not.

**7. Only the first declarator of an exported `const` is read, and the rest are not even declined.**

    $ printf 'export const a = 1, b = () => {}\n' > /tmp/src/x.ts
    -> methods: []
    -> DEBUG ... export not read as a method line=1 export=a reason=what is assigned to it is a value …

`b` is a function a caller can import. It is not in `methods`, not in `types`, and not in the
`export not read as a method` log that `_decline`'s docstring promises is the complete list of what
was skipped. `_binding_from` (typescriptsource.py:804) stops at the first declarator.

**8. A fan line whose evidence a reader can check and find false.**

    $ cat /tmp/src/uses.ts
    import * as api from './api'
    export function make(): number { const t = new api.Thing(); return 1 }

    src/uses reach: [{"kind":"module","matched":"called on api","moduleId":"src/api","name":"api"}]

Nothing is called on `api`. `javasource._receivers_in` (javasource.py:1615) declines exactly this
reading, with a comment calling it "the one failure this file exists to make impossible";
`_reached_in` (typescriptsource.py:1358) reintroduces it by building receivers with no `new` guard.

**9. One type spelled two ways stays two strings, where Java collapses them.**

    export function a(m: Record<string,number>): void {}
    export function b(m: Record<string, number>): void {}
    -> TS parameters:   ['Record<string,number>'] and ['Record<string, number>']

    public void a(Map<String,Long> m) { }
    public void b(Map<String, Long> m) { }
    -> Java parameters: ['Map<String, Long>'] and ['Map<String, Long>']   <- one spelling

`javasource._normalised` (javasource.py:2204) names this exact hazard in its docstring; the
TypeScript copy at typescriptsource.py:1753 dropped the comma handling.

### Where it comes from

Findings 1, 5 and 6 are one concept in two helpers, both of which treat `<`/`>` as a bracket pair:

- `_first_at_depth_zero` (typescriptsource.py:1142) — line 1152 counts `>` in `")>]"`, so the `>` of
  an `=>` drops depth to -1 and line 1155 returns None. `_first_brace_at_depth_zero` (1138) wraps it,
  and `_returns_and_body` (1056) calls that at line 1098, then raises "nothing closes it" at 1104.
- `_split_on_commas` (typescriptsource.py:1736) — line 1744 counts `>` the same way, so the comma
  between two parameters is seen at a negative depth and never splits. Note that the javasource
  original clamps with `max(0, depth - 1)` and this copy dropped the clamp.

`_first_in_the_open` (1213), added on this attempt, already gets it right by not treating angle
brackets as brackets at all, and `_up_to_the_arrow` (975) and `_up_to_the_assignment` (1288)
special-case `=>` by hand. Four scanners, two of which know and two of which do not. Settle it in one
place: the `>` of an `=>` closes nothing.

Findings 8 and 9 are the same shape of problem one level up: `_split_on_commas`, `_normalised` and
`_line_of` are hand-copies of javasource's private helpers, while `after_balanced` and
`in_evaluation_order` were deliberately made shared because "a second account of it would be one that
could drift". These three drifted, and two of the findings above are what the drift cost. Either
share them the same way or say in the file why these three are different.

### How to know you are done

`tests/thefrontendhonestly` is good work — the both-languages-same-numbers test and the padding test
are exactly right — so add to it rather than starting over. Every item above is a fixture the suite
does not have. In particular, beside the existing
`test_a_return_type_written_as_a_function_is_read_rather_than_declined`, which today covers only the
`const`-bound arrow, hold at minimum, as `function` declarations:

- a bare function return type, one inside a generic and one inside a tuple —
  `() => void`, `Promise<() => void>`, `[boolean, () => void]`;
- one written on the same line as its body, asserting the return reads
  `(url: string) => Promise<Response>` and that no type called `return` is on the seam;
- two parameters where the first is a generic holding an `=>`, asserting two parameters and the same
  numbers the equivalent Java shape gets;
- the stock Vite import ordering with `import './index.css'` third of four and no semicolons;
- an apostrophe in JSX prose on a line that also carries a quoted string;
- `export abstract class`, declined and named rather than failed;
- `export const a = 1, b = () => {}`, with `b` either measured or named in the declined log;
- `new api.Thing()` on a namespace import, reaching nothing;
- one type written `Record<string,number>` and `Record<string, number>`, coming out as one string.

Then correct `README.md:252` and `README.md:266-267` once the code keeps what they promise.

### What I confirmed fixed, so you do not redo it

Every point from attempt 1, reproduced by me on this branch:

1. Destructured parameter — `export function Banking({ customer, onSignOut }: Props)` parses; cost 4,
   one parameter `Props`. I also copied the real `frontend/src` and exported `SignIn`, `Banking`,
   `TopBar`, `Home` and `DepositForm` from the real 1545-line `App.tsx` — multi-line patterns and
   inline object types included — and got `filesUnparsed=0`, `src/App cost 21`, six methods read.
   That is the scenario attempt 1 said would take all 1545 lines off the page.
2. `export default () => {…}` is a method called `default`; `export default connect(App)` and
   `export default 42` are declined and named at DEBUG.
3. `export const total = (1 + 2)` no longer fails; it is declined as a value with a reason.
4. `import { fetchDeposits as fd }` then `fd(id)` gives `reach 1 -> src/api`, "calls fd, which this
   file imports from it"; `import App from './App'` called as `App()` gives `reach 1 -> src/App`.
5. A JSDoc `@throws` above a `const`-bound arrow and above a `function` now give identical output:
   methods `[(name,'number',['Refused'])]`, refusal documented=True raised=True, findings [].
6. `export function first<T>(items: T[]): T` -> cost 2, no type `T` on the seam.
7. `export function f(): { id: number }` -> returns `{ id: number }`, `number` charged as familiar.
8. `typesEveryCallerAlreadyKnows` is `{"java": [...], "typescript": [...]}`; a Java `Till` with a
   domain type named `Response` is charged for it again (cost 4, mustBeLearned true). The Java list
   gains only `Date`, `Error` and `Record` relative to ticket/07, all real JDK types.
9. The java-only lede reads "written in java" and carries no TypeScript grain sentence; a run whose
   longest module is never scored renders "no interface cost is printed for it because a rule on this
   page never scored it" rather than "null"; `--source frontend/node_modules` gives "source not read
   root=frontend/node_modules path=the root itself" and 0 modules; `javasource` is gone from both
   `scoring.py` and `graph.py`; `_refuse_unreadable_imports` lost its unused argument.

And the properties this branch must not have broken, all checked by me:

- `cd backend && ./mvnw test` exit 0, 113 tests. `npm run typecheck` exit 0 on Node 24.16.0.
- `python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests` — 672 tests, OK.
- Two fresh runs are `cmp`-identical for graph and page, both equal the committed
  `docs/module-depth-map.{json,html}` byte for byte, neither holds an absolute path, and `git status`
  is clean after a run.
- Diffed all 74 modules against `ticket/07:docs/module-depth-map.json`: **no backend module moved**,
  exactly three added (`frontend/src/App`, `frontend/src/api`, `frontend/src/main`), flows identical.
- 500 lines of padding added to a copy of the real `App.tsx` moved `lines` 1545 -> 2046 and nothing
  else on any of the three modules.
- The page at 1280 wide, light and dark, over six loads (committed, java-only, declined-root,
  largest-never-scored): zero console errors, zero pageerrors, zero failed requests,
  `scrollWidth == clientWidth`. `frontend/src` draws first with three FILE cards; `App` is a bar of 1
  over a fan of 1 at 1545 lines and is fourth by leverage behind three much smaller Java modules;
  `api` is the widest bar on the page (49) over no fan. Panels open and Escape closes them; the flow
  buttons and Clear still work.
- Exclusions: `node_modules`, `dist`, `build`, `__tests__`, `*.test.ts`, `*.spec.ts`, `*.d.ts` all
  named with the rule and what matched, and `WARNING module_depth_map.cli the page is drawn from 1 of
  2 source files` when one file would not parse.
- The running application is untouched by this branch (`git diff --name-only ticket/07..ticket/08 --
  backend frontend` is empty) and still works: an unknown address gives "No customer banks here under
  that email address", `anke.peeters@example.be` signs in and shows both savings accounts and the
  rewards catalogue, with `i.d.s.deposits.DepositsService : money balance summed from what remains
  savingsAccountId=1 deposits=0 balance=0.00` in the backend log and no WARN or ERROR from it.

## Review feedback - attempt 3

**All nine points from attempt 2 are genuinely fixed.** I reproduced every one of them against this
branch and every one now behaves; the list is under "What I confirmed fixed" at the bottom so you do
not redo any of it. Both lab checks, the 694-test suite, determinism, the no-regression diff against
ticket/07 and the page itself are all green, and the new fixtures are real: I restored the
attempt-2 readers over this branch's tests and **26 of the 117 tests in `tests/thefrontendhonestly`
fail**, one for each thing you fixed. That is good work and none of it needs redoing.

What sends this back is the same sentence as last time, `README.md:252`:

> And legal TypeScript is never failed, which matters more here than on the Java side: a failed file
> is a whole module off the page and every fan line into it gone with it.

Point 1 below is the most common line in React, it compiles under `tsc --strict` with no output at
all, and this tool refuses the whole file for a reason that is untrue of the source. Points 2 to 4
each lose or inflate something on a card with no line in any log saying so. I reproduced **every**
item below myself against this branch, with the exact source shown; none is a code reading.

### File-fatal: the whole module leaves the page

**1. A self-closing JSX element with a prop expression, inside a `.map()`, on one line.**

    $ cat /tmp/src/d1.tsx
    export function List(items: string[]): JSX.Element {
      return <ul>{items.map(i => <li key={i} />)}</ul>
    }
    $ frontend/node_modules/.bin/tsc --noEmit --strict --jsx react-jsx --target es2022 \
        --module esnext --moduleResolution bundler --skipLibCheck --lib es2022,dom d1.tsx
    $ echo $?
    0
    $ python3 scripts/module-depth-map.py --source /tmp/src --graph /tmp/g.json --page /tmp/p.html
    WARNING module_depth_map.graph could not parse source file root=src language=typescript
      path=d1.tsx reason=braces do not balance: 1 unclosed at end of file
    WARNING module_depth_map.cli the page is drawn from 0 of 1 source files

The braces balance perfectly in that file. `_AFTER_WHICH_A_REGEX_CAN_START`
(typescriptsource.py:251) holds `}`, so the `}` closing `key={i}` makes the `/` of the following
` />` open a regular expression, and the scan runs to the next `/` on the line — the one in
`</ul>` — blanking `>)}<` and with it the `}` that closes `{items.map(`. `<` and `>` were
deliberately kept out of that set so `</div>` would not do this; `}` was missed.

A second shape, same cause, same file-fatal outcome:

    export function Note(x: boolean): JSX.Element {
      return <><div className={c} /> {x ? <Bar /> : null}</>
    }
    -> reason=braces do not balance: a closing brace with nothing open on line 2

Why this is not a corner case: rendering a list with `.map()` and a self-closing child carrying
`key={...}` is the single most common line in React, and a conditional sibling after a self-closing
element is the second. It is latent here only because this repository's `App.tsx` happens never to
write a prop expression on a self-closing tag — `grep -cE '\{[^}]*\} */>' frontend/src/App.tsx`
returns **0**. Add one list of components, which is the most likely edit a participant makes to
this file, and all 1545 lines of `App.tsx` leave the page and take `main`'s fan line with them.
This is the same shape of latency attempt 1 sent this back for (the destructured parameter) and
attempt 2 sent it back for (the import ordering).

Note that `<div a={x} /></div>` and `<span title={'t'} /> {x ? 'a' : 'b'}</div>` both survive by
luck, because of where the next `/` happens to land — so a fixture has to be the `.map()` shape
specifically, not any self-closing tag with a prop.

### Read wrongly, with nothing reported

**2. A module whose file name equals a name it imports reaches nothing** (blocks "what it reaches …
derived from the source"). Two files, byte-identical but for their names:

    $ cat /tmp/src/util.ts
    export function format(n: number): string { return String(n) }

    $ cat /tmp/src/format.ts          # and the same bytes again as other.ts
    import { format } from './util'
    export function run(n: number): string { return format(n) }

    src/format  reach 0   leverage 0.0    <- no evidence line at all
    src/other   reach 1   leverage 0.5    "calls format, which this file imports from it"

`scoring.py:975` adds `{declared.name}` to the names a body is held to declare for itself. On the
Java side that is right — a class's own name is a binding in its scope. On the TypeScript side
`declared.name` is the file's basename, which is not a binding in the file at all, so a file called
`format.ts` is treated as declaring `format` and the import of that name is never followed. The same
line is repeated in `calls_from` (scoring.py:1180), so a flow entering such a module dead-ends
there too. Naming a file after the thing it is about is the normal way to write a frontend, and
`api.ts` in this repository is one rename away from it.

**3. A `<` anywhere in a declarator's value loses every later declarator, silently.**

    $ printf 'export const flag = 1 < 2, b = (x: number): number => x\n' > /tmp/src/x.ts
    -> cost 0, methods []
    -> DEBUG … export not read as a method line=1 export=flag reason=what is assigned to it is a value …
    -> DEBUG … parsed file path=x.ts module=src/x lines=1 exports=0 …

`b` is a function a caller can import. It is not in `methods`, not in `types`, and — unlike `flag` —
not in the `export not read as a method` log that `_decline`'s docstring promises is the complete
list of what was skipped. `_next_declarator` (typescriptsource.py:945) counts `<` as an opening
bracket, so the comparison leaves depth permanently above zero and the depth-zero comma is never
seen. Attempt 2's point 7 was this same outcome for the plain case; it is fixed there and
reintroduced here by an unrelated operator. The session note calls this a deliberate trade
costing "only a declarator this reading would have declined as a value anyway" with "no spurious
log line either way" — the run above shows it costs a measurable function and leaves no line at all.

**4. An overload set is charged as one method more than a caller can call.**

    export function ring(id: string): string
    export function ring(id: number): string
    export function ring(id: string | number): string { return String(id) }
    -> cost 6, three methods: ring(number), ring(string), ring(string | number)

    export function ring(id: string | number): string { return String(id) }
    -> cost 2, one method

TypeScript never exposes the implementation signature to a caller: two signatures are callable, not
three. `_exports_in` (typescriptsource.py:668) reads every `export function ring` header including
the implementation one, and `interface_of` sums `_cost_of` over all of them. The card names a call a
reader can go looking for and will never be able to make, and the inflated cost is the denominator
leverage is divided by — the same objection attempt 1 raised as its point 6. `has_a_body` already
tells the header from the implementation and is not consulted. Java overloads are all genuinely
callable, so this is a place where "the same rules" means a different reading, not the same one.

### Smaller, and each one a sentence on a card a reader can check and find false

**5. A binding whose written type wraps a line is recorded truncated** (typescriptsource.py:1441).

    const held: Till |
      Receipt = make()
    -> Field('held', 'Till |')

`_up_to_the_assignment` ends the written type at a newline seen at bracket depth zero, and a `|`
continuation is outside any bracket. `reach_of` then reads `Till` and never `Receipt`, so every call
written through `held` draws a fan line to one of the two, and the evidence printed on the card is
the string `Till |`.

**6. `|` is not closed up by `normalised`, so one union spelled two ways stays two strings**
(javasource.py:2239).

    export function f(a: string|null, b: string | null): void {}
    -> parameters ('string|null', 'string | null')

This is the hazard that function's own docstring names, and it was fixed for `,` on this attempt
and missed for `|`. Counting is unaffected; the panel text is not.

**7. A namespaced JSX component reaches nothing** (typescriptsource.py:190).

    import * as Icons from './icons'
    export function Row(): JSX.Element { return <Icons.Chevron /> }
    -> reach 0                 ( <Chevron /> from a named import gives reach 1 -> src/icons )

`_A_JSX_ELEMENT` does not match a dotted name. An understating reading is allowed here, but the page
lists its understating readings by name under "Readings that go the other way", and this one is
absent — so a reader checking a fan against the source finds a gap the page does not account for.

**8. A string-literal type on a seam is printed with its contents blanked.** Found by me, not by the
code review:

    export function union(a: 'one' | 'two' | 'three'): number { return a.length }
    -> parameters ["' ' | ' '  | ' '"]        (and Record<'a'|'b', number> -> Record<' '|' ', number>)

No number moves and no file fails — `_mask_quoted`'s output is reaching the printed parameter text.
The idiomatic form, a named `export type Mode = 'read' | 'write'`, is read correctly and charged 2,
so this is display-only. Listed so it is not rediscovered as a mystery.

### Two things that are arguments rather than defects

- `_next_declarator`'s `<` handling above is the only one of these where the session note already
  named the trade. It is written up as a defect because the note's justification does not hold.
- `scoring.json:168` lists `build`, `target`, `dist` and `coverage` as directories never walked
  into, while the rule's own `because` argues that a bare `test`/`tests` is deliberately absent
  "because that is a legal Java package name and a rule that quietly took a package of the
  application's own source off the page would be worse than one that misses a directory somebody
  can add to this list". All four of those are legal Java package names too. Either the argument
  covers them or it does not; say which in the file. I did not treat this as a defect — the ticket
  asks for these to be excluded and they are — but the two sentences contradict each other.

### How to know you are done

Every point above is a fixture the suite does not have. `tests/thefrontendhonestly` is good work,
so add to it rather than starting over. Hold at minimum:

- `export function List(items: string[]): JSX.Element { return <ul>{items.map(i => <li key={i} />)}</ul> }`
  read rather than failed, asserting `filesUnparsed=0` — and a second one with a self-closing
  element followed by `{x ? <Bar /> : null}` on the same line;
- two byte-identical modules differing only in file name, one of them named after a function it
  imports, asserting both reach the module they call;
- `export const flag = 1 < 2, b = (x: number): number => x`, with `b` either measured or named in
  the declined log;
- an overload set of two signatures plus its implementation, asserting two methods and the same cost
  as the single equivalent signature;
- a module-scope binding whose written type wraps a line, asserting both names are on the seam;
- one union written `string|null` and `string | null`, coming out as one string;
- `<Icons.Chevron />` on a namespace import — either reaching `icons`, or named on the page's list
  of understating readings;
- a string-literal type in a signature, asserting the parameter text is what the source wrote.

Then re-check `README.md:252` and the parse-failure list at `README.md:266-267` against what the
code actually keeps.

### What I confirmed fixed, so you do not redo it

Every point from attempt 2, reproduced by me on this branch:

1. A `function` whose return type contains `=>` — all eight forms read, none failed:
   `(): [boolean, () => void]`, `(): () => void`, `(): (x: number) => number`, `(): (() => void)`,
   `(): Array<() => void>`, `(): Promise<(x: number) => number>`, the `async` form, and
   `useDebounce(fn: () => void, ms: number): () => void` (cost 3, two parameters).
2. The stock Vite import ordering with `import './index.css'` third of four and no semicolons parses,
   and `main` still reaches `App` — with and without the `.tsx` on the specifier.
3. `<p>Don't miss it {label('key')}</p>` keeps its braces.
4. `export abstract class Shape` is declined and named, not failed.
5. `makeFetcher(): (url: string) => Promise<Response> { … }` on one line reads its return whole;
   no type called `return` on the seam.
6. `ring(m: Map<string, () => string>, n: number)` is two parameters.
7. `export const a = 1, b = () => {}` measures `b` and declines `a` by name. (Only the `<` variant
   in point 3 above still loses it.)
8. `new api.Thing()` reaches nothing; `api.go()` still reaches `api`.
9. `Record<string,number>` and `Record<string, number>` are one string.

And the properties this branch must not have broken, all checked by me:

- `cd backend && ./mvnw test` — exit 0, 113 tests, BUILD SUCCESS.
- `cd frontend && npm run typecheck` — exit 0 (Node 24.16.0; system node is v16).
- `python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests` — **694 tests, OK**.
- Restoring `a596326`'s `typescriptsource.py` and `javasource.py` under this branch's tests fails
  **26 of the 117** tests in `tests/thefrontendhonestly`, one per attempt-2 point. The fixtures are real.
- Two fresh runs are `cmp`-identical for graph and page, both equal the committed
  `docs/module-depth-map.{json,html}` byte for byte, neither holds an absolute path, and
  `git status` is clean after a run over the default roots.
- Diffed all 74 modules against `ticket/07:docs/module-depth-map.json`: **no backend module moved**,
  exactly three added (`frontend/src/App`, `frontend/src/api`, `frontend/src/main`), flows identical.
- 500 lines of padding on a copy of the real `App.tsx` moved `lines` 1545 -> 2045 and **nothing else
  on any of the three modules**.
- `frontend/src/App` is 1545 lines — 5.4x the next longest file — at interface cost 1 over a fan of
  1, leverage 1.0, and ranks **4th** by leverage behind three Java modules of 60, 67 and 149 lines.
  Size is not depth here, which is the whole point of the ticket.
- The same shape in both languages comes out identical: a Java `Till` and a TypeScript `till`, two
  methods, one documented refusal, three collaborators — cost 8, refusalCost 2, reach 5,
  leverage 0.62, verdict "earns its keep", same methods, types, refusals and reaches on both sides.
- The page at 1024, 1280 and 1440 wide, light and dark: **zero console errors, zero pageerrors, zero
  failed requests**, `scrollWidth == clientWidth` at every width. `frontend/src` draws first with
  three FILE cards; `App` is a sliver of a bar over a fan of 1, `api` the widest bar on the page (49)
  over no fan. Panels open and Escape closes each; flows still mark 12 / 13 / 9 and Clear resets to 0.
- Exclusions: `node_modules`, `dist`, `build`, `coverage`, `__tests__`, `*.test.ts`, `*.spec.tsx`,
  `*.d.ts` each named with the rule and what matched; `--source frontend/node_modules` gives
  `source not read root=frontend/node_modules path=the root itself` and 0 modules in 0.27s.
- Exporting five components from a copy of the real `App.tsx` **and** moving `import './index.css'`
  to second of four — the two edits attempts 1 and 2 said would take the file off the page — now
  gives `filesUnparsed=0`, `src/App cost 21` with six methods, and `main` still reaching `App`.
- 35 files of legal TypeScript I wrote as a sweep and 25 more aimed at the masking and bracket
  scanners, every one compiled by `tsc --strict` with exit 0, plus 30 real third-party `.ts` files
  out of `node_modules`: all 90 read, `filesUnparsed=0`. The JSX shape in point 1 above is the one
  thing in that sweep the tool refused.
- Logging follows CLAUDE.md: no `print` anywhere in `scripts/module_depth_map`, a
  `module_depth_map.*` logger per file, one INFO per business event with its values, WARNING with a
  reason on every refusal (`could not parse … reason=…`, `source not read … rule=… matched=…`,
  `refused to run: the scoring rules in … cannot be used: … Nothing is scored with a rule nobody
  wrote`), DEBUG for the inputs behind a decision.
- The running application is untouched by this branch (`git diff --name-only ticket/07..HEAD --
  backend frontend` is empty) and still works. Over HTTP against the running API I got 404 "No
  customer banks here under that email address", 200 for `anke.peeters@example.be`, 201 for a
  7.00 deposit, 400 "A deposit has to be an amount of more than zero, and 0.00 is not.", 400
  "Cinema ticket costs 100 points, and this account has 7.", and 201 for a withdrawal that
  allocated against deposit 1 — with `i.d.s.deposits.DepositsService : deposit accepted depositId=1
  savingsAccountId=1 fromCurrentAccountId=1 amount=7.00 pointsEarned=7` and
  `i.d.s.deposits.WithdrawalsService : withdrawal accepted withdrawalId=1 … amount=3.00` in
  `.scratch/module-depth-map/logs/08-the-frontend-honestly.app.3.backend.log`. Driving the page at
  :5173 signed in and showed both savings accounts and the rewards catalogue. (The application still
  logs no `io.dataroots.savingstreak` WARN when it refuses; that is the pre-existing gap already
  recorded at the end of ticket 07 and is not this branch's doing.)

## Review feedback - attempt 4

**All eight points from attempt 3 are genuinely fixed.** I reproduced every one against this branch
and every one now behaves; the list is under "What I confirmed fixed" at the bottom so you do not
redo any of it. Both lab checks, the 718-test suite, determinism, the no-regression diff against
ticket/07 and the page itself are all green, and the fixtures are real: restoring `21e302b`'s
`typescriptsource.py`, `javasource.py`, `scoring.py`, `page.py` and `languages.py` under this
branch's tests fails **20 of the 141** tests in `tests/thefrontendhonestly`, one per point you fixed.
That is good work. Five of the seven criteria are met and I saw them work.

What sends this back is two criteria I cannot tick, and the reason is a different one from the last
three rounds. This is not about legal TypeScript being *refused* any more — I could not break that.
It is about the fan and the card saying things the source does not say. Every point below I
reproduced myself against this branch with the exact source shown, and **every one is latent on this
repository** (I checked: `docs/module-depth-map.json` holds no punctuation in any `nested`, no type
spelled two ways, and all three frontend fan lines are truthful). Latent is how attempts 1, 2 and 3
were sent back too.

The standard is the page's own sentence, printed directly above its list of over-readings:

> A floor whose edge a reader cannot see is not one they can trust, and neither is a page that
> promises a floor while holding a reading that is not one.

### Blocks "what it reaches ... derived from the source"

**1. `_reached_in` builds `called` with none of the guards `_calls_in` applies, so a fan line's
evidence is a false statement about the source.** `_reached_in` (typescriptsource.py:1803-1808)
filters only `_NOT_A_CALL` and a leading dot. `_calls_in` (1873) additionally filters
`_PRECEDED_BY_NEW` and a declaration, and the Java side refuses exactly this reading in
`_declares_rather_than_calls` (javasource.py:1676, applied at 1376 and 1562), whose own comment at
javasource.py:112 calls reading a declaration as a call the failure that file exists to prevent.

(a) A construction reported as a call:

    $ cat /tmp/src/failed.ts
    export class SignInFailed extends Error {}
    $ cat /tmp/src/use.ts
    import { SignInFailed } from './failed'
    export function go(ok: boolean): number { if (!ok) { throw new SignInFailed() } return 1 }

    src/use reach: [('module', 'failed', 'calls SignInFailed, which this file imports from it')]

The file calls nothing. It constructs. `calls_from` reads the same site as `builds one`, so the fan
and the flow contradict each other about one line of source. This is attempt 2's point 8 in a new
spelling — that one was `new api.Thing()` on a namespace import and is still correctly refused; a
named import is not.

(b) A name that appears **only inside a type declaration** reported as a call, which invents the fan
line outright:

    $ cat /tmp/src/repo.ts
    export function save(id: string): void {}
    $ cat /tmp/src/api.ts
    import { save } from './repo'
    export interface Api { save(id: string): void }
    export function nothing(): number { return 1 }

    src/api reach 1 -> src/repo, 'calls save, which this file imports from it'

`save` is a member signature in an interface. The truthful reach is 0. Neither shape is on the page's
list of readings that go the other way, so a reader auditing a fan against the source finds a line
the list does not account for.

**2. `_read_one_export`'s `type`/`interface`/`enum` branch takes the next token as a name without
checking it is one** (typescriptsource.py:890-894), so punctuation is recorded as a type the module
declares and drawn on its card:

    export type { Deposit } from './api'   -> nested = ['{']
    export type * from './other'           -> nested = ['*', 'from']
    export default class { ... }           -> nested = ['{']

(The `from` comes from the `_DECLARES` sweep at line 752.) All three compile. `nested` is published in
the graph and rendered on the card as "nested: …", so the page names a type called `{` that a reader
can go looking for and will never find — the objection attempt 1 raised as its point 6 and attempt 3
as its point 4.

**3. A `<` comparison against a capitalised imported name in a `.tsx` file is read as a JSX
construction** (`_A_JSX_ELEMENT`, typescriptsource.py:214):

    $ cat /tmp/src/f.tsx
    import { Max } from './limits'
    export function F(n: number): number { if (0 < Max && n > 1) { return 1 } return 0 }

    src/f reach: [('module', 'limits', 'builds one')]

`_opens_type_arguments` finds no identifier behind the `<` and `0` is not in `")]"`, so the name is
taken for an element. The file builds nothing. A comparison against an imported constant is ordinary
code.

**4. The page names a floor the tool does not have** (page.py:722, repeated in README.md). The fan
paragraph lists, among the readings that leave a fan shorter than the source, "an import of a
directory rather than of a file (./components, where the module is the index inside it)". But
`followed` already returns the `<specifier>/index` candidate, and I checked end to end:

    src/components/index.ts   export function Card(): number { return 1 }
    src/App.ts                import { Card } from './components'  ...  return Card()

    src/App reach 1 -> src/components/index, 'calls Card, which this file imports from it'

An entry in the page's own list of known gaps that is not real devalues the rest of the list, which is
the one thing on that page a reader is invited to check the fans against.

### Blocks "the same rules as for backend modules"

**5. `normalised` was extended for `|` and `&` on this attempt and never taught `{`, `}` or `=>`**
(javasource.py:2263), which are TypeScript's commonest type punctuation and which that function is now
applied to. Its own docstring names "one type spelled two ways" as the hazard it exists to prevent:

    export function a(x: { id: number }): void {}
    export function b(x: {id: number}): void {}
    -> parameters '{ id: number }' and '{id: number}'      <- two strings, one type

    export function e(f: (a: number) => number): void {}
    export function g(f: (a: number)=>number): void {}
    -> '(a: number) => number' and '(a: number)=>number'

Worse, the existing `" [" -> "["` rule fires on a leading brace with nothing balancing the trailing
one, so the card prints a spelling that is in neither the source nor any normal form:

    export function c(x: { [k: string]: number }): void {}
    -> parameter '{[k: string]: number }'

That last one is attempt 3's point 8 exactly — a type printed as something the file does not
contain — and it was the one you fixed by spelling types off the original text. This is the other
half of it.

**6. `async` is missing from `_NOT_A_CALL`** (typescriptsource.py:242), so an async arrow is read as a
call to a name called `async`. Confirmed against the parser directly:

    export const go = async (id: string): Promise<number> => { return 1 }
    -> declared.called == ('async',)

`await`, `typeof`, `void`, `catch` and the rest are all on that list. It draws a fan line in any file
that also imports something called `async`, which is legal — `async` is only contextually reserved.

**7. A `new X` written without argument brackets swallows the next call's brackets and reverses
evaluation order**, which is the order a flow is walked in. `_calls_in` (typescriptsource.py:1909)
calls `after_balanced(body, match.end(1))` directly; javasource.py:1577 has `_after_the_arguments` for
exactly this and checks that a `(` actually follows. Confirmed against the parser:

    export function f(): number {
      const t = new Thing
      other(1)
      return 1
    }
    -> evaluation order [(None, 'other', False), (None, 'Thing', True)]     <- reversed
    -> with `new Thing()` written instead: [(None, 'Thing', True), (None, 'other', False)]  (right)

**8. `_receivers_in` reads the whitespace a masked regular expression leaves as a gap between a
receiver and its dot** (typescriptsource.py:1848). Confirmed:

    export function f(s: string): boolean { return /x/.test(s) }
    -> declared.receivers == ('return',)

Harmless only because nothing can import a name called `return`; the same shape after any other masked
construct attaches a real name to a call the source never wrote on it.

### Smaller, and worth fixing while you are in here

**9. A default run now refuses entirely when either default root is absent** (cli.py:91-94). I copied
`backend/` and `scripts/` into an empty directory with no `frontend/` and ran the tool with no
arguments:

    WARNING module_depth_map.cli refused to run: no such source directory frontend/src

and neither output was written. Before this branch the single default always existed. A `--source` the
operator typed is worth refusing for; a default the tool chose is not. The `sourcesNotRead` shape
already in the document is the natural place to say a default was skipped and why.

**10. `tests/thisrepository/test_this_repository_is_read_whole.py` is still pointed at
`BACKEND_SOURCE` only** — all three classes, at lines 17, 86 and 264. That is the suite the spec
names for "the properties that must hold on the real repository": every source file parsed or
reported, every module scored or excluded by a named rule. This branch adds a second language to the
default graph and did not widen the one suite whose job is that nothing in this repository goes
missing. The determinism and committed-output suites *were* widened in the same diff; this one was
not, so a frontend file that silently stopped parsing would pass it.

**11. A multi-line type argument list in a declarator emits a phantom decline.** Found by me, not by
the code review. `_next_declarator`'s new "a `<` is a bracket only where its partner is on the same
line" rule (typescriptsource.py:1084-1094) is a documented trade, and the trade is sound — but it
costs a false line in the log `_decline`'s docstring calls the complete list of what was skipped:

    export const p = new Map<
      string,
      number
    >(), q = (x: number): number => x

    DEBUG ... export not read as a method line=1 export=p reason=what is assigned to it is a value ...
    DEBUG ... export not read as a method line=3 export=number reason=nothing is assigned to it here

There is no export called `number`. `q` is still measured and nothing moves, so this is cosmetic —
listed so it is not rediscovered as a mystery. `{ [k: string]: number }` in an annotation does the
same with `export=(`.

**12. Two countable claims in the source are wrong by inspection.** `typescriptsource.py:74` says
"Ten names come from the Java reading" over an import list of thirteen. `README.md` says two exports
are failed by name on purpose (`export declare`, `export namespace`) while `_NOT_READ_HERE`
(typescriptsource.py:317) holds three — `module` too, so `export module Foo {}` fails a file under a
rule the README does not mention. In a file whose whole standard is that a reader can check every
sentence against the source, these are the cheapest possible things to falsify.

### How to know you are done

Every point above is a fixture the suite does not have — the 718 tests pass with all twelve present.
`tests/thefrontendhonestly` is good work, so add to it rather than starting over. Hold at minimum:

- `throw new SignInFailed()` on a named import — reaching its module with evidence that says *builds*
  rather than *calls*, or reaching nothing, but never "calls" for a construction;
- `export interface Api { save(id: string): void }` beside `import { save } from './repo'` —
  asserting reach 0;
- `export type { X } from './o'`, `export type * from './o'` and `export default class { }` —
  asserting `nested` holds no punctuation;
- `if (0 < Max && n > 1)` in a `.tsx` against an imported `Max` — asserting it builds nothing;
- `{ id: number }` and `{id: number}`, and `(a: A) => B` and `(a: A)=>B`, each coming out as one
  string; and `{ [k: string]: number }` printed as the source wrote it;
- `export const go = async () => {}` — asserting nothing called `async` is on the reading;
- `new Thing` with no brackets followed by another call — asserting source order;
- `return /x/.test(s)` — asserting no receiver called `return`;
- a default run with one default root absent — asserting the other is still drawn;
- and widen `test_this_repository_is_read_whole.py` to both roots.

Then re-check page.py:722's fan paragraph and the matching README list against what the code
actually misses, and fix the two counts in point 12.

### What I confirmed fixed, so you do not redo it

Every point from attempt 3, reproduced by me on this branch:

1. `export function List(items: string[]): JSX.Element { return <ul>{items.map(i => <li key={i} />)}</ul> }`
   parses — `filesUnparsed=0` — and so does the self-closing element followed by `{x ? <Bar /> : null}`.
2. Two byte-identical modules differing only in file name, one named `format.ts` after a `format` it
   imports: both reach `src/util` with "calls format, which this file imports from it".
3. `export const flag = 1 < 2, b = (x: number): number => x` measures `b`;
   `new Map<string, number>(), b = 2` still reads right.
4. The overload set is two methods at cost 4, not three at cost 6, and the implementation is declined
   by name with a reason. `@throws` above the second signature lands on that one only; above the
   implementation it lands on both.
5. `const held: Till |\n  Receipt = make()` puts both names on the seam and reaches both modules.
6. `string|null` and `string | null` are one string.
7. `<Icons.Chevron />` is read, said at DEBUG (`qualified names read as reaching no module here
   names=Icons.Chevron`), and named on the page's fan paragraph beside `new api.Thing()`.
8. `'one' | 'two' | 'three'` and `Record<'a'|'b', number>` print as the source wrote them, and charge
   no type a reader cannot find.

And the properties this branch must not have broken, all checked by me:

- `cd backend && ./mvnw test` — exit 0, 113 tests, BUILD SUCCESS.
- `cd frontend && npm run typecheck` — exit 0 (Node 24.16.0 on PATH; system node is v16).
- `python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests` — **718 tests, OK**.
- Restoring `21e302b`'s five source files under this branch's tests fails **20 of the 141** tests in
  `tests/thefrontendhonestly`. The fixtures are real. Tree restored, `git status` clean.
- Two fresh runs are `cmp`-identical to each other **and** to the committed
  `docs/module-depth-map.{json,html}`; neither holds an absolute path; `git status` clean after a run;
  0.6s.
- Diffed all 74 modules against `ticket/07:docs/module-depth-map.json`: **no backend module moved**,
  exactly three added (`frontend/src/App`, `frontend/src/api`, `frontend/src/main`), flows identical.
- 500 lines of padding on a copy of the real `App.tsx` moved `lines` 1545 -> 2046 and **nothing else**
  on any of the three modules.
- `frontend/src/App` is 1545 lines — 5.4x the next longest file — at interface cost 1 over a fan of 1,
  leverage 1.0, and ranks **4th** by leverage behind Java modules of 60, 67 and 149 lines. Size is not
  depth here, which is the whole point of the ticket.
- The same shape in both languages: a Java `Till` and a TypeScript `till`, two methods, one documented
  refusal, three collaborators — **cost 7, refusalCost 2, reach 4, leverage 0.57, verdict "earns its
  keep" on both sides**, same refusals and findings, only the languages' own spellings differing.
- `typesEveryCallerAlreadyKnows` is `{"java": [43], "typescript": [21]}`; a Java domain type named
  `Response` is charged for (cost 4, mustBeLearned true).
- 90 files of legal TypeScript I wrote as three sweeps (50 general, 20 idiomatic React, 20 aimed at
  this attempt's own changes) plus 16 combinatorial JSX/masking lines and all 30 real third-party
  `.ts`/`.tsx` files under `frontend/node_modules` — every one compiled by
  `frontend/node_modules/.bin/tsc --noEmit --strict --jsx react-jsx`, exit 0 — all read,
  `filesUnparsed=0`. **I could not make this reading refuse legal TypeScript.** That part is done.
- The compounded participant edit the last three reviews said would take 1545 lines off the page —
  five components exported from a copy of the real `App.tsx`, `import './index.css'` moved second of
  four with no semicolons, and a new `{xs.map(x => <span key={x} data-chip={x} />)}{xs.length} /
  {xs.length}` component — gives `filesUnparsed=0`, `src/App` cost 25 with seven methods, and `main`
  still reaching `App`.
- Exclusions: `node_modules`, `dist`, `build`, `coverage`, `__tests__`, `*.test.ts`, `*.spec.tsx`,
  `*.d.ts` each named with the rule and what matched; two genuinely broken files failed by name with
  true reasons plus `WARNING ... the page is drawn from 1 of 3 source files`;
  `--source frontend/node_modules` gives `path=the root itself` and 0 modules. `scoring.json`'s
  `because` now says which side of its own argument `build`/`target`/`dist`/`coverage` fall on.
- The page driven with Playwright (chromium; console, pageerror and requestfailed subscribed; log at
  `.scratch/module-depth-map/logs/08-the-frontend-honestly.review.4.browser.log`) at 1024, 1280 and
  1440 wide, light and dark: **zero console errors, zero pageerrors, zero failed requests**,
  `scrollWidth == clientWidth` at every width, 74 cards, 9 package sections. `frontend/src` draws
  first with three FILE cards; `App` is a sliver of a bar over a fan of 1, `api` the widest bar (49)
  with a gold refusal band over no fan. All three panels open and **Escape closes each** (the card
  opens the panel on a double-click debounce — wait for `dialog.behind[open]`, 400ms is not enough).
  Flows mark **12 / 13 / 9** and Clear resets to 0.
- Page edge cases, all rendering clean with no console error and no "null" in the text: a java-only
  run ("written in java", longest module `ScheduledJobs` at 284 lines), a declined root ("Nothing was
  read."), and a run with two unparseable files (both named with their reasons).
- The running application is untouched by this branch (`git diff --name-only ticket/07..HEAD --
  backend frontend` is empty) and still works. Over HTTP against the running API: 404 "No customer
  banks here under that email address", 200 for `anke.peeters@example.be`, 201 for a 7.00 deposit,
  400 "A deposit has to be an amount of more than zero, and 0.00 is not.", 201 for a 3.00 withdrawal
  allocating against deposit 1, 400 "Cinema ticket costs 100 points, and this account has 27.",
  400 "There is nothing called \"NOT_A_REWARD\" in the rewards catalogue.", and 201 for a
  CHARITY_DONATION claim — with `i.d.s.deposits.DepositsService : deposit accepted depositId=1
  savingsAccountId=1 fromCurrentAccountId=1 amount=7.00 pointsEarned=7`,
  `i.d.s.deposits.WithdrawalsService : withdrawal accepted withdrawalId=1 ... amount=3.00` and
  `i.d.savingstreak.rewards.RewardsService : claim issued redemptionId=1 savingsAccountId=1
  reward=CHARITY_DONATION pointsSpent=10` in
  `.scratch/module-depth-map/logs/08-the-frontend-honestly.app.4.backend.log`.
- The tool's own logging follows CLAUDE.md: no `print` anywhere in `scripts/module_depth_map`, a
  `module_depth_map.*` logger per file, one INFO per business event with the values behind it
  (`largest module=... lines=1545 interfaceCost=1 reach=1 leverage=1.0`), WARNING with a reason on
  every refusal (`could not parse ... reason=...`, `source not read ... rule=... matched=...`), DEBUG
  for the inputs behind a decision (`interface read name=... cost=...`, `export not read as a method
  ... reason=...`). No WARNING or ERROR from `module_depth_map` over the real source.
- The application still logs no `io.dataroots.savingstreak` WARN when it refuses — the reason for
  every 400 and 404 above is only visible through Spring's `ExceptionHandlerExceptionResolver` at
  DEBUG. That is the pre-existing gap recorded at the end of ticket 07 and named by the attempt-3
  reviewer; this branch touches no application source and it is not this branch's doing.

## Review feedback - attempt 5

**All twelve points from attempt 4 are genuinely fixed.** I reproduced every one against this branch
and every one now behaves; the list is under "What I confirmed fixed" at the bottom so you do not redo
any of it. Both lab checks, the 763-test suite, determinism, the no-regression diff against ticket/07
and the page itself are all green. Four of the seven criteria are met and I saw them work.

What sends this back is the same sentence that sent back attempts 1, 2 and 3 — `README.md:252`, and
this file's own module docstring:

> And legal TypeScript is never failed, which matters more here than on the Java side: a failed file
> is a whole module off the page and every fan line into it gone with it.

Point 1 below is one lowercase English word in a paragraph of visible UI prose. It compiles under
`tsc --strict` with no output at all, and this tool refuses the whole file with a reason that is
untrue of the source. Points 2 to 5 each lose or invent something on a card with no line in any log
saying so. I reproduced **every** item below myself against this branch with the exact source shown;
none is a code reading.

### File-fatal: the whole module leaves the page

**1. The word `import`, written as ordinary prose inside JSX, fails the file — at any depth.**

    $ cat /tmp/src/help.tsx
    export default function Help(): JSX.Element {
      return <p>You can import your statements here.</p>
    }
    $ frontend/node_modules/.bin/tsc --noEmit --strict --jsx react-jsx --target es2022 \
        --module esnext --moduleResolution bundler --skipLibCheck --lib es2022,dom help.tsx
    $ echo $?
    0
    $ python3 scripts/module-depth-map.py --source /tmp/src --graph /tmp/g.json --page /tmp/p.html
    WARNING module_depth_map.graph could not parse source file root=src language=typescript
      path=help.tsx reason=the import on line 2 could not be read: it names no module in either
      of the two forms this tool reads
    WARNING module_depth_map.cli the page is drawn from 0 of 1 source files

There is no import statement on line 2. `_refuse_unreadable_imports` (typescriptsource.py:735-744)
runs `_AN_IMPORT.finditer` over the whole masked file, and this reading deliberately does not mask
JSX text, so any bare lowercase `import` in prose is a statement it then cannot read.

**Why this is not a corner case.** I made the single most ordinary edit a participant can make to
this repository — one sentence of copy on the sign-in screen of a *banking* application:

    Move money into savings, earn a point for every whole euro.
    -> Move money into savings, earn a point for every whole euro. You can import your statements here.

`tsc --strict` on the edited `App.tsx`, `api.ts` and `main.tsx`: **exit 0**. The tool:

    WARNING module_depth_map.graph could not parse source file root=realedit language=typescript
      path=App.tsx reason=the import on line 185 could not be read: it names no module in either
      of the two forms this tool reads
    INFO  module_depth_map.graph graph built ... filesSeen=3 filesParsed=2 filesUnparsed=1 modules=2

All 1545 lines of `App.tsx` leave the page, and `main`'s only fan line goes with them. "Import your
statements", "import", "export to CSV" are the plainest possible copy in this domain.

I narrowed the shape for you. These fail: `<p>you can import statements</p>`, `<p>import</p>`,
`<p>we import and we export</p>`. These are fine, so a fixture has to be the lowercase whole word
specifically: `<p>Import statements</p>` (capitalised), `<li>Imports and exports</li>` (plural),
`<p>{'You can import things'}</p>` (inside a string, which is masked), and the same words in a `//`
comment.

**2. The word `export` in JSX prose written at brace depth 0 fails the file — which is the
entry-point shape.**

    $ cat /tmp/src/entry.tsx
    import { createRoot } from 'react-dom/client'
    createRoot(document.getElementById('root')!).render(
      <main>Use the export button</main>,
    )
    -> tsc --strict exit 0
    WARNING ... path=entry.tsx reason=the export on line 3 could not be read: it is followed by
      'button', which this tool has no reading of

`_exports_in` sweeps for `export` and `_read_one_export` fails the file on a word it has no reading
of (typescriptsource.py:1024-1027). Top-level JSX is written in exactly one kind of file — the entry
point — and this repository's `main.tsx` is that file. Note the asymmetry with point 1, which is
worth a fixture each: the same prose *inside* a component body
(`export function F(): JSX.Element { return <p>use the export button</p> }`) parses fine.

**Both of these contradict this file's own stated trade,** which is the sharpest way to see them.
`typescriptsource.py:39-43` says JSX text is read as source because:

> It can only *add* a name to what a module reaches ... so the risk is a fan line to a card the
> source calls nothing on. Masking it instead would need a JSX parser, and a JSX parser that got it
> wrong would blank real code — the failure that costs a module rather than adding to one.

Reading JSX text as source is doing exactly the thing that paragraph says masking was avoided in
order to prevent. Whatever the fix is, that paragraph has to become true or change.

### Read wrongly, with nothing reported

**3. A type-only default import is recorded as a type the module declares, and drawn on its card.**

    $ cat /tmp/src/u.ts
    import type Customer from './api'
    export function f(c: Customer): void {}

    src/u  nested = ['Customer']        <- the card names a type this file does not contain

The unexported-declarations sweep in `_exports_in` (typescriptsource.py:788) matches `_DECLARES`
against `import type X from './y'`. This is the same objection attempt 4 raised as its point 2 and
attempt 1 as its point 6: a name on a card a reader can go looking for and will never find. It also
lands in `declares`, so a same-named value import has its static-import fan line suppressed.

**4. A JSDoc `@throws` above an unassigned declaration lands on the next function, and the module is
then falsely accused.**

    $ cat /tmp/src/x.ts
    /**
     * @throws Boom
     */
    let pending

    export function f(): void {}

    -> refusals  [('Boom', documented=True, raised=False)]
    -> findings  ['documented but never raised — Boom']
    -> interface cost 3, refusalCost 2

`_NOTHING_BUT_WORDS` (typescriptsource.py:2132) is `[\s\w$]*`, which admits any run of words between
a JSDoc block and a declaration — not only `export`/`default`/`async` as its docstring says. The
page's own words, which attempt 1 quoted for the mirror-image defect: "A machine that accused a
module which kept its word would stop being read." This one accuses a module that said nothing at
all, and inflates the interface cost the leverage denominator is read from.

**5. An exported arrow whose written type carries a generic default vanishes from the interface, and
the reason logged is untrue of the source.**

    $ printf 'export const ring: <T = string>(a: T) => T = (a) => a\n' > /tmp/src/y.ts
    -> cost 0, methods []
    -> DEBUG ... export not read as a method line=1 export=ring reason=what is assigned to it is
       a value rather than a function ...

`_binding_from` (typescriptsource.py:1272) walks to the first `=` after the annotation, and the `=`
inside `<T = string>` is taken for the assignment. `ring` is a function a caller can import. This is
the "interface *cheaper* than the source makes it" direction `_exports_in`'s own docstring says it
must never be wrong in, and the declined line — the one `_decline`'s docstring calls the complete
list of what was skipped — says something false about the file.

**6. A concise arrow reports a body it read nothing out of, so the fan and the flow disagree.**

    export const load = () => get('x')     -> method.calls = ()      declared.called = ('get',)
    export const load = () => { return get('x') }
                                           -> method.calls = ((None,'get',False),)

`_arrow_from` (typescriptsource.py:1360) reads calls only when the body is a brace block, but records
`has_a_body=True` either way; `_binding_from`'s bare-parameter arrow (line 1297) hardcodes
`calls=()` the same way. The fan is right because `reach_of` reads `declared.called`, so nothing on
this repository's page is wrong today — but `calls_from` is what a flow is walked with, so a flow
whose step is a concise arrow stops one module short with nothing saying the body was not read. That
is the disagreement your own note on attempt-4 point 8 says cannot happen ("applied in `_receivers_in`
*and* `_calls_in` so a flow and a fan cannot disagree about one line"). Concise arrows are how React
writes handlers and small api wrappers.

### Smaller, and worth fixing while you are in here

**7. Parse time is quadratic in file size.** `_PRECEDED_BY_NEW` and `_A_NAME_BEHIND_IT` are
`$`-anchored patterns run with `.search()` over a fresh slice of the whole file prefix, once per call
site, receiver and JSX element (typescriptsource.py:1189, 1902, 1935, 2035, 2071, 2087). Measured:
0.20s at 50 KB, 0.74s at 100 KB, 2.85s at 200 KB — about 14x for 4x the input. Nothing here is
anywhere near that today (the whole run is 0.6s), but spec user story 17 is "runs in seconds", and
`before.endswith("new")` plus the boundary check, or `.search(before[-4:])`, costs nothing.

**8. The Java `typesEveryCallerAlreadyKnows` list gained `Record`, `Date` and `Error`**
(scoring.json:41). The schema comment justifies the per-language split by saying a Java module whose
seam is spelled with a domain type called `Response` was charged nothing for it off a list that meant
the platform type of that name. `Record`, `Date` and `Error` are at least as likely to be domain
names in a banking application. None of the three moves a number here today, so this is latent rather
than a regression — but it is the same hazard the split was written to remove.

**9. The page never says which language's list decided a card.** graph.py:975 justifies the split as
"a reader checking a card's types against this has to be able to see which list decided it", but
page.py:587 still describes it as one list and renders neither. A reader looking at a TypeScript
card's `Response` (free) beside a Java card's `Response` (must be learned) has nothing on the page
that explains the difference.

**10. `_place_of` splits the relative path on `"/"`** (typescriptsource.py:625) while
`graph._files_under` builds it with `os.path.relpath`, which uses `os.sep`. On Windows every
TypeScript module id would carry a backslash, no import specifier would resolve, and every TypeScript
fan line would disappear. The Java side is immune because its id comes from the `package` line, so
this is new with this change. `graph.py:271`'s `declined` entries have the same gap, while
`cli.py:110` normalises `os.sep` for `rootsNotRead`.

**11. Seven near-identical bracket-depth scanners.** `_next_declarator` (1147),
`_end_of_the_declaration` (1216), `_up_to_the_arrow` (1396), `_after_angles` (1456),
`_first_at_depth_zero` (1592), `_first_in_the_open` (1725) and `_up_to_the_assignment` (1789) each
re-implement the same loop, differing only in which characters are brackets, what they stop at and
whether they take a bound. The `ends_an_arrow` guard is in five of them and not in
`_first_in_the_open`; `_first_at_depth_zero` counts `{` as a stop while `_first_in_the_open` counts
it as a bracket. Attempt 2's review already sent this back once as "four scanners, two of which know
and two of which do not"; it is seven now. One parameterised scanner taking (brackets, stops, limit)
would carry all seven.

**12. `here` is bound twice in `_files_under`** (graph.py:172 and 186) to the root's basename and to
each walked directory's realpath. Not a live bug — the first use ends before the loop — but the
natural future edit (moving the root-name check into the walk, where every other directory is matched
against the same rule) would silently compare a realpath against `declined.directories`.

### How to know you are done

Every point above is a fixture the suite does not have — the 763 tests pass with all twelve present.
`tests/thefrontendhonestly` is good work, so add to it rather than starting over. Hold at minimum:

- `<p>You can import your statements here.</p>` inside a component, asserting `filesUnparsed=0`;
  and the four near-misses that must keep working, so the fix is not a blanket one —
  `<p>Import statements</p>`, `<li>Imports and exports</li>`, `<p>{'You can import things'}</p>`,
  and `// you can import things`;
- top-level JSX carrying the lowercase word `export` — the stock `createRoot(...).render(<main>Use
  the export button</main>,)` shape — asserting the file is read;
- a real `import './index.css'` and a real `export function` still read, in the same file as prose
  carrying both words, so the fix does not turn the sweep off;
- `import type Customer from './api'`, asserting `nested` holds no `Customer`;
- a JSDoc `@throws` above `let pending` followed by an undocumented `export function`, asserting no
  refusal on the seam and no finding;
- `export const ring: <T = string>(a: T) => T = (a) => a`, asserting one method;
- `export const load = () => get('x')` beside the braced form, asserting the same calls out of both
  — or a line in the log saying the body was not read.

Then make `typescriptsource.py:39-43` true again, and re-check `README.md:252` against what the code
keeps.

### What I confirmed fixed, so you do not redo it

Every point from attempt 4, reproduced by me on this branch:

1. `throw new SignInFailed()` on a named import reaches `src/failed` with evidence **"builds one"**,
   not "calls". `export interface Api { save(...) }` beside `import { save } from './repo'` reaches
   **0**.
2. `export type { Deposit } from './api'`, `export type * from './api'` and `export default class { }`
   all give `nested = []` — no punctuation on a card.
3. `if (0 < Max && n > 1)` in a `.tsx` against an imported `Max` builds nothing. I checked both
   directions: comparisons after a call, an index, a string, a digit and a name all read as
   comparisons, while ternary arms, array elements, `&&` and a plain return all still read as JSX.
4. `./components` → `components/index` is followed (`calls Card, which this file imports from it`);
   the false clause is off the fan paragraph.
5. `{ id: number }` / `{id: number}` and `(a: number) => number` / `(a: number)=>number` each come
   out as one string, and `{ [k: string]: number }` prints as the source wrote it.
6. `export const go = async (...)` reads no call to anything called `async`.
7. `new Thing` with no brackets followed by `other(1)` gives source order `[Thing(builds), other]`,
   the same as `new Thing()`.
8. `return /x/.test(s)` gives `receivers = ()`.
9. A default run with `frontend/src` absent writes both outputs, logs `INFO ... default source not
   read path=frontend/src reason=...`, publishes `source.rootsNotRead`, and the page renders
   "frontend/src — one of the directories this tool reads when no --source is given, and there is
   none of that name in this repository. Nothing under it is drawn below." with no `null` anywhere.
10. `test_this_repository_is_read_whole.py` reads both roots — all three classes go through
    `this_repository()`, which builds from `BACKEND_SOURCE` and `FRONTEND_SOURCE`.
11. `new Map<\n string,\n number\n>(), q = ...` logs no export called `number`;
    `{ [k: string]: number }` logs none called `(`; `1 < 2, b = ...` still measures `b`.
12. The import comment says fourteen and breaks them down six/eight, which is what the import list
    holds; `README.md:332` names three exports failed by name including `export module`.

And the properties this branch must not have broken, all checked by me:

- `cd backend && ./mvnw test` — exit 0, 113 tests, BUILD SUCCESS.
- `cd frontend && npm run typecheck` — exit 0 (Node 24.16.0 on PATH; system node is v16).
- `python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests` — **763 tests, OK**.
- Two fresh runs are `cmp`-identical to each other **and** to the committed
  `docs/module-depth-map.{json,html}`; neither holds an absolute path; `git status` clean after a
  run; 0.71s.
- Diffed all 74 modules against `ticket/07:docs/module-depth-map.json`: **no backend module changed
  in any field**, exactly three added (`frontend/src/App`, `api`, `main`), flows byte-identical.
- 500 lines of padding on a copy of the real `App.tsx` moved `lines` 1545 → 2046 and **nothing else**
  on any of the three modules — cost, reach, leverage, methods, findings and verdict all identical.
- `frontend/src/App` is 1545 lines — 5.4x the next longest file — at interface cost 1 over a fan of 1,
  leverage 1.0, and ranks **4th of 34** scored modules by leverage, behind Java modules of 60, 67 and
  149 lines. Size is not depth here, which is the whole point of the ticket.
- The same shape in both languages: a Java `shop.Till` and a TypeScript `t/till`, two methods, one
  documented refusal, three collaborators plus the refusal — **cost 7, costWithoutRefusals 5,
  refusalCost 2, reach 4, leverage 0.57, verdict "earns its keep" on both**, same methods, types,
  refusals, reaches and findings, only the languages' own spellings differing.
- `typesEveryCallerAlreadyKnows` is `{"java": [...], "typescript": [...]}`; a Java domain type named
  `Response` is charged for (cost 4, mustBeLearned true) while a TypeScript `Response` is free.
- Exclusions: `node_modules`, `dist`, `build`, `coverage`, `target`, `__tests__`, `*.test.ts`,
  `*.spec.tsx`, `*.d.ts` each named with the rule and what matched, in the log **and** in
  `source.notRead.paths`, **and** on the page. Two genuinely broken files failed by name with true
  reasons (`a block comment opened on line 1 is never closed`, `braces do not balance: 1 unclosed at
  end of file`), both named on the page, plus `WARNING ... the page is drawn from 1 of 3 source
  files`.
- **50 files of legal TypeScript I wrote myself** — 30 general (optional/rest/default parameters,
  generic defaults, conditional and mapped types, template-literal types, `as const`/`satisfies`,
  optional chaining, private fields and accessors, `enum`/`const enum`, `export * as`, labelled
  tuples, assertions, async generators, division-vs-regex, template literals holding braces, JSX
  spread, fragments with `.map` and `key={}`, `<T,>` arrows in `.tsx`, overloads, index signatures,
  namespace/default/renamed imports, multi-line signatures, decorators, string-literal unions) and 20
  idiomatic React (hooks, list keys, conditional render, forms, generic fetch with `@throws`,
  context, reducers with `switch`, `Intl` money formatting, JSX prose full of apostrophes, nested
  generics, a barrel re-export, a class component with an error boundary, an entry point, async
  effects with `AbortController`, `FC<...>` arrows) — every one compiled by
  `frontend/node_modules/.bin/tsc --noEmit --strict --jsx react-jsx`, exit 0. The tool read all 50,
  `filesUnparsed=0`. **The only shape in that sweep it refused is point 1 above**, and it refused it
  only because none of my 50 files happened to put the lowercase word `import` in visible prose.
- I could not produce an over-read fan line. A method on an object sharing an import's name, the name
  in a string, in a comment, in a type position only, as an object key, and shadowed by a local all
  reach nothing; a template literal `${go(1)}` and a plain call both reach correctly. Every floor the
  page claims is real: `<Icons.Chevron />` reaches nothing, `new api.Thing()` reaches nothing,
  `ok ? go(x) : none` goes uncounted, and a plain `go(x)` still draws its line.
- The page driven with Playwright (chromium; console, pageerror and requestfailed subscribed; log at
  `.scratch/module-depth-map/logs/08-the-frontend-honestly.review.5.browser.log`) at 1024, 1280 and
  1440 wide, light and dark: **zero console errors, zero pageerrors, zero failed requests**,
  `scrollWidth == clientWidth` at every width, 74 cards, 9 package sections, no `null`/`undefined`/
  `NaN` in the visible text. `frontend/src` draws first with three FILE cards; `App` is a sliver of a
  bar over a fan of 1, `api` the widest bar (49) with a gold refusal band over no fan. All three
  panels open by click **and** by focusing the name button and pressing Enter, and Escape closes each.
  Flows mark **12 / 13 / 9** (`li.module.onTheFlow`) and Clear resets to 0. "What a line count is
  worth here" reads *"The longest module here is frontend/src/App, at 1545 lines of typescript. It
  presents 1 method, costing 1 unit of interface, over a fan of 1 thing — leverage 1"*, every value of
  which is `scoring.largest` in the graph.
- Page edge cases, all clean with no console error and no `null` in the text: a java-only run (lede
  reads "written in java"), a run with two unparseable files, a declined root ("Nothing was read."),
  and a default run with `frontend/src` absent.
- The tool's own logging follows CLAUDE.md: **no `print`, no `sys.stdout`** anywhere in
  `scripts/module_depth_map`; a `module_depth_map.*` logger per file; stdout empty on a real run; one
  INFO per business event with the values behind it (`largest module=frontend/src/App
  language=typescript lines=1545 interfaceCost=1 reach=1 leverage=1.0`, `graph built ... filesSeen=74
  filesParsed=74 filesUnparsed=0`, `widest interface module=frontend/src/api cost=49 methods=11
  typesToLearn=7`); WARNING with a reason on every refusal; DEBUG for the inputs behind a decision
  (`angle brackets not read as an element name=Customer reason=a value stands in front of them ...`,
  `name not read as a receiver name=Intl reason=new is written in front of it ...`, `qualified names
  read as reaching no module here names=Intl.DateTimeFormat,Intl.NumberFormat`). No WARNING or ERROR
  from `module_depth_map` over the real source.
- The running application is untouched by this branch (`git diff --name-only ticket/07..HEAD --
  backend frontend` is empty) and still works. Over HTTP against the running API: 404 "No customer
  banks here under that email address", 200 for `anke.peeters@example.be`, 201 for a 7.00 deposit,
  400 "A deposit has to be an amount of more than zero, and 0.00 is not.", 201 for a 3.00 withdrawal
  allocating against deposit 1, 400 "Cinema ticket costs 100 points, and this account has 7.", 400
  `There is nothing called "NOT_A_REWARD" in the rewards catalogue.`, and 201 for a CHARITY_DONATION
  claim — with `i.d.s.deposits.DepositsService : deposit accepted depositId=1 savingsAccountId=1
  fromCurrentAccountId=1 amount=7.00 pointsEarned=7`, `i.d.s.deposits.WithdrawalsService : withdrawal
  accepted withdrawalId=1 ... amount=3.00` and `i.d.savingstreak.rewards.RewardsService : claim
  issued redemptionId=1 savingsAccountId=1 reward=CHARITY_DONATION pointsSpent=10` in
  `.scratch/module-depth-map/logs/08-the-frontend-honestly.app.5.backend.log`. Driving :5173 refused
  an unknown address with "No customer banks here under that email address" and signed
  `anke.peeters@example.be` in to a fully styled page showing both savings accounts (€24,00 /
  17 points) and the rewards catalogue.
- The application still logs no `io.dataroots.savingstreak` WARN when it refuses — every 400 and 404
  above is visible only through Spring's `ExceptionHandlerExceptionResolver` at DEBUG. That is the
  pre-existing gap recorded at the end of ticket 07; this branch touches no application source and it
  is not this branch's doing.
