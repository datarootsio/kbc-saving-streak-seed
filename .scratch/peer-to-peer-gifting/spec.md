# Peer-to-peer gifting: points one customer can hand to another

Status: ready-for-agent

## Problem Statement

Points in this application are a private currency. A customer earns them by moving money into
savings, earns more of them by keeping a streak, earns more again on each anniversary the money
survives — and then the only thing they can ever do with them is spend them on their own reward. The
pot has one way in and one way out, and both ends are the same person.

That leaves several perfectly ordinary intentions with nowhere to go. A customer holding 180 points
and no interest in the cinema cannot pass them to a partner who is 20 short of the family pack. A
parent saving steadily cannot hand a child the price of a snack voucher. Two people banking here
cannot agree that one of them should have the coffee. Every one of those is a customer wanting to do
something generous with a thing they own, and the application's answer is that points are stuck
where they landed.

There is also a quieter cost. A pot whose only exit is a fixed catalogue of four rewards leaves a
customer holding points they will never use — the balance is theoretically theirs and practically
inert, and it drains away twelve months after it was earned whether they wanted it or not. Points
that can be given away are points that get used.

## Solution

A customer can give points to another customer of the bank, whenever they like, as many as they
like.

A **gift** names one recipient and a number of points. The points leave the sender's pot and arrive
in the recipient's, and that is the whole of it: no acceptance step, no request, no thank-you, no
reversal. The recipient does not have to be asked and cannot decline; the points are simply theirs
from the moment the gift goes through, spendable on any reward in the catalogue and giftable onward
to somebody else.

There is deliberately **no limit** — not on how often a customer may give, not on how much may go in
one gift, not on how much may go in a day. The only bound is the one arithmetic imposes: a customer
cannot give away points they do not hold. Four things are refused and nothing else is: a recipient
nobody banks under, a gift to yourself, a number of points that is not a positive whole number, and
a gift larger than the sender's balance.

Gifted points keep the age they were earned at. A point earned last March expires next March
whichever pot it is sitting in when the twelve months are up — so a gift moves points without
resetting their clock, and a chain of gifts between two customers cannot keep points alive forever.
That is the one rule in this feature a customer might not have guessed, and it is what makes "a
point lasts twelve months" true rather than nearly true.

A gift moves points and nothing else. No euros move, no week is secured, no streak changes, and
nothing appears in the ledger of money that moved. Each customer gets one list of the gifts they
have been part of — sent and received together, newest first, each row naming the other person and
what it was worth — reached from its own page, alongside the form that sends one.

## User Stories

1. As a customer, I want to give some of my points to another customer, so that points I will not use can be used by somebody who will.
2. As a customer, I want to choose who receives my gift from the people who bank here, so that I do not have to remember an account number.
3. As a customer, I want to name the recipient by the email address they bank with, so that I identify them the same way I identify myself when I sign in.
4. As a customer, I want to say exactly how many points to give, so that I decide the size of the gift rather than being offered fixed amounts.
5. As a customer, I want to give points as often as I like, so that generosity is not rationed.
6. As a customer, I want to give as many points as I hold in one gift, so that I can clear my pot in a single act if that is what I mean to do.
7. As a customer, I want no daily ceiling on what I give away, so that the application does not second-guess me.
8. As a customer, I want to be refused when I try to give away more points than I have, so that my balance can never go negative.
9. As a customer refused for want of points, I want to be told what I hold, so that I can correct the figure without going to look it up.
10. As a customer, I want to be refused when I name an email address nobody banks under, so that a typo does not silently send my points nowhere.
11. As a customer, I want to be refused when I try to give points to myself, so that a pointless act is named as one rather than quietly succeeding.
12. As a customer, I want to be refused when I type something that is not a whole number of points, so that half a point is never invented.
13. As a customer, I want to be refused when I ask to give zero or fewer points, so that a gift always means something.
14. As a customer, I want the reason a gift was refused shown to me in words, so that I can tell a typo from an empty pot.
15. As a customer, I want my balance to fall by exactly what I gave, so that nothing is lost or skimmed on the way.
16. As a recipient, I want my balance to rise by exactly what was given, so that a gift is a transfer rather than a bonus.
17. As a recipient, I want the points to be mine the moment they are given, so that there is nothing for me to accept or claim.
18. As a recipient, I want gifted points spendable on any reward in the catalogue, so that they are worth what every other point of mine is worth.
19. As a recipient, I want gifted points spent in the same oldest-first order as the rest of my pot, so that spending works the one way I already understand.
20. As a recipient, I want to be able to give gifted points onward to somebody else, so that a gift is not a dead end.
21. As a recipient, I want gifted points to expire twelve months after they were originally earned rather than twelve months after they were given, so that the twelve-month rule means one thing.
22. As a recipient, I want gifted points that are near the end of their twelve months to appear in what I am told expires next, so that the warning covers everything I stand to lose.
23. As a customer, I want a gift drawn from my oldest points first, so that the points nearest their expiry are the ones that get used.
24. As a customer, I want a gift larger than any one batch of my points to be drawn from several, so that the size of a gift is bounded by my balance and not by how I earned it.
25. As a recipient of points drawn from several batches, I want each part to keep the age it was earned at, so that my pot expires honestly rather than all at once.
26. As a customer, I want to see every gift I have sent, so that I have a record of what I gave and to whom.
27. As a customer, I want to see every gift I have received, so that I know where the points in my pot came from.
28. As a customer, I want sent and received gifts in one list marked by direction, so that the whole story is in one place and reads chronologically.
29. As a customer, I want each row to name the other person by name, so that the list reads as people rather than customer numbers.
30. As a customer, I want each row to say when the gift happened, so that I can line it up against the rest of my activity.
31. As a customer, I want my gift list to still show a gift whose points have since expired or been spent, so that the record of what I did survives the points I did it with.
32. As a customer, I want a gift to move points and leave my money alone, so that being generous with points never touches my euros.
33. As a customer, I want a gift not to count as a week's saving, so that my streak still measures money I actually paid in.
34. As a customer, I want a gift not to appear in the ledger of money that moved, so that the ledger stays a record of euros.
35. As a recipient, I want gifted points kept apart from what my deposits earned, so that the breakdown of a deposit stays a statement about that deposit.
36. As a customer, I want the gift page reachable from my home screen, so that I do not have to go looking for it.
37. As a customer, I want the send button unavailable while a gift is in flight, so that one click cannot become two gifts.
38. As a customer, I want to be shown the gift that went through, so that I get confirmation of what I just did.
39. As a customer, I want my balance on screen to update as soon as a gift goes through, so that the page never shows me points I no longer hold.
40. As a customer, I want a recipient list that does not offer me myself, so that the one refusal I could stumble into is unreachable from the page.
41. As a trainer, I want to sign in as one demo customer, give points to the other, and sign in as them to see the points arrive, so that I can demonstrate the feature end to end with the data the application already seeds.
42. As a trainer, I want to wind the clock forward and see gifted points expire on the anniversary of the day they were earned, so that the inherited clock is demonstrable rather than merely described.
43. As a reviewer, I want each gift logged with both customers, the points and the moment, so that a balance that changed without a deposit is explainable from the log.
44. As a reviewer, I want every refused gift logged with its reason, so that a customer's complaint can be traced to the rule that stopped them.
45. As a reviewer, I want the batches a gift was drawn from logged at DEBUG with what came out of each, so that I can check the oldest-first draw and the inherited dating by hand.

## Implementation Decisions

### A module of its own

A new **Gifting** module owns this feature end to end: the rule, the refusals and their wording, the
record of what was given, and the read of a customer's gifts. Its dependencies are the ones
`RewardsService` already has — Accounts (does this customer exist), Points (move the points), the
clock (when did this happen), and a repository of its own — and it is deliberately built in that
service's image, because a claim and a gift are the same shape of act: check a customer, take points
off them, stamp the moment, write a row, hand back what happened.

Points is the wrong home: the rule is about two people, and the ledger has no opinion about people.
Accounts is the wrong home: it knows people and nothing about points. Rewards is the wrong home for
the obvious reason that no reward is involved.

### The rule

- A gift names a **recipient** by the contact details they bank under — trimmed, matched
  case-insensitively, the same lookup sign-in performs — and a number of **points**.
- Points are whole and positive. Anything else is a refusal, including a fractional figure: the
  request carries the number as the customer typed it and Gifting rules on it, so `2.5` and `abc`
  come back as refusals in words rather than being coerced into something plausible.
- A gift to yourself is refused. It would be a no-op, and a no-op that reports success is worse than
  a refusal that explains itself.
- A gift larger than the sender's balance is refused, and the refusal quotes the balance, in the
  wording a reward claim already uses for the same shortfall.
- **Nothing else is refused.** No cap on the size of one gift, no daily total, no cooldown between
  gifts, no minimum, no limit on how many people one customer may give to. This is a stated absence,
  not an oversight: the four refusals above are the whole of the rule. Should a limit ever be wanted,
  it belongs in this module beside them, and the gift record holds everything a velocity rule would
  need to be written against after the fact.

### What actually moves

Points are moved as **slices of the sender's batches**, oldest first — the same order and the same
draw a reward claim makes — and each slice arrives at the recipient as a batch of its own **dated at
the moment the original batch was earned**.

The alternative was the obvious one: spend the points off the sender and credit the recipient a
single fresh batch dated now. It was rejected because it makes gifting an expiry-laundering machine.
With no limit on frequency, two customers passing the same points back and forth would refresh the
twelve-month clock on every hop and keep a balance alive indefinitely — the twelve-month rule would
hold for everybody who never gifted and be void for everybody who did. Inheriting the date closes
that by construction rather than by another rule, and it is why the move is sliced: a gift drawn
from three batches of different ages must arrive as three batches of different ages, or the
inheritance has nothing to inherit.

Two consequences, both deliberate and both stated in the log:

- Giving away points whose anniversary passed earlier today hands over points that night's expiry
  sweep will take. Both rules are holding at once; neither is misbehaving.
- A recipient's pot can contain points older than any deposit they ever made. That is the honest
  description of having been given something second-hand.

Points are conserved: the sender's balance falls by exactly the gift and the recipient's rises by
exactly the gift, with nothing created, skimmed or destroyed. This is the single most valuable
invariant to assert.

### Points gains one way in

`PointsService` gains one operation: move a stated number of points from one customer to another,
oldest first, against a stated source reference, returning what it moved as slices — or nothing when
the sender comes up short, mirroring `spend`'s boolean rather than throwing, so the refusal's wording
stays with the module that owns the rule. It is the only new production behaviour in the ledger, and
it has to live there because the batch entity and its dates are the ledger's own.

`PointsReason` gains **`GIFT_RECEIVED`**, and it is pointedly **not** added to the set of reasons a
deposit can have earned under. Nothing about any per-deposit breakdown changes, no deposit response
grows a field, and every existing assertion about what a deposit earned still holds.

`sourceReferenceId` on a batch widens in meaning from "the deposit that earned this" to "the id of
the thing that caused this, in the module the reason names". For gifted batches it is the gift's id.
The column, the type and every existing query are unchanged; only the sentence describing it grows.

### What is written down

Gifting keeps one row per gift: the sender, the recipient, the points, and the moment it happened
off the application's clock. That row is the record shown to both parties and the audit trail, and
it outlives the points — a gift stays in both customers' lists after the points have been spent,
expired, or given onward.

Deriving the list from the points ledger was rejected for the reason the loyalty record was: it
would require Points to say out loud which batches came from where and who held them before, which
is precisely the question that module refuses. A counter on the customer was rejected because it
discards the counterparty and the date.

Nothing is written on the sender's side beyond the decrement their batches already take. The gift
row is the outgoing record; direction is a property of who is reading it, not of a second row.

### Order of writes

The recipient's batches carry the gift's id, so the gift row is saved first and the move follows —
which reads backwards from `RewardsService`, where the spend comes before the redemption is saved,
and is therefore worth saying out loud. The whole thing is one transaction, so a gift refused for
want of points leaves no row behind. The refusals that do not need the id — unknown sender, unknown
recipient, self-gift, unparseable or non-positive points — are all settled before anything is
written.

### Schema

A new table for gifts and a new value in an existing enumerated column, both produced by the entity
model the way every table in this application is. Nothing existing changes shape, so there is
nothing to backfill and no start-up fixer: unlike `PointsOnStartUp`, this release does not have to
reinterpret rows written by an earlier one.

### API contract

- `POST /api/customers/{customerId}/gifts` — body names the recipient's contact details and the
  points as typed. **201** with the gift. Refusals: unknown sender or unknown recipient **404**;
  self-gift, non-positive or non-whole points, and insufficient balance **400**. Each carries its
  reason in `detail`, in the one error shape this application answers in.
- `GET /api/customers/{customerId}/gifts` — the gifts this customer sent and received, newest first,
  **404** for a customer who does not exist, as every other per-customer read does.

One response shape for both, carrying the gift's id, a **direction** of `SENT` or `RECEIVED`, both
parties by id and name, the points, and the moment — the idiom the money-movement ledger already set
with its own direction. A gift just created is `SENT`.

Nothing is added to the account overview: no "points given away" total, no "points received" total.
The balance already includes received points, and the gift list is the record. This follows the
loyalty bonus's precedent of showing a thing where it happened rather than adding a figure to the
front page.

### The page

A screen of its own in the frontend's screen union, reached by a way-through button on the home
screen beside Money history, holding the form and the list. Rewards is a panel rather than a screen
because it is a catalogue; gifting has a form *and* a ledger, which is the History screen's shape.

- The recipient is chosen from the people who bank here, with the signed-in customer excluded, so
  the one refusal a customer could stumble into is unreachable from the page. The list comes from
  the customers endpoint — the only way this frontend ever learns of another customer — and reuses
  the radio-card and avatar styling that already sits unused in the stylesheet. The request still
  carries the email address: the API contract stays the human-facing one and the picker is a
  convenience over it, not a different way in.
- The points figure is sent as the customer typed it, the way deposit and withdrawal amounts already
  are, so the backend remains the authority on what a valid gift is and the page shows its answer
  unchanged.
- Affordability greys the send button, computed on the page for the same reason a reward offer does
  it — a hint, never the ruling.
- The button is unavailable while a gift is in flight, in the shape the claim already uses, so one
  click cannot become two gifts.
- A gift that goes through is shown as confirmation, and refreshes both the account read and the
  gift list, exactly as a claim refreshes accounts and redemptions.
- A new icon, because the existing gift icon is already spoken for as the unknown-reward fallback.

### Logging

- **INFO**, one line per gift: the gift's id, both customers, the points and the moment. A balance
  that moved without a deposit or a claim is explainable from this line alone.
- **INFO** on the credit side is the ledger's existing `points credited` line, with
  `reason=GIFT_RECEIVED`, so a reviewer grepping that phrase still sees every way a pot has ever
  grown.
- **WARN** on every refusal, with the kind, the sender, the recipient as given, and the reason.
- **DEBUG**, one guarded line naming the slices the gift was drawn from — batch id, earned-at, taken,
  left in it — so the oldest-first draw and the inherited dating can be checked by hand. Guarded by
  `isDebugEnabled` and gathered into one line, because rendering a batch is work and the string is
  thrown away at INFO: the same reasoning `spend`'s drawn-on list and the withdrawal's drawn-down
  list carry, and the reasoning a previous review made blocking on exactly that shape of code.
- **DEBUG** on the inputs behind the decision: the sender's balance and the batches available when
  the gift was judged.

## Testing Decisions

A good test here states the rule from outside and would survive the rule being implemented another
way. It drives the whole application over HTTP as two customers, moves the clock through the endpoint
a trainer would use, runs sweeps by the name a trainer would type, and asserts on balances, gift
lists, what expires next, and refusals. It never reads the database, never names a table or a column,
and never asks Gifting a question the API does not expose — the gift record's shape is storage.

### The seam

One seam, and it already exists: **the HTTP API of a whole application on a throwaway database**
(`ApiIntegrationTest`), in its clock-moving variant (`AnApplicationWithAClockToMove`) for the cases
about inherited expiry. It already offers everything this feature needs, and the harness already
knows two seeded customers by name, their contact details, their points balances, what expires next
for them, and an id no customer has.

The only harness addition is one view record for a gift, beside the existing money-movement and
deposit views. No new seam is proposed and no test reaches below this one. A fresh application per
class for the clock-moving tests, as the streak, expiry and loyalty tests already do.

### Prior art

- `ClaimingRewardsApiTest` and `ClaimIsRefusedApiTest` are the models for taking points off a
  customer and for a refusal per kind.
- `PointsBelongToTheCustomerApiTest` is the model for asserting across two customers.
- `TheMoneyMovementLedgerApiTest` is the model for a one-list-with-direction read.
- `PointsExpireTwelveMonthsAfterTheyWereEarnedApiTest` is the model for the inherited-expiry cases:
  the rule in one test, both halves asserted, the sweep run twice.

### What gets tested

Named as sentences, in a package of their own:

1. A gift moves points from one customer to the other, and the two balances move by exactly the gift.
2. A gift is drawn from the sender's oldest points first.
3. A gift larger than any one batch is drawn from several, and each part keeps the age it was earned at.
4. Gifted points expire twelve months after they were earned, not twelve months after they were given.
5. Points given back and forth do not outlive their twelve months.
6. Gifted points appear in what the recipient is told expires next.
7. Gifted points can be spent on a reward, and are spent oldest-first alongside the recipient's own.
8. Received points can be given onward, and carry their original age when they go.
9. A gift is refused when the sender does not hold the points, and the refusal quotes their balance.
10. A gift is refused to an email address nobody banks under.
11. A gift to yourself is refused.
12. A gift of zero, of a negative number, and of a fraction are each refused.
13. A refused gift leaves both balances and both gift lists exactly as they were.
14. There is no limit on frequency, size or daily total: many gifts in a row, and one gift of the whole balance, all go through.
15. Both parties' lists show the gift, marked sent for one and received for the other, naming the other person.
16. A gift stays in both lists after its points have been spent and after they have expired.
17. A gift moves no money, secures no week, changes no streak, and does not appear in the ledger of money that moved.
18. A gift does not appear in any deposit's breakdown of what it earned.
19. A gift list is only read for a customer who exists.

Frontend verification is `npm run typecheck` plus driving the page in the lab, since this repository
has no frontend test tooling and every rule is stated by a backend API test.

## Out of Scope

- **Accepting, declining, returning or reversing a gift.** A gift is final the moment it is made. An accept step, a pending state, a cancel window and a claw-back are each a feature of their own and each would need a state machine this one does not have.
- **Requesting points from somebody.** Gifting is one-directional and sender-initiated.
- **A message, note or occasion attached to a gift.** The record carries who, how much and when.
- **Telling the recipient anything.** No notification, no email, no badge. They see it in their balance and their gift list, exactly as the loyalty sweep pays and tells nobody.
- **Any limit, cap, cooldown, daily total or velocity rule.** Explicitly asked for and explicitly absent.
- **Gifting to anybody who is not a customer of this bank**, by email invitation or otherwise.
- **Gifting money.** This moves points; euros stay where they are.
- **Gifting a claimed reward or a voucher.** The catalogue and redemptions are untouched.
- **Splitting one gift across several recipients, or group gifting.** One gift, one recipient.
- **Scheduled, recurring or conditional gifts.**
- **A fee, a tax or a spread on a gift.** Points are conserved exactly.
- **Resetting expiry on gifted points**, and any change to the expiry rule itself. The inheritance is how this feature avoids touching that rule.
- **Any new figure on the account overview**, and any change to what a deposit says it earned.
- **An administrative or trainer view of everybody's gifts.** Each customer sees the gifts they were part of.
- **Backdating or rewriting anything already recorded.**

## Further Notes

- This repo has no `CONTEXT.md` and no `docs/adr/`, so nothing here contradicts a recorded decision. One decision in this spec is the kind that would earn an ADR if that directory existed: **gifted points inherit the date they were earned at**. It is the single largest judgement call here, it is what keeps the twelve-month rule honest under unlimited gifting, and it is the reason the move is sliced rather than a spend-and-credit. The alternative is a small change to one module if it is ever wanted, and the gift record makes either reading auditable after the fact.
- The absence of limits is a business decision the spec is carrying, not discovering. It is worth naming that unlimited gifting plus a fixed catalogue means points can be pooled onto one customer without bound — several customers can club together to buy one person a family cinema pack, which is a feature to most eyes and a collusion vector to a suspicious one. Nothing in this application is worth colluding for; a real one would want a velocity rule, and the record is shaped so one could be written later against history that already exists.
- `sourceReferenceId` becoming polymorphic-by-reason is a small honest smell. It is already the shape the column has (a bare `long` with no foreign key, meaningful only alongside the reason), and this feature makes that explicit rather than introducing it. A ledger with more sources than deposits and gifts would want the reference typed.
- Concurrency is not reachable here in a way that matters: this application runs on SQLite with a single-writer pool of one, and a gift is one transaction that reads the sender's batches and writes them. A real deployment would want the balance check and the draw under the same lock, which one transaction over a real database gives; noted, not built.
- The gift list grows without bound and is read in full per customer, as the money-movement ledger already is. For a training application that is a handful of rows. A real one would page it.
