package io.dataroots.savingstreak.loyalty;

import java.time.Instant;
import java.time.Period;

import io.dataroots.savingstreak.streaks.SavingsWeek;

/**
 * A deposit's recurring twelve-month clock: when its <em>n</em>-th anniversary falls, and how many
 * of them have arrived by a given moment.
 *
 * <p>The only thing in the application that answers "when does this deposit next pay". Twelve
 * months is named once here, the way {@code €50} and {@code 1.50×} are each named once, so a
 * training exercise that reprices the scheme has one figure to change.
 *
 * <p>Recurring and never reset. Every anniversary is counted from the moment the money landed
 * rather than from the last one that was paid, which is what makes the third fall exactly two years
 * after the first: a clock restarted at each payment would drift, because twelve months added three
 * times is not always the same date as thirty-six months added once. A deposit made on 29 February
 * 2020 pays on 28 February in 2021, 2022 and 2023 — there is no 29th to pay on — and then on 29
 * February 2024, which is the answer somebody reading a calendar would give. Repeated addition
 * would have clamped that fourth anniversary back to the 28th and kept it there for good.
 *
 * <p>Calendar months rather than a count of days, counted in the zone this application already
 * counts calendar things in, for the reason {@link io.dataroots.savingstreak.points points} gives
 * about the same span: 365 days would put a deposit made on 29 February one day early in three
 * years out of four.
 *
 * <p>Package-private, like the record it dates: the clock is the Loyalty module's rule, and what it
 * says out loud is the bonus it produced.
 */
final class LoyaltyAnniversary {

    /** How often a deposit pays for the money still sitting in it, in one place. */
    static final Period HOW_OFTEN_A_DEPOSIT_PAYS = Period.ofMonths(12);

    private LoyaltyAnniversary() {
    }

    /**
     * When this deposit's <em>n</em>-th anniversary falls: twelve months multiplied by <em>n</em>,
     * added once to the moment the money landed.
     *
     * <p>Multiplied out and added once rather than added <em>n</em> times, which is the whole reason
     * this is a function of the ordinal instead of a loop. February clamping is not associative —
     * 29 February plus twelve months is the 28th, and the 28th plus twelve months is the 28th
     * forever after — so adding repeatedly would quietly move a leap-day deposit's anniversary a
     * day earlier and leave it there. Counted from the original moment every time, the clamp
     * applies to the one addition and undoes itself in the next leap year.
     *
     * @throws IllegalArgumentException if asked for an anniversary before the first, which nobody
     *                                  types and so can only be a mistake in the caller
     */
    static Instant anniversaryOf(Instant landedAt, int ordinal) {
        if (ordinal < 1) {
            throw new IllegalArgumentException(
                    "a deposit's first anniversary is its first, and this one was asked for " + ordinal);
        }
        return landedAt.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN)
                .plus(HOW_OFTEN_A_DEPOSIT_PAYS.multipliedBy(ordinal))
                .toInstant();
    }

    /**
     * How many of this deposit's anniversaries have arrived by the given moment — the ordinal of the
     * last one that has fallen, and none at all for a deposit still inside its first year.
     *
     * <p>An anniversary falling exactly at the moment being judged has arrived, the same way the
     * points sweep treats a batch whose anniversary is exactly now as expired. A deposit dated
     * after the moment — which a trainer who wound the clock forward, paid money in and wound it
     * back has left behind — has none.
     *
     * <p>Counted by walking the anniversaries rather than by dividing the months between the two
     * moments, because the division is wrong in exactly the case the clamping exists for: 29
     * February 2024 to 28 February 2025 is eleven months and thirty days to any month-counting
     * arithmetic, while the anniversary that day is the deposit's first and has plainly arrived. The
     * walk asks {@link #anniversaryOf}, so there is one answer to "when does it pay" and this is a
     * question asked of that answer. It runs once per deposit per sweep and takes one step per year
     * the deposit has been open.
     */
    static int anniversariesPassedBy(Instant landedAt, Instant now) {
        int passed = 0;
        while (!anniversaryOf(landedAt, passed + 1).isAfter(now)) {
            passed++;
        }
        return passed;
    }

    /**
     * A moment late enough that every deposit with an anniversary to pay by {@code now} landed
     * before it — what the sweep asks the database for, so that it reads the deposits old enough to
     * have paid something rather than every deposit ever made.
     *
     * <p>Two days of slack, for the reason the points sweep's cut-off gives: counting twelve months
     * forward and counting twelve months back are not exact inverses. A deposit made on 29 February
     * has its first anniversary clamped back to 28 February, and twelve months back from that day
     * lands on the 28th rather than the 29th — so a cut-off that was merely twelve months back
     * would leave that one deposit out of the sweep for a day. The slack costs a handful of rows
     * whose {@link #anniversariesPassedBy} the sweep then finds is none, and it means the query
     * never has to be exactly right about a case the rule already decides.
     *
     * <p>Only the first anniversary needs the cut-off to be generous. A deposit whose second or
     * tenth anniversary has arrived is older still, so it is further inside the same window.
     */
    static Instant nothingLandedAfterThisCanHaveAnAnniversaryBy(Instant now) {
        return now.atZone(SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN)
                .minus(HOW_OFTEN_A_DEPOSIT_PAYS)
                .plusDays(2)
                .toInstant();
    }
}
