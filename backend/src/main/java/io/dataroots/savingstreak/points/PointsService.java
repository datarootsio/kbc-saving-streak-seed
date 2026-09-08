package io.dataroots.savingstreak.points;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
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
 * what a customer has.
 *
 * <p>A customer, not a savings account. Points are one pot per person: a customer saving towards
 * two goals earns into the same pot from both and spends out of it on either, so where the euros
 * went decides nothing about whose points they are. This module is keyed by the customer throughout
 * and never learns that savings accounts exist.
 *
 * <p>What it will answer is deliberately this narrow: what something earned and under which
 * reasons, how many points a customer has, and whether a number of them could be spent. A caller
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
     *
     * <p>Unmodifiable, because it is handed to the repository and written into a log line: a set a
     * caller could add to is a query that could quietly come to ask for something else.
     */
    private static final Set<PointsReason> EARNED_BY_A_DEPOSIT = Collections.unmodifiableSet(
            EnumSet.of(PointsReason.BASE_ACCRUAL, PointsReason.STREAK_BONUS));

    private final PointsCreditRepository credits;

    PointsService(PointsCreditRepository credits) {
        this.credits = credits;
    }

    /**
     * Credits the points an amount of money earns at a given rate and answers what was credited,
     * broken down by the reason each part of it was earned under.
     *
     * <p>Rounding down twice, and points stay whole. The amount is floored to whole euros first —
     * one point per whole euro, the rule that has always held, so EUR 12.50 is 12 and EUR 0.99 is
     * none — then multiplied by the rate, then floored again. EUR 7.60 at 1.30 is 7 base points and
     * 9 altogether, so a bonus of 2. A deposit whose euros floor away earns nothing at any rate,
     * because a multiple of nothing is nothing.
     *
     * <p>Two batches rather than one, and the second only when there is an uplift to credit. The
     * base accrual is credited exactly as it always was, so the figure the deposit history has
     * reported since before there were streaks still means the euros; the bonus is the difference
     * between that and what the deposit was actually worth. A deposit that earns nothing is still
     * credited its nothing, so that every deposit has a batch to point at when it is asked what it
     * earned — but an uplift of nothing is not an event and leaves no batch behind.
     *
     * <p>The rate arrives already decided. What a run of weeks pays is the Streaks module's rule and
     * this ledger has no opinion about it; what it does insist on is that a rate never pays less than
     * the euros put in, because a bonus is an uplift and a negative one is a caller that has worked
     * something out wrongly.
     *
     * <p>The caller says when, because the points were earned at the moment the money moved rather
     * than at the moment this method happened to run.
     *
     * <p>The customer is named rather than the savings account the money went into. Which of their
     * accounts earned this is on the deposit, which is what {@code depositId} is for.
     *
     * @throws IllegalArgumentException if the rate would pay less than one point per whole euro
     */
    @Transactional
    public PointsByReason creditPointsFor(long customerId, long depositId, BigDecimal amountInEuros,
                                          BigDecimal multiplier, Instant earnedAt) {
        refuseARateThatPaysLessThanTheEuros(customerId, depositId, multiplier);
        long wholeEuros = wholeEurosIn(amountInEuros);
        long paidAtTheRate = pointsOn(wholeEuros, multiplier);
        long streakBonus = paidAtTheRate - wholeEuros;
        // The amount, the rate and what each flooring made of them, so that a credit of nothing for a
        // deposit of EUR 0.99 reads as the rule working rather than as points having gone missing —
        // and so that a bonus of 2 on a base of 7 can be checked against the multiplication a
        // reviewer would do by hand.
        log.debug("points to credit worked out from the amount customerId={} depositId={} "
                        + "amountInEuros={} wholeEuros={} multiplier={} paidAtTheRate={} "
                        + "streakBonus={} earnedAt={}",
                customerId, depositId, amountInEuros, wholeEuros, multiplier, paidAtTheRate,
                streakBonus, earnedAt);
        credits.save(PointsCredit.baseAccrualFor(customerId, depositId, wholeEuros, earnedAt));
        PointsByReason credited = PointsByReason.of(PointsReason.BASE_ACCRUAL, wholeEuros);
        if (streakBonus > 0) {
            credits.save(PointsCredit.streakBonusFor(customerId, depositId, streakBonus, earnedAt));
            credited = new PointsByReason(Map.of(
                    PointsReason.BASE_ACCRUAL, wholeEuros, PointsReason.STREAK_BONUS, streakBonus));
        }
        log.info("points credited customerId={} depositId={} multiplier={} pointsByReason={} points={}",
                customerId, depositId, multiplier, credited.points(), credited.total());
        return credited;
    }

    /**
     * What that many whole euros are worth at that rate: the product, floored, because points are
     * whole. Fractional points would ripple into spending, the balance, the API and the screen for
     * no benefit a customer can see.
     */
    private static long pointsOn(long wholeEuros, BigDecimal multiplier) {
        return BigDecimal.valueOf(wholeEuros)
                .multiply(multiplier)
                .setScale(0, RoundingMode.FLOOR)
                .longValueExact();
    }

    /**
     * Refuses a rate below one, which would credit a bonus of less than nothing.
     *
     * <p>Not a refusal anybody can cause: nobody types a rate, so the only way here is a mistake in
     * whoever worked one out. Said out loud rather than credited anyway, because the alternative is a
     * deposit that quietly earned fewer points than its euros and a balance nobody can explain.
     */
    private static void refuseARateThatPaysLessThanTheEuros(long customerId, long depositId,
                                                            BigDecimal multiplier) {
        if (multiplier == null || multiplier.compareTo(BigDecimal.ONE) < 0) {
            String reason = "a rate never pays less than one point per whole euro, and this one was "
                    + multiplier;
            log.warn("points not credited customerId={} depositId={} reason={}",
                    customerId, depositId, reason);
            throw new IllegalArgumentException(reason);
        }
    }

    /**
     * What the customer can spend, summed from what remains across their credits — every batch they
     * have earned, whichever of their savings accounts earned it. Derived on every read, so no
     * stored total can drift away from the batches underneath it.
     */
    @Transactional(readOnly = true)
    public long balanceOf(long customerId) {
        return credits.remainingPointsOf(customerId);
    }

    /**
     * Spends points out of the customer's batches, oldest first, and answers whether there were
     * enough. Nothing is taken from any batch unless the whole amount can be found.
     *
     * <p>Oldest across the whole pot, not oldest within one savings account: the batches are the
     * customer's, so a reward is paid for by whatever they earned first, wherever they earned it.
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
    public boolean spend(long customerId, long points) {
        if (points <= 0) {
            throw new IllegalArgumentException("points to spend has to be more than zero, was " + points);
        }
        List<PointsCredit> oldestFirst = credits.unspentOldestFirst(customerId);
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
