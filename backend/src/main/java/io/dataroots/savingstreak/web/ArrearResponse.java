package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.dataroots.savingstreak.accounts.AnArrear;

/**
 * One thing this current account still owes, as the API reports it: which bill, the day it was owed
 * from, how late that is by now, and what is still owed for it.
 *
 * <p>The shape behind the still-owed panel, and a shape of its own rather than a
 * {@link BillOccurrenceResponse} with the paid ones filtered out. They answer different questions: a
 * bill's history is what became of every date it fell due on, while this is what is still open
 * against the account, in the order the money will actually go into it. A page that had to filter
 * one list to draw the other would be deciding for itself what "owed" means.
 *
 * <p><strong>{@code daysLate} is counted to today rather than to when the date was last
 * presented</strong>, which is what makes it the figure a customer is actually asking for: how long
 * they have been carrying this. The history's own {@code daysLate} is the other figure — how late
 * the money was when it finally moved — and on an unpaid date that one would read as nought for
 * ever. Sent rather than left for the page to subtract, for the reason every other date arithmetic
 * in this application is: a second place deciding what a day is would be a second answer.
 *
 * <p>{@code amount} is what was originally due and never a cent more. Nothing is ever added to an
 * arrear — no interest, no fee, no charge of any kind — because the accumulation is the whole of the
 * consequence.
 *
 * <p>{@code billName} travels with it so that a list of what is owed reads as rent and phone bill
 * rather than as identifiers. The identifier is here too, for the page to key its rows on.
 */
record ArrearResponse(Long id, long billId, long currentAccountId, String billName,
                      LocalDate dueOn, long daysLate, BigDecimal amount) {

    static ArrearResponse of(AnArrear owed) {
        return new ArrearResponse(owed.id(), owed.billId(), owed.currentAccountId(),
                owed.billName(), owed.dueOn(), owed.daysLate(), owed.amount());
    }
}
