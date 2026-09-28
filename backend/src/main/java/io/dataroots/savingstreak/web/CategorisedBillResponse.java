package io.dataroots.savingstreak.web;

import java.time.Instant;

import io.dataroots.savingstreak.budgets.CategoryState;
import io.dataroots.savingstreak.budgets.TheCategoryABillIsIn;

/**
 * The category one recurring bill is in, as the API reports it: which bill, which category, what it
 * is called, whether that category is still standing, and when the bill was put there.
 *
 * <p>One shape for reading the account's labels, for what putting a bill in a category gives back
 * and for what taking it out leaves behind — the same bargain {@link RecurringBillResponse} strikes
 * — so that a page which has just moved a bill does not have to fetch the list again to see what it
 * did.
 *
 * <p><strong>Nothing about the bill but its identifier.</strong> What it is called, what it costs
 * and the day it goes out on come down with the bill itself, from the endpoint that owns it; a copy
 * here would be a second answer to "what is this bill" and the two would disagree the first time a
 * customer changed one. The page puts the two reads side by side, keyed on the identifier, which is
 * where this application already assembles modules that must not know about each other — the money
 * movements ledger does exactly the same with its bills.
 *
 * <p><strong>The category is nullable, and a null is not an absence of information.</strong> It is
 * the answer to "which category is this bill in" for a bill that is in none, which is what taking
 * one out of every category leaves behind and what a bill nobody has filed has always said. The read
 * of the whole account simply leaves those bills out, because there is no row for them; this shape
 * carries the null so that the answer to a single request can say what that request achieved.
 *
 * <p>{@code categoryState} rather than an inference. A category that has been ended keeps the bills
 * that pointed at it — nothing is silently unlinked — so a page drawing a label has to be able to
 * say that the word behind it is one its holder has stopped using, and working that out from a
 * second list would be working out a fact the backend already knows.
 */
record CategorisedBillResponse(long billId, long currentAccountId, Long categoryId,
                               String categoryName, CategoryState categoryState, Instant filedAt) {

    /** A bill and the category it is in, as this module's row reads it. */
    static CategorisedBillResponse of(TheCategoryABillIsIn filed) {
        return new CategorisedBillResponse(filed.billId(), filed.currentAccountId(),
                filed.categoryId(), filed.categoryName(), filed.categoryState(), filed.filedAt());
    }

    /**
     * A bill that is in no category at all, which is what taking one out answers with: the request
     * is about a bill, so the answer names the bill and says plainly that there is now no category
     * behind it rather than handing back nothing and leaving the page to assume.
     */
    static CategorisedBillResponse inNoCategory(long currentAccountId, long billId) {
        return new CategorisedBillResponse(billId, currentAccountId, null, null, null, null);
    }
}
