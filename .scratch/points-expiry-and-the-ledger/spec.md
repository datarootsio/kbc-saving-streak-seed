# Points expiry, and a ledger of the money that moved

Status: ready-for-agent

## Problem Statement

Points accumulate forever. A customer who saved hard in 2024 and has not been back since still has
every point they earned, worth exactly what it was worth then, and the bank carries that liability
with no end to it. Nothing in the application notices that a point has been sitting unspent for a
year.

The ledger underneath already anticipates the rule and does not enforce it. Points are kept as dated
batches and spending draws from the oldest first, with a comment in `PointsService.spend` saying in
so many words that this exists so "the ones nearest expiring leave first" — and that none of them
expire yet. `SchedulingIsOn` exists for a job that does not exist. `MovableClock` exists so that a
twelve-month rule can be reached inside a training day. Every seam is in place and the rule is
missing.

Separately, a customer cannot see the money they have moved as one story. A savings account page
lists the deposits into that account and the withdrawals out of it, in two separate lists, one
account at a time. Somebody saving towards two goals has no single place that answers "what have I
actually moved, and when?" — which is the question a person asks before they ask anything else about
their money.

## Solution

**Points expire twelve months after they are earned.** A batch that is still unspent when its
twelve-month anniversary passes is retired: whatever was left in it stops counting towards the
balance and can never be spent. Twelve months is counted as calendar months in the zone this
application already counts calendar things in, so a batch earned on 15 January expires on 15 January
the following year and one earned on 29 February expires on 28 February.

**Nothing rescues a batch except spending it.** Paying in again does not extend the life of points
already earned — it earns new points with twelve months of their own. This is what "twelve months of
inactivity" means here: the inactivity is the batch's, not the customer's, and a batch is active only
in the moment it is spent.

**The oldest points always leave first, whichever way they leave.** Spending already draws from the
oldest batch first, which is what makes the rule fair rather than punitive: a customer who keeps
claiming rewards is spending exactly the points that were about to expire, and a batch only ever
expires because it survived twelve months of the customer not spending that far down their pot. The
sweep works oldest-first too, so that the one line it logs reads as a chronology.

**A nightly job does the sweeping, and a trainer can run it on demand.** It is the application's
first scheduled job of its own — until now expiry was an exercise and the scheduling machinery stood
empty. It runs at 03:00 every day, reads the application's clock rather than the machine's, and is
reachable through the development jobs endpoint so that a demonstration is "wind the clock a year on,
run the job" rather than a year of waiting.

**A customer is told what they are about to lose.** The customer overview and each savings account
report how many points expire next and the day they do. Points that vanished with no warning are
indistinguishable from points that went missing.

**One ledger of the money that moved.** A new endpoint answers with every deposit into and every
withdrawal out of every savings account the customer holds, newest first, as one chronological list:
which way the money went, how much, between which two accounts, when, and — for a deposit — what it
earned. A new page in the frontend shows it.

## User Stories

1. As a customer, I want points I have not spent within twelve months to expire, so that the scheme is one the bank can afford to keep offering.
2. As a customer, I want my points to last a full twelve months from the moment I earned them, so that I know exactly how long I have.
3. As a customer, I want each batch of points to expire on its own anniversary, so that saving again does not shorten the life of what I already have.
4. As a customer, I want paying in again not to extend the life of the points I already had, so that the twelve months means what it says.
5. As a customer, I want the reward I claim to be paid for with my oldest points, so that claiming anything at all protects me from expiry.
6. As a customer, I want points I have already spent not to expire again, so that no figure is counted twice.
7. As a customer, I want to see how many points expire next and on what day, so that I can claim something before I lose them.
8. As a customer, I want expired points to be gone from my balance and unspendable, so that the figure on the screen is what I can actually spend.
9. As a trainer, I want to run the expiry job on demand after winding the clock forward, so that a twelve-month rule can be demonstrated in an afternoon.
10. As a trainer, I want the expiry job to appear in the list of jobs that can be run, so that nobody has to read the source for its name.
11. As a reviewer, I want one log line per sweep carrying how many batches and points went, so that a balance that dropped can be explained from the log alone.
12. As a customer, I want one list of every movement of money in and out of my savings, so that I can see what I have actually done with it.
13. As a customer, I want that list to cover every savings account I hold, so that saving towards two goals does not mean reading two histories.
14. As a customer, I want each entry to say which way the money went and between which accounts, so that a movement explains itself.
15. As a customer, I want each deposit in the list to say what it earned, so that the money and the points are one story.
16. As a customer, I want the list newest first, so that the most recent thing I did is the first thing I read.

## Technical Decisions

**The rule lives in the Points module, and nothing outside it learns how points are stored.** The
twelve months, the anniversary arithmetic and the sweep are all behind `PointsService`. The job that
triggers the sweep is in the same package and package-private, like `PointsOnStartUp`. No caller
gains the ability to expire one batch, or to learn that batches exist.

**A batch records its own expiry rather than being emptied.** `PointsCredit` gains a nullable
`expiredAt`, and an expired batch keeps `remainingPoints` untouched — that figure is then what was
left in it when it expired, which is the only place the number survives. The balance and the spending
query exclude expired batches explicitly. Zeroing `remainingPoints` instead would make an expired
batch indistinguishable from a fully spent one and would throw away the figure the log line and the
history are made of.

**The caller says when, as it does for every other moment in this application.** `PointsService`
takes the moment the sweep is running at rather than reading a clock, exactly as `creditPointsFor`
takes the moment the money moved. The job reads the injected `Clock`, so a wound-forward clock moves
expiry with everything else.

**Twelve months is calendar months, in `Europe/Brussels`.** The zone is the one `SavingsWeek` already
names, and it is named there rather than a second time here. Calendar months rather than 365 days,
because a customer reads "twelve months" as an anniversary and `plusMonths` is what puts 29 February
on 28 February rather than on 1 March.

**No migration.** `expiredAt` is a new nullable column and `ddl-auto=update` adds it; every batch
already in a database reads as un-expired, which is what it is. Unlike the savings-account column
`PointsOnStartUp` had to drop, nothing here was written `not null` and nothing has to be filled in.
Verified by starting this release against a file the previous one wrote: the column appeared, the
balance it had earned came back unchanged, and the sweep took nothing. No database is committed to
this repo (`data/*.db` is ignored), so the only upgrade path that exists is that one.

**The ledger is the Deposits module's third face, over the records it already keeps.** Deposits and
withdrawals live in one package and a list of both is that package's to assemble. It is asked for by
savings account rather than by customer, because a withdrawal does not record whose it was and the
web layer already asks Accounts which accounts a customer holds — the same shape
`CustomerController.accountsOf` uses to put a balance beside each account.

**Nothing in the ledger is added up.** No running balance and no totals: a running balance across
several accounts is not a figure that means anything, and a total the frontend worked out would be the
one figure on these screens that the backend did not derive.

**The day a customer is shown is decided in the backend.** `pointsExpiringNextOn` travels as a
plain calendar date rather than as a moment. Which day a moment falls on depends on the zone it is
read in, and a page turning an instant into a date would be picking the zone of whatever machine
happened to be drawing the screen — a customer in London would be shown a deadline a day early. The
zone is named once, in `SavingsWeek`, and Points reads it on the customer's behalf.

**Nothing is handed back from the sweep.** `PointsService.expireOldPoints` answers `void`. Its one
caller runs on a schedule with nobody waiting on it, so a count returned would be a count nothing
could read — and a count of *batches* would be this module saying out loud that it keeps batches,
which is the one thing about its storage it does not say. What the sweep took is in its INFO line.

**Logging follows the house rule.** One INFO line per sweep with the batches, the points and the
cut-off that decided them; DEBUG for the batches considered and the anniversary each was judged
against; WARN on nothing, because a sweep refuses nobody. One DEBUG line per ledger read with the
counts of each direction, in the style of the existing history reads.

## Testing Decisions

**Almost every test drives the application over HTTP, at the one seam this repo has.** Expiry is
observable in four places a test can reach: the points balance, the refusal a claim gets when the
points have gone, the expiring-next figures on the account resources, and the jobs endpoint.

**The calendar rule itself gets the repo's first unit test, because the HTTP seam cannot reach it.**
The only way to move time in this application is `POST /api/dev/clock/advance`, which takes whole days
and refuses to go backwards — so no request a test can make lands an hour past an anniversary, or on
29 February, or in the hour the clocks go forward. The expiry API tests wind the clock a comfortable
379 days precisely so that they are about the rule working rather than about which day of which month
the run happens on, which is exactly what makes them blind to the boundary. `PointsExpiryTest` asserts
the two functions that decide it directly and says in its own words why it is not an API test. It is
the test that fails when twelve calendar months are turned into 365 days, when the cut-off's slack is
put in the other direction, or when the day is read in UTC — three mutations the API tests all
survive.

**Expiry tests need their own application, for the reason the streak tests do.** A test that winds
the clock a year on cannot share a clock or a database with anything else, so the expiry tests use
`AnApplicationWithAClockToMove` — which gains the ability to run a named job and to advance by an
arbitrary number of days.

**The cases worth their own test:** a batch a year old expiring; a batch not yet a year old
surviving the same sweep; two batches earned a year apart, where the sweep takes the older and leaves
the newer; a batch spent before its anniversary having nothing left to expire; expired points being
unspendable and the refusal saying the smaller balance; a second sweep over the same batches taking
nothing; the expiring-next figures before and after a sweep; and the job being listed and runnable by
name.

**The ledger's tests:** deposits and withdrawals across two of one customer's savings accounts coming
back as one list newest first, with the direction, both account identifiers, the amount and the points
each deposit earned; a customer with no movements answering with an empty list; and a customer nobody
has heard of being refused.

## Out of Scope

- **Warning a customer before their points expire.** The figures are on the resources; a notification
  is a separate extension, as it is for the streak.
- **Reviving expired points.** Expiry is final. A batch with an `expiredAt` is never un-expired, and
  there is no endpoint that would.
- **Expiring anything other than points.** Money does not expire, and the loyalty-rate bonus that
  turns on the age of a deposit is a different exercise.
- **A configurable twelve months.** It is a constant in one place, as €50 and 1.50× are.
- **Running balances or totals in the ledger.** See the technical decision above.
- **Reward claims and points expiry in the money ledger.** It is a ledger of money. What has come out
  of the points pot is already listed beside the rewards catalogue.
- **Paging the ledger.** A training application's histories are short and every other list in it is
  answered whole.

## Further Notes

The clock only moves forwards, so a demonstration of expiry is one-way: once a trainer has wound a
year on and swept, those points are gone from that database for good. That is the rule working, and it
is why the seed ships a throwaway database rather than a precious one.

A batch becomes expirable at its anniversary but is actually retired by the next run of the job, which
is at 03:00. The figure reported as "expires next" is the anniversary rather than the sweep, because
the anniversary is the promise made to the customer and the sweep is an implementation detail of
keeping it. Between the two a customer can still see and still spend points whose day has passed —
which is the outcome the rule wants anyway, so the generosity is on the right side.

Three in the morning is three in the morning wherever the machine thinks it is standing; the cron
carries no zone. It makes no difference to what the sweep decides, because that comes off the
application's clock rather than off the hour the job happened to fire.

Scheduling ships in every profile, so every test context in the suite registers the 03:00 cron. A run
crossing three in the morning on the machine's own clock would fire a real sweep against a test
database. The damage is bounded — only the expiry classes wind a clock past a year, and each owns its
own file — but it is a latent flake with no seam to switch off, and it is the price of the job being
part of the application rather than part of the lab.

**An unresolved intermittent, left written down rather than papered over.** Twice in roughly twenty
full runs of the suite, a freshly booted `AnApplicationWithAClockToMove` answered its very first read
of `/api/customers` with something that would not deserialize, failing one expiry class. It did not
reproduce in a hundred-odd runs afterwards, under load or otherwise, and the cause was not
established: `TestRestTemplate` does not throw on an error status, so the only evidence left was
Jackson complaining about the type it had been asked to read. The mechanism is not new — the helper
and the seeded-account lookups both predate this feature and five streak classes already use them —
but this feature adds six more applications to the run and so makes whatever it is roughly twice as
likely to be met. What was done about it is the only thing that could honestly be done without a
cause: `SeededAccounts` now reports the status and the body it actually got, and says whether an
immediate second read succeeded, so the next occurrence tells whoever sees it what happened instead
of naming a Java type. Nothing is retried into a pass.
