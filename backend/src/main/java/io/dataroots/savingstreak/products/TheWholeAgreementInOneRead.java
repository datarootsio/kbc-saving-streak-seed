package io.dataroots.savingstreak.products;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * This module's answer to a caller that has to reason about one account's whole agreement at once,
 * given in one read so that no two halves of it can describe different agreements.
 *
 * <p><strong>A door of its own rather than a method on {@link ProductsService}.</strong> That class
 * is the catalogue's face — what is on the shelf, what a version says, what opening and closing an
 * account does — and it holds neither the postings nor a reason to. This question is about one
 * account rather than about the shelf, it needs the interest postings to answer the only part of it
 * a customer cannot see, and it writes nothing at all. {@link NoticesService} and
 * {@link InterestService} are already doors of this module beside {@code ProductsService}, so a
 * third one is the shape this module already has rather than a new idea.
 *
 * <p><strong>Nothing outside this module decides anything from the rows.</strong> What goes out is
 * {@link WhatAnAccountsProductPaysAndAsksFor}, already converted into the units the rules that
 * consume it work in, because the conversion belongs on this side of the boundary — see that
 * record's own documentation for the three spellings a rate has here and what two of them would
 * pay.
 *
 * <p>Read-only, and deliberately without a clock: every date it hands over is a date written on a
 * row, and what day it is now is the caller's question rather than this one's. A fold that walks a
 * year has to judge the same agreement against three hundred and sixty-six different days, and a
 * reading that had already judged it against one of them would be useless to it.
 */
@Service
public class TheWholeAgreementInOneRead {

    private static final Logger log = LoggerFactory.getLogger(TheWholeAgreementInOneRead.class);

    private final AccountAgreementRepository agreements;
    private final ProductTermsRepository terms;
    private final SavingsProductRepository products;

    /**
     * How far the interest sweep has already got with this account, which is the one part of the
     * answer that is a fact about the bookkeeping rather than about the agreement.
     *
     * <p>Here for the reason {@code TheStartingPoint} gives about the saving rules' cursors: a
     * caller predicting what the nightly run will do has to count from where the run has actually
     * got to, and counting off the calendar instead would pay a month twice or never pay one the
     * sweep still owes.
     */
    private final InterestPostingRepository postings;

    TheWholeAgreementInOneRead(AccountAgreementRepository agreements, ProductTermsRepository terms,
                               SavingsProductRepository products,
                               InterestPostingRepository postings) {
        this.agreements = agreements;
        this.terms = terms;
        this.products = products;
        this.postings = postings;
    }

    /**
     * Everything the agreement that savings account is living under decides, in one read.
     *
     * <p>An account with no agreement on record is answered with
     * {@link WhatAnAccountsProductPaysAndAsksFor#whatAnAccountWithNoAgreementIsOn} rather than with
     * an empty, for the reason that record argues: a projection is being drawn and an absence would
     * leave the drawing deciding what an absent product pays.
     *
     * <p>A closed account is answered exactly as an open one is. What it was living under is still
     * what it was living under, and whether a closed account should be projected at all is the
     * caller's question — this module has no opinion about futures.
     */
    @Transactional(readOnly = true)
    public WhatAnAccountsProductPaysAndAsksFor whatTheProductOfAnAccountPaysAndAsksFor(
            long savingsAccountId) {
        Optional<AccountAgreement> onRecord = agreements.findBySavingsAccountId(savingsAccountId);
        if (onRecord.isEmpty()) {
            // Not a refusal and not an error: it is the window before the start-up migration has
            // reached a row written by an older release, and it is said out loud so that a
            // projection that looks flat is not blamed on arithmetic.
            log.debug("a savings account has no agreement to be projected under savingsAccountId={} "
                    + "answeredWith=the terms every account lived under before the catalogue",
                    savingsAccountId);
            return WhatAnAccountsProductPaysAndAsksFor.whatAnAccountWithNoAgreementIsOn(
                    savingsAccountId);
        }
        AccountAgreement agreement = onRecord.get();
        ProductTerms version = theVersionOfRecord(agreement);
        ASetOfTerms published = version.asPublished();
        WhatAnAccountsProductPaysAndAsksFor whole = new WhatAnAccountsProductPaysAndAsksFor(
                savingsAccountId,
                agreement.productCode(),
                kindOf(agreement),
                version.version(),
                agreement.openedOn(),
                agreement.interestCountsFrom(),
                howManyPeriodsHaveBeenJudged(savingsAccountId),
                version.annualRateBasisPoints(),
                version.bonusRateBasisPoints(),
                version.minimumBalanceCents(),
                version.pointsMultiplier(),
                version.anniversaryRatePerWholeEuro(),
                published.noticeDays(),
                published.termMonths(),
                // The maturity is counted from the day the term runs from rather than from the day
                // the account was opened, because a term that has rolled over runs from the day it
                // rolled. TheTermAnAccountIsLockedInto owns that subtraction and is asked for it.
                TheTermAnAccountIsLockedInto.whenTheTermIsUp(agreement.theDayTheTermRunsFrom(),
                        published.termMonths()),
                published.earlyExitPenaltyDays());
        // One line with every figure a year folded out of this will turn on, because a projection
        // that came out wrong is diagnosed by asking first whether it was folded from the right
        // agreement. The two loyalty figures are named in the units they leave in, since the unit
        // is the thing most likely to be wrong about them.
        log.debug("the whole agreement of a savings account was read savingsAccountId={} "
                        + "product={} kind={} version={} openedOn={} interestCountsFrom={} "
                        + "periodsAlreadyJudged={} annualRateBasisPoints={} bonusRateBasisPoints={} "
                        + "minimumBalanceCents={} pointsMultiple={} anniversaryPerWholeEuro={} "
                        + "noticeDays={} termMonths={} maturesOn={} earlyExitPenaltyDays={}",
                whole.savingsAccountId(), whole.productCode(), whole.kind(), whole.version(),
                whole.openedOn(), whole.interestCountsFrom(), whole.periodsAlreadyJudged(),
                whole.annualRateBasisPoints(), whole.bonusRateBasisPoints(),
                whole.minimumBalanceCents(), whole.pointsMultiplier(),
                whole.anniversaryRatePerWholeEuro(), whole.noticeDays(), whole.termMonths(),
                whole.maturesOn(), whole.earlyExitPenaltyDays());
        return whole;
    }

    /**
     * Which of the four shapes the account's product is, read off the catalogue row.
     *
     * <p>Nothing in a projection decides anything from it — the rate, the floor, the notice and the
     * term each say their own piece, which is the reading {@link TheRateAPeriodIsPaidAt} argues at
     * length — and it travels because a log line and a walkthrough both read better for naming the
     * kind than for inferring it from four figures.
     *
     * <p>A product this bank does not sell is a broken row rather than a customer's mistake, and is
     * answered as nothing at all rather than thrown over: the figures beside it came out of a
     * published version and are perfectly good, and refusing to project an account because its
     * catalogue row has gone missing would be this module holding a screen over its own
     * bookkeeping.
     */
    private ProductKind kindOf(AccountAgreement agreement) {
        return products.findByCode(agreement.productCode())
                .map(SavingsProduct::kind)
                .orElse(null);
    }

    /**
     * The highest monthly period this account already has a posting for, and nought when it has
     * none.
     *
     * <p>The highest rather than the count, because a period that began before this bank started
     * paying interest on the account is passed over and never written down — so the postings are
     * contiguous from the first period that earned anything, and the count would be short by every
     * month that was skipped.
     */
    private int howManyPeriodsHaveBeenJudged(long savingsAccountId) {
        List<InterestPosting> paid =
                postings.findBySavingsAccountIdOrderByPeriodOrdinalAsc(savingsAccountId);
        return paid.isEmpty() ? 0 : paid.get(paid.size() - 1).asPaid().periodOrdinal();
    }

    /**
     * The version of the terms this account is actually living under, which is the one its
     * agreement names and never the one on offer today.
     *
     * <p>An agreement naming a version nobody published is a broken row rather than a customer's
     * mistake, so it is a fault rather than a refusal — the same reading every other place in this
     * module that resolves a version takes.
     */
    private ProductTerms theVersionOfRecord(AccountAgreement agreement) {
        return terms.findByProductCodeOrderByVersionAsc(agreement.productCode()).stream()
                .filter(version -> version.version() == agreement.version())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "savings account " + agreement.savingsAccountId() + " is on "
                                + agreement.productCode() + " version " + agreement.version()
                                + ", which has never been published"));
    }
}
