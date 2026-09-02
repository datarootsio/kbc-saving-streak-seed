# 05: Withdraw money back into a current account

**What to build:** A customer moves money out of a savings account and back into one of their own
current accounts. The savings balance falls and the current account balance rises by the same amount,
both or neither. The withdrawal draws on the account's deposits oldest first, and records which
deposits it took from and how much it took from each — that record is what the loyalty-bonus exercise
later reads to decide whether a deposit was reduced before its anniversary, which no balance figure
could answer. The points the deposits earned are not touched: base points are credited unconditionally,
and withdrawing the money does not undo them. The account's withdrawals can be read back, newest first.

**Blocked by:** 01 (Read the current time from an injected clock), 02 (Deposits carry what remains of them).

**Status:** ready-for-agent

- [ ] Withdrawing reduces the savings account's money balance and raises the named current account's balance by the same amount
- [ ] A withdrawal records the savings account it left, the current account it returned to, the amount, and the moment, taken from the injected clock
- [ ] Deposits are drawn down oldest first, and a withdrawal larger than the oldest deposit reaches into the next one
- [ ] Which deposits a withdrawal drew down, and how much of each, is recorded and survives a restart
- [ ] The savings account's points balance is unchanged by a withdrawal
- [ ] The customer's other savings accounts are unchanged by a withdrawal
- [ ] The entire money balance can be withdrawn in a single request, leaving the account at zero
- [ ] Asking for more than the account holds is refused with a reason naming the amount and the balance, and leaves both balances and every deposit exactly where they were
- [ ] The account's withdrawals can be read back newest first, each carrying its amount, destination and moment
