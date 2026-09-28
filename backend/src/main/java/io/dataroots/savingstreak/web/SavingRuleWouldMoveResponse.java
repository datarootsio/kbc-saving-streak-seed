package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.util.List;

import io.dataroots.savingstreak.automation.WhatWouldMove;

/**
 * What a rule would move, as honestly as it can be said in advance, and where it would land.
 *
 * <p><strong>{@code amount} is never read without
 * {@code anIllustrationRatherThanAPromise}.</strong> A fixed-amount rule quotes a figure a customer
 * can add twelve months of up; a sweep cannot, because what it moves next March depends on a balance
 * nobody has yet. So a sweep quotes its {@code floor} — which is knowable, because the customer said
 * it — and today's balance worked through as an <em>illustration</em>, with the flag set. A page
 * that shows the figure and drops the flag is a page promising something this application did not
 * say, and a confident figure for a sweep twelve months out is a fiction.
 *
 * <p>The flag is false on both kinds in a dry run, because that question is about today and there is
 * nothing left to be uncertain about: the balance is the one in the account right now.
 *
 * <p>{@code floor} is filled exactly on a sweep. The kind of rule is on the line around this one, so
 * a page reads the word rather than inferring the kind from whichever figure is not null.
 *
 * <p><strong>{@code intoGoals} and {@code leftUnallocated} add to {@code amount} exactly</strong>,
 * the way an occurrence's pair adds to what moved — the same function works out both, so a preview
 * and the transfer it predicts cannot disagree about the cents. What they do not claim is which goal
 * will still have room on the day: that is what {@code intoGoals} on the occurrence reports, after
 * the fact.
 *
 * <p>{@code intoGoals} is empty and {@code leftUnallocated} is the whole figure on a rule with no
 * split, which is what such a rule does: it deposits unallocated, exactly as a manual deposit does.
 *
 * <p>Every figure is quoted to the cent, because they have been through SQLite, which has no decimal
 * type and holds an amount as a float.
 */
record SavingRuleWouldMoveResponse(BigDecimal amount, BigDecimal floor,
                                   boolean anIllustrationRatherThanAPromise,
                                   List<SavingRuleGoalShareResponse> intoGoals,
                                   BigDecimal leftUnallocated) {

    static SavingRuleWouldMoveResponse of(WhatWouldMove wouldMove) {
        if (wouldMove == null) {
            return null;
        }
        return new SavingRuleWouldMoveResponse(
                wouldMove.amount(),
                wouldMove.floor(),
                wouldMove.anIllustrationRatherThanAPromise(),
                wouldMove.intoGoals().stream().map(SavingRuleGoalShareResponse::of).toList(),
                wouldMove.leftUnallocated());
    }
}
