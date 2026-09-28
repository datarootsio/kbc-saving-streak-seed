package io.dataroots.savingstreak.accounts;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/** Package-private: the rest of the application goes through {@link AccountsService}. */
interface IncomePaidRepository extends JpaRepository<IncomePaid, Long> {

    /**
     * Which days in that stretch this account has already been paid for — the days and nothing else,
     * because that is the whole of what the job has to know in order not to pay twice.
     *
     * <p><strong>A stretch rather than a list of exact days</strong>, and the difference is a money
     * bug. Asked "have you paid the 25th of January", an account paid on the 5th of January answers
     * no, and the second salary that month goes out under a day the customer moved their payday to.
     * The rule being kept is one salary a calendar month, so the question has to be able to see the
     * whole of each month the run is about to credit in — which day of it was paid is exactly what
     * the caller must not have to guess. {@code AccountsService} asks from the first day of the
     * first month to the last day of the last and sorts the answer into months itself, because a
     * month is a calendar fact and the database here holds days.
     *
     * <p>Every month the run is about to consider in one query rather than a question per day, so
     * that a clock wound three years forward is one query per account and not thirty-six.
     *
     * <p>The amount and the moment are deliberately not asked for. They are the audit trail and the
     * job has no use for them; what an account holds is the account's own balance.
     */
    @Query("select paid.dueOn from IncomePaid paid "
            + "where paid.currentAccountId = :currentAccountId "
            + "and paid.dueOn >= :from and paid.dueOn <= :to")
    List<LocalDate> whichDaysWerePaidBetween(@Param("currentAccountId") long currentAccountId,
                                             @Param("from") LocalDate from,
                                             @Param("to") LocalDate to);

    /**
     * The paydays this account was credited in a stretch of <em>moments</em>: the day each of them
     * was due, for every credit actually made after one moment and at or before another.
     *
     * <p><strong>Judged on when the money was credited, not on the day it was due, and that is the
     * whole point of this query.</strong> The two are the same fact only while nothing is late. A
     * salary due on the 10th and credited on the 11th — because the application was down on the
     * 10th, or because a trainer ran the jobs out of the order the night runs them in — is a payday
     * whose {@code dueOn} is already behind any reader whose cursor has passed the 10th. Asked for
     * the days <em>due</em> in a stretch, such a row is neither fetched nor kept, and a cursor that
     * only moves forward never comes back for it: the salary is in the customer's current account
     * and the rule that was supposed to save out of it has lost that month for good. Asked for the
     * credits <em>made</em> since a moment, it is found the first time anybody looks after it landed,
     * however late that was.
     *
     * <p>Open at the bottom and closed at the top, the same way every range in this application is,
     * so that one reader's last credit is not the next read's first.
     *
     * <p>Days rather than moments in the answer, because the day due is what the caller is deciding
     * about: a payday rule fires <em>for the 10th</em> whenever the money for the 10th arrived.
     *
     * <p>Every credit in one query rather than a question per day, so that a clock wound three years
     * forward is one query per account and not a thousand.
     */
    @Query("select paid.dueOn from IncomePaid paid "
            + "where paid.currentAccountId = :currentAccountId "
            + "and paid.paidAt > :creditedAfter and paid.paidAt <= :creditedThrough")
    List<LocalDate> whichPaydaysWereCreditedBetween(
            @Param("currentAccountId") long currentAccountId,
            @Param("creditedAfter") Instant creditedAfter,
            @Param("creditedThrough") Instant creditedThrough);

    /**
     * The most recent payday this account has been credited for, or nothing when it never has.
     *
     * <p>For the day a page tells a customer to expect their money. The job will not credit a month
     * this row is in — one salary a calendar month — so a next payday worked out from the cursor
     * alone would name a day nothing will land on, which is the same disagreement between the page
     * and the job that reading the cursor rather than the wall clock exists to close.
     *
     * <p>The whole row rather than the day on its own, because a derived query says what it means
     * without a fragment of JPQL, and one row is one row.
     */
    Optional<IncomePaid> findFirstByCurrentAccountIdOrderByDueOnDesc(long currentAccountId);

    /**
     * Everything this account has been credited, in the order the rows were written.
     *
     * <p>Here for the one claim about a catch-up that nothing in this feature yet reports over HTTP:
     * that the paydays were made up <em>oldest first</em>. A balance says how much arrived and says
     * nothing about the order the days were dealt with in, and the order is the promise — a history
     * read in any other order is one nobody can reconcile against a bank statement. It is read by a
     * test through the escape hatch {@code AnApplicationWithAClockToMove} documents, and by a later
     * ticket's history endpoint once there is one.
     */
    List<IncomePaid> findAllByCurrentAccountIdOrderByIdAsc(long currentAccountId);

    /**
     * Whether the index that makes one credit per account per payday a rule the database keeps —
     * rather than one this application merely intends — is already there.
     *
     * <p>Asked of SQLite's own catalogue rather than assumed, so that the ordinary start — every
     * start after the first — reads one row and does nothing. {@code create unique index if not
     * exists} would be idempotent on its own; asking first is what lets the start-up step say
     * whether it did anything, which is the difference between a log a reviewer can trust and one
     * that always claims the same thing.
     */
    @Query(value = "select count(*) from pragma_index_list('income_paid') "
            + "where name = 'one_income_per_account_per_payday'", nativeQuery = true)
    long theRecordIsAlreadyUniquePerPayday();

    /**
     * Makes it so, over the account and the day due.
     *
     * <p>Created here rather than declared on the entity because the entity cannot: this schema is
     * generated from the entity model against SQLite, and that dialect writes a composite unique
     * clause nowhere — {@link IncomePaid} says what the generated DDL does instead. A unique index
     * is a statement SQLite accepts, so this is where the guarantee comes from.
     *
     * <p>{@code if not exists} as well as the check above, so that two applications starting against
     * one file cannot race each other into a failure.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists one_income_per_account_per_payday "
            + "on income_paid (current_account_id, due_on)", nativeQuery = true)
    void makeTheRecordUniquePerPayday();
}
