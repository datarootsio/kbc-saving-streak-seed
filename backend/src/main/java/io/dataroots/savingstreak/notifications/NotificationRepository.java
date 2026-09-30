package io.dataroots.savingstreak.notifications;

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
    Optional<Notification> findFirstBySavingsAccountIdAndReasonInAndReadAtIsNullOrderByRaisedAtDescIdDesc(
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
}
