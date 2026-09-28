package io.dataroots.savingstreak.budgets;

import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * One current account's last few months, category by category: what each was allowed to cost in
 * each of them, what each cost, and the average of the months behind this one to judge this one
 * against.
 *
 * <p><strong>The answer to "is this a bad month or is this what I do".</strong>
 * {@link WhatAnAccountSpentInAMonth} says how one month is going and cannot say any more than that;
 * a customer looking at a hundred and forty euros of groceries wants to know what the last few
 * months cost, and that is a question about a stretch rather than about a month.
 *
 * <p><strong>Six months, ending with the one the clock is in.</strong> Six because it is two
 * quarters — long enough to hold a seasonal story a customer would recognise, short enough that
 * every row on it is a month they can still remember — and because six columns fit on a screen
 * beside a category's name. {@link #HOW_MANY_MONTHS_ARE_COMPARED} is where that trade is argued.
 *
 * <p><strong>{@code months} is the window, and a category's own rows may be fewer.</strong> The
 * window is the same for every category on the account, so a page can draw one set of column
 * headings; a category younger than the window has only the months it existed for and an ended one
 * stops at the month it was ended in, because padding either end with noughts would invent months in
 * which a customer spent nothing on something that was not one of the things their money went on.
 * Drawing the two together is the page's job and it has everything it needs to do it.
 *
 * <p><strong>A category with nothing in the window at all is not in the answer.</strong> One ended
 * before the window began is a record of months this read is not about, and a row of six blanks is a
 * row that says nothing at the cost of the space a real one would have taken.
 *
 * <p>Every figure is derived on every read from the declarations and the movements. There is no
 * rollup table, no monthly close and no job — the same decision the whole module is shaped by, and
 * the reason a correction to a March split moves March's row, the average taken over it, and this
 * month's comparison against that average, all at once.
 */
public record HowAnAccountHasBeenGoing(long currentAccountId, YearMonth month, YearMonth earliest,
                                       List<YearMonth> months,
                                       List<HowACategoryHasBeenGoing> categories) {

    /**
     * How many months a category is compared over: six, ending with the month the clock is in.
     *
     * <p><strong>A decision rather than a round number.</strong> Three months is the average itself
     * and leaves nothing to draw the average against; a year is a row a customer cannot read across
     * and a stretch in which a change of job, house or household makes the early months an argument
     * about somebody else. Six spans two quarters, which is long enough for a seasonal story — a
     * heating bill that climbs from October, a summer of eating out — and short enough that every
     * month in it is one its holder can still account for.
     *
     * <p>Named here with its reasoning beside it, for the reason
     * {@link WhatCarriesIntoAMonth#HOW_MANY_MONTHS_A_CARRY_IS_FOLDED_OVER} is named: it is exactly
     * the sort of figure a training exercise changes, and a literal in a loop is a figure nobody can
     * find.
     */
    static final int HOW_MANY_MONTHS_ARE_COMPARED = 6;

    /**
     * The first month the comparison covers, which is five months behind the one being read.
     *
     * <p>Exposed rather than worked out at the call site so that the window the rows are read over
     * and the window the answer is drawn from are the same window. Two places subtracting five would
     * be two places holding the six, and the day they disagreed the read would either fold months it
     * never drew or draw months it never read.
     */
    static YearMonth theEarliestMonthCompared(YearMonth month) {
        return month.minusMonths(HOW_MANY_MONTHS_ARE_COMPARED - 1L);
    }

    /**
     * The account's comparison with its window spelled out, so that a page never has to work out
     * which six months it is looking at.
     *
     * @param month      the month the application's clock is in, which the window ends with
     * @param categories one row per category that the window holds anything for, in declaration
     *                   order — the order the account's own category list is already drawn in
     */
    static HowAnAccountHasBeenGoing of(long currentAccountId, YearMonth month,
                                       List<HowACategoryHasBeenGoing> categories) {
        YearMonth earliest = theEarliestMonthCompared(month);
        List<YearMonth> window = new ArrayList<>();
        for (YearMonth walking = earliest; !walking.isAfter(month); walking = walking.plusMonths(1)) {
            window.add(walking);
        }
        return new HowAnAccountHasBeenGoing(currentAccountId, month, earliest,
                List.copyOf(window), categories);
    }
}
