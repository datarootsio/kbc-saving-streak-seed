package io.dataroots.savingstreak.web;

import java.util.List;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.products.InterestService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * What interest a savings account has been paid, month by month.
 *
 * <p>Its own resource rather than more fields on the account's overview, for the reason the
 * timeline beside it is one: the overview is read on every visit to every screen and already
 * assembles six modules, while this is wanted by one panel, it is the only caller of the read
 * behind it, and a resource that can be asked for on its own is one a trainer can curl while
 * demonstrating what a wound-forward clock does to a balance.
 *
 * <p>Its own controller rather than another method on the savings account's, which is the same
 * arrangement the goals on an account already have. The list is per account and the account is in
 * the path, so nothing is lost; what is gained is that the class a reader opens to find out how
 * interest reaches a screen contains nothing but interest.
 *
 * <p>Asked whether the account exists first, so that an account nobody has heard of is refused
 * rather than answered with the empty history of an account that has simply never been paid — the
 * same order, and the same refusal, the deposit history uses. The Products module cannot draw that
 * distinction itself: both are a list of no postings, and who holds which account is the Accounts
 * module's answer.
 *
 * <p>Nothing here is decided. Which months have been judged, what each was worth and at what rate
 * are the Products module's answers; this names the account and turns the reading into the one the
 * screen gets.
 */
@RestController
@RequestMapping("/api/savings-accounts")
class SavingsAccountInterestController {

    private final AccountsService accounts;
    private final InterestService interest;

    SavingsAccountInterestController(AccountsService accounts, InterestService interest) {
        this.accounts = accounts;
        this.interest = interest;
    }

    @GetMapping("/{savingsAccountId}/interest")
    List<InterestPostingResponse> interestPaidInto(@PathVariable long savingsAccountId) {
        if (!accounts.savingsAccountExists(savingsAccountId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND, AccountsService.noSuchSavingsAccount(savingsAccountId));
        }
        return interest.interestPaidInto(savingsAccountId).stream()
                .map(InterestPostingResponse::of)
                .toList();
    }
}
