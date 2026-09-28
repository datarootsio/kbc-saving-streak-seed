package io.dataroots.savingstreak.support;

import java.time.Instant;

/**
 * One spending category as the API reports it: what its holder calls it, when they said so, and
 * whether it is still standing.
 *
 * <p>Shared by every test that asks, for the same reason as {@link RecurringBillView} — copies of a
 * shape drift into disagreeing about it. It is the shape the categories' own endpoints answer with,
 * so a test that declares one and then reads the list back is comparing one thing.
 *
 * <p>{@code state} is the field that tells a standing category from one that was ended, which is
 * what lets a test assert that ending is one-way without reading a null and guessing what it meant.
 * {@code endedAt} is filled exactly when the state says ended, and a test reads both.
 *
 * <p>There is nothing in it about money. A category is a name; what it is allowed to cost is a
 * budget, which is a declaration of its own with a figure and a rule on it.
 */
public record SpendingCategoryView(long categoryId, long currentAccountId, String name,
                                   Instant declaredAt, String state, Instant endedAt) {
}
