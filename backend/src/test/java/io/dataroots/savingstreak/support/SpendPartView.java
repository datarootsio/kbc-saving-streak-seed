package io.dataroots.savingstreak.support;

import java.math.BigDecimal;

/**
 * One part of a split as the API reports it: how much of the spend it accounts for, and what it is
 * filed under.
 *
 * <p>Both {@code categoryId} and {@code categoryName} are null on an uncategorised part, which is a
 * state a customer chooses rather than a gap — and a test asserting that a spend can be half filed
 * and half not reads exactly that.
 */
public record SpendPartView(Long categoryId, String categoryName, BigDecimal amount) {
}
