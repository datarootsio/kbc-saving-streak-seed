package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

import io.dataroots.savingstreak.simulation.AMonthOfTheFuture;

/**
 * One month of a branch as the API answers it: where it leaves the money, the points and the run of
 * weeks, and what happened to the points inside it.
 *
 * <p>The seven figures the month row carries, unrepacked and unrounded, plus the two days a page
 * needs to place it. Nothing is worked out here — which months the year is cut into, what closes
 * one, and what each figure means are the Simulation module's decisions, and a response record
 * deciding any of them would be a rule in the web layer.
 *
 * <p><strong>{@code month} and {@code closesOn} are both sent, and neither is redundant.</strong>
 * The month is what a page writes under a bar; the day is what the figures are true on, and it is
 * the day a trainer winds the clock to in order to check the prediction against the application. A
 * page that derived either from the other would be doing calendar arithmetic against the browser's
 * clock, which can be a year away from the application's — the reason {@code YearAhead}'s own
 * comment already gives.
 *
 * <p>Every figure on it is an illustration rather than a promise, said once at the top of the answer
 * rather than on each of ninety fields.
 */
record AMonthOfTheFutureResponse(YearMonth month, LocalDate closesOn, BigDecimal balance,
                                 long pointsStanding, int securedWeeks, long pointsEarned,
                                 long pointsABonusPaid, long pointsThatExpired) {

    static AMonthOfTheFutureResponse of(AMonthOfTheFuture month) {
        return new AMonthOfTheFutureResponse(month.month(), month.closesOn(), month.balance(),
                month.pointsStanding(), month.securedWeeks(), month.pointsEarned(),
                month.pointsABonusPaid(), month.pointsThatExpired());
    }
}
