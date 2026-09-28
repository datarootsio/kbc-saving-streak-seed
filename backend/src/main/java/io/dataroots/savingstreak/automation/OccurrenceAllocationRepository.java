package io.dataroots.savingstreak.automation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Where each firing's money went, one row per goal that took something.
 *
 * <p>Every read is scoped by the occurrences it is about, and which occurrences a customer may ask
 * about is settled before a read ever reaches here — {@link SavingRuleRepository} says why that is a
 * rule rather than a convention.
 *
 * <p>Package-private, like the row it reads: the rest of the application goes through
 * {@link AutomationService}.
 */
interface OccurrenceAllocationRepository extends JpaRepository<OccurrenceAllocation, Long> {

    /**
     * Where the money went on every one of these occurrences, in split order within each.
     *
     * <p>A whole history in one query rather than one question per occurrence, because the caller is
     * a rule's history: a rule caught up over three years is a thousand occurrences, and a question
     * each would be a thousand round trips to draw one page. The caller groups the answer by
     * occurrence and keeps this ordering inside each group.
     */
    List<OccurrenceAllocation> findByRuleOccurrenceIdInOrderByRuleOccurrenceIdAscSpotAsc(
            List<Long> ruleOccurrenceIds);
}
