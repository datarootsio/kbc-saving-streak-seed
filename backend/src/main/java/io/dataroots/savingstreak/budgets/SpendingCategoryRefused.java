package io.dataroots.savingstreak.budgets;

/**
 * A spending category this application will not keep, or a change to one it will not make, carrying
 * the reason in words the person who typed it can act on.
 *
 * <p>Its own refusal, one per concept, as {@code accounts} has one for bills and another for income.
 * A spend that will not be recorded and a budget that is not worth having are different
 * declarations with different rules, and they will grow apart: this one is about a word, and those
 * are about money. A shared exception would tie the web layer's reading of one to the others', which
 * is the argument {@code NotificationRefused} makes about not borrowing {@code RewardRefused}.
 *
 * <p>There is deliberately no kind for a current account that is not there. The controller vouches
 * for the account in the path first and answers in the words
 * {@code AccountsService.noSuchCurrentAccount} owns, the same order and the same refusal every
 * other module's controller uses — so that "there is no such account" is one sentence in this
 * application rather than one per module.
 *
 * <p>No status code anywhere near it. Which HTTP status reports a refusal is a question about the
 * API, and it is answered in the web layer, where a new value in {@link Kind} breaks the switch by
 * design.
 */
public class SpendingCategoryRefused extends RuntimeException {

    /**
     * The sorts of mistake a refusal here can be about. They are apart because the person reading
     * one has a different thing to do next: look at which category they named, accept that the one
     * they are arguing about is over, use the category they already have, or fix what they typed.
     */
    public enum Kind {

        /**
         * No category with that identifier is on that current account — including one that is on
         * somebody else's. The two are deliberately the same answer, exactly as they are for a bill
         * and for a saving rule: telling a customer that a category exists but belongs to another
         * account would be telling them about another account.
         */
        NO_SUCH_CATEGORY,

        /**
         * The category named has been ended, and an ended category is a record rather than a word
         * still in use.
         *
         * <p>Kept readable and refused to every change, for the reason an ended bill is: the whole
         * point of ending a category rather than deleting it is that the months that were filed
         * under it stay explained, and a record that could be rewritten afterwards is not a record.
         * A customer who is describing that spending again declares it again, which is the honest
         * way to say it.
         */
        THE_CATEGORY_IS_ENDED,

        /**
         * A category of that name is already standing on this account.
         *
         * <p>Its own kind rather than one more thing that is against the rules, because the answer
         * is different in kind: nothing the customer typed is wrong, and what they do next is use
         * the category they already have rather than correct a mistake they did not make. The same
         * distinction {@code CustomerRefused.ALREADY_BANKS_HERE} draws, and the sentence names the
         * category back so that a customer with twenty of them does not have to go and look.
         *
         * <p>It is about the categories still <em>standing</em>. A name whose category was ended is
         * free again, because the old one is a record of months already gone and the new one is a
         * fresh declaration with its own identifier.
         */
        ALREADY_A_CATEGORY_HERE,

        /**
         * The request does not describe a category this application will keep: one with no name, or
         * one category too many on the account.
         *
         * <p>One kind for both, because they are the same thing to whoever is reading: fix what you
         * typed, or make room, and send it again. The sentence says which of them it was.
         */
        AGAINST_THE_RULES
    }

    private final Kind kind;

    SpendingCategoryRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
