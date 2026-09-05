# 05: Refusals as their own band

**What to build:** Every refusal a module can answer with is shown as part of what a caller must know
— because it is — but carried as its own band on the module rather than folded invisibly into one
number. A module whose interface is wide because it is honest about how it can fail should be
distinguishable from one that is merely wide, and a reader should be able to see a module's failure
modes before deciding to call it.

The tool also checks the documented refusals against the ones the implementation actually raises, and
reports a finding wherever the two disagree. A stale comment about how something fails is then caught
by a machine rather than by whoever called it.

**Blocked by:** 02 (What a caller must learn).

**Status:** needs-review

- [x] Every refusal a module can answer with appears in the graph as part of its interface
- [x] Refusals count toward interface cost, and are also reported separately from the rest of it
- [x] The page shows refusals as their own band, so an honestly-wide interface is distinguishable from a merely wide one
- [x] Documented refusals are read from the source's own documentation rather than guessed at
- [x] A module documenting a refusal it cannot raise produces a finding naming both sides of the disagreement
- [x] A module raising a refusal it does not document produces a finding naming both sides of the disagreement
- [x] A module whose documentation and implementation agree produces no finding
- [x] Fixture modules establish each direction of disagreement and the agreeing case
