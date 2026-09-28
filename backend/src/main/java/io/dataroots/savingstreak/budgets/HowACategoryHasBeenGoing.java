package io.dataroots.savingstreak.budgets;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.YearMonth;
import java.util.List;

import io.dataroots.savingstreak.deposits.AmountOfMoney;

/**
 * One spending category over the last few months: what stood over each of them, what each cost, the
 * difference, and the average of the months behind this one to judge this one against.
 *
 * <p><strong>The figure that tells a bad month from a habit.</strong> One month on its own says
 * whether a customer stayed inside a figure they chose; it cannot say whether the month was unusual,
 * and that is the judgement somebody deciding whether to change their budget actually needs. A
 * hundred and forty euros of groceries is an alarming month after three months of ninety and an
 * ordinary one after three months of a hundred and forty, and nothing but the months behind it can
 * tell the two apart.
 *
 * <p><strong>Only the months the category existed for, and never a padded nought.</strong> A
 * category declared last month has one month of history and says so. Filling the other five with
 * zeroes would be this application inventing a past in which the customer spent nothing on
 * something they had not yet named — which is not a quiet inaccuracy but exactly the wrong lesson,
 * because an average over those noughts would call every real month a spike. An ended category is
 * the same argument from the other end: it appears for the months it was live and stops, because
 * the months after it are months it was not one of the things the money went on.
 *
 * <p><strong>{@code trailingAverage} is over the months <em>before</em> this one, and it is absent
 * when there are none.</strong> Quoting this month against an average that included this month
 * would be comparing a month with itself, and the comparison would flatten by exactly the amount it
 * was supposed to reveal. A category in its very first month therefore has no average, and that is
 * reported as an absence rather than as nought — the same reading {@code SavingCapacityOnAnAccount}
 * takes of a capacity nobody declared, because an average of nothing is not a figure of nought and a
 * page drawing one would tell a customer they were spending infinitely more than usual in the first
 * month they ever recorded a spend.
 *
 * <p><strong>Three months, and it is over what was <em>spent</em> rather than over what was
 * left.</strong> Three is short enough that a change of circumstances shows in it within a quarter
 * and long enough that one unusual month does not become the trend — which is user story 40 in a
 * sentence. Spending is the thing the average has to be about because it is the only column every
 * month has: a month before the budget existed has no budget and no difference, but it has a figure
 * for what left the account, and an average that skipped those months would quietly be an average of
 * something else.
 *
 * <p>{@code monthsTheAverageIsOver} says how many months actually went into it, which is between one
 * and three and is the honest thing to print beside the figure. A customer told "EUR 92.00 on
 * average" wants to know whether that is three months or one, and a page that had to count the rows
 * itself would be working out something this module already knows.
 *
 * <p><strong>{@code comparedWithTheAverage} is this month's spending less that average</strong>, so
 * positive is a month costing more than usual and negative is one costing less. Sent rather than
 * left to whoever is drawing it, for the reason every other figure in this module is: two screens
 * subtracting it themselves would be two places this application decides what "more than usual"
 * means. It is absent exactly where the average is, and where this month is not one of the months
 * the category was live in — an ended category has no this-month to quote.
 *
 * <p>Every figure is derived on the read. There is no stored rollup, no monthly close and no job:
 * correct a spend's split in March and March's row, the average taken over it and this month's
 * comparison against that average all move together, which is the whole reason the derivation is
 * where it is.
 */
public record HowACategoryHasBeenGoing(long categoryId, String name, CategoryState categoryState,
                                       List<AMonthInTheComparison> months,
                                       AMonthInTheComparison thisMonth, BigDecimal trailingAverage,
                                       int monthsTheAverageIsOver,
                                       BigDecimal comparedWithTheAverage) {

    /**
     * How many months behind this one the average is taken over: three.
     *
     * <p><strong>A decision rather than a round number.</strong> Two months is not an average, it is
     * a pair, and either half of it moves the answer by fifty per cent; six is the whole window and
     * an average over the whole window has nothing left to compare against. Three is the shortest
     * span in which one unusual month is outvoted, and it is short enough that a customer who moved
     * house in April is not still being judged against March in September.
     *
     * <p>One named constant with its reasoning beside it, for the reason
     * {@link WhatCarriesIntoAMonth#HOW_MANY_MONTHS_A_CARRY_IS_FOLDED_OVER} is one: it is a figure a
     * training exercise might well want to change, and a literal in a loop is a figure nobody can
     * find.
     */
    static final int HOW_MANY_MONTHS_THE_TRAILING_AVERAGE_IS_OVER = 3;

    /**
     * One category's comparison, with the average and the quote against it worked out here so that
     * no caller can work them out differently.
     *
     * @param months    the months of the window this category was live in, oldest first, already
     *                  assembled — which may be fewer than the window holds and is never padded
     * @param thisMonth the month the application's clock is in, so that the row for it can be told
     *                  from the rows behind it without anybody reading a clock a second time
     */
    static HowACategoryHasBeenGoing of(ADeclaredCategory category,
                                       List<AMonthInTheComparison> months, YearMonth thisMonth) {
        AMonthInTheComparison latest = months.stream()
                .filter(row -> row.month().equals(thisMonth))
                .findFirst()
                .orElse(null);
        List<AMonthInTheComparison> behind = theMonthsBehind(months, thisMonth);
        BigDecimal average = averageOf(behind);
        return new HowACategoryHasBeenGoing(category.categoryId(), category.name(),
                category.state(), months, latest, average, behind.size(),
                average == null || latest == null
                        ? null
                        : AmountOfMoney.quotedToTheCent(latest.spent().subtract(average)));
    }

    /**
     * The months the average is taken over: the last three before this one, or as many of them as
     * the category has been alive for.
     *
     * <p>Counted off the end of the list rather than by naming months, because the list already
     * holds exactly the months this category was live in — a category declared two months ago has
     * two rows and averages over the one behind this, without anybody deciding separately which
     * months "exist".
     */
    private static List<AMonthInTheComparison> theMonthsBehind(List<AMonthInTheComparison> months,
                                                               YearMonth thisMonth) {
        List<AMonthInTheComparison> earlier = months.stream()
                .filter(row -> row.month().isBefore(thisMonth))
                .toList();
        return earlier.size() <= HOW_MANY_MONTHS_THE_TRAILING_AVERAGE_IS_OVER
                ? earlier
                : earlier.subList(earlier.size() - HOW_MANY_MONTHS_THE_TRAILING_AVERAGE_IS_OVER,
                        earlier.size());
    }

    /**
     * What those months cost on average, to the cent — or nothing at all where there are no months
     * behind this one.
     *
     * <p>Rounded half up at the last step rather than carried at full precision, so that the figure
     * a page prints is the figure {@code comparedWithTheAverage} was taken from. An average carried
     * to ten places and a difference taken from a rounded one would differ by a cent on a third of
     * all months, and a customer checking the row with a pencil would be right and the application
     * wrong.
     */
    private static BigDecimal averageOf(List<AMonthInTheComparison> months) {
        if (months.isEmpty()) {
            return null;
        }
        return months.stream()
                .map(AMonthInTheComparison::spent)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                // Rounded here and nowhere else, which is why the scale is named at the division
                // rather than by quoting the sum to the cent first and dividing an already-rounded
                // figure. The same shape {@code HowTheWeeklyMoneyIsSpent} uses when it divides what
                // a goal still needs over the weeks it has left.
                .divide(BigDecimal.valueOf(months.size()), 2, RoundingMode.HALF_UP);
    }

    /**
     * Whether there are any months behind this one to judge it against, which is what tells a
     * category with no history from one that averaged nought.
     *
     * <p>Asked here rather than by every screen comparing {@link #trailingAverage} against null, the
     * same bargain {@code SavingCapacityOnAnAccount.isDeclared} strikes: a category whose three
     * months behind it really did cost nothing has an average, and it is EUR 0.00.
     */
    public boolean hasSomethingToCompareWith() {
        return trailingAverage != null;
    }
}
