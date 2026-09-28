package io.dataroots.savingstreak.web;

import java.math.BigDecimal;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.products.NoticesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The three doors a customer needs for a notice account: give notice on an amount, read what is
 * ready and what is still waiting, and cancel a notice they no longer need.
 *
 * <p>Under the savings account rather than beside it, because a notice is a fact about one account
 * and about nothing else. The identifier in the path is what every one of these is addressed by,
 * and it is vouched for here — an account nobody has heard of is a 404 in the words Accounts owns
 * for an absent account, rather than an empty list from a module that was never asked whether the
 * account exists.
 *
 * <p>A controller of its own rather than three more methods on {@code SavingsAccountController}.
 * That class already answers for balances, deposits, withdrawals and the timeline of an account,
 * and it takes eight services to do it; notice is a separate subject with a separate service and no
 * overlap with any of them.
 *
 * <p>Reading the request and never judging it. Whether the characters typed into the box are a
 * number at all is a question about what arrived, answered here; whether the number is an amount of
 * money this application will start a clock on is a rule, and it belongs to the module that keeps
 * the agreement. A controller that checked first would be a second copy of the rule, and the second
 * copy is always the one that is out of date.
 */
@RestController
@RequestMapping("/api/savings-accounts/{savingsAccountId}/notices")
class SavingsAccountNoticeController {

    private static final Logger log = LoggerFactory.getLogger(SavingsAccountNoticeController.class);

    private final AccountsService accounts;
    private final NoticesService notices;

    SavingsAccountNoticeController(AccountsService accounts, NoticesService notices) {
        this.accounts = accounts;
        this.notices = notices;
    }

    /**
     * What this account's notice says today, for every savings account and not only for the ones
     * that ask for some.
     *
     * <p>An account with no notice period answers with nought days, nought ready, nought waiting
     * and an empty list, which is the true answer and the one a page draws no panel from. Refusing
     * it would make every screen ask what kind of account it was holding before it dared ask this.
     */
    @GetMapping
    TheNoticeOnAnAccountResponse noticeOn(@PathVariable long savingsAccountId) {
        vouchFor(savingsAccountId, "the notice on a savings account");
        return TheNoticeOnAnAccountResponse.of(notices.noticeOn(savingsAccountId));
    }

    /** Starts the clock on an amount, answering with the notice and the day it comes free. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    NoticeResponse give(@PathVariable long savingsAccountId,
                        @RequestBody(required = false) GiveNoticeRequest request) {
        vouchFor(savingsAccountId, "notice");
        if (request == null || request.amount() == null || request.amount().isBlank()) {
            String reason = "Notice needs an amount to be given on.";
            log.warn("notice rejected savingsAccountId={} reason={}", savingsAccountId, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return NoticeResponse.of(
                notices.give(savingsAccountId, anAmount(savingsAccountId, request.amount())));
    }

    /**
     * Cancels a notice, leaving nothing standing behind it.
     *
     * <p>A press rather than a {@code DELETE}, because nothing is deleted: the row survives so that
     * a later question about why a withdrawal was refused can still name the notice that was
     * cancelled the day before. It follows the shape the administration doors already use for a
     * thing that is switched rather than removed.
     */
    @PostMapping("/{noticeId}/cancel")
    NoticeResponse cancel(@PathVariable long savingsAccountId, @PathVariable long noticeId) {
        vouchFor(savingsAccountId, "notice cancellation");
        return NoticeResponse.of(notices.cancel(savingsAccountId, noticeId));
    }

    private void vouchFor(long savingsAccountId, String what) {
        if (accounts.savingsAccountExists(savingsAccountId)) {
            return;
        }
        String reason = AccountsService.noSuchSavingsAccount(savingsAccountId);
        log.warn("{} rejected savingsAccountId={} reason={}", what, savingsAccountId, reason);
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
    }

    private BigDecimal anAmount(long savingsAccountId, String typed) {
        try {
            return new BigDecimal(typed.trim());
        } catch (NumberFormatException notANumber) {
            String reason = "\"" + typed + "\" is not an amount of money. Write it in digits with a "
                    + "full stop, like 250.00.";
            log.warn("notice rejected savingsAccountId={} amount={} reason={}", savingsAccountId,
                    typed, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }
}
