package io.dataroots.savingstreak.products;

/**
 * What shape of agreement a savings product is — the one thing about a product that is not a
 * number.
 *
 * <p><strong>Four values, and the test for whether a fifth belongs here is whether it needs code
 * to enforce.</strong> Everything else that separates one product from another — the rate, the
 * bonus, the notice period, the term, the floor, the early-exit price, the two loyalty benefits —
 * is a figure on a {@link ProductTerms} row, so a new offer is a new set of numbers rather than a
 * new branch. These four are not figures: each of them asks a different question of a withdrawal,
 * and no amount of tuning turns one into another.
 *
 * <p><strong>{@link #INSTANT_ACCESS} is the absence of a condition rather than a condition of its
 * own</strong>, and it is a value here all the same. The alternative was a null kind meaning "no
 * rules", which would make every reader of this column write the same null check and would leave
 * the one product this application has always had unable to say what it is. It is also the shape
 * every existing savings account is migrated onto and the shape a broken term falls back to, so it
 * is the value most often read.
 *
 * <p><strong>{@link #MINIMUM_BALANCE} refuses nothing</strong>, which makes it the odd one out: the
 * other three each stand between a customer and their money, and this one withholds a bonus for a
 * period that went under the floor and leaves the money alone. It is a kind rather than simply a
 * floor figure on the terms because what the floor <em>does</em> is code — it is read by the
 * interest posting rather than by the withdrawal — and a product carrying a floor that nothing paid
 * a bonus for would be a rule with no consequence.
 *
 * <p>Public, because it travels out of this module inside {@link AProductOnOffer}: a page drawing
 * the four side by side has to be able to say what each one asks for, and the shape is the one part
 * of that which is not a figure it could simply print. Stored as its name rather than its ordinal,
 * so that inserting a value never reinterprets a row already written.
 */
public enum ProductKind {

    /** Money in and out whenever the holder likes, and the lowest rate, which is its price. */
    INSTANT_ACCESS,

    /** Money leaves only after notice has been given and has run its course. */
    NOTICE,

    /** Money is locked until the term matures, unless the term is broken and paid for. */
    FIXED_TERM,

    /** A bonus rate on top for keeping a floor, and nothing at all refused for dipping under it. */
    MINIMUM_BALANCE
}
