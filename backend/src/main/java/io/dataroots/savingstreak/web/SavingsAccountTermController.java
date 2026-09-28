package io.dataroots.savingstreak.web;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.products.FixedTermsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The two doors a customer holding a fixed term needs: read what the term says today, and break it.
 *
 * <p><strong>Two doors rather than one, and that is the feature.</strong> The reading quotes what
 * breaking would cost <em>before</em> anything is confirmed; the press is a separate request that
 * charges exactly that. A single door that broke the term as a side effect of somebody withdrawing
 * would be charging a price nobody had been shown, which is the thing the ticket rules out in as
 * many words.
 *
 * <p>Under the savings account rather than beside it, because a term is a fact about one account
 * and about nothing else. The identifier in the path is what both of these are addressed by, and it
 * is vouched for here — an account nobody has heard of is a 404 in the words Accounts owns for an
 * absent account, rather than an empty reading from a module that was never asked whether the
 * account exists.
 *
 * <p>A controller of its own rather than two more methods on {@code SavingsAccountController},
 * following the notice controller beside it: that class already answers for balances, deposits,
 * withdrawals and the timeline of an account and takes eight services to do it, and a term is a
 * separate subject with a separate service and no overlap with any of them.
 *
 * <p><strong>Breaking takes no request body, and there is nothing to put in one.</strong> Breaking
 * a fixed term breaks the whole of it — a partly-broken term would be two agreements over one
 * balance — so there is no amount to name, and the price is worked out from what the account holds
 * rather than from anything the customer types. A body would be a box somebody had to fill in to
 * say "yes, all of it", which the press already says.
 *
 * <p>It judges nothing about the term itself. Whether the account is on one, whether it has matured
 * and what breaking costs are rules the Products module keeps, and each comes back as a sentence
 * this layer carries untouched — a controller that checked first would be a second copy of the
 * rule, and the second copy is always the one that is out of date.
 */
@RestController
@RequestMapping("/api/savings-accounts/{savingsAccountId}/term")
class SavingsAccountTermController {

    private static final Logger log = LoggerFactory.getLogger(SavingsAccountTermController.class);

    private final AccountsService accounts;
    private final FixedTermsService terms;

    SavingsAccountTermController(AccountsService accounts, FixedTermsService terms) {
        this.accounts = accounts;
        this.terms = terms;
    }

    /**
     * What this account's term says today, for every savings account and not only for the ones that
     * are on one.
     *
     * <p>An account with no term answers with nought months, no maturity date, nothing locked and
     * nothing to pay, which is the true answer and the one a page draws no panel from. Refusing it
     * would make every screen ask what kind of account it was holding before it dared ask this —
     * the same reading, for the same reason, the notice door gives every account.
     */
    @GetMapping
    TheTermOnAnAccountResponse termOn(@PathVariable long savingsAccountId) {
        vouchFor(savingsAccountId, "the term on a savings account");
        return TheTermOnAnAccountResponse.of(terms.termOn(savingsAccountId));
    }

    /**
     * Breaks the term, charges the stated price and moves the account onto free savings.
     *
     * <p>A press rather than a {@code DELETE}, because nothing is deleted and quite a lot happens:
     * a charge is written into the ledger, the agreement is moved onto another product, and what
     * comes back says what all of that came to. It follows the shape every other deliberate act in
     * this application takes — closing an account, cancelling a notice, withdrawing a product.
     *
     * <p>200 rather than 201, because nothing was created that the customer now holds: the term
     * ended. The charge is a row in a ledger they can read, and the answer names what it came to.
     */
    @PostMapping("/break")
    ATermBrokenResponse breakIt(@PathVariable long savingsAccountId) {
        vouchFor(savingsAccountId, "breaking a fixed term");
        return ATermBrokenResponse.of(terms.breakTheTerm(savingsAccountId));
    }

    private void vouchFor(long savingsAccountId, String what) {
        if (accounts.savingsAccountExists(savingsAccountId)) {
            return;
        }
        String reason = AccountsService.noSuchSavingsAccount(savingsAccountId);
        log.warn("{} rejected savingsAccountId={} reason={}", what, savingsAccountId, reason);
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
    }
}
