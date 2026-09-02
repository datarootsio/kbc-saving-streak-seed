# 03: Advance the clock in the development profile

**What to build:** A trainer or a participant can move the application's clock forward by a number of
days. A deposit made afterwards records a moment that far ahead, so a rule measured in months can be
reached during a coffee break rather than during a calendar year. The move survives a restart, so an
application that is stopped halfway through an exercise does not rewind whoever was using it back to
day zero. The control exists only in the development profile and appears nowhere in the web page.

**Blocked by:** 01 (Read the current time from an injected clock).

**Status:** ready-for-agent

- [ ] The clock can be moved forward by a number of days over the API while the development profile is active
- [ ] A deposit made after a move records a moment that many days ahead of the real one
- [ ] How far the clock has been moved can be read back, so somebody mid-exercise can tell where in time they are
- [ ] The move is still in effect after the application is restarted
- [ ] Moving the clock backwards, or by a nonsensical number of days, is refused with a reason
- [ ] Neither control exists when the development profile is not active
