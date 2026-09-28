package io.dataroots.savingstreak.automation;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * When each rule was paused and when it was resumed, one row per pause.
 *
 * <p>Every read is scoped by a rule, and which rules a customer may ask about is settled before a
 * read ever reaches here — {@link SavingRuleRepository} says why that is a rule rather than a
 * convention.
 *
 * <p>Package-private, like the row it reads: the rest of the application goes through
 * {@link AutomationService}.
 */
interface RulePauseRepository extends JpaRepository<RulePause, Long> {

    /**
     * Every pause a rule has ever had, oldest first.
     *
     * <p>All of them rather than the last one, because the days a run is offered can fall in any of
     * them: a payday rule's days come out of the record of salaries credited, and a salary credited
     * late for a day inside a pause two pauses ago arrives with nothing else to exclude it.
     */
    List<RulePause> findBySavingRuleIdOrderByIdAsc(long savingRuleId);

    /**
     * Every pause on a set of rules, so that a night walking every standing rule asks this once
     * rather than once per rule.
     *
     * <p>Ordered by rule and then oldest first, so the caller can group them without sorting.
     */
    List<RulePause> findBySavingRuleIdInOrderBySavingRuleIdAscIdAsc(List<Long> savingRuleIds);

    /**
     * The pause a rule is in the middle of, or nothing at all when it is not in one.
     *
     * <p>The newest open one, because there can only be one: a rule is paused from {@code LIVE} and
     * resumed from {@code PAUSED}, so a second open row would mean the state and the record had
     * parted company. Taking the newest is what makes that disagreement recoverable rather than
     * ambiguous, and {@code AutomationService} says so in a WARN when it finds none at all.
     */
    Optional<RulePause> findFirstBySavingRuleIdAndEndedAtIsNullOrderByIdDesc(long savingRuleId);
}
