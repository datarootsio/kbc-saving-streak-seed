package io.dataroots.savingstreak.support;

import java.time.Instant;

/**
 * One member of a shared pot as the API reports them, which is exactly as a test reads them: which
 * customer, what they are called, what they are to the pot, and since when.
 *
 * <p>The role is read as the word the API sends rather than mapped onto an enum of the test's own,
 * for the reason {@link MoneyMovementView} gives: a rename in the backend should fail a test rather
 * than be quietly translated back.
 */
public record PotMemberView(Long customerId, String name, String role, Instant joinedAt) {
}
