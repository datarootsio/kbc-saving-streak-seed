package io.dataroots.savingstreak.scheme;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * What somebody running the bank sends to publish the next version of the scheme: the Monday it
 * takes effect, every figure this bank has decided about saving, and one line saying what changed.
 *
 * <p>A sibling of {@code ANewVersionOfTheTerms}, deliberately and nearly line for line, because the
 * scheme is the same idea as a product's agreement applied to a different set of numbers and a
 * second way of publishing a version would be a second way for one to be wrong. Where it parts
 * company it parts company loudly, and there is exactly one place: the day.
 *
 * <p><strong>There is no version number on it.</strong> Which version this becomes is the module's
 * answer — one higher than the last the bank published — and a number sent from outside would be a
 * number two administrators could send at once. There is no code beside it either, where a
 * product's version carries one: there is one scheme, so the version number is the whole address
 * and there is nothing for a version to belong to.
 *
 * <p><strong>Every figure is required, and nothing is carried forward from the version
 * before.</strong> Absent meaning "same as last time" would make a version a patch on its
 * predecessor, so what a published scheme said would depend on reading the whole chain behind it,
 * and a box nobody filled in would be an invisible copy. A version is the complete list of numbers;
 * that is the argument {@link SchemeVersion#published} already makes about its thirteen parameters,
 * and it does not stop at the module boundary. The screen fills the form in from the version in
 * force, so an administrator still changes one figure and presses once — but what goes up is the
 * whole scheme, visibly.
 *
 * <p><strong>Boxed numbers throughout, so that "nobody filled it in" is a sentence rather than a
 * nought.</strong> The danger is sharper here than it is on a product's terms, and that is worth
 * saying rather than inheriting. A product reads nought as a real agreement in six of its figures —
 * no notice, no term, no floor — so an empty box read as nought publishes something somebody could
 * honestly have meant. Not one figure on this form has an absence to read: an empty weekly
 * threshold read as nought would publish a scheme in which every week secures itself and every
 * customer walks the whole ladder for nothing, an empty ordinary rate would publish a deposit that
 * silently earns no points, and an empty points lifetime would publish a batch that expires the
 * moment it is earned. The module refuses each absence by name instead.
 *
 * <p><strong>The units are the ones {@link TheSchemeAsPublished} reads out in, so a figure read off
 * a screen and a figure published back are the same figure.</strong> Amounts are euros — the
 * weekly threshold and every rung — rates are plain multiples of one, and the running-low share is
 * a percentage rather than the fraction the rule that uses it compares against. Not one basis point
 * and not one cent crosses this boundary in either direction; {@link BasisPointsOfTheScheme} and
 * {@code AmountOfMoney} are where they become integers, and each refuses a figure it cannot hold
 * rather than rounding one.
 *
 * <p><strong>The day may only be a Monday still to come, and that is the divergence from
 * {@code ANewVersionOfTheTerms} worth arguing.</strong> That record's day "may be in the past,
 * today, or ahead of today", and a reader arriving from the products module will read the rule here
 * as an oversight unless it is spelled out. Two things make the scheme different. A savings week
 * runs Monday to Sunday and is judged by the scheme in force on its own Monday, so a version taking
 * effect on a Wednesday would judge one week under two schemes — there is no such thing as half a
 * week at the new threshold. And nobody is pinned to a version of the scheme: a product's account
 * is written under the version it was opened with, so backdating a product's terms changes nothing
 * that was already decided, whereas the scheme applies to everybody from its date and a back-dated
 * version would change which weeks already counted. That is precisely the silent rewriting of
 * history this whole feature exists to prevent, so the past is not a date this form may carry.
 */
public record ANewVersionOfTheScheme(

        /**
         * The Monday it takes effect, which has to be a Monday and has to be still to come.
         *
         * <p>Unlike a product's version, which may be dated in the past, today, or ahead of today.
         * The record's own javadoc argues why at length; the short of it is that a week is judged
         * by the scheme in force on its Monday, so a Wednesday would split a week and a day already
         * gone would re-judge one.
         */
        LocalDate effectiveFrom,

        /** What a week has to take in, net, to secure itself, in euros — {@code 50.00}. */
        BigDecimal weeklyThreshold,

        /** What the first week of a run pays per euro, as a multiple of one — {@code 1.0000}. */
        BigDecimal theOrdinaryRate,

        /**
         * What each further week of a run adds, as a multiple — {@code 0.1000}.
         *
         * <p>Nought is a scheme and not a gap: a bank paying the same for one week as for twenty
         * has published a flat ladder, which is a decision somebody could honestly make. It is the
         * one figure on this form where nought is accepted, and it is accepted because the ladder
         * is still a ladder without a step.
         */
        BigDecimal extraForEachFurtherWeek,

        /** Where the ladder stops climbing, as a multiple, and never below the ordinary rate. */
        BigDecimal theMostAStreakPays,

        /** How long a batch of points lasts, in whole months — 12, and never fewer than one. */
        Integer howLongABatchOfPointsLasts,

        /**
         * The balance rungs a customer is congratulated on reaching, in euros, strictly ascending
         * and each a whole euro.
         *
         * <p>A list rather than a fixed set of boxes, because it is genuinely a list: an ordered
         * collection of one kind of thing, of a length nobody has fixed. A version publishing seven
         * rungs is a longer ladder and not a broken form.
         */
        List<BigDecimal> balanceRungs,

        /**
         * The share of a budget at which it is said to be running low, as a percentage —
         * {@code 80.00} is four fifths.
         *
         * <p>A percentage rather than the {@code 0.80} fraction the rule compares against, because
         * this is the figure somebody types into a box marked "running low at" and the figure
         * {@link TheSchemeAsPublished} reads back out. A form that took the fraction and a reading
         * that gave the percentage would be two units for one figure, which is how a scheme gets
         * published at eighty times what anybody meant.
         */
        BigDecimal whatShareOfABudgetIsRunningLow,

        /** How many bills outstanding at once is arrears piling up — 3, and never fewer than one. */
        Integer howManyOutstandingIsASpiral,

        /** How many days before a maturity it is worth saying so — 30, and never fewer than one. */
        Integer daysBeforeAMaturityIsWorthSaying,

        /** How many days before an anniversary it is worth saying so — 30, and never fewer. */
        Integer daysBeforeAnAnniversaryIsWorthSaying,

        /**
         * One line saying what changed and why, and required, because somebody will read it.
         *
         * <p>Required on every version, where a product's first version leaves it empty. Nobody
         * opted into the scheme: it applies from its Monday to every customer the bank has, so a
         * version that arrived without a sentence would be a rate change with a date and no
         * explanation — which is one of the three things this feature exists to fix.
         */
        String whatChanged) {
}
