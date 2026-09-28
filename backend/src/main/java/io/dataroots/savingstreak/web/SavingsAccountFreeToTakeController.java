package io.dataroots.savingstreak.web;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.products.TheMoneyAnAgreementLetsGo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The one door behind "how much of this can I actually take out today".
 *
 * <p>Under the savings account rather than beside it, because what may leave is a fact about one
 * account and about nothing else. The identifier in the path is what it is addressed by and it is
 * vouched for here — an account nobody has heard of is a 404 in the words Accounts owns for an
 * absent account, rather than a nought from a module that was never asked whether the account
 * exists.
 *
 * <p>A controller of its own rather than another method on {@code SavingsAccountController},
 * following the notice and term controllers beside it: that class already answers for balances,
 * deposits, withdrawals and the timeline and takes eight services to do it, and this is a separate
 * subject with a separate service and no overlap with any of them.
 *
 * <p><strong>A reading and never a press.</strong> Nothing here moves a cent, and asking costs
 * nothing: it is the figure a customer reads before they decide what to type into the withdrawal
 * box, and the sentence they read instead of finding out by being refused.
 *
 * <p>It judges nothing. How much an agreement lets go, and why it lets go less than the balance,
 * are the Products module's rules and arrive already worded — a controller that worked the figure
 * out from a term and a notice would be a second copy of the order the conditions compose in, and
 * the second copy is always the one that is out of date.
 */
@RestController
@RequestMapping("/api/savings-accounts/{savingsAccountId}/free-to-take-today")
class SavingsAccountFreeToTakeController {

    private static final Logger log =
            LoggerFactory.getLogger(SavingsAccountFreeToTakeController.class);

    private final AccountsService accounts;
    private final TheMoneyAnAgreementLetsGo letsGo;

    SavingsAccountFreeToTakeController(AccountsService accounts, TheMoneyAnAgreementLetsGo letsGo) {
        this.accounts = accounts;
        this.letsGo = letsGo;
    }

    /**
     * What this account's agreement would let leave today, for every savings account and not only
     * for the ones with a condition attached.
     *
     * <p>Free savings answers with the whole balance free, no condition and no sentence, which is
     * the true answer and the one a page prints without a warning beside it. Refusing it would make
     * every screen ask what kind of account it was holding before it dared ask this — the same
     * reading, for the same reason, the notice and term doors give every account.
     */
    @GetMapping
    WhatCanLeaveTodayResponse freeToTakeToday(@PathVariable long savingsAccountId) {
        vouchFor(savingsAccountId, "what can leave a savings account today");
        return WhatCanLeaveTodayResponse.of(letsGo.whatCanLeaveToday(savingsAccountId));
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
