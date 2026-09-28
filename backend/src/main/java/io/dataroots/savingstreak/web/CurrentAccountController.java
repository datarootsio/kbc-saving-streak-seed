package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.util.List;

import io.dataroots.savingstreak.accounts.ADeclaredBill;
import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.accounts.AnArrear;
import io.dataroots.savingstreak.accounts.DeclaredIncome;
import io.dataroots.savingstreak.accounts.TheMonthAhead;
import io.dataroots.savingstreak.accounts.WhatACurrentAccountHolds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * One current account: what is in it, and the income its holder says lands in it every month.
 *
 * <p>The account reads as a resource of its own, rather than only as a row of the customer's
 * directory of accounts, because it now has a screen of its own to be drawn on. A page about one
 * account that had to read the whole directory and pick a row out of it would be fetching four
 * accounts to draw one, and would have nowhere to put the things a later slice hangs off this
 * account.
 *
 * <p>What goes out every month is its own resource under this one, in
 * {@code RecurringBillController}: a bill has an identifier and is declared, changed and ended,
 * which is five more handlers than this class should be about. The account's own read below still
 * carries the standing bills, because a page draws them in the same breath as the balance.
 *
 * <p>A controller of its own rather than more fields on {@code /api/customers/{id}/accounts}, because
 * an income belongs to one account rather than to the list: a household with two current accounts
 * declares an income against the one the salary actually lands in, and a figure hung off the
 * directory could not say which.
 *
 * <p><strong>This class vouches for the current account.</strong> Accounts owns what a current
 * account is and therefore owns what its absence is called, and this is where that is said —
 * {@code AccountsService.noSuchCurrentAccount}, the same sentence a deposit and a withdrawal answer
 * with. It is why there is no {@code NO_SUCH_ACCOUNT} kind on {@link
 * io.dataroots.savingstreak.accounts.MonthlyIncomeRefused}: an account nobody has heard of is a 404
 * before any rule about income is reached, rather than a refusal dressed up as one.
 *
 * <p>Beyond that it reads the request and judges nothing. Whether the characters that arrived are a
 * number at all is a question about the request and is answered here; whether the figure is an
 * income this application will keep, and whether the day is a day of the month, are rules, and they
 * belong to Accounts, which refuses on its own.
 *
 * <p>A PUT rather than a POST, for the reason the saving capacity's is: there is at most one income
 * per account, sending the same figure twice leaves the account exactly where it was, and "change
 * it" and "declare it for the first time" are the same request.
 */
@RestController
@RequestMapping("/api/current-accounts")
class CurrentAccountController {

    private static final Logger log = LoggerFactory.getLogger(CurrentAccountController.class);

    private final AccountsService accounts;

    CurrentAccountController(AccountsService accounts) {
        this.accounts = accounts;
    }

    /**
     * The account itself: what is in it, who holds it, what they say lands in it every month and
     * what they say leaves it.
     *
     * <p>In one read transaction, for the reason a savings account's own overview gives: the
     * balance, the declaration and the bills are three reads, and an income declared or a bill ended
     * between them would have the page quote a balance from before a request the answers beside it
     * already reflect. One transaction, and every part describes the same instant of the ledger.
     *
     * <p>The standing bills only. What has been ended is a record rather than a claim on this
     * balance, and it is read from the bills' own {@code /ended} path by whoever is showing it.
     *
     * <p>The month ahead is the fourth read and it is inside the same transaction for the same
     * reason: it is the sentence the other three add up to, and a bill declared between the two
     * would have the page quote a list of bills that its own total disagrees with.
     *
     * <p>Logged at DEBUG rather than at INFO. Reading an account is not a business event — nothing
     * about the account changed — but which figures were handed to a page is exactly what a reviewer
     * wants when the page and the database disagree.
     */
    @Transactional(readOnly = true)
    @GetMapping("/{currentAccountId}")
    CurrentAccountResponse currentAccount(@PathVariable long currentAccountId) {
        WhatACurrentAccountHolds account = accounts.currentAccountWith(currentAccountId)
                .orElseThrow(() -> {
                    String reason = AccountsService.noSuchCurrentAccount(currentAccountId);
                    log.warn("current account rejected currentAccountId={} reason={}",
                            currentAccountId, reason);
                    return new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
                });
        DeclaredIncome income = accounts.monthlyIncomeOn(currentAccountId);
        List<ADeclaredBill> bills = accounts.billsOn(currentAccountId);
        List<AnArrear> arrears = accounts.arrearsOn(currentAccountId);
        TheMonthAhead monthAhead = accounts.theMonthAheadOn(currentAccountId);
        log.debug("current account read currentAccountId={} balance={} incomeDeclared={} "
                        + "incomeDayOfMonth={} incomeAmount={} billsStanding={} arrears={} "
                        + "monthAheadUntil={} incomeDue={} billsDue={} leavesYou={}",
                currentAccountId, account.balance(), income.isDeclared(), income.dayOfMonth(),
                income.amount(), bills.size(), arrears.size(), monthAhead.until(),
                monthAhead.incomeDue(), monthAhead.billsDue(), monthAhead.leavesYou());
        return CurrentAccountResponse.of(account, income, bills, arrears, monthAhead);
    }

    /**
     * What this account still owes, oldest first — every date a bill fell due on that could not be
     * paid and has not been settled since.
     *
     * <p>A path of its own as well as being carried by the account's own read, the same bargain the
     * standing bills strike: the page draws what is owed in the same breath as the balance it is
     * claimed against, and anything that wants only the arrears can ask for only the arrears.
     *
     * <p>An empty list rather than a 404 when nothing is owed, because owing nothing is an answer
     * about an account that exists — and a very good one. What the page does with an empty list is
     * draw no section at all.
     */
    @GetMapping("/{currentAccountId}/arrears")
    List<ArrearResponse> arrearsOn(@PathVariable long currentAccountId) {
        vouchFor(currentAccountId, "arrears");
        return accounts.arrearsOn(currentAccountId).stream().map(ArrearResponse::of).toList();
    }

    /**
     * What the account's holder has said lands in it, or that they have said nothing.
     *
     * <p>Answered rather than left to a 404, because "nobody has declared one" is an answer about an
     * account that exists and a page has something to draw for it: the boxes to type one into.
     */
    @GetMapping("/{currentAccountId}/monthly-income")
    MonthlyIncomeResponse monthlyIncomeOn(@PathVariable long currentAccountId) {
        vouchFor(currentAccountId, "monthly income");
        return MonthlyIncomeResponse.of(accounts.monthlyIncomeOn(currentAccountId));
    }

    /** Declares it, or declares it again, and answers with the income as the account now reports it. */
    @PutMapping("/{currentAccountId}/monthly-income")
    MonthlyIncomeResponse declareMonthlyIncome(@PathVariable long currentAccountId,
                                               @RequestBody MonthlyIncomeRequest request) {
        vouchFor(currentAccountId, "monthly income");
        if (request == null || request.amount() == null || request.dayOfMonth() == null) {
            String reason = "Say the day of the month you are paid on and how much lands, "
                    + "as an amount of money.";
            log.warn("monthly income rejected currentAccountId={} reason={}", currentAccountId, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
        return MonthlyIncomeResponse.of(accounts.declareMonthlyIncome(
                currentAccountId,
                dayOfMonthIn(currentAccountId, request.dayOfMonth()),
                amountIn(currentAccountId, request.amount())));
    }

    /**
     * Withdraws the declaration, after which the nightly job credits this account nothing, and
     * answers with what the account now says — which is that nobody has declared anything.
     *
     * <p>An answer rather than an empty 204, so that a page which has just cleared the boxes is told
     * in the same shape it reads and declares with. Withdrawing a declaration nobody made is
     * accepted quietly, for the reason Accounts gives: the customer asked for there to be no income
     * on this account, and there is none.
     */
    @DeleteMapping("/{currentAccountId}/monthly-income")
    MonthlyIncomeResponse withdrawMonthlyIncome(@PathVariable long currentAccountId) {
        vouchFor(currentAccountId, "monthly income");
        return MonthlyIncomeResponse.of(accounts.withdrawMonthlyIncome(currentAccountId));
    }

    /**
     * Asked before Accounts' own rules are, in the words Accounts owns, so that every module
     * answering for a current account says its absence the same way. Logged as well as answered,
     * because a refusal decided here would otherwise leave no line in the application's log at all.
     */
    private void vouchFor(long currentAccountId, String whatWasAsked) {
        if (!accounts.currentAccountExists(currentAccountId)) {
            String reason = AccountsService.noSuchCurrentAccount(currentAccountId);
            log.warn("{} rejected currentAccountId={} reason={}", whatWasAsked, currentAccountId, reason);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, reason);
        }
    }

    /**
     * The amount as a number, or a refusal naming what could not be read as one — the same answer, in
     * the same shape, a goal's target and a weekly capacity get. Named back to whoever sent it,
     * because a person who typed a comma has to see the comma to see the mistake. Whether the figure
     * is an income this application will keep is Accounts' answer, not this one's.
     */
    private BigDecimal amountIn(long currentAccountId, String amount) {
        try {
            return new BigDecimal(amount.trim());
        } catch (NumberFormatException notANumber) {
            String reason = "\"" + amount + "\" is not an amount of money. Write it in digits with a "
                    + "full stop, like 2500.00.";
            log.warn("monthly income rejected currentAccountId={} amount={} reason={}",
                    currentAccountId, amount, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }

    /**
     * The day as a whole number, or a refusal naming what could not be read as one. Whether it is a
     * day a month actually has is Accounts' answer: this only says whether the characters are a
     * number at all, which is the same division of labour the amount above is read under.
     */
    private int dayOfMonthIn(long currentAccountId, String dayOfMonth) {
        try {
            return Integer.parseInt(dayOfMonth.trim());
        } catch (NumberFormatException notANumber) {
            String reason = "\"" + dayOfMonth + "\" is not a day of the month. Write it in digits, "
                    + "like 25.";
            log.warn("monthly income rejected currentAccountId={} dayOfMonth={} reason={}",
                    currentAccountId, dayOfMonth, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }
}
