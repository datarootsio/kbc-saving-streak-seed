# 05: The history page shows the loyalty bonus and the next anniversary

Status: ready-for-agent

**Blocked by:** 04 (a deposit says what it has earned in loyalty and when it next pays).

**What to build:** The customer sees it on the page. The breakdown line under each deposit in the
history already reads as base points plus streak bonus at a rate; it grows a third part for the
loyalty bonus that deposit has been paid, and a line saying when it next pays and what that is
worth.

The next anniversary is the part worth designing rather than merely rendering: it is a promise about
the future on a page that has so far only ever reported the past. It reads as a date and a figure a
customer can decide against — enough to make leaving the money alone feel like a choice, without
turning a history row into a sales pitch.

A deposit with no loyalty bonus yet does not need a nothing shown against it, and a deposit that has
been emptied has no promise to make, so both say less rather than showing a zero. The page stays
readable at the narrow widths the history was already built for, in both themes.

- [ ] A deposit that has been paid a loyalty bonus shows it in its breakdown, beside the base points and the streak bonus.
- [ ] The breakdown's parts still visibly add up to the total the deposit has earned.
- [ ] A deposit shows when it next pays and what that anniversary is worth.
- [ ] A deposit that has never been paid a loyalty bonus shows no loyalty figure rather than a zero.
- [ ] A deposit that has been emptied shows no next anniversary.
- [ ] The page holds up at the narrow widths the history is already checked at, in light and dark.
- [ ] The types the page reads the deposit through carry the three new fields, and the typecheck passes.
