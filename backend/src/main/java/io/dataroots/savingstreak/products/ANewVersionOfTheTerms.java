package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What somebody running the bank sends to publish the next version of a product's agreement: the
 * day it takes effect, every figure it carries, what happens at the end of it, and the line saying
 * what changed.
 *
 * <p><strong>There is no version number on it.</strong> Which version this becomes is the
 * catalogue's answer — one higher than the last that product published — and a number sent from
 * outside would be a number two administrators could send at once. The same argument the code makes
 * for not being sendable here: the product is named in the path, because a version belongs to a
 * product and cannot be moved to another one.
 *
 * <p><strong>Every figure is required, and nothing is carried forward from the version
 * before.</strong> That is the decision this record is really about, and it was the close one. The
 * alternative — absent means "same as last time" — would make a version a patch on its predecessor,
 * so what a published agreement said would depend on reading the whole chain behind it, and a field
 * nobody filled in would be an invisible copy. A version is the complete list of numbers; that is
 * the argument {@code ProductTerms.published} already makes about its thirteen parameters, and it
 * does not stop at the module boundary. The screen fills the form in from the version on offer, so
 * an administrator still changes one figure and presses once — but what goes up is the whole
 * agreement, visibly, and what is written down is a complete statement of it.
 *
 * <p><strong>Boxed numbers and nullable amounts, so that "nobody filled it in" is a sentence rather
 * than a nought.</strong> Zero is the absence of the rule in six of these figures — no notice, no
 * term, no floor, no penalty, no bonus, no interest at all — which means an empty box read as zero
 * would quietly publish a rate cut to nothing on a product people are saving into. The module
 * refuses the absence by name instead. The same reading the rewards catalogue makes of an offer's
 * stock, and for a harder-edged version of the same reason.
 *
 * <p><strong>The rates arrive as percentages and the floor as euros</strong>, which is what the
 * rest of this application speaks and what the catalogue hands out — so a figure read off a screen
 * and a figure published back are the same figure. Not one basis point crosses this boundary, in
 * either direction; {@link BasisPoints} is where they become integers, and it refuses a figure it
 * cannot hold rather than rounding one.
 *
 * <p><strong>The ending arrives as text rather than as {@link MaturityAction}.</strong> Which
 * endings this bank offers is this module's vocabulary, and a caller that had to name the enum
 * would be a caller that could not report "MOVE_TO_CURRENT is not one of them" in the module's own
 * sentence — it would have failed to deserialise instead, somewhere with no words in it.
 */
public record ANewVersionOfTheTerms(

        /** The day it takes effect, which may be in the past, today, or ahead of today. */
        LocalDate effectiveFrom,

        /** The headline rate a year, as a percentage — {@code 0.50} is 0.50%. */
        BigDecimal annualRatePercent,

        /** On top of the headline rate, as a percentage, and {@code 0.00} for no bonus. */
        BigDecimal bonusRatePercent,

        /** Days of warning before money may leave, and zero when none is needed. */
        Integer noticeDays,

        /** How long the money is locked, in months, and zero when it is not a term account. */
        Integer termMonths,

        /** The floor to keep for the bonus, in euros, and {@code 0.00} when there is no floor. */
        BigDecimal minimumBalance,

        /** Days of interest given up for breaking a term early, and zero when there is no term. */
        Integer earlyExitPenaltyDays,

        /** What a euro saved here is worth in points, as a multiple of one — {@code 1.0000}. */
        BigDecimal pointsMultiplier,

        /** What an anniversary pays on money left sitting here, as a percentage of the euros. */
        BigDecimal anniversaryRatePercent,

        /** What happens on the day a term is up, by name — {@code HOLD} on a product with no term. */
        String maturityAction,

        /** One line saying what changed and why, and required, because somebody will read it. */
        String whatChanged) {
}
