package io.dataroots.savingstreak.accounts;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Package-private: the rest of the application goes through {@link AccountsService}. */
interface BillOccurrenceRepository extends JpaRepository<BillOccurrence, Long> {

    /**
     * Which of this bill's due dates in that stretch have already been settled — the dates and
     * nothing else, because that is the whole of what the nightly run has to know in order not to
     * take the same rent twice.
     *
     * <p><strong>The record as well as the cursor, and the two are different guarantees.</strong>
     * The cursor is what keeps a run bounded; the record is what makes it idempotent. A cursor that
     * had been rewritten — by a hand-edited row, or by a bill whose day the customer corrected —
     * would re-derive dates that have already taken money, and only this question stops the second
     * withdrawal. It is the same pairing the income run keeps, and on this table it is money.
     *
     * <p><strong>The dates come back and the caller asks about months.</strong> This answers which
     * days were settled; {@code AccountsService.dueInMonthsNotAlreadySettled} folds them into
     * calendar months and drops every due date falling in one of those, because a month holds one
     * rent and the 10th and the 20th of one March are the same March.
     * {@code IncomePaidRepository.whichDaysWerePaidBetween} answers its own run in exactly this
     * shape and for exactly this reason: the rule lives in the question the run asks, so that it
     * reads in one place beside the calendar it is filtering.
     *
     * <p>A stretch in one query rather than a question per date, so that a clock wound three years
     * forward is one query per bill and not thirty-six. The caller hands in whole months — the
     * first of the first to the last of the last — for the same reason.
     *
     * <p>The amount, the moment and the outcome are deliberately not asked for. Whether a date was
     * paid or went unpaid does not change this answer: a date that was presented has been settled
     * either way, and presenting it a second time in the same run would be the double debit this
     * question exists to prevent. What becomes of the ones that went unpaid is the arrears slice's
     * question and it asks it separately.
     */
    @Query("select settled.dueOn from BillOccurrence settled "
            + "where settled.recurringBillId = :billId "
            + "and settled.dueOn >= :from and settled.dueOn <= :to")
    List<LocalDate> whichDueDatesWereSettledBetween(@Param("billId") long billId,
                                                    @Param("from") LocalDate from,
                                                    @Param("to") LocalDate to);

    /**
     * Everything that has ever happened to one bill, newest first — every date it fell due and what
     * became of it.
     *
     * <p>Newest first because that is how a history is read: what happened last month is what a
     * customer is looking for, and the rent from two years ago is the thing they scroll to. The same
     * order a saving rule's history and the money movements ledger answer in.
     *
     * <p>Broken by identifier when two rows share a due date, which cannot happen while the unique
     * index holds and is written all the same so that the order is total rather than
     * nearly-total — a list whose order depends on what the database felt like is a list a test
     * cannot assert on.
     */
    List<BillOccurrence> findByRecurringBillIdOrderByDueOnDescIdDesc(long recurringBillId);

    /**
     * Every date anywhere that was presented and not paid, oldest first — the arrears, in the order
     * a run has to settle them in.
     *
     * <p><strong>Oldest due date first, and the order is the feature.</strong> Money that arrives
     * goes to the debt that has been carried longest: March's rent is paid before April's, and a
     * customer who scrapes together nine hundred euros pays the older rent rather than the
     * convenient one. An order the database happened to feel like would make that a coincidence
     * instead of a promise, so it is asked for here and broken by identifier when two dates fall on
     * one day — the way every other total order in this module is.
     *
     * <p>Across every account rather than one at a time, because the nightly run walks the whole
     * application and one query is one query. Arrears on one account can only ever be settled out of
     * that account's own balance, so settling them all in one pass, oldest first, is the same
     * outcome as walking the accounts one by one — and the ordering a page reads is asked for
     * separately below.
     *
     * <p>Unpaid rows are the whole of what is owed. The cursor deliberately moves past a date that
     * went unpaid, so nothing about a bill's own state says anything is outstanding; this question
     * is the only thing that does.
     */
    List<BillOccurrence> findByOutcomeOrderByDueOnAscIdAsc(BillOutcome outcome);

    /**
     * The same question about one account: what that account still owes, oldest first.
     *
     * <p>What the still-owed panel is drawn from, and what the account's own read carries. The
     * account is on the row itself rather than reached through the bill, which is what makes this
     * one query instead of a join through a list of bills — and what keeps a date owed by a bill
     * that has since been ended in the answer, because ending a bill stops it being presented again
     * and does not waive what was already owed.
     */
    List<BillOccurrence> findByCurrentAccountIdAndOutcomeOrderByDueOnAscIdAsc(
            long currentAccountId, BillOutcome outcome);

    /**
     * Every date presented to any of these accounts, paid or not, newest settlement first.
     *
     * <p>What the money-movement ledger is drawn from, and it is the only read of these rows ordered
     * by <em>when they were settled</em> rather than by the day they were owed. A bill has two dates
     * and the ledger interleaves it with deposits and withdrawals, which have one: an arrear owed in
     * March and settled in June moved money in June, and sorting it by the day it was owed would
     * file it under a balance it never touched. The day it was owed from is still on the row and
     * still shown.
     *
     * <p>Every account the customer holds in one question, because a ledger is the customer's rather
     * than one account's — the same shape the movements into and out of savings are asked for in.
     *
     * <p>Both outcomes, deliberately. A date that took nothing is exactly what a customer reading
     * back over a balance is looking for, and a ledger of movements has no row for a movement that
     * did not happen.
     *
     * <p>An empty collection is never passed — {@link AccountsService} answers a customer who holds
     * no current account without asking — because {@code in ()} is not a thing to ask a database.
     */
    List<BillOccurrence> findByCurrentAccountIdInOrderBySettledAtDescIdDesc(
            Collection<Long> currentAccountIds);

    /**
     * The last date each of these bills was actually paid on, for the bills that have been paid at
     * all.
     *
     * <p>One query for the whole list rather than one per bill, so that drawing an account with
     * twenty bills on it is one question and not twenty. A bill that has never been taken is simply
     * missing from the answer, which is the honest shape: there is no date, and a null row claiming
     * otherwise would have to be filtered out by every caller.
     *
     * <p>Paid dates only. A date that was presented and refused is not a date the bill was taken,
     * and a page saying otherwise would tell the customer the opposite of what happened.
     *
     * <p>An empty collection is never passed — {@link AccountsService} answers an empty list of
     * bills without asking — because {@code in ()} is not a thing every database will parse.
     */
    @Query("select new io.dataroots.savingstreak.accounts.WhenABillWasLastTaken("
            + "taken.recurringBillId, max(taken.dueOn)) "
            + "from BillOccurrence taken "
            + "where taken.recurringBillId in :billIds "
            + "and taken.outcome = io.dataroots.savingstreak.accounts.BillOutcome.PAID "
            + "group by taken.recurringBillId")
    List<WhenABillWasLastTaken> whenEachOfTheseWasLastTaken(
            @Param("billIds") Collection<Long> billIds);

    /**
     * Every date on this account that was ever presented and refused, oldest failure first, whether
     * or not the hole has since been closed.
     *
     * <p>Keyed off the moment it fell short rather than off the outcome, because the outcome moves
     * when an arrear is settled and this question is about what happened, not about what is still
     * outstanding. Rows written before that moment was recorded carry null and are left out, which
     * is what {@code is not null} says here.
     *
     * <p>That exclusion is not allowed to disagree with {@link
     * #findByCurrentAccountIdAndOutcomeOrderByDueOnAscIdAsc}, which is what the still-owed panel is
     * drawn from and which keys off the outcome. A date the panel lists and this read cannot see
     * would be an arrear the piling-up count does not count — the page saying three and the warning
     * judging two. {@link #fillInTheNightEachOutstandingArrearFellShortOn} is what closes that,
     * before either is ever asked.
     */
    List<BillOccurrence> findByCurrentAccountIdAndFellShortAtNotNullOrderByFellShortAtAscIdAsc(
            long currentAccountId);

    /**
     * Fills in the night each still-outstanding arrear fell short on, for the rows written before
     * this application had a column to write it in.
     *
     * <p><strong>Recovered rather than invented, and only because it happens to be recoverable.</strong>
     * A row that is still {@link BillOutcome#UNPAID} has never been through
     * {@link BillOccurrence#settledLate}, so its {@code settledAt} still reads the moment the
     * billing run presented it and was refused — which is exactly what {@code fellShortAt} means.
     * The moment moves only when the arrear is settled, and at that point the outcome stops being
     * {@code UNPAID} and this statement stops touching the row. A date that went unpaid and was
     * settled before the column existed has genuinely lost its moment and is not backfilled: there
     * is nowhere to get it from, and inventing one would put a crossing into an account's history
     * that nobody can check.
     *
     * <p>The balance that fell short is not backfilled either, for the reason
     * {@link BillOccurrence} argues: what an account held on a night that has passed cannot be
     * worked out afterwards. Such a date is counted in the pile — its amount is on the row — and
     * still refused a notification of its own by the raiser, which says so in a WARN.
     *
     * <p>Answers how many rows it changed, so the start-up step can say whether it did anything.
     * Every start after the first changes nothing.
     */
    @Transactional
    @Modifying
    @Query(value = "update bill_occurrence set fell_short_at = settled_at "
            + "where fell_short_at is null and outcome = 'UNPAID'", nativeQuery = true)
    int fillInTheNightEachOutstandingArrearFellShortOn();

    /**
     * Whether the index that makes one settlement per bill per due date a rule the database keeps —
     * rather than one this application merely intends — is already there.
     *
     * <p>Asked of SQLite's own catalogue rather than assumed, so that the ordinary start — every
     * start after the first — reads one row and does nothing. {@code create unique index if not
     * exists} would be idempotent on its own; asking first is what lets the start-up step say
     * whether it did anything, which is the difference between a log a reviewer can trust and one
     * that always claims the same thing. {@link IncomePaidRepository} asks the identical question
     * about its own table.
     */
    @Query(value = "select count(*) from pragma_index_list('bill_occurrence') "
            + "where name = 'one_settlement_per_bill_per_due_date'", nativeQuery = true)
    long theRecordIsAlreadyUniquePerDueDate();

    /**
     * Makes it so, over the bill and the day due.
     *
     * <p>Created here rather than declared on the entity because the entity cannot: this schema is
     * generated from the entity model against SQLite, and that dialect writes a composite unique
     * clause nowhere — {@link BillOccurrence} says what the generated DDL does instead. A unique
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
    @Query(value = "create unique index if not exists one_settlement_per_bill_per_due_date "
            + "on bill_occurrence (recurring_bill_id, due_on)", nativeQuery = true)
    void makeTheRecordUniquePerDueDate();
}
