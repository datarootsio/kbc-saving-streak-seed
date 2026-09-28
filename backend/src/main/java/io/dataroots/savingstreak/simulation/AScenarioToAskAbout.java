package io.dataroots.savingstreak.simulation;

import java.util.List;

/**
 * One question a customer is asking: what they call it, and the changes it is made of.
 *
 * <p><strong>A name and a list, and nothing else.</strong> A scenario is not a thing this
 * application keeps: it is a question in a request body, folded once and thrown away, which is why
 * it has no identifier and why there is nothing here to ask about again later. The name is the
 * customer's own words, because four columns on a screen are four arguments and somebody choosing
 * between them is choosing between the words they typed rather than between "scenario 2" and
 * "scenario 3".
 *
 * <p><strong>A list, because changes compose.</strong> Two changes in one scenario are two changes —
 * five hundred out in March and twenty-five more a week from April is one question about one future,
 * and the fold asks every change every question it asks of any of them. Nothing here reorders them
 * or decides that one kind cancels another; they are asked in the order they were typed.
 *
 * <p><strong>The name is not optional, and that is a rule rather than a validation habit.</strong>
 * Four of these come back as four columns beside the one nobody asked for, and a column with no
 * heading cannot be compared against the one next to it — nor can a refusal about anything else in
 * the question point at it. A scenario nobody named is therefore refused, in
 * {@link WhatMayBeAskedAtOnce}, which is also where how many scenarios one asking may carry and how
 * many changes one scenario may be made of are written down with their reasoning. This record is the
 * shape all three are counted in, and it counts nothing itself: a record that refused its own
 * arguments would refuse them again in every test that ever built one by hand.
 */
public record AScenarioToAskAbout(String called, List<AnAdjustment> adjustments) {

    /** Copied on the way in, so that a question cannot change while it is being answered. */
    public AScenarioToAskAbout {
        adjustments = List.copyOf(adjustments);
    }
}
