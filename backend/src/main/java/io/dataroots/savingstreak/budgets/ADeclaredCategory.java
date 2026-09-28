package io.dataroots.savingstreak.budgets;

import java.time.Instant;

/**
 * One spending category as its holder finds it: what they call it, when they said so, whether it is
 * still standing, and when it stopped being.
 *
 * <p>A record rather than the entity, like everything that leaves this module. The rows here are
 * package-private and this is the shape the web layer, and any later module, is allowed to hold.
 *
 * <p>It carries its own identifier because everything a customer does to a category afterwards
 * names it: renaming it, ending it, budgeting it, filing part of a spend under it, putting a bill in
 * it. That is the one thing it does not share with {@code DeclaredIncome}, which is one per account
 * and needs no identifier to be named by.
 *
 * <p>{@code endedAt} is filled exactly when {@code state} is {@link CategoryState#ENDED}, which is
 * the same bargain {@code ADeclaredBill} strikes: the state is what a page switches on and the
 * moment is what it prints beside it.
 *
 * <p><strong>There is no figure in it.</strong> What a category is allowed to cost is a budget, and
 * a budget is a declaration of its own with an amount, a rollover rule and the month it takes effect
 * from — a category that carried its own budget would be a category that could not be superseded
 * without rewriting its history. What a category has actually cost is derived on every read from the
 * spends and the bill occurrences, and is a different question again, asked of a month.
 */
public record ADeclaredCategory(long categoryId, long currentAccountId, String name,
                                Instant declaredAt, CategoryState state, Instant endedAt) {

    /** The row as the rest of the application reads it. The one place the entity becomes a record. */
    static ADeclaredCategory of(SpendingCategory category) {
        return new ADeclaredCategory(category.getId(), category.getCurrentAccountId(),
                category.getName(), category.getDeclaredAt(), category.getState(),
                category.getEndedAt());
    }
}
