package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.dataroots.savingstreak.budgets.WhatAWeekAheadHolds;

/**
 * One of the six weekly rows as the API reports it: the days it runs between, what arrives in it,
 * what is committed in it, what the budgets claim of it, and what that leaves.
 *
 * <p><strong>The four figures add up.</strong> {@code leftOver} is
 * {@code arriving - committed - claimedByBudgets} to the cent, worked out once in the module and
 * sent rather than left to the page, so that a customer can check the row with a pencil — the same
 * bargain {@link MonthAheadResponse} strikes with the card beside it.
 *
 * <p><strong>The week is named by its two days rather than by a number.</strong> "Week 4" is a label
 * nobody can hold a calendar up against; a Monday and a Sunday are days somebody can find in their
 * own diary. They are sent rather than worked out here for the reason every window in this API is:
 * a page adding sevens to its own idea of today would draw weeks nobody in this application is in
 * on a wound clock.
 *
 * <p><strong>{@code takesMoreThanItBrings} is the comparison already made.</strong> It is true
 * exactly when {@code leftOver} is negative, which is a real week and not an error — one whose bills
 * and budgets claim more than lands in it. It travels because the comparison belongs in one place,
 * the same reason {@code overspent} travels on a category's row.
 */
record WeekAheadResponse(LocalDate startsOn, LocalDate endsOn, BigDecimal arriving,
                         BigDecimal committed, BigDecimal claimedByBudgets, BigDecimal leftOver,
                         boolean takesMoreThanItBrings) {

    static WeekAheadResponse of(WhatAWeekAheadHolds week) {
        return new WeekAheadResponse(week.startsOn(), week.endsOn(), week.arriving(),
                week.committed(), week.claimedByBudgets(), week.leftOver(),
                week.takesMoreThanItBrings());
    }
}
