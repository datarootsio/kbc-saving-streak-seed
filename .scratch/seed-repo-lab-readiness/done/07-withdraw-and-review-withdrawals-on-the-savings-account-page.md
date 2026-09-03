# 07: Withdraw and review withdrawals on the savings account page

**What to build:** A customer withdraws money from the page rather than by calling an API, so the two
directions of the same movement sit in one place. The form names an amount and which current account
the money goes back to, the balance updates when it succeeds, and when it is refused the backend's own
reason is shown unchanged. Withdrawals appear in the account's history alongside its deposits, so
every movement on the account can be accounted for.

**Blocked by:** 05 (Withdraw money back into a current account), 06 (Withdrawals are refused with a readable reason).

**Status:** done

- [x] A withdrawal form sits beside the deposit form, naming an amount and a destination current account
- [x] A successful withdrawal updates the money balance shown on the page without a reload
- [x] A refused withdrawal shows the reason the backend gave, unchanged
- [x] The form is disabled while the request is in flight, the way the deposit form is
- [x] The amount is sent as the text that was typed, so the backend remains the only judge of what counts as an amount of money
- [x] Withdrawals appear in the account's history with their amount, destination and moment
- [x] The points balance shown does not change when money is withdrawn
- [x] The form carries labels and is reachable by keyboard the way the deposit form is

## Verified

Reviewed `ticket/06-withdrawals-are-refused-with-a-readable-reason..ticket/07-withdraw-and-review-withdrawals-on-the-savings-account-page`.
The ticket's own change is one commit, `29ed709` "Name a withdrawal's destination by its IBAN",
plus the ticket-file move `f0e12bb`; `c6e0742` is the orchestrator's `.gitignore` housekeeping and
was not reviewed. Seven of the eight criteria were already shipped by ticket 05's `3fa9c0b`; this
branch adds the IBAN naming. All eight were exercised against the running application before ticking.

**Checks.** `cd backend && ./mvnw test` — BUILD SUCCESS, 113 tests, 0 failures. `cd frontend && npm run
typecheck` — clean (Node v24.16.0 from `~/.nvm/versions/node/v24.16.0/bin`; the default `node -v` here
is v16.18.0 and Vite refuses it).

**Driven with Playwright** (chromium, sync API, `reduced_motion="reduce"`) against the backend on a
throwaway SQLite database and Vite on 5173, signed in as `anke.peeters@example.be`, savings account 1.
I added a second current account (`BE99REVIEWER00099`, id 99) to that throwaway database first, because
the demo data gives each customer only one, and a one-option select cannot show that the destination is
honoured. 21 scripted assertions, all passing:

- *Form beside the deposit form* — `.transfer-area` holds exactly `['deposit', 'deposit withdrawal']`,
  and the screenshot shows the two forms side by side under the two balances.
- *Balance without a reload* — deposited 10.00 and 30.00, withdrew 12.50: Saved `€ 40,00 → € 27,50`
  with no navigation. Later withdrew the whole remaining balance in one go: `€ 27,50 → € 0,00`.
- *Points untouched* — `40 points → 40 points` across the first withdrawal, and `41 points` still
  standing after the account was emptied to `€ 0,00`.
- *Refusal shown unchanged* — four refusals, each compared by string equality against the backend's own
  `detail` for the identical request: more than the balance, `" 3,5 "`, `0`, `-5.00`. All equal, all
  400, e.g. `There is not enough in that savings account to move EUR 9999.00. It holds EUR 27.50.`
  Each left what was typed in the field and both balances where they were.
- *Amount as typed* — intercepted the POST body: `{"amount":"12.50","toCurrentAccountId":99}`, and the
  backend logged `amount= 3,5 ` with the leading and trailing spaces and the comma intact.
- *In flight* — delayed the withdrawal POST inside the browser: `{withdrawDisabled: True, withdrawLabel:
  'Withdrawing…', withdrawSpinner: True}` with the deposit button untouched, against the deposit form's
  own measured shape `{disabled: True, label: 'Depositing…', spinner: True}`. Exact parity.
- *Labels and keyboard* — `[['Withdraw','withdrawalAmount','INPUT'], ['Return to','toCurrentAccount','SELECT']]`;
  Tab from the amount reaches the select, Tab again the Withdraw button, and Enter submitted. The whole
  withdrawal was made without the mouse.
- *History* — `['3/09/2026, 09:42', '€ 12,50', 'BE99REVIEWER00099']`: moment, amount, destination, newest
  first, surviving a reload. The IBAN is per row and follows the destination actually chosen — the two
  withdrawals sent to account 99 read `BE99REVIEWER00099` and the later one sent to account 1 reads
  `BE68539007547034`, matching `toCurrentAccountId=1` in the log.

**Backend log** (`io.dataroots.savingstreak` at DEBUG). Every accepted withdrawal left DEBUG for the
inputs, the clock and the allocation, then one INFO business event:
`withdrawal accepted withdrawalId=3 savingsAccountId=1 toCurrentAccountId=1 amount=27.50 withdrawnAt=2026-09-03T07:44:19.842Z`,
preceded by `withdrawal allocated ... depositsTouched=2 cents=2750` (oldest deposit first, reaching into
the next). Every refusal I triggered left a WARN carrying its reason, from `WithdrawalsService` for the
balance and zero-or-less cases and from `SavingsAccountController` for the non-money amount:
`withdrawal rejected savingsAccountId=1 toCurrentAccountId=1 amount=1.00 balance=0.00 reason=There is not enough in that savings account to move EUR 1.00. It holds EUR 0.00.`
No ERROR and no 500 anywhere in the log.

**Browser console** — no `pageerror` at all. The four `console:error` entries are the four 400s I
deliberately provoked; the `ERR_ABORTED` request failures are React StrictMode's double mount aborting
its own in-flight loads through the `AbortController` the page installs. Vite's log shows no transform
or build error during the run.

**Notes for whoever picks this up next, neither of them blocking:**

1. The `Deposits` doc comment (`frontend/src/App.tsx:825`) still claims "the amounts add up to the one".
   It no longer does once money can leave: on screen the Deposits table summed to 41,00 while Saved read
   € 0,00. The comment was made stale by tickets 02 and 05, not by this branch, and no criterion here
   covers it, but it is the kind of wrong comment a seed repo should not teach from.
2. The history names the destination by looking the id up in the *live* current-account list rather than
   by a name recorded with the withdrawal. Nothing in the application can close an account or change an
   IBAN today, and there is a fallback for an id it cannot name, so it is correct as it ships; if
   closing accounts ever arrives, `GET /withdrawals` should return the IBAN alongside the id.
