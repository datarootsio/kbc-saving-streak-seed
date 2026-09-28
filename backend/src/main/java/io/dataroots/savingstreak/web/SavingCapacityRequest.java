package io.dataroots.savingstreak.web;

/**
 * What a customer sends to declare the most they can put away in a week: one figure, for the savings
 * account in the path.
 *
 * <p>The figure arrives as the text that was typed rather than as a number already read for us, for
 * the reason a goal's target and a deposit's amount do: "50,00" is the mistake a Belgian page makes
 * most, and it deserves an answer about the figure rather than about the whole request being
 * unreadable. Text is the only form that still has the comma in it to name back.
 *
 * <p>There is nothing else in it. Which account it belongs to is the path, when it was declared is
 * the application's clock, and whether it clears the weekly minimum is worked out rather than
 * claimed — a request that carried the answer would be a request that could carry the wrong one.
 */
record SavingCapacityRequest(String weeklyCapacity) {
}
