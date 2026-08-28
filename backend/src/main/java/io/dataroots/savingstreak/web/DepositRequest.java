package io.dataroots.savingstreak.web;

/**
 * What a customer fills in to make a deposit: how much, and which of their current accounts it comes
 * from. Where it goes is the savings account in the path.
 *
 * <p>The amount arrives as the text that was typed rather than as a number already read for us. Some
 * of what the application has to say about an amount is about the characters — "25,00" is a mistake
 * somebody makes and deserves an answer about the amount, not about the request being unreadable —
 * and text is the only form that still has them.
 */
record DepositRequest(String amount, Long fromCurrentAccountId) {
}
