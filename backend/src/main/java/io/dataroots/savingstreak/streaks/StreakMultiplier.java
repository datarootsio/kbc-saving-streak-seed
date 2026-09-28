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
 * <p><strong>The three figures used to be constants here and are now an argument.</strong> They were
 * constants for a good reason — a training exercise that asks a participant to reprice the scheme
 * should have one file to change, and a figure repeated at each place it is used is a rule that can
 * half-change — and the reason has not gone away, it has moved: the scheme is now published as rows,
 * in versions, with a date, and the one file to change is a form rather than this class. So
 * {@link #paidByAStreakOf(int, TheLadderARunClimbs)} is handed {@link TheLadderARunClimbs}, built
 * from the version of the scheme that was in force in the week being priced, and the arithmetic
 * below is all that stayed behind. The figures themselves survive as what version 1 of the scheme is
 * seeded at, which is why nothing moved on the morning of the upgrade.
 *
 * <p><strong>Every caller prices a run against a ladder it names.</strong> The derivation builds the
 * ladder from the version in force in the week it is reading and hands it to
 * {@link StreakOfSecuredWeeks}, which is the one place a run turns into a rate; the simulator's fold
 * builds one per week of the year it is imagining, off the history its snapshot carries; and the
 * projection on the products shelf, which belongs to nobody and is priced at the rate a deposit
 * outside any run is paid, asks the scheme in force today for that rate rather than reading it here.
 *
 * <p><strong>The three constants and the one-argument form are gone, and their going is a decision
 * rather than a tidy-up.</strong> That form priced a run at whatever the ladder happened to be
 * today, which is the right answer only for a week that is happening today — and a run spans weeks.
 * {@code LoyaltyRate} has been through this migration already and records what it cost to finish: a
 * bridge nobody removes is how two rates start disagreeing. Left standing, it would have gone on
 * quietly paying 1,50× to some caller nobody thought about after the bank published a cap of 1,40×,
 * and nothing anywhere would have objected, because 1,50× is a perfectly good cap. So the ladder is
 * an argument with no default and the compiler is what makes every caller name one.
 *
 * <p>The warning the constants carried is now a warning about the argument, and it is sharper for
 * it: there is still exactly one place a run of weeks is priced, and a caller that worked out its
 * own ordinary rate, added its own step or capped the answer itself would be the second place the
 * ladder is decided.
 */
public final class StreakMultiplier {

    /** A rate is quoted to two places, the way 1.10× is written rather than 1.1×. */
    private static final int DECIMAL_PLACES = 2;

    private StreakMultiplier() {
    }

    /**
     * The rate a run of that many consecutive secured weeks pays on the stated ladder: the ordinary
     * rate for none or one, a step more for each further week, and never more than the cap.
     *
     * <p>The whole of the arithmetic, and the only copy of it. The flooring at the first week, the
     * climb and the cap are exactly what they were when the three figures were constants beside
     * them; what changed is where the figures come from, and a caller that worked out any part of
     * this itself would be the second place the ladder is decided.
     *
     * <p><strong>Rounded once, at the end.</strong> The ladder's figures arrive at whatever scale
     * the scheme published them at — four places, because a step of 0,0250 is a real step and two
     * places would flatten it — and the climb is done at that scale so that the eleventh week of a
     * finely-quoted ladder is the figure the scheme actually describes. Only the answer is put back
     * to the two places a rate is written at, by {@link #asARate}, which is also what puts right a
     * figure that came back off SQLite's float carrying whatever scale it kept.
     *
     * <p><strong>A flat ladder is a ladder.</strong> A cap equal to the ordinary rate, or a step of
     * nought, is a scheme that pays the same for every run — legitimate, publishable, and needing no
     * special case here: the climb never passes a cap it starts at, and adding nought climbs
     * nowhere.
     *
     * @param weeks  how many consecutive weeks the run is
     * @param ladder what the first week pays, what a further week adds and where it stops — from
     *               {@link TheLadderARunClimbs#theLadderIn}, off the version of the scheme in force
     *               in the week being priced, never off today's
     * @throws IllegalArgumentException if asked about a negative number of weeks, which nobody types
     *                                  and so can only be a mistake in the derivation
     */
    public static BigDecimal paidByAStreakOf(int weeks, TheLadderARunClimbs ladder) {
        if (weeks < 0) {
            throw new IllegalArgumentException(
                    "a run of weeks is never negative, and this one was " + weeks);
        }
        // Weeks beyond the first, floored at none so that a run of nothing and a run of one week are
        // one case rather than two: both are somebody's first week, and both pay the ordinary rate.
        int weeksBeyondTheFirst = Math.max(weeks - 1, 0);
        BigDecimal climbed = ladder.theOrdinaryRate().add(
                ladder.extraForEachFurtherWeek().multiply(BigDecimal.valueOf(weeksBeyondTheFirst)));
        return asARate(climbed.min(ladder.theMostAStreakPays()));
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
