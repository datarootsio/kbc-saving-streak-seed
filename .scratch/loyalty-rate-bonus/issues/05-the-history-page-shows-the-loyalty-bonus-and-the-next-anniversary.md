# 05: The history page shows the loyalty bonus and the next anniversary

Status: needs-info

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

- [x] A deposit that has been paid a loyalty bonus shows it in its breakdown, beside the base points and the streak bonus.
- [x] The breakdown's parts still visibly add up to the total the deposit has earned.
- [x] A deposit shows when it next pays and what that anniversary is worth.
- [x] A deposit that has never been paid a loyalty bonus shows no loyalty figure rather than a zero.
- [x] A deposit that has been emptied shows no next anniversary.
- [ ] The page holds up at the narrow widths the history is already checked at, in light and dark.
- [x] The types the page reads the deposit through carry the three new fields, and the typecheck passes.

## Review feedback - attempt 1

Six of the seven criteria are met and I saw them work; one is not, and it is a layout defect this
branch introduces. Everything below was run against the orchestrator's application
(`.scratch/loyalty-rate-bonus/logs/05-...app.1r.backend.log`) on a throwaway database. My own
screenshots and browser log are under the prefix `05-...rev1.*` in that logs directory; the
implementer's `before.1.*`, `step1.*`, `step2.*`, `final*.`, `act.1.*` and `app.1/2/3.*` files are
untouched.

### The one thing that is wrong

**The deposits list overflows its own column between 700px and 800px wide, and the new anniversary
line is what makes it overflow.** `.ledger` is
`grid-template-columns: repeat(auto-fit, minmax(20rem, 1fr))`, so the two histories go **two-up** as
soon as the shell is wide enough — measured, that happens between 680px and 700px of viewport, where
each `.history` drops from 624px to 321px. The new fold is `@media (max-width: 36rem)` (576px), so it
does not reach that band at all: the page folds below 576px, is fine from 576px to 680px in one
column, and then breaks from 700px upwards until the columns grow back past the content.

Measured in Chromium, right edge of the widest `td` in the Deposits history minus the right edge of
that `.history` (positive = overruns its container), with the new line present and with
`.anniversary { display: none }` injected to stand in for the pre-branch cell:

```
acct2  680px histWidth= 624  overrun WITH= -24  WITHOUT= -24
acct2  700px histWidth= 321  overrun WITH= +41  WITHOUT= -21   <-- overflows
acct2  720px histWidth= 330  overrun WITH= +32  WITHOUT= -24   <-- overflows
acct2  740px histWidth= 339  overrun WITH= +23  WITHOUT= -24   <-- overflows
acct2  760px histWidth= 349  overrun WITH= +14  WITHOUT= -24   <-- overflows
acct2  768px histWidth= 352  overrun WITH= +10  WITHOUT= -24   <-- overflows  (iPad portrait)
acct2  780px histWidth= 358  overrun WITH=  +5  WITHOUT= -24   <-- overflows
acct2  800px histWidth= 367  overrun WITH=  -5  WITHOUT= -24
```

`WITHOUT` never overflows at any width; `WITH` overflows across the whole band. So this is not a
pre-existing squeeze that the branch merely inherits — hiding only the new `.anniversary` line makes
it go away.

What it looks like: the anniversary sentences run across the `border-left` that separates Deposits
from Withdrawals and sit in the neighbouring list's space. See
`05-...rev1.clip700.light.panel.png` and `05-...rev1.clip768.dark.panel.png` — "Nothing due on
5 januari 2031", "12 points / due on 5 januari 2031" and "20 points / due on 1 december 2030" all
cross the divider, in both themes. On an account that also has withdrawals the same band clips the
Withdrawals IBANs at the panel edge, because `.account` is `overflow: hidden`
(`05-...rev1.band700.light.panel.png`) — that half is pre-existing, but it is the same squeeze and
the new line is now inside it.

To reproduce from scratch: seed one savings account with a deposit big enough to have been paid a
bonus, a deposit under ten euros, and a fresh deposit (see "How I drove it" below), then open the
account page at a 700px-wide viewport and look at the rule between the two lists.

Two things in the new markup make the runs unbreakable and are the mechanism behind the numbers
above. Both are worth fixing whatever you do about the breakpoint, and both diverge from the
`.expiring` pattern this code deliberately mirrors:

- `App.tsx`, `WhatItEarned`: the separator is `{' + '}` **inside** `<span className="breakdown-loyalty">`,
  which is `white-space: nowrap`. JSX strips the newline-whitespace between `</span>` and the
  `{deposit.loyaltyBonusPoints > 0 && …}` expression, so that leading space is the only separator
  between the rate and the loyalty — and a space inside a `nowrap` element is not a break
  opportunity. "at 1,10× + 15 loyalty" is therefore one unbreakable run, which contradicts
  `.breakdown-rate`'s own comment about letting "at 1,30×" fall to its own line. Put the space
  outside the span and keep only `+ 15 loyalty` inside it.
- `App.tsx`, `NextAnniversary`, the worth-nothing branch: the whole sentence is inside
  `<span className="anniversary-when">Nothing due on {asADay(on)}</span>`, so "Nothing" is inside the
  nowrap too. The paid branch correctly splits `.anniversary-count` from `.anniversary-when` and can
  break after the figure; `ExpiringNext` does the same. As written, the state that is meant to say
  *less* is the widest unbreakable string in the table and is the largest single contributor to the
  overflow above. `Nothing <span className="anniversary-when">due on {asADay(on)}</span>`.

Note for whoever picks this up: the implementer's width set was
`{1280, 600, 570, 480, 360, 320}`, and their "0 widths where `scrollWidth != clientWidth`" is true
of that set — it simply has no width in the 700–800px two-up band, which is the only band where the
problem exists. Please check the band explicitly next time, and note that `.account`'s
`overflow: hidden` means the panel's own `scrollWidth` is not a reliable detector: compare each
`td`'s `getBoundingClientRect().right` against its `.history`'s instead.

### What I verified as working, so you do not have to redo it

- **Loyalty in the breakdown, and the parts adding up.** A €100 deposit paid two anniversaries reads
  `100 base + 10 bonus at 1,10× + 15 loyalty` under a badge of `125`, and 100+10+15 = 125. A €200
  deposit reads `200 base + 0 bonus at 1,00× + 20 loyalty` under `220`. Read off
  `05-...rev1.wide.light.panel.png` and `05-...rev1.afterwithdrawal.light.panel.png` after waiting
  four seconds for the load animation.
- **The anniversary, and the wording surviving a past date.** `6 points due on 28 oktober 2028` and
  `5 points due on 16 september 2028` rendered while the application clock stood at
  `2028-12-01T14:19:58Z`, i.e. both dates already gone and both bonuses genuinely owed. The backend
  log for that same page load says
  `when each deposit in an account next pays savingsAccountId=1 asAt=2028-12-01T14:19:58.402427Z depositsStillHoldingMoney=3 worthNothingOnTheirNextAnniversary=1 depositsOwedAnAnniversaryTheSweepHasNotPaid=2 nextAnniversariesWorthAltogether=11`.
  The "due", never "next" decision is the right one and it holds: nothing on the page reads as a
  contradiction on either side of the date, and `App.tsx` still never calls `new Date()` for today.
- **The promise is honest.** Running `payLoyaltyBonuses` immediately afterwards paid exactly what the
  page had promised — `loyalty bonuses paid asAt=2028-12-01T14:21:04.791719Z landedBefore=2027-12-03T14:21:04.791719Z depositsConsidered=3 anniversariesPaid=2 points=11`
  (5 + 6) — and the rows became `125 = 100 + 10 + 15` and `70 = 64 + 0 + 6`. A second run paid
  nothing (`anniversariesPaid=0 points=0`).
- **No loyalty figure rather than a zero.** A €9 deposit and a freshly made €64 deposit both render
  `… base + 0 bonus at 1,00×` with no loyalty part at all.
- **The three states are correctly distinguished**, and drawing them differently was the right call.
  `Nothing due on 23 september 2028` in grey for the €9 deposit, the warm promise for a deposit worth
  something, and no line at all for an emptied deposit.
- **An emptied deposit loses its promise, live.** Withdrawing €60 through the page emptied two
  deposits; both anniversary lines disappeared on the reload while their `+ 15 loyalty` and their
  totals stood (`05-...rev1.afterwithdrawal.light.panel.png`). The withdrawal figure falling is
  visible too: the same deposit's promise went 10 → 5 when €50 of it was withdrawn, and
  `withdrawal drew the oldest deposits down first … [depositId=1 took=100.00 leftInIt=0.00] [depositId=2 took=50.00 leftInIt=50.00]`
  in the log says why.
- **Folding below 576px is right and works.** The fold hands over exactly at 36rem — headers present
  and columns intact at 580px, folded into `'when amount' / 'detail detail'` blocks at 575px — and at
  360px in both themes every row reads as sentences with `document.scrollWidth == clientWidth`. The
  Money history page is correctly outside `.ledger` and unaffected.
- **Folding the Withdrawals table too was the right call, not scope creep.** The two tables share
  `className="deposits"`, so the fold cannot reach one without the other, and the implementer's own
  baseline `before.1.phone.light.a1.png` shows the IBAN cell already spilling out of the panel at
  360px before this branch. Keep it.
- **Empty state.** An account with no deposits shows `No deposits yet.` and no rule paragraph
  (`p.explanation.rule` count 0).
- **Checks.** `cd backend && ./mvnw test` → `Tests run: 215, Failures: 0, Errors: 0, Skipped: 0`,
  `BUILD SUCCESS`. `cd frontend && npx tsc --noEmit` → exit 0 (Node 24.16.0).
- **Console and logs are clean.** 0 `pageerror`, 0 `console:error`, 0 `console:warning` across 15
  page loads. The 50 `requestfailed … net::ERR_ABORTED` lines are StrictMode's double mount aborting
  its own fetches and appear identically in the pre-branch baseline `before.1.browser.log`. Backend:
  0 `WARN`, 0 `ERROR`, 0 `Completed 500`. Vite's only errors are ECONNREFUSED proxy lines from
  15:13:30, before the backend finished starting.
- **Logging.** No new backend code, so no new log lines were required, and every state the page draws
  is explainable from the existing `io.dataroots.savingstreak` DEBUG lines — including both meanings
  of "nothing": `worthNothingOnTheirNextAnniversary=1` for the €9 row and
  `depositsStillHoldingMoney=3` of 4 for the emptied one, plus
  `deposit passed over for a loyalty bonus depositId=3 … reason=a tenth of what it still holds rounds down to no points … wholeEuros=9 theLeastABonusIsPaidOn=10`.

### How I drove it

```
POST /api/savings-accounts/1/deposits  {"amount":"200.00","fromCurrentAccountId":1}
POST /api/dev/clock/advance            {"days":7}
POST /api/savings-accounts/1/deposits  {"amount":"100.00","fromCurrentAccountId":1}
POST /api/dev/clock/advance            {"days":7}
POST /api/savings-accounts/1/deposits  {"amount":"9.00","fromCurrentAccountId":1}
POST /api/dev/clock/advance            {"days":400}
POST /api/dev/jobs/payLoyaltyBonuses/run
POST /api/savings-accounts/1/withdrawals {"amount":"250.00","toCurrentAccountId":1}
POST /api/savings-accounts/1/deposits  {"amount":"64.00","fromCurrentAccountId":1}
POST /api/dev/clock/advance            {"days":400}          # past dates, unswept
POST /api/dev/jobs/payLoyaltyBonuses/run                     # then again, for idempotence
```

That leaves one account holding all five row states at once. Playwright (python3, sync API,
chromium), signed in as `anke.peeters@example.be`, `color_scheme` for the theme, four seconds of wait
after `networkidle` before reading any figure off a screenshot, at 320/360/480/575/580/600/620/640/
660/680/700/720/740/760/768/780/800/820/840/860/880/900/940/1280 px.
