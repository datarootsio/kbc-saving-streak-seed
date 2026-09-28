package io.dataroots.savingstreak.web;

import java.util.List;

import io.dataroots.savingstreak.simulation.WhatAdoptingAScenarioChanged;

/**
 * The answer to the one press that turns a branch into a plan: which pot, which branch, and exactly
 * what changed.
 *
 * <p><strong>What changed, and not the account as it now stands.</strong> A customer who has just
 * pressed once wants to know what that press did; the figures it moved are read from the screens
 * that own them, which are the screens they would have typed those figures into. A capacity or a
 * deadline riding back on this answer would be a second copy of a figure Goals owns, and the page
 * that read it would be drawing a plan from the wrong place the first time the two disagreed.
 *
 * <p><strong>Nothing here says it is an illustration, deliberately.</strong> Every other answer this
 * resource gives is a projection of a balance nobody has yet and says so once at the top. This one
 * is a record of writes that have happened and carries no such word — a customer told their capacity
 * is now sixty euros a week must not read it as a worked example.
 *
 * <p><strong>All of it or none of it, so there is no half-answer shape.</strong> A refusal is a
 * problem detail naming the part that caused it, in the owning module's own sentence, and nothing at
 * all was applied behind it. There is no field here for "these went through and that one did not",
 * because that state does not exist.
 */
record WhatAdoptingChangedResponse(long savingsAccountId, String called,
                                   List<AChangeThePlanNowCarriesResponse> changes) {

    /**
     * The plan as the module reported it, line by line and in the order the customer built the
     * branch in.
     *
     * <p>Nothing is reordered and nothing is filtered — least of all the lines naming what is still
     * the customer's to carry out, which are the ones a page most needs and would most easily drop.
     */
    static WhatAdoptingChangedResponse of(WhatAdoptingAScenarioChanged adopted) {
        return new WhatAdoptingChangedResponse(adopted.savingsAccountId(), adopted.called(),
                adopted.changes().stream().map(AChangeThePlanNowCarriesResponse::of).toList());
    }
}
