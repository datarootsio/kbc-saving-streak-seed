# 03: A loyalty bonus is an ordinary batch of points

Status: needs-review

**Blocked by:** 01 (an anniversary pays a tenth of the euros a deposit still holds).

**What to build:** Loyalty bonus points are worth exactly what every other point is worth. They can
be spent on any reward, they are spent in the same oldest-first order as the rest, they count
towards the balance the moment they are paid, and they run out twelve months after the anniversary
that paid them — not twelve months after the sweep happened to notice. They appear in what the
customer is told expires next, so the warning covers everything they stand to lose.

The one interaction worth stating plainly: a deposit keeps paying its anniversaries after its own
base points have expired. Expiry is a rule about points, and this is a reward for money staying put,
so a deposit two, three or ten years old is still on its clock even though everything it originally
earned has long since gone. A bonus credited for an anniversary far in the past may therefore arrive
already beyond its own twelve months and be swept away the same night, which is both rules holding at
once rather than either misbehaving.

Like 02, this is expected to need no new production code: the bonus is credited as an ordinary dated
batch and the ledger already treats every batch the same way. The work is the tests that say the
ledger has no special case here, and would fail the day somebody added one.

- [x] A loyalty bonus can be spent on a reward.
- [x] A loyalty bonus is spent in oldest-first order alongside base points and streak bonus, with no regard to which reason earned it.
- [x] A loyalty bonus counts towards the points balance from the moment it is paid.
- [x] A loyalty bonus expires twelve months after the anniversary that paid it, and a sweep before that leaves it alone.
- [x] A loyalty bonus appears in the points the customer is told expire next, on the day its own twelve months are up.
- [x] A deposit whose base points have already expired still pays its second anniversary.
- [x] A bonus paid for an anniversary already more than twelve months past is credited and then expires, and both events are readable in the log.
- [x] A claim refused for want of points quotes a balance that includes whatever loyalty bonus the customer holds.
