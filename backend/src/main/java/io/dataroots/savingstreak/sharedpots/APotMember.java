package io.dataroots.savingstreak.sharedpots;

import java.time.Instant;

/**
 * One member of a shared pot, as the rest of the application sees them: which customer, what they
 * are called, what they are to the pot, and when they joined it.
 *
 * <p>The name as well as the identifier, because a list of members should read as people. Who is
 * called what is the Accounts module's answer and is looked up there; nothing about a name is stored
 * on a membership, so a customer who is renamed is renamed in every pot they belong to.
 *
 * <p>The role travels as a member of {@link PotRole} rather than as a word, because inside the
 * application it is a decision and not a label — what may be done to a pot is read off it. The word
 * is what the web layer sends, the way a goal's state and a gift's direction are.
 *
 * <p>The stored membership stays inside the module; this is a statement about somebody belonging to
 * something.
 */
public record APotMember(Long customerId, String name, PotRole role, Instant joinedAt) {
}
