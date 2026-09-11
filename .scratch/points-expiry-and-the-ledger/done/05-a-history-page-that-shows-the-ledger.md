# 05: A history page that shows the ledger

**What to build:** A page reachable from the customer overview that shows the money-movement ledger:
one chronological list, newest first, of every euro that went into or out of savings, with which way it
went, between which accounts, when, and what a deposit earned.

The way back out is the top bar's existing back affordance, which already returns from an open savings
account to the overview.

Nothing on the page is worked out by the page. Every figure is the backend's, which is what lets the
list be checked against the balances.

Blocked by: 04

Status: done

- [x] The overview has a way through to the history, and the top bar's back affordance returns from it.
- [x] The page lists every movement newest first, with direction, amount, both accounts and the moment.
- [x] A deposit's row says what it earned; a withdrawal's row does not pretend to have earned anything.
- [x] A customer who has moved nothing sees a sentence saying so rather than an empty frame.
- [x] A failed read shows the backend's own reason, in the styled refusal the rest of the app uses.
- [x] The page is styled with the stylesheet the rest of the app uses, works in both themes, and does not scroll sideways at 320px.

## Verified

Driven with Playwright in both themes at 1280, 900, 700, 400 and 320px. Script, console log and
screenshots under `logs/` (`drive.py`, `checks.log`, `history-*.png`). **31/31 assertions, 0
`pageerror`.**

- **The way in and the way back.** A dashed full-width row under the savings-account cards reads
  *"Money history / Every euro in and out, across all your savings, newest first"*; the top bar's
  existing "All accounts" affordance returns from it, as it does from an open account. Navigation is
  now a three-way union rather than a nullable account id, so "an account is open and so is the
  history" is not a state the component can reach.
- **Five rows, newest first**, columns `When / Movement / Amount / Points earned`, each row a
  direction chip over the two accounts in the order the money travelled
  (`BE68539007547034 → Savings account 1`). Every figure matches the API character for character.
- A deposit that earned nothing shows `0`; a withdrawal shows an em dash, because those are
  different statements. The two direction chips are visibly different colours
  (`rgba(0,151,219,.14)` and `rgba(0,54,101,.1)`) and neither is dressed as bad news.
- **No total and no running balance**, and no `tfoot` to put one in.
- **Fixed during review: the page opened on its least useful columns at a phone's width.** The table
  scrolled inside its frame, so at 320px a customer saw a date and half an IBAN with nothing on
  screen to say the amounts were off to the right. Below 36rem the rows are now a two-line grid —
  date and amount, then the movement and what it earned — and the drive asserts that every cell of
  every row is inside the viewport at all five widths, as well as that the page never scrolls
  sideways.
- A read that fails shows the backend's own reason in the styled `Refusal`; a customer who has moved
  nothing sees a sentence rather than an empty frame.
