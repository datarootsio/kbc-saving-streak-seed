package io.dataroots.savingstreak.web;

/**
 * What a customer sends to fix a goal's weekly contribution: one figure, for the goal in the path.
 *
 * <p>The figure arrives as the text that was typed rather than as a number already read for us, for
 * the reason a goal's target and a weekly capacity do: "50,00" is the mistake a Belgian page makes
 * most, and it deserves an answer about the figure rather than about the whole request being
 * unreadable. Text is the only form that still has the comma in it to name back.
 *
 * <p>There is nothing else in it. Which goal it belongs to is the path, and whether the figure fits
 * inside what the account's holder said they could save in a week is worked out rather than claimed
 * — and it is not a condition of accepting it either: a pin larger than the capacity is a legal
 * thing to say, and what it costs the goals below it is the answer rather than a refusal.
 *
 * <p>Taking it off again needs no body at all, so there is no record for that: it is a DELETE on the
 * same path.
 */
record PinnedWeeklyAmountRequest(String weeklyAmount) {
}
