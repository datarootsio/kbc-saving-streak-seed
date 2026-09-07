# 03: A streak of consecutive secured weeks, and the best one ever run

**What to build:** A customer can see how many consecutive weeks they have secured on a savings
account, and the longest streak they have ever run on it. A week is secured once at least €50 of new
savings has landed in it. The current streak is the run of consecutive secured weeks ending at the
most recently secured one — and it counts only while it is still alive, meaning the last secured week
is the current week or the one immediately before it. A customer who skipped last week has a current
streak of zero *now*, on a Wednesday, without waiting for this week to end.

The best-ever streak is the longest such run anywhere in the account's history, and it survives a
lapse: losing the streak costs the run, not the record.

Both figures appear on the savings account resource and on the savings account page beside the week's
progress. Both are derived from the deposit records; nothing is stored and nothing is counted
incrementally.

**Blocked by:** 02 (this week's new savings — supplies the week and the €50 threshold).

**Status:** needs-review

- [x] The savings account resource reports the current streak in weeks and the best-ever streak in weeks.
- [x] Paying €50 into an account in a week where nothing had landed yet makes the current streak one week.
- [x] Several deposits in one week that together reach €50 secure it exactly as a single €50 deposit does.
- [x] Securing the following week too makes the current streak two weeks.
- [x] A week in which €30 landed does not secure it: the streak that ran up to the previous week is over, and the current streak reads zero.
- [x] A week in which nothing landed ends the streak the same way.
- [x] Once a streak has ended, the best-ever streak still reports its length.
- [x] The current streak reads zero as soon as a week has passed unsecured, even mid-way through the following week and before any deposit is made in it.
- [x] Securing a week after a lapse gives a current streak of one, while the best-ever streak keeps the higher figure.
- [x] A withdrawal, of any size and at any point in a week, neither ends a streak nor prevents a week from being secured that had already taken €50 in.
- [x] Each savings account carries its own streak; deposits into one never lengthen another's.
- [x] The savings account page shows the current streak and the best-ever streak beside the week's progress.
- [x] DEBUG logging shows the weeks the derivation walked back through, which of them were secured, and where the streak was found to end.
- [x] Points earned by a deposit are still unchanged: one per whole euro.
