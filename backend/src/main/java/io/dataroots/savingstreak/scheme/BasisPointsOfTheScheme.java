package io.dataroots.savingstreak.scheme;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The unit every rate the scheme carries is held in, and the only place one turns into a figure
 * somebody outside this module reads.
 *
 * <p><strong>Rates are integers in hundredths of a percent, and money is a count of cents.</strong>
 * The argument is the one the products module already made about the same unit and it is worth
 * restating rather than pointing at, because the figures here are multiplied rather than printed: a
 * run of weeks pays 1,00× rising by 0,10×, and a {@code double} would make the eleventh week's rate
 * a number whose last digits depend on which way it was arrived at. A {@link BigDecimal} column
 * would be right and would still leave every writer of a row free to choose a scale, so two versions
 * of the scheme could disagree about how many places their step has while claiming to be the same
 * step. An {@code int} of basis points has exactly one spelling per rate.
 *
 * <p><strong>This is a second copy of a unit the products module also holds, and the duplication is
 * deliberate.</strong> {@code io.dataroots.savingstreak.products.BasisPoints} is package-private,
 * like every other internal of that module, and the two ways to share it are both worse than
 * writing the two conversions again. Making it public would publish one module's storage decision
 * as another module's vocabulary, so the day the products module decided to hold a rate to five
 * places the scheme's ladder would silently change shape; lifting it into a shared package would
 * create a module that exists only to be depended on, which is the thing this codebase's
 * module-per-subject layout is arranged to avoid. What is shared here is a convention — basis
 * points, four places for a multiple, two for a percentage — and a convention is cheaper to repeat
 * than a class is to couple through. The cost is real and it is small: two one-line conversions,
 * each of which is a statement about where the decimal point goes.
 *
 * <p><strong>Nothing outside this module ever sees a basis point.</strong> The rest of the
 * application speaks euros as {@link BigDecimal} through {@code AmountOfMoney}, multiples as plain
 * factors of one, and percentages as figures a page can print. So this class is the boundary: the
 * columns are integers, the record that leaves is decimals, and the conversion is written once here
 * rather than once per reader.
 *
 * <p>Both conversions are produced by moving the point rather than by dividing, which is what makes
 * "exact" a fact about the arithmetic rather than a hope about a rounding mode.
 *
 * <p>Both directions are here now. The two on the way out were written when the scheme became rows;
 * the two on the way in arrived with the door that publishes a version, which is what this class
 * said would happen — a rule with no caller is a rule nobody can check, and the sentence grew
 * beside the door that needed it.
 *
 * <p><strong>What comes in is judged as a unit and not as a scheme.</strong> Whether a figure is
 * less than nothing, finer than the column can hold or larger than an {@code int} has room for is a
 * question about this unit and is answered here. Whether <em>nought</em> is a thing a particular
 * figure may be is emphatically not: the step of the ladder may be nought and is a flat scheme, the
 * ordinary rate may not and is a deposit that earns nothing, and the running-low share may not be
 * over a hundred. Those are readings of the scheme, they differ figure by figure, and
 * {@link WhatAVersionOfTheSchemeMaySay} makes each of them in its own sentence. The products
 * module folds "not nought" into its own multiple check because it has exactly one multiple; the
 * scheme has three and they do not agree, so the split is drawn one level up.
 *
 * <p>Each of the two refusals answers with the reason rather than throwing, in the shape
 * {@code AmountOfMoney} set for a rule several callers share. The {@link SchemeRefused} it becomes
 * is raised in one place, with one WARN line, by the class that reads the whole form.
 */
final class BasisPointsOfTheScheme {

    /** A percentage has at most two places, because a basis point is a hundredth of one. */
    private static final int PLACES_IN_A_PERCENTAGE = 2;

    /** A plain multiple has at most four, because 10 000 basis points is one. */
    private static final int PLACES_IN_A_MULTIPLE = 4;

    /** What one whole multiple is worth in basis points: 10 000, which is the ordinary rate. */
    static final int ONE_WHOLE_MULTIPLE = 10_000;

    private BasisPointsOfTheScheme() {
    }

    /**
     * The rate as a plain multiple of one — 10 000 becomes {@code 1.0000}, 15 000 becomes
     * {@code 1.5000}, and 1 000 becomes the {@code 0.1000} a further week adds.
     *
     * <p>Four places rather than two, because the step is the figure that makes the difference: a
     * scheme published with a step of 0,0250 is a ladder that climbs a quarter as fast, and two
     * places would quietly turn it into a ladder that does not climb at all.
     *
     * <p>The scale is fixed rather than stripped, so that a rate of exactly one reads as
     * {@code 1.0000} and not as {@code 1}. A scheme whose figures change shape depending on their
     * value is one a page has to format defensively.
     */
    static BigDecimal asAMultiple(int basisPoints) {
        return BigDecimal.valueOf(basisPoints, PLACES_IN_A_MULTIPLE);
    }

    /**
     * The share as a percentage a page can print — 8 000 becomes {@code 80.00}.
     *
     * <p>A percentage rather than the fraction the rule that uses it compares against, because that
     * is the figure an administrator types into a box marked "running low at" and the figure a
     * customer reads in a sentence. Whoever compares a spend against a budget divides by a hundred
     * at the moment they do so, which is a decision belonging to the arithmetic rather than to the
     * published scheme.
     */
    static BigDecimal asAPercentage(int basisPoints) {
        return BigDecimal.valueOf(basisPoints, PLACES_IN_A_PERCENTAGE);
    }

    /**
     * Why that multiple is not one a version of the scheme can carry, in words the person who typed
     * it can act on, or nothing at all if it is one.
     *
     * <p><strong>Three objections, and not one of them is a view about what the bank ought to
     * pay.</strong> Less than nothing is not a multiple — a run of weeks that paid a negative
     * factor would take points off a deposit. Finer than four places is a figure
     * {@link #asAMultiple} could not hand back, so accepting it would mean publishing a ladder
     * nobody typed; it is refused rather than rounded, because rounding a step from 0,10005 to
     * 0,1000 is exactly the silent repricing this feature exists to prevent. Larger than
     * {@link #THE_MOST_A_MULTIPLE_CAN_BE} is the edge of the column and not an extravagant offer.
     *
     * <p><strong>Nought passes here.</strong> The step of the ladder may honestly be nought, and
     * the two figures where it may not — the ordinary rate and the cap — are refused by name one
     * level up, where the reason can be the scheme's rather than the unit's.
     *
     * @param which the figure, named the way the form names it, so the sentence says which box
     */
    static Optional<String> whyItIsNotAMultipleThatCanBeHeld(String which, BigDecimal multiple) {
        if (multiple.signum() < 0) {
            return Optional.of(which + " cannot be less than nothing, and "
                    + multiple.toPlainString() + " is.");
        }
        if (multiple.stripTrailingZeros().scale() > PLACES_IN_A_MULTIPLE) {
            return Optional.of(which + " is held to four decimal places, and "
                    + multiple.toPlainString() + " is finer than that. Write it like 1.2500.");
        }
        if (multiple.compareTo(THE_MOST_A_MULTIPLE_CAN_BE) > 0) {
            return Optional.of(which + " of " + multiple.toPlainString()
                    + " is more than this application can write down.");
        }
        return Optional.empty();
    }

    /**
     * That multiple as the basis points a row holds — {@code 1.5000} becomes 15 000, {@code 0.1000}
     * becomes 1 000.
     *
     * <p>The exact inverse of {@link #asAMultiple}, by moving the point rather than by multiplying,
     * so that a figure published and read back is the figure that was typed. Only ever called on a
     * multiple {@link #whyItIsNotAMultipleThatCanBeHeld} has already passed, which is what makes
     * {@code intValueExact} a statement rather than a gamble: the trailing zeros are stripped
     * first, because {@code 1.50} and {@code 1.5000} are the same rate and only one of them
     * survives the move with a scale of nought.
     */
    static int ofAMultiple(BigDecimal multiple) {
        return multiple.stripTrailingZeros().movePointRight(PLACES_IN_A_MULTIPLE).intValueExact();
    }

    /**
     * Why that percentage is not one a version of the scheme can carry, or nothing at all if it is
     * one.
     *
     * <p>The same three objections as a multiple's, read to the hundredth of a percent rather than
     * to the ten-thousandth of one, because that is what a basis point of a share is. The one
     * percentage the scheme carries is where a budget is running low, and whether four fifths or a
     * hundred and ten percent is a sensible place to draw that line is a reading of the scheme and
     * is made one level up.
     */
    static Optional<String> whyItIsNotAPercentageThatCanBeHeld(String which,
                                                              BigDecimal percentage) {
        if (percentage.signum() < 0) {
            return Optional.of(which + " cannot be less than nothing, and "
                    + percentage.toPlainString() + " is.");
        }
        if (percentage.stripTrailingZeros().scale() > PLACES_IN_A_PERCENTAGE) {
            return Optional.of(which + " is held to the hundredth of a percent, and "
                    + percentage.toPlainString() + " is finer than that. Write it with at most two "
                    + "decimal places, like 80.00.");
        }
        if (percentage.compareTo(THE_MOST_A_PERCENTAGE_CAN_BE) > 0) {
            return Optional.of(which + " of " + percentage.toPlainString()
                    + "% is more than this application can write down.");
        }
        return Optional.empty();
    }

    /** That percentage as the basis points a row holds — {@code 80.00} becomes 8 000. */
    static int ofAPercentage(BigDecimal percentage) {
        return percentage.stripTrailingZeros()
                .movePointRight(PLACES_IN_A_PERCENTAGE)
                .intValueExact();
    }

    /**
     * The largest multiple an {@code int} of basis points has room for, which is 214 748,3647 times.
     *
     * <p>Worked out from the column rather than typed beside it, so that it says what it actually
     * is: the edge of the storage, and emphatically not a view about how generous a scheme may be.
     */
    private static final BigDecimal THE_MOST_A_MULTIPLE_CAN_BE =
            BigDecimal.valueOf(Integer.MAX_VALUE, PLACES_IN_A_MULTIPLE);

    /** The same edge read as a percentage, which is 21 474 836,47%. */
    private static final BigDecimal THE_MOST_A_PERCENTAGE_CAN_BE =
            BigDecimal.valueOf(Integer.MAX_VALUE, PLACES_IN_A_PERCENTAGE);
}
