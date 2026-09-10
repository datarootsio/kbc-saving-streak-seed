# 04: A gift moves points and nothing else

Status: needs-review

**Blocked by:** 01 (a customer can give points to another customer).

**What to build:** Points move; nothing else does. Being generous with points never touches anybody's
euros, never secures a week, never changes a streak, and never appears in the ledger of money that
moved — that ledger stays a record of euros. Nor does a gift show up in any deposit's breakdown of
what it earned: a deposit's breakdown is a statement about that deposit, and points somebody was
given did not come from it.

Nothing is added to the account overview either — no "points given away" total, no "points received"
total. The balance already includes received points and ticket 05's list is the record. This follows
the loyalty bonus's precedent of showing a thing where it happened rather than adding a figure to the
front page.

This is the negative space around the feature, and it is worth stating as tests because every item on
it is a thing a plausible implementation could get wrong by being helpful. Expected to need no new
production code if ticket 01 was built as specified; if any of these fail, the fix is in ticket 01's
code, not a new special case.

- [x] Both customers' current-account and savings balances are unchanged by a gift.
- [x] A gift secures no week and leaves both customers' streaks and multipliers exactly as they were.
- [x] A gift does not appear in either customer's ledger of money that moved.
- [x] A gift does not appear in any deposit's breakdown of what it earned, for either customer, and the total a deposit says it earned is unchanged.
- [x] Nothing new appears on the account overview.
