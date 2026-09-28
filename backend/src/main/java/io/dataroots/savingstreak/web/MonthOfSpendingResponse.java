package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.budgets.WhatAnAccountSpentInAMonth;

/**
 * One current account's month as the API reports it: which month, the days it runs between, the
 * account's own totals, and a row per category under them.
 *
 * <p>One read answers both screens. The current account's budget card draws the totals and the
 * categories nearest their limit; the budget screen draws every row with a bar. They cannot disagree
 * about what the month cost, because they are two readings of one answer rather than two requests
 * with two sums in them.
 *
 * <p><strong>{@code month} travels as {@code "2026-03"}</strong>, which is what a month is. A date
 * would invite a page to print a day nobody meant and a number would invite arithmetic on it; the
 * two days beside it say how far the month runs, sent rather than inferred for the reason
 * {@link MonthAheadResponse} sends the ends of its window — a page working out what month it is
 * would draw a month nobody is in on a wound clock.
 *
 * <p><strong>{@code budgeted}, {@code carriedIn}, {@code allowed} and {@code left} are null when no
 * category on the account carries a figure in this month.</strong> Not declared is a state and not a
 * nought, the same reading a single category's row takes and the same one the declared saving
 * capacity has always taken. Where some categories are budgeted and others are not, these are the
 * totals over the ones that are.
 *
 * <p><strong>{@code carriedIn} and {@code allowed} are the account's view of the carry</strong>:
 * what every budgeted category was handed by the months before this one, and what the budgets plus
 * that carry allow altogether. {@code left} is taken from {@code allowed} rather than from
 * {@code budgeted}, so the card and the rows under it agree about a month that a frugal spring paid
 * for. There is no rule at this level, because a rule is chosen per category and an account with two
 * of them has no single answer.
 *
 * <p><strong>{@code uncategorised} is money that left and is filed under nothing</strong>, quoted on
 * its own and deliberately not added into {@code spent}: {@code spent} is the sum of the rows below,
 * and a total holding money that belongs to none of them would be a total nobody could check. It is
 * here at all because a spend can be recorded with a part carrying no category — which is a state a
 * customer chooses, and exactly what a correction is for — and euros that simply vanished from the
 * page would be the one thing a budget read must never do.
 *
 * <p>Every figure is derived on the read, from the declarations and the movements, and none of it is
 * stored anywhere. Correct a split, move a bill into another category or supersede a budget, and the
 * next read of this says something else.
 */
record MonthOfSpendingResponse(long currentAccountId, String month, LocalDate from, LocalDate until,
                               BigDecimal budgeted, BigDecimal carriedIn, BigDecimal allowed,
                               BigDecimal committed, BigDecimal discretionary, BigDecimal spent,
                               BigDecimal left, BigDecimal uncategorised,
                               List<CategorySpendingResponse> categories) {

    static MonthOfSpendingResponse of(WhatAnAccountSpentInAMonth month) {
        return new MonthOfSpendingResponse(month.currentAccountId(), month.month().toString(),
                month.from(), month.until(), month.budgeted(), month.carriedIn(), month.allowed(),
                month.committed(), month.discretionary(), month.spent(), month.left(),
                month.uncategorised(),
                month.categories().stream().map(CategorySpendingResponse::of).toList());
    }
}
