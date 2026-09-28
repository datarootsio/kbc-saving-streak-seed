package io.dataroots.savingstreak.budgets;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The spread: how a month's allowance is shared out over the weeks the month touches, and why the
 * rows always add back up to it.
 *
 * <p>A direct unit test rather than an API one, and the exception is the one
 * {@code WhenABillIsDueTest}, {@code WhenIncomeIsDueTest}, {@code HowAnAmountIsSplitTest},
 * {@link TheMonthAMomentFallsInTest} and {@link WhatCarriesIntoAMonthTest} already take: this is a
 * function of two values with no database, no clock and no HTTP in it, and its interesting cases are
 * calendars — a month that begins on a Monday, one that begins on a Sunday and so leans a single day
 * into the week before it, and one whose division by its own length does not come out in cents.
 * Driving those over HTTP would mean winding a clock onto each of them to assert arithmetic that is
 * a dozen lines. What a customer can actually observe of it — the six weekly rows summing back to
 * the month — is asserted over HTTP, where every other claim in this feature is.
 *
 * <p><strong>The claim worth stating on its own: nothing is lost and nothing is invented.</strong>
 * The weeks a month touches always add back up to the month's own figure, whatever the month's
 * length and whichever day it begins on. A spread that lost a cent would make the six-week leftover
 * — the figure this whole feature exists to produce, and the one a customer is invited to lock away
 * in savings on — wrong by an amount nobody could account for.
 *
 * <p>Covers user stories 33 and 34: a customer reading the next six weeks week by week, and each
 * week showing a figure they can check.
 */
class HowAMonthsBudgetIsSpreadOverWeeksTest {

    /**
     * February 2021: twenty-eight days beginning on a Monday, which is the one month shape that
     * falls into whole weeks with nothing hanging off either end.
     */
    private static final YearMonth FOUR_WHOLE_WEEKS = YearMonth.of(2021, 2);

    /**
     * March 2026: thirty-one days beginning on a Sunday, so the month leans one day into the week
     * that began in February and two days into the week that runs on into April.
     */
    private static final YearMonth A_DAY_EITHER_SIDE = YearMonth.of(2026, 3);

    @Test
    void a_month_of_four_whole_weeks_gives_each_of_them_a_quarter_and_nothing_is_left_to_place() {
        Map<LocalDate, BigDecimal> spread = HowAMonthsBudgetIsSpreadOverWeeks
                .acrossTheWeeksItTouches(FOUR_WHOLE_WEEKS, new BigDecimal("280.00"));

        assertThat(spread.keySet())
                .as("a month beginning on a Monday and ending on a Sunday touches four weeks and "
                        + "no others, so no week outside February is handed a cent of February's "
                        + "budget")
                .containsExactly(LocalDate.of(2021, 2, 1), LocalDate.of(2021, 2, 8),
                        LocalDate.of(2021, 2, 15), LocalDate.of(2021, 2, 22));
        assertThat(spread.values())
                .as("seven days of a twenty-eight day month is a quarter of it, four times over — "
                        + "an even spread over the days and not a guess about which of them cost "
                        + "more")
                .allSatisfy(week -> assertThat(week).isEqualByComparingTo("70.00"));
        assertThat(theTotalOf(spread)).isEqualByComparingTo("280.00");
    }

    @Test
    void a_month_hanging_off_both_ends_gives_each_week_the_days_of_it_that_week_actually_holds() {
        Map<LocalDate, BigDecimal> spread = HowAMonthsBudgetIsSpreadOverWeeks
                .acrossTheWeeksItTouches(A_DAY_EITHER_SIDE, new BigDecimal("310.00"));

        assertThat(spread.keySet())
                .as("the week beginning 23 February holds the 1st of March, and the week beginning "
                        + "30 March holds the 30th and the 31st — both are weeks this month has "
                        + "days in, so both carry a share of it")
                .containsExactly(LocalDate.of(2026, 2, 23), LocalDate.of(2026, 3, 2),
                        LocalDate.of(2026, 3, 9), LocalDate.of(2026, 3, 16),
                        LocalDate.of(2026, 3, 23), LocalDate.of(2026, 3, 30));
        assertThat(spread.get(LocalDate.of(2026, 2, 23)))
                .as("one day of a thirty-one day month at ten euros a day")
                .isEqualByComparingTo("10.00");
        assertThat(spread.get(LocalDate.of(2026, 3, 2)))
                .as("and seven of them in each whole week")
                .isEqualByComparingTo("70.00");
        assertThat(spread.get(LocalDate.of(2026, 3, 30)))
                .as("and two on the week the month runs out in")
                .isEqualByComparingTo("20.00");
        assertThat(theTotalOf(spread)).isEqualByComparingTo("310.00");
    }

    @Test
    void a_figure_that_does_not_divide_into_cents_leaves_its_remainder_on_the_last_week_the_month_touches() {
        Map<LocalDate, BigDecimal> spread = HowAMonthsBudgetIsSpreadOverWeeks
                .acrossTheWeeksItTouches(A_DAY_EITHER_SIDE, new BigDecimal("200.00"));

        assertThat(spread.get(LocalDate.of(2026, 2, 23)))
                .as("one day of two hundred euros over thirty-one days is 6.4516…, which is 6.45 "
                        + "to the cent")
                .isEqualByComparingTo("6.45");
        assertThat(spread.get(LocalDate.of(2026, 3, 2)))
                .as("and seven of them is 45.1612…, which is 45.16")
                .isEqualByComparingTo("45.16");
        assertThat(spread.get(LocalDate.of(2026, 3, 30)))
                .as("two days is 12.9032…, which is 12.90 — and the cent the six roundings lost "
                        + "lands here, on the last week the month touches, rather than being left "
                        + "somewhere nobody can find it")
                .isEqualByComparingTo("12.91");
        assertThat(theTotalOf(spread))
                .as("so the weeks add back up to the month exactly, which is the whole promise "
                        + "this function makes to the figure the forecast is built out of")
                .isEqualByComparingTo("200.00");
    }

    @Test
    void a_month_allowing_nothing_gives_every_week_it_touches_nothing() {
        Map<LocalDate, BigDecimal> spread = HowAMonthsBudgetIsSpreadOverWeeks
                .acrossTheWeeksItTouches(A_DAY_EITHER_SIDE, BigDecimal.ZERO);

        assertThat(spread.values())
                .as("a category budgeted to nothing, or one whose month is already spent to its "
                        + "limit, claims nothing in any week — and the weeks are still there, "
                        + "because a week claiming nought is a real answer and a missing week is "
                        + "not")
                .allSatisfy(week -> assertThat(week).isEqualByComparingTo("0.00"));
        assertThat(theTotalOf(spread)).isEqualByComparingTo("0.00");
    }

    @Test
    void a_month_beginning_on_a_sunday_leaves_the_week_before_it_holding_a_single_day_of_it() {
        Map<LocalDate, BigDecimal> spread = HowAMonthsBudgetIsSpreadOverWeeks
                .acrossTheWeeksItTouches(YearMonth.of(2026, 11), new BigDecimal("300.00"));

        assertThat(spread.keySet().iterator().next())
                .as("1 November 2026 is a Sunday, so the first week of November's budget is the "
                        + "week that began in October — which is exactly the week a customer "
                        + "reading their six rows on a Monday in late October is looking at")
                .isEqualTo(LocalDate.of(2026, 10, 26));
        assertThat(theTotalOf(spread)).isEqualByComparingTo("300.00");
    }

    /** What the weeks add up to, which is the claim every case here ends on. */
    private static BigDecimal theTotalOf(Map<LocalDate, BigDecimal> spread) {
        return spread.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
