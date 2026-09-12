package be.kbc.savingstreak.service;

import be.kbc.savingstreak.domain.PointsLot;
import be.kbc.savingstreak.domain.PointsSource;
import be.kbc.savingstreak.repo.PointsLotRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A customer's points wallet. Points lapse {@value PointsRules#POINTS_VALID_MONTHS} months
 * after they are earned, so a balance is always derived from the batches that are still valid
 * rather than from a stored total: a wallet left untouched for a year empties itself correctly.
 */
@Service
public class PointsWallet {

    private final PointsLotRepository lots;
    private final ZoneId zone;

    public PointsWallet(PointsLotRepository lots, ZoneId zone) {
        this.lots = lots;
        this.zone = zone;
    }

    public Instant expiryFor(Instant earnedAt) {
        return earnedAt.atZone(zone).plusMonths(PointsRules.POINTS_VALID_MONTHS).toInstant();
    }

    @Transactional
    public PointsLot credit(Long memberId, int points, Instant earnedAt, Long transferId) {
        return credit(memberId, points, earnedAt, transferId, PointsSource.DEPOSIT);
    }

    @Transactional
    public PointsLot credit(Long memberId, int points, Instant earnedAt, Long transferId, PointsSource source) {
        return lots.save(new PointsLot(memberId, points, earnedAt, expiryFor(earnedAt), transferId, source));
    }

    public int balanceAt(Long memberId, Instant moment) {
        return lots.sumSpendableAt(memberId, moment);
    }

    /** Points this customer earned themselves, so gifts they were given do not count. */
    public int everEarned(Long memberId) {
        return lots.sumEverCreditedFrom(memberId, PointsSource.DEPOSIT)
                + lots.sumEverCreditedFrom(memberId, PointsSource.LOYALTY_BONUS);
    }

    public int everEarnedFromLoyalty(Long memberId) {
        return lots.sumEverCreditedFrom(memberId, PointsSource.LOYALTY_BONUS);
    }

    public int everCredited(Long memberId) {
        return lots.sumEverCredited(memberId);
    }

    /** Points that lapsed before they could be spent. */
    public int lapsedAt(Long memberId, Instant moment) {
        return lots.sumLapsedAt(memberId, moment);
    }

    /** The batch that lapses first, which is also the next one to be spent. */
    public Optional<PointsLot> nextToExpire(Long memberId, Instant moment) {
        return lots.findFirstByMemberIdAndPointsRemainingGreaterThanAndExpiresAtAfterOrderByExpiresAtAscIdAsc(
                memberId, 0, moment);
    }

    /**
     * The batch each of these transfers earned by itself, for showing expiry next to the
     * history. Loyalty batches are left out; they are reported per position instead.
     */
    public Map<Long, PointsLot> byTransfer(Collection<Long> transferIds) {
        if (transferIds.isEmpty()) {
            return Map.of();
        }
        return lots.findByTransferIdIn(transferIds).stream()
                .filter(lot -> lot.getSource() == PointsSource.DEPOSIT)
                .collect(Collectors.toMap(PointsLot::getTransferId, lot -> lot, (first, second) -> first));
    }

    /**
     * Spends points from the batches closest to expiry first, so nothing lapses that could
     * still have been used.
     */
    @Transactional
    public void spend(Long memberId, int points, Instant moment) {
        if (points <= 0) {
            return;
        }
        List<PointsLot> spendable = spendableFor(memberId, moment);
        int outstanding = points;
        for (PointsLot lot : spendable) {
            outstanding -= lot.spendUpTo(outstanding);
            if (outstanding == 0) {
                break;
            }
        }
        requireNothingOutstanding(outstanding);
        lots.saveAll(spendable);
    }

    /**
     * Moves points from one wallet to another, closest to expiry first. The batches keep their
     * original expiry dates, so passing points between customers cannot extend their life.
     */
    @Transactional
    public void giveTo(Long fromMemberId, Long toMemberId, int points, Instant moment, Long giftId) {
        List<PointsLot> spendable = spendableFor(fromMemberId, moment);
        List<PointsLot> received = new ArrayList<>();
        int outstanding = points;
        for (PointsLot lot : spendable) {
            if (outstanding == 0) {
                break;
            }
            PointsLot gifted = lot.giveTo(toMemberId, outstanding, giftId);
            outstanding -= gifted.getPointsEarned();
            received.add(gifted);
        }
        requireNothingOutstanding(outstanding);
        lots.saveAll(spendable);
        lots.saveAll(received);
    }

    private List<PointsLot> spendableFor(Long memberId, Instant moment) {
        return lots.findByMemberIdAndPointsRemainingGreaterThanAndExpiresAtAfterOrderByExpiresAtAscIdAsc(
                memberId, 0, moment);
    }

    private static void requireNothingOutstanding(int outstanding) {
        if (outstanding > 0) {
            throw new BusinessRuleException("You need " + outstanding + " more points for this.");
        }
    }
}
