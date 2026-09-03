# 07: Withdraw and review withdrawals on the savings account page

**What to build:** A customer withdraws money from the page rather than by calling an API, so the two
directions of the same movement sit in one place. The form names an amount and which current account
the money goes back to, the balance updates when it succeeds, and when it is refused the backend's own
reason is shown unchanged. Withdrawals appear in the account's history alongside its deposits, so
every movement on the account can be accounted for.

**Blocked by:** 05 (Withdraw money back into a current account), 06 (Withdrawals are refused with a readable reason).

**Status:** needs-review

- [x] A withdrawal form sits beside the deposit form, naming an amount and a destination current account
- [x] A successful withdrawal updates the money balance shown on the page without a reload
- [x] A refused withdrawal shows the reason the backend gave, unchanged
- [x] The form is disabled while the request is in flight, the way the deposit form is
- [x] The amount is sent as the text that was typed, so the backend remains the only judge of what counts as an amount of money
- [x] Withdrawals appear in the account's history with their amount, destination and moment
- [x] The points balance shown does not change when money is withdrawn
- [x] The form carries labels and is reachable by keyboard the way the deposit form is
