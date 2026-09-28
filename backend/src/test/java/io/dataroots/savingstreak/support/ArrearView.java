package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One thing a current account still owes, as the API reports it — which is exactly as a test reads
 * it: which bill, the day it was owed from, how late that is by now, and what is still owed for it.
 * Shared by every test that reads the arrears back, for the same reason as {@link RecurringBillView}
 * — copies of a shape drift into disagreeing about it.
 *
 * <p>{@code daysLate} is read rather than worked out here, for the reason
 * {@link BillOccurrenceView}'s is: a test that subtracted the dates for itself would be asserting
 * its own arithmetic and would pass against an application that reported nothing at all. It is
 * counted to today rather than to the night the date was refused, which is the figure a customer is
 * actually asking for.
 *
 * <p>{@code amount} is what was originally due and never a cent more, which is the assertion behind
 * "an arrear never accrues a fee, interest or any charge".
 */
public record ArrearView(Long id, long billId, long currentAccountId, String billName,
                         LocalDate dueOn, long daysLate, BigDecimal amount) {
}
