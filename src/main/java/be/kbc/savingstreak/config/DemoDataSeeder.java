package be.kbc.savingstreak.config;

import be.kbc.savingstreak.domain.Account;
import be.kbc.savingstreak.domain.AccountType;
import be.kbc.savingstreak.domain.Member;
import be.kbc.savingstreak.domain.Reward;
import be.kbc.savingstreak.domain.RewardCategory;
import be.kbc.savingstreak.domain.Transfer;
import be.kbc.savingstreak.domain.TransferDirection;
import be.kbc.savingstreak.repo.AccountRepository;
import be.kbc.savingstreak.repo.MemberRepository;
import be.kbc.savingstreak.repo.RedemptionRepository;
import be.kbc.savingstreak.repo.RewardRepository;
import be.kbc.savingstreak.repo.NotificationRepository;
import be.kbc.savingstreak.repo.PointsGiftRepository;
import be.kbc.savingstreak.repo.PointsLotRepository;
import be.kbc.savingstreak.repo.SavingsPositionRepository;
import be.kbc.savingstreak.repo.TransferRepository;
import be.kbc.savingstreak.service.LoyaltyService;
import be.kbc.savingstreak.service.PointsRules;
import be.kbc.savingstreak.service.PointsWallet;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Fills the SQLite database with a believable starting position on first boot. */
@Component
public class DemoDataSeeder {

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final RewardRepository rewards;
    private final RedemptionRepository redemptions;
    private final MemberRepository members;
    private final PointsLotRepository pointsLots;
    private final PointsGiftRepository pointsGifts;
    private final NotificationRepository notifications;
    private final SavingsPositionRepository savingsPositions;
    private final PointsRules pointsRules;
    private final PointsWallet wallet;
    private final LoyaltyService loyalty;
    private final Clock clock;
    private final ZoneId zone;

    public DemoDataSeeder(AccountRepository accounts, TransferRepository transfers, RewardRepository rewards,
                          RedemptionRepository redemptions, MemberRepository members, PointsLotRepository pointsLots,
                          PointsGiftRepository pointsGifts, NotificationRepository notifications,
                          SavingsPositionRepository savingsPositions,
                          PointsRules pointsRules, PointsWallet wallet, LoyaltyService loyalty, Clock clock,
                          ZoneId zone) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.rewards = rewards;
        this.redemptions = redemptions;
        this.members = members;
        this.pointsLots = pointsLots;
        this.pointsGifts = pointsGifts;
        this.notifications = notifications;
        this.savingsPositions = savingsPositions;
        this.pointsRules = pointsRules;
        this.wallet = wallet;
        this.loyalty = loyalty;
        this.clock = clock;
        this.zone = zone;
    }

    @Transactional
    public void seedIfEmpty() {
        if (members.count() > 0) {
            return;
        }
        reset();
    }

    @Transactional
    public void reset() {
        // Batch deletes run as immediate DML; a plain deleteAll() would be flushed
        // after the inserts below and collide with the unique IBAN constraint.
        transfers.deleteAllInBatch();
        notifications.deleteAllInBatch();
        pointsGifts.deleteAllInBatch();
        pointsLots.deleteAllInBatch();
        savingsPositions.deleteAllInBatch();
        redemptions.deleteAllInBatch();
        accounts.deleteAllInBatch();
        rewards.deleteAllInBatch();
        members.deleteAllInBatch();

        Member member = new Member("Lotte", "Vermeulen", true);
        members.save(member);
        // People to send points to. They have no accounts of their own in this demo.
        members.saveAll(List.of(
                new Member("Jasper", "De Wit"),
                new Member("Amina", "Haddad"),
                new Member("Bram", "Peeters"),
                new Member("Sofie", "Claes")));

        Account current = accounts.save(new Account("Current account", "BE68 5390 0754 7034",
                AccountType.CURRENT, 243_158L, null, "Your everyday account", null, 0));
        current.alertBelow(200_000L);
        accounts.save(current);
        Account buffer = accounts.save(new Account("Rainy day fund", "BE71 0961 2345 6769",
                AccountType.SAVINGS, 525_000L, 1_000_000L, "For unexpected costs", 175, 1));
        Account holiday = accounts.save(new Account("Lisbon travel fund", "BE62 5100 0754 7061",
                AccountType.SAVINGS, 112_000L, 300_000L, "Holiday, summer 2027", 150, 2));
        Account kids = accounts.save(new Account("Finn's savings", "BE43 0689 9990 9501",
                AccountType.SAVINGS, 68_500L, 250_000L, "For when he turns 18", 200, 3));

        rewards.saveAll(List.of(
                new Reward("Coffee of your choice", "Coffeelab", "A fresh coffee or tea at any Coffeelab in Belgium.",
                        RewardCategory.FOOD_DRINK, 150, 320, "coffee", 0),
                new Reward("Snack voucher", "Panos", "A sandwich or snack of your choice during your lunch break.",
                        RewardCategory.FOOD_DRINK, 200, 500, "snack", 1),
                new Reward("Donation to Bednet", "Bednet vzw", "We donate \u20ac5 so ill children can follow lessons from home.",
                        RewardCategory.DONATION, 250, 500, "heart", 2),
                new Reward("Cinema ticket", "Kinepolis", "One ticket for any film, valid every day of the week.",
                        RewardCategory.ENTERTAINMENT, 900, 1_250, "ticket", 3),
                new Reward("Museum duo pass", "Museums Flanders", "Two tickets for a museum of your choice, valid for a year.",
                        RewardCategory.ENTERTAINMENT, 1_200, 2_400, "museum", 4),
                new Reward("Family cinema pack", "Kinepolis", "Four tickets, two large popcorns and four drinks.",
                        RewardCategory.FAMILY, 2_500, 5_500, "family", 5)));

        // Three weeks that each clear the weekly minimum, and nothing yet in the current week.
        LocalDate thisWeek = LocalDate.ofInstant(clock.instant(), zone).with(DayOfWeek.MONDAY);
        List<Transfer> history = List.of(
                depositOf(current, kids, 40_000L, thisWeek.minusWeeks(75).plusDays(2)),
                depositOf(current, buffer, 30_000L, thisWeek.minusWeeks(3).plusDays(1)),
                // Earned almost a year ago, so its points are about to lapse.
                depositOf(current, kids, 7_500L, thisWeek.minusWeeks(50).plusDays(4)),
                depositOf(current, buffer, 20_000L, thisWeek.minusWeeks(2).plusDays(0)),
                withdrawalOf(buffer, current, 4_000L, thisWeek.minusWeeks(2).plusDays(3)),
                depositOf(current, holiday, 15_000L, thisWeek.minusWeeks(2).plusDays(5)),
                depositOf(current, holiday, 25_000L, thisWeek.minusWeeks(1).plusDays(1)),
                withdrawalOf(holiday, current, 2_500L, thisWeek.minusWeeks(1).plusDays(2)),
                depositOf(current, buffer, 10_000L, thisWeek.minusWeeks(1).plusDays(4)));
        List<Transfer> saved = transfers.saveAll(history);
        saved.stream()
                .filter(transfer -> transfer.getPointsEarned() > 0)
                .forEach(transfer -> {
                    wallet.credit(member.getId(), transfer.getPointsEarned(), transfer.getCreatedAt(),
                            transfer.getId());
                    loyalty.openPosition(transfer.getId(), transfer.getToAccountId(),
                            transfer.getNewSavingsCents(), transfer.getCreatedAt());
                });

        member.raiseSavingsPeak(buffer.getBalanceCents() + holiday.getBalanceCents() + kids.getBalanceCents());
        member.secureWeek(thisWeek.minusWeeks(3));
        member.secureWeek(thisWeek.minusWeeks(2));
        member.secureWeek(thisWeek.minusWeeks(1));

        // Pays the anniversaries the seeded history has already lived through.
        loyalty.settleDue();
    }

    private Transfer depositOf(Account from, Account to, long amountCents, LocalDate day) {
        int points = pointsRules.basePoints(amountCents);
        return new Transfer(from.getId(), to.getId(), amountCents, TransferDirection.DEPOSIT, amountCents, points,
                10_000, "Saved to " + to.getName(), middayOn(day));
    }

    private Transfer withdrawalOf(Account from, Account to, long amountCents, LocalDate day) {
        return new Transfer(from.getId(), to.getId(), amountCents, TransferDirection.WITHDRAWAL, 0, 0,
                10_000, "Withdrawal from " + from.getName(), middayOn(day));
    }

    private Instant middayOn(LocalDate day) {
        return day.atTime(12, 0).atZone(zone).toInstant();
    }
}
