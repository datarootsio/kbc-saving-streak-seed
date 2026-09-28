package io.dataroots.savingstreak.challenges;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Optional;
import java.util.TreeMap;

import io.dataroots.savingstreak.deposits.DepositLanded;
import io.dataroots.savingstreak.deposits.WithdrawalMade;
import io.dataroots.savingstreak.streaks.SavingsWeek;

/**
 * The moment a customer's savings balance last rose to a floor and stayed there, worked out by
 * walking their money movements forward, and the whole days that have passed since.
 *
 * <p><strong>Why a walk rather than a date somebody writes down.</strong> "Held since" is the sort
 * of fact an application is tempted to store: a column set when the balance crosses the floor and
 * cleared when it falls back. That column would be wrong the first time a trainer wound the
 * development clock backwards, because the movements would move with the clock and the column would
 * not — it would still be claiming a run that started in a week the application no longer believes
 * has happened. The ledger already knows every movement and every moment; the run is a reading over
 * it, and a reading over a ledger cannot go stale. Nothing here is stored, and that is the same
 * bargain every derived figure in this application makes.
 *
 * <p><strong>A dip resets it, and the reset is the rule rather than a consequence of one.</strong>
 * The walk keeps the moment the balance last came up to the floor and throws that moment away the
 * instant the balance goes under it again, however briefly and however much money goes back in
 * afterwards. A customer eighty-nine days into ninety who takes a hundred euros out and puts it back
 * the next morning is at nothing, not at eighty-nine — because the challenge is for money left
 * alone, and money that left was not left alone.
 *
 * <p><strong>Movements stamped at the same moment are one step of the walk.</strong> A balance is a
 * function of time, and at a single instant it has a single value: the one after everything stamped
 * at that instant has been applied. Deciding the order of two movements inside one millisecond would
 * be inventing a dip, or hiding one, on the strength of which row the database happened to hand back
 * first.
 *
 * <p><strong>Whole days, counted in the calendar rather than in multiples of 86 400 seconds.</strong>
 * This application's clock moves in calendar days, in the zone its weeks are counted in, and a
 * spring clock change makes thirty of those an hour short of thirty times a day. Counted in
 * durations, a customer who had held the floor for exactly ninety calendar days across a clock
 * change would be told they had held it for eighty-nine, and a demonstration would fail on the
 * strength of the month it was given in. Counted in the calendar, ninety days on is ninety days
 * held whatever the clocks did in between.
 */
final class WhenTheBalanceLastRoseToTheFloor {

    private WhenTheBalanceLastRoseToTheFloor() {
    }

    /**
     * The moment the balance last rose to the floor without falling below it again, or nothing at
     * all for a customer who has never reached it.
     *
     * <p>Both halves of the ledger, because both move a balance: a run of deposits pushes it up and
     * a withdrawal pulls it down, and a walk given only the deposits would report every floor ever
     * touched as still held. The lists are the customer's across every savings account they hold, so
     * a second account is a second pocket of one buffer rather than a way of holding a floor twice.
     *
     * @param floor         what the balance has to be at or above, which is the challenge's own
     *                      figure
     * @param paidIn        every deposit of theirs up to the moment being asked about
     * @param takenBackOut  every withdrawal of theirs up to the same moment
     */
    static Optional<Instant> walkingTheMovements(BigDecimal floor, List<DepositLanded> paidIn,
                                                 List<WithdrawalMade> takenBackOut) {
        NavigableMap<Instant, BigDecimal> whatEachMomentChanged = new TreeMap<>();
        for (DepositLanded deposit : paidIn) {
            whatEachMomentChanged.merge(deposit.depositedAt(), deposit.amount(), BigDecimal::add);
        }
        for (WithdrawalMade withdrawal : takenBackOut) {
            whatEachMomentChanged.merge(
                    withdrawal.withdrawnAt(), withdrawal.amount().negate(), BigDecimal::add);
        }

        BigDecimal balance = BigDecimal.ZERO;
        Instant roseToIt = null;
        for (Map.Entry<Instant, BigDecimal> moment : whatEachMomentChanged.entrySet()) {
            balance = balance.add(moment.getValue());
            if (balance.compareTo(floor) < 0) {
                roseToIt = null;
            } else if (roseToIt == null) {
                roseToIt = moment.getKey();
            }
        }
        return Optional.ofNullable(roseToIt);
    }

    /**
     * How many whole days lie between the moment the floor was reached and now, and never fewer than
     * none.
     *
     * <p>Whole days, so that a floor reached at half past two this afternoon is held for no days
     * until half past two to-morrow: a challenge asking for ninety days is asking for ninety of
     * them, and counting the day it started as one would pay gold on the eighty-ninth.
     */
    static long wholeDaysBetween(Instant roseToIt, Instant now) {
        LocalDateTime from = LocalDateTime.ofInstant(roseToIt, SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN);
        LocalDateTime until = LocalDateTime.ofInstant(now, SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN);
        return Math.max(0, ChronoUnit.DAYS.between(from, until));
    }
}
