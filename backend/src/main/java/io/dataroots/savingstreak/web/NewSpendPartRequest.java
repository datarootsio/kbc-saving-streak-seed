package io.dataroots.savingstreak.web;

/**
 * One part of a split as the page sends it: how much of the spend it accounts for, and which
 * category it goes under — or none at all.
 *
 * <p>{@code categoryId} absent or null means <em>uncategorised</em>, which is a state a customer
 * chooses rather than a field they forgot. Somebody in a hurry records the spend now and files it
 * later, and a form that insisted on a category here would make recording the spend at all the hard
 * part.
 *
 * <p>The amount arrives as the text that was typed, like every other figure in this application's
 * requests, and for the same reason: the characters are what the refusal has to name back.
 */
record NewSpendPartRequest(Long categoryId, String amount) {
}
