# 03: Reach, and the fan

**What to build:** Beneath each module's interface bar, a fan of lines out to everything that module
coordinates on its caller's behalf — the other modules it calls, the adapters it drives, the records
it writes, the transaction it establishes. This is where the page starts making its argument: a deep
module reads as a short bar over a wide fan, and a pass-through reads as a bar as wide as its fan,
one line per method, coordinating nothing.

Depth is measured as leverage — behaviour a caller can exercise per unit of interface they must learn
— and never as implementation lines over interface lines. That framing is rejected by this project's
design vocabulary because it rewards padding: under it, the largest file in the repository would score
as its deepest module. Reach cannot be inflated by writing more lines, which is exactly why it is the
numerator.

**Blocked by:** 02 (What a caller must learn).

**Status:** needs-review

- [x] A module's reach counts the distinct collaborating modules it calls, the adapters it drives, the persistent records it writes, and whether it establishes a transaction
- [x] Adding lines to an implementation without adding coordination does not change its reach, and a fixture establishes this
- [x] Depth is reported as reach relative to interface cost, and appears in the graph document as its own value
- [x] The fan beneath each module is drawn from its reach, with one line per thing reached
- [x] A module with several collaborators behind few methods is visibly distinguishable from one with a method per collaborator, without reading any label
- [x] Every line in a fan resolves to something the graph document also contains
- [x] Fixture source trees establish a deep module and a pass-through, each asserting the depth it was built to produce
