# 07: Flows through the modules

**What to build:** The three things this application actually does — money moving into a savings
account, money moving back out and drawing down the deposits it came from, and points being spent on a
reward — are each traceable on the page. Choosing one highlights the modules it passes through, in
order, so a reader can follow a business event across the application without stepping through it in a
debugger.

The paths are derived from the call graph the analyser already builds, not written out by hand, so a
flow that stops matching the code is a flow that fails rather than one that quietly misleads.

**Blocked by:** 03 (Reach, and the fan).

**Status:** ready-for-agent

- [ ] A deposit, a withdrawal with the deposits it draws down, and a reward claim are each available as a flow
- [ ] Choosing a flow highlights every module it passes through, in the order it passes through them
- [ ] Flows are derived from the call graph rather than listed by hand
- [ ] Each flow is defined by its entry point in the checked-in configuration file, not by a hardcoded path through the modules
- [ ] A flow whose path can no longer be resolved fails loudly rather than rendering a shorter path
- [ ] Every module named in a flow is a module the graph contains
- [ ] Clearing the selection returns the page to showing all modules equally
