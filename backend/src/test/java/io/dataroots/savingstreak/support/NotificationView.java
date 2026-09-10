package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A notification as the API reports one, which is exactly as a test reads it: which rule spoke, the
 * pot and the deposit it was about, the figures behind it, when it was raised and when it was read.
 *
 * <p>The reason is read as the word the API sends rather than mapped onto the domain's own enum, for
 * the reason {@link MoneyMovementView} gives about a direction: a rename in the backend should fail a
 * test rather than be quietly translated back into the name the test meant. The page switches on that
 * word too, so a test asserting on it is asserting on the contract the frontend reads.
 *
 * <p>Which figures are filled in depends on the reason, and a test asserting that the ones that do
 * not apply came back null is asserting a real part of the contract — a balance rung is about an
 * account and about no one deposit, and a coming anniversary is about one deposit and names no rung.
 */
public record NotificationView(Long id, String reason, Long savingsAccountId, Long depositId,
                               BigDecimal amount, Long points, LocalDate occursOn, Instant raisedAt,
                               Instant readAt) {
}
