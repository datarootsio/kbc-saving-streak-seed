# 04: A deposit says what it has earned in loyalty and when it next pays

Status: ready-for-agent

**Blocked by:** 01 (an anniversary pays a tenth of the euros a deposit still holds).

**What to build:** A customer looking at a deposit can see what leaving the money alone has paid them
and what it is going to pay them next. Each deposit reports three things it did not before: the
loyalty bonus it has earned so far across all its anniversaries, the date of its next anniversary,
and what that next anniversary is currently worth at what the deposit holds today.

The next-anniversary figure is the point of the feature made visible. It falls when the customer
withdraws from that deposit, so the cost of a withdrawal is legible afterwards; it is nothing at all
for a deposit holding under €10; and a deposit that has been emptied reports no next anniversary,
because there is no promise left to make about money that has gone.

The total a deposit has earned now means its base points plus its streak bonus plus every loyalty
bonus paid on it, so that one figure still answers "what has this deposit been worth to me". The
three parts sum to the total, and the shared test view that records that invariant is updated to say
so. A deposit's total therefore grows on each anniversary, which is what a recurring reward means —
while what a deposit earned *when it landed* is unchanged, so every assertion made immediately after
a deposit still holds.

A newly made deposit reports the same three fields: nothing earned yet, and the date twelve months
out that it has just started counting towards.

Nothing is added to the account overview. There is no total loyalty bonus across deposits, and no
figure on the summary — the bonus is shown on the deposits that earned it, where the next
anniversary means something.

- [ ] Each deposit in the history reports the loyalty bonus it has been paid, across every anniversary.
- [ ] Each deposit in the history reports the date of its next anniversary and what that anniversary is currently worth.
- [ ] A deposit's next-anniversary figure falls after a withdrawal from it.
- [ ] A deposit holding nothing reports no next anniversary at all.
- [ ] A deposit holding under €10 reports its next anniversary date with nothing to be earned on it.
- [ ] The total a deposit has earned is its base points, its streak bonus and its loyalty bonuses, and the three always sum to it.
- [ ] A deposit just made reports no loyalty bonus and a first anniversary twelve months out.
- [ ] What a deposit earned when it landed is unchanged by this ticket, and the existing assertions about it still pass untouched.
- [ ] The account overview gains nothing.
