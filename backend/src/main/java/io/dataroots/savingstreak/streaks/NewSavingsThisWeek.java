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
     * Euros are quoted to the cent, and a figure that came back off a float has to be put back. Open
     * to the module rather than to this record alone, so that a sibling writing an amount out — the
     * log line in {@link StreaksService}, for one — quotes it to the same number of places instead
     * of restating the figure.
     */
    static final int DECIMAL_PLACES = 2;

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
     * How much more the week needs, and nothing below zero: a week that has taken EUR 80 in needs no
     * more, and reporting that it needs minus thirty would be arithmetic rather than an answer.
     */
    public BigDecimal stillNeeded() {
        BigDecimal outstanding = WEEKLY_MINIMUM.subtract(newSavings);
        return outstanding.signum() > 0 ? outstanding : BigDecimal.ZERO.setScale(DECIMAL_PLACES);
    }
}
