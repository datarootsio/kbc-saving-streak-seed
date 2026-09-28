package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The unit every rate in this module is held in, and the only place one turns into a figure
 * somebody outside reads.
 *
 * <p><strong>Rates are integers in hundredths of a percent, and money is a count of cents.</strong>
 * A rate is the sort of figure that gets multiplied by a balance and divided by twelve, and a
 * {@code double} would make 0.60% a year into a number whose last digits depend on which way it was
 * arrived at. A {@link BigDecimal} column would be right and would still leave every writer of a
 * row free to choose a scale, so two versions of one product could disagree about how many places
 * their rate has while claiming to be the same rate. An {@code int} of basis points has exactly one
 * spelling per rate: 60 is 0.60% and there is no second way to write it.
 *
 * <p><strong>Nothing outside this module ever sees a basis point.</strong> The rest of the
 * application speaks euros as {@link BigDecimal} through {@code AmountOfMoney}, and percentages as
 * figures a page can print. So this class is the boundary: the columns are integers, the records
 * that leave are decimals, and the conversion is written once here rather than once per reader.
 *
 * <p>The two conversions are exact divisions and neither of them rounds. A basis point is a
 * hundredth of a percent, so {@code bp / 100} is a percentage with at most two decimal places, and
 * {@code bp / 10000} is a plain multiplier with at most four. Both are produced by moving the point
 * rather than by dividing, which is what makes "exact" a fact about the arithmetic rather than a
 * hope about the rounding mode.
 */
final class BasisPoints {

    /** A percentage has at most two places, because a basis point is a hundredth of one. */
    private static final int PLACES_IN_A_PERCENTAGE = 2;

    /** A plain multiplier has at most four, because 10 000 basis points is one. */
    private static final int PLACES_IN_A_MULTIPLIER = 4;

    /** What one whole multiple is worth in basis points: 10 000, meaning no change at all. */
    static final int ONE_WHOLE_MULTIPLE = 10_000;

    private BasisPoints() {
    }

    /**
     * The rate as a percentage a page can print — 60 becomes {@code 0.60}, 240 becomes
     * {@code 2.40}.
     *
     * <p>A percentage rather than a fraction, because that is the figure on the poster and the
     * figure a customer weighs one product against another with. Whoever multiplies a balance by it
     * divides by a hundred at the moment they do so, which is a decision belonging to the
     * arithmetic rather than to the catalogue.
     *
     * <p>The scale is fixed at two rather than stripped, so that a rate of exactly one percent reads
     * as {@code 1.00} and not as {@code 1}. A catalogue whose figures change shape depending on
     * their value is one a page has to format defensively.
     */
    static BigDecimal asAPercentage(int basisPoints) {
        return BigDecimal.valueOf(basisPoints, PLACES_IN_A_PERCENTAGE);
    }

    /**
     * The rate as a plain multiple of one — 10 000 becomes {@code 1.0000}, 12 500 becomes
     * {@code 1.2500}.
     *
     * <p>Four places rather than two, because this is the one rate here that is not a percentage of
     * anything: it is the factor a deposit's points are multiplied by, and 10 050 basis points is a
     * half-percent uplift that two places would quietly turn into no uplift at all. Nothing this
     * application seeds needs the third and fourth place; the day somebody publishes a version that
     * does, the figure that leaves here is the figure they typed.
     */
    static BigDecimal asAMultiple(int basisPoints) {
        return BigDecimal.valueOf(basisPoints, PLACES_IN_A_MULTIPLIER);
    }

    /**
     * Why that percentage is not one a version can carry, in words the person who typed it can act
     * on, or nothing at all if it is one.
     *
     * <p><strong>It answers with the reason rather than refusing</strong>, in the shape
     * {@code AmountOfMoney} set for a rule several callers have to share. The refusal that comes of
     * it is {@link ProductRefused} and it is raised in one place, with one log line, by
     * {@link WhatAVersionMaySay} — so this class stays what it has always been: the unit, and the
     * two directions a figure crosses it in.
     *
     * <p><strong>Two objections, and neither of them is about what the bank ought to pay.</strong>
     * Less than nothing is not a rate; and a figure finer than a hundredth of a percent is one this
     * module cannot hold, because {@link #asAPercentage} would hand back something the administrator
     * did not type. Rounding it would be this application quietly repricing a product by a
     * hundredth, which is precisely the silent change the whole feature exists to prevent.
     *
     * <p><strong>There is deliberately no ceiling on what a rate may be, only on what the column can
     * hold.</strong> A bank that capped its own rate in code would be the code deciding what may be
     * offered, which is the thing this ticket takes away from the code and gives to a person. What
     * is refused above {@link #THE_MOST_A_PERCENTAGE_CAN_BE} is not an extravagant offer but a
     * figure an {@code int} of basis points has no room for.
     *
     * @param which the figure, named the way the form names it, so the sentence says which box
     */
    static Optional<String> whyItIsNotAPercentageThatCanBeHeld(String which, BigDecimal percentage) {
        if (percentage.signum() < 0) {
            return Optional.of(which + " cannot be less than nothing, and "
                    + percentage.toPlainString() + " is.");
        }
        if (percentage.stripTrailingZeros().scale() > PLACES_IN_A_PERCENTAGE) {
            return Optional.of(which + " is held to the hundredth of a percent, and "
                    + percentage.toPlainString() + " is finer than that. Write it with at most two "
                    + "decimal places, like 0.50.");
        }
        if (percentage.compareTo(THE_MOST_A_PERCENTAGE_CAN_BE) > 0) {
            return Optional.of(which + " of " + percentage.toPlainString()
                    + "% is more than this application can write down.");
        }
        return Optional.empty();
    }

    /**
     * That percentage as the basis points a row holds — {@code 0.50} becomes 50, {@code 2.40}
     * becomes 240.
     *
     * <p>The exact inverse of {@link #asAPercentage}, by moving the point rather than by
     * multiplying, so that a figure published and read back is the figure that was typed. Only ever
     * called on a percentage {@link #whyItIsNotAPercentageThatCanBeHeld} has already passed, which
     * is what makes {@code intValueExact} a statement rather than a gamble: the trailing zeros are
     * stripped first, because {@code 0.500} and {@code 0.50} are the same rate and only one of them
     * survives the move with a scale of nought.
     */
    static int ofAPercentage(BigDecimal percentage) {
        return percentage.stripTrailingZeros()
                .movePointRight(PLACES_IN_A_PERCENTAGE)
                .intValueExact();
    }

    /**
     * Why that multiple is not one a version can carry, or nothing at all if it is one.
     *
     * <p>The same two objections as a percentage's and one more, which is the difference between a
     * rate and a factor: <strong>a multiple of nought is refused</strong>. Nought percent is a real
     * and perfectly sayable offer — a product that pays no interest — and nought <em>times</em> is
     * not an offer at all, it is a deposit that silently earns no points however much is saved. The
     * other six figures on a version read zero as the absence of a rule; this one has no absence to
     * read, because every deposit is multiplied by it, and {@code 1.0000} is what "changes nothing"
     * spells here.
     *
     * <p>Four places rather than two, for {@link #asAMultiple}'s reason: 10 050 basis points is a
     * half-percent uplift that two places would quietly turn into no uplift at all.
     */
    static Optional<String> whyItIsNotAMultipleThatCanBeHeld(String which, BigDecimal multiple) {
        if (multiple.signum() <= 0) {
            return Optional.of(which + " has to be more than nothing — 1.0000 is the multiple that "
                    + "changes nothing — and " + multiple.toPlainString() + " is not.");
        }
        if (multiple.stripTrailingZeros().scale() > PLACES_IN_A_MULTIPLIER) {
            return Optional.of(which + " is held to four decimal places, and "
                    + multiple.toPlainString() + " is finer than that. Write it like 1.2500.");
        }
        if (multiple.compareTo(THE_MOST_A_MULTIPLE_CAN_BE) > 0) {
            return Optional.of(which + " of " + multiple.toPlainString()
                    + " is more than this application can write down.");
        }
        return Optional.empty();
    }

    /** That multiple as the basis points a row holds — {@code 1.2500} becomes 12 500. */
    static int ofAMultiple(BigDecimal multiple) {
        return multiple.stripTrailingZeros().movePointRight(PLACES_IN_A_MULTIPLIER).intValueExact();
    }

    /**
     * The largest percentage an {@code int} of basis points has room for, which is 21 474 836.47%.
     *
     * <p>Worked out from the column rather than typed beside it, so that it says what it actually
     * is: the edge of the storage, and emphatically not a view about what a savings account ought to
     * pay.
     */
    private static final BigDecimal THE_MOST_A_PERCENTAGE_CAN_BE =
            BigDecimal.valueOf(Integer.MAX_VALUE, PLACES_IN_A_PERCENTAGE);

    /** The same edge read as a multiple, which is 214 748.3647 times. */
    private static final BigDecimal THE_MOST_A_MULTIPLE_CAN_BE =
            BigDecimal.valueOf(Integer.MAX_VALUE, PLACES_IN_A_MULTIPLIER);
}
