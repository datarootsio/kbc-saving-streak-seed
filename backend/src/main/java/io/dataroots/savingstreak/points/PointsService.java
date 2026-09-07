package io.dataroots.savingstreak.points;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.points.PointsCreditRepository.EarnedPoints;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The points ledger, and the only way into it. It credits the points a deposit earns and reports
 * what a savings account has.
 *
 * <p>What it will answer is deliberately this narrow: what something earned and under which
 * reasons, how many points an account has, and whether a number of them could be spent. A caller
 * cannot learn that points are kept as dated batches, when a batch was earned, or how much of one
 * is left — which is why spending arrived as an addition to this module and changed no caller.
 *
 * <p>Earnings come back as a breakdown by {@link PointsReason} rather than as a single figure. The
 * reasons are the ledger's vocabulary and are the one thing about its storage it does say out loud:
 * a caller that reports what a deposit earned has to be able to say what part of it was earned
 * which way, and a total it was handed instead would have to be redefined the moment a second
 * reason exists.
 */
@Service
public class PointsService {

    private static final Logger log = LoggerFactory.getLogger(PointsService.class);

    /**
     * The reasons a deposit can have earned points under, which is what a lookup by deposit asks
     * for. Named here rather than left to the query, so that a new way for a deposit to earn is
     * added to one list and is then reported by every caller that lists deposits.
     */
    private static final Set<PointsReason> EARNED_BY_A_DEPOSIT = EnumSet.of(PointsReason.BASE_ACCRUAL);

    private final PointsCreditRepository credits;

    PointsService(PointsCreditRepository credits) {
        this.credits = credits;
    }

    /**
     * Credits the points an amount of money earns and answers what was credited, broken down by the
     * reason each part of it was earned under.
     *
     * <p>One point per whole euro, rounding down: EUR 12.50 earns 12 points and EUR 0.99 earns none.
     * Base accrual is the whole of it for now, so the breakdown has one entry — a caller reading the
     * total gets the same figure it always did. A deposit that earns nothing is still credited, so
     * that every deposit has a batch to point at when it is asked what it earned.
     *
     * <p>A breakdown rather than a total, because the total is the part that will stop being the
     * whole story: a deposit that earns a bonus as well as its euros has two answers to give, and a
     * caller that was handed one number would have no way to say which of them it holds.
     *
     * <p>The caller says when, because the points were earned at the moment the money moved rather
     * than at the moment this method happened to run.
     */
    @Transactional
    public PointsByReason creditPointsFor(long savingsAccountId, long depositId, BigDecimal amountInEuros,
                                          Instant earnedAt) {
        long wholeEuros = wholeEurosIn(amountInEuros);
        // The amount and what the flooring made of it, so that a credit of nothing for a deposit of
        // EUR 0.99 reads as the rule working rather than as points having gone missing.
        log.debug("points to credit worked out from the amount savingsAccountId={} depositId={} "
                        + "amountInEuros={} wholeEuros={} earnedAt={}",
                savingsAccountId, depositId, amountInEuros, wholeEuros, earnedAt);
        credits.save(PointsCredit.baseAccrualFor(savingsAccountId, depositId, wholeEuros, earnedAt));
        PointsByReason credited = PointsByReason.of(PointsReason.BASE_ACCRUAL, wholeEuros);
        log.info("points credited savingsAccountId={} depositId={} pointsByReason={} points={}",
                savingsAccountId, depositId, credited.points(), credited.total());
        return credited;
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
     * What each of the given deposits earned, broken down by reason and keyed by deposit. A deposit
     * this ledger has no record of is simply absent, so a caller can tell "earned nothing" from
     * "never heard of it".
     *
     * <p>By reason rather than for one reason, so that a caller listing a history reports what each
     * deposit earned and why it earned it. Asking only for base accruals would leave every other
     * reason out of a figure the customer reads as what the deposit earned.
     *
     * <p>Asked for many deposits at once because callers list them that way, and a question per
     * deposit would be a query per deposit.
     */
    @Transactional(readOnly = true)
    public Map<Long, PointsByReason> pointsEarnedBy(Collection<Long> depositIds) {
        Map<Long, List<EarnedPoints>> batchesByDeposit =
                credits.earnedBy(EARNED_BY_A_DEPOSIT, depositIds).stream()
                        .collect(Collectors.groupingBy(EarnedPoints::getSourceReferenceId));
        Map<Long, PointsByReason> earned = batchesByDeposit.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, byDeposit -> pointsByReasonIn(byDeposit.getValue())));
        // How many deposits were asked about against how many were found, so that a history showing
        // nothing against a deposit can be told from a lookup that never asked about it. Counts
        // rather than the breakdowns themselves: this runs on every read of an account's history,
        // and one line per deposit would bury the business events in a page load.
        log.debug("points earned by deposits looked up by reason reasons={} depositsAsked={} depositsFound={}",
                EARNED_BY_A_DEPOSIT, depositIds.size(), earned.size());
        return earned;
    }

    /**
     * One deposit's batches folded into a breakdown. Summed per reason rather than taken one row at
     * a time, because nothing stops a reason from having been credited to a deposit twice and a
     * breakdown that reported only the last of them would be short.
     */
    private static PointsByReason pointsByReasonIn(List<EarnedPoints> batches) {
        Map<PointsReason, Long> byReason = new EnumMap<>(PointsReason.class);
        for (EarnedPoints batch : batches) {
            byReason.merge(batch.getReason(), batch.getPoints(), Long::sum);
        }
        return new PointsByReason(byReason);
    }

    private long wholeEurosIn(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.FLOOR).longValueExact();
    }
}
