package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A member's departure from a shared pot as the API reports it, which is exactly as a test reads it:
 * who left, what they were to the pot, what was still theirs, where it went, and what the pot holds
 * once they have gone.
 *
 * <p>{@code settled} is the whole subject of the slice: that member's own remaining euros, exact to
 * the cent, with no share arithmetic anywhere near it. A test asserting on it is asserting the thing
 * the feature promises.
 *
 * <p>{@code withdrawalId} is null when nothing moved, which is how a test tells "they were settled
 * nothing" from "they were settled something": leaving with nothing in the pot is an ordinary
 * departure and records no movement of money at all.
 *
 * <p>The role is read as the word the API sends rather than mapped onto an enum of the test's own,
 * for the reason {@link PotMemberView} gives: a rename in the backend should fail a test rather than
 * be quietly translated back.
 */
public record PotSettlementView(Long potId, String potName, Long customerId, String name,
                                String role, Instant joinedAt, BigDecimal settled,
                                Long toCurrentAccountId, Long withdrawalId, Instant settledAt,
                                BigDecimal thePotNowHolds) {
}
