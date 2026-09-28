package io.dataroots.savingstreak.rewards;

/**
 * Whether an offer is one thing or several handed over together.
 *
 * <p>Two values and no more. A bundle is not a different sort of row — it is a row with member
 * lines hanging off it and a price of its own — so what separates the two is one column, read
 * where a claim decides whose stock to draw down and what the voucher says it contains.
 *
 * <p><strong>Something reads it now.</strong> The paragraph that used to stand here said nothing
 * did, and that the column was written early because a schema that stops moving is worth more to
 * the tickets that follow than a column withheld until somebody uses it. The slice that composes
 * a bundle is the one that collected: {@code BUNDLE} is what decides that a claim has members'
 * stock to check and draw down, and that a reading has contents to name.
 *
 * <p>It is never chosen on a form. An offer written with member lines is a bundle and an offer
 * written without them is an item, so this column is decided by what was composed rather than
 * asked for beside it — the argument is on {@code ANewOffer}, and it is the only thing that
 * keeps the column and the lines from being able to disagree.
 *
 * <p>{@code FAMILY_CINEMA_PACK} is conspicuously a bundle in everything but structure and is
 * nevertheless seeded as an {@code ITEM}, exactly as it has always been, because the one promise
 * this slice makes is that nothing a customer already knew moves.
 */
enum OfferKind {

    /** One thing, with its own stock and its own voucher. */
    ITEM,

    /** Several offers handed over together for one price and one voucher. */
    BUNDLE
}
