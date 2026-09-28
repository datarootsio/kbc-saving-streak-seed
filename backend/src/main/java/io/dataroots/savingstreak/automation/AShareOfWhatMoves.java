package io.dataroots.savingstreak.automation;

/**
 * One line of a rule's split: this goal gets this many whole percent of whatever the rule moves.
 *
 * <p>The order the customer wrote them in is the order of the list these travel in, and it is not a
 * field here. It is a fact about the list rather than about any one line, and a number on each line
 * would be a second copy of it — one that a caller could hand in out of step with the order it sent
 * them in, leaving this module to decide which of the two the customer meant.
 *
 * <p>A whole percentage rather than an amount, because a rule that moves a sweep does not know its
 * own amount until the night it fires. The shares on one rule add to a hundred; what that comes to
 * in cents is {@link HowAnAmountIsSplit}'s answer, on the night, out of what actually moved.
 *
 * <p>Public, unlike {@link RuleSplit}, because it is what a rule is asked for and answered with: the
 * web layer names it in both directions.
 */
public record AShareOfWhatMoves(Long goalId, Integer share) {
}
