package io.dataroots.savingstreak.support;

import java.math.BigDecimal;

/**
 * A savings account as the API reports it: what it holds and what that earned, both figures derived
 * on every read. Shared by every test that reads a balance back, so that none of them can drift into
 * disagreeing about the shape of the answer.
 */
public record BalancesView(Long id, String customerName, BigDecimal moneyBalance, long pointsBalance) {
}
