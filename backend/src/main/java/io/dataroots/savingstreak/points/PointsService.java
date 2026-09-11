package io.dataroots.savingstreak.points;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * reasons, how many points a customer has, whether a number of them could be spent, and what they
 * stand to lose next. A caller cannot learn that points are kept as dated batches, when a batch was
 * earned, or how much of one is left — which is why spending arrived as an addition to this module
 * and changed no caller, and why expiry arrived the same way.
 *
 * <p>Twelve months is this module's rule and lives in {@link PointsExpiry}. Nothing outside can
 * expire a particular batch, or ask when one was earned in order to work the rule out for itself:
 * the ledger sweeps itself when it is told what time it is, and says what that cost.
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
     * Ends every batch in the ledger whose twelve months were up by the given moment, oldest first.
     *
     * <p>Answers nothing. What the sweep took is reported in the INFO line below and nowhere else:
     * its one caller runs on a schedule with nobody waiting on it, and a figure returned to a
     * scheduled method is a figure nothing can read. Handing back a count of batches would also be
     * this module saying out loud that it keeps batches, which is the one thing it does not say.
     *
     * <p>Public, unlike everything else here that only the nightly job uses, and not because
     * anything outside wants it: {@code @Transactional} is applied by a proxy, and a proxy cannot
     * advise a method that is not public — the annotation would be silently ignored and a
     * half-finished sweep would commit. The power this leaks is the power to run the nightly job
     * early, which is idempotent and is exactly what the development jobs endpoint offers anyway.
     *
     * <p>The caller says what time it is, exactly as {@link #creditPointsFor} is told when the money
     * moved. This module has no clock of its own and reads none: a sweep run against a wound-forward
     * clock has to judge anniversaries against the moment the application thinks it is, and a service
     * that read the machine's clock instead would quietly refuse to be demonstrated.
     *
     * <p>Every customer's batches at once, because a sweep is one pass over the ledger. Which
     * customers were affected is not said — a caller that wanted to tell one of them would be a
     * notification, which this is not.
     *
     * <p>A batch already spent down to nothing has nothing left to expire and is left as the fully
     * spent batch it is, rather than marked expired as well: two endings written on one batch would
     * make "what expired" a figure nobody could add up.
     *
     * <p>Idempotent by construction. A batch that has gone carries the moment it went, and the query
     * behind this asks only for batches that have not, so a second sweep over the same rows takes
     * nothing.
     */
    @Transactional
    public void expireOldPoints(Instant now) {
        Instant earnedBefore = PointsExpiry.nothingEarnedAfterThisCanHaveExpiredBy(now);
        List<PointsCredit> oldestFirst = credits.unspentBatchesEarnedBefore(earnedBefore);
        // The window the database was asked for, before anything is judged, so that a sweep that
        // took nothing can be told from a sweep that was handed nothing to look at.
        log.debug("points batches considered for expiry asAt={} earnedBefore={} batches={}",
                now, earnedBefore, oldestFirst.size());
        List<PointsCredit> expired = new ArrayList<>();
        long points = 0;
        for (PointsCredit batch : oldestFirst) {
            Instant anniversary = PointsExpiry.anniversaryOf(batch.getEarnedAt());
            if (anniversary.isAfter(now)) {
                // Inside the cut-off's slack rather than inside its twelve months. Said out loud
                // because it is the one place the query and the rule disagree on purpose, and a
                // reader counting batches would otherwise be short.
                log.debug("points batch still inside its twelve months batchId={} customerId={} "
                                + "earnedAt={} anniversary={} pointsLeft={}",
                        batch.getId(), batch.getCustomerId(), batch.getEarnedAt(), anniversary,
                        batch.getRemainingPoints());
                continue;
            }
            long taken = batch.expire(anniversary);
            points += taken;
            expired.add(batch);
            // One line per batch, and deliberately not guarded by isDebugEnabled the way the
            // streak walk's are: there is nothing to render here, only getters, and this is the
            // only record of which batch went and what it was worth when it did. A sweep runs once
            // a night rather than on every page load, so the volume is a night's worth of batches
            // and it is the half of the answer the INFO line's totals cannot give.
            log.debug("points batch expired batchId={} customerId={} reason={} earnedAt={} "
                            + "anniversary={} pointsExpired={}",
                    batch.getId(), batch.getCustomerId(), batch.getReason(), batch.getEarnedAt(),
                    anniversary, taken);
        }
        // The batches are managed and would be written out at the end of the transaction anyway;
        // saying so leaves nothing for a reader to infer from Hibernate's behaviour, as the spend
        // above does.
        credits.saveAll(expired);
        // One line per sweep with everything that decided it: the moment it judged anniversaries
        // against, the cut-off the query used, and what it took. A balance that dropped overnight is
        // explainable from this line alone, and a sweep that ended forty batches holding nothing
        // between them can be told from a sweep that found nothing at all.
        log.info("points expired asAt={} earnedBefore={} batchesConsidered={} batches={} points={}",
                now, earnedBefore, oldestFirst.size(), expired.size(), points);
    }

    /**
     * The next points this customer stands to lose, and nothing when there are none: how many, and
     * the moment their twelve months are up.
     *
     * <p>Read from the same batches a spend draws from, which is what makes the two answers
     * consistent by construction: the points that would pay for the next reward are the points that
     * would otherwise be the next to go.
     *
     * <p>The earliest day is found rather than taken off the front of the list. The batches come back
     * in the order they were earned, and earned order is <em>almost</em> anniversary order but not
     * quite: twelve calendar months clamp 29 February back onto the 28th, so a batch earned just
     * before midnight on the 28th outlives one earned just after it. Two passes cost nothing on a
     * handful of rows and mean this answer does not rest on an ordering that is nearly true.
     *
     * <p>Empty rather than zero for a customer with nothing left. "No points expire next" and "zero
     * points expire on some date" are different statements, and only the first of them is true of
     * somebody who has never earned anything — whoever is showing this to them should be able to
     * say nothing rather than nothing-on-a-date.
     *
     * <p>A query per read of an account, which the two busiest endpoints in the application both
     * make. It reads one customer's unspent batches and nothing else, which is the same handful of
     * rows a spend already reads, and it is the price of the figure being derived rather than stored.
     */
    @Transactional(readOnly = true)
    public Optional<PointsExpiringNext> whatExpiresNextFor(long customerId) {
        List<PointsCredit> surviving = credits.unspentOldestFirst(customerId);
        if (surviving.isEmpty()) {
            log.debug("nothing left to expire customerId={}", customerId);
            return Optional.empty();
        }
        LocalDate soonest = surviving.stream()
                .map(PointsService::theDayItExpires)
                .min(LocalDate::compareTo)
                .orElseThrow();
        long points = surviving.stream()
                .filter(batch -> soonest.equals(theDayItExpires(batch)))
                .mapToLong(PointsCredit::getRemainingPoints)
                .sum();
        // How many of their surviving batches are in the figure, so that a customer's "42 points go
        // on Tuesday" can be checked against the batches behind it rather than taken on trust.
        log.debug("points due to expire next customerId={} on={} points={} batchesSurviving={}",
                customerId, soonest, points, surviving.size());
        return Optional.of(new PointsExpiringNext(points, soonest));
    }

    /** The day this batch's twelve months are up, in the zone the application reads a calendar in. */
    private static LocalDate theDayItExpires(PointsCredit batch) {
        return PointsExpiry.dayOf(PointsExpiry.anniversaryOf(batch.getEarnedAt()));
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
     * <p>Oldest first is the rule this ledger keeps dated batches in order to follow, and expiry is
     * what it was settled for: the batch a spend draws from first is the one nearest its twelve
     * months, so claiming anything at all spends exactly the points that were about to go. A batch
     * only ever expires because it survived twelve months of the customer not spending that far down
     * their pot.
     *
     * <p>An expired batch is not one of the batches this draws from, however much was left in it.
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
