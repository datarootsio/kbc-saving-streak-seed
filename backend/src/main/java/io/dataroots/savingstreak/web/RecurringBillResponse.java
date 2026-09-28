package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import io.dataroots.savingstreak.accounts.ADeclaredBill;
import io.dataroots.savingstreak.accounts.BillState;

/**
 * One recurring bill as the API reports it: what the customer calls it, the day of the month it goes
 * out on, what it is worth, when they last said so, and whether it is still standing.
 *
 * <p>One shape for reading the list, for what declaring a bill gives back, for what changing one
 * gives back and for what ending one leaves behind — the same bargain {@link MonthlyIncomeResponse}
 * strikes — so that a page which has just changed a bill does not have to fetch the account again to
 * see what it did.
 *
 * <p>{@code state} rather than an inference from {@code endedAt}. A page draws a standing bill and an
 * ended one differently, and asking it to read the absence of a moment would be asking it to work
 * out a fact the backend already knows. The moment is there beside it for the page that wants to say
 * <em>when</em>.
 *
 * <p><strong>{@code lastTakenOn} is the day the money last actually left</strong>, and null on a
 * bill that has never been taken. It is the one thing about a standing bill that a customer cannot
 * work out for themselves: a current account keeps a figure rather than a sum of records, so a
 * balance says nothing about which bills moved it, and "did my rent go out this month" is exactly
 * the question a list of bills is read with.
 *
 * <p>A day rather than a moment, because it is the date the money was owed on and that is what the
 * customer recognises — the moment it was settled at, and how late that was, are on each date in the
 * bill's own history, which is where somebody asking about one month goes.
 *
 * <p>Deliberately the last date that was <em>paid</em>. A date presented against an account that
 * could not cover it is in the history as unpaid and is not this date, because the bill was not
 * taken then and saying otherwise would tell the customer the opposite of what happened.
 *
 * <p>Nothing here says when the bill will next be taken. The day of the month says when it falls due
 * and the calendar clamps it, and a forecast of the dates ahead is the timeline's answer rather than
 * this one's.
 */
record RecurringBillResponse(long billId, long currentAccountId, String name, int dayOfMonth,
                             BigDecimal amount, Instant declaredAt, BillState state,
                             Instant endedAt, LocalDate lastTakenOn) {

    static RecurringBillResponse of(ADeclaredBill bill) {
        return new RecurringBillResponse(bill.billId(), bill.currentAccountId(), bill.name(),
                bill.dayOfMonth(), bill.amount(), bill.declaredAt(), bill.state(), bill.endedAt(),
                bill.lastTakenOn());
    }
}
