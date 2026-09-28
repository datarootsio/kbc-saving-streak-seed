package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.util.List;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.budgets.APartAsAsked;
import io.dataroots.savingstreak.budgets.ASpendAsAsked;
import io.dataroots.savingstreak.budgets.SpendsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * What a customer actually spent out of one current account, and the recent ones read back.
 *
 * <p>A controller of its own rather than more handlers on {@code SpendingCategoryController}, for
 * the reason that one is separate from the account's: a category is a word and a spend is money
 * leaving, they are declared on different paths and read on different ones, and one class about
 * both would be a class about two things.
 *
 * <p><strong>What is not here is as deliberate as what is.</strong> There is no DELETE of a spend,
 * no PATCH of one and no mapping of any kind that takes an amount or a name for one already
 * recorded. The money moved, and a record that can be unmade is not a record. The one thing a
 * customer may say differently afterwards is what the spend was for, and it is said at the split's
 * own address.
 *
 * <p><strong>This class vouches for the current account.</strong> Every handler asks Accounts first
 * and refuses in the words Accounts owns, which is the same order and the same sentence the income,
 * the bills, the goals, the saving rules and the categories use. It is why {@code SpendRefused} has
 * no kind for a missing account.
 *
 * <p>Beyond that it reads the request and judges nothing. A body that did not arrive, and a figure
 * whose characters are not a number at all, are facts about the request and are answered here;
 * whether a name is a name, whether a figure is an amount of money, whether a split adds up and
 * whether the account holds it are rules, and they belong to Budgets, which refuses on its own.
 *
 * <p><strong>There is nothing here about whose account it is.</strong> This application has no
 * authentication and every path names its account by identifier, so there is no signed-in customer
 * to compare one against. The page makes the other half of the check, before it reads anything,
 * against the accounts the signed-in customer actually holds.
 */
@RestController
@RequestMapping("/api/current-accounts/{currentAccountId}/spends")
class SpendController {

    private static final Logger log = LoggerFactory.getLogger(SpendController.class);

    private final AccountsService accounts;

    private final SpendsService spends;

    SpendController(AccountsService accounts, SpendsService spends) {
        this.accounts = accounts;
        this.spends = spends;
    }

    /** The recent spends on the account, newest first, each carrying the split it was recorded with. */
    @GetMapping
    List<SpendResponse> spendsOn(@PathVariable long currentAccountId) {
        vouchFor(currentAccountId, "spends");
        return spends.recentSpendsOn(currentAccountId).stream().map(SpendResponse::of).toList();
    }

    /**
     * Records one, takes the money for it, and answers with the spend as it now reads.
     *
     * <p>A POST, and 201: a second spend is a second spend with an identifier of its own, exactly as
     * a second bill is. There is no PUT and no DELETE beside it and there will not be — the money
     * moved, and a record that can be unmade is not a record. What can be changed is the split,
     * which is the only thing about a spend that was ever an opinion.
     *
     * <p>The answer carries the spend rather than the balance. What the account holds afterwards is
     * Accounts' own read, and the page fetches it there so that one figure has one source.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    SpendResponse recordASpend(@PathVariable long currentAccountId,
                               @RequestBody(required = false) NewSpendRequest request) {
        vouchFor(currentAccountId, "spend");
        if (request == null) {
            String reason = "A spend needs a name, what it cost, and what it was for.";
            log.warn("spend rejected currentAccountId={} reason={}", currentAccountId, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return SpendResponse.of(spends.recordASpend(currentAccountId, new ASpendAsAsked(
                request.name(),
                amountIn(currentAccountId, request.amount()),
                splitIn(currentAccountId, request.parts()))));
    }

    /**
     * Corrects what a spend was for, replacing the whole split, and answers with the spend as it now
     * reads.
     *
     * <p>A PUT at the split's own address rather than a PATCH on the spend, and that address is the
     * whole argument: what is being replaced is the split, which is a thing a customer can state in
     * full, and a PUT says that sending it twice says the same thing. A PATCH on the spend would
     * suggest there are other fields on it to patch, and there are not — the amount and the name are
     * facts about money that moved, and there is no mapping here that would take either.
     *
     * <p>There is no DELETE beside it either, on the spend or on its split. A record that can be
     * unmade is not a record, and a spend whose euros were filed under nothing at all is not a
     * correction of anything — the way to say "I do not know yet" is a part with no category, which
     * this request can carry.
     *
     * <p>200 rather than 201: the spend was already there and is still the same spend, at the same
     * identifier, for the same amount. The answer carries the whole spend rather than the split
     * alone, so that a page which has just corrected one draws it without fetching the list again.
     *
     * <p>The body is optional so that a request arriving with none at all is answered in this
     * application's own words rather than in Spring's. A body that arrived carrying no parts is a
     * different sentence and it is Budgets', which owns what a split has to be.
     */
    @PutMapping("/{spendId}/split")
    SpendResponse correctTheSplitOf(@PathVariable long currentAccountId,
                                    @PathVariable long spendId,
                                    @RequestBody(required = false) CorrectedSplitRequest request) {
        vouchFor(currentAccountId, "spend split");
        if (request == null) {
            String reason = "Send the whole split you want the spend to have: a correction replaces "
                    + "what it was for rather than adding to it.";
            log.warn("spend split rejected currentAccountId={} spendId={} reason={}",
                    currentAccountId, spendId, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return SpendResponse.of(spends.correctTheSplitOf(currentAccountId, spendId,
                splitIn(currentAccountId, request.parts())));
    }

    /**
     * The split with every figure in it read as a number, or nothing at all when no split was sent.
     *
     * <p>Absent stays absent rather than becoming an empty list, because the two say different
     * things and only one of them is this class's to answer: nobody sent a split is Budgets'
     * objection, in the sentence it already has for it, and a list invented here would answer it in
     * this class's words instead.
     */
    private List<APartAsAsked> splitIn(long currentAccountId, List<NewSpendPartRequest> parts) {
        if (parts == null) {
            return null;
        }
        return parts.stream()
                .map(part -> part == null ? new APartAsAsked(null, null)
                        : new APartAsAsked(part.categoryId(),
                                amountIn(currentAccountId, part.amount())))
                .toList();
    }

    /**
     * The amount as a number, nothing at all when nobody sent one, or a refusal naming what could
     * not be read as one.
     *
     * <p>Absent stays absent rather than becoming a zero, because "you did not say what it cost" and
     * "a spend of nothing is not a spend" are different sentences and Budgets owns both. Named back
     * to whoever sent it, because a person who typed a comma has to see the comma to see the
     * mistake. The same division of labour a bill's amount is read under.
     */
    private BigDecimal amountIn(long currentAccountId, String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(amount.trim());
        } catch (NumberFormatException notANumber) {
            String reason = "\"" + amount + "\" is not an amount of money. Write it in digits with a "
                    + "full stop, like 42.50.";
            log.warn("spend rejected currentAccountId={} amount={} reason={}", currentAccountId,
                    amount, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }

    /**
     * Asked before Budgets' own rules are, in the words Accounts owns, so that every module
     * answering for a current account says its absence the same way — and so that an account nobody
     * has heard of is refused rather than answered with the empty spend list of an account that
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
