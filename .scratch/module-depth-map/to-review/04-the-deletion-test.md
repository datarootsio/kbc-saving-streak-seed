# 04: The deletion test

**What to build:** Each scored module carries a verdict on the page: does deleting it concentrate
complexity, or merely move it to its callers? A module coordinating almost nothing while several
callers go through it is a pass-through, and the page says so. A module with substantial reach earns
its keep, and the page says that too.

The verdict is computed, not written. It states the numbers it came from — how much the module
reaches, how many modules call it — so that a reader can check the reasoning rather than take the
label on trust.

**Blocked by:** 03 (Reach, and the fan).

**Status:** needs-review

- [x] The graph records, for every module, how many other modules call it
- [x] A module with reach of one or less and two or more callers is reported as a pass-through
- [x] A module with substantial reach is reported as earning its keep
- [x] Every verdict states the reach and caller counts it was derived from
- [x] The same source always produces the same verdict, with no wording that varies between runs
- [x] The verdict appears on the page beside the module it judges
- [x] A fixture pass-through and a fixture deep module each receive the verdict they were built to receive
- [x] Run against this repository, the tool identifies the accounts module's service as a pass-through without being told to
