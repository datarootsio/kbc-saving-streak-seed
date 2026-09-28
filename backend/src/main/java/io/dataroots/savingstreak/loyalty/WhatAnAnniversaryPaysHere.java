package io.dataroots.savingstreak.loyalty;

import java.math.BigDecimal;

/**
 * The one fact this module needs about the agreement a savings account is living under: what an
 * anniversary pays there, per whole euro.
 *
 * <p><strong>Declared here and implemented by whoever keeps the agreements.</strong> It is the
 * device this codebase already uses three times — Accounts states the question a shared pot
 * answers, the Rewards sweep states the question the customer standings answer, and Deposits states
 * which version its money is landing under. Loyalty knows when a deposit's anniversary falls, that
 * it recurs, and that what has been paid is never paid twice; it has no business knowing that
 * products exist, that a notice account pays twelve percent, or that an administrator repriced one
 * of them last Tuesday. A {@code LoyaltyService} that asked the Products module directly would
 * learn all three, and the two services would then want each other at start-up, because Products
 * reads the ledger Loyalty reads.
 *
 * <p><strong>Per whole euro, and as a fraction of a point.</strong> {@code 0.1000} is the tenth
 * every anniversary in this application has always paid; {@code 0.1200} is twelve percent. It is
 * the number {@link LoyaltyRate#pointsOn} multiplies whole euros by, so it arrives in the unit that
 * rule works in and in no other. That is not a preference: the same rate has three honest spellings
 * in the module that stores it — basis points, a percentage, a fraction — and two of them, handed
 * over here, would pay ten thousand and a hundred times too much without anything throwing. The
 * conversion belongs to the module that owns the unit, on its side of this interface, where the
 * number still has its unit attached.
 *
 * <p><strong>Asked when an anniversary is priced, not when the money landed.</strong> An
 * anniversary pays at the rate of the product the money is <em>sitting in</em>, the same way it
 * pays on the euros still sitting there rather than on the euros that arrived. A deposit is stamped
 * with the version it landed under because what priced it then must not move; an anniversary is a
 * thing that happens this year, under the agreement the account is living under this year, and it
 * is asked for afresh every time one is judged.
 */
public interface WhatAnAnniversaryPaysHere {

    /**
     * What one whole euro still sitting in that savings account earns on an anniversary.
     *
     * <p><strong>Always an answer.</strong> An account with no agreement on record — a row written
     * by a release older than the catalogue, in the milliseconds before the start-up migration
     * reaches it — pays what every anniversary in this application paid before there were products
     * to vary it. Handing back an empty would leave the sweep deciding what an absent product pays,
     * which is this module having an opinion about products.
     *
     * @param savingsAccountId the account the money is sitting in, which exists
     * @return the fraction of a point each whole euro earns, never null and never less than
     *         nothing, and possibly nought — a product that pays nothing for money staying put is a
     *         real agreement, and {@link LoyaltyRate} says what that means for a bonus and for the
     *         threshold under it
     */
    BigDecimal perWholeEuroIn(long savingsAccountId);
}
