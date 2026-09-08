package io.dataroots.savingstreak.streaks;

import java.math.BigDecimal;

/**
 * A run of consecutive weeks of real saving: the one the customer is on now, and the longest one they
 * have ever been on.
 *
 * <p>The customer's, so a week is secured by what they put away across every account they hold — two
 * goals are not two runs. See {@link StreaksService}.
 *
 * <p>Both counted in weeks, because a week is the unit the whole scheme is stated in. A week joins
 * a run once {@link NewSavingsThisWeek#WEEKLY_MINIMUM} of new saving has landed in it, and the run
 * is the weeks either side of it that did the same.
 *
 * <p>The current run counts only while it is alive, and it is alive while its last secured week is
 * this week or the one immediately before it. That makes it a reading of the calendar and the ledger
 * with nothing pending in it: a customer who skipped last week is on a run of nothing <em>now</em>,
 * on a Wednesday, rather than at the end of a week nobody has finished yet.
 *
 * <p>The best-ever run outlives the current one. Losing a streak costs the run and not the record,
 * which is the whole reason two figures are reported rather than one — a lapse leaves a customer
 * with a figure to beat instead of with nothing.
 *
 * <p>And a run pays: the rate a euro earns at is a function of how many consecutive weeks the run
 * is, which is {@link StreakMultiplier}'s answer and is stated there once.
 *
 * <p>Neither figure is stored. Both are derived from the deposit records every time they are asked
 * for, so that a development clock wound in either direction cannot leave a counter behind
 * describing a week that is now in the future.
 */
public record StreakOfSecuredWeeks(int currentWeeks, int bestWeeks) {

    public StreakOfSecuredWeeks {
        if (currentWeeks < 0 || bestWeeks < 0) {
            throw new IllegalArgumentException("a run of weeks is never negative, and this one was "
                    + currentWeeks + " now with a best of " + bestWeeks);
        }
        // The run happening now is itself a run of consecutive secured weeks, so the longest run
        // there has ever been cannot be shorter than it. Said out loud because it is the one way the
        // two figures can contradict each other, it would reach a screen as "3 weeks in a row, best
        // ever 2", and nobody typed either number: the only way here is a mistake in the derivation.
        if (currentWeeks > bestWeeks) {
            throw new IllegalArgumentException("a run happening now is a run that has happened, so a "
                    + "best of " + bestWeeks + " weeks cannot be shorter than the " + currentWeeks
                    + " weeks running now");
        }
    }

    /**
     * What a euro paid into this account earns right now: the rate the run happening <em>now</em>
     * pays.
     *
     * <p>Off the current run and never off the best-ever one, because a rate is a state and a record
     * is not: a customer whose run lapsed last month is paying the ordinary rate today, however good
     * their record is.
     *
     * <p>The one function the whole scheme is priced by, and the reason it lives here rather than in
     * whoever is pricing something. Read on a savings account it answers "what is my rate"; read on
     * the run a deposit has just been counted into it answers "what was this deposit paid" — the same
     * question about the same number of weeks, so the two can never disagree.
     */
    public BigDecimal multiplier() {
        return StreakMultiplier.paidByAStreakOf(currentWeeks);
    }
}
