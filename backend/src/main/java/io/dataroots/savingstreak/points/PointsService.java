package io.dataroots.savingstreak.points;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.points.PointsCreditRepository.EarnedPoints;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The points ledger, and the only way into it. It credits the points a deposit earns and reports
 * what a savings account has.
 *
 * <p>What it will answer is deliberately this narrow: how many points something earned, how many an
 * account has, and whether a number of them could be spent. A caller cannot learn that points are
 * kept as dated batches, when a batch was earned, or how much of one is left — which is why spending
 * arrived as an addition to this module and changed no caller.
 */
@Service
public class PointsService {

    private final PointsCreditRepository credits;

    PointsService(PointsCreditRepository credits) {
        this.credits = credits;
    }

    /**
     * Credits the base points an amount of money earns, and answers how many that was.
     *
     * <p>One point per whole euro, rounding down: EUR 12.50 earns 12 points and EUR 0.99 earns none.
     * A deposit that earns nothing is still credited, so that every deposit has a batch to point at
     * when it is asked what it earned.
     *
     * <p>The caller says when, because the points were earned at the moment the money moved rather
     * than at the moment this method happened to run.
     */
    @Transactional
    public long creditBasePointsFor(long savingsAccountId, long depositId, BigDecimal amountInEuros,
                                    Instant earnedAt) {
        long points = wholeEurosIn(amountInEuros);
        credits.save(PointsCredit.baseAccrualFor(savingsAccountId, depositId, points, earnedAt));
        return points;
    }

    /**
     * What the savings account can spend, summed from what remains across its credits. Derived on
     * every read, so no stored total can drift away from the batches underneath it.
     */
    @Transactional(readOnly = true)
    public long balanceOf(long savingsAccountId) {
        return credits.remainingPointsOf(savingsAccountId);
    }

    /**
     * Spends points out of the account's batches, oldest first, and answers whether there were
     * enough. Nothing is taken from any batch unless the whole amount can be found.
     *
     * <p>Whether there were enough comes back as an answer rather than as a refusal, because the
     * reason a refusal would give is not this module's to write: what the points were being spent on,
     * and what to call it in front of the person who asked, belongs to whoever is spending them.
     *
     * <p>Oldest first is the rule this ledger keeps dated batches in order to follow. It has no
     * consequence yet — a point is a point, and none of them expire — which is exactly why it is
     * settled now: the slice that expires the oldest points arrives to find them already leaving in
     * that order, rather than having to reorder spending that has already happened.
     *
     * @throws IllegalArgumentException if asked for nothing or for a negative number of points,
     *                                  which is a mistake in the caller rather than a refusal to
     *                                  report to anybody
     */
    @Transactional
    public boolean spend(long savingsAccountId, long points) {
        if (points <= 0) {
            throw new IllegalArgumentException("points to spend has to be more than zero, was " + points);
        }
        List<PointsCredit> oldestFirst = credits.unspentOldestFirst(savingsAccountId);
        // Counted from the batches this spend would draw from, rather than asked of the database a
        // second time: the figure checked and the rows changed are then the same rows.
        long available = oldestFirst.stream().mapToLong(PointsCredit::getRemainingPoints).sum();
        if (available < points) {
            return false;
        }
        long stillToFind = points;
        for (PointsCredit batch : oldestFirst) {
            if (stillToFind == 0) {
                break;
            }
            stillToFind -= batch.take(stillToFind);
        }
        // The batches are managed and would be written out at the end of the transaction anyway.
        // Saying so leaves nothing for a reader to infer from Hibernate's behaviour.
        credits.saveAll(oldestFirst);
        return true;
    }

    /**
     * How many points each of the given deposits earned, keyed by deposit. A deposit this ledger has
     * no record of is simply absent, so a caller can tell "earned nothing" from "never heard of it".
     *
     * <p>Asked for many deposits at once because callers list them that way, and a question per
     * deposit would be a query per deposit.
     */
    @Transactional(readOnly = true)
    public Map<Long, Long> basePointsEarnedBy(Collection<Long> depositIds) {
        return credits.earnedBy(PointsReason.BASE_ACCRUAL, depositIds).stream()
                .collect(Collectors.toMap(EarnedPoints::getSourceReferenceId, EarnedPoints::getPoints));
    }

    private long wholeEurosIn(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.FLOOR).longValueExact();
    }
}
