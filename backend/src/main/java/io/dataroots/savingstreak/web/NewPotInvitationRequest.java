package io.dataroots.savingstreak.web;

/**
 * What an owner sends to invite somebody into a shared pot: the email address that person banks
 * under, the role the invitation grants, and who is sending it.
 *
 * <p>The address rather than a customer number, for the reason a gift is addressed that way: one
 * person knows another's email address and knows nothing about their customer number, and an
 * application that asked for the number would be asking them to go and find something out first.
 *
 * <p>The role arrives as the text that was typed rather than as an enum read on the way in, for the
 * reason {@code NewGoalRequest} gives about a figure: what is wrong with "TREASURER" is a fact about
 * the roles a pot has, and it deserves an answer naming the three that exist rather than a request
 * that could not be read at all.
 *
 * <p><strong>The customer travels in the body, and the application trusts it</strong>, exactly as
 * {@code NewSharedPotRequest} argues: there is no authentication here, a pot has no customer in its
 * path, and roles on a pot are real rules about a claimed identity rather than a security boundary.
 */
record NewPotInvitationRequest(String contactDetails, String role, Long customerId) {
}
