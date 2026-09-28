package io.dataroots.savingstreak.web;

import java.time.Instant;

import io.dataroots.savingstreak.budgets.ADeclaredCategory;
import io.dataroots.savingstreak.budgets.CategoryState;

/**
 * One spending category as the API reports it: what the customer calls it, when they said so, and
 * whether it is still standing.
 *
 * <p>One shape for reading the standing list, for reading the ended one, for what declaring a
 * category gives back, for what renaming one gives back and for what ending one leaves behind — the
 * same bargain {@link RecurringBillResponse} strikes — so that a page which has just renamed a
 * category does not have to fetch the list again to see what it did.
 *
 * <p>{@code state} rather than an inference from {@code endedAt}. A page draws a standing category
 * and an ended one differently, and asking it to read the absence of a moment would be asking it to
 * work out a fact the backend already knows. The moment is there beside it for the page that wants
 * to say <em>when</em>.
 *
 * <p>Nothing here says what the category is allowed to cost or what it has cost. The first is a
 * budget, which is a declaration of its own and can be superseded without the category changing at
 * all; the second is derived from the spends and the bills of a particular month, and a figure with
 * no month named beside it would be a figure nobody could check.
 */
record SpendingCategoryResponse(long categoryId, long currentAccountId, String name,
                                Instant declaredAt, CategoryState state, Instant endedAt) {

    static SpendingCategoryResponse of(ADeclaredCategory category) {
        return new SpendingCategoryResponse(category.categoryId(), category.currentAccountId(),
                category.name(), category.declaredAt(), category.state(), category.endedAt());
    }
}
