# 06: Withdrawals are refused with a readable reason

**What to build:** Every remaining way a withdrawal cannot happen is refused the way a deposit that
cannot happen is refused: an answer in one shape, carrying the reason in words the person who caused
it can read, which the web page shows unchanged. What may and may not be done stays decided in one
place, so nothing downstream has to reword it.

**Blocked by:** 05 (Withdraw money back into a current account).

**Status:** done

- [x] Naming a savings account that does not exist is refused with a reason saying so
- [x] Naming a destination current account that does not exist is refused with a reason saying so
- [x] Naming two accounts held by different customers is refused with a reason saying so
- [x] An amount that is not an amount of money is refused with a reason saying so
- [x] An amount of zero or less is refused with a reason saying so
- [x] An amount quoted more finely than to the cent is refused, matching how a deposit treats one
- [x] Every refusal answers as a problem document carrying its reason in the detail field
- [x] Every refusal leaves both balances, every deposit and every points credit exactly where they were

## Verified

Reviewed on branch `ticket/06-withdrawals-are-refused-with-a-readable-reason`, diffed against
`ticket/05-withdraw-money-back-into-a-current-account`. Seven files, +437/-65: a new package-private
`AmountOfMoney` in the `deposits` package owning the zero/scale rule and `asMoney`, a new
`AccountsService.noSuchCurrentAccount(long)` beside the existing `noSuchSavingsAccount`, both
services rewired onto them, one controller log line, and a new
`withdrawalrefusals/WithdrawalIsRefusedApiTest` (9 tests). No frontend change.

**Checks, run by the reviewer.** `cd backend && ./mvnw test` — BUILD SUCCESS, 113 tests, 0 failures
(104 before this branch). `cd frontend && npm run typecheck` — exit 0 on Node 24.16.0.

**Exercised over HTTP** against the running app on a throwaway database, savings account 1 (Anke)
seeded with deposits of 10.00 and 15.50 (balance 25.50, 25 points). Every refusal came back
`Content-Type: application/problem+json` with the reason in `detail`:

- savings 999 → 404 `There is no savings account 999.`
- current 999 → 404 `There is no current account 999.`
- Anke's savings 1 → Bram's current 2 → 400 `A withdrawal can only return money to a current account held by the same customer.` (and the mirror, Bram's savings 3 → Anke's current 1, refused the same way)
- `25,00`, `abc`, `""`, `"   "` → 400 `"…" is not an amount of money. Write it in digits with a full stop, like 25.00.`
- `0.00` / `-5.00` → 400 `A withdrawal has to be an amount of more than zero, and … is not.`
- `10.001` → 400 `An amount of money has at most two decimal places, and 10.001 has 3.`
- `1000.00` → 400 `There is not enough in that savings account to move EUR 1000.00. It holds EUR 25.50.`
- `{}`, null amount, null destination → 400 `A withdrawal needs an amount and the current account it returns to.`

**Criterion 6 checked rather than assumed.** `POST …/withdrawals` and `POST …/deposits` with `10.001`
both answered `An amount of money has at most two decimal places, and 10.001 has 3.` — the same
sentence, character for character. `25,00` and current-account-999 are identical across both
directions too. The new test encodes this with `isEqualTo` between the two `detail` fields.

**Criterion 8 checked by diff, not by eye.** The six views (savings account 1's balances, its
deposits, its withdrawals, Anke's accounts, Bram's accounts, Anke's other savings account 2) were
captured before the refusals and re-fetched after all ten; `diff` reported every one byte-identical.
Money 25.50, points 25, both deposits still earning 15 and 10, withdrawals `[]`, Anke's current
2454.50, Bram's current 1150.

**Logging.** Every refusal triggered left a WARN from `io.dataroots.savingstreak` carrying the
sentence, not a summary of it — 23 WARN lines for the run, e.g.

```
i.d.s.deposits.WithdrawalsService : withdrawal rejected savingsAccountId=1 toCurrentAccountId=1 amount=1000.00 balance=25.50 reason=There is not enough in that savings account to move EUR 1000.00. It holds EUR 25.50.
i.d.s.web.SavingsAccountController : withdrawal rejected savingsAccountId=1 amount=25,00 reason="25,00" is not an amount of money. Write it in digits with a full stop, like 25.00.
```

Zero ERROR lines and zero `Completed 500` in the backend log for the whole session.

**Seen on the page.** Driven with Playwright as Anke. Each refusal renders in the `role="alert"`
box under the withdrawal form with the backend's sentence unchanged (screenshots read, page styled
and populated, not a blank frame). The browser log has no `pageerror`; the only console errors are
Chromium's own "Failed to load resource: 400" for the deliberate refusals. A real withdrawal of 5.50
straight afterwards still worked — balance 25,50 → 20,00, points unchanged at 25, one allocation
against deposit 1 — so the shared-rule refactor did not disturb the happy path.

**Noted, not blocking.** `DepositsService` still logs nothing when it refuses a deposit: five
deposit refusals over curl produced zero `DepositsService … rejected` lines. That predates this
branch and this ticket is about withdrawals, but CLAUDE.md asks for a WARN on every refusal and
somebody should pick it up.
