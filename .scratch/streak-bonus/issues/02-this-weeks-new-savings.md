# 02: This week's new savings, counted Monday to Sunday in Brussels

**What to build:** A customer can see how much they have paid into a savings account so far this
week, and how much more the week still needs. A week runs Monday to Sunday in `Europe/Brussels`, and
the figure is the gross total of deposits that landed in it — withdrawals do not reduce it. The
weekly minimum of €50 is named once here, and this is where the customer first sees a week as
something they are part-way through.

Nothing yet pays a bonus. This ticket establishes the week and proves it behaves at the boundaries:
a Sunday-late deposit counts for the week that is ending, a Monday-early one for the week starting,
both judged in Brussels terms rather than in the clock's UTC.

The savings account resource carries the new savings landed in the current week; the savings account
page shows it beside the balances as progress towards the €50 the week asks for. The figure is
derived from the deposit records on read — there is no stored weekly total to keep in step.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] The savings account resource reports the new savings that have landed in the current week.
- [ ] A deposit raises that figure by its full amount, immediately.
- [ ] A withdrawal leaves the figure unchanged, however large it is.
- [ ] Advancing the development clock into the next week returns the figure to zero without any deposit or job having run.
- [ ] Advancing the clock backwards restores the earlier week's figure — the derivation reads the ledger and holds no state that could go stale.
- [ ] A deposit at 23:30 on Sunday, Brussels time, counts towards the week that is ending; one at 00:30 on Monday, Brussels time, counts towards the week beginning — including across a daylight-saving change.
- [ ] Each savings account reports only its own new savings; a deposit into one account does not move the figure on another, including another held by the same customer.
- [ ] The €50 weekly minimum exists as a single named constant, not as a literal at each place it is compared against.
- [ ] The savings account page shows the week's progress towards €50 beside the money and points balances, formatted the way money already is on that page.
- [ ] DEBUG logging shows the week boundaries the derivation used, in the zone it used, and the deposits it counted into the week.
- [ ] Points earned by a deposit are unchanged: one per whole euro.
