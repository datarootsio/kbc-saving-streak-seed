# 02: A withdrawal forfeits only the anniversary it did not reach

Status: ready-for-agent

**Blocked by:** 01 (an anniversary pays a tenth of the euros a deposit still holds).

**What to build:** Taking money out costs the customer the coming year's bonus on the money they
took, and nothing else. Bonuses already paid on earlier anniversaries are theirs for good and are
never clawed back, however much they withdraw afterwards.

A deposit emptied before its anniversary pays nothing on it — that is the forfeit. A deposit partly
drawn down pays on what is left in it, so somebody who spends half their savings keeps half the
year's bonus rather than losing all of it. This is the generous half of the rule and it is
deliberate: the strict reading, where any touch at all forfeits the whole year, would let a €1
withdrawal destroy a hundred points on a €1,000 deposit.

Withdrawals already come out of the oldest deposit first, so a withdrawal that only partly covers
the savings draws down the oldest, most-nearly-vested money and leaves the newer deposits' clocks
running whole. That ordering is what makes the forfeit fall where a customer would expect it, and
this ticket is where it is pinned down as behaviour rather than left as an accident of how
withdrawals happen to be recorded.

This is expected to need no new production code — a drawn-down deposit simply has fewer euros in it
when its anniversary arrives. The work is saying so in tests that would fail if that stopped being
true. If it turns out something is needed, that is this ticket's finding rather than a reason to
widen it.

- [ ] A deposit emptied before its first anniversary earns nothing on it.
- [ ] A deposit emptied after its first anniversary keeps the bonus it was already paid, and earns nothing on its second.
- [ ] A deposit drawn halfway down before its anniversary earns a tenth of what is left in it.
- [ ] A withdrawal that only partly covers the savings comes out of the oldest deposit, and the newer deposits' anniversaries pay in full.
- [ ] A withdrawal after an anniversary has been paid takes back none of those points, and the customer's balance is unchanged by it.
- [ ] A deposit drawn below €10 earns nothing on its anniversary while the remaining euros stay in the account.
- [ ] Withdrawing changes no earlier bonus record: what a past anniversary was worked out from is still readable afterwards.
