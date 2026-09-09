# 01: A customer can give points to another customer

Status: ready-for-agent

**Blocked by:** None (can start immediately).

**What to build:** A customer can hand a number of points to another customer of the bank, and the
points arrive. The sender's balance falls by exactly what they gave, the recipient's rises by exactly
the same, and nothing is created, skimmed or destroyed on the way. The gift is final the moment it is
made: no acceptance step, no pending state, nothing for the recipient to do.

The points come off the sender's oldest batches first — the same draw a reward claim already makes —
and each slice drawn arrives in the recipient's pot as a batch of its own **dated at the moment the
original batch was earned**, not at the moment of the gift. A gift drawn from three batches of
different ages therefore arrives as three batches of different ages. This is the one decision in the
feature a customer would not have guessed, and it is load-bearing: with no limit on how often people
may give, a fresh twelve months per gift would let two customers pass the same points back and forth
forever and keep them alive indefinitely. Ticket 03 is what proves the clock is inherited; this
ticket is what makes it so.

This is the tracer bullet: the Gifting module, the record of what was given, the ledger's one new way
in, and the endpoint that sends one. Gifting is built in `RewardsService`'s image — a claim and a
gift are the same shape of act — and depends on Accounts, Points, the clock and a repository of its
own. Points gains one operation: move points from one customer to another, oldest first, against a
stated source reference, returning the slices it moved or nothing when the sender is short (a
boolean-shaped answer like `spend`'s, so the refusal's wording stays with Gifting in ticket 02).
`PointsReason` gains `GIFT_RECEIVED`, and it is deliberately kept out of the set of reasons a deposit
can have earned under, so no deposit breakdown grows a field and no existing assertion moves.

The gift row is saved before the move, because the recipient's batches carry its id — the opposite
order to `RewardsService`, which spends before it saves, so say so in the code. One transaction
throughout.

Logging is part of this: one INFO per gift with its id, both customers, the points and the moment;
the ledger's existing `points credited` line covers the credit side with `reason=GIFT_RECEIVED`; and
one DEBUG line naming the slices drawn — batch id, earned-at, taken, left in it — gathered into a
single line and **guarded by `isDebugEnabled`**, because rendering a batch is work and the string is
thrown away at INFO. That guard is not optional: a previous review made it blocking on
character-for-character this shape of code in `spend`.

- [ ] `POST /api/customers/{customerId}/gifts`, naming the recipient by the contact details they bank under and the points to give, answers 201 with the gift: its id, a direction of `SENT`, both parties by id and name, the points, and the moment off the application's clock.
- [ ] The recipient is found by contact details the way sign-in finds them: trimmed, matched case-insensitively.
- [ ] The sender's points balance falls by exactly the gift and the recipient's rises by exactly the gift.
- [ ] A gift is drawn from the sender's oldest points first.
- [ ] A gift larger than any one of the sender's batches is drawn from as many as it needs.
- [ ] Each slice drawn arrives as a batch of the recipient's dated at the moment the original batch was earned, so a gift drawn from batches of different ages arrives as batches of different ages.
- [ ] Gifted batches are credited under a reason of their own, and that reason does not appear in any deposit's breakdown of what it earned.
- [ ] One gift row is written per gift, carrying both customers, the points and the moment.
- [ ] INFO says what went where; the slices drawn are readable at DEBUG; the DEBUG gathering is guarded by `isDebugEnabled`.
