package io.dataroots.savingstreak.streaks;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * How much new saving has landed in a week, and what the week still asks for.
 *
 * <p>Gross, and that is the rule this record carries: it is what was paid in, never what is left of
 * it. A withdrawal takes money back out of the account without un-happening the deposit it came out
 * of, so a week that took EUR 60 in and let EUR 20 back out has still taken EUR 60 in. Read net, a
 * Thursday withdrawal would retroactively un-secure a week that was already secured on Tuesday,
 * which is either a bonus clawed back or a ledger disagreeing with the streak derived from it.
 *
 * <p>Nothing here is stored. The figure is summed from the deposit records every time it is asked
 * for, which is what lets the development clock be wound in either direction without leaving a
 * total behind describing a week that is now in the future.
 */
public record NewSavingsThisWeek(SavingsWeek week, BigDecimal newSavings) {

    /**
     * What a week has to take in to count towards a streak. The one place the figure is written
     * down: a training exercise that asks a participant to change what a week costs should have one
     * line to change, and a literal repeated at each comparison is a rule that can half-change.
     */
    public static final BigDecimal WEEKLY_MINIMUM = new BigDecimal("50.00");

    /**
     * Euros are quoted to the cent, and a figure that came back off a float has to be put back. This
     * record's own: the amounts it is summed from arrive quoted by the module that holds them, and
     * this is only the scale the total and what is still needed are written in.
     */
    private static final int DECIMAL_PLACES = 2;

    public NewSavingsThisWeek {
        // To the cent on the way in, because the terms came out of SQLite, which has no decimal type
        // and hands an amount back as a float: EUR 12.50 arrives as 12.5, and a sum of those carries
        // whatever scale the last term happened to have. Money is compared by value either way; this
        // is so that the figure reads as money wherever it is written down.
        newSavings = newSavings.setScale(DECIMAL_PLACES, RoundingMode.HALF_UP);
    }

    /**
     * What the week asks for, carried alongside the progress towards it so that whoever shows the
     * one shows the other without naming the figure itself. A screen that wrote "of EUR 50" into its
     * own markup would be the second place the minimum lives.
     */
    public BigDecimal weeklyMinimum() {
        return WEEKLY_MINIMUM;
    }

    /**
     * Whether the week has taken in what it asks for, and so counts towards a streak.
     *
     * <p>The comparison lives beside the figure being compared against, because "at least EUR 50 has
     * landed in it" is the whole definition of a secured week and a caller restating it is a second
     * place the threshold is decided. At least, not more than: a week that took in exactly the
     * minimum has done what the week asked.
     */
    public boolean isSecured() {
        return securedBy(newSavings);
    }

    /**
     * The same question about a week this record was not made for: whether that much gross new
     * saving secures a week.
     *
     * <p>Here because whoever walks back through an account's earlier weeks has a total per week and
     * no reason to build a record around each one. Static so that there is still exactly one
     * comparison against {@link #WEEKLY_MINIMUM} in the application.
     */
    public static boolean securedBy(BigDecimal grossNewSavings) {
        return grossNewSavings.compareTo(WEEKLY_MINIMUM) >= 0;
    }

    /**
     * How much more the week needs, and nothing below zero: a week that has taken EUR 80 in needs no
     * more, and reporting that it needs minus thirty would be arithmetic rather than an answer.
     */
    public BigDecimal stillNeeded() {
        BigDecimal outstanding = WEEKLY_MINIMUM.subtract(newSavings);
        return outstanding.signum() > 0 ? outstanding : BigDecimal.ZERO.setScale(DECIMAL_PLACES);
    }
}
