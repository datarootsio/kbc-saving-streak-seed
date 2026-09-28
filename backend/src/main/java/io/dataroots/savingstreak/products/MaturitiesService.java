package io.dataroots.savingstreak.products;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import io.dataroots.savingstreak.products.MaturitySettledRepository.AMaturityAlreadySettled;
import io.dataroots.savingstreak.products.TheTermAnAccountIsLockedInto.ATermInForce;
import io.dataroots.savingstreak.streaks.SavingsWeek;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A term that reaches its day is settled by the terms it was opened under, because what happens at
 * the end is part of what was agreed at the beginning.
 *
 * <p><strong>The ending comes off the version the account is living under, never off the version on
 * the shelf.</strong> That is the decision this whole class exists to keep, and it is the one it
 * would be easiest to get wrong: the bank may have published three versions of the twelve-month
 * fixed term since this account opened, and every one of them may say something different about
 * maturity. The account settles by what it agreed to. {@link MaturityAction} argues why the ending
 * lives on an immutable version row rather than as a column on the account, and this is the class
 * that would have been able to betray it.
 *
 * <p><strong>Three endings, and each of them is a different amount of work.</strong> A rolling term
 * goes straight into another of the same length at the rates on offer <em>that day</em> — an honest
 * new agreement pinned to the current version, not the old one extended — which means the agreement
 * gains a new version and a new day for its term to run from. A term set to move to instant access
 * goes onto free savings at the version being sold on the maturity morning, and the money is free
 * from that morning. A term set to wait changes nothing at all about the agreement: it keeps its
 * product, its version and its passed maturity date, and simply stops being locked.
 *
 * <p><strong>Every maturity an account has passed, rather than the most recent one.</strong>
 * Winding the clock three years forward and running this once settles three maturities of a rolling
 * term, in order, each pinned to the version that was current on its own day — which is what makes a
 * twelve-month rule demonstrable in an afternoon, and is the same arrangement that covers an
 * application switched off for a fortnight. The loop is what makes that true: a roll-over produces a
 * maturity that may itself have passed, and the account is asked again until the day it is waiting
 * for is still ahead of it.
 *
 * <p><strong>Idempotent by construction.</strong> Every maturity settled writes a row naming the
 * account and the ordinal, the pair is unique in the database, and a second run over the same
 * mornings finds every one of them already settled and does nothing. Two of the three endings would
 * be safe without the record — a rolled-over term has a new maturity date and a moved account has no
 * term at all — and the third would not: a term left waiting keeps a maturity date that has passed,
 * and would be settled again every night for the rest of the account's life.
 *
 * <p><strong>It reads no clock.</strong> The caller says what time it is, for the reason
 * {@link InterestService} gives: a sweep run against a wound-forward clock has to judge maturities
 * against the moment the application thinks it is, and a service that read the machine's clock would
 * quietly refuse to be demonstrated.
 *
 * <p><strong>There is no second copy of the maturity calendar here.</strong> When a term is up is
 * {@link TheTermAnAccountIsLockedInto}'s single answer, asked through {@code theTermOn} exactly as
 * the withdrawal gate and the account's own panel ask it. A sweep that worked the date out for
 * itself would disagree with the gate on precisely the dates that are hard, and the disagreement
 * would be money unlocked by one rule and refused by the other on the same morning.
 *
 * <p><strong>It moves no money, and that is deliberate.</strong> A maturity changes what agreement
 * an account is living under; it does not pay anything out, and it emphatically does not move the
 * balance into a current account on a morning nobody is looking. The rate that applies afterwards is
 * the new agreement's, and the interest sweep — which runs half an hour later, at a quarter to four,
 * precisely so that a term which matured overnight is on its new terms before the month is priced —
 * reads it off the same row this one wrote.
 */
@Service
public class MaturitiesService {

    private static final Logger log = LoggerFactory.getLogger(MaturitiesService.class);

    /**
     * How many maturities one account may have settled in a single run before this class decides
     * something is wrong and says so.
     *
     * <p>Six hundred, which is fifty years of monthly terms and more than any clock a trainer will
     * wind. It is not a rule about banking; it is a guard on a loop whose exit depends on a date
     * moving forward, and the one shape of bug that would spin it for ever — a product publishing a
     * version with a term of nought months, so that a roll-over lands on an agreement with no
     * maturity — is already handled by the term reading answering empty. The ceiling exists so that a
     * bug nobody has thought of yet stops with a WARN naming the account instead of pinning a
     * thread at a quarter past three in the morning.
     */
    private static final int A_CEILING_NO_HONEST_ACCOUNT_REACHES = 600;

    private final AccountAgreementRepository agreements;
    /**
     * The one reading of what a term says, asked exactly as the withdrawal gate asks it. It is what
     * keeps the maturity calendar in one place, and it is why this class holds no
     * {@code ProductTermsRepository} of its own: which version an account is living under and what
     * that version says are questions that already have an answer.
     */
    private final TheTermAnAccountIsLockedInto lock;
    /**
     * The module's one writer of an agreement, asked for the two things a maturity can do to one:
     * roll the term over, or move the account onto free savings. Nothing here touches an agreement
     * row directly, so what may and may not move on it goes on being argued in one class.
     */
    private final WhatEachSavingsAccountIsOn onProducts;
    private final MaturitySettledRepository settled;

    MaturitiesService(AccountAgreementRepository agreements, TheTermAnAccountIsLockedInto lock,
                      WhatEachSavingsAccountIsOn onProducts, MaturitySettledRepository settled) {
        this.agreements = agreements;
        this.lock = lock;
        this.onProducts = onProducts;
        this.settled = settled;
    }

    /**
     * Settles every maturity every account has passed and not been settled for, each by the terms
     * that account was opened under.
     *
     * <p>Answers nothing, for the reason the other two sweeps give: its one caller runs on a
     * schedule with nobody waiting on it, and a figure returned to a scheduled method is a figure
     * nothing can read. What the sweep did is in the INFO line at the end of it, and what it did to
     * each account is in an INFO line of its own.
     *
     * <p>Public, unlike the record and the repository, and not because anything outside wants it:
     * {@code @Transactional} is applied by a proxy, and the power this leaks is the power to run the
     * nightly job early — which is idempotent and is exactly what the development jobs endpoint
     * offers anyway.
     *
     * <p>One transaction for the whole night, so that an agreement moved and the row saying it was
     * moved are one event — and so that a sweep interrupted half-way leaves no account rolled into a
     * term the record does not mention.
     */
    @Transactional
    public void settleMaturedTerms(Instant now) {
        LocalDate today = asADay(now);
        List<AccountAgreement> onAProduct = agreements.everyAgreement();
        Set<AMaturity> alreadySettled = whatHasAlreadyBeenSettled(onAProduct);
        Map<Long, Integer> highestOrdinal = theHighestOrdinalEachAccountHas(onAProduct);
        int accounts = 0;
        int maturities = 0;
        for (AccountAgreement agreement : onAProduct) {
            int settledHere = settleEveryMaturityThisAccountHasPassed(
                    agreement.savingsAccountId(), today, now, alreadySettled, highestOrdinal);
            if (settledHere > 0) {
                accounts++;
                maturities += settledHere;
            }
        }
        // One line per sweep with everything that decided it: the moment it judged the maturities
        // against, the day that moment was read as, how many agreements it looked at and how many
        // maturities it settled. An account that came free overnight is explainable from this line
        // plus the one the settlement wrote, and a sweep that read forty accounts and settled none
        // can be told from a sweep that was handed nothing to look at.
        log.info("maturities settled asAt={} today={} accountsConsidered={} accountsSettled={} "
                        + "maturitiesSettled={}",
                now, today, onAProduct.size(), accounts, maturities);
    }

    /**
     * Every maturity this one account has passed and has not been settled for, oldest first.
     *
     * <p>A loop rather than a single settlement, because settling one can produce another: a rolling
     * term wound three years forward matures, rolls, matures again on a day that has also gone, and
     * rolls again. Each pass asks the term reading afresh, so the second maturity is worked out from
     * the agreement the first one wrote rather than from anything remembered here — which is the
     * same bargain the interest sweep makes when it re-reads the ledger for every period.
     *
     * <p>It stops on the first of three things, each said at DEBUG rather than silently: the account
     * is not on a term at all, which is three products in four and every account that has just been
     * moved to instant access; its term has not matured, which is the ordinary answer on the
     * ordinary night; or the maturity it is standing on has already been settled, which is what a
     * term left waiting looks like from the second night onwards.
     */
    private int settleEveryMaturityThisAccountHasPassed(long savingsAccountId, LocalDate today,
                                                        Instant now, Set<AMaturity> alreadySettled,
                                                        Map<Long, Integer> highestOrdinal) {
        int here = 0;
        while (here < A_CEILING_NO_HONEST_ACCOUNT_REACHES) {
            Optional<ATermInForce> onATerm = lock.theTermOn(savingsAccountId);
            if (onATerm.isEmpty()) {
                log.debug("savings account passed over for maturity savingsAccountId={} "
                        + "reason=there is no term to settle", savingsAccountId);
                return here;
            }
            ATermInForce term = onATerm.get();
            LocalDate maturesOn = term.maturesOn();
            if (!TheTermAnAccountIsLockedInto.hasMatured(maturesOn, today)) {
                log.debug("savings account passed over for maturity savingsAccountId={} product={} "
                                + "version={} termRunsFrom={} maturesOn={} on={} "
                                + "reason=its day has not come",
                        savingsAccountId, term.productCode(), term.itsTerms().version(),
                        term.termRunsFrom(), maturesOn, today);
                return here;
            }
            if (!alreadySettled.add(new AMaturity(savingsAccountId, maturesOn))) {
                // Which is every night after the first for a term set to wait: the day has come and
                // gone, the agreement says exactly what it said yesterday, and the only thing that
                // stops this being settled again is the row saying it already was.
                log.debug("maturity passed over savingsAccountId={} product={} maturedOn={} "
                                + "action={} reason=it has already been settled",
                        savingsAccountId, term.productCode(), maturesOn, term.maturityAction());
                return here;
            }
            settle(term, maturesOn, highestOrdinal.merge(savingsAccountId, 1, Integer::sum), today,
                    now);
            here++;
        }
        log.warn("a savings account was left with maturities unsettled savingsAccountId={} "
                        + "settledThisRun={} on={} reason=a single account settled more maturities "
                        + "in one run than any honest account should, so the sweep stopped rather "
                        + "than looping",
                savingsAccountId, here, today);
        return here;
    }

    /**
     * One maturity, done as the terms the account was opened under say to do it, and written down.
     *
     * <p>The agreement is moved first and the row written second, inside the one transaction the
     * sweep opened. The order is not load-bearing — neither is visible without the other — but it
     * reads the way the event happened, and it means the row records what the agreement
     * <em>actually</em> says afterwards rather than what this class intended it to say.
     *
     * <p>{@link MaturityAction#HOLD} writes the row and moves nothing, which is the whole of what
     * holding is. The account keeps its product and its version, its maturity date stays where it
     * is and stays passed, and the money is free because the term has matured rather than because
     * anything moved it. What it earns from that morning is
     * {@link AWaitingTermEarnsTheFreeSavingsRate}'s answer, derived from the same three facts
     * rather than from this row, so that the rate does not depend on the sweep having run.
     */
    private void settle(ATermInForce term, LocalDate maturedOn, int ordinal, LocalDate today,
                        Instant now) {
        long savingsAccountId = term.savingsAccountId();
        MaturityAction action = term.maturityAction();
        String wasOn = term.productCode();
        int wasOnVersion = term.itsTerms().version();
        log.debug("a maturity is being settled savingsAccountId={} maturity={} maturedOn={} on={} "
                        + "product={} version={} termMonths={} termRanFrom={} action={}",
                savingsAccountId, ordinal, maturedOn, today, wasOn, wasOnVersion, term.termMonths(),
                term.termRunsFrom(), action);
        TheAgreementAnAccountIsOn nowOn = switch (action) {
            case ROLL_OVER -> onProducts.rollTheTermOver(savingsAccountId, maturedOn,
                    "a " + term.termMonths() + "-month term matured on " + maturedOn + " and its "
                            + "terms said to roll it into another at the rates on offer that day");
            case MOVE_TO_INSTANT -> onProducts.moveToFreeSavingsAsAt(savingsAccountId, maturedOn,
                    "a " + term.termMonths() + "-month term matured on " + maturedOn + " and its "
                            + "terms said to move the account to instant access from that morning");
            case HOLD -> onProducts.theAgreementOf(savingsAccountId).orElseThrow(
                    () -> new IllegalStateException("savings account " + savingsAccountId
                            + " has no agreement on record, so its maturity cannot be held"));
        };
        settled.save(MaturitySettled.of(savingsAccountId, ordinal, maturedOn, action, wasOn,
                wasOnVersion, nowOn.productCode(), nowOn.version(), now));
        // One INFO line per maturity, carrying what the terms said and what was done, because this
        // is the business event the ticket is about and it happens while nobody is watching. "Why is
        // my account on a different version this morning" and "why is my money locked for another
        // year" are both answered by this line alone.
        log.info("a term matured and was settled savingsAccountId={} maturity={} maturedOn={} "
                        + "on={} termsSaid={} termMonths={} wasOn={} wasOnVersion={} nowOn={} "
                        + "nowOnVersion={} nowMaturesOn={}",
                savingsAccountId, ordinal, maturedOn, today, action, term.termMonths(), wasOn,
                wasOnVersion, nowOn.productCode(), nowOn.version(), nowOn.maturesOn());
    }

    /**
     * Which maturities of which of these accounts have already been settled, as a set that can be
     * asked about one morning at a time.
     *
     * <p>One query for every account rather than one per maturity considered, which is the same
     * arrangement the interest and loyalty sweeps use and for the same reason: the rows a sweep
     * would otherwise read one at a time are exactly the rows it is about to decide against.
     *
     * <p>A mutable set on purpose. The sweep adds to it as it settles, so that the loop over one
     * account's several maturities cannot settle the same morning twice without going back to the
     * database — and so that the skip and the write are the same fact rather than two.
     */
    private Set<AMaturity> whatHasAlreadyBeenSettled(List<AccountAgreement> onAProduct) {
        Set<AMaturity> already = new HashSet<>();
        if (onAProduct.isEmpty()) {
            return already;
        }
        for (AMaturityAlreadySettled row : settled.maturitiesAlreadySettledFor(
                onAProduct.stream().map(AccountAgreement::savingsAccountId).toList())) {
            already.add(new AMaturity(row.getSavingsAccountId(), row.getMaturedOn()));
        }
        log.debug("maturities already settled for these accounts accounts={} "
                + "maturitiesAlreadySettled={}", onAProduct.size(), already.size());
        return already;
    }

    /**
     * The highest maturity ordinal each of these accounts has on record, so that the next one is one
     * higher.
     *
     * <p>Allocated in sequence rather than derived from the calendar, and {@link MaturitySettled}
     * argues why: the months between the day an account opened and the day its third term is up are
     * not a whole number of terms once {@code plusMonths} has clamped a February, so an ordinal
     * computed from the dates would come out one short exactly where it mattered and collide with a
     * row already written. Counting is exact, and the skip that decides <em>whether</em> to settle
     * is keyed on the day rather than on this number.
     */
    private Map<Long, Integer> theHighestOrdinalEachAccountHas(List<AccountAgreement> onAProduct) {
        Map<Long, Integer> highest = new HashMap<>();
        if (onAProduct.isEmpty()) {
            return highest;
        }
        for (AMaturityAlreadySettled row : settled.maturitiesAlreadySettledFor(
                onAProduct.stream().map(AccountAgreement::savingsAccountId).toList())) {
            highest.merge(row.getSavingsAccountId(), row.getMaturityOrdinal(), Math::max);
        }
        return highest;
    }

    /**
     * A moment read as the day it fell on, in the zone this application counts its days in —
     * borrowed from {@link SavingsWeek} for the reason {@link ProductsService} gives about the same
     * line: a second copy of the zone is a copy that can be changed on its own.
     */
    private static LocalDate asADay(Instant moment) {
        return LocalDate.ofInstant(moment, SavingsWeek.ZONE_WEEKS_ARE_COUNTED_IN);
    }

    /**
     * One maturity of one account, by the account and the day it fell on.
     *
     * <p>The day rather than the ordinal, and that is the choice worth saying out loud. The database
     * keeps the record unique over the account and the ordinal, because an ordinal is what makes
     * "three maturities, in order" readable off the rows; but the question the sweep is standing
     * there asking is "has the maturity I can see already been dealt with", and what it can see is a
     * date derived from the agreement. Matching on the date is the only version of that question
     * that does not require the sweep to already know which number it is looking at.
     */
    private record AMaturity(long savingsAccountId, LocalDate maturedOn) {
    }
}
