# 04: Run a scheduled job on demand

**What to build:** Scheduling is switched on in the application, so that a participant who writes a
scheduled job during an exercise sees it run at all rather than sitting in silence wondering why
nothing happened. On top of that, any scheduled job can be run immediately by name instead of waiting
for its schedule to come round. The application ships no jobs of its own — points expiry and the
loyalty bonus are exercises — so this delivers the machinery and the guarantee that a job added later
is reachable.

**Blocked by:** 03 (Advance the clock in the development profile).

Status: needs-review

- [x] Scheduling is enabled, so a job annotated as scheduled anywhere in the application actually runs
- [x] A scheduled job can be run immediately by name while the development profile is active
- [x] The jobs available to run can be listed, so nobody has to guess a name
- [x] Naming a job that does not exist is refused with a reason saying so
- [x] Verified against a job defined in test scope, since the application deliberately ships none
- [x] Neither control exists when the development profile is not active
