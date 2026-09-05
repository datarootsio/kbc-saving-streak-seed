# 06: Behind the shape

**What to build:** Clicking a module opens everything standing behind its shape: each method with
what it costs a caller, each thing the module reaches, which modules call it, its deletion-test
verdict, and any findings against it. The shape makes the claim; this is where a reader checks it.

Everything shown here already exists in the graph document. Nothing is computed at render time, so
what a reader sees in the panel and what a later tool reads from the graph are the same facts.

**Blocked by:** 04 (The deletion test) and 05 (Refusals as their own band).

**Status:** needs-review

- [x] Clicking any module opens a panel about that module, and closing it returns to the full picture
- [x] The panel lists each method reachable from outside, with the cost each one puts on a caller
- [x] The panel itemises everything the module reaches, naming each one
- [x] The panel names every module that calls this one
- [x] The panel carries the module's deletion-test verdict and the numbers behind it
- [x] The panel lists every finding against the module, or says plainly that there are none
- [x] An excluded module's panel names the rule that excluded it rather than showing a score
- [x] Every value in the panel is read from the graph document, with nothing computed at render time
- [x] The panel is reachable and readable by keyboard


## Review feedback - attempt 1

Reviewed `ticket/05-refusals-as-their-own-band..ticket/06-behind-the-shape` (`b6798db`, `87eba3f`).
Almost all of this holds up: I drove all 71 module panels in chromium and cross-checked every
method, cost, type, refusal, reach entry, `matched` sentence, reached-module id, caller id,
verdict, verdict count and finding against `docs/module-depth-map.json`, and found **zero**
disagreements, zero console messages and zero page errors. The excluded-module branch is right
for all 36 never-scored modules. What sends it back is one reproducible bug in how the panel
opens, plus two smaller ones found beside it.

### 1. Blocking: the panel keeps the previous module's scroll position

`openBehind` (scripts/module_depth_map/page.py) empties and refills `behindBody`, but never
resets `behindBody.scrollTop`. The element outlives every open, so the offset a reader left on
one module is where the *next* module's panel opens.

Expected: clicking a module opens its panel at the top, on "What it costs a caller".
Saw: it opens part-way down, with the whole interface section — the cost line, the depth line,
every method and its price — scrolled off above the fold.

Reproduce (chromium, `docs/module-depth-map.html` over `file://`, viewport 1280x700):

1. Click the `AccountsService` card; its panel is 1496px of content in a 636px body.
2. Wheel down inside the panel to the callers list (`behindBody.scrollTop` reaches 860).
3. Press Escape.
4. Click the `WithdrawalsService` card.
5. `document.querySelector('.behindBody').scrollTop` is **758**, not 0. The panel opens on
   "The deletion test" and "Findings against it"; "What it costs a caller" is above the view.

Screenshot of exactly that state:
`.scratch/module-depth-map/logs/06-behind-the-shape.review.1.reopen.png`.

`.behindHead` is sticky, so the heading stays pinned and the panel does not *look* broken — which
is what makes this worth fixing rather than living with: a trainer clicking through modules on a
projector reads the second module's findings while believing they are looking at the top of its
panel. Every other piece of panel state here (content, focus) is managed explicitly; this one is
the only one that is not.

### 2. The panel drops the caveat the card carries for an interface cost of 0

`drawInterface` appends `" — nothing this bar counts, which is not the same as nothing to learn"`
when `interface.cost === 0`, with a comment saying a naked zero claims more than the bar ever
does. `drawBehindInterface` has no such branch, so the panel — the place the ticket bills as
"where a reader checks it" — is the one place the caveat is missing.

Four scored modules on the committed page hit this: `ClockRefused`, `JobFailed`, `JobRefused`,
`SchedulingIsOn`. Clicking `ClockRefused`:

- card: `0 to learn: 0 methods, 0 parameters, ... — nothing this bar counts, which is not the same as nothing to learn`
- panel: `interface cost 0 — 0 of it is what a caller must learn besides the refusals, and 0 of it is the refusals it can answer with`

Screenshot: `.scratch/module-depth-map/logs/06-behind-the-shape.review.1.zero-cost.png`.

### 3. A drag-selection released outside the panel closes it

The backdrop handler is `if (event.target === panel) { shutBehind(); }`. A `click` event's target
is the nearest common ancestor of the `mousedown` and `mouseup` targets, so selecting text inside
`.behindBody` and releasing the pointer past the panel's edge makes that ancestor the `<dialog>`
itself: the panel closes and the selection goes with it. Reproduced on the `DepositsService`
panel — mouse down at (panel.x+60, panel.y+120), move to (panel.x+width+80, panel.y+150), mouse
up: `dialog.open` is `false`. This panel is full of fully-qualified caller ids and method
signatures that a reader will want to copy, so it is the interaction most likely to hit it.
Requiring the `mousedown` to have landed on `panel` too would fix it.

### 4. Non-blocking observation: the no-`<dialog>` fallback cannot work

`openBehind`/`shutBehind` fall back to `setAttribute("open")` / `removeAttribute("open")` for a
browser without `showModal`. In such a browser `<dialog>` is an `HTMLUnknownElement`: the `open`
attribute is inert and the stylesheet has no rule tying visibility to it (`dialog.behind` sets no
`display`, and there is no `[open]` selector anywhere in `_STYLE`). The empty panel shell would
render in the page flow from load and nothing would ever dismiss it. Unreachable in any browser
released since 2022, so not blocking — but the branch does not do what its comment says, and a
`dialog.behind:not([open]) { display: none; }` rule is what it is missing.

### What I checked and found good, so it does not need redoing

- All 71 panels, every value cross-checked against the graph document: no mismatch anywhere.
- Excluded modules: rule, what matched and the rule's reason are printed, and no `interface cost`
  and no `per unit of interface` appear on any of the 36 never-scored panels.
- Keyboard: Tab reaches the module name buttons in document order, Enter opens, focus lands on
  `.behindBody`, Tab reaches Close, Enter on Close closes, Escape closes, and focus returns to the
  originating card every time (checked on all 71).
- Determinism: two fresh renders are byte-identical to each other and to the committed
  `docs/module-depth-map.{json,html}`.
- `./mvnw test` 113 pass, `tsc --noEmit` clean, `python3 -m unittest discover -t scripts -s scripts/module_depth_map/tests` 482 pass.

## Review feedback - attempt 2

Reviewed `ticket/05-refusals-as-their-own-band..ticket/06-behind-the-shape` (`53e34e6` on top of
`b6798db`). **All four points from attempt 1 are genuinely fixed** — I reproduced each one's
original scenario and each now behaves. What sends it back is two reproducible defects in *how the
panel opens and closes*, both in this branch's own new code, and one of them is the attempt-1
point 3 bug living one element up.

### 1. A drag to select text on a card opens the panel and throws the selection away

`page.py:1349` — `item.addEventListener("click", function () { openBehind(module, opens); });`

This is the same gesture attempt 1 sent the ticket back for, on the card rather than the panel. The
panel now reads the press and the release separately and both have to land on the backdrop, with a
paragraph of comment and a test explaining why. The card two hundred lines below still reads only
the `click`, and a `click`'s target is the nearest common ancestor of press and release — so any
drag that starts and ends inside a card fires it.

The card is worse than the panel was. On the panel a stray drag lost a selection; on the card it
lost the selection *and* threw a modal over the page. A trainer highlighting a module name on a
projector, or anyone copying `pass-through — coordinates 5 things behind 11 methods` out of a card,
gets a panel instead.

Expected: dragging across text on a card selects that text and leaves the page alone; a click opens
the panel.
Saw: the panel opens and `window.getSelection()` is `""`.

Reproduce (chromium, `docs/module-depth-map.html` over `file://`, viewport 1280x700):

1. Scroll the `AccountsService` card into view; its box is x=371 y=156 w=263 h=389.
2. Mouse down at (card.x+12, card.y+14) — on the module name.
3. Move to (card.x+150, card.y+16), mouse up.
4. `document.querySelector('dialog.behind').open` is `true` and `String(getSelection())` is `""`.

The same over the card body rather than the name (step 2 at card.y+210, step 3 at card.y+213) does
the same thing. Screenshot of exactly that state:
`.scratch/module-depth-map/logs/06-behind-the-shape.review.2.card-drag.png`.

The rule the panel already uses is the fix, and it is written in this file: require the press and
the release to have landed on the card too. `openBehind` has to keep working from a real click and
from the name button's Enter, which is a `click` with no mouse events at all — so a `mousedown`
that arms and a `click` that checks, rather than moving the card to `mouseup`.

### 2. Right-clicking the backdrop closes the panel

`page.py:1070` — `panel.addEventListener("mouseup", function (event) { if (event.target === panel
&& pressedOn === panel) { shutBehind(); } });`

`mousedown`/`mouseup` fire for every button, so button 2 dismisses the panel. The comment above it
says this is "the same rule the browser's own light dismiss uses" — light dismiss is primary-button
only, so the code does not do what the comment claims, and in this file a comment that overclaims
is the defect.

Expected: right-clicking beside the panel opens a context menu and leaves the panel open.
Saw: the panel closes.

Reproduce: open the `DepositsService` panel, then right-click 60px past the right edge of
`.behindBody` — `document.querySelector('dialog.behind').open` is `false`. A right-click *inside*
the panel correctly leaves it open, so the two disagree.

An `event.button !== 0` guard on the `mousedown` is what makes the code match its own comment.

### 3. Non-blocking: the panel drops the two self-contradiction guards the card carries

`drawReach` warns when `module.depth.reach !== module.reach.count`, and `drawVerdict` warns when
`test.reach`/`test.callers` disagree with `module.reach.count`/`module.callers.count`. In the panel,
`drawBehindInterface` (`page.py:1164`) prints `module.depth.reach` and `drawBehindReach` prints
`module.reach.count` in two separate sections with no cross-check, and `drawBehindVerdict`
(`page.py:1295`) prints its three counts with none either. Zero disagreements in the committed
document, so this is a gap rather than a live bug — but the panel is billed as where a reader checks
the card, which makes it the one place that should not be able to print two disagreeing numbers in
silence. Worth a line while you are in there; not what sends this back.

### What I checked and found good, so it does not need redoing

- **All four attempt-1 fixes, each reproduced from its original scenario.** The `AccountsService`
  panel is 1496px of content in a 636px body; wheeled to `scrollTop=860`, Escape, click
  `WithdrawalsService` → `scrollTop` **0**, first section "What it costs a caller"
  (`…review.2.reopen.png`). All 71 panels opened in turn with the previous one deliberately left
  scrolled to its bottom: `notAtTop=[]`. The zero caveat is one string on both card and panel for
  all four modules the bar prices at 0 — `ClockRefused`, `JobFailed`, `JobRefused`, `SchedulingIsOn`
  — and on no other panel (`…review.2.zero-cost.png`). A selection dragged out of the panel and
  released past its edge: `open=True` with 168 characters still selected
  (`…review.2.drag.png`); backdrop→inside drag leaves it open; a plain left click on the backdrop
  still closes it. The fallback with `showModal`/`close` deleted: `display: none` at load,
  `display: block` with the `open` attribute and `scrollTop=0` on click, hidden again and focus back
  on the card after Escape.
- **Every value in all 71 panels, cross-checked against `docs/module-depth-map.json`**: heading,
  kind/language/package, path and line count, the interface-cost split, the depth line, every method
  signature with its cost and documented refusals, every type with its side of the list, every
  refusal sentence, every reach entry with its kind, matched clause and module id, every caller id,
  the verdict with its three counts and its reason, and every finding. **Zero disagreements.**
- **Excluded modules**: all 36 never-scored panels print the rule, what matched and the rule's
  reason, and none of them prints `interface cost`, `per unit of interface` or a type's pricing.
- **Nothing computed at render time**: the `drawBehind*` functions contain no arithmetic at all —
  only `.length === 0` emptiness tests — and every number comes from a document field.
- **Keyboard**: the 71 name buttons are in the graph's document order; Enter opens each panel at the
  top on the right module with `aria-labelledby` resolving to its heading, focus lands on
  `.behindBody`, Tab reaches Close, Enter on Close closes, Escape closes, and focus returns to the
  originating card — checked on all 71.
- **Layout**: no horizontal overflow on the page or the panel body at 720, 1024, 1280 or 1440; dark
  theme reads correctly (`…review.2.panel-DepositsService.dark.png`).
- **Checks**: `./mvnw test` 113 pass; `tsc --noEmit` clean; 492 Python tests pass; two fresh runs of
  `scripts/module-depth-map.py` are byte-identical to each other and to the committed
  `docs/module-depth-map.{json,html}`; the DEBUG run is 581 lines with 0 WARNING and 0 ERROR.
- **Browser log**: zero console messages, zero page errors, zero failed requests across every run
  (`.scratch/module-depth-map/logs/06-behind-the-shape.review.2.browser.log`).

## Review feedback - attempt 3

Reviewed `ticket/05-refusals-as-their-own-band..ticket/06-behind-the-shape` (`80223ef` on top of
`53e34e6`, `b6798db`). **Both attempt-2 blocking points and the non-blocking third are genuinely
fixed** — I reproduced each from its original scenario in chromium and each now behaves. All nine
acceptance criteria are met and I have exercised every one of them; the boxes below record that.
What sends this back is two things, and both are a bug this ticket has already been sent back for,
surviving in a variant the fix did not cover: **the card's new rule reads one `click`, so it never
sees a double-click**, and **the panel's new primary-button guard reads `event.button`, so it never
sees a macOS Ctrl+click.**

These are the last two outstanding items in this family, and I have given the fix direction for each
below, so attempt 4 is small and bounded. Nothing else here needs redoing.

### 1. Blocking: double-clicking a module name opens the panel and throws the word away

`scripts/module_depth_map/page.py:1372-1400` (`aDragRatherThanAClick`) and `:1430-1434` (the card's
two handlers).

The guard decides on a single `click`. Click #1 of a multi-click gesture arrives before any word has
been selected, so `event.detail === 1`, the pointer has not moved, `selectionInside(card)` is false —
and the panel opens. Click #2 then lands on whatever the freshly-opened modal put under the pointer.

Double-clicking a word is *the* ordinary way to grab a module name off a card, and it is the exact
scenario the attempt-2 feedback named: "a trainer highlighting a module name on a projector". The
name button's computed `user-select` is `auto`, so the browser is willing; only this handler is not.

Expected: double-clicking `AccountsService` selects the word `AccountsService` and leaves the page
alone.
Saw: the panel opens, and the selection is a character **from inside the panel** — the reader's word
is gone and a modal is over the page.

Reproduce (chromium, `docs/module-depth-map.html` over `file://`, viewport 1280x700):

1. Scroll the `AccountsService` card into view; its name button is x=384.8 y=167.9 w=235.4 h=24.8.
2. `page.mouse.dblclick(name.x + 20, name.y + name.height / 2)`.
3. `document.querySelector('dialog.behind').open` is **`true`**; `String(getSelection())` is `'—'`,
   and `dialog.contains(getSelection().getRangeAt(0).commonAncestorContainer)` is **`true`** — the
   selection is the panel's own text, not the card's.

Screenshot of exactly that state, with the stray selection visible at "interface cost 32 —":
`.scratch/module-depth-map/logs/06-behind-the-shape.review.3.dblclick-name.png`.

Two more shapes of the same bug:

- **Triple-click** on the `AccountsService` card body (`click_count=3` at card.x+60, card.y+210):
  panel opens, and `getSelection()` is a line of the *panel's* text —
  `'public List<Customer> customers() — costs 1\n'`.
- **On a card in the leftmost or rightmost column, the double-click does nothing at all.** The panel
  body is centred at x=273 w=734, and the cards are a four-column grid: I measured every one of the
  71 name buttons after scrolling it into view, and **27 of the 71** put the click point outside that
  box — the whole left column sits at x=129.8. On `SavingStreakApplication` (name button x=109.8,
  click point 129.8): click #1 opens the panel, click #2's press and release both land on the
  backdrop, the attempt-2 dismissal rule fires and shuts it. Result: `dialog.open` is `false`,
  `getSelection()` is `''` — the reader double-clicked a module name and got neither the word nor
  the panel. Screenshot:
  `.scratch/module-depth-map/logs/06-behind-the-shape.review.3.dblclick-edge.png`.

The fix has to handle `event.detail > 1`, not distance and selection alone. Whatever you choose,
`openBehind` must keep working from a plain click (`detail === 1`) and from the name button's Enter
(`detail === 0`), which is the constraint that ruled out moving the opening to `mouseup` last time.

### 2. Blocking: Ctrl+click on the backdrop still dismisses the panel

`scripts/module_depth_map/page.py:1078-1085`.

Attempt 2 sent this ticket back because "`mousedown`/`mouseup` fire for every button, so button 2
dismisses the panel", with the objection that "a right-click *inside* the panel correctly leaves it
open, so the two disagree". Attempt 3 answered it with `event.button === PRIMARY_BUTTON`, which
catches button 2 — but on macOS the context-menu gesture is **Ctrl+click, delivered as button 0**.
It sails through the guard, and the two ends disagree again in exactly the way attempt 2 named.

Expected: Ctrl+clicking beside the panel opens a context menu and leaves the panel open.
Saw: the panel closes, so the menu opens over a page the panel has just left.

Reproduce (chromium, `docs/module-depth-map.html` over `file://`, viewport 1280x700):

1. Click the `DepositsService` card to open its panel. `.behindBody` is x=273 y=32 w=734 h=636.
2. `page.keyboard.down("Control")`, `page.mouse.click(bodyBox.x - 40, bodyBox.y + 120)`,
   `page.keyboard.up("Control")` — i.e. Ctrl+click on the backdrop.
3. `document.querySelector('dialog.behind').open` is **`false`**.

With a capturing listener on the dialog, the three events behind that are, verbatim:

    mousedown button=0 ctrl=true targetIsDialog=true
    contextmenu button=0 ctrl=true targetIsDialog=true
    mouseup button=0 ctrl=true targetIsDialog=true

A Ctrl+click *inside* the panel correctly leaves it open (`open` is `true`), which is the same
disagreement between the two ends that attempt 2 objected to. Screenshot:
`.scratch/module-depth-map/logs/06-behind-the-shape.review.3.ctrl-click.png`.

An `event.ctrlKey` check alongside the button check on the `mousedown`, or a `contextmenu` listener
that clears `pressedOn`, closes it. Note the card side is accidentally safe here: no `click` event
follows a Ctrl+click, so `aDragRatherThanAClick` never runs. Only the panel, which acts on `mouseup`,
is exposed.

### 3. Blocking, same defect as 1: the comment and the README claim the case the code misses

`page.py:1396-1399` says of the selection branch: "A press and a release in the same place that still
left text selected: **a reader taking one word out of the card.** The gesture the guard exists for,
at the one size the distance above cannot see." `scripts/module_depth_map/README.md` makes the same
claim in prose.

Taking one word out of a card *is* the double-click, and that path never reaches this branch. The
branch is real and does fire — I found a 1px drag at (card.x+40, card.y+210) that selects a single
character `'s'` and correctly leaves the panel shut — but not for the gesture it names. Both prior
reviews of this ticket established that in this file a comment which overclaims is itself the defect;
this is that. Fix the code and the sentence becomes true; if you fix it some other way, the sentence
has to change with it.

### 4. Non-blocking: the panel's third number-vs-list pair still has no guard

`page.py:1204-1206` — `drawBehindMethods` prints its heading count from `module.deletionTest.methods`
and then lists `module.interface.methods`: a number off one object over a list off another, with no
cross-check, a few lines below the two cross-checks attempt 3 added. The implement log says this was
left alone deliberately because comparing them needs `interface.methods.length`, which
`test_the_panel_never_counts_anything_it_could_read_instead` forbids — that is a fair reading of the
rule and I am not overriding it. Recording it so the next reader does not re-derive the question.

There is a second, odder edge on the same line worth knowing about: **33 of the 36 never-scored
panels caption their method list with a number read off `deletionTest.methods`**, while
`drawBehindVerdict` on the same panel prints "This module is never scored, so the deletion test gives
it no verdict" — so the panel declines to report the deletion test and then quotes one of its counts
two sections earlier. `DepositRepository` and every other repository interface do it, as do
`CustomerAccounts`, `RecordedDeposit` and `SavingStreakApplication`. I did **not** treat this as
breaking "an excluded module's panel names the rule that excluded it rather than showing a score":
I read all 36 never-scored panels and not one prints `interface cost`, a leverage figure or a
verdict, and a count above the list of methods it counts is not a score. But reading it out of the
object whose verdict the panel refuses to print is the wrong field for the job;
`module.interface.methods` is the list being captioned.

### 5. Non-blocking: the two new guards are drawn in different weights

`page.py:1187-1191` appends a bare text node to the `<p>`, so the depth guard inherits normal ink.
`page.py:1337-1342` uses `add(line, "span", "says", …)`, and `.behindPart .says` is
`color: var(--ink-soft)` (`page.py:290`) — so the louder of the two sentences, the one contradicting
a verdict, is drawn in the muted colour used for supporting prose. The card's equivalent
(`page.py:917`) passes `null`. Pick one; not what sends this back.

### 6. Non-blocking: the no-`<dialog>` fallback supplies only half the rule it says it supplies

`page.py:231-244`. Attempt 1 asked for `dialog.behind:not([open]) { display: none; }` and it is
there — but the `dialog.behind` block sets no `display` at all, and a browser's own stylesheet
supplies *both* halves. Measured in chromium on the committed page: a real `<dialog>` computes
`display: none` without `[open]` and `display: block` with it, and that `block` comes from the UA
sheet, not from this file. In the browser this branch exists for, where `<dialog>` is an unknown
element, `openBehind`'s `setAttribute("open")` would leave it at an unknown element's default
`display: inline` — an inline run spliced into the page flow with `width: min(46rem, …)` inert.
Hiding works; showing does not, and the README's claim that the stylesheet carries "the rule that a
browser knowing `<dialog>` would have supplied itself" overclaims by one declaration. Unreachable in
any browser released since 2022, which is the standing attempt 1 gave it, so not what sends this
back.

### What I checked and found good, so it does not need redoing

- **Both attempt-2 blocking points, reproduced from their original scenarios.** Drag across the
  `AccountsService` name (card.x+12,card.y+14 → +150,+16): panel **shut**, selection
  `'AccountsServic'`. Same over the card body (y+210 → y+213): panel **shut**, selection
  `'refuses with 1:'`. Right-click 60px past the right edge of `.behindBody` on the `DepositsService`
  panel: **open**. Right-click inside: **open**. Right-click on a card: nothing opens. Plain left
  click on the backdrop: closes, focus back on the card's name. Backdrop→inside drag: **open**.
  Selection dragged out of the panel and released past its edge: **open**, 166 characters still
  selected. 2px jitter click still opens; a press dragged off the card opens nothing and the next
  plain click still works; a selection standing in another card does not block opening.
- **All 71 cards, mouse.** Each opened with the *previous* panel deliberately left scrolled to its
  bottom: every one opened at `scrollTop=0` on "What it costs a caller", on the right module, focus
  on `.behindBody`; Escape closed it and returned focus to the originating name. `bad=[]`.
- **All 71 cards, keyboard.** Enter on the name button opens the right panel at the top with exactly
  one `dialog[open]` and `aria-labelledby="behind-the-shape"` resolving to its heading; Tab reaches
  Close; Enter on Close closes; focus returns to the originating name. `bad=[]`. Inside an open
  panel ArrowDown and End scroll `.behindBody`, and Tab cycles Close → body → `.behindBody` → Close
  without reaching the page's own controls.
- **Every value in all 71 panels, cross-checked against `docs/module-depth-map.json`**: heading,
  kind/language/package, root+path and line count, the interface-cost split, the depth line and its
  leverage, every method signature with its cost and documented refusals, every type, every refusal
  sentence, every reach entry with its kind/matched clause/module id, every caller id, the verdict
  with its three counts and its reason, and every finding with its refusal, its verdict word and its
  reason — or "There is no finding against this module." **Zero mismatches.**
- **Excluded modules**: all 36 never-scored panels print the rule and what matched plus the rule's
  reason, and not one prints `interface cost` or `per unit of interface`.
- **The zero caveat** is on both card and panel for exactly the four modules the bar prices at 0
  (`ClockRefused`, `JobFailed`, `JobRefused`, `SchedulingIsOn`) and on no other panel.
- **Nothing computed at render time**: I stripped the comments from `drawBehind`…`drawBehindFindings`
  and scanned for arithmetic and `.length` — every `.length` is a `=== 0` emptiness test and there is
  no arithmetic at all; the only `+`/`-` in the region is `Math.abs` inside the pointer helper. Every
  printed number is a document field, and
  `test_the_panel_never_counts_anything_it_could_read_instead` enforces it mechanically.
- **Both new disagreement guards fire.** Rendering the real graph with `AccountsService`'s
  `depth.reach` +4 and `deletionTest.reach` +7 / `callers` +2 produced
  `reach 9 over interface cost 32 — 0.16 reached per unit of interface — but this panel lists 5
  below, so the depth above was taken over a reach this panel does not show` and
  `pass-through — … — but this panel lists 5 reached and 5 going through it, so this verdict was read
  off counts the rest of this panel was not drawn from`. The committed document holds no such
  disagreement on any of the three count/list pairs, so today they are guards rather than prose.
- **Layout**: no horizontal overflow on the page or on `.behindBody` at 720, 1024, 1280 or 1440; the
  720px panel reads correctly (`…review.3.panel-720.png`); dark theme reads correctly
  (`…review.3.panel-DepositsService.dark.png`).
- **Browser log**: **zero** console messages, zero page errors, zero failed requests across every run
  in this review (`.scratch/module-depth-map/logs/06-behind-the-shape.review.3.browser.log`, 0 lines).
- **Checks**: `bash .scratch/module-depth-map/lab.sh checks` passed — backend `Tests run: 113,
  Failures: 0, Errors: 0`, BUILD SUCCESS; frontend `tsc --noEmit` clean. 504 Python tests pass. Two
  fresh runs of `scripts/module-depth-map.py` are byte-identical to each other **and to the committed
  `docs/module-depth-map.{json,html}`**; the DEBUG run is 581 lines with 0 WARNING and 0 ERROR and
  ends `run finished graphBytes=193563 pageBytes=265746 filesParsed=71 filesUnparsed=0 modules=71
  scored=35 neverScored=36`.
- **Backend**: this branch touches no Java and no TypeScript (`git diff --name-only` is
  `docs/module-depth-map.html`, `scripts/module_depth_map/{README.md,page.py}` and its tests), so the
  `io.dataroots.savingstreak` logging rule has no new surface here. I drove the running app anyway to
  confirm nothing regressed: `POST /api/savings-accounts/1/deposits {"amount":"12.50",
  "fromCurrentAccountId":1}` → 201 with `i.d.s.deposits.DepositsService : deposit accepted
  depositId=1 savingsAccountId=1 fromCurrentAccountId=1 amount=12.50 pointsEarned=12
  depositedAt=2026-09-05T12:47:22.604Z`, and `{"amount":"0"}` → 400 `A deposit has to be an amount of
  more than zero, and 0 is not.` The backend log has 0 WARN and 0 ERROR. Note for the orchestrator,
  not for this ticket: that zero-amount refusal left **no** `io.dataroots.savingstreak` WARN line
  saying why — only Spring's `ExceptionHandlerExceptionResolver` recorded it, and there is no
  `log.warn` anywhere under `deposits/` except in `WithdrawalsService`. That is pre-existing backend
  code outside this branch's diff.
