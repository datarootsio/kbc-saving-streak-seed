package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * One recurring bill as the API reports it: what it is called, the day of the month it goes out on,
 * what it is worth, when it was declared and whether it is still standing.
 *
 * <p>Shared by every test that asks, for the same reason as {@link MonthlyIncomeView} — copies of a
 * shape drift into disagreeing about it. It is the shape the bills' own endpoints answer with and
 * the shape the account's own read nests, so a test that reads a bill one way and then the other is
 * comparing one thing.
 *
 * <p>{@code state} is the field that tells a standing bill from one that was ended, which is what
 * lets a test assert that ending is one-way without reading a null and guessing what it meant.
 *
 * <p>{@code lastTakenOn} is the day the bill's money last actually left the account, and null on one
 * that has never been taken. A test reading it is reading what the page draws: a date on a bill
 * nothing has ever debited would be the page claiming a debit that never happened, and the null is
 * therefore as much a part of the contract as the date.
 */
public record RecurringBillView(long billId, long currentAccountId, String name, int dayOfMonth,
                                BigDecimal amount, Instant declaredAt, String state,
                                Instant endedAt, LocalDate lastTakenOn) {
}
