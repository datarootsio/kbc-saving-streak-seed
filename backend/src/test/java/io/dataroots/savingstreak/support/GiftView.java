package io.dataroots.savingstreak.support;

import java.time.Instant;

/**
 * A gift as the API reports one, which is exactly as a test reads it: which gift, which way it reads
 * for the customer it was fetched for, both people by identifier and by name, how many points, and
 * when.
 *
 * <p>The direction is read as the word the API sends rather than mapped onto an enum of the test's
 * own, for the reason {@link MoneyMovementView} gives: a rename in the backend should fail a test
 * rather than be quietly translated back.
 */
public record GiftView(Long id, String direction, Long senderId, String senderName, Long recipientId,
                       String recipientName, long points, Instant givenAt) {
}
