# 06: Withdrawals are refused with a readable reason

**What to build:** Every remaining way a withdrawal cannot happen is refused the way a deposit that
cannot happen is refused: an answer in one shape, carrying the reason in words the person who caused
it can read, which the web page shows unchanged. What may and may not be done stays decided in one
place, so nothing downstream has to reword it.

**Blocked by:** 05 (Withdraw money back into a current account).

**Status:** needs-review

- [x] Naming a savings account that does not exist is refused with a reason saying so
- [x] Naming a destination current account that does not exist is refused with a reason saying so
- [x] Naming two accounts held by different customers is refused with a reason saying so
- [x] An amount that is not an amount of money is refused with a reason saying so
- [x] An amount of zero or less is refused with a reason saying so
- [x] An amount quoted more finely than to the cent is refused, matching how a deposit treats one
- [x] Every refusal answers as a problem document carrying its reason in the detail field
- [x] Every refusal leaves both balances, every deposit and every points credit exactly where they were
