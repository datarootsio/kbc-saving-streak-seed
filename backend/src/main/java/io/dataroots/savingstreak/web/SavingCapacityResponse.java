package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;

import io.dataroots.savingstreak.goals.SavingCapacityOnAnAccount;

/**
 * What a savings account says about the most its holder can put away in a week.
 *
 * <p>One shape for reading it and for what declaring it gives back, like the allocations, so that a
 * page that has just saved a figure does not have to fetch the account again to see what it did.
 *
 * <p>{@code declared} is the field that matters before anything has been said. An account whose
 * holder has never declared a capacity answers {@code declared: false} with a {@code weeklyCapacity}
 * of null — not a zero. The two are different sentences: nobody has said what they can save, as
 * against somebody having said they can save nothing, and a page that read a zero would draw a plan
 * in which no goal ever arrives and present it as the customer's own.
 *
 * <p>{@code weeklyMinimum} travels with it so that whoever shows a capacity can show what it is being
 * measured against without writing the 50.00 into their own markup, and
 * {@code aWeekIsNotSecuredAtThisRate} is the comparison already made: true only when a figure has
 * been declared and it is under that minimum, which is the customer's own plan never securing a
 * streak week. It is a warning and not a refusal — the figure is accepted either way.
 */
record SavingCapacityResponse(long savingsAccountId, boolean declared, BigDecimal weeklyCapacity,
                              BigDecimal weeklyMinimum, boolean aWeekIsNotSecuredAtThisRate,
                              Instant declaredAt) {

    static SavingCapacityResponse of(SavingCapacityOnAnAccount capacity) {
        return new SavingCapacityResponse(
                capacity.savingsAccountId(),
                capacity.isDeclared(),
                capacity.weeklyCapacity(),
                capacity.weeklyMinimum(),
                capacity.aWeekIsNotSecuredAtThisRate(),
                capacity.declaredAt());
    }
}
