package io.dataroots.savingstreak.sharedpots;

import java.time.Instant;

/**
 * An invitation to a shared pot as the rest of the application sees one: which pot and what it is
 * called, who asked and who was asked, what role it offers, where it stands, and when it was sent
 * and answered.
 *
 * <p>One shape for the invitation a pot lists, the invitation waiting for a customer and the
 * invitation an answer hands straight back, so that nothing rendering one has to know which of those
 * it is holding in order to render it.
 *
 * <p><strong>Both names and the pot's name travel with it</strong>, because the two lists this
 * answers are lists a person reads. "Anke Peeters is asking you to save into Kitchen" is the
 * sentence an invitation panel writes, and a caller that had to ask Accounts for two names and
 * Shared Pots for a pot in order to write it would be making three more calls to say one thing. Who
 * is called what is the Accounts module's answer and is looked up there; nothing about a name is
 * stored on an invitation, so a customer who is renamed is renamed in every invitation they were
 * ever part of.
 *
 * <p>The role and the state travel as members of their enums rather than as words, because inside
 * the application they are decisions and not labels — what an acceptance grants is read off the
 * first, and whether an invitation may be answered at all off the second. The word is what the web
 * layer sends.
 *
 * <p>{@code answeredAt} is null for as long as the invitation is waiting, which is the one absence
 * on this record and the one that says nobody has answered it.
 *
 * <p>The stored invitation stays inside the module; this is a statement about somebody having been
 * asked something.
 */
public record APotInvitation(Long id, Long potId, String potName, Long invitedByCustomerId,
                             String invitedByName, Long invitedCustomerId, String invitedName,
                             PotRole role, InvitationState state, Instant invitedAt,
                             Instant answeredAt) {
}
