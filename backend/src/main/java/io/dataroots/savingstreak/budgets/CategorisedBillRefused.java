package io.dataroots.savingstreak.budgets;

/**
 * A bill this application will not put in a category, or a change to one it will not make, carrying
 * the reason in words the person who typed it can act on.
 *
 * <p>Its own refusal rather than {@link SpendingCategoryRefused}, although both are thrown by the
 * same service and reported by the same advice. These two refusals are about the <em>bill</em> — it
 * is not on this account, or it has been ended — and the one thing a refusal is for is telling
 * somebody where to look next. A customer told "there is no category 9" when what they named was a
 * bill would go and look through the wrong list, and that is exactly the drift one shared exception
 * per module produces. It is the argument {@code NotificationRefused} makes about not borrowing
 * {@code RewardRefused}, and the reason {@code accounts} has one refusal for bills and another for
 * income.
 *
 * <p>The refusals about the <em>category</em> a bill is being put in stay where they already are:
 * a category that is not on this account and a category that has been ended are
 * {@link SpendingCategoryRefused}, in the sentences that were written for them, because that is the
 * same mistake whether it was made while renaming a category or while filing a bill under one. Two
 * copies of a sentence are one rewording away from disagreeing.
 *
 * <p>There is deliberately no kind for a current account that is not there. The controller vouches
 * for the account in the path first and answers in the words
 * {@code AccountsService.noSuchCurrentAccount} owns, the same order and the same refusal every other
 * module's controller uses.
 *
 * <p>No status code anywhere near it. Which HTTP status reports a refusal is a question about the
 * API, and it is answered in the web layer, where a new value in {@link Kind} breaks the switch by
 * design.
 */
public class CategorisedBillRefused extends RuntimeException {

    /**
     * The sorts of mistake a refusal here can be about. They are apart because the person reading
     * one has a different thing to do next: look at which bill they named, or accept that the bill
     * they are arguing about is over.
     */
    public enum Kind {

        /**
         * No bill with that identifier is on that current account — including one that is on
         * somebody else's. The two are deliberately the same answer, exactly as they are for a
         * category and for a saving rule: telling a customer that a bill exists but belongs to
         * another account would be telling them about another account.
         */
        NO_SUCH_BILL,

        /**
         * The bill named has been ended, and an ended bill is a record rather than an instruction
         * still standing.
         *
         * <p>Its category stays readable and every change to it is refused, which is the same
         * bargain ending a bill already strikes with its name, its day and its amount: the months it
         * was taken for stay explained, and a record that could be re-labelled afterwards is not a
         * record. A customer paying it again declares it again, and the bill they get is a new bill
         * with a category of its own to choose.
         */
        THE_BILL_IS_ENDED
    }

    private final Kind kind;

    CategorisedBillRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
