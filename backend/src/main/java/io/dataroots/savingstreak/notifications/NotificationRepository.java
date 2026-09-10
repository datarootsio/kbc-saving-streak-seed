package io.dataroots.savingstreak.notifications;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
