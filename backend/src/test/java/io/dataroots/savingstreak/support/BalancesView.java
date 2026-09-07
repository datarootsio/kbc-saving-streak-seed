package io.dataroots.savingstreak.support;

import java.math.BigDecimal;

/**
 * A savings account as the API reports it: what it holds, what that earned, and how far into this
 * week's saving it has got. Every figure is derived on every read. Shared by every test that reads a
 * balance back, so that none of them can drift into disagreeing about the shape of the answer.
 */
public record BalancesView(Long id, String customerName, BigDecimal moneyBalance, long pointsBalance,
                           BigDecimal newSavingsThisWeek, BigDecimal weeklyMinimum,
                           BigDecimal stillNeededThisWeek) {
}
