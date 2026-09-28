package io.dataroots.savingstreak.automation;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * How a rule spreads what it moves, one row per goal in the split.
 *
 * <p>Every read is scoped by a rule, and which rules a customer may ask about is settled before a
 * read ever reaches here — {@link SavingRuleRepository} says why that is a rule rather than a
 * convention.
 *
 * <p>Package-private, like the row it reads: the rest of the application goes through
 * {@link AutomationService}.
 */
interface RuleSplitRepository extends JpaRepository<RuleSplit, Long> {

    /**
     * One rule's split, in the order the customer wrote it.
     *
     * <p>Ordered here rather than wherever it is used, because the order is not presentation: it
     * settles which goal a leftover cent goes to and which goal a share spills to. A caller that had
     * to remember to sort would be a caller that could forget.
     */
    List<RuleSplit> findBySavingRuleIdOrderBySpotAsc(long savingRuleId);

    /**
     * Every share on a set of rules, so a page listing an account's rules is one query rather than
     * one per rule.
     *
     * <p>Unordered by rule on purpose: the caller groups by rule and keeps this ordering inside each
     * group, which is the one ordering that means anything.
     */
    List<RuleSplit> findBySavingRuleIdInOrderBySavingRuleIdAscSpotAsc(List<Long> savingRuleIds);

    /**
     * Takes a rule's split away, so that a customer who says a new one has one split and not two
     * overlaid.
     *
     * <p>A deletion rather than a closing, unlike everything else this feature keeps: a split is
     * half of an instruction rather than a record of something that happened, and what a rule
     * actually did with the money on a day is written on the occurrence, which is never rewritten.
     * So the history stays true after the instruction changes, which is the whole reason the two are
     * separate tables.
     */
    void deleteBySavingRuleId(long savingRuleId);
}
