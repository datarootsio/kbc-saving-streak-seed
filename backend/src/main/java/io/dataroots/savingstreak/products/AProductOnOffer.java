package io.dataroots.savingstreak.products;

/**
 * One savings product as the rest of the application sees it, together with the set of terms it is
 * selling today.
 *
 * <p><strong>The product and its current terms in one answer, because the question nobody asks is
 * "what is this product" on its own.</strong> A card on the comparison screen is a name, a shape
 * and a rate; a product without a rate is half a thing, and a caller that had to fetch the terms
 * separately would be a caller that could draw a card with the wrong ones on it. The two stay
 * distinguishable — the terms are a nested {@link ASetOfTerms} rather than flattened into this —
 * because the version history hands out exactly that record on its own, and one shape for "an
 * agreement" is what stops the two readings from drifting into disagreeing about what a set of
 * terms is.
 *
 * <p><strong>"Today" is doing real work in that sentence.</strong> This is what the product is
 * offering now, which is emphatically not what any particular account is living under: free savings
 * has published a second version and every account opened before it carries on under the first.
 * That difference is the entire point of the feature, and it is why this record is named for the
 * <em>offer</em> rather than for the agreement.
 *
 * <p><strong>A closed product still comes back.</strong> {@link #openToNewAccounts} false means
 * nobody may open a new account on it; it does not mean the product has gone away. Customers are
 * still on it, their agreements still read, and hiding it would leave somebody holding an account
 * whose product the application would not admit to having. Whoever draws the list decides what to
 * do with a closed one; this record's job is to say which it is.
 *
 * <p><strong>The kind is an enum and the words are the backend's.</strong> The kind is a closed set
 * that genuinely belongs to the code — four shapes, each of which needs different code to enforce —
 * so a caller switching on it is switching on something that cannot grow behind its back without
 * the compiler saying so. The name and the description are words for a person, so that a page
 * renders the catalogue without knowing anything about what is in it.
 *
 * <p>There is deliberately nothing here about how many accounts are on it, or about any particular
 * customer. This is the same answer for everybody, in the way the rewards catalogue is, and that is
 * what makes a rate something a customer can weigh before they hold anything.
 */
public record AProductOnOffer(

        /** What an account names this product by, and what the API is addressed with. */
        String code,

        /** What the card is headed with. */
        String name,

        /** Which of the four shapes of agreement this is. */
        ProductKind kind,

        /** What the card says underneath: the condition weighed against the rate, in one sentence. */
        String description,

        /** Where it sits in the list somebody chose, lowest first. */
        int sortOrder,

        /** Whether a new savings account may be opened on it. */
        boolean openToNewAccounts,

        /** The set of terms it is selling today, which is not what every account on it is living under. */
        ASetOfTerms currentTerms) {
}
