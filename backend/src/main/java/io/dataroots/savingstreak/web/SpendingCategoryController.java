package io.dataroots.savingstreak.web;

import java.util.List;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.budgets.BudgetsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * What a customer says their money goes on, and what they used to.
 *
 * <p>A controller of its own rather than more handlers on {@code CurrentAccountController}, for the
 * reason {@code RecurringBillController} is one: a category is a thing with an identifier that is
 * declared, listed, renamed, ended and read back, and five handlers of it hung off the account's own
 * resource would make that class about two things. Unlike the bills, the standing categories are
 * <em>not</em> nested inside the account's own read: the account's page is about what arrives, what
 * goes out on a standing instruction and what that leaves, and the categories belong to the budget
 * screen that drills off it.
 *
 * <p><strong>This class vouches for the current account.</strong> Every handler asks Accounts first
 * and refuses in the words Accounts owns, which is the same order and the same sentence the income,
 * the bills, the goals and the saving rules use. It is why {@code SpendingCategoryRefused} has no
 * kind for a missing account: an account nobody has heard of is a 404 before any rule about
 * categories is reached, rather than a refusal dressed up as one — and an empty list would tell
 * somebody that an account they do not hold simply has nothing on it.
 *
 * <p>Beyond that it reads the request and judges nothing. A body that did not arrive at all is a
 * fact about the request and is answered here; whether a name is a name, whether that word is
 * already in use on the account and how many categories one account may carry are rules, and they
 * belong to Budgets, which refuses on its own.
 *
 * <p><strong>There is nothing here about whose account it is.</strong> This application has no
 * authentication and every path names its account by identifier, so there is no signed-in customer
 * to compare one against. What is scoped is the category: a category is only ever found through the
 * account in the path, so a category identifier from somebody else's account answers as a category
 * that is not there. The page makes the other half of the check, before it reads anything, against
 * the accounts the signed-in customer actually holds.
 */
@RestController
@RequestMapping("/api/current-accounts/{currentAccountId}/categories")
class SpendingCategoryController {

    private static final Logger log = LoggerFactory.getLogger(SpendingCategoryController.class);

    private final AccountsService accounts;

    private final BudgetsService budgets;

    SpendingCategoryController(AccountsService accounts, BudgetsService budgets) {
        this.accounts = accounts;
        this.budgets = budgets;
    }

    /** The categories standing on the account, in the order their holder named them. */
    @GetMapping
    List<SpendingCategoryResponse> categoriesOn(@PathVariable long currentAccountId) {
        vouchFor(currentAccountId, "spending categories");
        return budgets.categoriesOn(currentAccountId).stream()
                .map(SpendingCategoryResponse::of).toList();
    }

    /**
     * What was ended, so that a category closed rather than deleted can still be read back.
     *
     * <p>A path of its own rather than a flag on the list above, so that the ordinary read stays the
     * ordinary read — the same shape the ended bills, the ended saving rules and the abandoned goals
     * have. It is what makes "an ended category keeps its months" a thing somebody can see rather
     * than take on trust.
     */
    @GetMapping("/ended")
    List<SpendingCategoryResponse> endedCategoriesOn(@PathVariable long currentAccountId) {
        vouchFor(currentAccountId, "ended spending categories");
        return budgets.endedCategoriesOn(currentAccountId).stream()
                .map(SpendingCategoryResponse::of).toList();
    }

    /**
     * Names one, and answers with the category that now stands.
     *
     * <p>A POST rather than a PUT: a second category is a second category with an identifier of its
     * own, exactly as a second bill is, and it is the identifier the answer carries that everything
     * afterwards names it by.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SpendingCategoryResponse declareACategory(@PathVariable long currentAccountId,
                                              @RequestBody(required = false)
                                              NewCategoryRequest request) {
        vouchFor(currentAccountId, "spending category");
        if (request == null) {
            String reason = "A category needs a name — \"Groceries\", \"Fuel\", \"Going out\".";
            log.warn("spending category rejected currentAccountId={} reason={}", currentAccountId,
                    reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return SpendingCategoryResponse.of(
                budgets.declareACategory(currentAccountId, request.name()));
    }

    /**
     * Renames one, and answers with the category as it now reads.
     *
     * <p>A PATCH rather than a PUT, matching the bill's change: what arrives is a change to a
     * category that is already there rather than a category being put at that address. A category
     * has one field, so the two would look identical today — and would stop looking identical the
     * moment anything else joined it.
     *
     * <p>The body is optional here and on the POST above so that a request arriving with none at all
     * is answered in this application's own words rather than in Spring's. A body that arrived and
     * named nothing — <code>&#123;&#125;</code> — is Budgets' refusal rather than this one's: that
     * is a category with no name, and what a name has to be is a rule about categories.
     */
    @PatchMapping("/{categoryId}")
    SpendingCategoryResponse renameCategory(@PathVariable long currentAccountId,
                                            @PathVariable long categoryId,
                                            @RequestBody(required = false)
                                            RenameCategoryRequest request) {
        vouchFor(currentAccountId, "spending category");
        if (request == null) {
            String reason = "Say what to call this category instead.";
            log.warn("spending category rename rejected currentAccountId={} categoryId={} "
                    + "reason={}", currentAccountId, categoryId, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return SpendingCategoryResponse.of(
                budgets.renameCategory(currentAccountId, categoryId, request.name()));
    }

    /**
     * Ends a category for good, and answers with it as it now reads rather than with nothing.
     *
     * <p>A DELETE, and it is the honest verb for what a customer is doing — this word stops being
     * one they use — while what the application keeps is the record of it, read back through
     * {@code /ended}. The same choice ending a bill and ending a saving rule are made under.
     */
    @DeleteMapping("/{categoryId}")
    SpendingCategoryResponse endCategory(@PathVariable long currentAccountId,
                                         @PathVariable long categoryId) {
        vouchFor(currentAccountId, "spending category");
        return SpendingCategoryResponse.of(budgets.endCategory(currentAccountId, categoryId));
    }

    /**
     * Asked before Budgets' own rules are, in the words Accounts owns, so that every module
     * answering for a current account says its absence the same way — and so that an account nobody
     * has heard of is refused rather than answered with the empty category list of an account that
     * simply has none. Logged as well as answered, because a refusal decided here would otherwise
     * leave no line in the application's log at all.
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
