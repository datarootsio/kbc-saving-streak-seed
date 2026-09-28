package io.dataroots.savingstreak.automation;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the rules have actually done, one row per rule per day due.
 *
 * <p>Every read is scoped by a rule, and which rules a customer may ask about is settled before a
 * read ever reaches here — {@link SavingRuleRepository} says why that is a rule rather than a
 * convention.
 *
 * <p>Package-private, like the row it reads: the rest of the application goes through
 * {@link AutomationService}.
 */
interface RuleOccurrenceRepository extends JpaRepository<RuleOccurrence, Long> {

    /**
     * One rule's history, newest first — the day it was due, then, for two occurrences settled on
     * one run, the later row first.
     *
     * <p>Newest first because that is the question a customer arrives with: what did this rule do
     * last. The identifier breaks the tie rather than the moment they were settled at, because a
     * catch-up settles a month of occurrences inside one transaction and every one of them carries
     * the same moment; the order the rows were written in is the order the days were dealt with,
     * which is the only tie-break that reads as the history it is.
     */
    List<RuleOccurrence> findBySavingRuleIdOrderByDueOnDescIdDesc(long savingRuleId);

    /**
     * Which days in that stretch this rule has already been settled for.
     *
     * <p>The days and nothing else, because that is the whole of what the run has to know in order
     * not to move the money twice. The cursor already answers it for every ordinary second run; this
     * is what answers it when the cursor has been rewritten, when the day the rule moves on has been
     * moved inside a period it has already fired in, or when a rule was written before the cursor
     * column existed and is being read from its creation.
     *
     * <p>The stretch asked for is whole weeks or whole months rather than the days the calendar
     * named — {@code AutomationService.dueInPeriodsNotAlreadySettled} widens it and says why — and
     * every one of them in one query rather than a question per day, so that a clock wound three
     * years forward is one query per rule and not a thousand.
     */
    @Query("select occurrence.dueOn from RuleOccurrence occurrence "
            + "where occurrence.savingRuleId = :savingRuleId "
            + "and occurrence.dueOn >= :from and occurrence.dueOn <= :to")
    List<LocalDate> whichDaysWereSettledBetween(@Param("savingRuleId") long savingRuleId,
                                                @Param("from") LocalDate from,
                                                @Param("to") LocalDate to);

    /**
     * Which of these deposits a rule made, out of a list of deposits somebody else is holding.
     *
     * <p>The one read in this module that starts from a deposit, and it exists so that a money
     * history can mark the rows this module is responsible for. The question is asked in this
     * direction — the page hands over the deposits it is about to draw and is told which of them
     * were automatic — because the other direction would be Deposits asking Automation about its
     * own rows, which is the dependency the module is built not to have: {@code deposits} stays
     * ignorant that automation exists.
     *
     * <p>Identifiers and nothing else, and all of them in one query rather than a question per row,
     * so that a page showing a year of movements costs one round trip. A deposit no rule made is
     * simply absent from the answer, which is what the caller reads as "somebody pressed a button".
     */
    @Query("select occurrence.depositId from RuleOccurrence occurrence "
            + "where occurrence.depositId in :depositIds")
    List<Long> whichOfTheseDepositsWereMadeByARule(@Param("depositIds") Collection<Long> depositIds);

    /**
     * Every occurrence on one savings account that ended in a given outcome and was written after a
     * given one, oldest first.
     *
     * <p>The one read in this module that starts from an account rather than from a rule, and it
     * exists for the nightly notifications sweep: that sweep walks savings accounts, and asking it
     * to fetch each account's rules and then each rule's history would be a walk through a slower
     * door for an answer one question gives.
     *
     * <p>Joined to the rule rather than handed a list of rule identifiers, so the question stays one
     * question however many rules an account has had — and so that a rule which has been
     * <em>ended</em> is inside the answer. Its occurrences are still part of what happened on that
     * account, and a transfer that did not happen is not un-happened by the customer later closing
     * the rule that would have made it.
     *
     * <p>Oldest first, by identifier, which is the order the days were dealt with: a catch-up
     * settles a month of occurrences inside one transaction under one moment, so the row order is
     * the only thing that reads as the history it is.
     *
     * <p><strong>{@code after} is the caller's place in that order, not a filter on the
     * history.</strong> A reader that walks this record forwards and remembers where it got to asks
     * for what has been written since, rather than for everything and then throwing most of it
     * away; zero asks for the lot, because identifiers start at one. It is the same order the rows
     * come back in, so a caller that keeps the last identifier it saw misses nothing.
     *
     * <p><strong>Outcomes rather than one outcome</strong>, since a rule that had nowhere to pay
     * its money joined a rule that had none to pay: the two are different things to say to a
     * customer and are one question to ask of this record, because they are read in one order by one
     * reader keeping one place in it. Asking twice would hand that reader two orderings to merge and
     * two places to keep, and the cursor it walks by is a single highest identifier.
     */
    @Query("select occurrence from RuleOccurrence occurrence, SavingRule rule "
            + "where rule.id = occurrence.savingRuleId "
            + "and rule.savingsAccountId = :savingsAccountId "
            + "and occurrence.outcome in :outcomes "
            + "and occurrence.id > :after "
            + "order by occurrence.id asc")
    List<RuleOccurrence> findOnSavingsAccountWithOutcomeAfter(
            @Param("savingsAccountId") long savingsAccountId,
            @Param("outcomes") Collection<OccurrenceOutcome> outcomes,
            @Param("after") long after);

    /**
     * Whether the index that makes one occurrence per rule per day due a rule the database keeps —
     * rather than one this application merely intends — is already there.
     *
     * <p>Asked of SQLite's own catalogue rather than assumed, so that the ordinary start — every
     * start after the first — reads one row and does nothing. {@code create unique index if not
     * exists} would be idempotent on its own; asking first is what lets the start-up step say
     * whether it did anything, which is the difference between a log a reviewer can trust and one
     * that always claims the same thing.
     */
    @Query(value = "select count(*) from pragma_index_list('rule_occurrence') "
            + "where name = 'one_occurrence_per_rule_per_day'", nativeQuery = true)
    long theRecordIsAlreadyUniquePerDayDue();

    /**
     * Makes it so, over the rule and the day due.
     *
     * <p>Created here rather than declared on the entity because the entity cannot: this schema is
     * generated from the entity model against SQLite, and that dialect writes a composite unique
     * clause nowhere — {@link RuleOccurrence} says what the generated DDL does instead. A unique
     * index is a statement SQLite accepts, so this is where the guarantee comes from.
     *
     * <p>{@code if not exists} as well as the check above, so that two applications starting against
     * one file cannot race each other into a failure.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists one_occurrence_per_rule_per_day "
            + "on rule_occurrence (saving_rule_id, due_on)", nativeQuery = true)
    void makeTheRecordUniquePerDayDue();
}
