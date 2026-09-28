package io.dataroots.savingstreak.web;

/**
 * What a member sends to answer a withdrawal proposal: who is answering, and nothing else.
 *
 * <p>Which way they went is in the path rather than in here, for the reason the two endpoints give:
 * approving and rejecting are different acts with different consequences, and a word in a body that
 * could be misspelled is a poor place to keep the difference between spending somebody's euros and
 * not.
 *
 * <p><strong>The customer travels in the body, and the application trusts it</strong>, exactly as
 * {@code NewWithdrawalProposalRequest} argues: there is no authentication here, a pot has no
 * customer in its path, and roles on a pot are real rules about a claimed identity rather than a
 * security boundary.
 *
 * <p>A record of one field rather than a bare identifier, so that an answer that comes to carry
 * something else — a note to the other members, say — does not change the shape of the request
 * underneath whoever was already sending it.
 */
record AnswerToAProposalRequest(Long customerId) {
}
