package io.dataroots.savingstreak.streaks;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * What a run of consecutive secured weeks pays per whole euro paid in — the ladder, and the only
 * place it is written down.
 *
 * <p>One function of one number: how many consecutive weeks the run is. A run of no weeks and a run
 * of one week both pay the ordinary rate, and every further week adds a step until the ladder stops
 * climbing. Written as a function rather than as a handful of cases, because every clause of the
 * scheme falls out of it — the first week of a streak paying the ordinary rate, the sixth and the
 * sixtieth paying the same, a deposit that has just secured its week being paid at the rate of the
 * run that now includes that week — and four rules a reader has to reconcile is not the same thing as
 * one rule a reader can check.
 *
 * <p>The step and the cap are constants here rather than literals in the arithmetic. A training
 * exercise that asks a participant to reprice the scheme should have one file to change, and a figure
 * repeated at each place it is used is a rule that can half-change.
 */
public final class StreakMultiplier {

    /**
     * What a deposit earns per whole euro when there is no run behind it, and what the first week of
     * a run earns too.
     *
     * <p>The two being the same is the scheme working rather than an oversight: the first week of a
     * streak pays the ordinary rate by definition, so securing a week for the first time shows no
     * jump, and the promise that "the deposit which crosses the line is already paid at the new rate"
     * only becomes visible from the second week on. It is also what every deposit made before this
     * scheme existed was in fact paid.
     */
    public static final BigDecimal THE_ORDINARY_RATE = new BigDecimal("1.00");

    /** What each further consecutive secured week adds, until the ladder reaches its cap. */
    public static final BigDecimal EXTRA_FOR_EACH_FURTHER_WEEK = new BigDecimal("0.10");

    /**
     * The most a run ever pays, reached in the sixth consecutive secured week and held from there
     * on: a scheme a customer can understand and the bank can afford.
     */
    public static final BigDecimal THE_MOST_A_STREAK_PAYS = new BigDecimal("1.50");

    /** A rate is quoted to two places, the way 1.10× is written rather than 1.1×. */
    private static final int DECIMAL_PLACES = 2;

    private StreakMultiplier() {
    }

    /**
     * The rate a run of that many consecutive secured weeks pays: the ordinary rate for none or one,
     * a step more for each further week, and never more than the cap.
     *
     * @throws IllegalArgumentException if asked about a negative number of weeks, which nobody types
     *                                  and so can only be a mistake in the derivation
     */
    public static BigDecimal paidByAStreakOf(int weeks) {
        if (weeks < 0) {
            throw new IllegalArgumentException(
                    "a run of weeks is never negative, and this one was " + weeks);
        }
        // Weeks beyond the first, floored at none so that a run of nothing and a run of one week are
        // one case rather than two: both are somebody's first week, and both pay the ordinary rate.
        int weeksBeyondTheFirst = Math.max(weeks - 1, 0);
        BigDecimal climbed = THE_ORDINARY_RATE.add(
                EXTRA_FOR_EACH_FURTHER_WEEK.multiply(BigDecimal.valueOf(weeksBeyondTheFirst)));
        return asARate(climbed.min(THE_MOST_A_STREAK_PAYS));
    }

    /**
     * A rate written the way a rate is written, to two places.
     *
     * <p>Here for the same reason money has one place that decides how many places it has: a rate
     * that has been through the database comes back carrying whatever scale SQLite kept — it has no
     * decimal type and holds the figure as a float — so 1.50 arrives as 1.5, and the rate a deposit
     * reports having been paid at would read differently in its history from the way it read in the
     * answer to the deposit itself.
     */
    public static BigDecimal asARate(BigDecimal rate) {
        return rate.setScale(DECIMAL_PLACES, RoundingMode.HALF_UP);
    }
}
