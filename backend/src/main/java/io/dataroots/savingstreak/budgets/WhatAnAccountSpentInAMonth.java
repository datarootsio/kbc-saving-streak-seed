package io.dataroots.savingstreak.budgets;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import io.dataroots.savingstreak.deposits.AmountOfMoney;

/**
 * One current account's month, category by category, with the account's own totals above them.
 *
 * <p>The answer to "how is this month going": what every word the customer describes their money
 * with was allowed to cost, what it actually cost, and what that leaves — assembled once, here,
 * rather than by whoever is drawing it. The month card on the account's screen reads the totals and
 * nothing else; the budget screen reads the categories and draws a bar each; and the two cannot
 * disagree, because they are two readings of one answer.
 *
 * <p><strong>Which month, said out loud.</strong> {@code month} is the month this is an answer
 * about, and {@code from} and {@code until} are the days it runs between, sent rather than left to
 * be inferred for the reason {@code TheMonthAhead} sends its own: a page working out what month it
 * is would draw a month nobody is in on a wound clock, and an account with nothing recorded in it
 * would otherwise leave a page unable to say what the empty figures are empty <em>for</em>.
 *
 * <p><strong>Which categories are in it.</strong> Every category still standing, whether or not
 * anything happened in it — a category with nothing spent is a real answer and drawing it is how a
 * customer sees that they stayed inside it — plus any category already ended that this month
 * actually holds something for, whether a figure that stood or money that moved. An ended category
 * with nothing in the month is left out, because it is a record of months already gone and this is
 * not one of them.
 *
 * <p><strong>{@code uncategorised} is money that left and is filed under nothing.</strong> A spend
 * can be recorded with a part carrying no category at all, which is a state a customer chooses and
 * exactly what a correction is for. It is quoted on its own rather than added into {@code spent},
 * because {@code spent} is the sum of the categories below and a total that silently held money
 * belonging to none of them would be a total nobody could check. Without this figure the euros would
 * simply vanish from the page, which is the one thing a budget read must never do.
 *
 * <p><strong>The totals are absent rather than nought when nothing is budgeted.</strong>
 * {@code budgeted}, {@code carriedIn}, {@code allowed} and {@code left} are null on an account where
 * no category carries a figure in this month, the same reading {@link WhatACategoryCostInAMonth}
 * takes of one category and {@code SavingCapacityOnAnAccount} takes of a capacity nobody declared: a
 * blank drawn as a nought would tell somebody they had overspent a plan they never made.
 *
 * <p><strong>{@code carriedIn} and {@code allowed} are the account's own view of the carry.</strong>
 * {@code carriedIn} is what every budgeted category was handed by the months before this one, added
 * up, and {@code allowed} is what the account's budgets plus that carry allow altogether — which is
 * the figure the card's bar is drawn against and the figure {@code left} is taken from. Both are
 * summed over the rows below rather than worked out a second way, and both may be negative where
 * envelopes were overfilled, which is a real state and is sent as the negative figure it is. There
 * is no rule at this level, because a rule is a thing a customer chooses per category and an account
 * with two rules on it has no single answer.
 *
 * <p><strong>Where some categories are budgeted and others are not, the totals still add up.</strong>
 * {@code budgeted}, {@code carriedIn} and {@code allowed} are the sums over the figures that exist —
 * a limit is only a limit for the categories that have one — while {@code committed},
 * {@code discretionary} and {@code spent} are the sum over every row below, budgeted or not, and
 * {@code left} is {@code allowed - spent} to the cent. So the figures on the card are figures a
 * customer can check against each other with a pencil,
 * which is the bargain {@code TheMonthAhead} strikes and the reason it is worth striking. The
 * consequence is deliberate and is the useful one: money going out under words nobody has put a
 * figure on eats into what the plan said was left, because it is money going out. A customer who
 * does not like that answer is being shown exactly which category to budget next.
 *
 * <p>Every figure is derived on every read from the declarations and the movements. Nothing is
 * stored, no rollup table is kept and no job produces any of it, which is what makes a correction to
 * a split in March change March.
 */
public record WhatAnAccountSpentInAMonth(long currentAccountId, YearMonth month, LocalDate from,
                                         LocalDate until, BigDecimal budgeted, BigDecimal carriedIn,
                                         BigDecimal allowed, BigDecimal committed,
                                         BigDecimal discretionary, BigDecimal spent,
                                         BigDecimal left, BigDecimal uncategorised,
                                         List<WhatACategoryCostInAMonth> categories) {

    /**
     * The account's month with its totals summed from the categories, so that the figure at the top
     * of the page and the figures under it cannot be worked out two ways.
     */
    static WhatAnAccountSpentInAMonth of(long currentAccountId, YearMonth month,
                                         List<WhatACategoryCostInAMonth> categories,
                                         BigDecimal uncategorised) {
        boolean anythingBudgeted = categories.stream().anyMatch(WhatACategoryCostInAMonth::isBudgeted);
        BigDecimal budgeted = totalOf(categories, WhatACategoryCostInAMonth::budgeted);
        BigDecimal carriedIn = totalOf(categories, WhatACategoryCostInAMonth::carriedIn);
        BigDecimal allowed = totalOf(categories, WhatACategoryCostInAMonth::allowed);
        BigDecimal committed = totalOf(categories, WhatACategoryCostInAMonth::committed);
        BigDecimal discretionary = totalOf(categories, WhatACategoryCostInAMonth::discretionary);
        BigDecimal spent = committed.add(discretionary);
        return new WhatAnAccountSpentInAMonth(currentAccountId, month, month.atDay(1),
                month.atEndOfMonth(),
                anythingBudgeted ? AmountOfMoney.quotedToTheCent(budgeted) : null,
                anythingBudgeted ? AmountOfMoney.quotedToTheCent(carriedIn) : null,
                anythingBudgeted ? AmountOfMoney.quotedToTheCent(allowed) : null,
                AmountOfMoney.quotedToTheCent(committed),
                AmountOfMoney.quotedToTheCent(discretionary),
                AmountOfMoney.quotedToTheCent(spent),
                anythingBudgeted ? AmountOfMoney.quotedToTheCent(allowed.subtract(spent)) : null,
                AmountOfMoney.quotedToTheCent(uncategorised),
                categories);
    }

    /**
     * One column added down the categories, treating a category with no figure as contributing
     * nothing to the total rather than as a nought the customer declared. The distinction is kept
     * above, where the whole total is absent if no category has one at all.
     */
    private static BigDecimal totalOf(List<WhatACategoryCostInAMonth> categories,
                                      Function<WhatACategoryCostInAMonth, BigDecimal> column) {
        return categories.stream()
                .map(column)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
