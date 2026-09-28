package io.dataroots.savingstreak.web;

import java.math.BigDecimal;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.budgets.BudgetsService;
import io.dataroots.savingstreak.budgets.RolloverRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * What one spending category is allowed to cost each month, declared and stopped.
 *
 * <p>A controller of its own rather than two more handlers on {@code SpendingCategoryController},
 * for the reason {@code CategorisedBillController} is one although it shares the bills' prefix: a
 * budget is a different concept from the word it is a limit on. A category is a name that can be
 * renamed and ended; a budget is a figure that is superseded and never mutated, kept in rows of its
 * own so that the months it governed stay readable. One class about both would be a class two
 * readers open for two different reasons.
 *
 * <p><strong>A PUT for declaring and a DELETE for stopping, and no POST.</strong> A budget is the
 * whole of one fact about one category, so putting it at an address that names the category is
 * exactly what the customer is doing — and there is one address whether or not a figure is already
 * there, because "from now on, this much" is the same sentence either way. What happens underneath
 * is a supersession rather than an overwrite, which is the backend's business and not the caller's.
 *
 * <p><strong>This class vouches for the current account.</strong> Both handlers ask Accounts first
 * and refuse in the words Accounts owns, the same order and the same sentence the income, the bills,
 * the goals, the saving rules, the categories and the spends use. It is why {@code BudgetRefused}
 * has no kind for a missing account.
 *
 * <p>Beyond that it reads the request and judges nothing. A body that did not arrive, a figure
 * whose characters are not a number at all, and a rollover rule naming something this application
 * has never heard of are facts about the request and are answered here; whether the number describes
 * a budget this application will keep, and whether that category can carry one, are rules and belong
 * to Budgets, which refuses on its own.
 */
@RestController
@RequestMapping("/api/current-accounts/{currentAccountId}/categories/{categoryId}/budget")
class MonthlyBudgetController {

    private static final Logger log = LoggerFactory.getLogger(MonthlyBudgetController.class);

    private final AccountsService accounts;

    private final BudgetsService budgets;

    MonthlyBudgetController(AccountsService accounts, BudgetsService budgets) {
        this.accounts = accounts;
        this.budgets = budgets;
    }

    /**
     * Says what the category is allowed to cost each month, superseding whatever stood before, and
     * answers with the figure that now stands.
     *
     * <p>The rule comes in beside the figure and is superseded with it, because they are one
     * declaration: "from now on, this much, and this is what happens to what is left of it". A
     * customer who sends no rule is not corrected — the default is applied, and it is the one that
     * surprises nobody.
     *
     * <p>200 rather than 201, although a row is written. What the customer created is not a new
     * thing at a new address — it is the same category's budget, at the address that has always
     * named it — and an identifier they would have to remember is exactly what a PUT exists to spare
     * them. The supersession is how the history is kept, not something the caller addresses.
     */
    @PutMapping
    MonthlyBudgetResponse declareABudget(@PathVariable long currentAccountId,
                                         @PathVariable long categoryId,
                                         @RequestBody(required = false) MonthlyBudgetRequest request) {
        vouchFor(currentAccountId, "budget");
        if (request == null) {
            String reason = "Say what this category is allowed to cost each month.";
            log.warn("budget rejected currentAccountId={} categoryId={} reason={}", currentAccountId,
                    categoryId, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return MonthlyBudgetResponse.of(budgets.declareABudget(currentAccountId, categoryId,
                amountIn(currentAccountId, categoryId, request.amount()),
                ruleIn(currentAccountId, categoryId, request.rollover())));
    }

    /**
     * Stops budgeting the category without ending the category, and answers with the figure as it
     * now reads rather than with nothing.
     *
     * <p>A DELETE, and it is the honest verb for what the customer is doing — this figure stops
     * being one they hold themselves to — while what the application keeps is the record of it, so
     * that the months it governed still quote it. The same choice ending a bill, a saving rule and a
     * category are all made under.
     *
     * <p>The category is untouched: it stays on the list, money can still be filed under it, and its
     * spending is still reported. What changes is that the read stops quoting a figure beside it.
     */
    @DeleteMapping
    MonthlyBudgetResponse stopBudgeting(@PathVariable long currentAccountId,
                                        @PathVariable long categoryId) {
        vouchFor(currentAccountId, "budget stop");
        return MonthlyBudgetResponse.of(budgets.stopBudgeting(currentAccountId, categoryId));
    }

    /**
     * The figure as a number, nothing at all when nobody sent one, or a refusal naming what could
     * not be read as one.
     *
     * <p>Absent stays absent rather than becoming a nought, because "you did not say what it is
     * allowed to cost" and "a budget of nothing is not a budget" are different sentences and Budgets
     * owns both. Named back to whoever sent it, because a person who typed a comma has to see the
     * comma to see the mistake. The same division of labour a bill's amount and a spend's are read
     * under.
     */
    private BigDecimal amountIn(long currentAccountId, long categoryId, String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(amount.trim());
        } catch (NumberFormatException notANumber) {
            String reason = "\"" + amount + "\" is not an amount of money. Write it in digits with a "
                    + "full stop, like 250.00.";
            log.warn("budget rejected currentAccountId={} categoryId={} amount={} reason={}",
                    currentAccountId, categoryId, amount, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }

    /**
     * The rollover rule as one of the three, nothing at all when nobody named one, or a refusal
     * naming the three there are.
     *
     * <p>Absent stays absent rather than becoming the default here, although the default is what
     * Budgets will make of it. "You did not say what should happen to the difference" and "there is
     * no such rule" are different sentences, and only the second is a mistake; the first is the
     * ordinary case, because a customer who has never thought about rollover has a rule all the same
     * and it is not this layer's business to name it for them.
     *
     * <p>Read here rather than by the deserialiser for the reason the amount is: a person who typed
     * a word this application does not know has to be told which words it does, and a framework
     * refusing an enum constant says nothing they can act on. The same division of labour the figure
     * is read under.
     */
    private RolloverRule ruleIn(long currentAccountId, long categoryId, String rollover) {
        if (rollover == null || rollover.isBlank()) {
            return null;
        }
        return RolloverRule.named(rollover).orElseThrow(() -> {
            String reason = "\"" + rollover + "\" is not one of the ways a budget can roll over. "
                    + "Say one of: " + RolloverRule.theOnesThereAre() + ".";
            log.warn("budget rejected currentAccountId={} categoryId={} rollover={} reason={}",
                    currentAccountId, categoryId, rollover, reason);
            return new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        });
    }

    /**
     * Asked before Budgets' own rules are, in the words Accounts owns, so that every module
     * answering for a current account says its absence the same way. Logged as well as answered,
     * because a refusal decided here would otherwise leave no line in the application's log at all.
     */
    private void vouchFor(long currentAccountId, String whatWasAsked) {
        if (!accounts.currentAccountExists(currentAccountId)) {
            String reason = AccountsService.noSuchCurrentAccount(currentAccountId);
            log.warn("{} rejected currentAccountId={} reason={}", whatWasAsked, currentAccountId,
                    reason);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
        }
    }
}
