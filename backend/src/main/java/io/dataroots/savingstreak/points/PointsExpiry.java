package io.dataroots.savingstreak.points;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;

import io.dataroots.savingstreak.streaks.SavingsWeek;

/**
 * How long a batch of points lasts, and when a given batch's twelve months are up.
 *
 * <p>Twelve months from the moment the batch was earned, and nothing but spending it shortens or
 * lengthens that. Paying in again earns a new batch with twelve months of its own rather than
 * refreshing the old one, which is what "twelve months of inactivity" means here: the inactivity
 * belongs to the batch, and a batch is active only in the moment some of it is spent.
 *
 * <p>Calendar months rather than a count of days, counted in the zone this application already
 * counts calendar things in. A customer reads "twelve months" as an anniversary, and 365 days would
 * put a batch earned on 29 February 2024 on 28 February 2025 in three years out of four and on the
 * 27th in the fourth. {@link Period} clamps instead, which is the answer somebody looking at a
 * calendar would give.
 *
 * <p>Package-private, like the batch it measures: the twelve months are the ledger's rule, and the
 * one thing it says out loud about them is the moment a customer's next batch is due to go.
 */
final class PointsExpiry {

    /** Twelve months, in one place, the way €50 and 1.50× are each named in one place. */
    static final Period HOW_LONG_A_BATCH_LASTS = Period.ofMonths(12);

    private PointsExpiry() {
    }

    /**
     * The moment this batch's twelve months are up — the promise made to whoever earned it, and the
     * figure reported as when their points go.
     */
    static Instant anniversaryOf(Instant earnedAt) {
        return earnedAt.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN)
                .plus(HOW_LONG_A_BATCH_LASTS)
                .toInstant();
    }

    /**
     * A moment late enough that every batch whose anniversary has arrived by {@code now} was earned
     * before it — what a sweep asks the database for, so that it reads a year's worth of old batches
     * rather than every batch ever credited.
     *
     * <p>Two days of slack, because counting twelve months forward and counting twelve months back
     * are not exact inverses. A batch earned on 29 February has its anniversary clamped back to 28
     * February, and twelve months back from that day lands on the 28th rather than the 29th — so a
     * cut-off that was merely twelve months back would leave that one batch out of the sweep for a
     * day. The slack costs a handful of rows whose {@link #anniversaryOf} the sweep then finds is
     * still ahead of it, and it means the query never has to be exactly right about a case the rule
     * already decides.
     *
     * <p>Which is why the rule is only ever applied through {@link #anniversaryOf}, and why there is
     * no second method here that answers "has this expired?": the sweep needs the anniversary itself
     * — it is what gets written on the batch — so a predicate beside it would work the same date out
     * twice and give the rule two homes.
     */
    static Instant nothingEarnedAfterThisCanHaveExpiredBy(Instant now) {
        return now.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN)
                .minus(HOW_LONG_A_BATCH_LASTS)
                .plusDays(2)
                .toInstant();
    }

    /**
     * The day an anniversary falls on, in the zone the calendar is read in. Batches earned at
     * different moments of one day expire at different moments of one day, and a customer reads
     * those as one date — so this is what decides which batches are reported as going together.
     */
    static LocalDate dayOf(Instant anniversary) {
        return anniversary.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN).toLocalDate();
    }
}
