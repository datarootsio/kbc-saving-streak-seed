package io.dataroots.savingstreak.web;

import io.dataroots.savingstreak.products.AProductOnOffer;

/**
 * One savings product on the shelf: what to call it back, what to show a person, what shape of
 * agreement it is, whether it can still be opened, and the terms it is offering today.
 *
 * <p>The words are the backend's, so a page renders the shelf without knowing anything about what
 * is on it. The code is what an account will name — sent out here and sent back unchanged — so the
 * frontend never spells a product's name out for itself.
 *
 * <p><strong>The terms are nested rather than flattened into this.</strong> Ten more fields on this
 * record would have been fewer lines and would have made "the terms on offer" and "a version in the
 * history" two different shapes for one thing — and the whole feature turns on those two being
 * comparable, because the difference between the version an account is on and the version on offer
 * is what a customer is shown before they decide to move. {@link TermsVersionResponse} is that one
 * shape, and it is the same record in both places.
 *
 * <p><strong>{@code currentTerms} is what the product pays today, and emphatically not what any
 * account on it pays.</strong> Free savings has published a second version at a lower rate, and
 * every account opened before it carries on under the first. Naming this field for the offer rather
 * than for the agreement is the one piece of vocabulary that stops a page from quietly promising
 * somebody the wrong rate.
 *
 * <p><strong>A product closed to new accounts is still sent.</strong> {@code openToNewAccounts}
 * false is a fact about whether the button is drawn, not about whether the card is: customers are
 * still on it, their agreements still read, and leaving it out would hide a product somebody is
 * holding. The page decides what a closed card looks like; this record's job is to say which it is.
 *
 * <p>The kind travels as text rather than as the enum the backend holds, for the reason every other
 * response here sends a state as text: a page switching on {@code "FIXED_TERM"} is switching on
 * what actually goes over the wire, and sharing the backend's enum would let a value be renamed on
 * both sides at once with nothing noticing.
 */
record SavingsProductResponse(String code, String name, String kind, String description,
                              int sortOrder, boolean openToNewAccounts,
                              TermsVersionResponse currentTerms) {

    static SavingsProductResponse of(AProductOnOffer product) {
        return new SavingsProductResponse(
                product.code(),
                product.name(),
                product.kind().name(),
                product.description(),
                product.sortOrder(),
                product.openToNewAccounts(),
                TermsVersionResponse.of(product.currentTerms()));
    }
}
