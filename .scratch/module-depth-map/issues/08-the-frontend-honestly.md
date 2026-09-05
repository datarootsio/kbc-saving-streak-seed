# 08: The frontend, honestly

**What to build:** The frontend appears on the page alongside the backend, analysed by the same rules
at file grain, so the picture is of the application rather than of half of it.

Its result is shown rather than smoothed over. The largest file in this repository presents almost
nothing to a caller while containing a great deal, and under the measure this page uses it scores as
low-leverage for its size. That outcome is the clearest demonstration available of what the page
exists to teach: depth is complexity hidden behind a small interface, not a lot of code. Drawing the
frontend as one tidy box would be a picture that lies by omission.

**Blocked by:** 03 (Reach, and the fan).

Status: needs-info

- [x] Frontend source is analysed at file grain and appears on the page beside the backend modules
- [ ] Interface cost, reach and depth are computed for frontend modules by the same rules as for backend modules
- [ ] What a frontend module exports, and what it reaches, are both derived from the source
- [x] A large frontend module presenting a small interface is not reported as deep on account of its size
- [x] The page makes the frontend's shape visible rather than collapsing it into a single unscored box
- [ ] Frontend source the tool cannot parse is reported loudly and named, as backend source is
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
