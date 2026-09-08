# 05: The deposit history explains what each deposit earned and why

**What to build:** A customer looking back over their deposits can see, for each one, what it earned
as base points, what it earned as streak bonus, and the multiplier it was paid at. A past deposit
then explains itself without the customer having to reconstruct which week it fell in or what their
streak was at the time — and it keeps explaining itself correctly after the ladder changes, because
the figures come from what was recorded at the time rather than from a fresh derivation.

This is the reason the ledger records the multiplier applied rather than recomputing it, made
visible. Without it, a customer sees 9 points against a €7 deposit and has no way to check the
arithmetic.

**Blocked by:** 04 (a deposit is paid at the streak's multiplier and credits a streak bonus).

**Status:** needs-review

- [x] Each entry in a savings account's deposit history reports its base points, its streak bonus points, the multiplier it was paid at, and the total.
- [x] Those figures match what the deposit response said at the time the deposit was made.
- [x] A deposit that earned no bonus reports a bonus of zero and its own multiplier, rather than omitting either.
- [x] Deposits made before this feature existed report their base points, a bonus of zero and a multiplier of 1.00×, which is what they were in fact paid.
- [x] The history rows on the savings account page show the base points, the bonus and the multiplier, in the layout the page already uses for a deposit.
- [x] The history is still ordered as it was, and every other figure on the page is unchanged.
- [x] Advancing or rewinding the development clock does not change what a past deposit reports having earned.
