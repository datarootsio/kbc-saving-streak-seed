# 05: The history page shows the loyalty bonus and the next anniversary

Status: done

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
- [x] The page holds up at the narrow widths the history is already checked at, in light and dark.
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

## Verified

Attempt 2's one job was the layout defect attempt 1 was sent back on. It is fixed, the six criteria
attempt 1's reviewer verified still hold, and I saw all seven work. **Decision: done.** The trade the
implementer flagged — the two histories stacking between ~700px and ~975px instead of sitting side by
side — I measured myself and accept; the reasoning is below.

Everything was run against the orchestrator's application
(`.scratch/loyalty-rate-bonus/logs/05-...app.2r.backend.log`, throwaway DB) at clock
`2028-12-01`. My evidence is under the prefixes `05-...rev2.*`, `05-...rev2b.*` and
`05-...checks.2r.log`. I overwrote nothing from attempt 1, attempt 2 or the first review.

### The layout defect is fixed, and I measured the band myself

Chromium, both `color_scheme`s, right edge of the widest `td`/`th`/`p`/`span` in each `.history`
minus that `.history`'s own right edge (the first review's method, because `.account` has
`overflow: hidden`), at 320/360/480/560/575/580/600/640/680/700/720/740/760/768/800/840/880/900/
920/940/960/980/1000/1024/1100/1280/1440 px:

```
[light]   700px ncols=1 doc=0 :: Deposits: w=642 over=-24 | Withdrawals: w=642 over=-24
[light]   768px ncols=1 doc=0 :: Deposits: w=705 over=-24 | Withdrawals: w=705 over=-24
[light]   960px ncols=1 doc=0 :: Deposits: w=894 over=-24 | Withdrawals: w=894 over=-24
[light]   980px ncols=2 doc=0 :: Deposits: w=457 over=-24 | Withdrawals: w=457 over=-24
[light]  1280px ncols=2 doc=0 :: Deposits: w=479 over=-24 | Withdrawals: w=479 over=-24
```

**Zero positive overruns at any width in either theme**, `-24` everywhere (exactly `.history`'s own
1.5rem padding, i.e. the widest cell stops precisely at the content box), and
`document.scrollWidth == clientWidth` at every width. The 700–800px band attempt 1 was sent back on
is gone. Re-measured a second time with a fuller six-row list (`rev2b.*`) — same numbers.

### The stacking band, judged with my own eyes

Measured, not taken on trust: `ncols=1` from 320px to **960px**, `ncols=2` from **980px**. So the
implementer's "roughly 700px to 960px" is accurate, and iPad portrait (768px) does now stack while
iPad landscape (1024px) is two-up. **I accept the trade**, for three reasons I checked rather than
assumed:

- What it replaced was not a working two-up layout. In that band each `.history` was 321px, and
  attempt 1 measured *both* lists overrunning it — the deposits list because of the new anniversary
  line, the withdrawals IBAN "since it was written". Trading a broken two-up for a working one-up is
  a straight gain.
- Stacking is the layout the page already had at every width below 700px, so the band is an
  extension of an existing arrangement rather than a new one.
- I read the screenshots. `rev2.w768.light.ledger.png` and `rev2.w900.light.ledger.png` show a
  full-width Deposits table with roomy columns, headers intact, the rule paragraph wrapping cleanly,
  and Withdrawals below it with the IBAN complete. Nothing is cramped and nothing is clipped.

### The seven criteria

`rev2b.w1280.light.ledger.png` is the one frame that carries all five row states at once, at desktop
width, two-up, with nothing crossing the divider:

| row | badge | breakdown | anniversary |
|---|---|---|---|
| € 5,00 | `+ 5` | `5 base + 0 bonus at 1,00×` | `Nothing due on 1 december 2029` **in grey** |
| € 15,00 | `+ 15` | `15 base + 0 bonus at 1,00×` | `1 point due on 1 december 2029` (singular) |
| € 64,00 | `+ 70` | `64 base + 0 bonus at 1,00× + 6 loyalty` | `6 points due on 28 oktober 2029` |
| € 9,00 (emptied) | `+ 9` | `9 base + 0 bonus at 1,10×` | none at all |
| € 100,00 (emptied) | `+ 120` | `100 base + 10 bonus at 1,10× + 10 loyalty` | none at all |
| € 200,00 (emptied) | `+ 220` | `200 base + 0 bonus at 1,00× + 20 loyalty` | none at all |

- **Loyalty in the breakdown, and the parts adding up.** 64+0+6 = 70, 100+10+10 = 120,
  200+0+20 = 220, read off the rendered DOM after waiting four seconds for the load animation.
- **When it next pays and what it is worth.** Present on every deposit still holding money, and the
  singular is handled: a €15 deposit reads `1 point due on 1 december 2029`, not "1 points".
- **No loyalty figure rather than a zero.** The €5, €15, €9 and €64 (pre-sweep) rows have no
  `.breakdown-loyalty` element in the DOM at all — not a `+ 0 loyalty`.
- **An emptied deposit shows no next anniversary, live.** Withdrawing €60 through the page's own form
  emptied the €50 remaining in deposit 2 and the whole of deposit 3, and both anniversary lines
  disappeared on the reload while their `+ 10 loyalty` and their badge `120` stood. The two "nothing"
  states are genuinely distinguished: the €9 row went from grey `Nothing due on 23 september 2029`
  (still holds money, worth nothing) to **no line at all** (emptied) in front of me.
- **Both themes**, every width above, plus dark screenshots at 320/575/768/1024.
- **The types and the typecheck.** `api.ts` `RecordedDeposit` carries `loyaltyBonusPoints`,
  `nextAnniversaryOn: string | null` and `nextAnniversaryPoints: number | null`;
  `npm run typecheck` and `npx tsc --noEmit` both exit 0 on Node 24.16.0.

### The promise is honest

The page promised deposit 4 `6 points due on 28 oktober 2028`. Running `payLoyaltyBonuses`
immediately afterwards paid exactly that and no more — balance 428 → 434 — and the row became
`+ 70` / `64 base + 0 bonus at 1,00× + 6 loyalty` with the promise rolled on to
`6 points due on 28 oktober 2029`. The €15 deposit's future promise correctly paid nothing. A second
run paid nothing at all.

### The two unbreakable runs are really fixed, measured not just read

The first review's two string defects were the mechanism behind the overflow, and both now give a
real break opportunity. Measured by setting `width: min-content` against `max-content` on the
rendered elements:

```
anniversary.none:  "Nothing due on 1 december 2029"  minContent=150 maxContent=201 breakable=true
anniversary paid:  "1 point due on 1 december 2029"  minContent=150 maxContent=196 breakable=true
```

The worth-nothing state is no longer the widest unbreakable string in the table — it is now exactly
as narrow as the paid one, both bottoming out at the 150px of `due on <date>` that is deliberately
kept whole. And the rate/loyalty separator works in the live page: at 1024px two-up,
`.breakdown-rate` sits at `top=801` and `.breakdown-loyalty` at `top=890`, i.e. **on different
lines** — visible in `rev2.w1024.dark.ledger.png`, where `+ 10 loyalty` has fallen to its own line
with the date still whole.

### Backend log

The frontend change added no backend code, so no new log lines were required, and every state the
page drew is explainable from the existing `io.dataroots.savingstreak` DEBUG lines. 180 lines from
that package, **0 WARN, 0 ERROR, 0 `Completed 500`**. What I read:

- `when each deposit in an account next pays savingsAccountId=1 asAt=2027-10-28T13:53:39.096892Z depositsStillHoldingMoney=3 worthNothingOnTheirNextAnniversary=1 depositsOwedAnAnniversaryTheSweepHasNotPaid=0 nextAnniversariesWorthAltogether=11`
  — the page load showing the grey row plus the two warm ones, 6 + 5 = 11.
- `loyalty bonuses paid asAt=2028-12-01T15:00:00.628596Z landedBefore=2027-12-03T15:00:00.628596Z depositsConsidered=1 anniversariesPaid=1 points=6`
  — the sweep paying exactly what the page had promised.
- `deposit passed over for a loyalty bonus depositId=4 ... reason=this anniversary has already been paid ... ordinal=1`
  then `anniversariesPaid=0 points=0` — the second run, with its reason.
- `deposit passed over for a loyalty bonus depositId=3 ... reason=a tenth of what it still holds rounds down to no points ... wholeEuros=9 theLeastABonusIsPaidOn=10`
  — why the grey row says what it says.
- `withdrawal drew the oldest deposits down first ... [depositId=2 took=50.00 leftInIt=0.00] [depositId=3 took=9.00 leftInIt=0.00] [depositId=4 took=1.00 leftInIt=63.00]`
  — naming exactly the two deposits whose anniversary lines vanished, and why.

### Console and checks

0 `pageerror`, 0 `console:error`, 0 `console:warning` across every page load I drove, in both
themes (`rev2.browser.log`, `rev2.act.browser.log`). The 39 `requestfailed … net::ERR_ABORTED` lines
are StrictMode's double mount aborting its own fetches, the same pattern the first review found in
the pre-branch baseline. Vite's only errors are the ECONNREFUSED proxy lines from 15:49:58, before
the backend had finished starting.

`cd frontend && npm run typecheck` → exit 0. `cd backend && ./mvnw test` → **215 tests, 0 failures,
0 errors, BUILD SUCCESS**.

**One thing for the next person:** my *first* `./mvnw test` run failed with 1 failure and 2 errors in
`ADepositRecordedBeforeRatesWereApiTest`, all of them
`UnknownContentTypeException … content type [application/octet-stream]` and one
`400 BAD_REQUEST … Client sent an HTTP request to an HTTPS server.` That is the test's random port
colliding with something else on the machine, not this branch: the class re-ran green on its own and
the full suite re-ran green immediately after. If you see it, re-run before believing it.

### Two follow-ups, neither blocking this branch

Found by a code review over the range; both are pre-existing looseness this branch inherits or
widens rather than defects it introduces, so I have not held the ticket for them:

- `index.css:1397` — `.ledger .history + .history { border-left: … }` is not guarded by a media
  query, so when the grid is one column the stacked Withdrawals block still draws a faint 1px rule
  down its left edge with nothing to its left (see `rev2.border.w768.crop.png`). The rule is
  **byte-identical on ticket/04** (line 1260 there) and the diff touches no `border-left`, so this
  already happened at every width below 700px before this branch; raising the track minimum widens
  the band it shows in. At 0.14 alpha, 1px inside the panel's own border, it reads as a slight
  thickening of the panel edge rather than a stray line. Worth scoping to the two-up case one day.
- `App.tsx:1263` — `<th scope="col">Points earned</th>` now also scopes the promise, so a screen
  reader announces "6 points due on 28 oktober 2029" under a header saying those points were earned.
  Ticket 04 already put `at 1,10×` under the same header, so the looseness predates this branch;
  widening the header to "Points" would settle both.
