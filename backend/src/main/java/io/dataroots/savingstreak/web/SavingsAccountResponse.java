package io.dataroots.savingstreak.web;

import java.math.BigDecimal;

import io.dataroots.savingstreak.streaks.WeekAndStreak;

/**
 * A savings account and what it is worth, in the two currencies the customer cares about: the money
 * they have saved and the points that saving earned them, side by side because the point of the
 * application is the connection between the two.
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
 */
record SavingsAccountResponse(Long id, String customerName, BigDecimal moneyBalance, long pointsBalance,
                              BigDecimal newSavingsThisWeek, BigDecimal weeklyMinimum,
                              BigDecimal stillNeededThisWeek,
                              int currentStreakWeeks, int bestStreakWeeks,
                              BigDecimal currentMultiplier) {

    static SavingsAccountResponse of(long savingsAccountId, String customerName, BigDecimal moneyBalance,
                                     long pointsBalance, WeekAndStreak saving) {
        return new SavingsAccountResponse(savingsAccountId, customerName, moneyBalance, pointsBalance,
                saving.week().newSavings(), saving.week().weeklyMinimum(), saving.week().stillNeeded(),
                saving.streak().currentWeeks(), saving.streak().bestWeeks(),
                saving.streak().multiplier());
    }
}
