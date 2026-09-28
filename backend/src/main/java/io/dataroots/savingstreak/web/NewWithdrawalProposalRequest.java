package io.dataroots.savingstreak.web;

/**
 * What a member sends to propose taking money out of a shared pot: how much, which of their own
 * current accounts it should come back to, and who is proposing it.
 *
 * <p>The amount arrives as the text that was typed rather than as a number already read for us, for
 * the reason {@code NewGoalRequest} gives: "2500,00" is the mistake a Belgian page makes most, and
 * it deserves an answer about the figure rather than about the request being unreadable. Text is the
 * only form that still has the characters in it.
 *
 * <p>The current account is an identifier because it is one the page already holds — it is the
 * member's own account, off their own overview — and because what is wrong with somebody else's
 * account number is a rule about whose money goes where rather than anything about what was typed.
 *
 * <p><strong>The customer travels in the body, and the application trusts it</strong>, exactly as
 * {@code NewPotInvitationRequest} argues: there is no authentication here, a pot has no customer in
 * its path, and roles on a pot are real rules about a claimed identity rather than a security
 * boundary.
 */
record NewWithdrawalProposalRequest(String amount, Long toCurrentAccountId, Long customerId) {
}
