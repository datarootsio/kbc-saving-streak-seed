package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import io.dataroots.savingstreak.accounts.AccountHolder;
import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.deposits.DepositsService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the rules have decided was worth saying: the Notifications module's face to the rest of the
 * application, and the whole of the rule.
 *
 * <p>A module of its own because a notification is not owned by any of the rules it reports on. It
 * reads a savings balance from Deposits and who holds an account from Accounts, writes nothing but
 * its own record, and neither module learns that notifications exist. It moves no money, credits no
 * points and secures no week: this is a record addressed to a customer, not a rule that pays them.
 *
 * <p><strong>A balance notification is raised on a change of rung, not on a crossing event.</strong>
 * That is the load-bearing decision in here and it is forced by what this application stores. A
 * savings balance is derived by summing what every deposit still holds, every time it is asked for;
 * no previous balance is written down anywhere, so there is nothing for a crossing to be measured
 * against. So the sweep reads the rung the account stands on now, reads the rung it was last known
 * to stand on out of the newest thing said about that account's balance, and raises only on a
 * difference. A higher rung is one {@link NotificationReason#BALANCE_THRESHOLD_REACHED} naming the
 * rung landed on, however many rungs a single deposit vaulted. A lower rung is one
 * {@link NotificationReason#BALANCE_THRESHOLD_LOST}. The same rung is nothing at all, which is what
 * stops a balance resting at EUR 1.001 announcing itself every night for a year.
 *
 * <p>Reading the record backwards is therefore part of the rule, and it is why a lost rung names the
 * lowest rung the balance no longer reaches rather than the rung it was last known on. A row is read
 * back as a position: {@code REACHED(t)} says the balance stood on {@code t}, {@code LOST(t)} says
 * it stood on the rung below {@code t}, and no row at all says it stood on none. A balance that
 * falls from EUR 1.100 to EUR 120 has left the EUR 1.000 rung and the EUR 500 rung, and a row
 * naming EUR 1.000 would read back as "it stands on EUR 500" — which it does not, so the next sweep
 * would announce the same fall a second time. Naming the lowest rung it no longer reaches, EUR 500,
 * reads back as EUR 100, which is exactly where it stands. One fall, one notification, and a fall of
 * a single rung — every fall the acceptance criteria describe, and the ordinary case — names the rung
 * the customer had reached.
 *
 * <p>An account that already stands on a rung and has never been told so is announced on the first
 * sweep. Nothing is backfilled beyond that: the first run says where an account stands today and
 * does not walk backwards inventing the moments it climbed there.
 */
@Service
public class NotificationsService {

    private static final Logger log = LoggerFactory.getLogger(NotificationsService.class);

    /**
     * Two, like every other amount of money in this application. Written here rather than borrowed,
     * because the only figures this module quotes are the ones it logs and the ladder's own rungs.
     */
    private static final int DECIMAL_PLACES_IN_MONEY = 2;

    private final AccountsService accounts;
    private final DepositsService deposits;
    private final NotificationRepository notifications;

    NotificationsService(AccountsService accounts, DepositsService deposits,
                         NotificationRepository notifications) {
        this.accounts = accounts;
        this.deposits = deposits;
        this.notifications = notifications;
    }

    /**
     * Raises everything the rules have to say as at the given moment, and writes it down.
     *
     * <p>Every savings account on every run. With the seeded customers that is three accounts, and
     * with a real population it would be the first thing to make incremental — noted rather than
     * built, because a sweep that only looked at accounts that had moved would need a record of what
     * had moved, which is a second store to keep in step with this one.
     *
     * <p>Answers nothing, as neither of the other two nightly sweeps does: its caller runs on a
     * schedule with nobody waiting on it, and a figure handed back to a scheduled method is a figure
     * nothing can read. What the sweep did is in the INFO lines below.
     *
     * <p>The caller says what time it is. A sweep run against a clock a trainer has wound forward
     * has to judge everything against the moment the application thinks it is, and nothing in here
     * reads a clock of its own — one moment is stamped on everything one run raises.
     *
     * <p>Public, unlike the rest of this module, and not because anything outside wants it:
     * {@code @Transactional} is applied by a proxy, a proxy cannot advise a method that is not
     * public, and the annotation would otherwise be quietly ignored and a half-finished sweep would
     * commit. The power it leaks is the power to run tonight's sweep early, which raises nothing
     * that has already been raised and is exactly what the development jobs endpoint offers anyway.
     */
    @Transactional
    public void raiseNotifications(Instant now) {
        List<Long> savingsAccounts = accounts.everySavingsAccount();
        List<Notification> raising = new ArrayList<>();
        for (long savingsAccountId : savingsAccounts) {
            whatThisAccountsBalanceHasToSay(savingsAccountId, now).ifPresent(raising::add);
        }
        // One write for the run, so that a sweep either says everything it decided or nothing at
        // all. Nothing raised is no statement at all rather than an empty one.
        List<Notification> raised = notifications.saveAll(raising);
        for (Notification notification : raised) {
            // One line per notification with the figures that produced it and the row it became, so
            // that a reviewer can check by hand that the rule fired on the values it claims. After
            // the write rather than before it, because the identifier is half of what makes the line
            // worth having.
            log.info("notification raised customerId={} reason={} savingsAccountId={} depositId={} "
                            + "amount={} points={} occursOn={} notificationId={}",
                    notification.getCustomerId(), notification.getReason(),
                    notification.getSavingsAccountId(), notification.getDepositId(),
                    asMoney(notification.getAmount()), notification.getPoints(),
                    notification.getOccursOn(), notification.getId());
        }
        // One line per sweep: the moment it judged everything against, how many accounts it looked
        // at and how many notifications it raised. A quiet night and a night that was handed nothing
        // to look at can be told apart from this line alone.
        log.info("notifications raised asAt={} accountsConsidered={} raised={}",
                now, savingsAccounts.size(), raised.size());
    }

    /**
     * Everything this customer has been told, newest first, read and unread together.
     *
     * <p>Not annotated: a package-private method is invisible to the transactional proxy, so an
     * annotation here would be a promise the container cannot keep. It is one query, which is
     * atomic on its own.
     */
    List<RaisedNotification> notificationsOf(long customerId) {
        List<RaisedNotification> raised = notifications
                .findByCustomerIdOrderByRaisedAtDescIdDesc(customerId).stream()
                .map(RaisedNotification::of)
                .toList();
        log.debug("notifications read customerId={} notifications={}", customerId, raised.size());
        return raised;
    }

    /**
     * The one thing this account's balance has to say tonight, if it has anything to say at all.
     *
     * <p>At most one: a balance is in one position, and the notification is about the position
     * having changed rather than about the amount that changed it. A deposit that vaults from
     * nothing to EUR 2.600 is one occasion, not four.
     */
    private Optional<Notification> whatThisAccountsBalanceHasToSay(long savingsAccountId,
                                                                   Instant now) {
        Optional<AccountHolder> holder = accounts.holderOfSavingsAccount(savingsAccountId);
        if (holder.isEmpty()) {
            // Only reachable if an account is closed between the listing and this read, which
            // nothing in this application does yet. Said out loud because a sweep that considered an
            // account and raised nothing for it should never be silent about why.
            log.warn("account passed over for a balance notification savingsAccountId={} "
                    + "reason=nobody holds it any more", savingsAccountId);
            return Optional.empty();
        }
        long customerId = holder.get().customerId();
        BigDecimal balance = deposits.moneyBalanceOf(savingsAccountId);
        Optional<BigDecimal> standsOn = BalanceThresholds.theRungStoodOnWith(balance);
        Optional<Notification> lastSaid = notifications
                .findFirstBySavingsAccountIdAndReasonInOrderByRaisedAtDescIdDesc(
                        savingsAccountId, NotificationReason.THE_BALANCE_REASONS);
        Optional<BigDecimal> stoodOn = theRungThatWasLastSaidToBeStoodOn(lastSaid);
        // The inputs behind the decision, before it is taken: the balance, where that puts the
        // account, where the record says it was, and which row that reading came from. A reviewer
        // redoes the comparison from this line.
        log.debug("the rung a savings account stands on savingsAccountId={} customerId={} "
                        + "balance={} rungNow={} rungLastSaid={} fromNotificationId={}",
                savingsAccountId, customerId, asMoney(balance), asMoney(standsOn.orElse(null)),
                asMoney(stoodOn.orElse(null)), lastSaid.map(Notification::getId).orElse(null));
        int moved = howTheRungHasMoved(standsOn, stoodOn);
        if (moved == 0) {
            // Which is every account on the second run of a night, so this is the line that says a
            // sweep raising nothing is a sweep whose balances have not moved between rungs.
            log.debug("account passed over for a balance notification savingsAccountId={} "
                            + "balance={} rung={} reason=it stands on the rung it already stood on",
                    savingsAccountId, asMoney(balance), asMoney(standsOn.orElse(null)));
            return Optional.empty();
        }
        if (moved > 0) {
            // Higher, so the account is standing on a rung: nothing is lower than standing on none.
            return Optional.of(Notification.balanceRungReached(
                    customerId, savingsAccountId, standsOn.orElseThrow(), now));
        }
        // Lower, so the record had it on a rung. The rung named is the lowest one the balance no
        // longer reaches — the class comment argues out why that, and not the rung it was last
        // known on. There is always one, because a balance that has fallen is below the rung it
        // fell from and therefore below the top of the ladder; the fallback names that rung so
        // that an impossible reading cannot make the sweep say nothing at all.
        BigDecimal fallenOff = BalanceThresholds.theRungAbove(balance).orElseGet(stoodOn::orElseThrow);
        return Optional.of(
                Notification.balanceRungLost(customerId, savingsAccountId, fallenOff, now));
    }

    /**
     * Which rung the newest thing said about an account's balance says it was standing on.
     *
     * <p>The sweep's whole memory, and the reason the record has to be readable as a position rather
     * than as an event. A row saying a rung was reached says the balance stood on that rung; a row
     * saying a rung was lost says it stood on the rung below that one; no row at all says it stood
     * on none, which is where every account starts.
     */
    private Optional<BigDecimal> theRungThatWasLastSaidToBeStoodOn(Optional<Notification> lastSaid) {
        if (lastSaid.isEmpty()) {
            return Optional.empty();
        }
        Notification said = lastSaid.get();
        if (said.getReason() == NotificationReason.BALANCE_THRESHOLD_LOST) {
            return BalanceThresholds.theRungBelow(said.getAmount());
        }
        // Read back through the ladder rather than taken at face value, so that a rung the ladder no
        // longer has — it is fixed, but a release could change it — is read as the highest rung the
        // figure does reach instead of as a position that no longer exists.
        return BalanceThresholds.theRungStoodOnWith(said.getAmount());
    }

    /**
     * How the rung an account stands on now compares with the rung it was last said to stand on:
     * positive for higher, negative for lower, zero for the same.
     *
     * <p>Standing on no rung at all is a position and it is the lowest one, which is what lets an
     * account that has never been told anything be announced on the first sweep and an account
     * whose balance has fallen off the ladder altogether be told it has.
     */
    private int howTheRungHasMoved(Optional<BigDecimal> standsOn, Optional<BigDecimal> stoodOn) {
        if (standsOn.isEmpty() && stoodOn.isEmpty()) {
            return 0;
        }
        if (stoodOn.isEmpty()) {
            return 1;
        }
        if (standsOn.isEmpty()) {
            return -1;
        }
        return standsOn.get().compareTo(stoodOn.get());
    }

    /**
     * An amount quoted to the cent for a log line, and null left as null: the figures a notification
     * carries are per-reason, and a line reading {@code amount=null} says which family the row is
     * from.
     */
    private static String asMoney(BigDecimal amount) {
        return amount == null
                ? null
                : amount.setScale(DECIMAL_PLACES_IN_MONEY, RoundingMode.HALF_UP).toPlainString();
    }
}
