package io.dataroots.savingstreak.web;

/**
 * What a customer sends to open a savings goal: what to call it, what it is worth, and the day they
 * want it by. Which account it goes on is the one in the path, and where it sits in the order is not
 * asked for — a new goal is ranked last, and reordering is its own call.
 *
 * <p>The target arrives as the text that was typed rather than as a number already read for us, for
 * the reason a deposit's amount does: "2500,00" is the mistake a Belgian page makes most, and it
 * deserves an answer about the figure rather than about the request being unreadable. Text is the
 * only form that still has the characters in it.
 *
 * <p>The deadline is optional, and absent means a goal with no day — an emergency fund genuinely has
 * a target and no date. It is text for the same reason, so that a day that is not a day comes back
 * as a sentence about the day.
 */
record NewGoalRequest(String name, String target, String deadline) {
}
