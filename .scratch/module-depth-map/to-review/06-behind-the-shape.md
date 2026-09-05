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
