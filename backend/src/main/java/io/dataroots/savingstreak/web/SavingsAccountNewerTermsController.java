package io.dataroots.savingstreak.web;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.products.ProductsService;

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
 * The two doors a customer on an older version of their product's terms needs: read what the newer
 * ones would change, and take them.
 *
 * <p><strong>Two doors rather than one, and that is the whole ticket.</strong> The reading says,
 * line by line, what would be different; the press is a separate request that changes the agreement
 * and nothing else. A single door that moved an account onto newer terms as a side effect of
 * anything — opening the page, making a deposit, a night passing — would be this application
 * adopting an agreement on somebody's behalf, and newer is not the same as better: the second
 * version of free savings <em>cut</em> the rate from 0.60% to 0.50%. The only other place an
 * account's version moves is a roll-over at maturity, which its holder agreed to when they opened
 * the term.
 *
 * <p><strong>The reading is served here as well as on the account's own page, and both send the
 * same record.</strong> The account's page carries it because the comparison and the agreement it
 * is a comparison with have to be drawn from one read; this door exists so that a page which has
 * just taken newer terms, or which cares about nothing else, can ask the one question on its own.
 * Neither of them words a difference — the backend has exactly one function that does, and the
 * product's version history renders from it too.
 *
 * <p>Under the savings account rather than under the product, because which version an account is on
 * is a fact about that account: the product is selling one thing to everybody and the accounts on it
 * are on whatever versions their holders have agreed to. The identifier in the path is vouched for
 * here — an account nobody has heard of is a 404 in the words Accounts owns for an absent account,
 * rather than an empty reading from a module that was never asked whether the account exists.
 *
 * <p>A controller of its own rather than two more methods on {@code SavingsAccountController},
 * following the term and notice controllers beside it: that class already answers for balances,
 * deposits, withdrawals and the timeline and takes eight services to do it, and which version of an
 * agreement an account is living under is a separate subject.
 *
 * <p><strong>Taking takes no request body, and there is nothing to put in one.</strong> There is one
 * version on offer, it is the one the reading compared against, and it is the one that gets written.
 * A body naming a version would let somebody ask for one published for next month or one that
 * stopped being sold in March, and would make every screen that drew this button responsible for
 * deciding which versions are takeable.
 *
 * <p>It judges nothing. Whether a term is still running, whether there is anything newer and whether
 * the account has been closed are rules the Products module keeps, and each comes back as a sentence
 * this layer carries untouched — a controller that checked first would be a second copy of the rule,
 * and the second copy is always the one that is out of date.
 */
@RestController
@RequestMapping("/api/savings-accounts/{savingsAccountId}/newer-terms")
class SavingsAccountNewerTermsController {

    private static final Logger log =
            LoggerFactory.getLogger(SavingsAccountNewerTermsController.class);

    private final AccountsService accounts;
    private final ProductsService products;

    SavingsAccountNewerTermsController(AccountsService accounts, ProductsService products) {
        this.accounts = accounts;
        this.products = products;
    }

    /**
     * What this account's product is offering today, what the account is on, and what differs
     * between the two — for every savings account, not only the ones with something newer waiting.
     *
     * <p>An account already on the version on offer answers with both version numbers equal, nothing
     * newer, and an empty list of differences, which is the true answer and the one a page draws no
     * panel from. Refusing it would make every screen ask whether there was anything to read before
     * it dared read it — the same reading, for the same reason, the term and notice doors give every
     * account.
     *
     * <p>An account no agreement has been recorded for is a 404 rather than an empty answer: it is a
     * database that has not been through the start-up migration, there is no version to compare and
     * nothing true to say about a comparison that cannot be made.
     */
    @GetMapping
    TheNewerTermsResponse newerTermsOn(@PathVariable long savingsAccountId) {
        vouchFor(savingsAccountId, "the newer terms on offer for a savings account");
        return products.theNewerTermsFor(savingsAccountId)
                .map(TheNewerTermsResponse::of)
                .orElseThrow(() -> {
                    String reason = "Savings account " + savingsAccountId + " has no agreement on "
                            + "record, so there is nothing to compare newer terms with.";
                    log.warn("the newer terms on offer for a savings account were rejected "
                            + "savingsAccountId={} reason={}", savingsAccountId, reason);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
                });
    }

    /**
     * Takes the version the product is selling today, and answers with the agreement as it now
     * reads.
     *
     * <p>A press rather than a {@code PUT} at a version's address, and the difference is the ticket.
     * A PUT would be somebody putting their account at a version they chose; this is asking to take
     * what is on offer, which is the only thing this application will do.
     *
     * <p>200 rather than 201, because nothing was created: the same account carries on under a
     * different edition of the same agreement, and what comes back is that agreement. No money, no
     * points and no allocations moved, which is why there is no receipt to hand back.
     */
    @PostMapping("/take")
    AnAgreementResponse take(@PathVariable long savingsAccountId) {
        vouchFor(savingsAccountId, "taking the newer terms of a savings product");
        return AnAgreementResponse.of(products.takeTheNewerTerms(savingsAccountId));
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
