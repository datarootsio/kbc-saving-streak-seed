package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.util.List;

/**
 * One current account as its own resource reports it: what it is called, who holds it, what is in it
 * and what its holder says lands in it every month.
 *
 * <p>Shared by every test that asks, for the same reason as {@link MonthlyIncomeView} — copies of a
 * shape drift into disagreeing about it. The income inside it is that very shape, because it is the
 * same answer the income's own endpoint gives.
 *
 * <p>{@code bills} is what the account says leaves it every month — the standing ones, in the order
 * they were declared. Nested here because they come down with the account, so a test reading the
 * page's own read is reading the list the page actually draws rather than a second endpoint's answer
 * that could differ from it.
 *
 * <p>{@code arrears} is what the account still owes, oldest first — the dates a bill fell due on
 * that could not be paid and have not been settled since. Nested here for the same reason the bills
 * are: it comes down with the account because the page draws it in the same breath as the balance it
 * is claimed against, so a test reading the page's own read is reading the list the page draws.
 * Empty when nothing is owed, which is what the page turns into no section at all.
 *
 * <p>{@code monthAhead} is what the other three add up to: what is due in, what is due out and what
 * that leaves, between today and this day next month. Nested here for the same reason as the rest —
 * it is one read of the page's own resource, so a test asserting the sum is asserting on the figures
 * the screen actually shows rather than on a second endpoint's answer that could differ.
 *
 * <p>Named for the account rather than after the endpoint, so that it is not mistaken for the entry
 * of the same account in {@code SeededAccounts}' directory, which carries only an identifier, an
 * IBAN and a balance.
 */
public record TheCurrentAccountView(long currentAccountId, String iban, String customerName,
                                    BigDecimal balance, MonthlyIncomeView income,
                                    List<RecurringBillView> bills, List<ArrearView> arrears,
                                    MonthAheadView monthAhead) {
}
