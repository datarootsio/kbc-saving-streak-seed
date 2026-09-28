package io.dataroots.savingstreak.streaks;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * A run of consecutive weeks of real saving: the one the customer is on now, the longest one they
 * have ever been on, and the ladder the one happening now is paid on.
 *
 * <p>The customer's, so a week is secured by what they put away across every account they hold — two
 * goals are not two runs. See {@link StreaksService}.
 *
 * <p>Both counted in weeks, because a week is the unit the whole scheme is stated in. A week joins
 * a run once what that week asked for has landed in it — the threshold published by the version of
 * the scheme in force on the week's own Monday, which {@link NewSavingsThisWeek} carries and
 * compares — and the run is the weeks either side of it that did the same. Each of those weeks was
 * judged against its <em>own</em> Monday's figure, which is why a run counted this morning and the
 * same run counted after a repricing are the same run: the walk that built this never asks what a
 * week would have needed today.
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
 * <p><strong>And a run pays, and the ladder it pays on travels with it.</strong> The rate a euro
 * earns at is a function of how many consecutive weeks the run is, which is {@link StreakMultiplier}'s
 * answer and is stated there once — but the three figures that function is a shape of are published
 * in the scheme now and change on a Monday, so a record holding only a count of weeks could no longer
 * say what the count is worth. {@link #theLadderThisWeekPays} is the ladder in force in the week this
 * run was read in, and it is the same third component {@link NewSavingsThisWeek} grew for the same
 * reason: a figure the scheme decided belongs to the record describing the thing it decided, not to
 * a static field somebody reads afterwards.
 *
 * <p><strong>The rate is still worked out here rather than by whoever is pricing something, and the
 * argument for that is stronger than it was.</strong> It was tempting, once the ladder became an
 * argument, to delete {@link #multiplier()} and have each caller ask
 * {@link StreakMultiplier#paidByAStreakOf(int, TheLadderARunClimbs)} for itself — the figures are no
 * longer this record's, so why should the answer be. Because there are two callers and they must
 * agree: read on a savings account, {@link #multiplier()} answers "what is my rate"; read on the run
 * a deposit has just been counted into, it answers "what was this deposit paid". Two call sites each
 * fetching a ladder are two chances to fetch a <em>different</em> ladder — one off this week's
 * version and one off today's reading a minute after midnight on a Monday — and the two figures
 * would then disagree about the same run of weeks on the same screen. Carrying the ladder is what
 * makes that impossible: whoever built this record chose the week, and everybody who prices off it
 * is priced off that one choice.
 *
 * <p>Neither count is stored. Both are derived from the deposit records every time they are asked
 * for, so that a development clock wound in either direction cannot leave a counter behind
 * describing a week that is now in the future — and the ladder is not stored either, for the same
 * reason and one more: it is whatever the scheme's history says about this week, so winding the
 * clock across a published Monday changes it back and forth exactly as it changes the verdict.
 */
public record StreakOfSecuredWeeks(

        int currentWeeks,

        int bestWeeks,

        /**
         * What the run happening now is paid on: the ladder published by the version of the scheme
         * in force in the week this reading was taken in.
         *
         * <p>This week's and not each week's. The run is counted week by week under each week's own
         * threshold, which is what stops a repricing shortening it; what the run <em>pays</em> is a
         * rate quoted now, for a euro paid in now, and the only honest ladder for that is the one in
         * force now. The two rules are different on purpose and they answer different questions —
         * "did that week count" is about a week that has happened, and "what is a euro worth" is
         * about a euro that has not been paid in yet.
         */
        TheLadderARunClimbs theLadderThisWeekPays) {

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
        // Checked here rather than left to surface out of the arithmetic, for the reason
        // TheLadderARunClimbs gives about its own three figures: a null would come back as a
        // NullPointerException several frames away, inside a multiplication, with nothing on it
        // saying that the scheme was never asked.
        Objects.requireNonNull(theLadderThisWeekPays,
                "a run of weeks says what ladder it is paid on");
    }

    /**
     * What a euro paid into this account earns right now: the rate the run happening <em>now</em>
     * pays, on the ladder in force <em>now</em>.
     *
     * <p>Off the current run and never off the best-ever one, because a rate is a state and a record
     * is not: a customer whose run lapsed last month is paying the ordinary rate today, however good
     * their record is.
     *
     * <p>The one function the whole scheme is priced by, and the reason it lives here rather than in
     * whoever is pricing something. Read on a savings account it answers "what is my rate"; read on
     * the run a deposit has just been counted into it answers "what was this deposit paid" — the same
     * question about the same number of weeks <em>and the same ladder</em>, so the two can never
     * disagree. That last clause is what the migration from constants to a published scheme added to
     * this paragraph: while the three figures were constants, two callers asking
     * {@link StreakMultiplier} directly could not have got different answers; now they could, and
     * this method is where they are stopped from trying.
     */
    public BigDecimal multiplier() {
        return StreakMultiplier.paidByAStreakOf(currentWeeks, theLadderThisWeekPays);
    }
}
