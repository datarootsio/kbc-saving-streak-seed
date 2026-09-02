# 01: Read the current time from an injected clock

**What to build:** Nothing a customer can see changes. Two places in the application currently ask the
system what time it is — recording when a deposit happened, and when a reward was claimed. Both take
the moment from a clock the application supplies instead, so that a later ticket can move that clock
forward and make a rule measured in months demonstrable in a minute.

**Blocked by:** None (can start immediately).

**Status:** needs-review

- [x] The moment a deposit records and the moment a claim records both come from an application-supplied clock rather than the system clock
- [x] With the clock fixed to a chosen moment, a newly recorded deposit and a newly issued claim both carry that moment
- [x] Moments are still kept to the millisecond, so listings that order by moment and then by identifier behave exactly as before
- [x] No new place in the application reads the system clock directly
- [x] Every existing test passes unchanged
