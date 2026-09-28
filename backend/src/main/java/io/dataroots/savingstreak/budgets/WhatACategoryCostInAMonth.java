package io.dataroots.savingstreak.budgets;

import java.math.BigDecimal;

import io.dataroots.savingstreak.deposits.AmountOfMoney;

/**
 * One spending category's month: what it was allowed to cost, what its bills committed to it, what
 * its holder chose to spend, the two added, and what that leaves.
 *
 * <p><strong>The figure this half of the feature exists to put in front of somebody.</strong> A list
 * of spends says where the money went; this says whether it went there in the quantity the customer
 * decided on <em>before</em> the month, which is the judgement this application is here to train.
 *
 * <p><strong>The arithmetic is here rather than on the page, and it adds up exactly.</strong>
 * {@code spent} is {@code committed + discretionary} to the cent, {@code allowed} is
 * {@code budgeted + carriedIn}, and {@code left} is {@code allowed - spent}, so a page draws figures
 * a customer can check against each other with a pencil. A page doing its own subtraction would be a
 * second place this application decides what a category's month cost, and the two would disagree the
 * first time either changed. The same bargain {@code TheMonthAhead} strikes. The factory below is
 * the only place any of that arithmetic exists, and it stays that way.
 *
 * <p><strong>Committed and discretionary are told apart by the movement and never by the
 * category.</strong> {@code committed} is what the bills filed here actually took in the month —
 * money that left on a standing instruction the customer made once — and {@code discretionary} is
 * what they chose to spend, part by part, as the month went. One category holds both, and it is the
 * split that makes the read worth reading: a customer who budgets two hundred for Transport and pays
 * a ninety-euro season ticket by standing order has a hundred and ten of <em>room</em>, and knowing
 * which part of the two hundred they can still do something about is the whole point.
 *
 * <p><strong>{@code budgeted} is null when nothing was declared, and that is a state rather than a
 * nought.</strong> The precedent is {@code SavingCapacityOnAnAccount}: not declared is not zero, and
 * everything downstream reports that there was no budget rather than quoting a figure the customer
 * never gave. A category with no budget still reports what it cost — the spending is a fact whether
 * or not anybody put a limit on it — and a blank drawn as a nought would tell somebody they had
 * overspent a figure they had not set. {@code rollover}, {@code carriedIn}, {@code allowed} and
 * {@code left} are all null with it, and for the same reason: there is no rule on a limit that does
 * not exist, nothing carries into a month nobody was measuring, and there is nothing to have left of
 * it. The four are absent together or present together, so a page never has to decide what a carry
 * beside an absent budget would have meant.
 *
 * <p><strong>{@code carriedIn} is what the months before this one handed it, under the rule that
 * stood in each of them.</strong> It is derived by {@link WhatCarriesIntoAMonth} on every read and
 * is stored nowhere — there is no monthly close and no rollup — which is what makes a correction to
 * a March split move the carry in April, May and every month after them. It may be negative, on an
 * envelope whose holder overfilled it.
 *
 * <p><strong>{@code allowed} is what the month actually allows: the budget plus the carry.</strong>
 * Sent rather than left to whoever is drawing it, because it is the figure the spending is really
 * measured against — a bar drawn against {@code budgeted} on a category carrying forty euros would
 * call a month overspent that is nothing of the kind — and because it is the figure an alert asks
 * about. <strong>It may be negative, and that is a real state reported as the negative figure it
 * is.</strong> An envelope overfilled last month can leave this one with less than nothing to spend;
 * that consequence is the entire reason anybody keeps envelopes, it is not an error, and rounding it
 * up to nought would remove the mechanic while leaving the word.
 *
 * <p><strong>{@code rollover} is the rule that stood in <em>this</em> month</strong>, not the one
 * standing now. A month already gone quotes the budget, the rule and the carry that governed it, so
 * that a customer who turned a category into an envelope in June reads April as the April they
 * actually lived through.
 *
 * <p><strong>{@code left} may be negative, and that is the answer rather than an error.</strong> A
 * category can plainly cost more than it was allowed to — nothing refuses a spend for being over a
 * budget, because a budget is a plan and a balance is the only hard constraint in this application —
 * and rounding an overspend up to nothing would hide exactly the month a customer needs to see.
 *
 * <p><strong>{@code categoryState} is on it because a month can hold an ended category.</strong>
 * Ending a category stops its budget, and the month it was ended in still reports what was spent
 * against the figure that stood — the spending happened, so the standard it was measured against has
 * to be readable — with this field saying that the word is one its holder has stopped using. A page
 * that had to infer that from the category's absence from another list would be working out
 * something this module already knows.
 *
 * <p>Every figure is derived on every read from the declarations and the movements, and none of it
 * is stored. There is no rollup table, no monthly close and no job: change a spend's split, put a
 * bill in a different category or supersede a budget, and the next read says something else with
 * nothing to invalidate.
 */
public record WhatACategoryCostInAMonth(long categoryId, String name, CategoryState categoryState,
                                        RolloverRule rollover, BigDecimal budgeted,
                                        BigDecimal carriedIn, BigDecimal allowed,
                                        BigDecimal committed, BigDecimal discretionary,
                                        BigDecimal spent, BigDecimal left) {

    /**
     * One category's month, with every total worked out here so that no caller can add them up
     * differently.
     *
     * <p>The one place {@code spent}, {@code allowed} and {@code left} come from. A second
     * subtraction anywhere — on a page, in a controller, in an alert — would be a second answer to
     * what a category's month cost, and the two would disagree the first time either changed.
     *
     * @param stood     the figure and the rule that governed this month, or null if the category
     *                  carried none in it
     * @param carriedIn what the months before this one handed it under their own rules, as
     *                  {@link WhatCarriesIntoAMonth} folded them — ignored where no figure stood,
     *                  because a month nobody was measuring has nothing arriving in it
     */
    static WhatACategoryCostInAMonth of(ADeclaredCategory category, ABudgetThatStood stood,
                                        BigDecimal carriedIn, BigDecimal committed,
                                        BigDecimal discretionary) {
        BigDecimal spent = committed.add(discretionary);
        if (stood == null) {
            return new WhatACategoryCostInAMonth(category.categoryId(), category.name(),
                    category.state(), null, null, null, null,
                    AmountOfMoney.quotedToTheCent(committed),
                    AmountOfMoney.quotedToTheCent(discretionary),
                    AmountOfMoney.quotedToTheCent(spent), null);
        }
        BigDecimal allowed = stood.amount().add(carriedIn);
        return new WhatACategoryCostInAMonth(category.categoryId(), category.name(),
                category.state(), stood.rollover(),
                AmountOfMoney.quotedToTheCent(stood.amount()),
                AmountOfMoney.quotedToTheCent(carriedIn),
                AmountOfMoney.quotedToTheCent(allowed),
                AmountOfMoney.quotedToTheCent(committed),
                AmountOfMoney.quotedToTheCent(discretionary),
                AmountOfMoney.quotedToTheCent(spent),
                AmountOfMoney.quotedToTheCent(allowed.subtract(spent)));
    }

    /**
     * Whether a figure was ever declared for this category in this month, which is what tells an
     * absent budget from a small one.
     *
     * <p>Asked here rather than by every screen comparing {@code budgeted} against null, for the
     * reason {@code SavingCapacityOnAnAccount.isDeclared} exists: the comparison is made once so
     * that it cannot be made two ways.
     */
    public boolean isBudgeted() {
        return budgeted != null;
    }

    /**
     * Whether the month cost more than it was allowed to — the budget <em>and</em> the carry, which
     * is what {@code left} is taken from. A frugal month's surplus therefore buys the next month
     * room before it is called over, and an envelope's overspend takes room away, which is exactly
     * what the customer asked for by choosing the rule.
     *
     * <p>False when nothing was budgeted, because no amount of spending exceeds a limit nobody set —
     * that is a category being watched rather than policed, which is a thing a customer is allowed
     * to choose.
     */
    public boolean isOverspent() {
        return isBudgeted() && left.signum() < 0;
    }
}
