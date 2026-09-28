package io.dataroots.savingstreak.streaks;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.scheme.TheSchemeAsPublished;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The ladder itself, at the rule rather than over HTTP.
 *
 * <p>Beside {@code TheBalanceRungsTest} and {@code TheSchemeInForceOnTest}, and here for the
 * reason they are. Every other test of a run of weeks drives the whole application over HTTP, which
 * is the right shape for "a run of six weeks is paid 1,50×" and the wrong shape entirely for the
 * question this class asks: what does a ladder that is <em>not</em> the one this application is
 * seeded at pay? A flat ladder, a ladder with no step, a ladder whose figures have been through
 * SQLite — each of those would need a published version of the scheme, a customer, six weeks of
 * deposits and a wound clock to reach over HTTP, and would then assert one number.
 *
 * <p>So the arithmetic is asserted here, on the one function that decides it, and the HTTP tests go
 * on being about the rule that uses it: that a run of weeks is paid what the ladder says.
 *
 * <p><strong>Every ladder in this class is built explicitly, including the one this application
 * runs on.</strong> Reading the figures off anything would make this a test that the code equals
 * itself; they are written out, so the expectations below are a reader's arithmetic rather than the
 * application's.
 *
 * <p><strong>The first test used to compare the two forms of the rule and now names the rates
 * outright.</strong> While {@code StreakMultiplier} still held the three figures there were two
 * forms — one taking a ladder and one reading the constants — and the assertion worth having was
 * that they were the same arithmetic, which is what made thirty-three unmoved call sites safe. The
 * contract ticket deleted the constants and the form that read them, so there is one form and
 * nothing to compare it against. What is left is the claim that actually mattered underneath:
 * a run of <em>n</em> weeks on the ladder this application is seeded at pays exactly what it has
 * always paid, rung by rung and past the top.
 */
class TheLadderARunClimbsTest {

    /** The ladder this application has always paid, written out rather than read off anything. */
    private static final TheLadderARunClimbs THE_LADDER_IN_FORCE_TODAY = new TheLadderARunClimbs(
            new BigDecimal("1.00"), new BigDecimal("0.10"), new BigDecimal("1.50"));

    /**
     * Every rate the ladder this application is seeded at pays, from a run of none to well past the
     * cap, named one by one.
     *
     * <p>The claim the three deleted constants used to underwrite: nothing about what a customer is
     * paid moved when the figures became rows. Written as a table rather than as a loop over an
     * expectation computed from the ladder, because a loop that worked the answer out from the same
     * three figures the rule reads would be the rule checking itself — the point is that a reader
     * can add 0,10× up in their head thirteen times and find these numbers.
     */
    @Test
    void the_ladder_this_application_is_seeded_at_pays_what_it_has_always_paid() {
        String[] whatEachRunPays = {
                "1.00", "1.00", "1.10", "1.20", "1.30", "1.40", "1.50",
                "1.50", "1.50", "1.50", "1.50", "1.50", "1.50"};

        for (int weeks = 0; weeks < whatEachRunPays.length; weeks++) {
            assertThat(StreakMultiplier.paidByAStreakOf(weeks, THE_LADDER_IN_FORCE_TODAY))
                    .as("a run of " + weeks + " weeks")
                    .isEqualByComparingTo(whatEachRunPays[weeks]);
        }
    }

    /**
     * A run of no weeks and a run of one week pay the same, and both pay the ordinary rate.
     *
     * <p>The scheme working rather than an oversight, and the case the flooring in the arithmetic
     * exists for: the first week of a streak pays the ordinary rate by definition, so securing a
     * week for the first time shows no jump, and the promise that the deposit which crosses the line
     * is already paid at the new rate only becomes visible from the second week on.
     */
    @Test
    void a_run_of_none_and_a_run_of_one_both_pay_the_ordinary_rate() {
        assertThat(StreakMultiplier.paidByAStreakOf(0, THE_LADDER_IN_FORCE_TODAY))
                .isEqualByComparingTo("1.00");
        assertThat(StreakMultiplier.paidByAStreakOf(1, THE_LADDER_IN_FORCE_TODAY))
                .isEqualByComparingTo("1.00");
    }

    /**
     * The step applies from the second week, once per further week, and does so on a ladder that is
     * not the one this application is seeded at.
     *
     * <p>A quarter of a step, which is the figure that makes the scale argument concrete: a step of
     * 0,0250 quoted to two places on the way in would be 0,03 or 0,02, and eight weeks of it would
     * be out by a cent and a half. The climb is done at the scale the ladder was published at and
     * only the answer is put back to two places, which is why the eighth week below reads 1,18
     * rather than 1,17 or 1,24.
     */
    @Test
    void the_step_applies_from_the_second_week_and_once_for_each_week_after_it() {
        assertThat(StreakMultiplier.paidByAStreakOf(2, THE_LADDER_IN_FORCE_TODAY))
                .isEqualByComparingTo("1.10");
        assertThat(StreakMultiplier.paidByAStreakOf(3, THE_LADDER_IN_FORCE_TODAY))
                .isEqualByComparingTo("1.20");

        TheLadderARunClimbs aQuarterOfAStepAWeek = new TheLadderARunClimbs(
                new BigDecimal("1.0000"), new BigDecimal("0.0250"), new BigDecimal("2.0000"));
        assertThat(StreakMultiplier.paidByAStreakOf(1, aQuarterOfAStepAWeek))
                .isEqualByComparingTo("1.00");
        assertThat(StreakMultiplier.paidByAStreakOf(2, aQuarterOfAStepAWeek))
                .as("one further week, so one quarter-step")
                .isEqualByComparingTo("1.03");
        assertThat(StreakMultiplier.paidByAStreakOf(8, aQuarterOfAStepAWeek))
                .as("seven further weeks at 0,0250 is 0,1750, which is 1,18 as a rate")
                .isEqualByComparingTo("1.18");
    }

    /**
     * The cap is reached at the week the ladder's own arithmetic reaches it, and held for ever after.
     *
     * <p>The sixtieth week pays what the sixth does, which is the clause of the scheme a customer is
     * most likely to test and the one a handful of cases would most easily get wrong. Asserted past
     * the cap by a wide margin rather than one week past it, because "held" is the claim.
     */
    @Test
    void the_cap_is_reached_and_then_held_however_long_the_run_goes_on() {
        assertThat(StreakMultiplier.paidByAStreakOf(5, THE_LADDER_IN_FORCE_TODAY))
                .as("four further weeks, still climbing")
                .isEqualByComparingTo("1.40");
        assertThat(StreakMultiplier.paidByAStreakOf(6, THE_LADDER_IN_FORCE_TODAY))
                .as("five further weeks, exactly at the cap")
                .isEqualByComparingTo("1.50");
        assertThat(StreakMultiplier.paidByAStreakOf(7, THE_LADDER_IN_FORCE_TODAY))
                .isEqualByComparingTo("1.50");
        assertThat(StreakMultiplier.paidByAStreakOf(60, THE_LADDER_IN_FORCE_TODAY))
                .isEqualByComparingTo("1.50");
        assertThat(StreakMultiplier.paidByAStreakOf(Integer.MAX_VALUE, THE_LADDER_IN_FORCE_TODAY))
                .as("a run longer than anybody will ever have is still only worth the cap")
                .isEqualByComparingTo("1.50");
    }

    /**
     * A cap equal to the ordinary rate is a flat scheme, and a legitimate one.
     *
     * <p>A bank that wants to pay one point per euro and reward nothing else publishes exactly this,
     * and it needs no special case in the arithmetic: the climb never passes a cap it starts at. The
     * test is here because a rule written as a handful of cases rather than as a function would very
     * likely have got it wrong, and because ticket 03's door has no reason to refuse it.
     */
    @Test
    void a_cap_equal_to_the_ordinary_rate_is_a_flat_scheme_and_pays_the_same_at_every_run() {
        TheLadderARunClimbs flat = new TheLadderARunClimbs(
                new BigDecimal("1.0000"), new BigDecimal("0.1000"), new BigDecimal("1.0000"));
        for (int weeks : new int[] {0, 1, 2, 6, 60}) {
            assertThat(StreakMultiplier.paidByAStreakOf(weeks, flat))
                    .as("a run of " + weeks + " weeks on a ladder that caps where it starts")
                    .isEqualByComparingTo("1.00");
        }
    }

    /**
     * A step of nought is the same flat scheme said the other way round, and is equally legitimate.
     *
     * <p>Worth its own case because it reaches the arithmetic differently: nothing is capped here,
     * the climb simply never climbs. A scheme that published a generous cap and no step would pay
     * the ordinary rate for ever, and saying so here is what stops somebody "fixing" the flooring or
     * the cap into a minimum of one step.
     */
    @Test
    void a_step_of_nought_climbs_nowhere_however_generous_the_cap() {
        TheLadderARunClimbs noStep = new TheLadderARunClimbs(
                new BigDecimal("1.2500"), new BigDecimal("0.0000"), new BigDecimal("3.0000"));
        for (int weeks : new int[] {0, 1, 2, 6, 60}) {
            assertThat(StreakMultiplier.paidByAStreakOf(weeks, noStep))
                    .as("a run of " + weeks + " weeks on a ladder with no step")
                    .isEqualByComparingTo("1.25");
        }
    }

    /**
     * A ladder whose figures have been through SQLite still pays a rate written to two places.
     *
     * <p>SQLite has no decimal type and holds a figure as a float, so {@code 1.5000} comes back as
     * {@code 1.5} and {@code 1.0000} as {@code 1} — which is exactly the shape a rate arrives in
     * when it has been round-tripped, and exactly why {@code StreakMultiplier.asARate} exists. The
     * ladder keeps whatever scale it was handed, deliberately, so the defence has to be at the
     * answer; this asserts on the scale itself and not only on the value, because a rate that reads
     * {@code 1.5} in a deposit's history and {@code 1.50} in the answer to the deposit is the bug
     * that method was written for.
     */
    @Test
    void two_place_rounding_survives_a_round_trip_through_sqlites_float() {
        TheLadderARunClimbs offAFloat = new TheLadderARunClimbs(
                asItComesBackOffAFloat(1.0d),
                asItComesBackOffAFloat(0.1d),
                asItComesBackOffAFloat(1.5d));

        assertThat(StreakMultiplier.paidByAStreakOf(1, offAFloat).toPlainString())
                .isEqualTo("1.00");
        assertThat(StreakMultiplier.paidByAStreakOf(2, offAFloat).toPlainString())
                .isEqualTo("1.10");
        assertThat(StreakMultiplier.paidByAStreakOf(6, offAFloat).toPlainString())
                .as("the cap, off a float that dropped its trailing zeroes, still reads as a rate")
                .isEqualTo("1.50");
        assertThat(StreakMultiplier.paidByAStreakOf(60, offAFloat).toPlainString())
                .isEqualTo("1.50");

        for (int weeks = 0; weeks <= 12; weeks++) {
            assertThat(StreakMultiplier.paidByAStreakOf(weeks, offAFloat).scale())
                    .as("a rate is quoted to two places at a run of " + weeks + " weeks")
                    .isEqualTo(2);
        }
    }

    /**
     * The ladder a published version of the scheme describes is the ladder built out of its three
     * multiples, and the accessors are not transposed.
     *
     * <p>A small test of a small factory, and worth having because the mistake it guards is silent:
     * a ladder assembled with the step and the cap the wrong way round is arithmetic that runs
     * perfectly well and pays nonsense, and this is the one place in the application that assembly
     * happens.
     */
    @Test
    void the_ladder_in_a_published_scheme_is_its_three_multiples_in_the_order_they_are_named() {
        TheLadderARunClimbs ladder = TheLadderARunClimbs.theLadderIn(new TheSchemeAsPublished(
                1, LocalDate.of(2020, 1, 6), new BigDecimal("50.00"),
                new BigDecimal("1.0000"), new BigDecimal("0.1000"), new BigDecimal("1.5000"), 12,
                List.of(new BigDecimal("100.00")), new BigDecimal("80.00"), 3, 30, 30,
                "what the scheme has always said"));

        assertThat(ladder.theOrdinaryRate()).isEqualByComparingTo("1.0000");
        assertThat(ladder.extraForEachFurtherWeek()).isEqualByComparingTo("0.1000");
        assertThat(ladder.theMostAStreakPays()).isEqualByComparingTo("1.5000");
        assertThat(StreakMultiplier.paidByAStreakOf(6, ladder)).isEqualByComparingTo("1.50");
    }

    /**
     * A negative run is refused, on the stated ladder as it always was on the constants.
     *
     * <p>Nobody types a run of weeks, so a negative one can only be a mistake in the derivation, and
     * a rate quietly computed from it would be a rate below the ordinary one that nothing would ever
     * explain.
     */
    @Test
    void a_negative_run_of_weeks_is_refused_rather_than_priced() {
        assertThatThrownBy(() -> StreakMultiplier.paidByAStreakOf(-1, THE_LADDER_IN_FORCE_TODAY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("-1");
    }

    /**
     * A figure as SQLite hands it back: through a {@code double} and into a {@link BigDecimal} by
     * its shortest decimal spelling, which is what drops {@code 1.5000} to {@code 1.5}.
     */
    private static BigDecimal asItComesBackOffAFloat(double figure) {
        return new BigDecimal(Double.toString(figure));
    }
}
