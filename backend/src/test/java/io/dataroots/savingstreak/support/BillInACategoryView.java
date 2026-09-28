package io.dataroots.savingstreak.support;

import java.time.Instant;

/**
 * The category one recurring bill is in, as the API reports it: which bill, which category, what it
 * is called, whether that category is still standing, and when the bill was put there.
 *
 * <p>Shared by every test that asks, for the same reason as {@link RecurringBillView} — copies of a
 * shape drift into disagreeing about it. It is the shape the bill-category endpoints answer with, so
 * a test that files a bill and then reads the account's labels back is comparing one thing.
 *
 * <p>{@code categoryId} is nullable because a bill taken out of every category is answered with
 * exactly that: the bill, and no category behind it. {@code categoryState} is what tells a label on a
 * word still in use from one on a word its holder has ended, which is the fact a test about ending a
 * category has to be able to read without inferring it from a second list.
 *
 * <p>Nothing in it says what the bill is called or what it costs. That comes down with the bill
 * itself from the endpoint that owns it, and a test joins the two on the identifier exactly as the
 * page does.
 */
public record BillInACategoryView(long billId, long currentAccountId, Long categoryId,
                                  String categoryName, String categoryState, Instant filedAt) {
}
