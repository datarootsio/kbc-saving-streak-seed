# 04: One ledger of the money that moved

**What to build:** An endpoint that answers with every deposit into and every withdrawal out of every
savings account the customer holds, as one chronological list, newest first. Each entry says which way
the money went, how much, between which two accounts, when, and — for a deposit — what it earned.

Somebody saving towards two goals currently has to read two account pages and four lists to answer
"what have I actually moved?". This answers it once.

Nothing in it is added up. No running balance and no totals: a running balance across several accounts
is not a figure that means anything.

Status: done

- [x] `GET /api/customers/{id}/money-movements` answers with the customer's deposits and withdrawals as one list, newest first.
- [x] Every savings account the customer holds is covered, and no other customer's movement appears.
- [x] Each entry carries its direction, the savings account, the current account at the other end, the amount and the moment.
- [x] A deposit carries what it earned; a withdrawal carries nothing earned, which is what a withdrawal earns.
- [x] A customer who has moved nothing answers with an empty list.
- [x] A customer nobody has heard of is refused with the sentence Accounts words for that.
- [x] One DEBUG line per read carrying the counts of each direction.

## Verified

`GET /api/customers/1/money-movements`, driven by hand after two deposits into two different savings
accounts, a withdrawal, and a €0.99 deposit that earned nothing:

    INTO_SAVINGS   id=3 savings=1 current=1  0.99  points=0
    OUT_OF_SAVINGS id=1 savings=1 current=1 10.00  points=0
    INTO_SAVINGS   id=2 savings=2 current=1 25.50  points=25
    INTO_SAVINGS   id=1 savings=1 current=1 60.00  points=60

Newest first, both accounts in one list, both directions in one shape, every amount quoted to the
cent (`0.99`, `10.00`, `25.50`, `60.00` — not `25.5`). A withdrawal carries `pointsEarned: 0`, which
is what a withdrawal earns; the €0.99 deposit also carries 0, which is a different statement and one
the page tells apart.

**The account filter is actually pinned.** The test first read Bram's ledger, which was empty because
he had never moved anything — so a query that had forgotten to filter by savings account at all would
have passed. It now deposits €13 for Bram and asserts his pot is absent from Anke's ledger, that hers
still holds exactly three movements, and that his holds exactly one.

A customer nobody has heard of is refused: `GET /api/customers/999/money-movements` → 404 *"There is
no customer 999."*, and — new — that refusal now leaves a WARN, which the whole of `CustomerController`
was missing. `GET` for a customer who exists and has moved nothing answers `[]`.

One DEBUG line per read: `money movements listed savingsAccounts=2 intoSavings=3 outOfSavings=1
movements=4`. Counts rather than rows, because this runs on every page load.
