package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.dataroots.savingstreak.accounts.TheMonthAhead;

/**
 * What one current account has to cover between today and this day next month, as the API reports
 * it: what is in it, what is due to arrive, what is due to leave and what that leaves.
 *
 * <p>The four figures a customer needs before deciding how much to save, and the reason the whole
 * feature exists: a balance on its own says "you have 2480 euros", and this says "you have 2480
 * euros and 1165 of it is spoken for".
 *
 * <p><strong>They add up, and a page can check them.</strong> {@code leavesYou} is
 * {@code balance + incomeDue - billsDue} to the cent, worked out by the backend for the reason every
 * other arithmetic in this application is: a page doing its own subtraction would be a second place
 * this application decides what a month costs.
 *
 * <p>{@code billsDue} holds what is already owed as well as what is still to fall, because an arrear
 * is a claim on this balance exactly as a standing bill is and the nightly run offers the money to
 * it first. {@code arrearsOutstanding} says how much of the figure that is, so the page can name it.
 *
 * <p>{@code leavesYou} may be negative, and that is the answer rather than an error: a month that
 * cannot be paid in full unless something changes is exactly the month worth being told about.
 *
 * <p>Nested inside the account's own read rather than given a path of its own, because it is the
 * same question the page is already asking — and a page that had to make a second request to say
 * what its balance means would show the balance a moment before showing what is claimed against it.
 */
record MonthAheadResponse(LocalDate from, LocalDate until, BigDecimal balance,
                          BigDecimal incomeDue, BigDecimal billsDue,
                          BigDecimal arrearsOutstanding, BigDecimal leavesYou) {

    static MonthAheadResponse of(TheMonthAhead month) {
        return new MonthAheadResponse(month.from(), month.until(), month.balance(),
                month.incomeDue(), month.billsDue(), month.arrearsOutstanding(), month.leavesYou());
    }
}
