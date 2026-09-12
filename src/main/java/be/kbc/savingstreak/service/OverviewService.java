package be.kbc.savingstreak.service;

import be.kbc.savingstreak.domain.Account;
import be.kbc.savingstreak.domain.AccountType;
import be.kbc.savingstreak.domain.Member;
import be.kbc.savingstreak.domain.Notification;
import be.kbc.savingstreak.domain.PointsGift;
import be.kbc.savingstreak.domain.PointsLot;
import be.kbc.savingstreak.domain.Redemption;
import be.kbc.savingstreak.domain.Reward;
import be.kbc.savingstreak.domain.SavingsPosition;
import be.kbc.savingstreak.domain.Transfer;
import be.kbc.savingstreak.domain.TransferDirection;
import be.kbc.savingstreak.repo.AccountRepository;
import be.kbc.savingstreak.repo.MemberRepository;
import be.kbc.savingstreak.repo.PointsGiftRepository;
import be.kbc.savingstreak.repo.RedemptionRepository;
import be.kbc.savingstreak.repo.RewardRepository;
import be.kbc.savingstreak.repo.TransferRepository;
import be.kbc.savingstreak.web.dto.AccountTimeline;
import be.kbc.savingstreak.web.dto.AccountView;
import be.kbc.savingstreak.web.dto.ContactView;
import be.kbc.savingstreak.web.dto.GiftView;
import be.kbc.savingstreak.web.dto.NotificationView;
import be.kbc.savingstreak.web.dto.MemberView;
import be.kbc.savingstreak.web.dto.OverviewResponse;
import be.kbc.savingstreak.web.dto.RedemptionView;
import be.kbc.savingstreak.web.dto.RewardView;
import be.kbc.savingstreak.web.dto.TransferView;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assembles everything the single-page frontend needs in one payload. */
@Service
public class OverviewService {

    private static final int TRANSFER_HISTORY_SIZE = 25;

    private final AccountRepository accounts;
    private final TransferRepository transfers;
    private final RewardRepository rewards;
    private final RedemptionRepository redemptions;
    private final MemberRepository members;
    private final PointsGiftRepository gifts;
    private final PointsRules pointsRules;
    private final PointsWallet wallet;
    private final LoyaltyService loyalty;
    private final AccountTimelineService timelineService;
    private final NotificationService notificationService;
    private final Clock clock;
    private final ZoneId zone;

    public OverviewService(AccountRepository accounts, TransferRepository transfers, RewardRepository rewards,
                           RedemptionRepository redemptions, MemberRepository members,
                           PointsGiftRepository gifts, PointsRules pointsRules, PointsWallet wallet,
                           LoyaltyService loyalty, AccountTimelineService timelineService,
                           NotificationService notificationService, Clock clock, ZoneId zone) {
        this.accounts = accounts;
        this.transfers = transfers;
        this.rewards = rewards;
        this.redemptions = redemptions;
        this.members = members;
        this.gifts = gifts;
        this.pointsRules = pointsRules;
        this.wallet = wallet;
        this.loyalty = loyalty;
        this.timelineService = timelineService;
        this.notificationService = notificationService;
        this.clock = clock;
        this.zone = zone;
    }

    public LocalDate currentWeekStart() {
        return LocalDate.ofInstant(clock.instant(), zone).with(DayOfWeek.MONDAY);
    }

    /** The customer this demo is signed in as. */
    public Member member() {
        return members.findFirstByPrimaryCustomerTrue()
                .orElseThrow(() -> new NotFoundException("No customer has been created yet."));
    }

    public Member memberById(Long id) {
        return members.findById(id)
                .orElseThrow(() -> new NotFoundException("Customer " + id + " does not exist."));
    }

    public Account account(Long id) {
        return accounts.findById(id)
                .orElseThrow(() -> new NotFoundException("Account " + id + " does not exist."));
    }

    public Reward reward(Long id) {
        return rewards.findById(id)
                .orElseThrow(() -> new NotFoundException("Reward " + id + " does not exist."));
    }

    /**
     * Loyalty anniversaries are settled on access rather than by a scheduled job, so this
     * writes as well as reads: a wallet nobody looked at for two years still gets both years.
     */
    @Transactional
    public OverviewResponse overview() {
        int loyaltyJustPaid = loyalty.settleDue();
        Member member = member();
        // Alerts are worked out on access too, right after settlement so the loyalty ones
        // reflect the bonuses that just vested.
        notificationService.evaluate(member.getId());
        List<Account> allAccounts = accounts.findAllByOrderBySortOrderAsc();
        Map<Long, String> namesById = new HashMap<>();
        allAccounts.forEach(account -> namesById.put(account.getId(), account.getName()));

        Map<Long, AccountTimeline> timelines = timelineService.forAccounts(allAccounts);
        List<Transfer> history =
                transfers.findAllByOrderByCreatedAtDescIdDesc(PageRequest.of(0, TRANSFER_HISTORY_SIZE));
        List<Long> earningTransferIds = history.stream()
                .filter(transfer -> transfer.getPointsEarned() > 0)
                .map(Transfer::getId)
                .toList();
        Map<Long, PointsLot> lotsByTransfer = wallet.byTransfer(earningTransferIds);
        Map<Long, SavingsPosition> positionsByTransfer = loyalty.byTransfer(earningTransferIds);

        long totalBalance = allAccounts.stream().mapToLong(Account::getBalanceCents).sum();
        long totalSaved = allAccounts.stream()
                .filter(account -> account.getType() == AccountType.SAVINGS)
                .mapToLong(Account::getBalanceCents)
                .sum();

        return new OverviewResponse(
                toView(member),
                allAccounts.stream().map(account -> toView(account, timelines.get(account.getId()))).toList(),
                Money.toEuros(totalBalance),
                Money.toEuros(totalSaved),
                history.stream()
                        .map(transfer -> toView(transfer, namesById, lotsByTransfer.get(transfer.getId()),
                                positionsByTransfer.get(transfer.getId())))
                        .toList(),
                rewards.findAllByOrderBySortOrderAsc().stream()
                        .map(reward -> toView(reward, wallet.balanceAt(member.getId(), clock.instant())))
                        .toList(),
                redemptions.findAllByOrderByCreatedAtDescIdDesc().stream().map(OverviewService::toView).toList(),
                contacts(),
                giftsFor(member),
                notificationService.feed(member.getId()).stream().map(OverviewService::toView).toList(),
                notificationService.unreadCount(member.getId()),
                loyaltyJustPaid);
    }

    public MemberView toView(Member member) {
        long newSavingsThisWeek = newSavingsThisWeek();
        long minimum = PointsRules.WEEKLY_MINIMUM_CENTS;
        int weeklyGoalPercent = (int) Math.min(100, newSavingsThisWeek * 100 / minimum);
        int streakWeeks = member.effectiveStreakWeeks(currentWeekStart());
        Instant now = clock.instant();
        PointsLot nextToExpire = wallet.nextToExpire(member.getId(), now).orElse(null);
        return new MemberView(
                member.getFirstName(),
                member.getLastName(),
                member.initials(),
                wallet.balanceAt(member.getId(), now),
                wallet.everEarned(member.getId()),
                streakWeeks,
                member.getBestStreakWeeks(),
                multiplier(pointsRules.multiplierBasisPoints(streakWeeks)),
                multiplier(pointsRules.multiplierBasisPoints(streakWeeks + 1)),
                Money.toEuros(newSavingsThisWeek),
                Money.toEuros(minimum),
                weeklyGoalPercent,
                currentWeekStart().equals(member.getLastDepositWeek()),
                Money.toEuros(member.savingsPeakAtLeast(totalSavings())),
                PointsRules.POINTS_VALID_MONTHS,
                nextToExpire == null ? 0 : nextToExpire.getPointsRemaining(),
                nextToExpire == null ? null : LocalDate.ofInstant(nextToExpire.getExpiresAt(), zone),
                wallet.lapsedAt(member.getId(), now),
                wallet.everEarnedFromLoyalty(member.getId()),
                gifts.sumSentBy(member.getId()),
                gifts.sumReceivedBy(member.getId()));
    }

    /** Savings booked above the peak since Monday of the current week. */
    public long newSavingsThisWeek() {
        Instant weekStart = currentWeekStart().atStartOfDay(zone).toInstant();
        return transfers.sumNewSavingsSince(weekStart);
    }

    public long totalSavings() {
        return accounts.sumBalanceCentsByType(AccountType.SAVINGS);
    }

    public static BigDecimal multiplier(int basisPoints) {
        return BigDecimal.valueOf(basisPoints, 4).setScale(2, RoundingMode.HALF_UP);
    }

    public static AccountView toView(Account account, AccountTimeline timeline) {
        Integer goalProgress = null;
        if (account.getGoalCents() != null && account.getGoalCents() > 0) {
            goalProgress = (int) Math.min(100, account.getBalanceCents() * 100 / account.getGoalCents());
        }
        return new AccountView(
                account.getId(),
                account.getName(),
                account.getIban(),
                account.getType(),
                account.getSubtitle(),
                Money.toEuros(account.getBalanceCents()),
                account.getGoalCents() == null ? null : Money.toEuros(account.getGoalCents()),
                goalProgress,
                account.getInterestBasisPoints() == null
                        ? null
                        : BigDecimal.valueOf(account.getInterestBasisPoints(), 2),
                account.getAlertBelowCents() == null ? null : Money.toEuros(account.getAlertBelowCents()),
                account.getAlertAboveCents() == null ? null : Money.toEuros(account.getAlertAboveCents()),
                timeline);
    }

    public TransferView toView(Transfer transfer, Map<Long, String> accountNames, PointsLot lot,
                               SavingsPosition position) {
        Instant now = clock.instant();
        boolean expired = lot != null && lot.hasExpiredAt(now);
        Instant nextAnniversary = position == null ? null : loyalty.nextAnniversary(position);
        return new TransferView(
                transfer.getId(),
                transfer.getDescription(),
                accountNames.getOrDefault(transfer.getFromAccountId(), "Unknown account"),
                accountNames.getOrDefault(transfer.getToAccountId(), "Unknown account"),
                Money.toEuros(transfer.getAmountCents()),
                transfer.getDirection(),
                transfer.getPointsEarned(),
                lot == null ? null : LocalDate.ofInstant(lot.getExpiresAt(), zone),
                lot == null || expired ? 0 : lot.getPointsRemaining(),
                expired,
                position == null ? 0 : position.getLoyaltyPointsPaid(),
                position == null ? 0 : loyalty.nextBonusFor(position),
                nextAnniversary == null ? null : LocalDate.ofInstant(nextAnniversary, zone),
                position == null ? null : Money.toEuros(position.getPrincipalLeftCents()),
                transfer.getCreatedAt());
    }

    public static RewardView toView(Reward reward, int pointsBalance) {
        int pointsShort = Math.max(0, reward.getPointsCost() - pointsBalance);
        int progress = (int) Math.min(100, (long) pointsBalance * 100 / reward.getPointsCost());
        return new RewardView(
                reward.getId(),
                reward.getTitle(),
                reward.getPartner(),
                reward.getDescription(),
                reward.getCategory(),
                reward.getPointsCost(),
                Money.toEuros(reward.getValueCents()),
                reward.getIcon(),
                pointsShort == 0,
                pointsShort,
                progress);
    }

    private List<ContactView> contacts() {
        return members.findByPrimaryCustomerFalseOrderByFirstNameAsc().stream()
                .map(contact -> new ContactView(contact.getId(), contact.fullName(), contact.initials()))
                .toList();
    }

    private List<GiftView> giftsFor(Member member) {
        Map<Long, Member> everyone = members.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Member::getId, other -> other));
        return gifts.findAllForMember(member.getId()).stream()
                .map(gift -> toView(gift,
                        everyone.get(gift.getFromMemberId()),
                        everyone.get(gift.getToMemberId()),
                        member.getId()))
                .toList();
    }

    /** A gift as the given customer sees it: sent to someone, or received from someone. */
    public static GiftView toView(PointsGift gift, Member from, Member to, Long seenBy) {
        boolean sent = gift.getFromMemberId().equals(seenBy);
        Member counterpart = sent ? to : from;
        return new GiftView(
                gift.getId(),
                sent ? "SENT" : "RECEIVED",
                counterpart == null ? "Unknown customer" : counterpart.fullName(),
                counterpart == null ? "?" : counterpart.initials(),
                gift.getPoints(),
                gift.getMessage(),
                gift.getCreatedAt());
    }

    public static NotificationView toView(Notification notification) {
        return new NotificationView(
                notification.getId(),
                notification.getKind(),
                notification.getTitle(),
                notification.getBody(),
                notification.getPoints(),
                notification.isUnread(),
                notification.getCreatedAt());
    }

    public static RedemptionView toView(Redemption redemption) {
        return new RedemptionView(
                redemption.getId(),
                redemption.getRewardTitle(),
                redemption.getPointsSpent(),
                redemption.getVoucherCode(),
                redemption.getCreatedAt());
    }
}
