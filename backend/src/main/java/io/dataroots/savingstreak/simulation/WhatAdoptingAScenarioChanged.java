package io.dataroots.savingstreak.simulation;

import java.util.List;

/**
 * The answer to the one press that turns a branch into the plan a customer has decided on: which
 * branch it was, and exactly what changed — line by line, in the order the customer built it.
 *
 * <p><strong>What changed rather than what the account now holds.</strong> A customer who has just
 * pressed once is not asking for their account back; they are asking what that press did, and the
 * honest answer to that is a list of the presses it made on their behalf. The figures those presses
 * moved are read from the screens that own them, which are the same screens the customer would have
 * typed them into, and a second copy of a capacity or a deadline travelling back on this answer
 * would be a second place either could be read wrong.
 *
 * <p><strong>The branch's own name travels back.</strong> Four columns are four arguments, and
 * somebody who has just adopted one is owed the words they chose for it rather than the
 * application's summary of what those words meant. It also makes the log line and the answer say
 * the same thing, which is the whole of why a scenario has a name at all.
 *
 * <p><strong>A scenario made of nothing adopts to nothing, and that is not a refusal.</strong> An
 * empty list is a true and complete account of what pressing adopt on an empty branch did, and it
 * comes back as one rather than as an objection: the branch a customer built out of no changes is
 * the year they are already in, and deciding to carry on as they are is a decision this application
 * has nothing to write down.
 *
 * <p>Nothing here is an illustration. Every other answer this module gives is a projection of a
 * balance nobody has yet and says so at the top; this one is a record of writes that have happened,
 * and it deliberately does not carry that word — a customer who has just been told their capacity is
 * now sixty euros a week must not read it as a guess.
 *
 * @param savingsAccountId the pot the plan is now on, named because a customer holds more than one
 * @param called           the customer's own word for the branch they adopted
 * @param changes          one line per change in the branch, in the order it was built, each saying
 *                         what was done or what is theirs to do
 */
public record WhatAdoptingAScenarioChanged(long savingsAccountId, String called,
                                           List<AChangeThePlanNowCarries> changes) {

    /** The lines are copied, for the reason every list in this module is: an answer cannot be edited. */
    public WhatAdoptingAScenarioChanged {
        changes = List.copyOf(changes);
    }
}
