package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import io.dataroots.savingstreak.accounts.DeclaredIncome;

/**
 * What a current account says about the income its holder has declared.
 *
 * <p>One shape for reading it, for what declaring it gives back, and for what withdrawing it leaves
 * behind — like the saving capacity — so that a page which has just saved a figure does not have to
 * fetch the account again to see what it did, and a page that has just cleared one is told in the
 * same words that there is now nothing.
 *
 * <p>{@code declared} is the field that matters before anything has been said. An account whose
 * holder has never declared an income answers {@code declared: false} with a null amount and a null
 * day — not a zero and not the first of the month. The two are different sentences: nobody has said
 * what they are paid, as against somebody having said they are paid nothing, and only the second
 * would be a figure the application could act on. It is also the difference between an account the
 * nightly job credits and one it passes over.
 *
 * <p>{@code nextPayday} travels with it so that whoever shows an income can say when the money
 * arrives without doing the month-end arithmetic in their own markup: an income declared for the
 * 31st says the 28th when February is what is next, and that clamp is the application's answer
 * rather than the page's.
 */
record MonthlyIncomeResponse(long currentAccountId, boolean declared, Integer dayOfMonth,
                             BigDecimal amount, LocalDate nextPayday, Instant declaredAt) {

    static MonthlyIncomeResponse of(DeclaredIncome income) {
        return new MonthlyIncomeResponse(
                income.currentAccountId(),
                income.isDeclared(),
                income.dayOfMonth(),
                income.amount(),
                income.nextPayday(),
                income.declaredAt());
    }
}
