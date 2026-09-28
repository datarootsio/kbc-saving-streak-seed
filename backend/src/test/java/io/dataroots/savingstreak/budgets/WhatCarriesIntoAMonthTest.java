package io.dataroots.savingstreak.budgets;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.budgets.RolloverRule.NOTHING_ROLLS_OVER;
import static io.dataroots.savingstreak.budgets.RolloverRule.THE_SURPLUS_AND_THE_OVERSPEND_ROLL_OVER;
import static io.dataroots.savingstreak.budgets.RolloverRule.THE_SURPLUS_ROLLS_OVER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The carry: what one category's earlier months hand on to a later one, under each of the three
 * rules a customer can choose between.
 *
 * <p>A direct unit test rather than an API one, and the exception is the one
 * {@code WhenABillIsDueTest}, {@code WhenIncomeIsDueTest}, {@code HowAnAmountIsSplitTest} and
 * {@link TheMonthAMomentFallsInTest} already take: this is a function of its arguments with no
 * database, no clock and no HTTP in it, and its interesting cases are months of a customer's life.
 * Three rules run over three months is nine months of spending and two years of winding for the
 * bound — an afternoon of round trips otherwise, to assert arithmetic that is a dozen lines. What a
 * customer can actually observe of all this is asserted over HTTP, where every other claim in this
 * feature is.
 *
 * <p><strong>The three rules are asserted against each other on the same figures</strong>, which is
 * the whole design of the first test here. A rule is only meaningful by comparison: "the surplus
 * rolls over" says nothing on its own, and three separate tests with three sets of amounts could all
 * pass against a fold that had quietly collapsed two of the rules into one. One set of months, one
 * set of spends, three answers that differ in exactly the way the customer was promised.
 *
 * <p>Covers user stories 24, 25, 26 and 27: unspent budget carrying when the customer says so,
 * overspend following them when they say so, a category that carries nothing starting every month
 * clean, and a rule that applies from the month it was named in rather than backwards.
 */
class WhatCarriesIntoAMonthTest {

    /** The category every case here is about. Carried for the log, and the log is the point of it. */
    private static final long GROCERIES = 7L;

    private static final YearMonth FIRST = YearMonth.of(2026, 1);

    private static final YearMonth SECOND = FIRST.plusMonths(1);

    private static final YearMonth THIRD = FIRST.plusMonths(2);

    /**
     * One frugal month, one overspent month, and a third to read the answer in. The figures are the
     * same under all three rules and only the rule changes, so every difference below is the rule
     * doing its work.
     */
    private static Map<YearMonth, BigDecimal> aFrugalMonthThenAnOverspentOne() {
        Map<YearMonth, BigDecimal> cost = new HashMap<>();
        cost.put(FIRST, new BigDecimal("60.00"));
        cost.put(SECOND, new BigDecimal("160.00"));
        return cost;
    }

    /** The same figure under the same rule in every month of a stretch, which is the ordinary case. */
    private static Map<YearMonth, ABudgetThatStood> oneHundredAMonth(RolloverRule rule,
                                                                     YearMonth from, int months) {
        Map<YearMonth, ABudgetThatStood> stood = new HashMap<>();
        for (int month = 0; month < months; month++) {
            stood.put(from.plusMonths(month), new ABudgetThatStood(new BigDecimal("100.00"), rule));
        }
        return stood;
    }

    @Test
    void nothing_rolling_over_starts_every_month_clean_whatever_happened_in_the_last_one() {
        Map<YearMonth, ABudgetThatStood> stood = oneHundredAMonth(NOTHING_ROLLS_OVER, FIRST, 3);
        Map<YearMonth, BigDecimal> cost = aFrugalMonthThenAnOverspentOne();

        assertThat(WhatCarriesIntoAMonth.carriedInto(GROCERIES, SECOND, stood, cost))
                .as("forty euros were left in January and the customer asked for none of it to "
                        + "follow them, so February begins on its own hundred")
                .isEqualByComparingTo("0.00");
        assertThat(WhatCarriesIntoAMonth.carriedInto(GROCERIES, THIRD, stood, cost))
                .as("and February went sixty over, which March is not made to pay for either — "
                        + "choosing not to be haunted by January is choosing both ways")
                .isEqualByComparingTo("0.00");
    }

    @Test
    void the_surplus_rolling_over_makes_a_frugal_month_buy_a_generous_one_and_forgives_the_overspend() {
        Map<YearMonth, ABudgetThatStood> stood = oneHundredAMonth(THE_SURPLUS_ROLLS_OVER, FIRST, 3);
        Map<YearMonth, BigDecimal> cost = aFrugalMonthThenAnOverspentOne();

        assertThat(WhatCarriesIntoAMonth.carriedInto(GROCERIES, SECOND, stood, cost))
                .as("a hundred allowed and sixty spent leaves forty, and the customer asked for it")
                .isEqualByComparingTo("40.00");
        assertThat(WhatCarriesIntoAMonth.carriedInto(GROCERIES, THIRD, stood, cost))
                .as("February was allowed a hundred and forty and cost a hundred and sixty, so it "
                        + "has nothing to hand on — and under this rule it hands on nothing rather "
                        + "than handing on a debt, which is the whole difference from an envelope")
                .isEqualByComparingTo("0.00");
    }

    @Test
    void an_envelope_carries_the_overspend_too_and_hands_the_next_month_a_negative_figure() {
        Map<YearMonth, ABudgetThatStood> stood =
                oneHundredAMonth(THE_SURPLUS_AND_THE_OVERSPEND_ROLL_OVER, FIRST, 3);
        Map<YearMonth, BigDecimal> cost = aFrugalMonthThenAnOverspentOne();

        assertThat(WhatCarriesIntoAMonth.carriedInto(GROCERIES, SECOND, stood, cost))
                .as("the surplus carries exactly as it does under the forgiving rule, because the "
                        + "two rules only part company on a month that went over")
                .isEqualByComparingTo("40.00");
        assertThat(WhatCarriesIntoAMonth.carriedInto(GROCERIES, THIRD, stood, cost))
                .as("February was allowed a hundred and forty and cost a hundred and sixty, so "
                        + "March begins twenty euros down — an envelope you overfill this month is "
                        + "an envelope with less in it next month, and that consequence is the "
                        + "whole reason anybody keeps one")
                .isEqualByComparingTo("-20.00");
    }

    @Test
    void the_three_rules_answer_differently_about_one_month_on_one_set_of_figures() {
        Map<YearMonth, BigDecimal> cost = aFrugalMonthThenAnOverspentOne();

        BigDecimal nothing = WhatCarriesIntoAMonth.carriedInto(GROCERIES, THIRD,
                oneHundredAMonth(NOTHING_ROLLS_OVER, FIRST, 3), cost);
        BigDecimal surplus = WhatCarriesIntoAMonth.carriedInto(GROCERIES, THIRD,
                oneHundredAMonth(THE_SURPLUS_ROLLS_OVER, FIRST, 3), cost);
        BigDecimal envelope = WhatCarriesIntoAMonth.carriedInto(GROCERIES, THIRD,
                oneHundredAMonth(THE_SURPLUS_AND_THE_OVERSPEND_ROLL_OVER, FIRST, 3), cost);

        assertThat(nothing)
                .as("the same months, the same spending, and the only thing that differs is what "
                        + "the customer asked for")
                .isEqualByComparingTo(surplus);
        assertThat(envelope)
                .as("and the envelope is the one rule under which a month can begin with less than "
                        + "its budget, which is why it is the only one of the three that is worth "
                        + "the word")
                .isLessThan(nothing);
    }

    @Test
    void a_surplus_carries_month_after_month_until_the_customer_spends_it() {
        Map<YearMonth, ABudgetThatStood> stood = oneHundredAMonth(THE_SURPLUS_ROLLS_OVER, FIRST, 4);
        Map<YearMonth, BigDecimal> cost = new HashMap<>();
        cost.put(FIRST, new BigDecimal("70.00"));
        cost.put(SECOND, new BigDecimal("80.00"));
        cost.put(THIRD, new BigDecimal("90.00"));

        assertThat(WhatCarriesIntoAMonth.carriedInto(GROCERIES, FIRST.plusMonths(3), stood, cost))
                .as("thirty, then twenty on top of it, then ten on top of that: three frugal "
                        + "months buying a generous fourth, which is the whole of user story 24")
                .isEqualByComparingTo("60.00");
    }

    @Test
    void an_envelope_that_was_overfilled_digs_itself_out_again_over_the_months_after_it() {
        Map<YearMonth, ABudgetThatStood> stood =
                oneHundredAMonth(THE_SURPLUS_AND_THE_OVERSPEND_ROLL_OVER, FIRST, 4);
        Map<YearMonth, BigDecimal> cost = new HashMap<>();
        cost.put(FIRST, new BigDecimal("250.00"));
        cost.put(SECOND, new BigDecimal("40.00"));
        cost.put(THIRD, new BigDecimal("40.00"));

        assertThat(WhatCarriesIntoAMonth.carriedInto(GROCERIES, SECOND, stood, cost))
                .as("a hundred and fifty over is a hundred and fifty carried")
                .isEqualByComparingTo("-150.00");
        assertThat(WhatCarriesIntoAMonth.carriedInto(GROCERIES, THIRD, stood, cost))
                .as("February was allowed minus fifty and spent forty, so it is still ninety down")
                .isEqualByComparingTo("-90.00");
        assertThat(WhatCarriesIntoAMonth.carriedInto(GROCERIES, FIRST.plusMonths(3), stood, cost))
                .as("and a third careful month brings it back to thirty down, which is an envelope "
                        + "doing exactly what it was chosen for")
                .isEqualByComparingTo("-30.00");
    }

    @Test
    void a_rule_named_this_month_leaves_the_months_before_it_folding_under_the_rule_that_stood() {
        Map<YearMonth, ABudgetThatStood> stood = new HashMap<>();
        stood.put(FIRST, new ABudgetThatStood(new BigDecimal("100.00"), NOTHING_ROLLS_OVER));
        stood.put(SECOND, new ABudgetThatStood(new BigDecimal("100.00"), NOTHING_ROLLS_OVER));
        stood.put(THIRD,
                new ABudgetThatStood(new BigDecimal("100.00"), THE_SURPLUS_AND_THE_OVERSPEND_ROLL_OVER));
        Map<YearMonth, BigDecimal> cost = new HashMap<>();
        cost.put(FIRST, new BigDecimal("10.00"));
        cost.put(SECOND, new BigDecimal("10.00"));

        assertThat(WhatCarriesIntoAMonth.carriedInto(GROCERIES, THIRD, stood, cost))
                .as("the customer turned this category into an envelope in March; they did not say "
                        + "that January and February had been envelopes all along, so the ninety "
                        + "each of them left is not waiting for them")
                .isEqualByComparingTo("0.00");
    }

    @Test
    void a_month_nobody_was_budgeting_carries_nothing_out_of_itself() {
        Map<YearMonth, ABudgetThatStood> stood = new HashMap<>();
        stood.put(FIRST, new ABudgetThatStood(new BigDecimal("100.00"), THE_SURPLUS_ROLLS_OVER));
        stood.put(THIRD, new ABudgetThatStood(new BigDecimal("100.00"), THE_SURPLUS_ROLLS_OVER));
        Map<YearMonth, BigDecimal> cost = new HashMap<>();
        cost.put(FIRST, new BigDecimal("20.00"));

        assertThat(WhatCarriesIntoAMonth.carriedInto(GROCERIES, SECOND, stood, cost))
                .as("January left eighty and handed it on, because January had a figure on it")
                .isEqualByComparingTo("80.00");
        assertThat(WhatCarriesIntoAMonth.carriedInto(GROCERIES, THIRD, stood, cost))
                .as("but February was a month its holder had stopped policing, and a surplus "
                        + "cannot survive a month in which nobody was being measured against "
                        + "anything — the chain begins again at the next figure they name")
                .isEqualByComparingTo("0.00");
    }

    @Test
    void a_category_no_figure_has_ever_stood_on_carries_nothing() {
        assertThat(WhatCarriesIntoAMonth.carriedInto(GROCERIES, THIRD, Map.of(), Map.of()))
                .as("there is nothing to have been left of a limit nobody set, which is the same "
                        + "reading the month itself takes of an absent budget")
                .isEqualByComparingTo("0.00");
    }

    @Test
    void a_budget_that_took_effect_thirty_months_ago_is_folded_from_the_bound_rather_than_the_start() {
        YearMonth now = YearMonth.of(2028, 7);
        YearMonth thirtyMonthsAgo = now.minusMonths(30);
        Map<YearMonth, ABudgetThatStood> stood =
                oneHundredAMonth(THE_SURPLUS_ROLLS_OVER, thirtyMonthsAgo, 31);

        assertThat(WhatCarriesIntoAMonth.carriedInto(GROCERIES, now, stood, Map.of()))
                .as("a hundred a month, none of it spent, folded over the twenty-four months the "
                        + "bound allows rather than the thirty the rows would support — beyond it "
                        + "the carry is taken as nought rather than approximated")
                .isEqualByComparingTo("2400.00");
    }

    @Test
    void a_budget_that_took_effect_exactly_at_the_bound_is_folded_in_full() {
        YearMonth now = YearMonth.of(2028, 7);
        YearMonth atTheBound =
                now.minusMonths(WhatCarriesIntoAMonth.HOW_MANY_MONTHS_A_CARRY_IS_FOLDED_OVER);
        Map<YearMonth, ABudgetThatStood> stood =
                oneHundredAMonth(THE_SURPLUS_ROLLS_OVER, atTheBound, 25);

        assertThat(WhatCarriesIntoAMonth.carriedInto(GROCERIES, now, stood, Map.of()))
                .as("the bound is where the fold starts and not where it stops one short: "
                        + "twenty-four months of a hundred is what the customer is owed")
                .isEqualByComparingTo("2400.00");
    }

    @Test
    void the_month_being_read_is_folded_into_rather_than_over() {
        Map<YearMonth, ABudgetThatStood> stood = oneHundredAMonth(THE_SURPLUS_ROLLS_OVER, FIRST, 2);
        Map<YearMonth, BigDecimal> cost = Map.of(FIRST, new BigDecimal("25.00"));

        assertThat(WhatCarriesIntoAMonth.carriedInto(GROCERIES, FIRST, stood, cost))
                .as("nothing carries into the first month a figure ever stood in, however little "
                        + "was spent in it — a month does not hand anything on to itself")
                .isEqualByComparingTo("0.00");
        assertThat(WhatCarriesIntoAMonth.carriedInto(GROCERIES, SECOND, stood, cost))
                .as("and what it handed on arrives in the month after it")
                .isEqualByComparingTo("75.00");
    }
}
