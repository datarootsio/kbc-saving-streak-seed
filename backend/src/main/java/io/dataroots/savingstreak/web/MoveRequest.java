package io.dataroots.savingstreak.web;

/**
 * What a customer types to move money from one of their savings accounts to another of their own:
 * how much, and which account it is going to.
 *
 * <p>The account it comes <em>out</em> of is not in here, because it is in the path: a move is
 * addressed under the account it leaves, the way a withdrawal and a deposit are addressed under the
 * account they touch. One of the two accounts in the URL and the other in the body is the shape
 * every other two-ended request in this API already takes.
 *
 * <p>The amount travels as a string, as every amount in this API does, so that what the customer
 * typed reaches the domain to be judged rather than being silently reshaped by a JSON parser: a
 * figure with three decimal places is a refusal with a sentence, and a number would have quietly
 * become a double first.
 */
record MoveRequest(String amount, Long toSavingsAccountId) {
}
