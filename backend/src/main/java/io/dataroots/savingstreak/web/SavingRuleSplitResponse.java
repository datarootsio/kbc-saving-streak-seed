package io.dataroots.savingstreak.web;

import io.dataroots.savingstreak.automation.AShareOfWhatMoves;

/**
 * One line of a rule's split as the API reports it: which goal, and what whole percentage of
 * whatever the rule moves it is offered.
 *
 * <p>The order of the list is the order the customer wrote the split, and there is no field saying
 * so. It is a fact about the list rather than about any one line, and a page that re-sorted these
 * would be re-wording somebody's instruction.
 *
 * <p>What is offered rather than what any firing placed. A goal that is nearly complete takes part
 * of its share and the rest spills to the next goal, and that is a fact about a night rather than
 * about the rule — it travels on the occurrence, where the record of what happened lives.
 *
 * <p>A goal that has been given up on since the split was written is still here. Taking it out on
 * the customer's behalf would quietly re-word what they said, and a page has the goal list beside
 * this to say what became of it.
 */
record SavingRuleSplitResponse(long goalId, int share) {

    static SavingRuleSplitResponse of(AShareOfWhatMoves share) {
        return new SavingRuleSplitResponse(share.goalId(), share.share());
    }
}
