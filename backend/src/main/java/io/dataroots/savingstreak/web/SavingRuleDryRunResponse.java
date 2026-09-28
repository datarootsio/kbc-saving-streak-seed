package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.dataroots.savingstreak.automation.ADryRun;

/**
 * What a rule nobody has saved would do if it fired this minute, as the API reports it.
 *
 * <p>The preview a customer wants before pressing save rather than after. Asking for it writes
 * nothing at all — no rule, no occurrence, no deposit, no point — so a page can ask for it while
 * somebody is still making their mind up, on every keystroke if it likes.
 *
 * <p><strong>A sweep is a promise here rather than an illustration</strong>, which is the one thing
 * this answers that the twelve-month preview cannot: the question is about today, so the balance is
 * the one in the account right now and the figure is what would actually move.
 * {@code balance} is sent beside it so the subtraction can be read rather than taken on trust.
 *
 * <p><strong>{@code outcome} is the same word the occurrence would carry.</strong> A rule asking for
 * more than the account holds says {@code NOT_ENOUGH_MONEY} and fills {@code shortfall}, because a
 * fixed amount moves all of itself or none of it; a sweep whose account is already at or under its
 * floor says {@code NOTHING_TO_MOVE}, which is arithmetic rather than a failure. Being told which of
 * those a rule would be <em>before</em> committing to it is the whole point of asking.
 *
 * <p>{@code shortfall} is null on the other two outcomes rather than nought, for the reason an
 * occurrence's is: "short of nothing" and "short of EUR 0,00" are different sentences.
 *
 * <p>A rule this application would refuse to keep is refused here too, in the same sentences and
 * with the same status — so a dry run never answers for a rule the save would then turn down.
 *
 * <p>{@code asAt} is the day the application's clock reads, so a trainer who has wound it can see
 * which day the answer is about.
 */
record SavingRuleDryRunResponse(LocalDate asAt, BigDecimal balance, String outcome,
                                SavingRuleWouldMoveResponse wouldMove, BigDecimal shortfall) {

    static SavingRuleDryRunResponse of(ADryRun dryRun) {
        return new SavingRuleDryRunResponse(
                dryRun.asAt(),
                dryRun.balance(),
                dryRun.outcome().name(),
                SavingRuleWouldMoveResponse.of(dryRun.wouldMove()),
                dryRun.shortfall());
    }
}
