package io.dataroots.savingstreak.web;

import java.math.BigDecimal;

import io.dataroots.savingstreak.streaks.NewSavingsThisWeek;

/**
 * A savings account and what it is worth, in the two currencies the customer cares about: the money
 * they have saved and the points that saving earned them, side by side because the point of the
 * application is the connection between the two.
 *
 * <p>And the week they are part-way through. The two balances say where the account has got to
 * altogether; the three weekly figures say where it has got to since Monday, which is the thing a
 * customer can still do something about before Sunday. All five are derived on every read and none
 * of them is worked out here.
 *
 * <p>What the week asks for travels with what has landed in it. A screen showing progress towards
 * EUR 50 that had the 50 written into its own markup would be a second place the weekly minimum
 * lives, and the two would be one repricing away from disagreeing.
 */
record SavingsAccountResponse(Long id, String customerName, BigDecimal moneyBalance, long pointsBalance,
                              BigDecimal newSavingsThisWeek, BigDecimal weeklyMinimum,
                              BigDecimal stillNeededThisWeek) {

    static SavingsAccountResponse of(long savingsAccountId, String customerName, BigDecimal moneyBalance,
                                     long pointsBalance, NewSavingsThisWeek thisWeek) {
        return new SavingsAccountResponse(savingsAccountId, customerName, moneyBalance, pointsBalance,
                thisWeek.newSavings(), thisWeek.weeklyMinimum(), thisWeek.stillNeeded());
    }
}
