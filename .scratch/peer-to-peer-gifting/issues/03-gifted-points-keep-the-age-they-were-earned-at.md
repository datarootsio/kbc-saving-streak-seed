# 03: Gifted points keep the age they were earned at

Status: ready-for-agent

**Blocked by:** 01 (a customer can give points to another customer).

**What to build:** Gifted points are worth exactly what every other point is worth, and they carry
their original age with them. A point earned last March expires next March whichever pot it is
sitting in when the twelve months are up. So a gift moves points without touching the expiry rule,
and a chain of gifts cannot keep points alive: two customers passing the same points back and forth
watch them expire on the anniversary of the day they were originally earned, however many hops they
made in between.

Everything else about them is unremarkable. They count towards the balance the moment they arrive,
they are spendable on any reward, they are spent oldest-first alongside the recipient's own points
with no regard to which reason earned them, they appear in what the recipient is told expires next,
and they can be given onward to a third customer — carrying the same original age when they go.

Two consequences are honest rather than defects, and both should be readable in the log rather than
smoothed over:

- Giving away points whose anniversary passed earlier today hands over points that night's expiry
  sweep will take. Both rules are holding at once.
- A recipient's pot can hold points older than any deposit they have ever made. That is what having
  been given something second-hand looks like.

Like ticket 03 of the loyalty feature, this is expected to need little or no new production code —
ticket 01 already dates the batches, and the ledger already treats every batch the same way. The work
is the tests that say there is no special case here, and that would fail the day somebody added one.
The clock-moving harness is the seam; a fresh application per class, since these tests wind the clock
a year forward.

- [ ] Gifted points expire twelve months after they were originally earned, not twelve months after they were given, and the expiry sweep run before that date leaves them alone.
- [ ] Points given back and forth between two customers do not outlive their twelve months.
- [ ] Gifted points near the end of their twelve months appear in the points the recipient is told expire next, on the day their own twelve months are up.
- [ ] Gifted points count towards the recipient's balance from the moment the gift goes through.
- [ ] Gifted points can be spent on a reward, and are spent oldest-first alongside the recipient's own points.
- [ ] Received points can be given onward to a third customer, and carry their original age when they go.
- [ ] A gift of points whose anniversary has already passed is credited and then swept away, and both events are readable in the log.
- [ ] A claim refused for want of points quotes a balance that includes whatever gifted points the customer holds.
