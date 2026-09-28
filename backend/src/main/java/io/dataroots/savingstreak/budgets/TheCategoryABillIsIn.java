package io.dataroots.savingstreak.budgets;

import java.time.Instant;

/**
 * The category one recurring bill is in, as the rest of the application reads it: which bill, which
 * category, what that category is called, whether it is still standing, and when the bill was put
 * there.
 *
 * <p>A record rather than the entity, like everything that leaves this module.
 *
 * <p><strong>The category's name and state travel with it</strong>, although they belong to
 * {@link SpendingCategory} and could be read from there. Two reasons, and the second is the one that
 * decides it: a list of identifiers is a list nobody can act on, so a page drawing twenty bills
 * would otherwise make twenty-one reads; and a category that has been <em>ended</em> keeps the bills
 * that pointed at it, so every read of one has to be able to say so out loud. A page that had to
 * infer "this label is no longer a word in use" from the absence of the category in another list
 * would be working out a fact this module already knows.
 *
 * <p><strong>Nothing about the bill but its identifier.</strong> What the bill is called, what it
 * costs and the day it goes out on are Accounts' record of it, and whoever draws a bill already has
 * them; copying them here would make this read a second answer to "what is this bill" that could
 * disagree with the first. What this record answers is one question — where is this bill filed —
 * and the page puts the two side by side, which is where this application already puts modules that
 * must not know about each other.
 */
public record TheCategoryABillIsIn(long billId, long currentAccountId, long categoryId,
                                   String categoryName, CategoryState categoryState,
                                   Instant filedAt) {

    /**
     * The row as the rest of the application reads it, with the category it points at beside it.
     * The one place the entity becomes a record.
     */
    static TheCategoryABillIsIn of(CategorisedBill filed, SpendingCategory category) {
        return new TheCategoryABillIsIn(filed.getBillId(), filed.getCurrentAccountId(),
                category.getId(), category.getName(), category.getState(), filed.getFiledAt());
    }
}
