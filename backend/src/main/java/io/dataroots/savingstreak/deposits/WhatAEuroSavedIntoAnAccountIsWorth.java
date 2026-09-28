package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;

/**
 * The second fact this module needs about the agreement a savings account is living under: what a
 * euro saved into it is worth in points.
 *
 * <p><strong>Declared here and implemented by whoever keeps the agreements</strong>, which is the
 * device {@link TheTermsADepositLandsUnder} already states at length and the one Accounts uses for
 * {@code WhoMayPayIntoAnAccountNobodyHolds}. Deposits records money moving and what it earned; it
 * has no business knowing that products exist, that a fixed term pays a quarter more, or that the
 * figure came out of a published version at all. A {@code DepositsService} that asked the Products
 * module directly would learn all three, and the two services would then want each other at
 * start-up, because Products reads this module's ledger to find out when an account was first paid
 * into.
 *
 * <p><strong>A second interface rather than a second method on the first</strong>, although one
 * class answers both today. The two say different things: one is the <em>address</em> of the
 * agreement, written onto the deposit so that what priced it can be looked up in ten years, and
 * this one is a <em>number the deposit is multiplied by</em> at the moment the money lands. A
 * deposit stamped with version 3 and priced at version 4 would be a lie that no compiler could
 * catch, and keeping the questions apart is what makes it obvious that both answers have to come
 * out of the same agreement — which is why the same class answers them.
 *
 * <p><strong>A plain multiple of one, never a percentage and never basis points.</strong>
 * {@code 1.0000} is a euro worth the point it has always been worth, {@code 1.2500} is a quarter
 * more. The figure arrives ready to multiply: whoever implements this owns the unit it is stored
 * in, and a caller that had to divide by ten thousand would be a caller that had learned how the
 * other module writes rates down. It multiplies with the streak rather than replacing it, which is
 * {@link TheRateADepositIsPaidAt}'s business rather than this interface's.
 */
public interface WhatAEuroSavedIntoAnAccountIsWorth {

    /**
     * What a euro saved into that account is worth in points today, as a multiple of one.
     *
     * <p><strong>Always an answer, and that is the difference from
     * {@link TheTermsADepositLandsUnder#theVersionAnAccountIsOn}.</strong> A version nobody
     * recorded cannot be guessed at, so that one answers with nothing; a rate can be, because there
     * is an honest default and every deposit made before this application had products was in fact
     * paid at it. An account with no agreement on record — a row written by a release older than
     * the catalogue, in the milliseconds before the start-up migration reaches it — is therefore
     * priced at the multiple that changes nothing, and the deposit it prices earns exactly what it
     * would have earned the day before the upgrade. Handing back an empty here would leave the one
     * place a deposit is priced deciding what an absent product pays, which is this module having
     * an opinion about products.
     *
     * @param savingsAccountId the account the money is landing in, which exists
     * @return the multiple a euro saved here earns at, never null and never nought — nought
     *         <em>times</em> is not an offer, it is a deposit that silently earns nothing, and it
     *         is refused where a version is published rather than tolerated here
     */
    BigDecimal theMultipleAEuroEarnsAt(long savingsAccountId);
}
