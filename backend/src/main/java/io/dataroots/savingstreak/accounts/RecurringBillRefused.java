package io.dataroots.savingstreak.accounts;

/**
 * A recurring bill this application will not keep, or a change to one it will not make, carrying the
 * reason in words the person who typed it can act on.
 *
 * <p>Its own refusal rather than {@link MonthlyIncomeRefused} widened, although the two live in one
 * module and refuse some of the same figures. What lands in an account and what leaves it are
 * different declarations with different rules — one is unique per account, the other is capped and
 * can be ended — and they will grow apart: this one already has two kinds an income has no use for.
 * A shared exception would tie the web layer's reading of one to the other's, which is the argument
 * {@code NotificationRefused} makes about not borrowing {@code RewardRefused}. What they do share is
 * the sentence about an amount of money, which {@code AmountOfMoney} owns, so the objection a
 * customer meets on a deposit is word for word the objection they meet here.
 *
 * <p>There is deliberately no kind for a current account that is not there. The controller vouches
 * for the account in the path first and answers in the words {@link AccountsService#noSuchCurrentAccount}
 * owns, the same order and the same refusal every other module's controller uses — so that "there is
 * no such account" is one sentence in this application rather than one per module.
 *
 * <p>No status code anywhere near it. Which HTTP status reports a refusal is a question about the
 * API, and it is answered in the web layer, where a new value in {@link Kind} breaks the switch by
 * design.
 */
public class RecurringBillRefused extends RuntimeException {

    /**
     * The sorts of mistake a refusal here can be about. They are apart because the person reading
     * one has a different thing to do next: look at which bill they named, accept that the bill they
     * are arguing about is over, or fix what they typed.
     */
    public enum Kind {

        /**
         * No bill with that identifier is on that current account — including one that is on
         * somebody else's. The two are deliberately the same answer, exactly as they are for a
         * saving rule: telling a customer that a bill exists but belongs to another account would be
         * telling them about another account.
         */
        NO_SUCH_BILL,

        /**
         * The bill named has been ended, and an ended bill is a record rather than an instruction.
         *
         * <p>Kept readable and refused to every change, for the reason an ended saving rule is: the
         * whole point of ending a bill rather than deleting it is that the months it was taken for
         * stay explained, and a record that could be rewritten afterwards is not a record. A
         * customer who is paying something again declares it again, which is the honest way to say
         * it.
         */
        THE_BILL_IS_ENDED,

        /**
         * The request does not describe a bill this application will keep: a bill with no name, a
         * day of the month outside 1 to 31, an amount that is not an amount of money, a change that
         * says nothing at all, or one bill too many on the account.
         *
         * <p>One kind for all of them, because they are all the same thing to whoever is reading:
         * fix what you typed and send it again. The sentence says which of them it was.
         */
        AGAINST_THE_RULES
    }

    private final Kind kind;

    RecurringBillRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
