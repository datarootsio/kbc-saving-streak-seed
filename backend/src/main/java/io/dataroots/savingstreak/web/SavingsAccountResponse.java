package io.dataroots.savingstreak.web;

import java.math.BigDecimal;

/**
 * A savings account and what it is worth, in the two currencies the customer cares about: the money
 * they have saved and the points that saving earned them, side by side because the point of the
 * application is the connection between the two.
 */
record SavingsAccountResponse(Long id, String customerName, BigDecimal moneyBalance, long pointsBalance) {
}
