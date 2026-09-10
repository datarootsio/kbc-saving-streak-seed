package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.accounts.AccountHolder;
import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.deposits.DepositStillHoldingMoney;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.loyalty.LoyaltyService;
import io.dataroots.savingstreak.loyalty.NextAnniversaryOfADeposit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the rules have decided was worth saying: the Notifications module's face to the rest of the
 * application, and the whole of the rule.
 *
 * <p>A module of its own because a notification is not owned by any of the rules it reports on. It
 * reads a savings balance and the deposits still holding money from Deposits, who holds an account
 * from Accounts, and when those deposits next pay from Loyalty; it writes nothing but its own
 * record, and none of those modules learns that notifications exist. It moves no money, credits no
 * points and secures no week: this is a record addressed to a customer, not a rule that pays them.
 *
 * <p>Three things happen here: a sweep raises what the rules have to say, a customer reads what has
 * been said to them ({@link #notificationsOf}), and a customer marks what they have read
 * ({@link #markEverythingReadFor}). The two reads are addressed to a customer rather than to an
 * account — a notification carries the account it is about so the panel can name the pot, but who it
 * concerns is a person, and somebody saving towards two goals has one panel and not two.
 *
 * <p>Two rules, and the sweep runs both over every savings account. One is about where a balance
 * stands on a fixed ladder, argued out below; the other is about a deposit's coming anniversary and
 * is argued out at {@link #whatThisAccountsAnniversariesHaveToSay}. They share the walk, the moment
 * and the one write, and share nothing else — a balance rung is about an account and an anniversary
 * is about one deposit inside it.
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
    private final LoyaltyService loyalty;
    private final NotificationRepository notifications;

    /**
     * For the one moment this module stamps that nobody hands it: the moment a customer looked.
     *
     * <p>The sweep is told when, because its caller runs on a schedule and one moment has to be
     * stamped on everything one run raises. A customer reading their notifications is not a sweep —
     * it is a request arriving now, exactly like a deposit landing or a reward being claimed, and
     * every one of those reads the clock in the service that owns the rule rather than being told
     * the time by a controller. The application's clock, which a trainer can wind, so that a
     * notification read on a wound-forward clock is marked at the moment the application thinks it
     * is.
     */
    private final Clock clock;

    NotificationsService(AccountsService accounts, DepositsService deposits, LoyaltyService loyalty,
                         NotificationRepository notifications, Clock clock) {
        this.accounts = accounts;
        this.deposits = deposits;
        this.loyalty = loyalty;
        this.notifications = notifications;
        this.clock = clock;
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
        int depositsConsidered = 0;
        for (long savingsAccountId : savingsAccounts) {
            Optional<AccountHolder> holder = accounts.holderOfSavingsAccount(savingsAccountId);
            if (holder.isEmpty()) {
                // Only reachable if an account is closed between the listing and this read, which
                // nothing in this application does yet. Said out loud because a sweep that
                // considered an account and raised nothing for it should never be silent about why,
                // and because with nobody to address a notification to there is nothing either half
                // of this sweep could say about the account.
                log.warn("account passed over savingsAccountId={} "
                        + "reason=nobody holds it any more", savingsAccountId);
                continue;
            }
            long customerId = holder.get().customerId();
            whatThisAccountsBalanceHasToSay(savingsAccountId, customerId, now)
                    .ifPresent(raising::add);
            // The deposits still holding money, oldest first — which is both the set the
            // anniversary rule has anything to say about and the order the next withdrawal would
            // drain them in. Read here rather than inside the rule so that the count reaches the
            // one line the sweep logs about itself.
            List<DepositStillHoldingMoney> holding =
                    deposits.depositsStillHoldingMoneyIn(savingsAccountId);
            depositsConsidered += holding.size();
            raising.addAll(
                    whatThisAccountsAnniversariesHaveToSay(savingsAccountId, customerId, holding, now));
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
        // One line per sweep: the moment it judged everything against, how many accounts and how
        // many deposits it looked at, and how many notifications it raised. A quiet night and a
        // night that was handed nothing to look at can be told apart from this line alone.
        log.info("notifications raised asAt={} accountsConsidered={} depositsConsidered={} "
                        + "raised={}",
                now, savingsAccounts.size(), depositsConsidered, raised.size());
    }

    /**
     * Everything this customer has been told, newest first, read and unread together.
     *
     * <p>Read and unread together because the panel is a record rather than an inbox that empties.
     * A rule that fired is worth being able to look up after you have seen it once, and a training
     * application whose whole point is showing a rule fire should not hide the evidence that it did
     * the moment somebody glances at it.
     *
     * <p>Newest first, ordered by the moment each was raised and then by identifier, so that two
     * notifications raised by the same sweep — one sweep stamps one moment on everything it raises —
     * still come back in a settled order rather than in whatever order the rows are read in.
     *
     * <p>Whether the customer exists is asked first, so that somebody nobody has heard of is refused
     * rather than answered with the empty list of somebody who has simply never been told anything.
     * That is the line every per-customer read in this application draws.
     *
     * <p>One transaction over both reads, so the existence check and the list describe the same
     * instant of the record.
     *
     * @throws NotificationRefused if there is no such customer
     */
    @Transactional(readOnly = true)
    public List<RaisedNotification> notificationsOf(long customerId) {
        refuseUnlessTheCustomerExists(customerId);
        List<RaisedNotification> raised = notifications
                .findByCustomerIdOrderByRaisedAtDescIdDesc(customerId).stream()
                .map(RaisedNotification::of)
                .toList();
        log.debug("notifications read customerId={} notifications={} unread={}",
                customerId, raised.size(),
                raised.stream().filter(one -> one.readAt() == null).count());
        return raised;
    }

    /**
     * Marks everything this customer has not yet looked at as looked at, now, and answers the whole
     * list back exactly as {@link #notificationsOf} would.
     *
     * <p>The list comes back so that the caller needs one round trip rather than two. Opening the
     * panel is one action, and a page that had to ask again afterwards to find out what it was
     * showing would render the count it just cleared for as long as the second request took.
     *
     * <p>Idempotent, and the notification is what makes it so: {@link Notification#read} keeps the
     * moment it was first read at, so a second call marks nothing, changes nothing and answers the
     * same list. The count in the INFO line is what was actually marked and not how many the
     * customer holds, which is what makes a second call's line say plainly that it did nothing.
     *
     * <p>Every unread notification of theirs, across every savings account they hold, because that
     * is what the panel showed: it lists everything, so reading it is a statement about everything
     * in it.
     *
     * <p>The whole list is loaded rather than only the unread ones, because both things this method
     * does need it — the unread rows to mark, and all of them to answer with. Two queries would be
     * two readings of a record that changed in between.
     *
     * @throws NotificationRefused if there is no such customer
     */
    @Transactional
    public List<RaisedNotification> markEverythingReadFor(long customerId) {
        refuseUnlessTheCustomerExists(customerId);
        Instant now = clock.instant();
        List<Notification> theirs =
                notifications.findByCustomerIdOrderByRaisedAtDescIdDesc(customerId);
        int marked = 0;
        for (Notification notification : theirs) {
            if (notification.read(now)) {
                marked++;
            }
        }
        // The business event, with the values that decided it: who looked, when they looked, how
        // many were actually marked and how many they hold altogether. A count dropping to zero has
        // this line behind it, and a second call over the same rows says marked=0 rather than going
        // silent.
        log.info("notifications marked read customerId={} notifications={} asAt={} held={}",
                customerId, marked, now, theirs.size());
        return theirs.stream().map(RaisedNotification::of).toList();
    }

    /**
     * Refuses, in the words Accounts owns, unless this application has heard of the customer.
     *
     * <p>One place decides the words and one place says them out loud, which is why both reads call
     * this rather than each asking and refusing for itself: a refusal's reason only reaches whoever
     * asked, and the WARN line is the only copy anybody reviewing the application afterwards can
     * read.
     */
    private void refuseUnlessTheCustomerExists(long customerId) {
        if (accounts.customerExists(customerId)) {
            return;
        }
        String reason = AccountsService.noSuchCustomer(customerId);
        log.warn("notifications rejected customerId={} kind={} reason={}",
                customerId, NotificationRefused.Kind.NO_SUCH_CUSTOMER, reason);
        throw new NotificationRefused(NotificationRefused.Kind.NO_SUCH_CUSTOMER, reason);
    }

    /**
     * The one thing this account's balance has to say tonight, if it has anything to say at all.
     *
     * <p>At most one: a balance is in one position, and the notification is about the position
     * having changed rather than about the amount that changed it. A deposit that vaults from
     * nothing to EUR 2.600 is one occasion, not four.
     */
    private Optional<Notification> whatThisAccountsBalanceHasToSay(long savingsAccountId,
                                                                   long customerId, Instant now) {
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
     * Everything this account's deposits have to say about their coming anniversaries tonight.
     *
     * <p>One notification per deposit at most, and none for most deposits most nights. A deposit is
     * worth saying something about when its next anniversary is near enough
     * ({@link AnAnniversaryComingSoon}), is worth at least one point, and has not already been
     * announced under the reason it would get now.
     *
     * <p><strong>The split is the whole of this rule.</strong> The deposits arrive oldest first,
     * ordered by the moment the money landed and then by identifier, which is precisely the order
     * {@code WithdrawalsService} drains them in — so the first of them is the deposit the next euro
     * withdrawn from this account comes out of. That one's bonus is
     * {@link NotificationReason#LOYALTY_BONUS_AT_RISK}; every other deposit near its anniversary is
     * standing behind money that would go first, and gets
     * {@link NotificationReason#LOYALTY_BONUS_ABOUT_TO_PAY}. Mutually exclusive by construction, so
     * one deposit never says both things about one anniversary — and a deposit that becomes the
     * oldest, when what stood in front of it is emptied, is announced again under the other reason,
     * which is the escalation this feature is for.
     *
     * <p>Nothing here works out what an anniversary falls on or what it pays. Both come from
     * {@code LoyaltyService.whenTheDepositsInAnAccountNextPay} exactly as the deposits table already
     * shows them, and the rate they were worked out from is written down once, in {@code
     * LoyaltyRate}. A tenth computed a second time here would be a second answer to disagree with
     * the first the day anybody reprices loyalty.
     */
    private List<Notification> whatThisAccountsAnniversariesHaveToSay(
            long savingsAccountId, long customerId, List<DepositStillHoldingMoney> holding,
            Instant now) {
        if (holding.isEmpty()) {
            // Which is every account nobody has saved into, so this is the line that says a sweep
            // raising no anniversary for an account had no anniversary to raise one about. The
            // deposits that have been emptied are outside the listing rather than passed over in
            // it: money never comes back into one, and a deposit at zero has no anniversary left to
            // reach.
            log.debug("account passed over for anniversary notifications savingsAccountId={} "
                    + "reason=none of its deposits holds money", savingsAccountId);
            return List.of();
        }
        Map<Long, NextAnniversaryOfADeposit> whenTheyNextPay =
                loyalty.whenTheDepositsInAnAccountNextPay(savingsAccountId);
        Set<AnAnnouncedAnniversary> alreadyAnnounced = whatHasAlreadyBeenAnnouncedFor(holding);
        LocalDate theLastDayWorthSaying = AnAnniversaryComingSoon.theLastDayWorthSayingAsAt(now);
        // The oldest deposit still holding money, which is the one the next withdrawal empties
        // first. Present because the listing is not empty.
        long firstInLineForTheNextWithdrawal = holding.get(0).id();
        // The inputs behind every decision below, before any of them is taken: how far the window
        // reaches tonight, and which deposit the queue puts first. A reviewer redoes the split from
        // this line and the per-deposit lines under it.
        log.debug("the withdrawal queue in a savings account savingsAccountId={} customerId={} "
                        + "depositsStillHoldingMoney={} firstInLineForTheNextWithdrawal={} "
                        + "anniversariesWorthSayingUpToAndIncluding={} alreadyAnnounced={}",
                savingsAccountId, customerId, holding.size(), firstInLineForTheNextWithdrawal,
                theLastDayWorthSaying, alreadyAnnounced.size());
        List<Notification> raising = new ArrayList<>();
        for (DepositStillHoldingMoney deposit : holding) {
            NextAnniversaryOfADeposit nextPays = whenTheyNextPay.get(deposit.id());
            if (nextPays == null) {
                // Loyalty's way of saying a deposit holds nothing, and unreachable while both reads
                // ask the same question inside one transaction. Kept because the absence has a
                // meaning and a sweep that silently dropped a deposit it had counted would be the
                // one silence this module cannot explain.
                log.debug("deposit passed over for an anniversary notification depositId={} "
                        + "reason=it holds no money", deposit.id());
                continue;
            }
            if (!AnAnniversaryComingSoon.isWorthSayingAsAt(nextPays.on(), now)) {
                log.debug("deposit passed over for an anniversary notification depositId={} "
                                + "occursOn={} worthSayingUpToAndIncluding={} "
                                + "reason=its anniversary is further off than thirty days",
                        deposit.id(), nextPays.on(), theLastDayWorthSaying);
                continue;
            }
            if (nextPays.points() == 0) {
                log.debug("deposit passed over for an anniversary notification depositId={} "
                                + "occursOn={} remainingAmount={} "
                                + "reason=a tenth of what it still holds rounds down to no points",
                        deposit.id(), nextPays.on(), asMoney(deposit.remainingAmount()));
                continue;
            }
            NotificationReason reason = deposit.id() == firstInLineForTheNextWithdrawal
                    ? NotificationReason.LOYALTY_BONUS_AT_RISK
                    : NotificationReason.LOYALTY_BONUS_ABOUT_TO_PAY;
            // Added rather than asked, so that the set carries what this run has decided as well as
            // what the record already held: an account listed twice, or a deposit reached twice by
            // any later change to this loop, cannot announce the same occasion under the same reason
            // twice. Which is also what the database's own index refuses.
            if (!alreadyAnnounced.add(
                    new AnAnnouncedAnniversary(deposit.id(), reason, nextPays.on()))) {
                // Which is every deposit on the second run of a night, and on all thirty nights an
                // anniversary is near after the first of them. One occasion, one notification.
                log.debug("deposit passed over for an anniversary notification depositId={} "
                                + "reason=this anniversary has already been announced occursOn={} "
                                + "announcedReason={}",
                        deposit.id(), nextPays.on(), reason);
                continue;
            }
            // The figures behind the decision and, above all, why this deposit got this reason
            // rather than the other one — the split is the load-bearing call in this module and it
            // should not have to be inferred from which enum came out.
            log.debug("a deposit's anniversary is worth saying depositId={} savingsAccountId={} "
                            + "occursOn={} points={} remainingAmount={} "
                            + "firstInLineForTheNextWithdrawal={} reason={}",
                    deposit.id(), savingsAccountId, nextPays.on(), nextPays.points(),
                    asMoney(deposit.remainingAmount()),
                    firstInLineForTheNextWithdrawal, reason);
            raising.add(reason == NotificationReason.LOYALTY_BONUS_AT_RISK
                    ? Notification.anniversaryAtRiskFor(customerId, savingsAccountId, deposit.id(),
                            nextPays.on(), nextPays.points(), now)
                    : Notification.anniversaryComingFor(customerId, savingsAccountId, deposit.id(),
                            nextPays.on(), nextPays.points(), now));
        }
        return raising;
    }

    /**
     * Which anniversaries of these deposits have already been announced, and under which reason.
     *
     * <p>Read in one question for the whole account rather than one per deposit, and read as a set
     * the loop can both ask and add to — so the check against the record and the check against what
     * this run has already decided are one check rather than two that could disagree.
     */
    private Set<AnAnnouncedAnniversary> whatHasAlreadyBeenAnnouncedFor(
            List<DepositStillHoldingMoney> holding) {
        List<Long> depositIds = holding.stream().map(DepositStillHoldingMoney::id).toList();
        return notifications
                .anniversariesAlreadyAnnouncedFor(
                        depositIds, NotificationReason.THE_ANNIVERSARY_REASONS)
                .stream()
                .map(said -> new AnAnnouncedAnniversary(
                        said.getDepositId(), said.getReason(), said.getOccursOn()))
                .collect(Collectors.toCollection(HashSet::new));
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

    /**
     * One anniversary that has been announced: the deposit, the reason it was announced under and
     * the day.
     *
     * <p>The key uniqueness is judged on, in Java and in the database's own index, and the same
     * three columns in both places. The reason belongs in it because the two anniversary reasons are
     * an escalation rather than two names for one statement: a deposit told it is shielded, and
     * later told it is first in line, has said two different things about one day and both are worth
     * having.
     *
     * <p>A record so that equality is the three values, which is the whole of what it is for.
     */
    private record AnAnnouncedAnniversary(long depositId, NotificationReason reason,
                                          LocalDate occursOn) {
    }
}
