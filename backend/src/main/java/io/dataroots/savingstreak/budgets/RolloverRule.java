package io.dataroots.savingstreak.budgets;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Optional;

/**
 * What a customer has said should happen to the difference between what a category was allowed to
 * cost in a month and what it actually cost, when that month ends.
 *
 * <p><strong>Three rules and no fourth.</strong> A category can start every month clean, it can
 * carry its surplus forward so that a frugal month buys a generous one, or it can carry both its
 * surplus and its overspend forward — which is envelope budgeting, and the third is the only one
 * where a month can begin with less in it than its budget. That consequence is the whole reason
 * anybody keeps envelopes: an envelope you overfill this month is an envelope with less in it next
 * month, and a rule that carried the good news and not the bad would be a label rather than a
 * constraint.
 *
 * <p><strong>On the budget rather than on the category</strong>, because it is the budget that has a
 * surplus. A category is a word and has no arithmetic; the figure put on it does, and the figure is
 * superseded and never mutated — so a rule changed in June leaves April and May carrying under the
 * rule that stood in them, by the one mechanism that already leaves them quoting the amount that
 * stood in them. {@link MonthlyBudget} argues that at length and this enum is the second field that
 * supersession was always going to cover.
 *
 * <p><strong>No money moves for any of these.</strong> An envelope holds nothing: there is no
 * sub-balance, no transfer and no pot. It is a claim on the one current account balance, exactly as
 * a savings goal is a claim on the one savings balance, and inventing sub-accounts here would make
 * this the one place in the application where a balance is not the balance.
 *
 * <p>{@link #NOTHING_ROLLS_OVER} is the default and is what every budget declared before this rule
 * existed behaved as, which is why it is what a row with nothing in the column is read as. A
 * customer who has never thought about rollover has a rule all the same, and it is the one that
 * surprises nobody.
 *
 * <p>Public, because it leaves the module on {@link ABudgetOnACategory} and on
 * {@link WhatACategoryCostInAMonth}: a page drawing a carry has to be able to say which rule
 * produced it, and a month already gone quotes the rule that stood in it beside the figure.
 */
public enum RolloverRule {

    /**
     * Every month starts clean. Whatever was left is not carried and whatever was overspent is not
     * chased: the difference simply ends with the month.
     *
     * <p>The default, and a real choice rather than the absence of one — a customer is allowed not
     * to be haunted by January.
     */
    NOTHING_ROLLS_OVER,

    /**
     * What was left over is added to next month's allowance, and an overspend is forgiven.
     *
     * <p>Asymmetric on purpose. This is the rule for somebody saving up inside a category — three
     * frugal months buying a fourth generous one — and carrying the overspend as well would be a
     * different promise altogether, which is the rule below.
     */
    THE_SURPLUS_ROLLS_OVER,

    /**
     * The difference is carried whatever its sign: a surplus makes next month bigger and an
     * overspend makes it smaller. The envelope.
     *
     * <p>The only rule under which a month can begin with less than its budget, and the only one
     * under which what a month allows can be negative. That is a real state and is reported as the
     * negative figure it is, because the consequence is the entire point of keeping envelopes and an
     * application that quietly rounded it up to nought would have removed the mechanic while leaving
     * the word.
     */
    THE_SURPLUS_AND_THE_OVERSPEND_ROLL_OVER;

    /**
     * What this rule hands on to the next month, given what this one had left after it was spent.
     *
     * <p><strong>The one place the three rules differ, written as three lines.</strong> Everything
     * else about the fold — adding the carry in, subtracting what was spent, walking the months in
     * order — is the same arithmetic whichever rule stood, so the difference belongs here rather
     * than as three branches scattered through {@link WhatCarriesIntoAMonth}. A fourth rule would be
     * a fourth line here and nothing else anywhere.
     *
     * @param left what the month allowed less what it cost: positive where the category stayed
     *             inside itself, negative where it did not
     */
    BigDecimal whatItHandsOn(BigDecimal left) {
        return switch (this) {
            case NOTHING_ROLLS_OVER -> BigDecimal.ZERO;
            // Only if positive, which is what makes this rule the forgiving one: an overspent month
            // hands on nothing rather than handing on a debt.
            case THE_SURPLUS_ROLLS_OVER -> left.signum() > 0 ? left : BigDecimal.ZERO;
            case THE_SURPLUS_AND_THE_OVERSPEND_ROLL_OVER -> left;
        };
    }

    /**
     * The rule a row carries, reading a row that carries none as the default.
     *
     * <p>A budget written before this column existed has nothing in it, and what it behaved as for
     * every month it governed was {@link #NOTHING_ROLLS_OVER} — so that is what it is read as, here,
     * once. The schema in this application is generated from the entity model and there are no
     * migrations, so "the column is empty on the rows that predate it" is the ordinary case on any
     * database that has been running rather than a fault to guard against.
     */
    static RolloverRule orTheDefault(RolloverRule rule) {
        return rule == null ? NOTHING_ROLLS_OVER : rule;
    }

    /**
     * The rule somebody named, or nothing at all if what they typed is not one of the three.
     *
     * <p>Read here rather than by the framework so that a customer who sends a word this application
     * does not know gets a sentence naming the three it does, instead of whatever a deserialiser
     * would have said about an enum constant. It is the same division of labour the amount is read
     * under: whether the characters are a rule at all is a fact about the request, and the web layer
     * answers it in words the person who typed it can act on.
     *
     * <p>Absent is not the same as unreadable and is deliberately not answered here: a customer who
     * says nothing about rollover has said something — the default — and the caller that knows they
     * said nothing is the one that applies it.
     */
    public static Optional<RolloverRule> named(String name) {
        if (name == null) {
            return Optional.empty();
        }
        String said = name.trim();
        return Arrays.stream(values()).filter(rule -> rule.name().equalsIgnoreCase(said)).findFirst();
    }

    /** The three, written out for the sentence a refusal gives somebody who named a fourth. */
    public static String theOnesThereAre() {
        return String.join(", ", Arrays.stream(values()).map(Enum::name).toList());
    }
}
