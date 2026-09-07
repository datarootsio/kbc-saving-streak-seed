package io.dataroots.savingstreak.support;

import java.math.BigDecimal;

/**
 * A savings account as the API reports it: what it holds, what that earned, how far into this week's
 * saving it has got, the run of consecutive secured weeks behind that week, and what that run pays
 * per whole euro. Every figure is derived on every read. Shared by every test that reads a balance
 * back, so that none of them can drift into disagreeing about the shape of the answer.
 */
public record BalancesView(Long id, String customerName, BigDecimal moneyBalance, long pointsBalance,
                           BigDecimal newSavingsThisWeek, BigDecimal weeklyMinimum,
                           BigDecimal stillNeededThisWeek,
                           int currentStreakWeeks, int bestStreakWeeks,
                           BigDecimal currentMultiplier) {
}
