package io.dataroots.savingstreak.support;

import java.time.Instant;

/**
 * An invitation to a shared pot as the API reports one, which is exactly as a test reads it: which
 * pot and what it is called, who sent it and who it went to, what role it offers, where it stands,
 * and when it was sent and answered.
 *
 * <p>One shape for the invitation a pot lists, the invitation waiting for a customer and the
 * invitation an answer hands straight back — which is the API's own promise, and reading all three
 * as one record is what would fail if it ever stopped being true. Shared by every test that reads an
 * invitation, so that none of them can drift into disagreeing about the shape of the answer.
 *
 * <p>The pot's name and both people's names travel beside their identifiers, because the two pages
 * this answers are lists a person reads: "Anke is asking you to save into Kitchen" is the sentence,
 * and a page that had to look up two names and a pot to write it would be making three more calls to
 * say one thing.
 *
 * <p>The role and the state are read as the words the API sends rather than mapped onto enums of the
 * test's own, for the reason {@link PotMemberView} gives: a rename in the backend should fail a test
 * rather than be quietly translated back.
 *
 * <p>{@code answeredAt} is null while the invitation is still waiting, which is the absence that
 * says nobody has answered it — and the only field on here that is ever null.
 */
public record PotInvitationView(Long id, Long potId, String potName, Long invitedByCustomerId,
                                String invitedByName, Long invitedCustomerId, String invitedName,
                                String role, String state, Instant invitedAt, Instant answeredAt) {
}
