package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One published version of a product's agreement, as the rest of the application reads it.
 *
 * <p><strong>The same figures as the row, in the units everything outside this module
 * speaks.</strong> The columns are integers — basis points and cents — because that is how a rate
 * and an amount are held without either of them acquiring a scale somebody chose by accident. Not
 * one of those integers gets out. The rates arrive here as percentages a page can print, the
 * multiplier as a plain multiple of one, and the floor as a euro amount quoted through the money
 * vocabulary this application already speaks. A caller that wanted to know about basis points would
 * be a caller that had learned how this module stores things.
 *
 * <p><strong>A record rather than the entity.</strong> The entity is a row with an identifier and a
 * lifetime; this is a value that answers "what does this agreement say". Handing the entity out
 * would hand out its identifier, which nothing outside should name, and would leave every reader
 * holding something JPA might still be managing.
 *
 * <p><strong>It carries its product's code, and a version number that counts from one per
 * product.</strong> Both are needed together, because "version 2" is not an address: free savings
 * has one and so does the notice account. A customer's account will name the pair, and a sentence
 * reading "free savings, version 2" is exactly what the pair spells.
 *
 * <p><strong>Zero is the absence of the rule</strong>, uniformly, in the four counts and in the two
 * rates that can be nothing: no notice, no term, no floor, no penalty, no rate, no bonus. That
 * reading is the row's and it is repeated here rather than translated into nulls, because a page
 * drawing four products side by side has to render "—" for six different absences and one rule for
 * all of them is the only version of that a reader can hold in their head.
 *
 * <p>{@link #pointsMultiplier} is the one figure here that is never zero, because it is the one
 * that is not a rate: it is a multiple of one, {@code 1.0000} where the product changes nothing,
 * and nought <em>times</em> is not an offer but a deposit that silently earns nothing.
 * {@link #anniversaryRatePercent} is a rate like the two above it and reads zero the way they do —
 * a product that pays nothing for money staying put, which is a real agreement somebody could
 * publish. Both are seeded on every product at the multiple of one and the tenth every deposit in
 * this application has always been paid.
 *
 * <p>{@link #whatChanged} is the line the customer comparing versions reads, and it is null on a
 * first version, because nothing changed: version one is what the product has always said.
 */
public record ASetOfTerms(

        /** The product this agreement belongs to, by the code that never changes. */
        String productCode,

        /** Which version of that product's terms this is, counting from one. */
        int version,

        /** The day it took effect, which is the day an account opened on it was matched against. */
        LocalDate effectiveFrom,

        /** The headline rate a year, as a percentage — {@code 0.60} is 0.60%. */
        BigDecimal annualRatePercent,

        /**
         * On top of the headline rate, as a percentage, and {@code 0.00} unless the product has a
         * condition to keep. It is only paid for a period in which that condition was kept.
         */
        BigDecimal bonusRatePercent,

        /** Days of warning before money may leave, and zero when none is needed. */
        int noticeDays,

        /** How long the money is locked, in months, and zero when it is not a term account. */
        int termMonths,

        /** The floor to keep for the bonus, in euros, and zero when there is no floor. */
        BigDecimal minimumBalance,

        /** Days of interest given up for breaking a term early, and zero when there is no term. */
        int earlyExitPenaltyDays,

        /**
         * What a euro saved here is worth in points, as a multiple of one — {@code 1.0000} changes
         * nothing and {@code 1.2500} is a quarter more. It multiplies with the streak rather than
         * replacing it.
         */
        BigDecimal pointsMultiplier,

        /**
         * What an anniversary pays on money still sitting here, as a percentage of the euros —
         * {@code 10.00} is the tenth this application has always paid.
         */
        BigDecimal anniversaryRatePercent,

        /** What happens on the day a term is up, and {@code HOLD} on a product with no term. */
        MaturityAction maturityAction,

        /** One line saying what changed and why, and null on a first version. */
        String whatChanged) {
}
