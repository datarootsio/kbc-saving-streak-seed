package io.dataroots.savingstreak.products;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * What an account's balance was on every day of a period, read as the two figures that decide what
 * the period pays: the average of them and the lowest of them.
 *
 * <p><strong>The first figure in this application that has to be true of a day in the past.</strong>
 * Everything else here — a balance, a week, a run of weeks, the most ever saved — is a question
 * about now, answered by summing rows as they stand. This is a question about the 3rd of last
 * month, and the only honest way to answer it is to walk the movements the account already records
 * and replay them. Nothing is stored: a daily balance table would be a second record of what the
 * ledger already says, and the day the two disagreed the interest would be right in neither.
 *
 * <p><strong>The average and not the closing balance, and not the opening one either.</strong> The
 * closing balance pays a whole month on money that arrived yesterday, which is wrong in the
 * customer's favour; the opening balance pays nothing at all on it, which is wrong in the bank's.
 * The average pays for the money that was actually there, for as long as it was actually there —
 * so a deposit on the last day of a period earns one day of interest out of thirty, and a customer
 * can check that by hand.
 *
 * <p><strong>The lowest comes out of the same walk for nothing</strong>, and it is the figure that
 * decides whether an account with a floor to keep kept it. {@link TheRateAPeriodIsPaidAt} is what
 * reads it, and it is recorded on every posting besides, because the walk that produces it is this
 * walk and asking for it later would mean walking a month that has already been paid for. The
 * lowest rather than the closing or the average balance is the whole strictness of the rule: money
 * taken out on the 3rd and put back on the 4th is a month that went under the floor, and no other
 * figure this walk could report would say so.
 *
 * <p><strong>A day's balance is the balance at the end of that day</strong> — everything that had
 * arrived by then, less everything that had been taken by then. So money that lands on a day counts
 * for that day, which is the reading a customer makes ("I paid it in on the 3rd, so it was there on
 * the 3rd") and the one that makes a deposit on the last day of a period worth exactly one day. The
 * alternative, counting a movement from the following day, would pay nothing at all on a deposit
 * made on the last day and would be a rule nobody could see the reason for.
 *
 * <p>Cents throughout, because a balance summed as cents cannot pick up a fraction on the way
 * round, and because the average of a month of balances is a division that has to be floored
 * somewhere — doing it once, on a whole number of cents, is what makes the figure on the posting
 * the figure the interest was worked out from.
 *
 * <p>A class of its own with no state, no repository and nothing injected, for
 * {@link TheTermsOnOfferToday}'s reason and one more: its interesting cases are arithmetic and
 * calendar cases — everything arriving on the last day, everything on the first, a one-day dip, a
 * month with no movement at all, an account empty for half of it — and an API test would need years
 * of clock-winding to reach them. It is the one thing in this module tested directly rather than
 * through the door.
 *
 * <p><strong>Public, and widened on purpose rather than by drift.</strong>
 * The what-if simulator has to work out what a projected month's interest comes to, and that is
 * the average of the balances the branch stood at on each of that month's days — the same walk,
 * over movements the fold made rather than movements the ledger holds. A fold averaging its own way
 * would be the second place this bank decides what an average daily balance is, and the two would
 * part company the first time the flooring or the end-of-day reading moved.
 * The reason it is safe is the reason {@code LoyaltyRate} gives for the same move: there is nothing
 * of this module's machinery in it — no repository, no entity, no clock, no state, just a rule
 * about numbers. A module that calls it is not reading Products; it is quoting a rule Products
 * wrote down, which is the only way a projection and a sweep can be kept from disagreeing.
 */
public final class TheDailyBalancesOfAnAccount {

    private TheDailyBalancesOfAnAccount() {
    }

    /**
     * One movement of money in or out of an account, as this walk needs it: the day it happened on
     * and what it did to the balance.
     *
     * <p>Signed cents rather than an amount and a direction, because a walk that has to ask which
     * way each movement went before adding it is a walk with a branch in it for no gain. Whoever
     * hands these in reads the direction the ledger states and turns it into a sign once.
     *
     * <p><strong>Every kind of movement, including the interest already paid.</strong> Interest is
     * money in the account, so it is in the balance on every day after it landed and therefore in
     * the next period's average — which is the whole of how monthly interest compounds here, and it
     * happens because the caller hands in the ledger rather than because anything compounds it.
     */
    public record AMovementOfMoney(LocalDate on, long cents) {
    }

    /**
     * What a period's balances came to: the average, the lowest, and how many days were averaged.
     *
     * <p>The number of days is reported rather than left to be worked out again, because it is what
     * makes the other two checkable: a reader with the daily balances and this figure can redo the
     * division, and a period of twenty-eight days paying less than one of thirty-one is explained
     * rather than suspicious.
     */
    public record TheBalanceAcrossAPeriod(long averageCents, long lowestCents, int days) {
    }

    /**
     * The average and the lowest daily balance over a period, from the movements the account
     * records.
     *
     * <p>The movements may be in any order and may be from any time at all: everything before the
     * period is what the account opened the period holding, everything inside it moves the balance
     * on the day it happened, and everything after it is ignored. That last one matters more than
     * it looks — a sweep paying twelve months at once works out each of them from the same ledger,
     * and a period must not be paid on money that arrived after it ended.
     *
     * <p>The average is floored to the cent, once, here. It is the figure the posting records and
     * the figure the interest is worked out from, so flooring it anywhere else would leave a
     * customer unable to redo the arithmetic from what they were shown.
     *
     * @param from  the first day of the period, which counts
     * @param until the day the period ends, which does not — the half-open reading every other
     *              stretch of time in this application uses
     * @throws IllegalArgumentException if the period is not at least a day long, which is a caller
     *                                  asking about no time at all rather than anything a customer
     *                                  could have done
     */
    public static TheBalanceAcrossAPeriod across(List<AMovementOfMoney> movements,
                                                 LocalDate from, LocalDate until) {
        long days = ChronoUnit.DAYS.between(from, until);
        if (days < 1) {
            throw new IllegalArgumentException("an interest period is at least a day long, and "
                    + from + " to " + until + " is not");
        }
        long balance = whatItHeldGoingIn(movements, from);
        Map<LocalDate, Long> whatMovedEachDay = whatMovedEachDayOf(movements, from, until);
        long addedUp = 0;
        long lowest = Long.MAX_VALUE;
        LocalDate day = from;
        for (long counted = 0; counted < days; counted++) {
            // The day's movements before the day's balance is read, because a day's balance is what
            // the account held at the end of it.
            balance += whatMovedEachDay.getOrDefault(day, 0L);
            addedUp += balance;
            lowest = Math.min(lowest, balance);
            day = day.plusDays(1);
        }
        // Floored rather than rounded, and floored towards the lower figure however the arithmetic
        // arrived: the bank pays on the balance that was actually there, and a division that
        // rounded up would pay a cent on a balance the account never held.
        long average = Math.floorDiv(addedUp, days);
        return new TheBalanceAcrossAPeriod(average, lowest, (int) days);
    }

    /**
     * What the account was holding on the morning the period began: everything that had moved
     * before it, netted.
     *
     * <p>Netted rather than walked, because how the balance got there is not a question this period
     * asks — only what it was. A period with no movement at all in it is therefore this figure on
     * every one of its days, which is the case that would be easiest to get wrong by walking only
     * the movements inside the window.
     */
    private static long whatItHeldGoingIn(List<AMovementOfMoney> movements, LocalDate from) {
        long held = 0;
        for (AMovementOfMoney moved : movements) {
            if (moved.on().isBefore(from)) {
                held += moved.cents();
            }
        }
        return held;
    }

    /**
     * What moved on each day inside the period, gathered by day.
     *
     * <p>Gathered once rather than searched per day, so that a month of an account with a hundred
     * movements in it is one pass over them and thirty lookups rather than thirty passes.
     */
    private static Map<LocalDate, Long> whatMovedEachDayOf(List<AMovementOfMoney> movements,
                                                           LocalDate from, LocalDate until) {
        Map<LocalDate, Long> byDay = new HashMap<>();
        for (AMovementOfMoney moved : movements) {
            if (!moved.on().isBefore(from) && moved.on().isBefore(until)) {
                byDay.merge(moved.on(), moved.cents(), Long::sum);
            }
        }
        return byDay;
    }
}
