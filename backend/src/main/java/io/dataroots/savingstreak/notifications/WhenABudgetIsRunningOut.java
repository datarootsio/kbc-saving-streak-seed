package io.dataroots.savingstreak.notifications;

import java.math.BigDecimal;
import java.util.Optional;

import io.dataroots.savingstreak.budgets.WhatACategoryCostInAMonth;

/**
 * When a spending category's month is worth saying something about, and which of the two things to
 * say: that it is running low, or that it has gone over.
 *
 * <p><strong>Measured against what the month allows and never against what was budgeted.</strong>
 * {@link WhatACategoryCostInAMonth#allowed()} is the budget <em>and</em> the carry — what the month
 * actually allows once the customer's own rollover rule has had its say — and it is the figure the
 * spending is really being taken out of. A fraction taken of the bare budget would call a month four
 * fifths gone on a category carrying forty euros forward that is nothing of the kind, and would
 * leave an envelope carrying a deficit into this month looking untouched while it is already over.
 * The budgets module makes exactly this argument about the bar a page draws, and an alert is the
 * same reading of the same figure.
 *
 * <p><strong>The share is a published figure and arrives as an argument.</strong> Four fifths is
 * what version 1 of the scheme is seeded at and what this rule ran on before the scheme had
 * versions; it is no longer written down here, and the sweep is handed the share in force on the
 * night it runs. A share rather than a number of euros, which is the part of the old argument that
 * was never about the figure: the categories on one account differ by an order of magnitude — fifty
 * euros of room left is nothing on a rent and most of a month on a coffee habit — and a fixed sum
 * would fire on every small category and on no large one. The scheme publishes it as a percentage
 * because that is what an administrator types, and {@link #theShareThatIs} is the one place the two
 * units meet.
 *
 * <p><strong>Two lines and never both.</strong> The quieter reason is defined as the crossing of
 * that share <em>without</em> the crossing of all of it, so the two are mutually exclusive by
 * construction rather than by a rule somebody has to remember: a category that goes straight past
 * its limit between two sweeps is called overspent, and the sentence saying it is running low —
 * which would be false the moment the other is true — is never said about that month at all.
 * Whether it may be said <em>later</em>, after a correction has brought the month back under its
 * limit, is not this class's question but {@link NotificationsService}'s, because it is a question
 * about what has already been said rather than about where the month stands.
 *
 * <p><strong>A month that allows nothing at all has nothing to take a share of.</strong> An
 * envelope overfilled last month can leave this one allowing nought or less, which is a real state
 * and the whole reason anybody keeps envelopes. Such a month is either already overspent — which
 * {@link WhatACategoryCostInAMonth#isOverspent()} says, and says before a single euro is spent in it
 * — or it is exactly at nought with nothing spent, which is not a category running low but a
 * category with no room at all, and calling it "most of the way gone" would be arithmetic dressed
 * up as a warning. So the quieter line is only ever drawn where there is a positive allowance to
 * take the share of.
 *
 * <p><strong>A category nobody has budgeted is passed over entirely.</strong> No amount of spending
 * exceeds a limit nobody set, which is the reading {@code WhatACategoryCostInAMonth.isOverspent}
 * already takes: a category being watched rather than policed is a thing a customer is allowed to
 * choose, and warning them about it would take the choice away.
 *
 * <p>Pure, and asserted over HTTP rather than in a test of its own: the feature's testing decision
 * puts the notification transitions at the integration seam with the job and the wound clock, and
 * keeps the plain unit tests for the calendar and money functions. It lives in a class of its own
 * all the same, for the reason {@link TheBalanceRungs} and {@link WhenArrearsArePilingUp} do — the
 * rule is worth reading on its own, and the sweep that uses it is long enough already.
 */
final class WhenABudgetIsRunningOut {

    /**
     * What a hundred percent is, for the one conversion below. Named rather than written as a
     * literal beside the {@code movePointLeft} that uses it, because "two places" and "a hundred"
     * are the same fact said two ways and a reader checking the conversion should not have to notice
     * that.
     */
    private static final int PLACES_BETWEEN_A_PERCENTAGE_AND_A_SHARE = 2;

    private WhenABudgetIsRunningOut() {
    }

    /**
     * <strong>The one place a published percentage becomes the share this rule compares
     * against.</strong>
     *
     * <p>The scheme publishes this figure as a percentage — {@code 80.00} — because that is what an
     * administrator types into a box marked "running low at" and what a page prints in a sentence.
     * This rule multiplies an allowance by it, so it needs the fraction — {@code 0.8000} — and
     * multiplying by eighty instead would call every month running low the moment a euro was spent
     * in it. There is no unit to check at runtime, for the reason {@code LoyaltyRate} gives about
     * exactly this hazard: {@code 80.00} is a perfectly good {@link BigDecimal}, and a rule handed
     * it as a share would be wrong rather than broken. The defence is that this conversion exists
     * once, is named for both units, and is the only thing a caller holding a published scheme
     * should reach for.
     *
     * <p>The point is moved rather than divided, so that "exact" is a fact about the arithmetic
     * instead of a hope about a rounding mode. The share comes out two places finer than the
     * percentage went in — {@code 80.00} becomes {@code 0.8000} — which is scale and not value:
     * everything this share is used in compares by value, and the only place the further places show
     * is the figure a DEBUG line quotes.
     *
     * @param percentageOfAMonthsAllowance the scheme's own figure, where {@code 80.00} is four
     *                                     fifths — never the fraction, which is what comes back
     */
    static BigDecimal theShareThatIs(BigDecimal percentageOfAMonthsAllowance) {
        return percentageOfAMonthsAllowance.movePointLeft(PLACES_BETWEEN_A_PERCENTAGE_AND_A_SHARE);
    }

    /**
     * What there is to say about one category's month tonight at the stated share, or nothing at
     * all.
     *
     * <p>The whole of the rule, and the only copy of it. Which of the two lines applies, the mutual
     * exclusion between them, the category nobody budgeted and the month with no room at all are all
     * exactly what they were when the share was a constant beside them; what changed is where the
     * share comes from.
     *
     * <p><strong>There is no form of this that supplies the share itself.</strong> The constant and
     * the one-argument form that read it are gone, and their going is a decision rather than a
     * tidy-up: a bridge nobody removes is how two shares start disagreeing, and that one would have
     * gone on calling a month running low at four fifths after the bank published nine tenths, with
     * nothing anywhere objecting, because four fifths is a perfectly good share. The warning that
     * constant carried is now a warning about the argument and is sharper for it — a caller that
     * multiplied out its own share would be the second place this line is decided.
     *
     * <p>The comparison is made in {@link BigDecimal} throughout and the multiplication is done on
     * the allowance rather than the division on the spending, so that a month allowing three euros
     * is judged to the cent instead of against a fraction that had to be rounded somewhere. Which is
     * also why the share may arrive at any scale it likes — the four places {@link #theShareThatIs}
     * hands back, or the two a share is ordinarily written at — without the answer moving.
     *
     * @param month                   the category's month as the budgets module reads it
     * @param shareOfWhatAMonthAllows the fraction of the allowance at which it is worth saying
     *                                something — {@code 0.80} for four fifths, never {@code 80.00};
     *                                {@link #theShareThatIs} is what turns the scheme's published
     *                                percentage into it
     */
    static Optional<NotificationReason> whatThereIsToSayAbout(WhatACategoryCostInAMonth month,
                                                             BigDecimal shareOfWhatAMonthAllows) {
        if (!month.isBudgeted()) {
            return Optional.empty();
        }
        if (month.isOverspent()) {
            return Optional.of(NotificationReason.A_BUDGET_HAS_BEEN_OVERSPENT);
        }
        if (month.allowed().signum() <= 0) {
            // Allowed nothing and not over it, which is a month allowing exactly nought with nothing
            // spent in it. There is no fraction of nothing, and the category has no room rather than
            // little room.
            return Optional.empty();
        }
        BigDecimal whereItStartsRunningLow = month.allowed().multiply(shareOfWhatAMonthAllows);
        if (month.spent().compareTo(whereItStartsRunningLow) >= 0) {
            return Optional.of(NotificationReason.A_BUDGET_IS_RUNNING_LOW);
        }
        return Optional.empty();
    }

    /**
     * What a month has to have cost before it is called running low at the stated share, quoted for
     * the DEBUG line the transition check writes — so that a reviewer redoing the comparison by hand
     * is reading the same figure the comparison was made against rather than multiplying it out
     * themselves.
     *
     * <p>Null where there is no allowance to take a share of, which is exactly the two cases the
     * rule above passes over: a category nobody budgeted, and a month that allows nothing.
     *
     * @param shareOfWhatAMonthAllows the same fraction the decision was taken against, so that the
     *                                line quotes the figure the comparison actually used
     */
    static BigDecimal whereAMonthStartsRunningLow(WhatACategoryCostInAMonth month,
                                                  BigDecimal shareOfWhatAMonthAllows) {
        if (!month.isBudgeted() || month.allowed().signum() <= 0) {
            return null;
        }
        return month.allowed().multiply(shareOfWhatAMonthAllows);
    }
}
