package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import io.dataroots.savingstreak.points.PointsExpiringNext;
import io.dataroots.savingstreak.streaks.WeekAndStreak;

/**
 * A savings account and what it is worth, in the two currencies the customer cares about: the money
 * they have saved here and the points their saving has earned them, side by side because the point
 * of the application is the connection between the two.
 *
 * <p>The money is this account's; the points, the week and the run of weeks are the holder's. All
 * three belong to the customer rather than to any one account they save into, so they read the same
 * on every account they hold — what paying in <em>here</em> earned is on the deposit, in the history
 * underneath.
 *
 * <p>And the week they are part-way through, the run of weeks behind it, and what that run pays. The
 * two balances say where the account has got to altogether; the three weekly figures say where it has
 * got to since Monday, which is the thing a customer can still do something about before Sunday; the
 * two streak figures say how many weeks like this one have run consecutively and how many ever did —
 * what there is to lose, and what there is to beat; and the multiplier says what the run is worth per
 * euro, which is the figure that decides whether to pay in now. All eight are derived on every read
 * and none of them is worked out here.
 *
 * <p>The multiplier is the account's rate as it stands, not a rate any deposit was paid at. What a
 * past deposit was paid is on the deposit, was decided at the moment the money moved, and does not
 * move when this figure does.
 *
 * <p>What the week asks for travels with what has landed in it. A screen showing progress towards
 * EUR 50 that had the 50 written into its own markup would be a second place the weekly minimum
 * lives, and the two would be one repricing away from disagreeing.
 *
 * <p>And what the holder stands to lose next: how many of their points expire soonest, and the day
 * they do. Beside the balance rather than anywhere else, because it is the same figure read from the
 * other end — what they can spend, and how long they have to spend it in. Both are null for a
 * customer with nothing left to lose rather than zero on no date, because "nothing expires next" and
 * "nothing expires on some particular day" are different statements and only the first is true of
 * somebody who has never earned anything.
 *
 * <p>The day travels as a plain date rather than as a moment. Which calendar day a moment falls on
 * depends on the zone it is read in, and the Points module has already read it in the one zone this
 * application counts calendars in — so a client is handed the answer instead of the means to get it
 * wrong.
 */
record SavingsAccountResponse(Long id, String customerName, BigDecimal moneyBalance, long pointsBalance,
                              Long pointsExpiringNext, LocalDate pointsExpiringNextOn,
                              BigDecimal newSavingsThisWeek, BigDecimal weeklyMinimum,
                              BigDecimal stillNeededThisWeek,
                              int currentStreakWeeks, int bestStreakWeeks,
                              BigDecimal currentMultiplier) {

    static SavingsAccountResponse of(long savingsAccountId, String customerName, BigDecimal moneyBalance,
                                     long pointsBalance, Optional<PointsExpiringNext> expiringNext,
                                     WeekAndStreak saving) {
        return new SavingsAccountResponse(savingsAccountId, customerName, moneyBalance, pointsBalance,
                expiringNext.map(PointsExpiringNext::points).orElse(null),
                expiringNext.map(PointsExpiringNext::on).orElse(null),
                saving.week().newSavings(), saving.week().weeklyMinimum(), saving.week().stillNeeded(),
                saving.streak().currentWeeks(), saving.streak().bestWeeks(),
                saving.streak().multiplier());
    }
}
