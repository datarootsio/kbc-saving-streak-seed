# 06: The gift page

Status: done

**Blocked by:** 01 (a customer can give points to another customer), 02 (a gift is refused in words
and changes nothing), 05 (each customer's list of the gifts they were part of).

**What to build:** A page where a customer picks somebody who banks here, says how many points to
give, and sends them — with the list of gifts they have been part of underneath.

A screen of its own rather than a panel on the home screen: gifting has a form *and* a ledger, which
is the money-history screen's shape, where the rewards catalogue is a panel because it is only a
catalogue. It joins the frontend's screen union and is reached by a way-through button on the home
screen beside Money history.

- The recipient is chosen from the people who bank here, **with the signed-in customer excluded**, so
  the one refusal a customer could stumble into is unreachable from the page. The customers endpoint
  is the only way this frontend ever learns of another customer; behind the sign-in gate nothing
  currently calls it. The stylesheet already carries a radio-card and avatar pattern that no
  component uses yet — reuse it rather than inventing a third way to present a person.
- The request still carries the recipient's email address. The picker is a convenience over the
  human-facing contract, not a different way in.
- The points figure goes over the wire **as the customer typed it**, the way deposit and withdrawal
  amounts already do, so the backend stays the authority on what a valid gift is and the page shows
  its answer unchanged through the existing refusal component. Affordability greys the send button —
  a hint computed on the page for the same reason a reward offer does it, never the ruling.
- The send button is unavailable while a gift is in flight, in the shape the reward claim already
  uses, so one click cannot become two gifts.
- A gift that goes through is shown as confirmation, and refreshes both the account read and the gift
  list — the pattern the claim already follows when it reloads accounts and redemptions together.
- A new icon: the existing gift icon is already spoken for as the unknown-reward fallback.

This repository has no frontend test tooling, so verification is `npm run typecheck` plus driving the
page in the lab as both demo customers — give points as one, sign in as the other, see them arrive.

- [x] A way-through button on the home screen opens the gift page, and its back button returns home.
- [x] The page lists the other people who bank here to choose from, and never offers the signed-in customer themselves.
- [x] Choosing someone and entering a number of points sends the gift and shows what went through.
- [x] The signed-in customer's points balance on screen falls by the gift without a reload.
- [x] The gift appears in the list on the page immediately after it is sent.
- [x] The list shows sent and received gifts together, newest first, naming the other person, the direction and the points.
- [x] A refused gift shows the backend's reason verbatim and nothing on the page changes.
- [x] Typing something that is not a whole number of points produces the backend's refusal in words rather than a silently coerced gift.
- [x] The send button is disabled while a gift is in flight and while no recipient or no points are chosen.
- [x] `npm run typecheck` passes, and the page has been driven end to end as both demo customers with a clean browser console.

## Review feedback - attempt 1

Nine of the ten criteria are genuinely met and I saw them work. The page is well made: it is
styled, it reuses the stylesheet's radio-card pattern, the balance falls without a reload, the list
interleaves both directions newest first, and the browser console is clean. One criterion is not
met, and it is a one-line fix.

### Blocking: the affordability hint takes a ruling the backend owns (criterion 8)

`frontend/src/App.tsx:1649-1654`

```ts
const asked = Number(howMany.trim())
const short =
  pointsToSpend !== null && howMany.trim() !== '' && Number.isFinite(asked)
    ? asked - pointsToSpend
    : 0
const beyondTheBalance = short > 0
```

`Number('2.5')` is finite, so a **fractional** figure is fed into the affordability hint. When that
fraction is larger than the balance, the send button is greyed and the request never leaves the
page — so the customer never sees the backend's refusal, and the button label renders a fractional
number of points.

What I expected: typing `2.5` produces the backend's words, "Points are whole, and 2.5 is not a
whole number.", whatever the balance is.

What I saw, driving the page as Anke with a balance of 0:

```
[points] 0 | points to give
[typed '2.5']  disabled=True  label='2,5 to go'
[typed '0.5']  disabled=True  label='0,5 to go'
[typed '1.25'] disabled=True  label='1,25 to go'
[typed 'abc']  disabled=False label='Give points'
```

To reproduce: sign in, open the gift page, choose the other customer, and type any fraction larger
than the balance on screen (`12.5` against a balance of 10 does it just as well as `2.5` against
0). The button greys instead of sending, and nothing from `io.dataroots.savingstreak` appears in
the backend log because no request is made.

Two things are wrong with that, and both are named in the documents this ticket was written from:

- The ticket says of affordability, "a hint computed on the page for the same reason a reward offer
  does it, **never the ruling**". Here it is the ruling: for `12.5` against 10 points, the page and
  not the backend decides the gift does not happen.
- The spec's user story 12 is "I want to be refused when I type something that is not a whole
  number of points, **so that half a point is never invented**". `label='0,5 to go'` invents half a
  point in the application's own UI. `points` (`App.tsx:40`) is a plain `nl-BE` NumberFormat, and
  this is the only place in the repository that can hand it a non-integer — `Offer`'s `short` is
  always whole.

The fix is to compute the hint only for figures that are whole numbers of points — the same class
of input the button is allowed to have an opinion about — and to leave every other figure pressable
so the backend answers it, which is what the rest of the page already does correctly for `abc`.

Please also re-drive criterion 8 with a fraction that is *over* the balance as well as one under
it, since the under-balance case (which is what attempt 1 exercised, at a balance of 120) passes
and hides this.

### Worth fixing while you are in there, none of them blocking

1. `App.tsx:1646-1648` — the comment says a fraction, a word, "an empty field once something has
   been typed into it" is "left to the backend, which is the only place that knows what a number of
   points is". The empty field is not left to the backend: `howMany.trim() === ''` is a disable
   clause at `App.tsx:1765`. The comment contradicts the code fifteen lines below it, and after the
   fix above the fraction half will be wrong too.
2. `App.tsx:1573-1582` — `GiftPage` takes the whole `Customer` but reads only `customer.id`. Every
   sibling screen (`MoneyHistory`, `Home`) takes `customerId: number`. Gratuitous divergence.
3. `App.tsx:1729-1740` — the radio carries `aria-label={`${who.name}, ${who.contactDetails}`}` while
   the drawn `.choice-name` / `.choice-note` spans are not `aria-hidden` (only `.avatar` is), so a
   screen reader hears the person twice.
4. `index.css:2185-2191` — `.give-line label` duplicates `.deposit label` (744-750) byte for byte;
   `.deposit label, .give-line label` would have done. (`.signin label` is already a third copy, so
   this is in keeping — noted only for completeness.)

### Things I checked that are *not* faults, so nobody re-litigates them

- `celebrated` is not cleared when a later gift is refused, so a stale confirmation can sit beside a
  fresh refusal. `Rewards.claim()` (`App.tsx:788-798`) has exactly the same shape, and the ticket
  told the author to copy it. Leave it, or change both.
- `loadGifts()` after a gift is fired unsignalled and unsequenced. `Banking`'s `onClaimed` calls
  `loadAccounts(); loadClaimed()` the same way. Established idiom.
- `accountsError` is not handed to the gifts screen, so a failed post-gift `loadAccounts()` would
  leave a stale headline figure. `MoneyHistory` has the identical gap. Consistent with the screen
  it was told to imitate.
- The way-through sits in the "Savings accounts" panel under Money history. The ticket says "beside
  Money history", so this meets the letter of it.

## Verified

Reviewed on attempt 2, branch `ticket/06-the-gift-page` against `agentic_engineered`. Only
`frontend/` changed between the two attempts (`e29f75e`); the code was read as a diff by hand
because the `/code-review` skill did not report in time.

**Checks, run again by the reviewer** (`logs/06-the-gift-page.review.2.checks.log`):
`cd backend && ./mvnw test` → `Tests run: 256, Failures: 0, Errors: 0`, BUILD SUCCESS.
`cd frontend && npm run typecheck` → clean.

**The attempt-1 blocker is fixed.** The hint now reads only `/^\d+$/`, so a fraction is never
ruled on by the page. Driven with Playwright against a throwaway database
(`logs/06-the-gift-page.app.2b.backend.log`, browser output in `…review.2.browser.log`):

- At a balance of **0**, with Bram chosen: `2.5`, `0.5`, `1.25`, `12.5`, `abc`, `-5`, `1e3` all
  report `disabled=False label='Give points'`. Sending each produced the backend's own words —
  `Points are whole, and 2.5 is not a whole number.`, `A gift is a whole number of points, and
  "abc" is not a number.`, `A gift has to be more than zero points, and -5 is not.` — with the
  field, the balance and the (empty) gift list unchanged.
- At a balance of **10**, the case the previous review asked for: `12.5`, `10.5`, `99.9` are all
  pressable, and `12.5` came back `Points are whole, and 12.5 is not a whole number.` while the
  balance stayed at 10 and the list stayed empty (`…review.2.anke-05-fraction-over-balance.png`).
- A *whole* number over the balance still greys, as the ticket wants a hint to:
  `[typed '99'] disabled=True label='89 to go'`. No fractional figure can reach that label any more.
- Probed by curl that the hint never disagrees with the backend: `05` is accepted as 5 and a
  21-digit figure is refused for want of points, which is what the grey button already says.

**The rest of the page, driven end to end as both demo customers** (screenshots read, all styled,
no blank frames):

- Home carries `Money history` and `Give points` side by side; `All accounts` returns home
  (`…review.2.final-anke-home.png`).
- Anke's picker offers only `Bram De Vos, bram.devos@example.be`; Bram's offers only Anke. The
  radio-card/avatar pattern from the stylesheet is what is drawn, and the radio's `aria-label` is
  the only thing announced (the drawn spans are `aria-hidden`).
- Gift of 4 to Bram: `[in flight] disabled=True label='Giving…'`, then the flash
  `4 points to Bram De Vos`, the headline falling 10 → 6 with no reload, and the row
  `To Bram De Vos | Sent | −4` appearing highlighted. A second gift of 1 landed above it and the
  balance fell to 5.
- Bram, signed in separately, saw `From Anke Peeters … +1` above `… +4`, gave 2 back, and both
  lists then interleave directions newest first (`…final-anke-giftpage.png`,
  `…final-bram-giftpage.png`). Narrow viewport stacks the chip and badge as the new CSS intends.
- Button disabled with nothing chosen, with nothing typed, and with both — checked in all three.

**Backend log** (`io.dataroots.savingstreak` at DEBUG) has the whole flow, WARN on every refusal
I triggered and INFO plus the DEBUG draw on every gift:

```
GiftingService : gift rejected senderCustomerId=1 recipientAsGiven=bram.devos@example.be kind=NOT_A_NUMBER_OF_POINTS reason=Points are whole, and 12.5 is not a whole number.
GiftingService : gift judged against the sender's pot senderCustomerId=1 recipientCustomerId=2 points=4 senderBalance=10
PointsService  : points credited customerId=2 sourceReferenceId=1 reason=GIFT_RECEIVED points=4 batches=1 oldestEarnedAt=2026-09-10T08:02:25.549Z
GiftingService : gift drawn from the sender's oldest points first giftId=1 … slices=1 drawnOn=[points=4 earnedAt=2026-09-10T08:02:25.549Z]
GiftingService : gift given giftId=1 senderCustomerId=1 recipientCustomerId=2 points=4 givenAt=2026-09-10T08:03:07.294Z
```

**Browser console:** zero `pageerror`. The only `console:error` lines are Chromium's
`Failed to load resource: … 400` for the seven refusals I deliberately triggered, and the
`ERR_ABORTED` `requestfailed` lines are StrictMode's double-invoked effects hitting the existing
`AbortController` cleanup — the same lines the pre-existing accounts and redemptions reads produce.

The four non-blocking notes from attempt 1 were all taken (`GiftPage` now takes `customerId:
number`, `.choice-name` is `aria-hidden`, the misleading comment is gone, `.give-line label` is
folded into `.deposit label`). Nothing new was found.
