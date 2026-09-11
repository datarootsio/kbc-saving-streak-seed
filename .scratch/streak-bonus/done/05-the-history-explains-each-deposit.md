# 05: The deposit history explains what each deposit earned and why

**What to build:** A customer looking back over their deposits can see, for each one, what it earned
as base points, what it earned as streak bonus, and the multiplier it was paid at. A past deposit
then explains itself without the customer having to reconstruct which week it fell in or what their
streak was at the time — and it keeps explaining itself correctly after the ladder changes, because
the figures come from what was recorded at the time rather than from a fresh derivation.

This is the reason the ledger records the multiplier applied rather than recomputing it, made
visible. Without it, a customer sees 9 points against a €7 deposit and has no way to check the
arithmetic.

**Blocked by:** 04 (a deposit is paid at the streak's multiplier and credits a streak bonus).

Status: done

- [x] Each entry in a savings account's deposit history reports its base points, its streak bonus points, the multiplier it was paid at, and the total.
- [x] Those figures match what the deposit response said at the time the deposit was made.
- [x] A deposit that earned no bonus reports a bonus of zero and its own multiplier, rather than omitting either.
- [x] Deposits made before this feature existed report their base points, a bonus of zero and a multiplier of 1.00×, which is what they were in fact paid.
- [x] The history rows on the savings account page show the base points, the bonus and the multiplier, in the layout the page already uses for a deposit.
- [x] The history is still ordered as it was, and every other figure on the page is unchanged.
- [x] Advancing or rewinding the development clock does not change what a past deposit reports having earned.

## Verified

Reviewed as one body of work across all three commits on this branch — `5c03c47` (the stalled
session's uncommitted work, committed unverified), `a93c984` (the stylesheet the page needed) and
`ddd304a` (the ticket move) — held to the same standard throughout. Most of the *backend* this ticket
describes already landed in ticket 04 (`RecordedDeposit`, `DepositResponse`, `asRecorded`,
`rateItWasPaidAt`, `PointsService.pointsEarnedBy` asking for both reasons); what this branch adds is
the page's breakdown line and its CSS, the `RecordedDeposit` type field, one DEBUG line in
`DepositsService.depositsInto`, and three API test classes. The criteria are met at the tip of this
branch, which is what matters for merging it.

### Checks, run again here rather than taken from the log

- `cd backend && ./mvnw test` — **157 tests, 0 failures, BUILD SUCCESS**.
- `cd frontend && npm run typecheck` — clean, exit 0 (Node 24.16.0 from nvm; the system `node` is
  v16 and Vite needs 20.19+).

### The API, driven by hand on the throwaway database

Requests and responses are in `logs/05-the-history-explains-each-deposit.review.2.curl.log`.
Built a three-week run on savings account 1 from an empty history: €30 + €20 (week 1, secured by the
second, 1.00×), advance 7 days, €50 (1.10×) + €7.60, advance 7 days, €50 (1.20×) + €7.60 + €0.99.
Every entry in `GET /api/savings-accounts/1/deposits` came back with the same four figures the deposit
response had given, **including the two-place scale**: raw JSON quotes `"multiplierApplied":1.20` /
`1.10` / `1.00`, not `1.2`. (Pretty-printing the body through `python3 -m json.tool` collapses these
to `1.2` — that is the printer, not the API. Read the raw bytes.)

- `€7.60 at 1.10×` → `basePoints 7, streakBonusPoints 0` (7 × 1.10 = 7.7, floors back to 7): a **bonus
  of zero written out, with its own 1.10× rate**, not omitted.
- `€7.60 at 1.20×` → `7 base + 1 bonus = 8`.
- `€0.99 at 1.20×` → `0 base + 0 bonus = 0`, all three fields present.

**Deposits from before the scheme.** Nulled `multiplier_applied` on deposits 1 and 2 with `sqlite3`
against the running database and re-read the history: both reported `"multiplierApplied":1.00`, their
base points, and a bonus of 0 — indistinguishable from a deposit that recorded 1.00. The DEBUG line
told the two cases apart, which is the only place in the application that does:

    deposit history reported with what each deposit earned savingsAccountId=1 deposits=7
      atTheRateTheyWerePaidAt=5 atTheOrdinaryRateForLackOfOne=2

**The clock, both ways.**
- Forwards: `POST /api/dev/clock/advance {"days":28}` → 42 days moved. The account's derived figures
  moved (`currentStreakWeeks` 3 → 0, `currentMultiplier` 1.20 → 1.00, `bestStreakWeeks` stayed 3)
  while `GET .../deposits` came back **byte-identical** to the body captured before the advance.
- Backwards: the clock refuses to move back, so I rewound the only way the application allows —
  stopped it, `delete from clock_offset`, restarted it on the same file. `GET /api/dev/clock` went
  from `movedForwardByDays:42, now 2026-10-20` to `movedForwardByDays:0, now 2026-09-08`, leaving
  deposits dated up to six weeks in the clock's future; `ClockOnStartUp` logged
  `clock was never moved movedForwardByDays=0`. The derived figures moved again
  (`currentStreakWeeks` 0 → 1, `newSavingsThisWeek` 0.00 → 50.00) and the history was again
  **byte-identical**. This is the criterion that justifies recording the rate rather than deriving it,
  and it holds live, not only in the test.
- A withdrawal of €25 (allocated against deposit 1) also left the history byte-identical.

Refusals exercised: `GET /api/savings-accounts/999/deposits` → 404 *"There is no savings account
999."*; `POST` a deposit of `0` and of `-5` → 400 *"A deposit has to be an amount of more than zero,
and 0 is not."* The listing itself has no new refusal of its own to trigger.

### The page, driven with Playwright and actually looked at

Script, console log and screenshots are in `logs/` under
`05-the-history-explains-each-deposit.review.2.*`. Subscribed to `console`, `pageerror` and
`requestfailed` before navigating. **0 failures out of 40 assertions; 0 `pageerror`.**

- All seven rows carried a `.breakdown` line reading the API's own figures, character for character:
  `0 base + 0 bonus at 1,20×`, `7 base + 1 bonus at 1,20×`, `50 base + 10 bonus at 1,20×`,
  `7 base + 0 bonus at 1,10×`, `50 base + 5 bonus at 1,10×`, `20 base + 0 bonus at 1,00×`,
  `30 base + 0 bonus at 1,00×`. The last of those is the deposit whose rate is NULL in the database
  and it renders identically to the one that recorded 1.00.
- `base + bonus == pointsEarned` in every row, and the badge above shows that total.
- Order unchanged (newest first) and the columns are still `When / Amount / Points earned`.
- The breakdown is genuinely styled: `display: block`, `font-size: 11.52px`, `color: rgb(122,149,171)`,
  and geometrically below the points badge rather than beside it. At 400px and 320px it wraps to
  `7 base + 1 bonus` / `at 1,20×` with the rate kept whole, and
  `document.documentElement.scrollWidth - innerWidth` was 0 at 1280/900/700/400/320 in both themes.
- Read the screenshots: fully styled page, both themes, nothing blank or clipped. The account panel
  read `earning 1,00× per euro / no weeks in a row / best ever 3 weeks` (post-lapse) while the history
  rows still said 1,20× — the present and the past disagreeing, and both right, which is the feature.
- Deposited €7.60 through the page's own form: a new row appeared with no reload reading
  `7 base + 0 bonus at 1,00×`, matching the API. Submitting `0` showed the styled refusal *"A deposit
  has to be an amount of more than zero, and 0 is not."*
- The only `requestfailed` lines are `net::ERR_ABORTED` on the page's own initial fetches — the
  `AbortController` cleanup in `App.tsx`'s effects, present in every earlier ticket's browser log and
  untouched here. The one `console:error` is the browser noting the deliberate 400.

### Logging

`grep 'io.dataroots.savingstreak'` over `logs/05-the-history-explains-each-deposit.app.2.backend.log`
shows the whole flow: `deposit takes its moment from the application clock`, `this week's new savings
derived from the ledger … secured=true`, `streak of secured weeks derived from the ledger …
walkedBackThrough=[…] currentStreakWeeks=2 … multiplier=1.10`, `points to credit worked out from the
amount … paidAtTheRate=55 streakBonus=5`, then INFO `deposit accepted depositId=3 … multiplier=1.10
basePoints=50 streakBonusPoints=5 pointsEarned=55`. This ticket's own new line appeared on all 15
history reads, greppable and in the surrounding style. No `ERROR` and no unexpected stack trace in the
whole log.

### Notes for whoever merges — none of these is a criterion failure

1. **`depositsInto` walks the deposit list a second time even when DEBUG is off.** The null-multiplier
   count is computed unguarded, whereas `WeekAndStreakDerivation` wraps both of its DEBUG lines in
   `if (log.isDebugEnabled())` with a comment explaining that the line runs on every read. Nothing is
   rendered here, only an int counted, so the cost is negligible — but it is a small inconsistency
   with the convention next door, and the two passes over `made` could be one.
2. **`7 base + 2 bonus at 1,30×` can be misread as `(7 + 2) × 1,30`** rather than `7 × 1,30 = 9`.
   Wording, not arithmetic; the figures shown are correct.
3. **The history's `amount` comes back unscaled** (`50` where the deposit response says `50.00`,
   `7.6` where it says `7.60`). Pre-existing — `asRecorded` hands out the raw amount and only the
   multiplier is quoted to two places — and the page formats it as euros either way, so no figure a
   customer sees is wrong. "Every other figure unchanged" argued against touching it here.
4. **A refused deposit leaves no `io.dataroots.savingstreak` WARN.** `POST` a deposit of `0` or `-5`
   and only Spring's own DEBUG lines record it; the withdrawal paths do log `log.warn(... reason=...)`
   but `DepositsService.refuseUnlessAnAmountOfMoney` does not. Pre-existing on `ticket/04` and
   earlier, untouched by this branch (the web layer's diff here is empty), and spec.md says this
   feature adds no new refusal. Worth a line for whoever owns the deposit-refusal slice.
5. **The throwaway database I drove is left with deposit 1's `multiplier_applied` NULL and
   `clock_offset` empty**, and the application is running under a new PID because I restarted it to
   prove the rewind. Throwaway state; mentioned only so the next reader is not puzzled by it.
