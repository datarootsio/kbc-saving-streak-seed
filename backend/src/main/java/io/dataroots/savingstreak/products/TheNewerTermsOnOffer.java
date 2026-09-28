package io.dataroots.savingstreak.products;

import java.util.List;

/**
 * What one savings account would be taking on if its holder took the terms its product is offering
 * today: the version they are on, the version on offer, and what differs between the two.
 *
 * <p><strong>An offer to read and never a thing that happens.</strong> Nothing in this application
 * adopts a newer version on anybody's behalf, so this reading is the whole of what a newer version
 * does to an existing account until its holder presses something. The spec's line is that newer is
 * not the same as better, and the proof is seeded: free savings' second version <em>cut</em> the
 * rate from 0.60% to 0.50%, so an application that treated this record as a recommendation would be
 * recommending a worse agreement to everybody it had ever repriced.
 *
 * <p><strong>Both version numbers, always, including when they are the same.</strong> The account's
 * own version is already on {@link TheAgreementAnAccountIsOn} and is repeated here on purpose: this
 * reading is a comparison, and a comparison that named only one of the two things being compared
 * would make a page join two answers to say "version 1 of 2". When they are equal there is nothing
 * newer, {@link #whatWouldChange} is empty and {@link #newerTermsExist} is false — which is a
 * complete and true answer rather than an absence, and is why this is a record with an empty list in
 * it rather than a reading that goes missing.
 *
 * <p><strong>The differences are sentences, worded by
 * {@link WhatIsDifferentBetweenTwoSetsOfTerms} and by nothing else.</strong> The same list, produced
 * by the same function, is what a product's version history renders. A screen showing this one is
 * showing what the history would say about the step from the version the account is on to the
 * version on offer, which is exactly the promise being made.
 *
 * <p><strong>An empty list of differences does not mean there is nothing newer.</strong> A version
 * can be published that moves no figure at all — to correct the line saying what changed, or because
 * a rate was put back to what it was — and such a version is genuinely newer and genuinely takeable.
 * The two fields are therefore read separately: {@link #newerTermsExist} decides whether the button
 * is there, and {@link #whatWouldChange} decides what is printed beside it.
 *
 * <p>The product is named as well as coded, so that a page can say "Free savings has newer terms"
 * without going back to the catalogue for the word.
 */
public record TheNewerTermsOnOffer(

        /** The savings account this is about. */
        long savingsAccountId,

        /** The product it is on, by the code that never changes. */
        String productCode,

        /** What that product is called, so a sentence can name it. */
        String productName,

        /** The version the account is living under, which nothing moves but its holder. */
        int theVersionYouAreOn,

        /** The version that product is selling today, which may be the same one. */
        int theVersionOnOfferToday,

        /**
         * One sentence per figure that differs between the two, in the order a customer reads them
         * in, and empty when the two agreements say the same thing.
         */
        List<String> whatWouldChange) {

    /**
     * Whether there is anything newer to take, which is the question the button is drawn from.
     *
     * <p>Strictly newer, rather than merely different. A version on offer that is <em>lower</em>
     * than the account's cannot arise from this application — versions only ever count upwards and
     * an account is only ever put on one that was on offer — and if a hand-edited database produced
     * one, offering to move a customer backwards would be the wrong answer to it.
     */
    public boolean newerTermsExist() {
        return theVersionOnOfferToday > theVersionYouAreOn;
    }
}
