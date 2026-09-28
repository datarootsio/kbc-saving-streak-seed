package io.dataroots.savingstreak.web;

import java.util.List;

import io.dataroots.savingstreak.products.TheNewerTermsOnOffer;

/**
 * What this savings account's product is offering today, what the account is on, and — line by line
 * — what taking the newer terms would change.
 *
 * <p><strong>A reading beside the agreement rather than inside it.</strong>
 * {@link AnAgreementResponse} says what the account <em>is</em> living under, which is a fact about
 * this account and nothing else; this says what it <em>could</em> be living under, which is a fact
 * about the account and the catalogue together and changes the morning somebody publishes a version.
 * Folding the two into one panel would make every reader of an agreement carry a comparison they did
 * not ask for, and would have the version a page prints under "your agreement" sitting one field
 * away from a different version number.
 *
 * <p><strong>The differences arrive as sentences, already worded.</strong> The backend has one
 * function that compares two sets of terms and it is the only place in this application a difference
 * is put into words — the product's version history renders from the same one. A page that took two
 * sets of figures and wrote "0.60% → 0.50%" itself would be the second place, and the second place is
 * always the one that forgets the field somebody added last week. So this page prints the list and
 * composes nothing.
 *
 * <p><strong>{@code newerTermsExist} is its own field rather than a list being non-empty.</strong>
 * A version may be published that moves no figure at all — to reword the line saying what changed,
 * or to put a rate back where it was — and such a version is genuinely newer and genuinely takeable.
 * The button is drawn from the flag and the explanation from the list, which keeps "there is
 * something newer" and "here is what it does" two separate statements, as they are.
 *
 * <p><strong>Nothing here says whether taking them is a good idea, and nothing ever will.</strong>
 * The seeded proof is free savings' second version, which cut the rate from 0.60% to 0.50%: an
 * application that marked newer terms as an improvement would be recommending a worse agreement to
 * every customer it had ever repriced. What the customer gets is both version numbers and the
 * sentences, and the decision.
 */
record TheNewerTermsResponse(long savingsAccountId, String productCode, String productName,
                             int theVersionYouAreOn, int theVersionOnOfferToday,
                             boolean newerTermsExist, List<String> whatWouldChange) {

    /**
     * The module's own reading, turned into the one the screen gets.
     *
     * <p>{@code newerTermsExist} is sent as a field although the module derives it from the two
     * version numbers beside it, for the reason every other derived flag on these responses is sent:
     * a client that worked it out for itself would be a second place the rule "newer means strictly
     * higher" lives, and the day a version dated for next month starts counting differently there
     * would be two answers on one screen.
     */
    static TheNewerTermsResponse of(TheNewerTermsOnOffer newer) {
        return new TheNewerTermsResponse(
                newer.savingsAccountId(),
                newer.productCode(),
                newer.productName(),
                newer.theVersionYouAreOn(),
                newer.theVersionOnOfferToday(),
                newer.newerTermsExist(),
                newer.whatWouldChange());
    }
}
