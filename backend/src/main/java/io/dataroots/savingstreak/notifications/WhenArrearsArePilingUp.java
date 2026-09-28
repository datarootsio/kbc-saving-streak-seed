package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import io.dataroots.savingstreak.accounts.ABillThatCouldNotBePaid;

/**
 * When a current account's unpaid bills stop being a bad month and become a spiral, and — the part
 * that matters — the moment they last <em>crossed</em> into being one.
 *
 * <p><strong>Two conditions, either alone.</strong> As many dates outstanding at once as the scheme
 * says is a spiral, or a total owed that has passed the monthly income the customer declared. A
 * customer with one enormous unpaid bill is in as much trouble as one with three small ones, so the
 * count cannot be the whole rule; a customer with three small ones is in trouble their income says
 * nothing about, so the total cannot be either.
 *
 * <p><strong>The count is a published figure and arrives as an argument.</strong> Three is what
 * version 1 of the scheme is seeded at and what this rule ran on before the scheme had versions:
 * two is a bad month and a customer can see their way out of it, and by the third the hole is
 * growing faster than one month's room can close it. That was always an argument for a default
 * rather than for a constant, which is exactly why it is now a figure somebody can publish a
 * different answer to — and why the constant that held it, and the form that read it, are gone. A
 * bridge nobody removes is how two counts start disagreeing: it would have gone on calling three
 * bills a spiral after the bank published four, with nothing anywhere objecting, because three is a
 * perfectly good count. The warning is now about the argument and is sharper for it — a caller that
 * counted outstanding dates against a figure of its own would be the second place this warning is
 * decided.
 *
 * <p><strong>The crossing rather than the condition, and that is the whole of this class.</strong>
 * An account that has stood over the threshold every night since March crossed it once, in March,
 * and its holder is told once. Asking "is it over the threshold tonight" would put the same line in
 * front of them on all of the nights since, and an inbox holding forty copies of one line is worse
 * than no notification at all. So the record is replayed rather than counted: every date that ever
 * fell short opens a hole at the moment it fell short and closes it at the moment it was settled,
 * and walking those moments in order says not only where the pile stands now but when it last got
 * there. A pile that has been over the threshold without interruption since March answers with
 * March, which is older than anything already said about it, and nothing is raised.
 *
 * <p><strong>A crossing is a statement about a night, not about a moment inside one.</strong> The
 * 02:30 billing run settles what is already owed and then presents what is newly due, and it stamps
 * every row it touches with the same instant — so the ordinary shape of a household digging itself
 * out, money back from savings clearing the oldest arrear while a new date falls short the same
 * night, reaches this replay as a settlement and a failure at one moment. Everything sharing an
 * instant is therefore netted into the one movement it was before the threshold is tested at all.
 * Walked one at a time it would read as three arrears dipping to two and climbing back to three: a
 * climb-out that never happened, the crossing being carried spent on it, and a fresh warning every
 * month for an account that owed three bills at dusk and owes three at dawn.
 *
 * <p>The replay is what {@link ABillThatCouldNotBePaid} exists for, and it is why the billing run
 * writes down the night a date fell short as well as the moment it was settled. Neither figure can
 * be recovered afterwards from a list of what is outstanding now.
 *
 * <p><strong>Judged with today's declared income throughout.</strong> An income declaration is not
 * versioned — an account has the one its holder last typed — so a replay cannot know what was
 * declared last March and does not pretend to. The consequence is small and worth stating: a
 * customer who puts their declared income up can move the crossing the replay finds, and will then
 * be told once more that their bills are piling up. Being told again after saying their money has
 * changed is a defensible answer; the alternative is versioning a declaration nothing else in this
 * application versions.
 *
 * <p>An account whose holder has declared no income at all is judged on the count alone. There is no
 * figure to exceed, and inventing one would make the louder warning fire on an account nobody has
 * described yet.
 *
 * <p>Pure, and asserted over HTTP rather than in a test of its own: the feature's testing decision
 * puts the notification transitions at the integration seam with the job and the cursor, and keeps
 * the plain unit tests for the calendar and money functions. It lives in a class of its own all the
 * same, for the reason {@link TheBalanceRungs} does — the rule is worth reading on its own, and
 * the sweep that uses it is long enough already.
 */
final class WhenArrearsArePilingUp {

    private WhenArrearsArePilingUp() {
    }

    /**
     * Replays every date this account ever failed to pay, in the order it happened, and answers
     * where the pile stands now and when it last crossed into being one — with the stated count
     * deciding how many outstanding at once is a spiral.
     *
     * <p>The whole of the replay, and the only copy of it. The netting of everything that shares an
     * instant, the crossing being spent the moment the pile goes back under, and the two conditions
     * either of which alone is a spiral are all exactly what they were when the count was a constant
     * beside them; what changed is where the count comes from.
     *
     * <p><strong>The whole replay is judged at one count, which is the same concession the declared
     * income already makes.</strong> A scheme published in March that raised the count from three to
     * four does not mean a crossing in February should be re-read at three: nothing here knows what
     * was published then, and the paragraph above already states the consequence for the income — a
     * figure that moves can move the crossing this finds, and the customer is told once more. That
     * is the honest answer for a sweep that runs tonight and reads tonight's scheme. What it is
     * emphatically not is the streak derivation, which spans weeks and is handed the scheme's whole
     * history precisely because one set of figures could never be right for it.
     *
     * @param fellShort every date presented and refused on one current account, settled or not
     * @param declaredMonthlyIncome what the holder says lands every month, or null if they have not
     *                              said
     * @param howManyOutstandingIsASpiral how many dates outstanding at once is a spiral on its own,
     *                                    from the scheme in force on the night the sweep runs
     */
    static ThePile replaying(List<ABillThatCouldNotBePaid> fellShort,
                            BigDecimal declaredMonthlyIncome,
                            int howManyOutstandingIsASpiral) {
        // Keyed by the moment rather than listed, which is what collapses everything that happened
        // at one instant into the single net movement it was. The billing run settles what is
        // already owed and then presents what is newly due, and it stamps both with the same
        // {@code now} — so a night that clears one arrear and misses another arrives here as a
        // settlement and a failure sharing an instant exactly.
        SortedMap<Instant, AMomentThePileMoved> whenThePileMoved = new TreeMap<>();
        for (ABillThatCouldNotBePaid refused : fellShort) {
            BigDecimal worth = refused.amount() == null ? BigDecimal.ZERO : refused.amount();
            whenThePileMoved.merge(refused.fellShortAt(), new AMomentThePileMoved(1, worth),
                    AMomentThePileMoved::and);
            if (refused.isCleared()) {
                whenThePileMoved.merge(refused.clearedAt(),
                        new AMomentThePileMoved(-1, worth.negate()), AMomentThePileMoved::and);
            }
        }
        long outstanding = 0;
        BigDecimal owed = BigDecimal.ZERO;
        boolean over = false;
        Instant crossedAt = null;
        for (Map.Entry<Instant, AMomentThePileMoved> moment : whenThePileMoved.entrySet()) {
            outstanding += moment.getValue().howManyMore();
            owed = owed.add(moment.getValue().howMuchMore());
            // Judged on where the moment left the pile, never on a figure from inside it. Walking a
            // settlement and a failure that share an instant one at a time would read an account
            // that owed three bills at dusk and owes three at dawn as having dipped to two in
            // between, spend the crossing it is carrying on that invented dip, and announce the
            // failure that follows as a fresh crossing — which is the ordinary shape of a household
            // digging itself out, and would earn it a copy of the same warning every month.
            boolean overNow = isASpiral(outstanding, owed, declaredMonthlyIncome,
                    howManyOutstandingIsASpiral);
            if (overNow && !over) {
                crossedAt = moment.getKey();
            } else if (!overNow) {
                // Back under, so whatever crossing was being carried is spent: the next time this
                // account goes over it is a new crossing and is worth saying again.
                crossedAt = null;
            }
            over = overNow;
        }
        return new ThePile(outstanding, owed, over, crossedAt);
    }

    private static boolean isASpiral(long outstanding, BigDecimal owed,
                                     BigDecimal declaredMonthlyIncome,
                                     int howManyOutstandingIsASpiral) {
        if (outstanding >= howManyOutstandingIsASpiral) {
            return true;
        }
        return declaredMonthlyIncome != null && owed.compareTo(declaredMonthlyIncome) > 0;
    }

    /**
     * Where an account's pile of unpaid bills stands, and when it last got there: how many dates are
     * outstanding, what they come to, whether that is a spiral, and the moment it became one — null
     * when it is not one.
     */
    record ThePile(long outstanding, BigDecimal owed, boolean isASpiral, Instant crossedAt) {
    }

    /**
     * How far the pile moved at one instant, netted: a date going unpaid, one being settled, or —
     * the case this record exists for — several of both at the one moment the billing run stamped
     * them all with.
     */
    private record AMomentThePileMoved(int howManyMore, BigDecimal howMuchMore) {

        /** The two movements at one instant as the one movement that instant actually was. */
        AMomentThePileMoved and(AMomentThePileMoved alsoThen) {
            return new AMomentThePileMoved(howManyMore + alsoThen.howManyMore(),
                    howMuchMore.add(alsoThen.howMuchMore()));
        }
    }
}
