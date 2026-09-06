# 09: What the page admits

**What to build:** The page states its own limits, in its own words, where a reader will see them.

It carries the date of the snapshot it represents, passed in when the tool runs rather than read from
the machine's clock, so the output stays identical between runs. It names itself an observation of the
codebase on that date, not a list of work to be done — this repository is extended by agents, and an
undated ranking of shallow modules reads as an instruction to start merging things. And it says
plainly what it did not measure: what was excluded and under which rule, and which parts of an
interface the tool cannot see at all.

That last part matters most. Invariants and ordering constraints are as much a part of an interface as
the methods are, they are not mechanically derivable, and a page that quietly leaves them out invites
a reader to mistake the score for the whole picture.

**Blocked by:** 06 (Behind the shape).

**Status:** needs-review

- [x] The snapshot date is supplied when the tool runs and never read from the system clock
- [x] The page shows the snapshot date it was given
- [x] The page names itself an observation on that date rather than a backlog, in words a reader will see without hunting
- [x] The page lists everything excluded from scoring and the rule that excluded it
- [x] The page states which parts of an interface the tool does not measure, naming invariants and ordering constraints
- [x] The page reports how much of the source was parsed, so a reader can weigh what they are looking at
- [x] Nothing on the page describes a module as needing to be changed
