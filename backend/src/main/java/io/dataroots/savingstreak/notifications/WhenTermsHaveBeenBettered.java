package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;

import io.dataroots.savingstreak.products.ASetOfTerms;

/**
 * Whether one published version of a product's agreement is <em>better</em> than another — the one
 * opinion this feature holds, written down on its own so that it can be read and argued with.
 *
 * <p><strong>Why it is here and not in Products.</strong> That module already knows how to compare
 * two agreements: {@code WhatIsDifferentBetweenTwoSetsOfTerms} words every figure that moved, and it
 * is the only place in this application a difference between two agreements is put into words. It
 * has no opinion, on purpose. It says the rate goes from 0.60% a year to 0.50% a year and never
 * says whether that is an improvement, because the promise the whole savings-products feature rests
 * on is that nothing adopts newer terms on a holder's behalf — and a comparison that had learned to
 * grade itself is exactly how that promise quietly becomes "we decided this one was an improvement
 * and told you so". Free savings' seeded version 2 <em>cut</em> the rate, so this is not a
 * hypothetical: an opinionated comparison would have been recommending a worse agreement to every
 * account the bank had ever repriced.
 *
 * <p>So the comparison stays mute and the judgement lives here, in the module that needs it, where
 * a reviewer can find it by name and change it without touching anything a customer's version
 * history is drawn from. The sentences the notification carries are still that function's, quoted
 * word for word through {@code ProductsService.theNewerTermsFor}; this class reads only figures and
 * writes no words at all.
 *
 * <p><strong>What "bettered" means: the headline annual rate went up. That is the whole of
 * it.</strong> One figure, strictly greater, nothing added and nothing weighed.
 *
 * <p><strong>The alternative that was rejected, and it is the obvious one:</strong> score the
 * agreement. Give a point for the rate rising, a point for the bonus rising, a point for the points
 * multiplier going up, a point for the notice period getting shorter or the term getting shorter or
 * the early-exit penalty getting smaller, and call it bettered when the points come out positive.
 * It was rejected for three reasons that pull the same way.
 *
 * <p>The first is that most of those figures are not improvements, they are <em>trades</em>. Notice
 * getting shorter buys liquidity and is normally paid for out of the rate; a longer term is worse
 * for somebody who wants their money and is precisely what somebody else opened a term in order to
 * be held to. A rule that called a shorter notice better would be this application deciding what
 * the customer's own plans are. The second is that a score is an opinion with the argument hidden
 * inside an arithmetic: nobody reading "bettered" off a weighted sum can say what it was weighted
 * by, which is the failure this class was split out to avoid rather than to reproduce at one remove.
 * The third is that the criterion this was built from says it in one line — a version that improves
 * on the headline rate of the version an account holds — and inventing more than was asked for is
 * how a notification ends up saying something nobody agreed to.
 *
 * <p><strong>Which means a version can be "bettered" and still be worse for the customer</strong>,
 * and that is the honest consequence rather than a hole. A version that lifts the rate and adds
 * thirty days' notice passes this test. What the customer is sent is an invitation to read: the
 * notification quotes every sentence the comparison produced, including the ones about the figures
 * that moved against them, and nothing whatever is adopted on their behalf. The judgement decides
 * whether it is worth interrupting somebody; the sentences decide what they are shown; the press is
 * theirs. A rule that tried to be sure it was better for this customer would have to know what they
 * are saving for, and would go silent on the case this feature exists for — a plain rate rise.
 *
 * <p><strong>Strictly greater, so an equal rate is not a betterment.</strong> A version republished
 * at the same rate — to reword the explanation, or to put a rate back where it was — is a genuinely
 * newer and genuinely takeable version, and the button offering it is drawn from
 * {@code TheNewerTermsOnOffer.newerTermsExist}, which is a different question and stays where it is.
 * It is simply not news.
 *
 * <p>A class of static methods with no state, like the two windows beside it, so that the rule can
 * be read on its own and cannot pick up a dependency on anything that knows an account.
 */
final class WhenTermsHaveBeenBettered {

    private WhenTermsHaveBeenBettered() {
    }

    /**
     * Whether the version on offer betters the version an account is living under.
     *
     * <p>Both readings come from Products and neither is recomputed here: a headline rate is stored
     * in basis points and handed out as a percentage, and re-deriving one from the other in this
     * module would be a second unit conversion free to disagree with the one the product card
     * prints.
     *
     * <p>Compared with {@link BigDecimal#compareTo} rather than {@code equals}, because {@code 0.50}
     * and {@code 0.5} are the same rate and different objects, and a scale is not part of what a
     * rate means.
     */
    static boolean isBettered(ASetOfTerms whatYouAreOn, ASetOfTerms whatIsOnOffer) {
        return theHeadlineRateOf(whatIsOnOffer).compareTo(theHeadlineRateOf(whatYouAreOn)) > 0;
    }

    /**
     * The one figure the judgement is taken on, named rather than reached for twice, so that the
     * sweep's log line and the rule itself cannot end up quoting two different numbers.
     */
    static BigDecimal theHeadlineRateOf(ASetOfTerms terms) {
        return terms.annualRatePercent();
    }
}
