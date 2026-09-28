package io.dataroots.savingstreak.support;

/**
 * One line of a rule's split as the API reports it, which is exactly as a test reads it: which goal,
 * and what whole percentage of whatever the rule moves it is offered.
 *
 * <p>The order of the list is the order the customer wrote the split, and there is no field saying
 * so — which is why a test asserts on the list rather than on any line's position.
 *
 * <p>What is offered rather than what a firing placed. What each goal actually took is on the
 * occurrence, and the two being different records is the whole point: a goal that is nearly complete
 * takes part of its share and the rest spills.
 */
public record SavingRuleSplitView(long goalId, int share) {
}
