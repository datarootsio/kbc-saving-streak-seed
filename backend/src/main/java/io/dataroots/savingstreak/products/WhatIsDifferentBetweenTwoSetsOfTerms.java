package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import io.dataroots.savingstreak.deposits.AmountOfMoney;

/**
 * What changed between one version of a product's terms and another, said in sentences a customer
 * can read.
 *
 * <p><strong>The one place in this application a difference between two agreements is worded, and
 * that is the whole reason the class exists.</strong> Two readings ask the same question — the
 * account comparing the version it is living under with the version on offer, and the product's
 * history comparing each version with the one before it — and a second implementation of "what
 * moved" would eventually word the same rate cut two ways on two screens of the same application.
 * Worse, the two would disagree about what counts as a change: one of them would notice that the
 * ending moved from a roll-over to instant access and the other would not, and nobody would find
 * out until a customer took terms they had not been shown.
 *
 * <p><strong>A pure function over two records rather than a method on either of them.</strong>
 * {@link ASetOfTerms} is a value that says what one agreement says; a difference is a fact about a
 * <em>pair</em> of them and belongs to neither, exactly as {@link TheTermsOnOfferToday} — a fact
 * about a list and a date — belongs to neither the list nor the date. There is nothing to construct
 * and nothing to inject, and the answer depends on nothing but the two arguments, which is what
 * makes it the same answer wherever it is asked from.
 *
 * <p><strong>Sentences rather than a structure of fields that moved.</strong> A record per changed
 * figure — the name of the figure, the old value, the new one — was the obvious alternative and was
 * rejected: every reader of it would have to turn it back into words, which is the wording problem
 * moved one layer out and duplicated across a web response and a page, and the unit each figure
 * carries (a percentage, a multiple of one, days, months, euros, an ending out of three) would have
 * to travel beside it for anybody to print it correctly. Words are also what the customer is
 * actually being asked to decide on. The cost is that a screen cannot sort or filter the list, and
 * nothing wants to: it is nine lines at the very most, in a fixed order.
 *
 * <p><strong>The order is fixed and is the order a customer cares in</strong>, not the order the
 * columns happen to sit in: what the money earns first, then what the agreement asks of them, then
 * what it costs to leave, then how it ends. Two readings of the same pair therefore produce the
 * same list in the same order, which is what lets a test assert that the account's reading and the
 * history's are one function rather than two that agree today.
 *
 * <p><strong>Nothing is said about a figure that did not move</strong>, including one that is
 * nought on both sides — and an empty list is the honest answer for two versions that say the same
 * thing, which is a real case: a version may be published to change nothing but the line saying
 * what changed. The line saying what changed is itself deliberately not compared. It is prose
 * somebody wrote about a version rather than a figure the version carries, and "the explanation
 * changed" is not a change to the agreement.
 *
 * <p><strong>It says what moved and never whether the move was good.</strong> Free savings'
 * second version cut the rate from 0.60% to 0.50%, and the sentence for it reads exactly like the
 * sentence for a rise. That is the spec's line held in the one place it could be broken: a
 * comparison that sorted improvements from losses would be this application forming an opinion
 * about an agreement its customer is the one who has to decide about.
 *
 * <p>Package-private, like every other piece of reasoning in this module. What leaves is the list
 * of sentences.
 */
final class WhatIsDifferentBetweenTwoSetsOfTerms {

    private WhatIsDifferentBetweenTwoSetsOfTerms() {
    }

    /**
     * One sentence per figure that moved, in the order a customer reads them in, and an empty list
     * when the two agreements say the same thing.
     *
     * @param was what the agreement said before — the version an account is living under, or the
     *            version a history entry follows
     * @param now what it says after — the version on offer today, or the history entry itself
     */
    static List<String> between(ASetOfTerms was, ASetOfTerms now) {
        List<String> differences = new ArrayList<>();
        if (moved(was.annualRatePercent(), now.annualRatePercent())) {
            differences.add("The rate goes from " + asAPercentage(was.annualRatePercent())
                    + " a year to " + asAPercentage(now.annualRatePercent()) + " a year.");
        }
        if (moved(was.bonusRatePercent(), now.bonusRatePercent())) {
            differences.add("The bonus for keeping the balance the terms ask for goes from "
                    + asAPercentage(was.bonusRatePercent()) + " a year to "
                    + asAPercentage(now.bonusRatePercent()) + " a year.");
        }
        if (moved(was.pointsMultiplier(), now.pointsMultiplier())) {
            differences.add("What a euro saved here is worth in points goes from "
                    + was.pointsMultiplier().toPlainString() + " times to "
                    + now.pointsMultiplier().toPlainString() + " times.");
        }
        if (moved(was.anniversaryRatePercent(), now.anniversaryRatePercent())) {
            differences.add("What an anniversary pays on money that stays put goes from "
                    + asAPercentage(was.anniversaryRatePercent()) + " to "
                    + asAPercentage(now.anniversaryRatePercent()) + ".");
        }
        if (was.noticeDays() != now.noticeDays()) {
            differences.add("The notice before money may leave goes from "
                    + inDays(was.noticeDays()) + " to " + inDays(now.noticeDays()) + ".");
        }
        if (was.termMonths() != now.termMonths()) {
            differences.add("The term the money is locked away for goes from "
                    + inMonths(was.termMonths()) + " to " + inMonths(now.termMonths()) + ".");
        }
        if (moved(was.minimumBalance(), now.minimumBalance())) {
            differences.add("The balance to keep for the bonus goes from EUR "
                    + AmountOfMoney.asMoney(was.minimumBalance()) + " to EUR "
                    + AmountOfMoney.asMoney(now.minimumBalance()) + ".");
        }
        if (was.earlyExitPenaltyDays() != now.earlyExitPenaltyDays()) {
            differences.add("Breaking the term early goes from "
                    + inDays(was.earlyExitPenaltyDays()) + " of interest to "
                    + inDays(now.earlyExitPenaltyDays()) + " of interest.");
        }
        if (was.maturityAction() != now.maturityAction()) {
            differences.add("What happens when the term is up goes from "
                    + asAnEnding(was.maturityAction()) + " to "
                    + asAnEnding(now.maturityAction()) + ".");
        }
        return List.copyOf(differences);
    }

    /**
     * Whether two figures are different figures, by value and never by how many places they are
     * written to.
     *
     * <p>{@code compareTo} rather than {@code equals}, which is the difference between a rate that
     * changed and a rate that came back from the database carrying a different scale.
     * {@code BigDecimal.equals} says 0.50 and 0.5000 are different objects, which is true and is not
     * the question: a version republished at the same rate must produce no sentence at all, or every
     * customer on an older version would be told their rate had moved by nothing.
     */
    private static boolean moved(BigDecimal was, BigDecimal now) {
        return was.compareTo(now) != 0;
    }

    /**
     * A rate written the way a page prints one.
     *
     * <p>{@code toPlainString} rather than anything cleverer, because the figure arrives here from
     * {@link BasisPoints#asAPercentage} already carrying the two places every rate in this module
     * carries: 0.60 rather than 0.6, which is what makes "0.60% to 0.50%" read as two rates rather
     * than as two numbers of different sizes. Trimming the trailing nought would be this class
     * deciding how a rate is written, which is a decision {@link BasisPoints} has already made.
     */
    private static String asAPercentage(BigDecimal percentage) {
        return percentage.toPlainString() + "%";
    }

    /**
     * A count of days in words, singular where the count is one.
     *
     * <p>"1 days" is the sort of thing a customer reads as a bug in the bank, and the same shape
     * {@link TheTermAnAccountIsLockedInto} already gives the days left on a term. Written again
     * rather than borrowed because that method is private to a sentence about a maturity date and
     * this one is used by two figures — notice and a penalty — that have nothing to do with it.
     *
     * <p>Nought reads as "0 days" rather than as "no notice", uniformly with every other figure
     * here: the sentence is a comparison of two values and "goes from no notice to 32 days" would
     * be one half of it worded as an absence and the other as a number.
     */
    private static String inDays(int days) {
        return days == 1 ? "1 day" : days + " days";
    }

    /** The same, for the months a term runs, and for the same reason. */
    private static String inMonths(int months) {
        return months == 1 ? "1 month" : months + " months";
    }

    /**
     * What an ending is called in a sentence rather than in the enum.
     *
     * <p>Here rather than as a method on {@link MaturityAction}, and the choice is worth arguing.
     * The enum travels out of this module on {@link ASetOfTerms} and goes over the wire as its name,
     * which is what a page switches on; a display sentence hung on it would be a second thing that
     * value carries, readable by everything that ever holds one, and the first screen to print it
     * would have taken the wording of a difference out of the one place this ticket puts it. The
     * three phrases are part of the comparison, so they live with the comparison.
     */
    private static String asAnEnding(MaturityAction action) {
        return switch (action) {
            case ROLL_OVER -> "a new term of the same length at the terms on offer that day";
            case MOVE_TO_INSTANT -> "the money moving into instant access";
            case HOLD -> "the money sitting where it is until you act";
        };
    }
}
