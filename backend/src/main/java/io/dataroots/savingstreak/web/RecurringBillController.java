package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.util.List;

import io.dataroots.savingstreak.accounts.ABillAsAsked;
import io.dataroots.savingstreak.accounts.AChangeToABill;
import io.dataroots.savingstreak.accounts.AccountsService;
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
 * What a customer says leaves one current account every month, and what they used to.
 *
 * <p>A controller of its own rather than more handlers on {@code CurrentAccountController}, for the
 * reason {@code SavingRuleController} is one: a bill is a thing with an identifier that is declared,
 * listed, changed, ended and read back, and six handlers of it hung off the account's own resource
 * would make that class about two things. The account's own read still carries the standing bills,
 * because a page that draws the account draws them in the same breath — the list here is the same
 * answer asked for by itself.
 *
 * <p><strong>This class vouches for the current account.</strong> Every handler asks Accounts first
 * and refuses in the words Accounts owns, which is the same order and the same sentence the income,
 * the goals and the saving rules use. It is why {@code RecurringBillRefused} has no kind for a
 * missing account: an account nobody has heard of is a 404 before any rule about bills is reached,
 * rather than a refusal dressed up as one.
 *
 * <p>Beyond that it reads the request and judges nothing. Whether the characters that arrived are a
 * number at all is a question about the request and is answered here; whether the figure is an
 * amount of money, whether the day is a day of the month, whether the name is a name and how many
 * bills one account may carry are rules, and they belong to Accounts, which refuses on its own.
 *
 * <p><strong>There is nothing here about whose account it is.</strong> This application has no
 * authentication and every path names its account by identifier, so there is no signed-in customer
 * to compare one against. What is scoped is the bill: a bill is only ever found through the account
 * in the path, so a bill identifier from somebody else's account answers as a bill that is not
 * there. The page makes the other half of the check, before it reads anything, against the accounts
 * the signed-in customer actually holds.
 */
@RestController
@RequestMapping("/api/current-accounts/{currentAccountId}/bills")
class RecurringBillController {

    private static final Logger log = LoggerFactory.getLogger(RecurringBillController.class);

    private final AccountsService accounts;

    RecurringBillController(AccountsService accounts) {
        this.accounts = accounts;
    }

    /** The bills standing against the account, in the order their holder declared them. */
    @GetMapping
    List<RecurringBillResponse> billsOn(@PathVariable long currentAccountId) {
        vouchFor(currentAccountId, "bills");
        return accounts.billsOn(currentAccountId).stream().map(RecurringBillResponse::of).toList();
    }

    /**
     * What was ended, so that a bill closed rather than deleted can still be read back.
     *
     * <p>A path of its own rather than a flag on the list above, so that the ordinary read stays the
     * ordinary read — the same shape the ended saving rules and the abandoned goals have. It is what
     * makes "an ended bill keeps its record" a thing somebody can see rather than take on trust.
     */
    @GetMapping("/ended")
    List<RecurringBillResponse> endedBillsOn(@PathVariable long currentAccountId) {
        vouchFor(currentAccountId, "ended bills");
        return accounts.endedBillsOn(currentAccountId).stream()
                .map(RecurringBillResponse::of).toList();
    }

    /**
     * Every date one bill fell due on and what became of each, newest first.
     *
     * <p>A path of its own under the bill, the same shape a saving rule's history has, and read on
     * demand rather than sent down with the account: a bill caught up over three years is thirty-six
     * dates, and a page that opened with all of them for every bill would be reading an account's
     * whole record to answer "what is standing here".
     *
     * <p>It is the only place a date that was <em>not</em> paid can be seen. The balance is a figure
     * rather than a sum of records, so a month the rent did not go out leaves no trace in it at all;
     * here it is a row saying what was owed and that nothing moved.
     *
     * <p>Answered for an ended bill as well as for a standing one, which is what makes ending a
     * closing rather than a deletion: the months it was taken for stay explained.
     */
    @GetMapping("/{billId}/history")
    List<BillOccurrenceResponse> historyOf(@PathVariable long currentAccountId,
                                           @PathVariable long billId) {
        vouchFor(currentAccountId, "bill history");
        return accounts.historyOfBill(currentAccountId, billId).stream()
                .map(BillOccurrenceResponse::of).toList();
    }

    /**
     * Declares a bill, and answers with the bill that now stands.
     *
     * <p>A POST rather than the PUT the income uses, and the difference is the whole shape of this
     * feature: there is at most one income per account, so declaring it twice is the same request
     * made twice, while a second bill is a second bill with an identifier of its own.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    RecurringBillResponse declareABill(@PathVariable long currentAccountId,
                                       @RequestBody(required = false) NewBillRequest request) {
        vouchFor(currentAccountId, "bill");
        if (request == null) {
            String reason = "A bill needs a name, the day of the month it goes out on and what it "
                    + "is worth.";
            log.warn("bill rejected currentAccountId={} reason={}", currentAccountId, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return RecurringBillResponse.of(accounts.declareABill(currentAccountId, new ABillAsAsked(
                request.name(),
                dayOfMonthIn(currentAccountId, request.dayOfMonth()),
                amountIn(currentAccountId, request.amount()))));
    }

    /**
     * Changes whichever of the three a customer sent, and leaves the rest of the bill alone.
     *
     * <p>The body is optional here and on the POST above so that a request arriving with none at all
     * is answered in this application's own words rather than in Spring's. A body that arrived and
     * asked for nothing — <code>&#123;&#125;</code> — is Accounts' refusal rather than this one's:
     * that is a change that says nothing, and what a change has to say is a rule about bills.
     */
    @PatchMapping("/{billId}")
    RecurringBillResponse changeBill(@PathVariable long currentAccountId, @PathVariable long billId,
                                     @RequestBody(required = false) ChangeBillRequest request) {
        vouchFor(currentAccountId, "bill");
        if (request == null) {
            String reason = "Say what to change about the bill: its name, the day of the month it "
                    + "goes out on, or what it is worth.";
            log.warn("bill change rejected currentAccountId={} billId={} reason={}",
                    currentAccountId, billId, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return RecurringBillResponse.of(accounts.changeBill(currentAccountId, billId,
                new AChangeToABill(
                        request.name(),
                        dayOfMonthIn(currentAccountId, request.dayOfMonth()),
                        amountIn(currentAccountId, request.amount()))));
    }

    /**
     * Ends a bill for good, and answers with the bill as it now reads rather than with nothing.
     *
     * <p>A DELETE, and it is the honest verb for what a customer is doing — this instruction stops
     * existing — while what the application keeps is the record of it, read back through
     * {@code /ended}. The same choice ending a saving rule is made under.
     */
    @DeleteMapping("/{billId}")
    RecurringBillResponse endBill(@PathVariable long currentAccountId, @PathVariable long billId) {
        vouchFor(currentAccountId, "bill");
        return RecurringBillResponse.of(accounts.endBill(currentAccountId, billId));
    }

    /**
     * Asked before Accounts' own rules are, in the words Accounts owns, so that every module
     * answering for a current account says its absence the same way — and so that an account nobody
     * has heard of is refused rather than answered with the empty bill list of an account that
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

    /**
     * The amount as a number, nothing at all when nobody sent one, or a refusal naming what could
     * not be read as one.
     *
     * <p>Absent stays absent rather than becoming a zero, because on a change it means "leave the
     * figure alone" and on a new bill it is an objection Accounts makes in its own sentence. Named
     * back to whoever sent it, because a person who typed a comma has to see the comma to see the
     * mistake.
     */
    private BigDecimal amountIn(long currentAccountId, String amount) {
        if (amount == null || amount.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(amount.trim());
        } catch (NumberFormatException notANumber) {
            String reason = "\"" + amount + "\" is not an amount of money. Write it in digits with a "
                    + "full stop, like 900.00.";
            log.warn("bill rejected currentAccountId={} amount={} reason={}", currentAccountId,
                    amount, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }

    /**
     * The day as a whole number, nothing at all when nobody sent one, or a refusal naming what could
     * not be read as one. Whether it is a day a month actually has is Accounts' answer: this only
     * says whether the characters are a number at all, which is the same division of labour the
     * amount above is read under.
     */
    private Integer dayOfMonthIn(long currentAccountId, String dayOfMonth) {
        if (dayOfMonth == null || dayOfMonth.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(dayOfMonth.trim());
        } catch (NumberFormatException notANumber) {
            String reason = "\"" + dayOfMonth + "\" is not a day of the month. Write it in digits, "
                    + "like 1.";
            log.warn("bill rejected currentAccountId={} dayOfMonth={} reason={}", currentAccountId,
                    dayOfMonth, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }
}
