package io.dataroots.savingstreak.web;

/**
 * What an owner sends to change what somebody is to a shared pot: the role that member is to hold
 * from now on, and who is making the change.
 *
 * <p>Who it is about is not in here. The member whose role it is travels in the path, because the
 * membership is the thing being changed and the path is what names it — a body carrying both people
 * would be two customers in one request with nothing but their field names to say which was which.
 *
 * <p>The role arrives as the text that was typed rather than as an enum read on the way in, for the
 * reason {@code NewPotInvitationRequest} gives: what is wrong with "TREASURER" is a fact about the
 * roles a pot has, and it deserves an answer naming the three that exist rather than a request that
 * could not be read at all.
 *
 * <p><strong>The customer travels in the body, and the application trusts it</strong>, exactly as
 * {@code NewSharedPotRequest} argues: there is no authentication here, a pot has no customer in its
 * path, and roles on a pot are real rules about a claimed identity rather than a security boundary.
 */
record PotRoleChangeRequest(String role, Long customerId) {
}
