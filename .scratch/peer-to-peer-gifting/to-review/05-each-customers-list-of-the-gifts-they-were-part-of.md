# 05: Each customer's list of the gifts they were part of

Status: needs-review

**Blocked by:** 01 (a customer can give points to another customer).

**What to build:** Each customer can read the gifts they have been part of — sent and received
together in one list, newest first, each row marked with its direction and naming the other person by
name and what the gift was worth. One list rather than two endpoints, because the whole story of a
customer's gifting reads chronologically and direction is a property of who is reading it; this is
the idiom the money-movement ledger already set with its own direction.

The record outlives the points. A gift stays in both customers' lists after its points have been
spent, given onward, or expired — what somebody did is not undone by what later happened to the
points they did it with.

Reading the list for a customer who does not exist is a 404, as every other per-customer read is.

The test harness gains one view record for a gift, beside the existing money-movement and deposit
views. Nothing else about the harness changes: it already knows both seeded customers by name, their
contact details and their balances.

- [x] `GET /api/customers/{customerId}/gifts` answers the gifts this customer sent and received, newest first.
- [x] Each row carries the gift's id, its direction for this customer, the other party by id and name, the points, and the moment it happened.
- [x] A gift appears in the sender's list marked sent and in the recipient's list marked received, naming the other person in each.
- [x] A customer who has been part of no gifts gets an empty list, not a 404.
- [x] A gift still appears in both lists after its points have been spent.
- [x] A gift still appears in both lists after its points have expired.
- [x] Reading the list for a customer who does not exist answers 404.
