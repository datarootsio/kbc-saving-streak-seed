package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import io.dataroots.savingstreak.deposits.AmountOfMoney;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads what somebody typed into the figures a version row is made of, and refuses in this module's
 * own words whatever cannot be one.
 *
 * <p><strong>A class of its own rather than a method on the service</strong>, for
 * {@link TheTermsOnOfferToday}'s reason: it is a pure reading of one record, it is the only place
 * the rules about what a version may say are written down, and those rules are exactly the sort of
 * thing that gets re-decided slightly differently the second time somebody needs them. There is
 * nothing to construct and nothing to inject.
 *
 * <p><strong>It refuses and logs here rather than answering with reasons.</strong>
 * {@link BasisPoints} and {@code AmountOfMoney} both hand back a sentence instead of throwing,
 * because each of them is shared by callers who refuse differently; this class has one caller and
 * one refusal, so the sentence and the {@link ProductRefused} it becomes are one step. That also
 * puts the WARN line exactly where the decision is made, with the figure that decided it, rather
 * than at a service that would have to restate what it was told.
 *
 * <p><strong>Every figure is judged, and the first objection wins.</strong> A form with three
 * mistakes in it comes back naming one of them, which is what every other refusal in this
 * application does. Collecting all three would read better on a screen and would be the only
 * refusal here shaped like a list, and a screen that showed one sentence for eight boxes and a list
 * for one of them is a screen nobody can write a renderer for.
 *
 * <p><strong>Nothing is defaulted, nothing is rounded and nothing is carried over.</strong> An
 * absent figure is refused by name; a rate quoted more finely than the column holds is refused
 * rather than rounded to the nearest hundredth. Rounding would move a rate nobody typed, on an
 * agreement people are about to be living under, which is the failure this whole feature exists to
 * prevent — {@code AmountOfMoney} makes the same argument about a deposit somebody typed three
 * decimal places into, and this is the same argument about a bigger number of people.
 */
final class WhatAVersionMaySay {

    private static final Logger log = LoggerFactory.getLogger(WhatAVersionMaySay.class);

    /**
     * The names the refusals use, which are the words on the form rather than the names of the
     * columns.
     *
     * <p>Written as sentence openings — "The annual rate cannot be less than nothing" — so that one
     * phrase serves every objection a figure can draw, instead of a set of phrasings per figure
     * that drift into sounding like four different applications.
     */
    private static final String THE_ANNUAL_RATE = "The annual rate";
    private static final String THE_BONUS_RATE = "The bonus rate";
    private static final String THE_NOTICE_PERIOD = "The notice period, in days,";
    private static final String THE_TERM = "The term, in months,";
    private static final String THE_FLOOR = "The minimum balance";
    private static final String THE_EARLY_EXIT_PENALTY = "The early-exit penalty, in days,";
    private static final String THE_POINTS_MULTIPLIER = "The points multiplier";
    private static final String THE_ANNIVERSARY_RATE = "The anniversary rate";

    private WhatAVersionMaySay() {
    }

    /**
     * That product's next version, built out of what was typed, or a refusal saying what cannot be
     * read as one.
     *
     * <p>The product's code and the version number are this module's own — the caller found the
     * product and counted the versions — so neither of them can be wrong here and neither is
     * judged. Everything else came off a form.
     *
     * @param productCode the product the version belongs to, already known to exist
     * @param version     one higher than the last that product published, counted by the caller
     * @param typed       what somebody running the bank filled in
     * @throws ProductRefused when any part of it is not something a version can say
     */
    static ProductTerms readInto(String productCode, int version, ANewVersionOfTheTerms typed) {
        log.debug("a new version of a savings product's terms is being read product={} version={} "
                        + "effectiveFrom={} annualRatePercent={} bonusRatePercent={} noticeDays={} "
                        + "termMonths={} minimumBalance={} earlyExitPenaltyDays={} "
                        + "pointsMultiplier={} anniversaryRatePercent={} maturityAction={}",
                productCode, version, typed.effectiveFrom(), typed.annualRatePercent(),
                typed.bonusRatePercent(), typed.noticeDays(), typed.termMonths(),
                typed.minimumBalance(), typed.earlyExitPenaltyDays(), typed.pointsMultiplier(),
                typed.anniversaryRatePercent(), typed.maturityAction());

        if (typed.effectiveFrom() == null) {
            throw refuse(productCode, ProductRefused.Kind.A_VERSION_WITH_NO_DAY_IT_TAKES_EFFECT,
                    "A new version needs the day it takes effect. Write it as a date, like "
                            + "2026-12-31 — it may be in the past, today, or a day still to come.");
        }
        if (typed.whatChanged() == null || typed.whatChanged().isBlank()) {
            throw refuse(productCode,
                    ProductRefused.Kind.A_VERSION_THAT_DOES_NOT_SAY_WHAT_CHANGED,
                    "A new version has to say what changed and why, in one line, because that is "
                            + "what a customer comparing it with the version they are on will "
                            + "read.");
        }

        return ProductTerms.published(
                productCode,
                version,
                typed.effectiveFrom(),
                aPercentage(productCode, THE_ANNUAL_RATE, typed.annualRatePercent()),
                aPercentage(productCode, THE_BONUS_RATE, typed.bonusRatePercent()),
                aCount(productCode, THE_NOTICE_PERIOD, typed.noticeDays()),
                aCount(productCode, THE_TERM, typed.termMonths()),
                anAmountInCents(productCode, THE_FLOOR, typed.minimumBalance()),
                aCount(productCode, THE_EARLY_EXIT_PENALTY, typed.earlyExitPenaltyDays()),
                aMultiple(productCode, THE_POINTS_MULTIPLIER, typed.pointsMultiplier()),
                aPercentage(productCode, THE_ANNIVERSARY_RATE, typed.anniversaryRatePercent()),
                anEnding(productCode, typed.maturityAction()),
                typed.whatChanged().trim());
    }

    /**
     * A rate as the basis points a row holds, or a refusal naming the box it came out of.
     *
     * <p>The two rates that pass through here are the headline one and the anniversary, and both
     * may be nought: a product that pays no interest and a product with no anniversary bonus are
     * both agreements somebody could honestly publish, and nought reads there exactly as it reads
     * in the other five figures — the rule is not there. The multiplier is the one that cannot be
     * nought, and it goes through {@link #aMultiple} instead.
     */
    private static int aPercentage(String productCode, String which, BigDecimal percentage) {
        return theFigure(productCode, which, percentage,
                () -> BasisPoints.whyItIsNotAPercentageThatCanBeHeld(which, percentage),
                () -> BasisPoints.ofAPercentage(percentage));
    }

    /** The points multiplier as basis points of a whole multiple, or a refusal about it. */
    private static int aMultiple(String productCode, String which, BigDecimal multiple) {
        return theFigure(productCode, which, multiple,
                () -> BasisPoints.whyItIsNotAMultipleThatCanBeHeld(which, multiple),
                () -> BasisPoints.ofAMultiple(multiple));
    }

    /**
     * A count of days or months, which is a whole thing and cannot be less than nothing.
     *
     * <p>Nought is the absence of the rule in all three of them — no notice to give, no term to
     * serve, nothing to pay for leaving — so nought is accepted and only an absent or a negative
     * one is refused. There is no ceiling: a notice period of a thousand days is a ridiculous
     * product and not a broken one, and the day this application starts having views about that is
     * the day it takes the decision back off the person this ticket gave it to.
     */
    private static int aCount(String productCode, String which, Integer count) {
        if (count == null) {
            throw nothingWasSent(productCode, which);
        }
        if (count < 0) {
            throw refuse(productCode, ProductRefused.Kind.A_FIGURE_THESE_TERMS_CANNOT_CARRY,
                    which + " cannot be less than nothing, and " + count + " is. Write 0 where "
                            + "the rule does not apply.");
        }
        return count;
    }

    /**
     * The floor as the cents a row holds, judged as money rather than as a number.
     *
     * <p>Quoted through {@code AmountOfMoney} rather than checked here, so that "an amount of money
     * has at most two decimal places" is the sentence a customer typing into a deposit box already
     * gets. How finely a euro is written down is a decision this application made once, and a
     * second copy of it in the products module is a second copy that can be changed on its own.
     * Only the objection to nought is skipped — nought is a real and different agreement here, the
     * one with no floor to keep — which is exactly the split {@code AmountOfMoney} has a method for.
     */
    private static long anAmountInCents(String productCode, String which, BigDecimal euros) {
        if (euros == null) {
            throw nothingWasSent(productCode, which);
        }
        if (euros.signum() < 0) {
            throw refuse(productCode, ProductRefused.Kind.A_FIGURE_THESE_TERMS_CANNOT_CARRY,
                    which + " cannot be less than nothing, and " + euros.toPlainString()
                            + " is. Write 0.00 where there is no floor to keep.");
        }
        AmountOfMoney.whyItIsNotQuotedToTheCent(euros).ifPresent(reason -> {
            throw refuse(productCode, ProductRefused.Kind.A_FIGURE_THESE_TERMS_CANNOT_CARRY,
                    which + ": " + reason);
        });
        if (euros.compareTo(THE_MOST_A_FLOOR_CAN_BE) > 0) {
            throw refuse(productCode, ProductRefused.Kind.A_FIGURE_THESE_TERMS_CANNOT_CARRY,
                    which + " of " + AmountOfMoney.asMoney(euros)
                            + " is more than this application can write down.");
        }
        return euros.movePointRight(2).longValueExact();
    }

    /**
     * The ending, read out of the three this bank offers, or a refusal listing them.
     *
     * <p>The list is quoted from the enum rather than written out in the sentence, so that a fourth
     * ending added in a later ticket is offered here without anybody remembering to come back.
     */
    private static MaturityAction anEnding(String productCode, String named) {
        if (named == null || named.isBlank()) {
            throw refuse(productCode, ProductRefused.Kind.AN_ENDING_THIS_BANK_DOES_NOT_OFFER,
                    "A new version has to say what happens when a term is up. Choose one of "
                            + theEndingsThereAre() + " — HOLD is the one that does nothing, for a "
                            + "product that never reaches a maturity.");
        }
        return Arrays.stream(MaturityAction.values())
                .filter(ending -> ending.name().equals(named.trim()))
                .findFirst()
                .orElseThrow(() -> refuse(productCode,
                        ProductRefused.Kind.AN_ENDING_THIS_BANK_DOES_NOT_OFFER,
                        "\"" + named + "\" is not something that can happen when a term is up. "
                                + "Choose one of " + theEndingsThereAre() + "."));
    }

    private static String theEndingsThereAre() {
        return Arrays.stream(MaturityAction.values())
                .map(MaturityAction::name)
                .collect(Collectors.joining(", "));
    }

    /**
     * One figure read through whichever rule owns it: absent is refused by name, the rule is asked
     * for its sentence, and only then is the figure converted.
     *
     * <p>Written once for the three rates rather than three times, because the shape is the same
     * every time and the only thing that differs is which rule answers. The conversion is a
     * {@link Supplier} so that it is not evaluated before the rule has vouched for the figure —
     * {@code intValueExact} on a rate nobody has checked is how a refusal turns into a stack trace.
     */
    private static int theFigure(String productCode, String which, BigDecimal figure,
                                 Supplier<Optional<String>> whyNot, Supplier<Integer> asHeld) {
        if (figure == null) {
            throw nothingWasSent(productCode, which);
        }
        whyNot.get().ifPresent(reason -> {
            throw refuse(productCode, ProductRefused.Kind.A_FIGURE_THESE_TERMS_CANNOT_CARRY,
                    reason);
        });
        return asHeld.get();
    }

    /**
     * The refusal for a box nobody filled in, in one wording for all eight figures — because eight
     * wordings of one objection is how an application starts sounding like eight.
     */
    private static ProductRefused nothingWasSent(String productCode, String which) {
        return refuse(productCode, ProductRefused.Kind.A_FIGURE_THESE_TERMS_CANNOT_CARRY,
                which + " was not sent. A version says every figure outright and carries nothing "
                        + "over from the version before it, so write 0 where the rule does not "
                        + "apply.");
    }

    /**
     * Says no, in words, and leaves a line in the log saying the same words.
     *
     * <p>The sentence is logged rather than a summary of it, so that what the log says and what the
     * administrator was told are the same thing — the reading {@code SavingsAccountController}
     * already has about a refusal decided outside the module that would otherwise own it.
     */
    private static ProductRefused refuse(String productCode, ProductRefused.Kind kind,
                                         String reason) {
        log.warn("a new version of a savings product's terms was refused product={} kind={} "
                + "reason={}", productCode, kind, reason);
        return new ProductRefused(kind, reason);
    }

    /**
     * The largest floor a {@code long} of cents has room for, worked out from the column rather
     * than typed beside it. It is the edge of the storage and not a view about how much a bank may
     * ask somebody to keep in an account.
     */
    private static final BigDecimal THE_MOST_A_FLOOR_CAN_BE =
            BigDecimal.valueOf(Long.MAX_VALUE, 2);
}
