package be.kbc.savingstreak.service;

import be.kbc.savingstreak.domain.Account;
import be.kbc.savingstreak.domain.AccountType;
import be.kbc.savingstreak.domain.PointsLot;
import be.kbc.savingstreak.domain.PointsSource;
import be.kbc.savingstreak.domain.SavingsPosition;
import be.kbc.savingstreak.domain.Transfer;
import be.kbc.savingstreak.repo.PointsLotRepository;
import be.kbc.savingstreak.repo.SavingsPositionRepository;
import be.kbc.savingstreak.repo.TransferRepository;
import be.kbc.savingstreak.web.dto.AccountTimeline;
import be.kbc.savingstreak.web.dto.TimelineEvent;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Builds the timeline drawn under a savings account: the money that moved in and out over the
 * past year, and the points events maturing over the year ahead.
 */
@Service
public class AccountTimelineService {

    private static final int MONTHS_EITHER_SIDE = 12;

    private final TransferRepository transfers;
    private final SavingsPositionRepository positions;
    private final PointsLotRepository lots;
    private final LoyaltyService loyalty;
    private final Clock clock;
    private final ZoneId zone;

    public AccountTimelineService(TransferRepository transfers, SavingsPositionRepository positions,
                                  PointsLotRepository lots, LoyaltyService loyalty, Clock clock, ZoneId zone) {
        this.transfers = transfers;
        this.positions = positions;
        this.lots = lots;
        this.loyalty = loyalty;
        this.clock = clock;
        this.zone = zone;
    }

    public AccountTimeline forAccount(Account account) {
        if (account.getType() != AccountType.SAVINGS) {
            return null;
        }

        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        LocalDate from = today.minusMonths(MONTHS_EITHER_SIDE);
        LocalDate to = today.plusMonths(MONTHS_EITHER_SIDE);
        Window window = new Window(from, to);

        List<TimelineEvent> events = new ArrayList<>();
        long largestMovementCents = 0;
        int earlier = 0;

        for (Transfer transfer : transfers.findAll()) {
            boolean incoming = account.getId().equals(transfer.getToAccountId());
            boolean outgoing = account.getId().equals(transfer.getFromAccountId());
            if (!incoming && !outgoing) {
                continue;
            }
            LocalDate day = dayOf(transfer.getCreatedAt());
            if (day.isBefore(from)) {
                earlier++;
                continue;
            }
            largestMovementCents = Math.max(largestMovementCents, transfer.getAmountCents());
            events.add(new TimelineEvent(
                    incoming ? "MONEY_IN" : "MONEY_OUT",
                    day,
                    window.positionOf(day),
                    Money.toEuros(transfer.getAmountCents()),
                    0,
                    (incoming ? "In " : "Out ") + euros(transfer.getAmountCents())));
        }

        // Points events hang off the deposits that earned them.
        List<SavingsPosition> accountPositions = positions.findAll().stream()
                .filter(position -> account.getId().equals(position.getAccountId()))
                .toList();
        List<Long> depositIds = accountPositions.stream().map(SavingsPosition::getTransferId).toList();
        Map<Long, List<PointsLot>> lotsByTransfer = depositIds.isEmpty()
                ? Map.of()
                : lots.findByTransferIdIn(depositIds).stream()
                        .collect(Collectors.groupingBy(PointsLot::getTransferId));

        Instant now = clock.instant();
        int nextBonusPoints = 0;
        LocalDate nextBonusOn = null;
        int expiringNextPoints = 0;
        LocalDate expiringNextOn = null;

        for (SavingsPosition position : accountPositions) {
            for (PointsLot lot : lotsByTransfer.getOrDefault(position.getTransferId(), List.of())) {
                LocalDate expiry = dayOf(lot.getExpiresAt());
                if (lot.getSource() == PointsSource.LOYALTY_BONUS) {
                    // A bonus batch is dated to the anniversary that paid it.
                    LocalDate paidOn = dayOf(lot.getEarnedAt());
                    if (window.holds(paidOn)) {
                        events.add(new TimelineEvent("BONUS_PAID", paidOn, window.positionOf(paidOn), null,
                                lot.getPointsEarned(), "Loyalty bonus +" + lot.getPointsEarned()));
                    }
                    continue;
                }
                if (lot.hasExpiredAt(now)) {
                    if (lot.getPointsRemaining() > 0 && window.holds(expiry)) {
                        events.add(new TimelineEvent("POINTS_LAPSED", expiry, window.positionOf(expiry), null,
                                lot.getPointsRemaining(), lot.getPointsRemaining() + " points lapsed unused"));
                    }
                } else if (lot.getPointsRemaining() > 0) {
                    if (window.holds(expiry)) {
                        events.add(new TimelineEvent("POINTS_EXPIRING", expiry, window.positionOf(expiry), null,
                                lot.getPointsRemaining(), lot.getPointsRemaining() + " points expire"));
                    }
                    if (expiringNextOn == null || expiry.isBefore(expiringNextOn)) {
                        expiringNextOn = expiry;
                        expiringNextPoints = lot.getPointsRemaining();
                    }
                }
            }

            Instant anniversary = loyalty.nextAnniversary(position);
            if (anniversary == null) {
                continue;
            }
            LocalDate due = dayOf(anniversary);
            int bonus = loyalty.nextBonusFor(position);
            if (bonus > 0 && window.holds(due)) {
                events.add(new TimelineEvent("BONUS_DUE", due, window.positionOf(due), null, bonus,
                        "Loyalty bonus +" + bonus + " due"));
            }
            if (bonus > 0 && (nextBonusOn == null || due.isBefore(nextBonusOn))) {
                nextBonusOn = due;
                nextBonusPoints = bonus;
            }
        }

        events.sort(Comparator.comparing(TimelineEvent::on).thenComparing(TimelineEvent::kind));
        return new AccountTimeline(
                from, today, to,
                window.positionOf(today),
                Money.toEuros(Math.max(largestMovementCents, 1)),
                events,
                earlier,
                nextBonusPoints, nextBonusOn,
                expiringNextPoints, expiringNextOn);
    }

    /** Maps the account list in one go so each account gets its own timeline. */
    public Map<Long, AccountTimeline> forAccounts(List<Account> accounts) {
        return accounts.stream()
                .filter(account -> account.getType() == AccountType.SAVINGS)
                .collect(Collectors.toMap(Account::getId, this::forAccount, (first, second) -> first));
    }

    private LocalDate dayOf(Instant moment) {
        return LocalDate.ofInstant(moment, zone);
    }

    private static String euros(long cents) {
        return "€" + Money.toEuros(cents).toPlainString();
    }

    /** The window, and where a date sits inside it. */
    private record Window(LocalDate from, LocalDate to) {

        boolean holds(LocalDate day) {
            return !day.isBefore(from) && !day.isAfter(to);
        }

        int positionOf(LocalDate day) {
            long span = ChronoUnit.DAYS.between(from, to);
            long offset = ChronoUnit.DAYS.between(from, day);
            return (int) Math.max(0, Math.min(100, Math.round(offset * 100.0 / span)));
        }
    }
}
