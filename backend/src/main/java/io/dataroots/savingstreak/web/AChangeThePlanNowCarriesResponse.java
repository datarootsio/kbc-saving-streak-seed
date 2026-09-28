package io.dataroots.savingstreak.web;

import io.dataroots.savingstreak.simulation.AChangeThePlanNowCarries;
import io.dataroots.savingstreak.simulation.AKindOfAdjustment;

/**
 * One line of what adopting a branch changed: which of the four changes it was about, whether the
 * application made it or it is the customer's to make, and the sentence they read.
 *
 * <p>The kind travels as the same word the request carried it in, so that a page which drew a chip
 * for a change while the branch was being built can draw the same chip against the line saying what
 * became of it. A second vocabulary for the same four things would be two vocabularies to keep in
 * step.
 *
 * <p><strong>{@code yoursToCarryOut} is a flag and not a second list.</strong> A page must be able
 * to draw the withdrawal it did not make as plainly as the capacity it did, and a shape that hid one
 * of them behind an absence would let a careless screen draw only the half that was applied — a plan
 * that quietly omits what the customer still has to do is exactly the half-told plan this resource
 * exists to prevent.
 *
 * <p>The sentence is the module's own, never assembled here. What a press did is said in the figures
 * the module wrote, and a web layer rewording it would be a second place those sentences lived.
 */
record AChangeThePlanNowCarriesResponse(AKindOfAdjustment kind, boolean yoursToCarryOut,
                                        String what) {

    static AChangeThePlanNowCarriesResponse of(AChangeThePlanNowCarries change) {
        return new AChangeThePlanNowCarriesResponse(change.kind(), change.yoursToCarryOut(),
                change.what());
    }
}
