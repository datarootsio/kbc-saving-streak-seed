package be.kbc.savingstreak.service;

import be.kbc.savingstreak.domain.PointsSource;
import be.kbc.savingstreak.domain.SavingsPosition;
import be.kbc.savingstreak.repo.MemberRepository;
import be.kbc.savingstreak.repo.SavingsPositionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The loyalty rate: every full 12 months a deposit stays untouched, it pays another
 * {@value PointsRules#LOYALTY_BONUS_BP} basis points of the base points its remaining
 * principal is worth.
 *
 * <p>Each deposit keeps its own clock, so anniversaries are settled per position rather than
 * on one account-wide date. Settlement is done on access and counts every anniversary that has
 * passed, which means an app that was not running for two years still pays both of them.
 */
@Service
public class LoyaltyService {

    private final SavingsPositionRepository positions;
    private final PointsWallet wallet;
    private final MemberRepository members;
    private final PointsRules pointsRules;
    private final Clock clock;
    private final ZoneId zone;

    public LoyaltyService(SavingsPositionRepository positions, PointsWallet wallet, MemberRepository members,
                          PointsRules pointsRules, Clock clock, ZoneId zone) {
        this.positions = positions;
        this.wallet = wallet;
        this.members = members;
        this.pointsRules = pointsRules;
        this.clock = clock;
        this.zone = zone;
    }

    /** Puts the part of a deposit that earned points onto its own clock. */
    @Transactional
    public SavingsPosition openPosition(Long transferId, Long accountId, long principalCents, Instant openedAt) {
        if (principalCents <= 0) {
            return null;
        }
        return positions.save(new SavingsPosition(transferId, accountId, principalCents, openedAt));
    }

    /**
     * Takes money out of an account's positions, oldest first. The part that leaves stops
     * earning, so the anniversary it had not reached yet is forfeited for that part.
     */
    @Transactional
    public ForfeitedBonus withdrawPrincipal(Long accountId, long amountCents) {
        long outstanding = amountCents;
        List<SavingsPosition> open = openIn(accountId);
        LocalDate today = LocalDate.ofInstant(clock.instant(), zone);
        int forfeited = 0;
        long soonest = Long.MAX_VALUE;

        for (SavingsPosition position : open) {
            if (outstanding == 0) {
                break;
            }
            int bonusBefore = pointsRules.loyaltyBonusFor(position.getPrincipalLeftCents());
            outstanding -= position.takeUpTo(outstanding);
            int bonusAfter = pointsRules.loyaltyBonusFor(position.getPrincipalLeftCents());

            LocalDate due = LocalDate.ofInstant(
                    anniversary(position, position.getAnniversariesPaid() + 1), zone);
            long days = ChronoUnit.DAYS.between(today, due);
            if (days >= 0 && days <= NotificationService.VESTING_NOTICE_DAYS) {
                forfeited += bonusBefore - bonusAfter;
                soonest = Math.min(soonest, days);
            }
        }
        positions.saveAll(open);
        return forfeited > 0 ? new ForfeitedBonus(forfeited, soonest) : ForfeitedBonus.NOTHING;
    }

    /**
     * Follows money moved to another savings account: it is still sitting still, so it keeps
     * its clock, it just sits somewhere else now.
     */
    @Transactional
    public void movePrincipal(Long fromAccountId, Long toAccountId, long amountCents) {
        long outstanding = amountCents;
        List<SavingsPosition> open = openIn(fromAccountId);
        List<SavingsPosition> moved = new ArrayList<>();
        for (SavingsPosition position : open) {
            if (outstanding == 0) {
                break;
            }
            if (position.getPrincipalLeftCents() <= outstanding) {
                outstanding -= position.getPrincipalLeftCents();
                position.moveTo(toAccountId);
            } else {
                moved.add(position.splitOff(outstanding, toAccountId));
                outstanding = 0;
            }
        }
        positions.saveAll(open);
        positions.saveAll(moved);
    }

    /**
     * Pays every anniversary that has come due. Returns the points credited, so a caller can
     * tell the customer about it.
     */
    @Transactional
    public int settleDue() {
        return settleDue(clock.instant());
    }

    /** Settles as at a given moment, which is what makes anniversaries testable. */
    @Transactional
    public int settleDue(Instant now) {
        // Savings accounts belong to the signed-in customer, so that is who a bonus is paid to.
        Long memberId = members.findFirstByPrimaryCustomerTrue().map(member -> member.getId()).orElse(null);
        if (memberId == null) {
            return 0;
        }
        int credited = 0;
        List<SavingsPosition> open = positions.findByPrincipalLeftCentsGreaterThan(0);
        for (SavingsPosition position : open) {
            for (int year = position.getAnniversariesPaid() + 1; ; year++) {
                Instant anniversary = anniversary(position, year);
                if (anniversary.isAfter(now)) {
                    break;
                }
                int points = pointsRules.loyaltyBonusFor(position.getPrincipalLeftCents());
                // The batch is dated to the anniversary, so it runs its own 12 months from there.
                if (points > 0) {
                    wallet.credit(memberId, points, anniversary, position.getTransferId(),
                            PointsSource.LOYALTY_BONUS);
                }
                position.recordAnniversary(points);
                credited += points;
            }
        }
        positions.saveAll(open);
        return credited;
    }

    public Instant anniversary(SavingsPosition position, int year) {
        return position.getOpenedAt().atZone(zone)
                .plusMonths((long) PointsRules.LOYALTY_PERIOD_MONTHS * year)
                .toInstant();
    }

    /** The next anniversary of a position, or null once it has been fully withdrawn. */
    public Instant nextAnniversary(SavingsPosition position) {
        return position.isOpen() ? anniversary(position, position.getAnniversariesPaid() + 1) : null;
    }

    public int nextBonusFor(SavingsPosition position) {
        return position.isOpen() ? pointsRules.loyaltyBonusFor(position.getPrincipalLeftCents()) : 0;
    }

    public Map<Long, SavingsPosition> byTransfer(Collection<Long> transferIds) {
        if (transferIds.isEmpty()) {
            return Map.of();
        }
        return positions.findByTransferIdIn(transferIds).stream()
                .collect(Collectors.toMap(SavingsPosition::getTransferId, Function.identity(),
                        (first, second) -> first));
    }

    private List<SavingsPosition> openIn(Long accountId) {
        return positions.findByAccountIdAndPrincipalLeftCentsGreaterThanOrderByOpenedAtAscIdAsc(accountId, 0);
    }
}
