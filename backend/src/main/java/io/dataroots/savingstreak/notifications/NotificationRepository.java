package io.dataroots.savingstreak.notifications;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * The record of what has been said, and the only store this module has.
 *
 * <p>Two reads, and they are the two directions the record is used in. The sweep asks the newest
 * balance row for one account, because that row is the whole of what it knows about where the
 * balance was last seen standing; a customer asks for their own, newest first, because the panel is
 * a record rather than an inbox that empties.
 */
interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * The newest thing said about this account's balance, or nothing at all if nothing ever has
     * been.
     *
     * <p>The sweep's memory. No previous balance is stored anywhere in this application — a savings
     * balance is summed from what its deposits still hold, every time it is asked for — so the rung
     * an account was last known to stand on is read back out of what was last said about it.
     *
     * <p>Newest by the moment it was raised, and by identifier where two share a moment: a clock a
     * trainer has wound forward reads the same instant for a whole afternoon, so two sweeps in one
     * afternoon can have written rows the moment alone cannot order. The later row is the later
     * decision, and the identifiers say which that is.
     *
     * <p>By reason rather than by everything about the account, because the anniversary rows in the
     * same table are about a deposit's own occasion and say nothing about where a balance stands.
     */
    Optional<Notification> findFirstBySavingsAccountIdAndReasonInOrderByRaisedAtDescIdDesc(
            long savingsAccountId, Collection<NotificationReason> reasons);

    /**
     * Everything this customer has been told, newest first, read and unread together — ordered the
     * same way and for the same reason as the read above.
     */
    List<Notification> findByCustomerIdOrderByRaisedAtDescIdDesc(long customerId);

    /**
     * Which anniversaries of these deposits have already been announced, and under which reason.
     *
     * <p>The sweep's second memory, and the one that keeps an occasion an occasion. An anniversary
     * is near for the thirty nights before it falls, and a sweep that could not see what it had
     * already said would announce the same day thirty times.
     *
     * <p>By reason as well as by deposit, because the two anniversary reasons are the escalation
     * rather than two spellings of the same thing: a deposit announced as coming, whose shields are
     * then emptied, is announced again as at risk for the same day. Only the pairing of the reason
     * with the day has already been said.
     *
     * <p>Every deposit in one question rather than one question per deposit, as the loyalty sweep's
     * own read does: the rows are exactly the ones the sweep is about to decide against.
     *
     * <p>The reasons are asked for although {@code depositId} is null on every balance row, so that
     * the query says what it means rather than relying on a nullability the schema does not enforce.
     */
    @Query("select notification from Notification notification "
            + "where notification.depositId in :depositIds and notification.reason in :reasons")
    List<Notification> anniversariesAlreadyAnnouncedFor(
            @Param("depositIds") Collection<Long> depositIds,
            @Param("reasons") Collection<NotificationReason> reasons);

    /**
     * Which of these saving-rule occurrences have already been announced as transfers that did not
     * happen.
     *
     * <p>The sweep's third memory, and the one that makes "once, however many times the sweep runs"
     * true. An occurrence that could not be honoured stays in Automation's record for ever — that is
     * the whole point of that record — so a sweep that could not see what it had already said would
     * announce the same failed transfer every night from now on.
     *
     * <p>Only the identifiers come back, because the identifiers are the whole of what the decision
     * needs. Every one of the account's failed occurrences in one question rather than one question
     * per occurrence, as the anniversary read above does.
     *
     * <p>The reason is not asked for and does not need to be: {@code occurrenceId} is filled by one
     * family and left null by the other two, so the column names the reason by itself.
     */
    @Query("select notification.occurrenceId from Notification notification "
            + "where notification.occurrenceId in :occurrenceIds")
    List<Long> whichOccurrencesHaveAlreadyBeenAnnounced(
            @Param("occurrenceIds") Collection<Long> occurrenceIds);

    /**
     * The newest occurrence on one savings account that has already been announced as a transfer
     * that did not happen, or null if none ever has been.
     *
     * <p>The sweep's place in Automation's record, and what turns the read above from a question
     * about every failure an account has ever had into a question about tonight's. Occurrences are
     * written in one sequence and this module walks it forwards, so the highest identifier it has
     * spoken about is the whole of where it got to — one number, kept in the rows it was already
     * writing rather than in a cursor of its own that could fall out of step with them.
     *
     * <p>Scoped to the account because that is the unit the sweep walks in, and one account's
     * failures say nothing about where another account's read should start.
     *
     * <p>{@code max} passes over every row whose {@code occurrenceId} is null, which is every
     * balance row and every anniversary row — the column names the family by itself, exactly as the
     * read above relies on.
     */
    @Query("select max(notification.occurrenceId) from Notification notification "
            + "where notification.savingsAccountId = :savingsAccountId")
    Long theNewestOccurrenceAlreadyAnnouncedOn(@Param("savingsAccountId") long savingsAccountId);

    /**
     * Every notification already raised about one of these bills, whatever the day, so that a raiser
     * walking tonight's refused dates can tell which of them it has already spoken about.
     *
     * <p>The rows rather than the pairs, because the pair is (bill, day) and a projection of two
     * columns is a shape nobody else wants. One question for the whole account, as the anniversary
     * read does, rather than one per date.
     */
    @Query("select notification from Notification notification "
            + "where notification.billId in :billIds and notification.reason = :reason")
    List<Notification> billDatesAlreadyAnnouncedFor(@Param("billIds") Collection<Long> billIds,
                                                    @Param("reason") NotificationReason reason);

    /**
     * The last thing this module said about one current account under one reason, newest first — the
     * record the piling-up warning's transition check is made against.
     */
    Optional<Notification> findFirstByCurrentAccountIdAndReasonOrderByRaisedAtDescIdDesc(
            long currentAccountId, NotificationReason reason);

    /**
     * What has already been said about these categories in one month, and under which reason.
     *
     * <p>The sweep's memory for the budget half, and the thing that keeps a month's warning a
     * month's warning. A category four fifths gone on the tenth is four fifths gone on every night
     * to the thirty-first, so a sweep that could not see what it had already said would put the same
     * line in front of its holder twenty times about one thing.
     *
     * <p>Both reasons in one question, because the pair is judged as a pair: the quieter one is not
     * raised about a category and month the louder one has already been raised about, so the sweep
     * has to see both to decide either.
     *
     * <p>One question for the whole account rather than one per category, as the anniversary and the
     * bill reads do. The day is the day the month began, which is how a month is written down here —
     * {@link Notification} argues out why it travels in the existing {@code occursOn} rather than in
     * a column of its own.
     */
    @Query("select notification from Notification notification "
            + "where notification.categoryId in :categoryIds and notification.reason in :reasons "
            + "and notification.occursOn = :theMonthBegan")
    List<Notification> budgetWarningsAlreadyRaisedFor(
            @Param("categoryIds") Collection<Long> categoryIds,
            @Param("reasons") Collection<NotificationReason> reasons,
            @Param("theMonthBegan") LocalDate theMonthBegan);

    /**
     * Whether this account has already been told that this month is promised to more than it holds.
     *
     * <p>The whole of that warning's transition check, and it is a question about a month rather
     * than about a moment: a month is the thing the warning is about, so having said it once in this
     * month is the whole of the reason not to say it again in this month. The next month is a new
     * promise and is worth its own line — which is the same bargain the two category reasons strike,
     * and the reason all three carry the month in the row rather than beside it.
     */
    boolean existsByCurrentAccountIdAndReasonAndOccursOn(long currentAccountId,
                                                        NotificationReason reason,
                                                        LocalDate occursOn);

    /**
     * Whether the index that makes one warning per category, reason and month a rule the database
     * keeps — rather than one this application merely intends — is already there. Asked of SQLite's
     * own catalogue for the reason every other start-up check in this application gives.
     */
    @Query(value = "select count(*) from pragma_index_list('notification') "
            + "where name = 'one_notification_per_category_and_month'", nativeQuery = true)
    long theRecordIsAlreadyUniquePerCategoryAndMonth();

    /**
     * Makes it so, over the category, the reason and the day the month began — and over the rows
     * that name a category only.
     *
     * <p>Partial for the reason the bill index is: {@code category_id} is null on every other row in
     * this table, and a plain unique index over three columns that are null on most rows is an index
     * that would refuse the second balance notification ever raised.
     *
     * <p>The reason is in the key with the category and the month because the two budget reasons are
     * two different things to say about one month — running low and having gone over are a sequence
     * and not two spellings of one statement. That they are nevertheless never both said about one
     * month is a rule about state, kept in {@link NotificationsService}, and not something an index
     * could express.
     *
     * <p>Created here rather than declared on the entity for the reason
     * {@link #makeTheRecordUniquePerDepositAndAnniversary} gives, which holds word for word: the
     * SQLite dialect writes a composite unique clause nowhere, so a constraint declared in the model
     * reaches the database as nothing at all and the once-only rule would be an intention rather
     * than a guarantee.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists one_notification_per_category_and_month "
            + "on notification (category_id, reason, occurs_on) where category_id is not null",
            nativeQuery = true)
    void makeTheRecordUniquePerCategoryAndMonth();

    /**
     * Whether the index that makes one over-committed warning per account and month a rule the
     * database keeps is already there.
     */
    @Query(value = "select count(*) from pragma_index_list('notification') "
            + "where name = 'one_over_committed_warning_per_account_and_month'", nativeQuery = true)
    long theRecordIsAlreadyUniquePerOverCommittedMonth();

    /**
     * Makes it so, over the account and the day the month began, for the one reason that is about an
     * account's whole promise.
     *
     * <p><strong>Partial over the reason itself rather than over a column being null</strong>, which
     * is the only shape that works here and is worth reading twice. Every other partial index in
     * this table can name a column that one family fills and the others leave null; this family
     * fills {@code current_account_id} and {@code occurs_on}, and so does a bill that could not be
     * paid — and two bills on one account falling due on one day are two honest rows sharing all
     * three values. Naming the reason in the predicate is what keeps this index about the family it
     * is for.
     *
     * <p>The piling-up warning gets no index of this kind and could not have one: an account can
     * honestly cross that threshold many times, so its uniqueness is a comparison about state rather
     * than a constraint about rows. This one is capped at one per month by its own rule, so the
     * database can keep it, and a guarantee is worth more than an intention.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists "
            + "one_over_committed_warning_per_account_and_month "
            + "on notification (current_account_id, occurs_on) "
            + "where reason = 'THE_MONTH_IS_OVER_COMMITTED'",
            nativeQuery = true)
    void makeTheRecordUniquePerOverCommittedMonth();

    /**
     * Whether the index that makes one announcement per bill and due date a rule the database keeps
     * — rather than one this application merely intends — is already there.
     *
     * <p>Asked of SQLite's own catalogue rather than assumed, for the reason every other start-up
     * check in this application gives: {@code create unique index if not exists} is idempotent on
     * its own, and asking first is what lets the start-up step say whether it did anything.
     */
    @Query(value = "select count(*) from pragma_index_list('notification') "
            + "where name = 'one_notification_per_bill_and_due_date'", nativeQuery = true)
    long theRecordIsAlreadyUniquePerBillAndDueDate();

    /**
     * Makes it so, over the bill, the reason and the day — and over the rows that name a bill only.
     *
     * <p>Partial for the reason the anniversary index is: {@code bill_id} is null on every balance
     * row, every anniversary row and every piling-up warning, and the last of those is the one that
     * would be refused by a whole-table index. An account can honestly cross the piling-up threshold
     * twice, so that family's uniqueness is the crossing comparison in {@link NotificationsService}
     * and cannot be a constraint about rows at all.
     *
     * <p>The reason is in the key as well as the bill and the day, so that a later reason about the
     * same date — a bill about to fall due, say — is not refused by the one already raised about it.
     *
     * <p>Created here rather than declared on the entity for the reason
     * {@link #makeTheRecordUniquePerDepositAndAnniversary} gives, which holds word for word.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists one_notification_per_bill_and_due_date "
            + "on notification (bill_id, reason, occurs_on) where bill_id is not null",
            nativeQuery = true)
    void makeTheRecordUniquePerBillAndDueDate();

    /**
     * Whether the index that makes one announcement per failed occurrence a rule the database keeps
     * is already there — asked of SQLite's own catalogue, for the reason the read above it gives.
     */
    @Query(value = "select count(*) from pragma_index_list('notification') "
            + "where name = 'one_notification_per_occurrence'", nativeQuery = true)
    long theRecordIsAlreadyUniquePerOccurrence();

    /**
     * Makes it so, over the occurrence, and over the rows that name one only.
     *
     * <p>Partial again, and for the same reason the anniversary index is: {@code occurrence_id} is
     * null on every balance row and on every anniversary row, and SQLite would happily hold many
     * nulls in a unique index — but saying {@code where occurrence_id is not null} is what makes the
     * index say what it means rather than rely on that. One occurrence is one transfer that did not
     * happen, whatever the sweep is asked to do afterwards.
     *
     * <p>Created here rather than declared on the entity for the reason
     * {@link #makeTheRecordUniquePerDepositAndAnniversary} gives, which holds word for word.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists one_notification_per_occurrence "
            + "on notification (occurrence_id) where occurrence_id is not null",
            nativeQuery = true)
    void makeTheRecordUniquePerOccurrence();

    /**
     * Whether the index that makes one announcement per deposit, reason and anniversary a rule the
     * database keeps — rather than one this application merely intends — is already there.
     *
     * <p>Asked of SQLite's own catalogue rather than assumed, for the reason the loyalty module's
     * equivalent gives: {@code create unique index if not exists} is idempotent on its own, and
     * asking first is what lets the start-up step say whether it did anything.
     */
    @Query(value = "select count(*) from pragma_index_list('notification') "
            + "where name = 'one_notification_per_deposit_and_anniversary'", nativeQuery = true)
    long theRecordIsAlreadyUniquePerDepositAndAnniversary();

    /**
     * Makes it so, over the deposit, the reason and the day — and over the anniversary rows only.
     *
     * <p>Created here rather than declared on the entity because the entity cannot: this schema is
     * generated from the entity model against SQLite ({@code ddl-auto=update}), and that dialect
     * writes a composite unique clause nowhere. A {@code create unique index} is a statement SQLite
     * does accept, so this is where the guarantee comes from.
     *
     * <p><strong>Partial, and that is the load-bearing half.</strong> {@code where deposit_id is not
     * null} leaves every balance row outside the index, because a balance rung genuinely can be
     * announced twice: crossing EUR 1.000, falling back and crossing it again is three occasions and
     * three notifications, and an index over the whole table would refuse the third. The balance
     * family's uniqueness is the rung comparison in {@link NotificationsService}, which raises
     * nothing when the rung has not moved — a rule about state rather than a constraint about rows.
     *
     * <p>{@code if not exists} as well as the check above, so that two applications starting against
     * one file cannot race each other into a failure.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists "
            + "one_notification_per_deposit_and_anniversary "
            + "on notification (deposit_id, reason, occurs_on) where deposit_id is not null",
            nativeQuery = true)
    void makeTheRecordUniquePerDepositAndAnniversary();

    /**
     * Which of these notices have already been announced as ones that have run their days.
     *
     * <p>The sweep's memory for notice, and the shape the occurrence read already has: only the
     * identifiers come back, because the identifiers are the whole of what the decision needs, and
     * every standing notice on the account is asked about in one question rather than one each.
     *
     * <p>Ready notice never lapses, so a notice that came free a fortnight ago is still standing and
     * is still read every night. Without this the same notice would be announced on every one of
     * those nights.
     *
     * <p>The reason is not asked for and does not need to be: {@code notice_id} is filled by one
     * family and left null by every other, so the column names the reason by itself — exactly as
     * {@link #whichOccurrencesHaveAlreadyBeenAnnounced} relies on.
     */
    @Query("select notification.noticeId from Notification notification "
            + "where notification.noticeId in :noticeIds")
    List<Long> whichNoticesHaveAlreadyBeenAnnounced(@Param("noticeIds") Collection<Long> noticeIds);

    /**
     * Whether this account has already been told that its term is coming up for the day named.
     *
     * <p>A question about one account and one day rather than a listing, because a term has exactly
     * one maturity date at a time: there is nothing to walk and nothing to keep a place in. A
     * rolling term's next maturity is a different day and is a different question, which is what
     * makes the year after this one worth its own line.
     *
     * <p>The reason is in the question although the day and the account would nearly do on their
     * own: a notice that has become ready fills both of those columns too, and two families sharing
     * a key is how an announcement goes missing.
     */
    boolean existsBySavingsAccountIdAndReasonAndOccursOn(long savingsAccountId,
                                                        NotificationReason reason,
                                                        LocalDate occursOn);

    /**
     * Which versions of its product this account has already been told bettered the terms it was on.
     *
     * <p>Every version in one question rather than one question per version, because the sweep is
     * about to decide against the one version on offer tonight but the answer is the account's whole
     * history of being told — and a product with four versions has been on offer four times.
     *
     * <p>Only the version numbers come back. A version number is the whole of what "have I said this
     * already" needs, and the account is already in the question.
     */
    @Query("select notification.termsVersion from Notification notification "
            + "where notification.savingsAccountId = :savingsAccountId "
            + "and notification.reason = :reason")
    List<Integer> whichVersionsHaveAlreadyBeenAnnouncedOn(
            @Param("savingsAccountId") long savingsAccountId,
            @Param("reason") NotificationReason reason);

    /**
     * Whether the index that makes one announcement per notice a rule the database keeps is already
     * there — asked of SQLite's own catalogue, for the reason every other start-up check gives.
     */
    @Query(value = "select count(*) from pragma_index_list('notification') "
            + "where name = 'one_notification_per_notice'", nativeQuery = true)
    long theRecordIsAlreadyUniquePerNotice();

    /**
     * Makes it so, over the notice, and over the rows that name one only.
     *
     * <p>Partial for the reason {@link #makeTheRecordUniquePerOccurrence} is: {@code notice_id} is
     * null on every other row in this table, and saying {@code where notice_id is not null} is what
     * makes the index say what it means rather than lean on SQLite's willingness to hold many nulls
     * in a unique one.
     *
     * <p>The reason is deliberately <em>not</em> in the key. One notice coming free is one occasion,
     * and unlike an anniversary there is no second thing this application could ever say about the
     * same notice — it is ready or it is not, and it never goes back.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists one_notification_per_notice "
            + "on notification (notice_id) where notice_id is not null",
            nativeQuery = true)
    void makeTheRecordUniquePerNotice();

    /**
     * Whether the index that makes one maturity warning per account and day a rule the database
     * keeps is already there.
     */
    @Query(value = "select count(*) from pragma_index_list('notification') "
            + "where name = 'one_maturity_warning_per_account_and_day'", nativeQuery = true)
    long theRecordIsAlreadyUniquePerMaturity();

    /**
     * Makes it so, over the account and the day the term is up, for the one reason that is about a
     * maturity coming.
     *
     * <p><strong>Partial over the reason rather than over a column being null</strong>, which is the
     * shape {@link #makeTheRecordUniquePerOverCommittedMonth} needs and for the same reason: this
     * family fills {@code savings_account_id} and {@code occurs_on}, and so does a notice that has
     * become ready and so does an anniversary. Naming the reason in the predicate is what keeps the
     * index about the family it is for.
     *
     * <p>The day is in the key because a rolling term matures every year and each of those years is
     * a decision worth putting in front of its holder once. The account alone would say a term is
     * announced once and never again, which is true of a term that is broken or moved and false of
     * the one product this bank sells that rolls.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists one_maturity_warning_per_account_and_day "
            + "on notification (savings_account_id, occurs_on) "
            + "where reason = 'A_TERM_IS_ABOUT_TO_MATURE'",
            nativeQuery = true)
    void makeTheRecordUniquePerMaturity();

    /**
     * Whether the index that makes one bettered-terms announcement per account and version a rule
     * the database keeps is already there.
     */
    @Query(value = "select count(*) from pragma_index_list('notification') "
            + "where name = 'one_bettered_terms_notification_per_account_and_version'",
            nativeQuery = true)
    long theRecordIsAlreadyUniquePerAccountAndVersion();

    /**
     * Makes it so, over the account and the version on offer, and over the rows that name a version
     * only.
     *
     * <p>This is the guarantee behind "once per version". The sweep asks what it has already said
     * before it says anything, and that check is what normally does the work; a rule the database
     * keeps is what makes it true when two runs read the same record in the same millisecond.
     *
     * <p>Partial over {@code terms_version} being null, which every other reason leaves it, so the
     * ordinary shape rather than the predicate-on-the-reason shape the maturity index needs. The
     * account is in the key because a version is only a version <em>of a product</em>, and two
     * accounts on one product are two people to tell.
     */
    @Transactional
    @Modifying
    @Query(value = "create unique index if not exists "
            + "one_bettered_terms_notification_per_account_and_version "
            + "on notification (savings_account_id, terms_version) "
            + "where terms_version is not null",
            nativeQuery = true)
    void makeTheRecordUniquePerAccountAndVersion();
}
