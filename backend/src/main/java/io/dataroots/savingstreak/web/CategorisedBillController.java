package io.dataroots.savingstreak.web;

import java.util.List;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.budgets.BudgetsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Which of this account's recurring bills sit in which of its spending categories.
 *
 * <p><strong>It shares the bills' path and belongs to Budgets, and that is deliberate.</strong> The
 * path is what reads best to whoever is calling it — a bill's category is under the bill, which is
 * where somebody looking for it will go — while the row behind it is this module's, keyed on the
 * bill's identifier. Putting the handler on {@code RecurringBillController} would make Accounts
 * depend on Budgets and close a cycle around the largest module in the application; putting the
 * link on {@code RecurringBill} would do the same and worse. The mappings here are
 * {@code /categories} and {@code /&#123;billId&#125;/category}, and neither collides with anything
 * that controller answers.
 *
 * <p><strong>This class vouches for the current account</strong> and for nothing else. Every handler
 * asks Accounts first and refuses in the words Accounts owns, which is the same order and the same
 * sentence the income, the bills, the categories, the goals and the saving rules use. Whether the
 * bill is on that account, whether it is still standing, whether the category is on that account and
 * whether it is still standing are rules, and every one of them belongs to Budgets, which refuses on
 * its own — a bill is Accounts' record, but what may be done to its label is this module's question.
 *
 * <p>It reads two modules' answers side by side and joins neither of them. The account's own screen
 * draws the bills it already has with a label from here; the budget screen draws the categories it
 * already has with the bills that sit in them. This is the web layer assembling what two modules
 * each answer on their own, exactly as {@code CustomerController} assembles the money movements
 * ledger out of three, and it is what lets neither of them know the other exists.
 *
 * <p><strong>There is nothing here about whose account it is.</strong> This application has no
 * authentication and every path names its account by identifier, so there is no signed-in customer
 * to compare one against. What is scoped is the bill and the category: both are only ever found
 * through the account in the path, so an identifier from somebody else's account answers as one that
 * is not there. The page makes the other half of the check, before it reads anything, against the
 * accounts the signed-in customer actually holds.
 */
@RestController
@RequestMapping("/api/current-accounts/{currentAccountId}/bills")
class CategorisedBillController {

    private static final Logger log = LoggerFactory.getLogger(CategorisedBillController.class);

    private final AccountsService accounts;

    private final BudgetsService budgets;

    CategorisedBillController(AccountsService accounts, BudgetsService budgets) {
        this.accounts = accounts;
        this.budgets = budgets;
    }

    /**
     * Where each of this account's bills is filed, for the bills that are filed anywhere at all.
     *
     * <p>One read for the whole account rather than one per bill, so that a page drawing twenty
     * bills with their labels makes two requests and not twenty-one. A bill in no category is
     * missing from the list, which is the honest shape: there is no row, and the page draws its
     * absence as "not in a category".
     *
     * <p>Ended bills are in it as well as standing ones. An ended bill keeps the category it was in
     * — the months it was taken for stay explained — and a list that left it out would be the page
     * promising a record it then had no way to read.
     */
    @GetMapping("/categories")
    List<CategorisedBillResponse> theCategoryEachBillIsIn(@PathVariable long currentAccountId) {
        vouchFor(currentAccountId, "bill categories");
        return budgets.theCategoryEachBillIsIn(currentAccountId).stream()
                .map(CategorisedBillResponse::of).toList();
    }

    /**
     * Puts one bill in one category, moving it from wherever it was, and answers with where it now
     * sits.
     *
     * <p>A PUT rather than a POST or a PATCH: a bill is in one category or in none, so this request
     * is the whole of that fact being put at an address that names it, and sending it twice is the
     * same request made twice. It is also what makes moving a bill and filing it for the first time
     * one thing rather than two — the customer is saying where the bill belongs either way.
     *
     * <p>The body is optional so that a request arriving with none at all is answered in this
     * application's own words rather than in Spring's, and a body naming no category is the same
     * mistake with the same sentence: taking a bill out of every category is the DELETE below, which
     * says what it means.
     */
    @PutMapping("/{billId}/category")
    CategorisedBillResponse putBillInACategory(@PathVariable long currentAccountId,
                                               @PathVariable long billId,
                                               @RequestBody(required = false)
                                               BillCategoryRequest request) {
        vouchFor(currentAccountId, "bill category");
        if (request == null || request.categoryId() == null) {
            String reason = "Say which category this bill belongs in, or take it out of the one it "
                    + "is in.";
            log.warn("bill category rejected currentAccountId={} billId={} reason={}",
                    currentAccountId, billId, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return CategorisedBillResponse.of(
                budgets.putBillInACategory(currentAccountId, billId, request.categoryId()));
    }

    /**
     * Takes a bill out of every category, and answers with the bill saying plainly that it is now in
     * none.
     *
     * <p>A DELETE of the bill's category rather than a PUT carrying nothing, because that is the
     * honest verb for what a customer is doing: the label stops existing. What is unmade is an
     * opinion about a payment and not the record of the payment — the bill, its declaration and
     * every date it fell due on are untouched — which is why this is a deletion where ending a
     * category is a closing.
     *
     * <p>Answered with the bill rather than with nothing, the same choice ending a bill and ending a
     * category are made under: a page that has just pressed the button gets back what it now has to
     * draw.
     */
    @DeleteMapping("/{billId}/category")
    CategorisedBillResponse takeBillOutOfEveryCategory(@PathVariable long currentAccountId,
                                                       @PathVariable long billId) {
        vouchFor(currentAccountId, "bill category");
        budgets.takeBillOutOfEveryCategory(currentAccountId, billId);
        return CategorisedBillResponse.inNoCategory(currentAccountId, billId);
    }

    /**
     * Asked before Budgets' own rules are, in the words Accounts owns, so that every module
     * answering for a current account says its absence the same way — and so that an account nobody
     * has heard of is refused rather than answered with the empty list of an account whose bills are
     * simply filed nowhere. Logged as well as answered, because a refusal decided here would
     * otherwise leave no line in the application's log at all.
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
