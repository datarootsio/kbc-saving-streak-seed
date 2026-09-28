package io.dataroots.savingstreak.web;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.budgets.SpendingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The next six weeks on one current account: what arrives, what is committed, what the budgets
 * claim, what that leaves, and what it says its holder could save each week.
 *
 * <p>A controller of its own rather than another handler on {@code SpendingController}, and the
 * address says why: this is not a month of spending asked about over a different window, it is a
 * different question with different assumptions — a forecast, built out of two of Accounts'
 * calendars as well as this module's declarations, in which every budget is assumed to be spent to
 * its limit. A reader looking for "why does the card say I could save thirty euros a week" opens
 * this class and finds one service call behind it.
 *
 * <p><strong>This class vouches for the current account</strong>, exactly as the spending reads do
 * and in the words Accounts owns, so that an account nobody has heard of is a 404 rather than six
 * empty weeks — which would tell somebody that an account they do not hold simply has nothing
 * coming.
 *
 * <p><strong>The saving-capacity half of this story is not here, and that is deliberate.</strong>
 * What the customer has <em>declared</em> they can save is Goals' answer, served by
 * {@code SavingCapacityController} against a savings account; what their budget <em>says</em> they
 * could save is this one, against a current account. The two are put beside each other by whoever
 * draws them, and adopting the derived figure is a press that sends it to that other address. That
 * keeps the offer an offer: neither module gains a dependency on the other, and nothing in this
 * application quietly rewrites a sentence its customer said.
 */
@RestController
@RequestMapping("/api/current-accounts/{currentAccountId}/weeks-ahead")
class WeeksAheadController {

    private static final Logger log = LoggerFactory.getLogger(WeeksAheadController.class);

    private final AccountsService accounts;

    private final SpendingService spending;

    WeeksAheadController(AccountsService accounts, SpendingService spending) {
        this.accounts = accounts;
        this.spending = spending;
    }

    /**
     * The six weeks, beginning on the Monday the current week began on.
     *
     * <p>Without a week in the path, for the reason this month's spending is read without a month in
     * it: the page a customer opens should not have to work out what week it is, and on a wound
     * clock it would get that wrong.
     */
    @GetMapping
    WeeksAheadResponse theWeeksAhead(@PathVariable long currentAccountId) {
        vouchFor(currentAccountId);
        return WeeksAheadResponse.of(spending.theWeeksAheadOn(currentAccountId));
    }

    /**
     * Asked before Budgets is, in the words Accounts owns, so that every module answering for a
     * current account says its absence the same way. Logged as well as answered, because a refusal
     * decided here would otherwise leave no line in the application's log at all.
     */
    private void vouchFor(long currentAccountId) {
        if (!accounts.currentAccountExists(currentAccountId)) {
            String reason = AccountsService.noSuchCurrentAccount(currentAccountId);
            log.warn("the weeks ahead rejected currentAccountId={} reason={}", currentAccountId,
                    reason);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
        }
    }
}
