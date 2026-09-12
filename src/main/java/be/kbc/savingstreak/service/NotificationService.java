package be.kbc.savingstreak.service;

import be.kbc.savingstreak.domain.Account;
import be.kbc.savingstreak.domain.Notification;
import be.kbc.savingstreak.domain.NotificationKind;
import be.kbc.savingstreak.domain.SavingsPosition;
import be.kbc.savingstreak.repo.AccountRepository;
import be.kbc.savingstreak.repo.NotificationRepository;
import be.kbc.savingstreak.repo.SavingsPositionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Works out what the customer should be told: an account crossing a level they set, and a
 * loyalty bonus close enough to its anniversary to be worth protecting.
 *
 * <p>Like loyalty settlement this runs on access rather than on a schedule. Balance alerts
 * fire on the crossing, not on every visit while the balance sits the wrong side of the line,
 * and anniversaries are announced once each thanks to a dedupe key.
 */
@Service
public class NotificationService {

    /** How close an anniversary has to be before it is worth mentioning. */
    public static final int VESTING_NOTICE_DAYS = 30;

    private static final int FEED_SIZE = 25;

    private final NotificationRepository notifications;
    private final AccountRepository accounts;
    private final SavingsPositionRepository positions;
    private final LoyaltyService loyalty;
    private final Clock clock;
    private final ZoneId zone;

    public NotificationService(NotificationRepository notifications, AccountRepository accounts,
                               SavingsPositionRepository positions, LoyaltyService loyalty, Clock clock,
                               ZoneId zone) {
        this.notifications = notifications;
        this.accounts = accounts;
        this.positions = positions;
        this.loyalty = loyalty;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional
    public int evaluate(Long memberId) {
        Instant now = clock.instant();
        int raised = 0;
        raised += checkBalances(memberId, now);
        raised += checkVesting(memberId, now);
        return raised;
    }

    private int checkBalances(Long memberId, Instant now) {
        int raised = 0;
        List<Account> all = accounts.findAllByOrderBySortOrderAsc();
        for (Account account : all) {
            if (account.getAlertBelowCents() != null) {
                boolean breached = account.getBalanceCents() < account.getAlertBelowCents();
                if (breached && !account.wasBelowBreached()) {
                    raise(memberId, NotificationKind.BALANCE_BELOW,
                            account.getName() + " is below " + euros(account.getAlertBelowCents()),
                            "The balance is " + euros(account.getBalanceCents()) + " right now.",
                            0, account.getId(), null, now);
                    raised++;
                }
                account.recordBelowBreached(breached);
            }
            if (account.getAlertAboveCents() != null) {
                boolean breached = account.getBalanceCents() > account.getAlertAboveCents();
                if (breached && !account.wasAboveBreached()) {
                    raise(memberId, NotificationKind.BALANCE_ABOVE,
                            account.getName() + " passed " + euros(account.getAlertAboveCents()),
                            "The balance is " + euros(account.getBalanceCents()) + " right now.",
                            0, account.getId(), null, now);
                    raised++;
                }
                account.recordAboveBreached(breached);
            }
        }
        accounts.saveAll(all);
        return raised;
    }

    private int checkVesting(Long memberId, Instant now) {
        int raised = 0;
        LocalDate today = LocalDate.ofInstant(now, zone);
        for (SavingsPosition position : positions.findByPrincipalLeftCentsGreaterThan(0)) {
            Instant anniversary = loyalty.nextAnniversary(position);
            int bonus = loyalty.nextBonusFor(position);
            if (anniversary == null || bonus <= 0) {
                continue;
            }
            LocalDate due = LocalDate.ofInstant(anniversary, zone);
            long days = ChronoUnit.DAYS.between(today, due);
            if (days < 0 || days > VESTING_NOTICE_DAYS) {
                continue;
            }
            String key = "VEST:" + position.getId() + ":" + (position.getAnniversariesPaid() + 1);
            if (notifications.existsByDedupeKey(key)) {
                continue;
            }
            String accountName = accounts.findById(position.getAccountId())
                    .map(Account::getName)
                    .orElse("your savings");
            raise(memberId, NotificationKind.BONUS_VESTING_SOON,
                    "+" + bonus + " points vest in " + days + (days == 1 ? " day" : " days"),
                    euros(position.getPrincipalLeftCents()) + " in " + accountName
                            + " reaches another year on " + DAY.format(due)
                            + ". Leave it there and the bonus is yours.",
                    bonus, position.getAccountId(), key, now);
            raised++;
        }
        return raised;
    }

    /** Records that a withdrawal gave up a bonus that was about to vest. */
    @Transactional
    public void bonusForfeited(Long memberId, Long accountId, String accountName, int points, long daysAway,
                               Long transferId) {
        if (points <= 0) {
            return;
        }
        raise(memberId, NotificationKind.BONUS_FORFEITED,
                "You gave up +" + points + " points",
                "That money was " + daysAway + (daysAway == 1 ? " day" : " days")
                        + " from another year in " + accountName + ". The bonus it would have paid is gone.",
                points, accountId, "FORFEIT:" + transferId, clock.instant());
    }

    private void raise(Long memberId, NotificationKind kind, String title, String body, int points,
                       Long accountId, String dedupeKey, Instant now) {
        if (dedupeKey != null && notifications.existsByDedupeKey(dedupeKey)) {
            return;
        }
        notifications.save(new Notification(memberId, kind, title, body, points, accountId, dedupeKey, now));
    }

    public List<Notification> feed(Long memberId) {
        return notifications.findByMemberIdOrderByCreatedAtDescIdDesc(memberId, PageRequest.of(0, FEED_SIZE));
    }

    public int unreadCount(Long memberId) {
        return notifications.countByMemberIdAndReadAtIsNull(memberId);
    }

    @Transactional
    public int markAllRead(Long memberId) {
        List<Notification> unread = notifications.findByMemberIdAndReadAtIsNull(memberId);
        Instant now = clock.instant();
        unread.forEach(notification -> notification.markRead(now));
        notifications.saveAll(unread);
        return unread.size();
    }

    @Transactional
    public void setThreshold(Account account, Long belowCents, Long aboveCents) {
        account.alertBelow(belowCents);
        account.alertAbove(aboveCents);
        accounts.save(account);
    }

    private static final DateTimeFormatter DAY =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private static String euros(long cents) {
        return "€" + Money.toEuros(cents).toPlainString();
    }
}
