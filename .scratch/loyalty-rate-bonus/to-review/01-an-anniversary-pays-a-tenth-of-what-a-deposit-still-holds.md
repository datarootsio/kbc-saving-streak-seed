# 01: An anniversary pays a tenth of the euros a deposit still holds

Status: needs-review

**Blocked by:** None (can start immediately).

**What to build:** Money that stays in savings earns again. Every twelve months a deposit's money is
still there, that deposit pays a loyalty bonus of a tenth of the whole euros still sitting in it —
one point per euro, a tenth of that, rounded down — and the points land in the customer's pot like
any other points. A €500 deposit left alone pays 50 points a year after it landed, 50 more a year
after that, and 50 more the year after.

The clock belongs to the deposit and recurs. Each anniversary is twelve calendar months multiplied
out and added once to the moment the money landed, in the zone the application already counts
calendar things in, so the anniversaries never drift and the third falls exactly two years after the
first. A deposit made on 29 February has its anniversaries clamped to the 28th in the years without
a 29th, which is the answer somebody reading a calendar would give.

What an anniversary is worth is decided from what is still in the deposit at that moment, which is
where the forfeit comes from without a rule of its own: a deposit drawn down to nothing is worth
nothing on its anniversary, and one drawn halfway down is worth half. A deposit holding under €10 is
worth nothing at all, because a tenth of nine euros rounds down to no points — the same way €0.99
has always earned no base point.

A sweep pays these, nightly, taking the moment from the application's clock rather than the
machine's, and named for what it does so that a trainer can type it into the development jobs
endpoint and watch a year's loyalty arrive in an afternoon. It pays every anniversary a deposit has
passed and has not been paid for yet, each credited as its own batch dated at its own anniversary —
so winding the clock three years forward and running the job once pays three bonuses, and a deposit
made before this scheme existed is paid every anniversary it has already served.

The rule, the recurring clock, the record of what has been paid and the job that pays it are one new
module's business. The points ledger gains one more reason a deposit can have earned under and one
more way in; it learns nothing about anniversaries. Deposits answers one new factual question —
which deposits still hold money and landed before a given moment, and how much is in them — and
keeps its opinion about points to itself, which is none.

Running the sweep a second time pays nothing more. What has been paid is written down against the
deposit and the anniversary it was paid for, so a second pass over the same rows cannot pay twice —
the same way an expired batch carries the moment it went. The record also keeps the euros the bonus
was worked out from, because the deposit's remaining amount moves on afterwards and that figure is
otherwise gone.

- [x] A deposit left untouched for twelve months earns a tenth of its whole euros, and the points count towards the customer's balance.
- [x] A sweep run inside the twelve months pays nothing, and says out loud that it looked.
- [x] A deposit left untouched pays again on its second and third anniversaries, each counted from the day it landed rather than from the last payment.
- [x] A deposit whose anniversaries have all passed unpaid is paid every one of them in a single sweep, each dated at the anniversary it is for.
- [x] A deposit holding under €10 earns nothing on its anniversary.
- [x] A deposit holding nothing earns nothing, and is not considered by the sweep at all.
- [x] Running the sweep twice pays nothing the second time.
- [x] The bonus is a tenth of the euros' own points, never a tenth of what the streak multiplier paid.
- [x] The sweep can be run out of turn through the development jobs endpoint, by name, and reports itself in the list of jobs that can be run.
- [x] The sweep takes its moment from the application's clock, so a wound-forward clock is what decides which anniversaries have arrived.
- [x] Paying a bonus moves no money: no ledger entry, no change to any balance in euros.
- [x] Paying a bonus secures no week and changes no streak.
- [x] The anniversary arithmetic gets a unit test of its own for the calendar cases the HTTP seam cannot reach — twelve months multiplied out, February clamping, and the ordinal of a given date.
- [x] The sweep logs one INFO line per run carrying the moment it judged against, the cut-off its query used, how many deposits it considered, how many anniversaries it paid and the points; and one DEBUG line per deposit carrying the anniversary, its ordinal, what remained, the whole euros and the points, or the reason it was passed over.
