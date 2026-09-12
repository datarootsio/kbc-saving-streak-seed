package be.kbc.savingstreak.service;

import be.kbc.savingstreak.domain.Account;
import be.kbc.savingstreak.domain.Member;
import be.kbc.savingstreak.domain.PointsLot;
import be.kbc.savingstreak.domain.SavingsPosition;
import be.kbc.savingstreak.domain.Transfer;
import be.kbc.savingstreak.domain.TransferDirection;
import be.kbc.savingstreak.repo.AccountRepository;
import be.kbc.savingstreak.repo.TransferRepository;
import be.kbc.savingstreak.web.dto.TransferRequest;
import be.kbc.savingstreak.web.dto.TransferResult;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BankingService {

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final OverviewService overviewService;
    private final PointsRules pointsRules;
    private final PointsWallet wallet;
    private final LoyaltyService loyalty;
    private final NotificationService notifications;
    private final Clock clock;

    public BankingService(AccountRepository accounts, TransferRepository transfers, OverviewService overviewService,
                          PointsRules pointsRules, PointsWallet wallet, LoyaltyService loyalty,
                          NotificationService notifications, Clock clock) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.overviewService = overviewService;
        this.pointsRules = pointsRules;
        this.wallet = wallet;
        this.loyalty = loyalty;
        this.notifications = notifications;
        this.clock = clock;
    }

    private static String describe(TransferDirection direction, Account from, Account to) {
        return switch (direction) {
            case DEPOSIT -> "Saved to " + to.getName();
            case WITHDRAWAL -> "Withdrawal from " + from.getName();
            case REBALANCE -> "Moved to " + to.getName();
        };
    }

    @Transactional
    public TransferResult transfer(TransferRequest request) {
        // Any anniversary that already came due is paid before this transfer can take the
        // principal away.
        loyalty.settleDue();

        long amountCents = Money.toCents(request.amount());
        if (amountCents <= 0) {
            throw new BusinessRuleException("Enter an amount of at least \u20ac0.01.");
        }
        if (request.fromAccountId().equals(request.toAccountId())) {
            throw new BusinessRuleException("Choose two different accounts.");
        }

        Account from = overviewService.account(request.fromAccountId());
        Account to = overviewService.account(request.toAccountId());
        if (from.getBalanceCents() < amountCents) {
            throw new BusinessRuleException("There is not enough money in " + from.getName() + ".");
        }

        Member member = overviewService.member();
        TransferDirection direction = TransferDirection.between(from.getType(), to.getType());

        LocalDate weekStart = overviewService.currentWeekStart();
        int streakBefore = member.effectiveStreakWeeks(weekStart);
        int points = 0;
        long newSavings = 0;
        int multiplierBp = pointsRules.multiplierBasisPoints(streakBefore);

        // Everything below is decided on the balances as they are now, so it has to run
        // before the money moves: the queries would otherwise flush the new balances first.
        if (direction == TransferDirection.DEPOSIT) {
            // Only savings above the all-time peak are new, so moving the same money out and
            // back in earns nothing the second time.
            long savingsBefore = overviewService.totalSavings();
            long savingsAfter = savingsBefore + amountCents;
            newSavings = Math.max(0, savingsAfter - member.savingsPeakAtLeast(savingsBefore));
            long newSavingsThisWeek = overviewService.newSavingsThisWeek();

            // The week joins the streak as soon as its new savings reach the minimum, and the
            // deposit that gets it there is already paid at the higher multiplier.
            if (pointsRules.securesTheWeek(newSavingsThisWeek + newSavings)) {
                member.secureWeek(weekStart);
            }
            int streak = member.effectiveStreakWeeks(weekStart);
            multiplierBp = pointsRules.multiplierBasisPoints(streak);
            points = pointsRules.pointsFor(newSavings, streak);
            member.raiseSavingsPeak(savingsAfter);
        }

        from.withdraw(amountCents);
        to.deposit(amountCents);

        Instant now = clock.instant();
        Transfer transfer = transfers.save(new Transfer(
                from.getId(),
                to.getId(),
                amountCents,
                direction,
                newSavings,
                points,
                multiplierBp,
                describe(direction, from, to),
                now));
        accounts.saveAll(java.util.List.of(from, to));
        PointsLot lot = points > 0 ? wallet.credit(member.getId(), points, now, transfer.getId()) : null;

        SavingsPosition position = null;
        switch (direction) {
            // Only the part that earned points goes onto a loyalty clock.
            case DEPOSIT -> position = loyalty.openPosition(transfer.getId(), to.getId(), newSavings, now);
            // Money leaving savings stops earning, oldest principal first.
            case WITHDRAWAL -> {
                ForfeitedBonus forfeited = loyalty.withdrawPrincipal(from.getId(), amountCents);
                if (forfeited.isSomething()) {
                    notifications.bonusForfeited(member.getId(), from.getId(), from.getName(),
                            forfeited.points(), forfeited.daysAway(), transfer.getId());
                }
            }
            // Still sitting still, just in another pot, so the clock follows it.
            case REBALANCE -> loyalty.movePrincipal(from.getId(), to.getId(), amountCents);
        }

        return new TransferResult(
                overviewService.toView(transfer,
                        Map.of(from.getId(), from.getName(), to.getId(), to.getName()), lot, position),
                points,
                OverviewService.multiplier(multiplierBp),
                member.effectiveStreakWeeks(weekStart) > streakBefore,
                member.effectiveStreakWeeks(weekStart),
                overviewService.overview());
    }
}
