package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.accounts.ABillToCome;
import io.dataroots.savingstreak.automation.AnOccurrenceToCome;
import io.dataroots.savingstreak.automation.WhatTheRulesWillDo;

/**
 * What every rule standing on one savings account will do over the coming twelve months, as the API
 * reports it: one merged list in date order, and the two days the window is drawn between.
 *
 * <p>Merged rather than grouped by rule, because "what will my rules do?" is a question about a
 * calendar: a customer reads down the year to see the mornings money leaves their current account,
 * whichever rule asked for it. Which rule it was is on each line, with its name, so that a page does
 * not have to match identifiers against the rule list by hand.
 *
 * <p>In the order the nights would fire them — by the day, and within a day by the order their
 * holder wrote the rules — so two rules falling on one morning read in the order their money will
 * actually move.
 *
 * <p><strong>{@code from} and {@code until} are sent rather than left to be inferred from the first
 * and last lines.</strong> An account whose rules do nothing for a fortnight, and an account with no
 * rules at all, would otherwise leave a page unable to say how far "nothing" reaches. They are the
 * same twelve months the account's timeline bar is drawn over, quoted from it rather than restated,
 * so that the two forward-looking screens look equally far.
 *
 * <p><strong>{@code bills} is the same twelve months going the other way</strong>, and it is what
 * makes this list decision support rather than decoration: a customer looking at March sees the rent
 * beside the sweep that would starve it. Every standing bill on every current account the holder of
 * this savings account holds, on the dates the calendar the nightly run walks says they fall on.
 *
 * <p>A second array rather than one merged one, because the two are different sentences — an
 * occurrence carries a trigger, a figure that may be an illustration and a split across goals, and a
 * bill carries a name and a figure that is never anything but exact. One array would mean rows with
 * half their fields null and a page inferring which kind it was holding. Both are in date order over
 * the same window, which is all a page needs to interleave them; interleaving two lists of the
 * backend's own days is not re-deriving anything, and each line still says for itself whether it is
 * owed or still to come.
 *
 * <p>Derived on every read and stored nowhere: change a balance, a rule, a goal or a bill and the
 * next read says something else, with nothing to invalidate.
 */
record SavingRulePreviewResponse(LocalDate from, LocalDate until,
                                 List<SavingRuleForecastResponse> occurrences,
                                 List<BillForecastResponse> bills) {

    static SavingRulePreviewResponse of(WhatTheRulesWillDo willDo) {
        return new SavingRulePreviewResponse(
                willDo.from(),
                willDo.until(),
                willDo.occurrences().stream().map(SavingRuleForecastResponse::of).toList(),
                willDo.bills().stream().map(BillForecastResponse::of).toList());
    }

    /**
     * One date a bill is going to fall due on, and what it will take when it does.
     *
     * <p>{@link BillOccurrenceResponse} in the future tense, and a different shape for the reason
     * the two tenses are different sentences: that one carries a moment it was settled at and an
     * outcome, and neither of those exists yet.
     *
     * <p>{@code billName} travels with the date so that the year ahead reads as rent and phone bill
     * rather than as identifiers, and {@code currentAccountId} is here because a household with two
     * current accounts has claims on both and a page showing them together has to be able to say
     * which is which.
     *
     * <p><strong>{@code owedRatherThanStillToCome} says which of the two kinds of line this
     * is</strong>, for the same reason and in the same words as the occurrences above: a bill is
     * forecast from its own cursor rather than from today, so one the 02:30 run has not caught up
     * with carries the dates it is late for at the head of the list. Sent rather than left to be
     * inferred, because a page comparing each day against its own idea of today would be re-deriving
     * a boundary this application has already drawn against its own clock.
     */
    record BillForecastResponse(long billId, long currentAccountId, String billName,
                                LocalDate dueOn, BigDecimal amount,
                                boolean owedRatherThanStillToCome) {

        static BillForecastResponse of(ABillToCome toCome) {
            return new BillForecastResponse(toCome.billId(), toCome.currentAccountId(),
                    toCome.billName(), toCome.dueOn(), toCome.amount(),
                    toCome.owedRatherThanStillToCome());
        }
    }

    /**
     * One day a rule is going to fall due on, and what it would move when it does.
     *
     * <p>{@link SavingRuleOccurrenceResponse} in the future tense, and a different shape for the
     * reason the two tenses are different sentences: that one carries a moment it was settled at, an
     * outcome, a deposit and a lateness, and every one of those is a thing that happened. None of
     * them exists yet here, and a record with half its fields null would leave a page unable to tell
     * "this has not happened" from "this happened and moved nothing".
     *
     * <p>The trigger and the kind of amount travel as their words, so a page decides what to call
     * each kind from a word rather than by inferring it from whichever figure is not null.
     *
     * <p><strong>{@code owedRatherThanStillToCome} says which of the two kinds of line this is.</strong>
     * Every rule is forecast from its own cursor rather than from today, so a rule the nightly run
     * has not caught up with carries the mornings it is late for at the head of the list, dated
     * before {@code from}. Those are transfers the <em>next</em> run will make for a day already
     * past; the rest are transfers a night in the future will make on the day it names. A page shows
     * the two differently — "these two already fell and the next run will make them" is a different
     * sentence from "this is due next Monday" — and it is sent rather than left to be inferred,
     * because a page comparing each day against its own idea of today would be re-deriving a
     * boundary this application has already drawn against its own clock.
     */
    record SavingRuleForecastResponse(long ruleId, String ruleName, LocalDate dueOn, String trigger,
                                      String howMuchMoves,
                                      SavingRuleWouldMoveResponse wouldMove,
                                      boolean owedRatherThanStillToCome) {

        static SavingRuleForecastResponse of(AnOccurrenceToCome toCome) {
            return new SavingRuleForecastResponse(
                    toCome.ruleId(),
                    toCome.ruleName(),
                    toCome.dueOn(),
                    toCome.trigger().name(),
                    toCome.howMuchMoves().name(),
                    SavingRuleWouldMoveResponse.of(toCome.wouldMove()),
                    toCome.owedRatherThanStillToCome());
        }
    }
}
