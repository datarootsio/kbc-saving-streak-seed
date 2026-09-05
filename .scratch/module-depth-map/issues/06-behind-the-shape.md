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
