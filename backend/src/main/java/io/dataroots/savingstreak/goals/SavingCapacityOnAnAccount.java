package io.dataroots.savingstreak.goals;

import java.math.BigDecimal;
import java.time.Instant;

import io.dataroots.savingstreak.streaks.NewSavingsThisWeek;

/**
 * What a savings account's holder says they can put away in a week — or that they have not said.
 *
 * <p>This is the second of the feature's two scarcities, and the one that does the planning. The
 * balance is the hard constraint and breaking it is a refusal; this is what decides whether a goal
 * arrives before its deadline, and it is where the competition between goals actually bites, because
 * when their weekly needs add up to more than this figure somebody is going to be late.
 *
 * <p><strong>Not declared is a state, not a zero.</strong> {@link #weeklyCapacity()} is null until
 * the customer says a figure, and everything downstream is expected to report that capacity is not
 * set rather than to quote a number. Goals still exist and still take money without it; what does
 * not exist is a plan, because nothing says how fast anything fills.
 *
 * <p><strong>It says in the same breath whether a week is secured at that rate.</strong> The
 * application already has a weekly figure of its own — the weekly minimum that secures a streak week
 * — and capacity is a different thing: what a customer <em>can</em> save, not what <em>secures</em>
 * a week. A capacity under the minimum is perfectly legal and is accepted as declared; it is also
 * the customer telling us that their own plan never secures a week, which is worth their knowing.
 * Saying so costs one comparison, and the comparison is made here rather than by each screen that
 * shows a capacity, so that it cannot be made two ways.
 *
 * <p><strong>The minimum is carried rather than read, and that is what changed when the scheme got
 * versions.</strong> It used to be {@code NewSavingsThisWeek.WEEKLY_MINIMUM}, a constant this module
 * quoted; the bank now publishes the figure and can reprice it on a Monday, so the record says which
 * threshold it was judged against instead of asking a static field afterwards. That constant is
 * gone, along with the one-argument {@code securedBy} that read it — a bridge nobody removes is how
 * two thresholds start disagreeing, and this one would have gone on telling a customer their EUR 60
 * a week secures a week after the bank started asking EUR 80, with nothing anywhere objecting,
 * because EUR 50 is a perfectly good threshold.
 *
 * <p>Quoting {@code NewSavingsThisWeek}'s comparison is still this module quoting a rule rather than
 * reading another module, in the shape {@code AmountOfMoney} and {@code SavingsWeek} already set: a
 * pure comparison, no repository, no entity and no state. Restating the threshold here — or in a
 * screen that wrote "under EUR 50" into its own markup — is the one thing that would be wrong,
 * because it is the figure a training exercise is most likely to change and now the figure a bank
 * can change without one.
 */
public record SavingCapacityOnAnAccount(
        long savingsAccountId,

        BigDecimal weeklyCapacity,

        Instant declaredAt,

        /**
         * What a week has to take in to count towards a streak, carried alongside the capacity so
         * that whoever shows the one can show what it is being judged against without naming the
         * figure itself.
         *
         * <p>The one in force today, and today is the right day: a declared capacity is a statement
         * about the weeks in front of the customer rather than about any week already counted, so
         * the threshold it is weighed against is the one the bank is asking for now. A week already
         * behind them is judged by its own Monday's scheme, and that is {@code NewSavingsThisWeek}'s
         * business and not this record's.
         */
        BigDecimal weeklyMinimum) {

    /**
     * An account whose holder has not said what they can save: no figure and no moment, and the
     * threshold they would be weighed against if they said one.
     */
    static SavingCapacityOnAnAccount notDeclaredOn(long savingsAccountId,
                                                   BigDecimal weeklyMinimum) {
        return new SavingCapacityOnAnAccount(savingsAccountId, null, null, weeklyMinimum);
    }

    /** Whether the customer has said a figure at all, which is what tells absence from a small one. */
    public boolean isDeclared() {
        return weeklyCapacity != null;
    }

    /**
     * Whether saving this much in a week would secure a streak week — false when nothing has been
     * declared, because no rate secures nothing.
     */
    public boolean securesAWeek() {
        return isDeclared() && NewSavingsThisWeek.securedBy(weeklyCapacity, weeklyMinimum);
    }

    /**
     * The warning the account carries alongside a capacity below the weekly minimum: at this rate
     * the customer's own plan never secures a week.
     *
     * <p>False, not true, when nothing has been declared. An account whose holder has said nothing
     * has no rate to warn about, and a warning about a figure nobody gave would be the application
     * arguing with a sentence the customer never said.
     */
    public boolean aWeekIsNotSecuredAtThisRate() {
        return isDeclared() && !securesAWeek();
    }
}
