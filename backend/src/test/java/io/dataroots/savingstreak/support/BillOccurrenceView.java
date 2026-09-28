package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One date a recurring bill fell due, as the API reports it — which is exactly as a test reads it:
 * the day it was owed, the moment it was settled, how late that was, what it asked for and whether
 * it was paid. Shared by every test that reads a bill's history back, for the same reason as
 * {@link RecurringBillView} — copies of a shape drift into disagreeing about it.
 *
 * <p>{@code outcome} is read as the word the API sends rather than mapped onto an enumeration of the
 * test's own, for the reason {@link RecurringBillView} reads a state that way: a rename in the
 * backend should fail a test rather than be quietly translated back.
 *
 * <p>{@code amount} is what the date asked for on both outcomes, so a test asserting that an unpaid
 * date still names the whole rent is asserting a real part of the contract — an unpaid row is what
 * says how much is still owed, and a nought there would lose the figure entirely.
 *
 * <p>{@code daysLate} is the gap between the two moments, as the API reports it — read rather than
 * worked out here, because a test that subtracted the dates for itself would be asserting its own
 * arithmetic and would pass against an application that reported nothing at all.
 */
public record BillOccurrenceView(Long id, long billId, long currentAccountId, LocalDate dueOn,
                                 Instant settledAt, long daysLate, String outcome,
                                 BigDecimal amount) {
}
