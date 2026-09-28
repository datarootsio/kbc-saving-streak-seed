package io.dataroots.savingstreak.web;

/**
 * What a customer sends to move money between what their goals have claimed: how much, which way
 * with respect to the goal in the path, and — when the money is not coming from or going back to the
 * part of the balance no goal has claimed — the goal at the other end.
 *
 * <p><strong>A direction rather than a sign.</strong> Every amount in this application is positive,
 * and "take 50.00 out of the holiday" is a different instruction from "put 50.00 towards it" rather
 * than the same one with a minus in front. A sign is one typo away from meaning the opposite of what
 * somebody meant, and a refusal quoting {@code -50.00} teaches them nothing.
 *
 * <p>{@code otherGoalId} absent is the ordinary case and means the part of the balance no goal has
 * claimed: money going into a goal comes from there, money coming out of one goes back there. Naming
 * a goal is how a customer moves money straight from one goal to another without it ever being spare
 * in between.
 *
 * <p>The amount arrives as the text that was typed, for the reason a deposit's does: "25,00" is a
 * mistake somebody makes and deserves an answer about the amount rather than about the request being
 * unreadable, and text is the only form that still has the characters in it.
 */
record AllocationRequest(String amount, String direction, Long otherGoalId) {
}
