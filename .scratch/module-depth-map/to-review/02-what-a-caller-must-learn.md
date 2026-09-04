# 02: What a caller must learn

**What to build:** Each module on the page carries a bar whose width is the cost of its interface —
everything a caller has to learn in order to use it correctly. A reader comparing two modules can see
which one asks more of them before reading a word.

Three kinds of thing are drawn but never scored, because they are shallow by construction and scoring
them would bury the real finding: values that only carry data across a seam, repository interfaces
whose implementation is generated rather than written, and the application's entry point. Every one of
those exclusions is attributable to a named rule in a checked-in configuration file, so a reader who
disagrees with a score can point at the rule that produced it.

**Blocked by:** 01 (Modules on a page).

**Status:** needs-review

- [x] Interface cost counts every method reachable from outside the module, every parameter of those methods, and every distinct type crossing the seam in a parameter or a return
- [x] A method handing back a domain type costs a caller more than one handing back a primitive
- [x] Each module's bar width on the page is its interface cost, and comparable between modules
- [x] Scoring weights and exclusion rules live in a configuration file beside the tool, not inside it
- [x] Data carriers, generated repository interfaces and the entry point are excluded from scoring
- [x] Excluded modules are still drawn in the graph and on the page, marked as excluded
- [x] Every exclusion in the graph names the rule that caused it, and no module is excluded without one
- [x] Changing a weight or an exclusion in the configuration file changes the output without any edit to the analyser
- [x] Fixture source trees establish each scoring rule independently of the application's own code
