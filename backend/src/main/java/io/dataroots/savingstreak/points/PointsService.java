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
import java.util.TreeMap;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.points.PointsCreditRepository.EarnedPoints;
import io.dataroots.savingstreak.scheme.SchemeService;
import io.dataroots.savingstreak.scheme.TheSchemeAsPublished;

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
 * <p>Moving points from one pot to another arrived the same way again, in {@link #movePoints}. It
 * has to live here because the batches and their dates are this ledger's own, and it says only what
 * it moved and when each slice was originally earned — never which batch any of it came off of. Who
 * may give points to whom, and what to call a move that cannot be made, is not this module's
 * question: the ledger has no opinion about people.
 *
 * <p>How long a batch lasts is this module's rule and lives in {@link PointsExpiry}. Nothing outside
 * can expire a particular batch, or ask when one was earned in order to work the rule out for
 * itself: the ledger sweeps itself when it is told what time it is, and says what that cost.
 *
 * <p><strong>The figure the rule is applied with comes from the scheme, and it is asked once, when
 * the batch is credited.</strong> The published lifetime used to be a constant in
 * {@link PointsExpiry} that the sweep recomputed against every night, so the day anybody shortened
 * it every older batch died that night — including the batches whose owners had been promised a
 * year. Now the scheme in force on the day a batch is earned says how long it lasts, that moment is
 * written onto the batch, and every later reader reads what was written. Which makes this module the
 * tenth reader of {@code Scheme} and keeps the direction the scheme module insists on: Points
 * depends on Scheme and Scheme has never heard of Points.
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
     *
     * <p>{@link PointsReason#GIFT_RECEIVED} is deliberately not in it, and that absence is load
     * bearing rather than an omission: points somebody was given were not earned by any deposit of
     * theirs, so no deposit's breakdown grows a field and every existing figure a deposit reports
     * means exactly what it did. A batch credited under that reason references a gift, and reading
     * one as a deposit is precisely what naming the reasons here prevents.
     */
    private static final Set<PointsReason> EARNED_BY_A_DEPOSIT = Collections.unmodifiableSet(
            EnumSet.of(PointsReason.BASE_ACCRUAL, PointsReason.STREAK_BONUS,
                    PointsReason.LOYALTY_BONUS));

    private final PointsCreditRepository credits;

    /**
     * Where the published lifetime of a batch comes from.
     *
     * <p>Read on every credit rather than held, because the scheme is a handful of rows that module
     * deliberately reads rather than caches: a stored copy here would be a second place the answer
     * lives, and the whole point of publishing the scheme is that the copy nobody remembered to
     * refresh is the one that pays somebody the wrong figure. A credit is already a write, so one
     * more small read alongside it is not the cost worth optimising.
     */
    private final SchemeService scheme;

    PointsService(PointsCreditRepository credits, SchemeService scheme) {
        this.credits = credits;
        this.scheme = scheme;
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
        // One promise for both lots, because they are one earning at one moment: a base accrual and
        // the uplift beside it are two batches for the ledger's own reasons and a single event to
        // the customer, and two stamps worked out separately could only ever differ by a bug.
        Instant expiresAt = whenABatchEarnedAtWouldGo(customerId, earnedAt);
        credits.save(PointsCredit.baseAccrualFor(customerId, depositId, wholeEuros, earnedAt,
                expiresAt));
        PointsByReason credited = PointsByReason.of(PointsReason.BASE_ACCRUAL, wholeEuros);
        if (streakBonus > 0) {
            credits.save(PointsCredit.streakBonusFor(customerId, depositId, streakBonus, earnedAt,
                    expiresAt));
            credited = new PointsByReason(Map.of(
                    PointsReason.BASE_ACCRUAL, wholeEuros, PointsReason.STREAK_BONUS, streakBonus));
        }
        log.info("points credited customerId={} depositId={} multiplier={} pointsByReason={} "
                        + "points={} expiresAt={}",
                customerId, depositId, multiplier, credited.points(), credited.total(), expiresAt);
        return credited;
    }

    /**
     * Credits a stated number of points to a customer against a deposit, earned at a stated moment,
     * for the money in that deposit having stayed where it was put.
     *
     * <p>The second way into this ledger, and deliberately a much smaller one than the first. A
     * deposit's own credit arrives as an amount of money and a rate and is priced here, because
     * "one point per whole euro at the rate the week paid" is this module's rule. A loyalty bonus
     * arrives already decided: when an anniversary falls, how often, and what a tenth of the euros
     * comes to are the Loyalty module's rule end to end, and a ledger that recomputed any of it
     * would be a second opinion about a figure that has one.
     *
     * <p>So this module learns nothing about anniversaries. It is handed a number of points and the
     * moment they were earned, exactly as it is handed the moment a deposit's money moved, and it
     * writes one ordinary dated batch — spendable, spent oldest-first, expiring twelve months after
     * the moment given here, and counted in what the customer is told expires next. There is no
     * special case anywhere in the ledger for this reason, which is the point of crediting it this
     * way.
     *
     * <p>The moment is the anniversary rather than the moment the sweep ran, because that is when
     * the money had in fact stayed a further year. It means a bonus paid for an anniversary long
     * past may already be beyond its own twelve months and go in the same night's expiry sweep. Both
     * rules holding at once is the honest answer, and both are in the log.
     *
     * <p>The customer is named rather than the savings account the money is sitting in, as
     * everywhere else here: their points are one pot.
     *
     * @throws IllegalArgumentException if asked to credit nothing or less, which is a mistake in
     *                                  whoever worked the bonus out rather than a refusal to report
     *                                  to anybody — an anniversary worth nothing is not an event and
     *                                  has no batch to leave behind
     */
    @Transactional
    public void creditLoyaltyBonus(long customerId, long depositId, long points, Instant earnedAt) {
        if (points <= 0) {
            String reason = "a loyalty bonus is a batch of points and this one was " + points;
            log.warn("points not credited customerId={} depositId={} reason={}",
                    customerId, depositId, reason);
            throw new IllegalArgumentException(reason);
        }
        Instant expiresAt = whenABatchEarnedAtWouldGo(customerId, earnedAt);
        credits.save(PointsCredit.loyaltyBonusFor(customerId, depositId, points, earnedAt,
                expiresAt));
        // The same line a deposit's own credit writes, in the same words, because it is the same
        // event: points arriving in somebody's pot. A reviewer greps "points credited" and sees
        // every way this ledger has ever grown, with the reason saying which of them this was.
        log.info("points credited customerId={} depositId={} reason={} points={} earnedAt={} "
                        + "expiresAt={}",
                customerId, depositId, PointsReason.LOYALTY_BONUS, points, earnedAt, expiresAt);
    }

    /**
     * Credits a stated number of points to a customer against an award, earned at a stated moment,
     * for a rung of a challenge they had taken on having been reached.
     *
     * <p>The third way into this ledger, and the same small one a loyalty bonus comes through. What
     * a challenge asks for, which rung a reading cleared, what that rung pays and whether it has
     * already been paid are the Challenges module's rule from end to end; a ledger that recomputed
     * any of it would be a second opinion about a figure that has one. So this module learns nothing
     * about challenges, rungs or readings. It is handed a number of points and the moment they were
     * earned, exactly as it is handed the moment a deposit's money moved, and it writes one ordinary
     * dated batch.
     *
     * <p><strong>Ordinary is the entire claim being made here.</strong> The batch expires twelve
     * months after the moment given, is spent oldest-first alongside every other batch, is counted
     * in what the customer is told goes next, can be given away and buys anything in the catalogue —
     * and there is no special case for this reason anywhere in the ledger, in spending, in expiry,
     * in gifting or in the balance. A customer earns points from a challenge in a currency they
     * already understand, and nothing in this module had to learn a second kind of point for that to
     * be true.
     *
     * <p>The reference is the award rather than the deposit that happened to carry the reading past
     * the mark, exactly as a gift's reference is the gift. One deposit may clear three rungs and the
     * next may clear none, so there is no deposit this batch belongs to; the award is what says
     * which rung was paid, what reading won it and when. {@link #EARNED_BY_A_DEPOSIT} therefore does
     * not name this reason, which is what keeps every figure a deposit reports meaning exactly what
     * it did.
     *
     * <p>The moment is when the rung was cleared rather than when a judging pass noticed, because
     * that is when the customer earned it — the same reading a loyalty bonus takes of its own
     * anniversary. The customer is named rather than any savings account: their points are one pot,
     * and a challenge spans every account they hold.
     *
     * @throws IllegalArgumentException if asked to credit nothing or less, which is a mistake in
     *                                  whoever priced the rung rather than a refusal to report to
     *                                  anybody — a rung worth nothing is not something this ledger
     *                                  has a batch to leave behind for
     */
    @Transactional
    public void creditChallengeReward(long customerId, long awardId, long points, Instant earnedAt) {
        if (points <= 0) {
            String reason = "a challenge reward is a batch of points and this one was " + points;
            log.warn("points not credited customerId={} awardId={} reason={}",
                    customerId, awardId, reason);
            throw new IllegalArgumentException(reason);
        }
        Instant expiresAt = whenABatchEarnedAtWouldGo(customerId, earnedAt);
        credits.save(PointsCredit.challengeRewardFor(customerId, awardId, points, earnedAt,
                expiresAt));
        // The same line every other way into this ledger writes, in the same words, because it is
        // the same event: points arriving in somebody's pot. A reviewer greps "points credited" and
        // sees every one of them, with the reason saying which this was.
        log.info("points credited customerId={} awardId={} reason={} points={} earnedAt={} "
                        + "expiresAt={}",
                customerId, awardId, PointsReason.CHALLENGE_REWARD, points, earnedAt, expiresAt);
    }

    /**
     * Credits back what a claim cost, because an administrator revoked the voucher it paid for.
     *
     * <p>The fourth way into this ledger and the same small one the three above come through: a
     * number of points, the thing that caused them, and a moment. What a claim cost, whether the
     * voucher could still be cancelled and whether it has been cancelled already are the Rewards
     * module's rules from end to end, and a ledger with an opinion about any of them would be a
     * second opinion about a figure that has one. This module learns nothing about vouchers.
     *
     * <p><strong>A fresh batch with twelve months of its own, and never the batches that were
     * spent put back.</strong> This is the decision the whole refund turns on, so it is argued
     * here as well as on {@link PointsReason#REDEMPTION_CANCELLED} and on
     * {@code PointsCredit.redemptionCancelledFor}, because it is the thing a reader of any one of
     * the three would want to check. Spending goes oldest-first, so the points a claim took came
     * out of the batches nearest the end of their own twelve months. A cancellation can arrive
     * weeks or months later, by which time some of those batches have expired — and points
     * returned to an expired batch are counted by nothing, spendable by nobody, and reported to
     * the customer as a refund. That is strictly worse than not refunding, because the failure is
     * invisible: the balance simply does not move and there is nothing to point at. Restoring
     * them would also need this ledger to have recorded which batches paid for which claim, which
     * it never has and which nothing else wants; building that record purely in order to reverse
     * it would be inventing storage to make a worse answer possible.
     *
     * <p>So the points arrive through the one door, as an ordinary dated batch, under a reason of
     * its own — the pattern gifting and challenges already set — and the customer gets back what
     * they paid with a clean twelve months on it. It is a slightly better deal than a perfect
     * reversal would have been, and that is the right way round: the cancellation is the scheme's
     * own mistake, and the customer should not be the one absorbing the difference.
     *
     * <p><strong>Ordinary is the whole claim being made.</strong> The batch expires twelve months
     * after the moment given, is spent oldest-first alongside every other batch, is counted in
     * what the customer is told goes next, can be given away and buys anything in the catalogue.
     * There is no special case for this reason in spending, in expiry, in gifting or in the
     * balance, and nothing in this module had to learn a second kind of point for a refund to
     * work.
     *
     * <p>The moment is the cancellation, supplied by the caller off the application's own clock,
     * rather than the moment of the claim it undoes. Dating it at the claim would hand somebody a
     * batch already part-way through its life — and, for a claim made over a year ago, one that
     * the next night's sweep would take away again, which is a refund in name only.
     *
     * @throws IllegalArgumentException if asked to credit nothing or less, which is a mistake in
     *                                  whoever worked the refund out rather than a refusal to
     *                                  report to anybody — no claim in this application ever cost
     *                                  nought points, because no offer may be priced at nought
     */
    @Transactional
    public void creditCancellationRefund(long customerId, long redemptionId, long points,
                                         Instant refundedAt) {
        if (points <= 0) {
            String reason = "a cancellation refund is a batch of points and this one was " + points;
            log.warn("points not credited customerId={} redemptionId={} reason={}",
                    customerId, redemptionId, reason);
            throw new IllegalArgumentException(reason);
        }
        Instant expiresAt = whenABatchEarnedAtWouldGo(customerId, refundedAt);
        credits.save(PointsCredit.redemptionCancelledFor(customerId, redemptionId, points,
                refundedAt, expiresAt));
        // The same line every other way into this ledger writes, in the same words, because it is
        // the same event: points arriving in somebody's pot. A reviewer greps "points credited"
        // and sees every one of them, with the reason saying which this was.
        log.info("points credited customerId={} redemptionId={} reason={} points={} earnedAt={} "
                        + "expiresAt={}",
                customerId, redemptionId, PointsReason.REDEMPTION_CANCELLED, points, refundedAt,
                expiresAt);
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
     * What the customer has earned in all, ever — the credited figures added up, whatever became
     * of them afterwards.
     *
     * <p><strong>A second question about the same ledger, and deliberately not the balance.</strong>
     * A balance is what is left and it goes down: it is spent on rewards and it is taken by the
     * twelve-month sweep. A lifetime never goes down, because it is a record of what somebody
     * did rather than of what they still hold — and that is the only reading under which a
     * reward gated behind "five thousand points earned" is a thing to work towards. Gating on a
     * balance would be gating on <em>not having spent</em>, which is the opposite of what a
     * rewards scheme is trying to encourage, and a customer would lose the right to the offer by
     * claiming anything else.
     *
     * <p>Derived on every read like the balance beside it, and stored nowhere. A running total
     * on the customer would be a second figure that has to agree with the batches underneath it,
     * and the application already holds this line everywhere else it could have kept one.
     *
     * <p>Asked for by the web layer, which hands it to whoever needs it. Rewards has offers that
     * can be restricted to a lifetime of points earned and does not read this module to find
     * out: it names the fact it needs and is given it, which is the arrangement written out on
     * its own {@code CustomerStanding}. Nothing here knows what it is for.
     */
    @Transactional(readOnly = true)
    public long lifetimePointsEarnedBy(long customerId) {
        return credits.everEarnedBy(customerId);
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
        int shortestEverPublished = theShortestLifetimeEverPublished();
        Instant earnedBefore = PointsExpiry.nothingEarnedAfterThisCanHaveExpiredBy(
                now, shortestEverPublished);
        List<PointsCredit> oldestFirst = credits.unspentBatchesEarnedBefore(earnedBefore);
        // The window the database was asked for and the figure that drew it, before anything is
        // judged, so that a sweep that took nothing can be told from a sweep that was handed nothing
        // to look at — and so that a window nobody expected can be traced to the scheme that set it.
        log.debug("points batches considered for expiry asAt={} earnedBefore={} "
                        + "shortestLifetimeEverPublished={} batches={}",
                now, earnedBefore, shortestEverPublished, oldestFirst.size());
        List<PointsCredit> expired = new ArrayList<>();
        long points = 0;
        for (PointsCredit batch : oldestFirst) {
            // Read off the batch and never worked out again. This is the line the whole ticket is
            // about: the sweep acts on the promise the batch was credited with, so a scheme that
            // shortens the lifetime tomorrow cannot reach back and take points somebody was told
            // they had for a year.
            Instant anniversary = batch.getExpiresAt();
            if (anniversary == null) {
                // Cannot happen after PointsOnStartUp has run, which is before this application
                // answers anything — so if it ever does, the batch is left alone and the row is
                // named rather than swept on a date nobody can point at.
                log.warn("points batch not swept batchId={} customerId={} earnedAt={} "
                                + "reason=it carries no moment it was promised it would expire",
                        batch.getId(), batch.getCustomerId(), batch.getEarnedAt());
                continue;
            }
            if (anniversary.isAfter(now)) {
                // Inside the cut-off's slack rather than inside its own lifetime. Said out loud
                // because it is the one place the query and the rule disagree on purpose, and a
                // reader counting batches would otherwise be short.
                log.debug("points batch still inside its lifetime batchId={} customerId={} "
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
        log.info("points expired asAt={} earnedBefore={} shortestLifetimeEverPublished={} "
                        + "batchesConsidered={} batches={} points={}",
                now, earnedBefore, shortestEverPublished, oldestFirst.size(), expired.size(), points);
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

    /**
     * The day this batch was promised it would go, in the zone the application reads a calendar in.
     *
     * <p>Read off the stamp rather than derived from the moment it was earned, so that the date a
     * customer is shown is the date that will actually happen. The two used to be worked out by the
     * same arithmetic in two places; now the sweep and the figure on the screen read one column, and
     * there is no arrangement of published schemes under which a customer can be told one day and
     * swept on another.
     */
    private static LocalDate theDayItExpires(PointsCredit batch) {
        return PointsExpiry.dayOf(batch.getExpiresAt());
    }

    /**
     * How long a batch earned right now would last, in months — the one thing this module says out
     * loud about the figure behind its own rule, and it says it for exactly one caller.
     *
     * <p>The simulator's fold earns points inside a branch and has to know when they would go. It
     * calls {@link PointsExpiry} rather than adding months of its own, which is settled, and that
     * call now needs the lifetime as well as the moment. It could read the scheme itself; it must
     * not. How long a batch of points lasts is this ledger's question, the scheme merely holds the
     * number, and a second module reading that number straight off the scheme is the second place
     * the rule lives — which is the precise shape of bug this whole feature exists to remove. So the
     * fold asks the ledger what the ledger would do, and gets the figure the ledger would have used.
     *
     * <p>As it stands today rather than on some named day, because a branch is folded from a present
     * and everything it earns is earned inside its window. A fold that wanted a different lifetime
     * on a later day of that window would be predicting a publication nobody has made.
     */
    @Transactional(readOnly = true)
    public int howLongABatchEarnedNowLasts() {
        int lifetime = scheme.theSchemeInForce().howLongABatchOfPointsLasts();
        log.debug("how long a batch of points earned now would last months={}", lifetime);
        return lifetime;
    }

    /**
     * The moment a batch earned at this instant is promised it will go: the scheme that was in force
     * on the day it was earned, applied by the rule that owns it.
     *
     * <p>The day the batch was earned rather than today. Most credits are dated now and the two are
     * the same reading, but not all of them are — a loyalty bonus is dated at the anniversary it was
     * paid for, and that anniversary can be months behind a sweep that is catching up. Dating the
     * promise by the scheme in force when the points were actually earned is the same answer a
     * deposit already gives about the rate it was paid at.
     *
     * <p>The whole history is read and resolved to a day rather than the in-force reading being
     * asked for, because the in-force reading is about today and this is about a day that may not be
     * today. Both go through the same rule inside the scheme module, so the two cannot disagree.
     */
    private Instant whenABatchEarnedAtWouldGo(long customerId, Instant earnedAt) {
        LocalDate theDayItWasEarned = PointsExpiry.dayOf(earnedAt);
        TheSchemeAsPublished judging = scheme.theSchemeThroughItsVersions()
                .onTheDayOf(theDayItWasEarned);
        Instant expiresAt = PointsExpiry.anniversaryOf(earnedAt,
                judging.howLongABatchOfPointsLasts());
        // The version that priced the promise and the promise it made, so that a batch going on a
        // date somebody disputes can be traced to the row of the scheme that said so.
        log.debug("the lifetime a batch was promised customerId={} earnedAt={} "
                        + "theDayItWasEarned={} schemeVersion={} months={} expiresAt={}",
                customerId, earnedAt, theDayItWasEarned, judging.version(),
                judging.howLongABatchOfPointsLasts(), expiresAt);
        return expiresAt;
    }

    /**
     * The shortest lifetime any version of the scheme has ever published, which is what draws the
     * sweep's query window.
     *
     * <p>The shortest and not today's, because the window has to be generous about every promise a
     * surviving batch could be carrying: a batch stamped under a six-month lifetime is due six
     * months after it was earned, and a window drawn from a twelve-month figure would not look at it
     * for another half a year. {@link PointsExpiry#nothingEarnedAfterThisCanHaveExpiredBy} argues
     * the arithmetic; this is where the figure it is given comes from.
     *
     * <p>Worked out from the history here rather than asked of the scheme module, because it is a
     * question about what this sweep needs rather than a fact about the scheme. A module holding
     * numbers should not have to learn why a points sweep wants the smallest of one of them.
     */
    private int theShortestLifetimeEverPublished() {
        return scheme.theSchemeThroughItsVersions().everyVersionPublished().stream()
                .mapToInt(TheSchemeAsPublished::howLongABatchOfPointsLasts)
                .min()
                .orElseThrow();
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
            // The refusal itself is worded and warned about by whoever is spending, because the
            // reason belongs to them; what this module knows and they do not is what the pot was
            // made of when it came up short.
            log.debug("points not spent customerId={} points={} reason=only {} left across {} "
                            + "batches with anything left in them",
                    customerId, points, available, oldestFirst.size());
            return false;
        }
        long stillToFind = points;
        // Asked once, before the loop, so that the gathering and the line that says it can never
        // disagree about whether anybody is listening — a level changed mid-spend would otherwise
        // print a list missing its first batches.
        boolean sayWhichBatchesItCameOffOf = log.isDebugEnabled();
        List<String> drawnOn = new ArrayList<>();
        for (PointsCredit batch : oldestFirst) {
            if (stillToFind == 0) {
                break;
            }
            long taken = batch.take(stillToFind);
            stillToFind -= taken;
            // What came out of which batch, in the order it came out, so that the one rule this
            // ledger keeps dated batches in order to follow is readable rather than merely
            // intended. Gathered rather than logged here, so that a spend spread over a long list
            // of batches is still one line in the log, and guarded, because rendering a batch is
            // work — five values per batch the spend reaches — and the string is thrown away when
            // the application runs at INFO. The same reasoning as the withdrawal's drawn-down
            // list, and the opposite of the expiry sweep's per-batch line above, which passes
            // getters and renders nothing — and which runs once a night, where a spend runs on
            // every claim.
            if (sayWhichBatchesItCameOffOf) {
                drawnOn.add("[batchId=" + batch.getId() + " reason=" + batch.getReason()
                        + " earnedAt=" + batch.getEarnedAt() + " taken=" + taken
                        + " leftInIt=" + batch.getRemainingPoints() + "]");
            }
        }
        // The batches are managed and would be written out at the end of the transaction anyway.
        // Saying so leaves nothing for a reader to infer from Hibernate's behaviour.
        credits.saveAll(oldestFirst);
        // The inputs behind the whole decision: which batches were nearest their twelve months, how
        // much each of them gave up, and what was left in the last one the spend reached. A reward
        // paid for out of a bonus reads as one of these, with the reason on it saying so.
        if (sayWhichBatchesItCameOffOf) {
            log.debug("points spent oldest first customerId={} points={} available={} "
                            + "batchesWithSomethingLeft={} drawnOn={}",
                    customerId, points, available, oldestFirst.size(), String.join(" ", drawnOn));
        }
        return true;
    }

    /**
     * Moves a stated number of points from one customer's pot to another's, oldest first, against a
     * stated source reference — and answers with the slices it moved, or nothing at all when the
     * sender does not hold that many. Nothing moves unless the whole amount can be found.
     *
     * <p>Sliced rather than spent-and-recredited, and this is the one decision in it. Each slice
     * arrives in the receiving pot as a batch of its own <em>dated at the moment the batch it came
     * out of was earned</em>, so a move drawn from three batches of different ages arrives as three
     * batches of different ages. A single fresh batch dated now would be simpler and would restart
     * the twelve months on every move: with nothing limiting how often points may be moved, two
     * customers passing the same points back and forth would keep them alive indefinitely, and the
     * twelve-month rule would hold only for whoever never moved any. {@link
     * PointsCredit#giftReceivedFor} carries the same reasoning from the batch's end.
     *
     * <p>Whether there were enough comes back as an answer rather than as a refusal, exactly as
     * {@link #spend} does and for the same reason: what the points were being moved for, and what
     * to call the shortfall in front of the person who asked, belongs to whoever is moving them.
     *
     * <p>That both customers exist is the caller's to have settled: a ledger that knows nothing
     * about people cannot check it, and who exists is the Accounts module's answer. That the two are
     * two different people this does check, because it can and because the alternative is silent
     * nonsense — a pot moved into itself would be cut into fresh slices carrying a gift's reference,
     * and the move would report success having changed nothing anybody asked to change. Said out
     * loud as {@link #spend} says a figure of nothing out loud, and for the same reason: neither is
     * a refusal to report to anybody, both are a mistake in whoever called.
     *
     * <p>The reference is written onto every arriving batch and means whatever the reason on it
     * means; {@link PointsCredit#sourceReferenceId} says so. Nothing here learns what it refers to.
     *
     * <p>Expired batches are not moved, however much was left in them, because they are not batches
     * a spend would draw from either: their points are gone, and moving them would hand over points
     * the sender's own balance has already stopped counting.
     *
     * @throws IllegalArgumentException if asked to move nothing or a negative number of points, or
     *                                  to move a customer's points to themselves — either is a
     *                                  mistake in the caller rather than a refusal to report to
     *                                  anybody
     */
    @Transactional
    public Optional<List<MovedPoints>> movePoints(long fromCustomerId, long toCustomerId, long points,
                                                  long sourceReferenceId) {
        if (points <= 0) {
            String reason = "points to move has to be more than zero, was " + points;
            log.warn("points not moved fromCustomerId={} toCustomerId={} reason={}",
                    fromCustomerId, toCustomerId, reason);
            throw new IllegalArgumentException(reason);
        }
        if (fromCustomerId == toCustomerId) {
            String reason = "points move between two customers, and both of these were "
                    + fromCustomerId;
            log.warn("points not moved fromCustomerId={} toCustomerId={} reason={}",
                    fromCustomerId, toCustomerId, reason);
            throw new IllegalArgumentException(reason);
        }
        List<PointsCredit> oldestFirst = credits.unspentOldestFirst(fromCustomerId);
        // Counted from the batches this move would draw from rather than asked of the database a
        // second time, as the spend above does: the figure checked and the rows changed are then the
        // same rows.
        long available = oldestFirst.stream().mapToLong(PointsCredit::getRemainingPoints).sum();
        // The inputs behind the decision, before it is taken: what the sender's pot was made of when
        // the move was judged, so that a refusal and a move can be told apart by more than their
        // outcome.
        log.debug("points to move judged against the sender's batches fromCustomerId={} "
                        + "toCustomerId={} points={} available={} batchesWithSomethingLeft={} "
                        + "sourceReferenceId={}",
                fromCustomerId, toCustomerId, points, available, oldestFirst.size(), sourceReferenceId);
        if (available < points) {
            // The refusal itself is worded and warned about by whoever is moving the points, because
            // the reason belongs to them; what this module knows and they do not is what the pot was
            // made of when it came up short.
            log.debug("points not moved fromCustomerId={} toCustomerId={} points={} reason=only {} "
                            + "left across {} batches with anything left in them",
                    fromCustomerId, toCustomerId, points, available, oldestFirst.size());
            return Optional.empty();
        }
        long stillToFind = points;
        // Asked once, before the loop, for the reason the spend above gives: a level changed
        // mid-move would otherwise print a list missing its first batches.
        boolean sayWhichBatchesItCameOffOf = log.isDebugEnabled();
        List<String> drawnOn = new ArrayList<>();
        List<MovedPoints> moved = new ArrayList<>();
        List<PointsCredit> arriving = new ArrayList<>();
        for (PointsCredit batch : oldestFirst) {
            if (stillToFind == 0) {
                break;
            }
            long taken = batch.take(stillToFind);
            stillToFind -= taken;
            // The arriving batch is dated at the batch it came out of, which is the whole rule —
            // and it inherits that batch's promise rather than being priced again. Two customers
            // passing points back and forth must not be able to buy the slice a fresh lifetime, and
            // asking the scheme again on the way in is exactly how they would: the scheme in force
            // on the day the points were originally earned is the promise, and the promise is what
            // travels with them. Re-deriving it would also be this ledger applying the rule twice to
            // one batch, which is the habit the stamp was written to break.
            arriving.add(PointsCredit.giftReceivedFor(toCustomerId, sourceReferenceId, taken,
                    batch.getEarnedAt(), batch.getExpiresAt()));
            moved.add(new MovedPoints(taken, batch.getEarnedAt()));
            // What came out of which batch, in the order it came out, gathered rather than logged
            // here so that a move spread over a long list of batches is still one line — and
            // guarded, because rendering a batch is work and the string is thrown away when the
            // application runs at INFO. The same reasoning, and the same shape, as the spend above.
            if (sayWhichBatchesItCameOffOf) {
                drawnOn.add("[batchId=" + batch.getId() + " reason=" + batch.getReason()
                        + " earnedAt=" + batch.getEarnedAt() + " taken=" + taken
                        + " leftInIt=" + batch.getRemainingPoints() + "]");
            }
        }
        // The drawn-on batches are managed and would be written out at the end of the transaction
        // anyway; the arriving ones are new and would not. Saying both leaves nothing for a reader to
        // infer from Hibernate's behaviour.
        credits.saveAll(oldestFirst);
        credits.saveAll(arriving);
        if (sayWhichBatchesItCameOffOf) {
            log.debug("points moved oldest first fromCustomerId={} toCustomerId={} points={} "
                            + "available={} batchesWithSomethingLeft={} slices={} drawnOn={}",
                    fromCustomerId, toCustomerId, points, available, oldestFirst.size(), moved.size(),
                    String.join(" ", drawnOn));
        }
        // Under the same words a deposit's own credit and a loyalty bonus are logged under, because
        // it is the same event: points arriving in somebody's pot. A reviewer greps "points credited"
        // and sees every way this ledger has ever grown, with the reason saying which of them this
        // was. The keys after it differ, as they do between those two — each says what decided its
        // own credit, and what decided this one is the batches it was sliced out of. One line for the
        // move rather than one per slice, because the arrival is one event and the slices are already
        // in the DEBUG line above; the oldest slice's earned-at is on it because the inherited dating
        // is the whole of this move's rule and a reader at INFO would otherwise not see it at all.
        log.info("points credited customerId={} sourceReferenceId={} reason={} points={} batches={} "
                        + "oldestEarnedAt={}",
                toCustomerId, sourceReferenceId, PointsReason.GIFT_RECEIVED, points, moved.size(),
                moved.get(0).earnedAt());
        return Optional.of(List.copyOf(moved));
    }

    /**
     * Every day some of what the given deposits earned is due to go, and how many points go on each
     * — ordered by day, and empty when those deposits have nothing left between them.
     *
     * <p>The schedule rather than the next line of it. {@link #whatExpiresNextFor} answers the one
     * day a customer deciding whether to claim something today needs; this answers every day there
     * is one, which is what somebody looking at a whole pot's year is asking. Both read the same
     * surviving batches, so the soonest day here can never be sooner than the customer's own next
     * day while naming points they do not have.
     *
     * <p>By deposit, which is what makes the answer an account's rather than a customer's: the
     * caller holds the deposits made into one savings account and gets back what the points those
     * deposits earned have coming. Points a customer was given are earned by no deposit of theirs
     * and are in neither this answer nor any account's — see the reasons named above, which is where
     * that is decided once for every lookup by deposit.
     *
     * <p>Grouped by the day rather than reported one lot at a time, for the reason
     * {@link PointsExpiringOnADay} gives: batches earned at four moments of one afternoon reach
     * their twelve months at four moments of one day, and every point going on that day is one
     * figure to whoever is reading it. Which is also what keeps this from being a way to count the
     * ledger's batches through the API.
     *
     * <p>A day already gone stays in the answer on the day it was promised on. The sweep runs at
     * three the following morning, so between an anniversary falling and the sweep acting on it
     * these points are still here and still spendable — and a date in the past is the honest way to
     * say they are going tonight, exactly as a fallen anniversary is reported on the loyalty side.
     *
     * <p>Asked for many deposits at once because callers hold them that way, and a question per
     * deposit would be a query per deposit.
     */
    @Transactional(readOnly = true)
    public List<PointsExpiringOnADay> whenThePointsEarnedByDepositsGo(Collection<Long> depositIds) {
        if (depositIds.isEmpty()) {
            // Asked about nothing, which is what an account nobody has ever paid into looks like.
            // Answered without a query, because "in ()" is not a question worth putting to a
            // database and some of them refuse to be asked it.
            log.debug("nothing earned by no deposits to expire depositsAsked=0");
            return List.of();
        }
        List<PointsCredit> surviving = credits.survivingBatchesEarnedBy(EARNED_BY_A_DEPOSIT, depositIds);
        Map<LocalDate, Long> byDay = new TreeMap<>();
        for (PointsCredit batch : surviving) {
            byDay.merge(theDayItExpires(batch), batch.getRemainingPoints(), Long::sum);
        }
        List<PointsExpiringOnADay> schedule = byDay.entrySet().stream()
                .map(day -> new PointsExpiringOnADay(day.getKey(), day.getValue()))
                .toList();
        // How many deposits were asked about, how many batches were behind the answer and how many
        // days they came to, so that a schedule showing three days can be checked against the lots
        // that made it. Counts rather than the days themselves: this runs on every read of a page,
        // and a line per day would bury the business events in a page load.
        log.debug("when the points earned by deposits go depositsAsked={} batchesSurviving={} days={} points={}",
                depositIds.size(), surviving.size(), schedule.size(),
                schedule.stream().mapToLong(PointsExpiringOnADay::points).sum());
        return schedule;
    }

    /**
     * Every day some of this customer's points are due to go, and how many go on each — ordered by
     * day, and empty for somebody with nothing left to lose.
     *
     * <p>The whole pot's schedule, which is the third and widest of the three questions this module
     * answers about its own rule. {@link #whatExpiresNextFor} gives the one day somebody deciding
     * whether to claim something today needs; {@link #whenThePointsEarnedByDepositsGo} gives one
     * account's, by naming the deposits that earned it; this gives the person's, which is what the
     * points actually are. All three read the same surviving batches, so no two of them can come to
     * disagree about what is still there.
     *
     * <p><strong>Added for a fold over the year ahead, and cut to the narrowest fact that answers
     * it.</strong> What a simulation needs in order to walk twelve months is how many points leave
     * on which day; what it must never be handed is the lots and the moments they were earned at,
     * because how long a batch lasts is this module's rule and {@link PointsExpiry} is the one place
     * it is written down. A caller holding earned-at moments would be one short step from applying
     * that rule itself, and now that the period really can be repriced there would be two answers to
     * when a point goes. So this hands over the consequence and keeps the rule — the same shape and the same
     * reasoning as the two reads above it, one question wider.
     *
     * <p>Grouped by the day rather than reported one lot at a time, for the reason
     * {@link PointsExpiringOnADay} gives at length: points earned at four moments of one afternoon
     * reach their twelve months at four moments of one day, a customer reads those as one date, and
     * grouping is also what keeps this from being a way to count the ledger's batches from outside.
     *
     * <p>A day already gone stays in the answer on the day it was promised on. The sweep runs at
     * three the following morning, so between an anniversary falling and the sweep acting on it these
     * points are still here and still spendable — and a fold that dropped them would be predicting a
     * year from a balance the customer does not have.
     *
     * <p>Points somebody was given are in it, unlike the by-deposit read above, and that is the
     * difference between the two: a gift was earned by no deposit of theirs, but it is theirs, it is
     * in their balance and it expires on its own twelve months like everything else.
     */
    @Transactional(readOnly = true)
    public List<PointsExpiringOnADay> whenThePointsOfACustomerGo(long customerId) {
        List<PointsCredit> surviving = credits.unspentOldestFirst(customerId);
        Map<LocalDate, Long> byDay = new TreeMap<>();
        for (PointsCredit batch : surviving) {
            byDay.merge(theDayItExpires(batch), batch.getRemainingPoints(), Long::sum);
        }
        List<PointsExpiringOnADay> schedule = byDay.entrySet().stream()
                .map(day -> new PointsExpiringOnADay(day.getKey(), day.getValue()))
                .toList();
        // How many batches were behind the answer and how many days they came to, in the same shape
        // the by-deposit read logs, so that a customer's whole schedule and one account's slice of it
        // read as two lines of one story. Counts rather than the days themselves, for the same
        // reason: this is read on every simulation and a line per day would bury the business event.
        log.debug("when a customer's points go customerId={} batchesSurviving={} days={} points={}",
                customerId, surviving.size(), schedule.size(),
                schedule.stream().mapToLong(PointsExpiringOnADay::points).sum());
        return schedule;
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

    private static long wholeEurosIn(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.FLOOR).longValueExact();
    }

    /**
     * What an amount of money earns in points at a rate, without crediting anything to anybody: the
     * euros floored to whole ones, multiplied by the rate, floored again.
     *
     * <p><strong>Public, and public for one reason — so that a screen promising what a deposit
     * would earn is promising the figure this ledger would actually credit.</strong> The savings
     * products comparison asks what a named amount would be worth in each product over twelve
     * months, and the points half of that answer is this exact double flooring at the rate the
     * product's own multiple composes to. Written out a second time on the comparison's side, it
     * would be right on the day it was written and wrong the first time anybody changed where the
     * rounding falls — and the symptom would be a card promising 63 points for a deposit the ledger
     * credits 62 for, which no test of either side on its own would catch. That is the same
     * argument {@link io.dataroots.savingstreak.loyalty.LoyaltyRate} makes about its own rule being
     * public, and it is made here about the rule beside it.
     *
     * <p><strong>Static, and it credits nothing.</strong> The alternative was a second
     * {@code @Transactional} method on this service, which would have meant a caller wanting an
     * arithmetic answer holding the whole ledger and a transaction it has no use for. There is no
     * state in this sentence: it is three numbers, and {@link #creditPointsFor} reaches the same
     * answer by the same two steps because it needs the whole euros in between in order to split
     * the base accrual from the bonus.
     *
     * <p>It refuses nothing, unlike the method that credits. A rate below one is a mistake in
     * whoever worked one out, and that is worth stopping a write for; a projection that asked for
     * one would get the smaller figure the multiplication actually gives, which is a reading rather
     * than an entry in anybody's pot.
     *
     * @param amountInEuros the money, in euros — EUR 12.50 is twelve whole euros here
     * @param multiplier    the rate it is paid at, which is what the streak and the product
     *                      together come to
     */
    public static long pointsEarnedOn(BigDecimal amountInEuros, BigDecimal multiplier) {
        return pointsOn(wholeEurosIn(amountInEuros), multiplier);
    }
}
