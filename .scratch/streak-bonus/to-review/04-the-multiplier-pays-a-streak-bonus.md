# 04: A deposit is paid at the streak's multiplier and credits a streak bonus

**What to build:** The payoff. A deposit now earns one point per whole euro *multiplied by* the
streak multiplier, and the uplift is credited as a streak bonus beside the base points.

One rule decides every case: **a deposit is paid at the multiplier of the streak as it stands once
that deposit has been counted.** If the deposit is what carries its week past €50, the streak is a
week longer by the time the deposit is priced, so the deposit itself already earns the new, higher
rate. If it does not secure the week, it earns whatever the live streak was already paying. If there
is no live streak, the length is zero and the rate is 1.00×. Implement it as one function of the
streak length after the deposit, not as a set of special cases.

The ladder: a streak of zero or one week pays 1.00×; a streak of *n* weeks pays
`min(1.00 + 0.10 × (n − 1), 1.50)`, so the sixth week and every week after it pays 1.50×. The step
and the cap are named constants here.

Points stay whole and round down twice: floor the amount to whole euros, multiply, floor the product.
€7.60 at 1.30× is 7 base points and 9 in total, so a bonus of 2. Each deposit credits its base
accrual exactly as it does today, plus a streak bonus for the uplift when the uplift is non-zero. The
multiplier a deposit was paid at is recorded at the moment of the deposit and never recomputed — the
derived figures answer "what is my rate", the ledger answers "what was this deposit paid".

The deposit response reports the base points, the streak bonus points, the multiplier applied and
the total; its existing points-earned figure means the total credited, which is unchanged at 1.00×
and therefore still true of every deposit made before this feature existed. The savings account
resource and page gain the current multiplier — the rate the next deposit will earn at.

**Blocked by:** 01 (points reported by reason) and 03 (streak length and best streak).

**Status:** needs-review

- [x] A deposit made with no live streak earns one point per whole euro, exactly as before.
- [x] The deposit that carries a week past €50 is itself paid at the multiplier of the streak that now includes that week.
- [x] Deposits made earlier in that same week keep the points they were already paid; nothing is topped up retrospectively.
- [x] The second consecutive secured week pays 1.10×, the third 1.20×, the sixth 1.50×.
- [x] The seventh and every later consecutive secured week still pays 1.50× and no more.
- [x] After a lapse, the first week of the new streak pays 1.00× again.
- [x] A deposit below €50 earns points at the current multiplier and does not secure the week on its own.
- [x] Points are whole: €7.60 at 1.30× earns 7 base and 2 bonus, and €3 at 1.50× earns 3 base and 1 bonus.
- [x] A deposit of less than one euro earns nothing at any multiplier, bonus included.
- [x] The points balance rises by the base points plus the bonus points, and the two always sum to the total the deposit reported.
- [x] The bonus is credited as its own batch under its own reason; no batch is credited when the uplift is zero.
- [x] Spending points still draws from the oldest batch first and treats a bonus batch no differently from a base one.
- [x] The deposit response reports base points, streak bonus points, the multiplier applied and the total, and its existing points-earned figure is the total.
- [x] The savings account resource and the savings account page report the current multiplier, alongside the streak figures already there.
- [x] Withdrawing money takes back neither the base points nor the bonus.
- [x] No table is added and no column is dropped or retyped; the new reason is another value in the column that already records why points were earned.
- [x] One INFO line per deposit carries the week, the new savings in it, whether this deposit secured it, the streak length, the multiplier, the base points and the bonus points.
