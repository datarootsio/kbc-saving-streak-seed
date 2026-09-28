package io.dataroots.savingstreak.support;

import java.util.List;

/**
 * What the API says one press of adopt changed, which is exactly as a test reads it: the pot, the
 * branch's own name, and one line per change in the order the customer built it.
 *
 * <p>The kind is read as the word the API sends rather than mapped onto an enum of the test's own,
 * for the reason {@link MoneyMovementView} gives: a rename in the backend should fail a test rather
 * than be quietly translated back.
 *
 * <p>{@code yoursToCarryOut} is boxed, and it is the field these tests turn on hardest. Two of the
 * four kinds of change are deliberately not applied — a withdrawal is a thing a customer does on the
 * day, and pausing is what a rule's own pause already is — and a test that could not tell a line
 * saying so from a line saying a figure was written would pass whether the application had moved
 * five hundred euros or not.
 *
 * <p>{@code what} is the module's own sentence and is read as one. Nothing here re-words it: the
 * tests assert on the figures in it, because those figures are the plan the customer is being told
 * they now have.
 */
public record WhatAdoptingChangedView(Long savingsAccountId, String called,
                                      List<AChangeThePlanNowCarriesView> changes) {

    /** The lines of one kind, so that a test can say "the one about the withdrawal" and mean it. */
    public List<AChangeThePlanNowCarriesView> changesOfKind(String kind) {
        return changes.stream().filter(change -> kind.equals(change.kind())).toList();
    }

    /** One line of what adopting changed: the kind, whose it is to carry out, and the sentence. */
    public record AChangeThePlanNowCarriesView(String kind, Boolean yoursToCarryOut, String what) {
    }
}
