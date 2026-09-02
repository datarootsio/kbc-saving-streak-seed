# Seed repo lab-readiness: withdrawal and a controllable clock

Status: ready-for-agent

## Problem Statement

Saving Streak is the seed repo participants extend during the training. Four extensions are built on
top of it during the exercises — points expiry, the loyalty-rate bonus, peer-to-peer gifting, and
notifications — and two things the seed does not have block three of those four before anybody starts.

Money can only ever move *into* a savings account. There is no way to take it out. The loyalty-rate
bonus is defined entirely in terms of withdrawal: a deposit that stays untouched for twelve months
earns 10% of its base points, withdrawing it before an anniversary forfeits that year's bonus, and a
partial withdrawal comes off the oldest deposit first. None of those rules can be reached in a repo
where money never leaves, so the exercise degenerates into "every anniversary vests, always", and the
notification warning a customer that a bonus is about to be forfeited can never fire at all.

The application also has no way to make time pass. Points expire twelve months after they are earned;
bonuses vest on anniversaries; forfeit warnings run a fortnight ahead of one. Every one of those is
unreachable inside a single training day. A participant who builds the expiry job correctly has no way
to watch it work, and a trainer has nothing to demonstrate. The technical design flagged this and it
was never built: "waiting on real @Scheduled cron timing during a training session is a bad experience."

## Solution

Two additions to the seed, both shipped before the training rather than built during it.

A customer can move money back out of a savings account into one of their own current accounts. A
withdrawal names the amount and the destination account, is refused with a readable reason when it
cannot happen, and draws down the account's deposits oldest first — so that when the loyalty-bonus
exercise arrives, "which deposits did this withdrawal touch?" is a question the records already answer.

And the application reads the time from a clock it is given rather than from the system, with a
development-only way to move that clock forward and to run scheduled jobs on demand. A participant can
then deposit money, advance the clock a year, run the job they just wrote, and see what happened —
inside a coffee break rather than inside a calendar year.

Neither addition changes any rule that already exists. Points earned by a deposit are not taken back
when the money is withdrawn: base points are credited immediately and unconditionally, and nothing in
the requirements makes them contingent on the money staying. Only the loyalty bonus depends on the
money staying put, and that does not exist yet.

## User Stories

1. As a customer, I want to move money out of a savings account back into my current account, so that money I have saved is not trapped there.
2. As a customer, I want to choose which of my current accounts a withdrawal goes back to, so that the money lands where I expect it.
3. As a customer, I want a withdrawal to arrive in full or not at all, so that money is never lost between two accounts.
4. As a customer, I want to be told why a withdrawal was refused, in the same terms a refused deposit is explained, so that I can tell a mistake from a rule.
5. As a customer, I want to be refused when I ask to withdraw more than the savings account holds, so that I cannot overdraw an account meant for saving.
6. As a customer, I want to be refused when I name a current account that is not mine, so that money cannot be moved into somebody else's account.
7. As a customer, I want to be refused when I name a savings account that is not mine, so that nobody can move money out of my savings.
8. As a customer, I want to be refused when I name an amount that is not an amount of money, so that a typo does not become a transfer.
9. As a customer, I want to be refused when I ask to withdraw nothing or a negative amount, so that a withdrawal always means what it says.
10. As a customer, I want my savings balance to drop by exactly what I withdrew, so that the figure on screen matches what happened.
11. As a customer, I want my current account balance to rise by exactly what I withdrew, so that the two halves of the transfer agree.
12. As a customer, I want to keep the points a deposit earned even after I withdraw the money, so that a reward I have already earned is not taken away.
13. As a customer, I want to see my withdrawals alongside my deposits, newest first, so that I can account for every movement on the account.
14. As a customer, I want each withdrawal to show its amount, its destination account and when it happened, so that the history is checkable against my bank statement.
15. As a customer, I want a withdrawal to leave my other savings accounts untouched, so that accounts I keep separate stay separate.
16. As a customer, I want to withdraw the whole balance in one go, so that closing out a savings goal does not take several attempts.
17. As a customer, I want a withdrawal larger than my oldest deposit to draw on later ones too, so that the balance is what I can withdraw, not one deposit at a time.
18. As a customer, I want to withdraw from the web page rather than by calling an API, so that the feature is usable the same way depositing is.
19. As a customer, I want the withdrawal form to sit next to the deposit form, so that the two directions of the same movement are in one place.
20. As a customer using a screen reader or keyboard, I want the withdrawal form to be reachable and labelled the way the deposit form is, so that the new feature does not exclude me.
21. As a trainer, I want the seed repo to record which deposits a withdrawal drew down, so that the loyalty-bonus exercise has the facts it needs to decide a forfeiture.
22. As a trainer, I want withdrawals to draw on the oldest deposit first, so that the "oldest deposit first" rule in the requirements is already true when the exercise arrives.
23. As a trainer, I want to move the application's clock forward, so that I can demonstrate a twelve-month rule during a one-day training session.
24. As a trainer, I want to run a scheduled job on demand, so that a demonstration does not wait on a cron expression.
25. As a trainer, I want the clock to stay where I moved it after a restart, so that an application that crashes mid-demonstration does not rewind to day zero.
26. As a trainer, I want the clock controls to exist only in the development profile, so that nothing in the seed teaches participants to ship a time machine.
27. As a participant, I want every part of the application to read the same clock, so that advancing it moves deposits, points and jobs together rather than some of them.
28. As a participant, I want to deposit money, advance the clock a year and run the expiry job I wrote, so that I can see my own work succeed or fail.
29. As a participant, I want to withdraw money and then check whether a bonus was forfeited, so that I can test the rule I was asked to implement.
30. As a participant, I want the withdrawal endpoint to refuse in the same shape as every other refusal, so that I have a pattern to follow when my own feature has to refuse something.
31. As a participant, I want tests for withdrawal in the seed, so that I can see what a test of this codebase looks like before writing one.
32. As a participant, I want the seed's test packages named after features, so that the package I add for my exercise has an obvious name and place.
33. As an agent working in this repo, I want the withdrawal flow to mirror the deposit flow, so that the existing code answers most of my questions about how to write the next feature.
34. As an agent, I want the clock available as an injected dependency, so that I can write a scheduled job without reaching for the system time.

## Implementation Decisions

**A withdrawal is a movement between two accounts, modelled the way a deposit already is.** The
Deposits module records money arriving; a parallel capability records it leaving. It names the savings
account it left, the current account it returned to, the amount, and the moment — the mirror image of
what a deposit records. Whether it lives in the existing Deposits module or beside it is the
implementer's call; what matters is that one module owns both directions, because the money balance is
derived from both and splitting that derivation across two owners is how the two figures drift apart.

**A deposit gains a remaining amount; it does not gain an anniversary count.** A deposit currently
records only what was put in. Withdrawal needs to know how much of each deposit is still there, so
deposits gain that figure, starting equal to the amount deposited. The savings account's money balance
changes from the sum of deposit amounts to the sum of what remains of them. The count of anniversaries
a deposit has been paid for is deliberately *not* added — nothing in this spec reads it, and it belongs
to the loyalty-bonus exercise that introduces the concept.

**A withdrawal draws down deposits oldest first, and records which ones it touched.** The allocation
is recorded, not merely computed: the loyalty-bonus exercise has to ask whether a particular deposit
was reduced before a particular date, and a balance figure cannot answer that. This mirrors the points
ledger, which already spends the oldest batch first and says in its own comments that it does so to
leave the expiry slice something to build on.

**Refusals reuse the vocabulary the deposit flow already established.** The pairing of a savings and a
current account is already expressed as a single answer covering "no such savings account", "no such
current account", "held by different customers" and "held by one customer", and a withdrawal asks
exactly the same question in the opposite direction. Not enough money is a distinct refusal, as it is
for deposits, but it is asked of the savings account rather than the current account. Every refusal
answers as a problem document carrying its reason in the detail field, which the frontend renders
unchanged.

**A withdrawal is all-or-nothing in one transaction.** The money leaving the savings account, the
deposits being drawn down, the allocation records, and the money arriving in the current account either
all happen or none do. This is the same guarantee the deposit flow makes and for the same reason: a
half-applied transfer is a balance nobody can explain.

**Points are not reclaimed by a withdrawal.** Base points are credited immediately and unconditionally
by the requirements. Withdrawing the money does not undo them, and the points ledger is not touched by
this feature at all. The only rule that ties points to money staying put is the loyalty bonus, which is
an exercise, not part of this spec.

**The API mirrors the deposit endpoints.** Creating a withdrawal takes the amount and the destination
current account; listing them returns the account's withdrawals newest first. The amount travels as the
text that was typed, exactly as a deposit's does, because what counts as an amount of money is the
backend's decision and rounding it through a number on the way in would move that decision to the
caller. Everything stays under the existing /api prefix.

**The frontend gains a withdrawal form beside the deposit form, and shows withdrawals in the history.**
The savings account page already formats money, points and dates, renders a refusal from the detail
field, and disables a form while a request is in flight; the withdrawal form follows all of it. A
backend-only withdrawal would leave the seed's own demonstration half-finished, and one of the training
modules asks participants how they would validate a UI change — which needs a UI that does the thing.

**Time is read from an injected clock, everywhere.** Every place the application currently asks the
system for the current moment takes it from a clock supplied to it instead. The deposit flow already
does the right thing here — it passes the moment a deposit happened into the points ledger rather than
letting the ledger read a clock of its own — so the change is mostly about where that first moment
comes from.

**The clock is movable in the development profile only, and its offset survives a restart.** A
development-only endpoint advances the clock by a number of days; another runs a named scheduled job
immediately. The offset is stored so that restarting the application does not return a participant to
day zero halfway through an exercise. Neither endpoint exists outside the development profile, and
neither appears in the frontend.

**The clock ships as its own commit at the end of the seed's history.** The seed repo's per-slice
history is training material in its own right, and a lab affordance should read as one rather than
being folded into a feature commit.

## Testing Decisions

**A good test here asserts external behaviour over HTTP and nothing below it.** The repo has exactly
one test seam and states its own reasoning: the whole application booted for real, talking to a real
SQLite database, driven the way the frontend drives it. Asserting at a service or repository level
would bind the tests to storage decisions the extension exercises need free to change. This spec adds
no new seam. The injected clock is a production seam, not a test one — tests move time by calling the
development endpoint over HTTP like any other request.

**Tests assert on what their own requests changed.** One database file serves the whole run, so a test
reads a balance before and after its own call rather than asserting an absolute figure it did not put
there. The existing tests do this and the new ones follow.

**Test packages are named after features, matching the existing convention** — points earned, deposit
refusals, deposit history, claiming rewards, redemption refusals. Withdrawal adds packages in the same
shape: one for the successful movement and its effect on both balances, one for the refusals, and one
for the clock.

**Prior art to follow directly.** The all-or-nothing deposit test shows how to prove a failed transfer
left nothing behind on either side. The deposit refusal test shows the expected shape of every refusal,
including that the reason reaches the caller in the detail field. The deposit history test shows how a
listing is asserted, ordering included. The seeded-accounts helper arranges every test from the API,
never from the database, and gives out identifiers that deliberately belong to nobody for the
"no such account" cases.

**What the tests must cover.** A withdrawal reduces the savings balance and raises the current account
balance by the same amount. It draws down the oldest deposit first, and a withdrawal larger than that
deposit reaches into the next one. It leaves the account's points untouched. It leaves the customer's
other savings account untouched. Each refusal — unknown savings account, unknown current account,
accounts held by different customers, an amount that is not money, an amount of zero or less, and more
than the account holds — answers with its reason, and leaves both balances exactly where they were.
For the clock: advancing it changes what the application records as "now" for a subsequent deposit, the
advanced value survives a restart, and the endpoints are absent outside the development profile.

## Out of Scope

The four extensions themselves — points expiry, the loyalty-rate bonus, peer-to-peer gifting and
notifications — are exercises, not seed content, and nothing in this spec builds any part of them. The
anniversary count on a deposit belongs to the loyalty-bonus exercise. A record of *why* points left an
account (spent, expired, gifted) belongs to the expiry exercise, which is expected to discover that the
current ledger cannot answer the question and add the record itself.

Also out of scope: replacing schema generation with migrations, authentication of any kind, any change
to how points are earned or spent, and any change to the reward catalogue.

The documentation work settled alongside this spec belongs to the workshop repo, not here: bringing the
technical design into line with what actually shipped, removing the contradictory seed-state claims from
the MVP scope and lab README, mapping the four extensions onto their training modules, and the script
that generates the participant repo. They are tracked separately.

## Further Notes

Three documents currently disagree with this spec's premise and with each other. The technical design
places withdrawal in the loyalty-bonus exercise rather than the seed, and describes a points ledger,
catalogue entity and stack that were never built. The MVP scope states in as many words that this
repository is not a seed repo. The lab README describes the seed as repurposed KBC code. All three
need editing to match; none of it changes the work described here.

The clock is the gating item. Withdrawal makes the loyalty-bonus exercise coherent, but without a way
to move time, expiry, the bonus and forfeit notifications are all unobservable inside a training day —
three of the four extensions. If only one of the two additions ships, it should be the clock.
