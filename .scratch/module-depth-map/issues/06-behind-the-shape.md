# 06: Behind the shape

**What to build:** Clicking a module opens everything standing behind its shape: each method with
what it costs a caller, each thing the module reaches, which modules call it, its deletion-test
verdict, and any findings against it. The shape makes the claim; this is where a reader checks it.

Everything shown here already exists in the graph document. Nothing is computed at render time, so
what a reader sees in the panel and what a later tool reads from the graph are the same facts.

**Blocked by:** 04 (The deletion test) and 05 (Refusals as their own band).

**Status:** needs-info

- [ ] Clicking any module opens a panel about that module, and closing it returns to the full picture
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
