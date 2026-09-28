package io.dataroots.savingstreak.web;

/**
 * What a customer sends when they answer an invitation to a shared pot: who is answering, and
 * nothing else.
 *
 * <p>Nothing else on purpose. What the answer grants is the invitation's own — the role it named
 * when it was sent — and an answer that could carry a role of its own would let somebody accept as
 * an owner an invitation that offered them a look. Which way the answer goes is the path: accepting
 * and declining are two endpoints rather than one endpoint with a word in its body, because a
 * request that says what it does is a request nobody can send by mistake.
 *
 * <p>The same shape serves both, because both are the same act by the same person.
 *
 * <p>The customer travels in the body, and the application trusts it, for the reason
 * {@code NewSharedPotRequest} sets out at length.
 */
record PotInvitationAnswerRequest(Long customerId) {
}
