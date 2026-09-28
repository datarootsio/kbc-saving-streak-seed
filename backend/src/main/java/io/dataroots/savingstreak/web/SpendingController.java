package io.dataroots.savingstreak.web;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;

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
 * How a month is going on one current account, and how the last few of them went: what every
 * category was allowed to cost, what it cost, and what that leaves.
 *
 * <p>A controller of its own rather than more handlers on {@code SpendingCategoryController} or
 * {@code SpendController}, and it mirrors the three faces underneath it: one path declares the
 * words and the figures, one records the money, and this one reads what came of both. A reader
 * looking for "why does the page say I have eighty euros left" opens this class and finds one
 * service behind it.
 *
 * <p><strong>Two handlers for one question.</strong> This month and a month already gone are the
 * same fold over the same records, asked about different windows, so they are one service method
 * with two addresses — {@code /spending} for the month the application's clock is in, which is the
 * one a page opens without having to know what day it is, and {@code /spending/{yearMonth}} for a
 * month somebody names. A second derivation for history would be a second place to get the
 * arithmetic right.
 *
 * <p><strong>And a third for a different question.</strong> {@code /spending/history} is not a
 * month at all: it is the last few months side by side, with the average behind them, which is what
 * tells a bad month from a habit. It is here rather than on a controller of its own because it is
 * the same records read over a longer window and served by the same face underneath — but it is a
 * handler of its own rather than a parameter on the month read, because a stretch and a month are
 * two questions and one address answering both would be an address whose answer changed shape.
 *
 * <p><strong>This class vouches for the current account.</strong> Every handler asks Accounts first
 * and refuse in the words Accounts owns, so that an account nobody has heard of is a 404 rather than
 * an empty month — which would tell somebody that an account they do not hold simply has nothing on
 * it.
 *
 * <p>Beyond that it reads the request and judges nothing. Whether {@code "2026-03"} is a month at
 * all is a fact about the request and is answered here, in a sentence written for whoever typed it;
 * every figure below it is a rule and belongs to Budgets.
 */
@RestController
@RequestMapping("/api/current-accounts/{currentAccountId}/spending")
class SpendingController {

    private static final Logger log = LoggerFactory.getLogger(SpendingController.class);

    private final AccountsService accounts;

    private final SpendingService spending;

    SpendingController(AccountsService accounts, SpendingService spending) {
        this.accounts = accounts;
        this.spending = spending;
    }

    /**
     * This month, which is the month the application's clock reads.
     *
     * <p>Without a month in the path, deliberately, so that the page a customer opens does not have
     * to work out what month it is. On a wound clock it would get that wrong, and a screen quoting a
     * month nobody in this application is in would be the one bug this whole read exists to avoid.
     */
    @GetMapping
    MonthOfSpendingResponse thisMonth(@PathVariable long currentAccountId) {
        vouchFor(currentAccountId, "this month's spending");
        return MonthOfSpendingResponse.of(spending.thisMonthOn(currentAccountId));
    }

    /**
     * A month somebody names, in the shape {@code 2026-03}.
     *
     * <p>The same answer as the read above it, about a different window — which is what makes "a
     * budget superseded in June leaves April quoting the figure that stood then" something a
     * customer can go and look at rather than a promise about rows they cannot see.
     */
    @GetMapping("/{yearMonth}")
    MonthOfSpendingResponse aMonth(@PathVariable long currentAccountId,
                                   @PathVariable String yearMonth) {
        vouchFor(currentAccountId, "a month's spending");
        return MonthOfSpendingResponse.of(
                spending.theMonthOn(currentAccountId, theMonthIn(currentAccountId, yearMonth)));
    }

    /**
     * The last few months on this account, category by category, with the average behind them.
     *
     * <p>A third address on the same prefix rather than a month read asked six times, because it is
     * a different question: "how is this month going" is about a month and "is this a bad month or
     * is this what I do" is about a stretch. One request draws the whole comparison, and underneath
     * it one set of range reads folds every month of it — six requests would be six passes over
     * almost exactly the same records for one screen.
     *
     * <p><strong>{@code /history} is a word and not a month, and that is what decides which handler
     * answers it.</strong> A literal beats a variable in the path matching this application already
     * runs on, so this handler takes {@code /spending/history} and the one above it goes on taking
     * {@code /spending/2026-03}. It is worth saying out loud because the two mappings sit on one
     * prefix and the one that is easier to write is the one that would quietly shadow the other.
     *
     * <p>Without a month in it, deliberately. The comparison always ends with the month the
     * application's clock reads: a page naming the month itself would name the wrong one on a wound
     * clock, which is the bug every read in this controller is shaped to avoid.
     */
    @GetMapping("/history")
    SpendingHistoryResponse theLastFewMonths(@PathVariable long currentAccountId) {
        vouchFor(currentAccountId, "the last few months of spending");
        return SpendingHistoryResponse.of(spending.theLastFewMonthsOn(currentAccountId));
    }

    /**
     * The month as a month, or a refusal naming what could not be read as one.
     *
     * <p>Read here rather than by a converter, so that the sentence a person gets back is this
     * application's own and names what they typed. Spring would otherwise answer a mistyped month
     * with a failure about type conversion, which tells somebody that a framework could not do
     * something rather than that they wrote the month the wrong way round.
     *
     * <p>A bad request rather than a missing page, and the distinction is worth drawing: the address
     * exists, it is the month in it that is wrong, and what the customer does next is type it again.
     */
    private YearMonth theMonthIn(long currentAccountId, String yearMonth) {
        try {
            return YearMonth.parse(yearMonth);
        } catch (DateTimeParseException notAMonth) {
            String reason = "\"" + yearMonth + "\" is not a month. Write it as the year and the "
                    + "month, like 2026-03.";
            log.warn("a month's spending rejected currentAccountId={} yearMonth={} reason={}",
                    currentAccountId, yearMonth, reason);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, reason);
        }
    }

    /**
     * Asked before Budgets is, in the words Accounts owns, so that every module answering for a
     * current account says its absence the same way — and so that an account nobody has heard of is
     * refused rather than answered with the empty month of an account that simply has nothing in it.
     * Logged as well as answered, because a refusal decided here would otherwise leave no line in
     * the application's log at all.
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
