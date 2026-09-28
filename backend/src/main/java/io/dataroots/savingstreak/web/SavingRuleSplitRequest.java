package io.dataroots.savingstreak.web;

/**
 * One line of a split as a customer fills it in: which goal, and what share of whatever the rule
 * moves it gets.
 *
 * <p>The order the lines arrive in is the order the customer wrote the split, and it is not a field
 * here. It settles which goal a leftover cent goes to and which goal a share spills to, and a number
 * on each line would be a second copy of an order the list already has — one a page could send out
 * of step with itself, leaving the application to decide which of the two the customer meant.
 *
 * <p>The share arrives as the text that was typed rather than as a number already read for us, for
 * the reason a day of the month does: "sixty" and "60%" are mistakes somebody makes, and they
 * deserve an answer about the share rather than about the request being unreadable. Whether the
 * characters are a whole number at all is answered in the controller; whether that number is a share
 * this application will keep, and whether the shares add to a hundred, belong to Automation.
 *
 * <p>The goal arrives as an identifier, like the current account a rule draws from, because nobody
 * types it: a page sends back one of the goals it listed.
 */
record SavingRuleSplitRequest(Long goalId, String share) {
}
