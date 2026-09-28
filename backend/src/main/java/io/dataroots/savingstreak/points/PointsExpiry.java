package io.dataroots.savingstreak.points;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;

import io.dataroots.savingstreak.streaks.SavingsWeek;

/**
 * How long a batch of points lasts, and when a given batch's lifetime is up.
 *
 * <p>A lifetime from the moment the batch was earned, and nothing but spending it shortens or
 * lengthens that. Paying in again earns a new batch with a lifetime of its own rather than
 * refreshing the old one, which is what "a year of inactivity" means here: the inactivity belongs to
 * the batch, and a batch is active only in the moment some of it is spent.
 *
 * <p><strong>The lifetime is an argument and there is no constant here any more.</strong> It used to
 * be twelve months written down in this class, which made "how long do points last" a figure only a
 * release could change — and, worse, a figure the sweep recomputed every night against whatever the
 * constant currently said. Shorten it to six months and every batch older than six months would die
 * that night, including the batches whose owners had been promised a year. So the figure now comes
 * from the scheme in force when the batch was earned, is written onto the batch as it is credited,
 * and is handed to this rule by whoever is applying it.
 *
 * <p><strong>No overload survives that supplies a default.</strong> A bridge method taking only the
 * moment would compile every caller that had not been thought about, and the day the scheme
 * published a second lifetime those callers would quietly go on answering twelve months. That is
 * exactly the warning {@link io.dataroots.savingstreak.loyalty.LoyaltyAnniversary} leaves beside its
 * own ordinal, and a second copy of that mistake in the class the whole promise turns on would be
 * unforgivable. A caller that does not know the lifetime does not know enough to ask this question.
 *
 * <p>Months rather than a {@link Period}, because months are what the scheme publishes and what its
 * refusals speak about — "a points lifetime below one month" is a sentence about an integer. A
 * period would let a caller hand in a span of days that no version of the scheme can express, and
 * the sweep's cut-off below would then have no month to count back.
 *
 * <p>Calendar months rather than a count of days, counted in the zone this application already
 * counts calendar things in. A customer reads "twelve months" as an anniversary, and 365 days would
 * put a batch earned on 29 February 2024 on 28 February 2025 in three years out of four and on the
 * 27th in the fourth. {@link Period} clamps instead, which is the answer somebody looking at a
 * calendar would give.
 *
 * <p>The lifetime is the ledger's rule, and what this module says out loud about it is the day a
 * customer's points are due to go. It was package-private for exactly that reason, and it is public
 * for one caller and one reason: the simulator's fold earns points inside a branch and has to know
 * when those points would go. A fold that added twelve months of its own would be the second place
 * this rule lived — and the whole promise of that fold is that it cannot disagree with the
 * application it is predicting, which it can only keep by calling the rule rather than copying it,
 * with the lifetime the ledger itself would have used. Nothing else is opened up:
 * {@link #nothingEarnedAfterThisCanHaveExpiredBy} is the sweep's query cut-off and stays this
 * module's own, because a caller that wanted it would be writing a sweep.
 *
 * <p>Quoting three functions of their arguments is not reading a module — there is no repository, no
 * entity and no state here — which is the same reasoning {@code TimelineHorizon} gives for being
 * quoted by the two screens that draw a year.
 */
public final class PointsExpiry {

    private PointsExpiry() {
    }

    /**
     * The moment a batch earned at this instant, under a scheme promising this many months, is up —
     * the promise made to whoever earned it, and the figure written onto the batch so that no later
     * repricing can move it.
     *
     * <p>Answered rather than stored here, and then stored by the ledger. This function is applied
     * once, when the batch is credited; every later reader — the sweep, the figure a customer is
     * shown, the schedule a whole pot has coming — reads what was written rather than asking again.
     * A rule asked twice about the same batch under two schemes is two answers, and the second one
     * is the one that breaks a promise.
     *
     * @throws IllegalArgumentException if asked for a lifetime of less than a month, which no
     *                                  version of the scheme may publish and which can therefore
     *                                  only be a mistake in the caller
     */
    public static Instant anniversaryOf(Instant earnedAt, int lifetimeInMonths) {
        if (lifetimeInMonths < 1) {
            throw new IllegalArgumentException(
                    "a batch of points lasts at least a month, and this one was asked to last "
                            + lifetimeInMonths);
        }
        return earnedAt.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN)
                .plus(Period.ofMonths(lifetimeInMonths))
                .toInstant();
    }

    /**
     * A moment late enough that every batch whose lifetime has run out by {@code now} was earned
     * before it — what a sweep asks the database for, so that it reads a year's worth of old batches
     * rather than every batch ever credited.
     *
     * <p>Two days of slack, because counting months forward and counting months back are not exact
     * inverses. A batch earned on 29 February has its anniversary clamped back to 28 February, and
     * twelve months back from that day lands on the 28th rather than the 29th — so a cut-off that
     * was merely a lifetime back would leave that one batch out of the sweep for a day. The slack
     * costs a handful of rows whose stamp the sweep then finds is still ahead of it, and it means
     * the query never has to be exactly right about a case the rule already decides.
     *
     * <p><strong>The shortest lifetime ever published, and not the one in force today.</strong> The
     * window has to be generous about every promise any surviving batch could be carrying, and a
     * batch stamped under a six-month lifetime is due a full six months before a window drawn from
     * today's twelve would even look at it. The shortest figure gives the widest window and is
     * therefore the only safe one: a longer figure narrows the window and would silently leave
     * batches unswept for months. While the scheme has published one version this is twelve, the
     * window is exactly the window it has always been, and the sweep behaves exactly as it did.
     *
     * <p>Which is also why the cut-off survived the stamp at all. The query could now ask for the
     * stamp directly and need no slack; it keeps the shape it had because the stamp is what
     * <em>decides</em>, and a window that is merely generous cannot be wrong about a boundary — it
     * can only hand the rule a few more rows to say no to.
     */
    static Instant nothingEarnedAfterThisCanHaveExpiredBy(Instant now,
                                                          int theShortestLifetimeEverPublished) {
        if (theShortestLifetimeEverPublished < 1) {
            throw new IllegalArgumentException(
                    "a batch of points lasts at least a month, and the sweep was given a shortest "
                            + "published lifetime of " + theShortestLifetimeEverPublished);
        }
        return now.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN)
                .minus(Period.ofMonths(theShortestLifetimeEverPublished))
                .plusDays(2)
                .toInstant();
    }

    /**
     * The day a moment falls on, in the zone the calendar is read in. Batches earned at different
     * moments of one day expire at different moments of one day, and a customer reads those as one
     * date — so this is what decides which batches are reported as going together.
     *
     * <p>Asked of an expiry stamp by everything that reports one, and of an earning moment by the
     * ledger working out which day's scheme priced a batch. It is the same conversion in both
     * cases and there is no second opinion about the zone.
     */
    public static LocalDate dayOf(Instant moment) {
        return moment.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toLocalDate();
    }
}
