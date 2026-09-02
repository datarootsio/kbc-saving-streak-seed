# 02: Deposits carry what remains of them

**What to build:** Nothing a customer can see changes. Each deposit gains a record of how much of it
is still sitting in the savings account, which is the full amount at the moment it is made. The
account's money balance becomes the sum of what remains across its deposits rather than the sum of
what was originally put in. The two figures are identical today; they diverge once a withdrawal can
reduce one of them, which is what this prepares for.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] A deposit records a remaining amount, equal to the amount deposited when it is made
- [ ] A savings account's money balance is derived from what remains across its deposits
- [ ] Every balance the API reports is unchanged for every scenario that already has a test
- [ ] An application started against a database written before this change reports the same balances afterwards, with deposits already recorded carrying a remaining amount equal to their original amount
- [ ] Every existing test passes unchanged
