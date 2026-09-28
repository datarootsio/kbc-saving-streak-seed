package io.dataroots.savingstreak.budgets;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.dataroots.savingstreak.deposits.AmountOfMoney;
import io.dataroots.savingstreak.streaks.SavingsWeek;

/**
 * One week of the six: what is due to arrive in it, what is committed in it, what the budgets claim
 * of it, and what that leaves.
 *
 * <p><strong>The row the whole feature exists to produce, seven days at a time.</strong> A month
 * card says what a month leaves; this says what <em>this</em> week leaves, which is the horizon a
 * person actually plans on — a month starting today is not one anybody lives on and a year is not
 * one anybody can act on.
 *
 * <p><strong>The arithmetic is here rather than on the page, and it adds up exactly.</strong>
 * {@code leftOver} is {@code arriving - committed - claimedByBudgets} to the cent, so a customer can
 * check the row with a pencil. The same bargain {@code TheMonthAhead} and
 * {@link WhatACategoryCostInAMonth} strike, and for the same reason: a page doing its own
 * subtraction would be a second place this application decides what a week leaves, and the two would
 * disagree the first time either changed.
 *
 * <p><strong>{@code committed} counts the bill dates falling in the week and, in the first week, every
 * arrear outstanding.</strong> An arrear is a claim on the very next money in — the nightly run
 * offers it first — so a forecast that waited for a date to come round again would tell a customer
 * carrying two rents that they had room they do not have. {@code TheMonthAhead} makes the same
 * argument about the same figure over a longer window.
 *
 * <p><strong>{@code claimedByBudgets} assumes every budget is spent to its limit.</strong> It is the
 * month's allowance spread evenly over the month's days and summed into this week by
 * {@link HowAMonthsBudgetIsSpreadOverWeeks} — what is left of the budgets in the month the clock is
 * in, and the whole of them in the months after it. That is what makes {@code leftOver} a floor
 * rather than a guess: it is what would still be there if the customer spent every euro they have
 * allowed themselves, which is the conservative direction to be wrong in for a figure whose whole
 * purpose is to decide how much to lock away in savings.
 *
 * <p><strong>{@code leftOver} may be negative, and that is the answer rather than an error.</strong>
 * A week whose bills and budgets claim more than lands in it is exactly the week a customer needs to
 * see coming, and rounding it up to nought would hide it. It is a flow rather than a balance — what
 * the week itself brings and takes — so a negative row beside a healthy balance is a week that eats
 * into what is already there rather than a week that cannot be paid.
 *
 * <p>The week is named by the two days it runs between rather than by a number, because "week 4" is
 * a label nobody can check a calendar against. Both come off {@link SavingsWeek}, which is the week
 * this application already counts in, so a row and a streak week are the same seven days.
 *
 * <p>Derived on every read and stored nowhere, as every other preview in this codebase is.
 */
public record WhatAWeekAheadHolds(LocalDate startsOn, LocalDate endsOn, BigDecimal arriving,
                                  BigDecimal committed, BigDecimal claimedByBudgets,
                                  BigDecimal leftOver) {

    /**
     * One week's row with the subtraction done here, so that no caller can work out what a week
     * leaves a second way.
     */
    static WhatAWeekAheadHolds of(SavingsWeek week, BigDecimal arriving, BigDecimal committed,
                                  BigDecimal claimedByBudgets) {
        BigDecimal leftOver = arriving.subtract(committed).subtract(claimedByBudgets);
        return new WhatAWeekAheadHolds(week.startsOn(), week.endsOn(),
                AmountOfMoney.quotedToTheCent(arriving),
                AmountOfMoney.quotedToTheCent(committed),
                AmountOfMoney.quotedToTheCent(claimedByBudgets),
                AmountOfMoney.quotedToTheCent(leftOver));
    }

    /**
     * Whether the week takes more than it brings, asked here rather than by every screen comparing a
     * figure against nought, for the reason {@link WhatACategoryCostInAMonth#isOverspent} is asked
     * here: a comparison made once cannot be made two ways.
     */
    public boolean takesMoreThanItBrings() {
        return leftOver.signum() < 0;
    }
}
