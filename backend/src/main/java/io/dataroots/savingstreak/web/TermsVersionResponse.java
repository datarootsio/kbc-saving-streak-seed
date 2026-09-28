package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.products.ASetOfTerms;
import io.dataroots.savingstreak.products.AVersionAndWhatItChanged;

/**
 * One published version of a savings product's agreement: the day it took effect, every figure it
 * carries, what happens at the end of it, and the line saying what changed.
 *
 * <p>Sent out in two places and identical in both — nested inside {@link SavingsProductResponse} as
 * the terms a product is offering today, and listed on its own as the history a product has
 * published. One shape for both, because they are the same thing at two moments: a page that
 * rendered a version differently depending on which list it came out of would be two renderers to
 * keep in agreement, and the diff a later slice draws between "the version you are on" and "the
 * version on offer" compares exactly these fields.
 *
 * <p><strong>Every rate is a percentage and the floor is a euro amount.</strong> The backend holds
 * rates as basis points and money as cents, because that is how neither of them acquires a scale
 * somebody chose by accident; not one of those integers gets this far. What arrives here is what a
 * page prints: {@code 0.60} is 0.60% a year, {@code 1.2500} is a quarter more points per euro, and
 * {@code 500.00} is five hundred euros. The frontend does no arithmetic on any of them — it has
 * never priced anything in this application and it does not start here.
 *
 * <p><strong>Zero is the absence of the rule, in six of these fields, and the page renders one rule
 * for all of them.</strong> No bonus to earn, no notice to give, no term to serve, no floor to
 * keep, no price for leaving, no rate at all. A null per absent rule would have been the other way
 * to say it and would have made six fields the page has to check before printing; one reading for
 * six absences is the version a person can hold in their head.
 *
 * <p><strong>{@code whatIsDifferent} is the history's own sentences and is empty everywhere
 * else.</strong> A version listed in a product's history carries what it moved about the version
 * before it, worded by the one function in the backend that words a difference; the same version
 * nested on a product's card carries an empty list, because a card has no predecessor in view and
 * an offer is not a comparison. One shape with an empty list beats two shapes: the page that draws
 * the history and the page that draws the card read the same version record, and a field that is
 * empty in one of them is a fact about the question rather than about the version.
 *
 * <p>The maturity action and the version number are the backend's own words and numbers, sent out
 * as text so that a page switching on {@code "ROLL_OVER"} is switching on what actually goes over
 * the wire. The line saying what changed is null on a first version, because nothing changed —
 * that is what a first version is — and a page draws nothing for it.
 */
record TermsVersionResponse(String productCode, int version, LocalDate effectiveFrom,
                            BigDecimal annualRatePercent, BigDecimal bonusRatePercent,
                            int noticeDays, int termMonths, BigDecimal minimumBalance,
                            int earlyExitPenaltyDays, BigDecimal pointsMultiplier,
                            BigDecimal anniversaryRatePercent, String maturityAction,
                            String whatChanged, List<String> whatIsDifferent) {

    /**
     * One entry of a product's history: the version, and the sentences saying what it moved about
     * the version before it.
     *
     * <p>The backend words those sentences and the page prints them, which is the whole point of
     * them arriving as sentences rather than as a structure of fields that moved. A page that built
     * "The rate goes from 0.60% to 0.50%" out of two numbers would be the second place in this
     * application a difference between two agreements is worded, and the two would disagree the
     * first time somebody added a figure to a set of terms.
     */
    static TermsVersionResponse of(AVersionAndWhatItChanged entry) {
        return of(entry.terms(), entry.whatIsDifferent());
    }

    /**
     * A version with no difference beside it, which is the right answer in the two places one is
     * sent without a predecessor in view: the terms a product is offering today, nested on its
     * card, and the version an administrator has just published.
     *
     * <p>An empty list rather than null, so that a page renders "nothing listed" from a list being
     * empty in both cases rather than from a null it has to know the meaning of.
     */
    static TermsVersionResponse of(ASetOfTerms terms) {
        return of(terms, List.of());
    }

    private static TermsVersionResponse of(ASetOfTerms terms, List<String> whatIsDifferent) {
        return new TermsVersionResponse(
                terms.productCode(),
                terms.version(),
                terms.effectiveFrom(),
                terms.annualRatePercent(),
                terms.bonusRatePercent(),
                terms.noticeDays(),
                terms.termMonths(),
                terms.minimumBalance(),
                terms.earlyExitPenaltyDays(),
                terms.pointsMultiplier(),
                terms.anniversaryRatePercent(),
                terms.maturityAction().name(),
                terms.whatChanged(),
                whatIsDifferent);
    }
}
