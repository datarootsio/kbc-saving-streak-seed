package io.dataroots.savingstreak.products;

/**
 * What a set of terms says happens to an account on the day its term is up.
 *
 * <p><strong>It is written on the terms rather than chosen at maturity, and that is the whole
 * point of it being here.</strong> What happens at the end is part of what was agreed at the
 * beginning: an account settles its maturity by the action in the version it was opened under, so
 * the bank cannot change the ending after the story has started. A column on the account would say
 * the same thing today and would be a field somebody could move; a column on an immutable version
 * row cannot be moved at all.
 *
 * <p><strong>Three values, because three are the honest endings.</strong> A term either becomes
 * another term, becomes ordinary money, or sits still waiting for its holder. A fourth — "pay it
 * into the current account" — was rejected because it moves money on a customer's behalf on a
 * morning they may not be looking, and this application has never done that.
 *
 * <p>Every product this bank seeds except the twelve-month fixed term carries {@link #HOLD},
 * because a product with no term never reaches a maturity and the column has to say something. It
 * is the value that does nothing, which is the right reading of a question that is never asked.
 *
 * <p>Public, because it travels out of this module on {@link ASetOfTerms}: a customer comparing two
 * versions of the same product has to be able to see that the ending changed, and that comparison
 * is drawn outside here. Stored as its name rather than its ordinal, so that inserting a value
 * never reinterprets a row already written.
 */
public enum MaturityAction {

    /**
     * Into another term of the same length, at the terms on offer <em>that day</em> — a new
     * agreement, which is why a roll-over pins the current version rather than carrying the old one
     * forward.
     */
    ROLL_OVER,

    /** The product changes to instant access and the money is free from that morning. */
    MOVE_TO_INSTANT,

    /** It sits where it is, earning what instant access earns, until somebody acts. */
    HOLD
}
