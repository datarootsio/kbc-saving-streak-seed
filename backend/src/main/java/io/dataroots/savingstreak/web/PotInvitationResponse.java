package io.dataroots.savingstreak.web;

import java.time.Instant;

import io.dataroots.savingstreak.sharedpots.APotInvitation;

/**
 * An invitation to a shared pot as the API reports one: which pot and what it is called, who asked
 * and who was asked, what role it offers, where it stands, and when it was sent and answered.
 *
 * <p>One shape for the invitation a pot lists, the invitation waiting for a customer and the
 * invitation an answer hands straight back, so that nothing rendering one has to know which of those
 * it is holding.
 *
 * <p>Both names and the pot's name travel beside their identifiers, because the two lists this
 * answers are lists a person reads: "Anke Peeters is asking you to save into Kitchen" is the
 * sentence, and a page that had to make three more calls to write it would be three round trips
 * short of saying one thing. The names are looked up when the invitation is read and never stored on
 * it, so somebody renamed is renamed in every invitation they were ever part of.
 *
 * <p>The role and the state travel as their own words, the idiom a goal's state and a gift's
 * direction already set: whoever renders it decides what to call {@code PENDING} and which of
 * {@code OWNER}, {@code CONTRIBUTOR} and {@code VIEWER} to put a padlock beside.
 *
 * <p>{@code answeredAt} is null while the invitation is still waiting, which is the absence that
 * says nobody has answered it — and it is not an expiry: nothing in this application expires an
 * invitation, so there is no date here for when it would have.
 */
record PotInvitationResponse(Long id, Long potId, String potName, Long invitedByCustomerId,
                             String invitedByName, Long invitedCustomerId, String invitedName,
                             String role, String state, Instant invitedAt, Instant answeredAt) {

    static PotInvitationResponse of(APotInvitation invitation) {
        return new PotInvitationResponse(invitation.id(), invitation.potId(), invitation.potName(),
                invitation.invitedByCustomerId(), invitation.invitedByName(),
                invitation.invitedCustomerId(), invitation.invitedName(),
                invitation.role().name(), invitation.state().name(), invitation.invitedAt(),
                invitation.answeredAt());
    }
}
