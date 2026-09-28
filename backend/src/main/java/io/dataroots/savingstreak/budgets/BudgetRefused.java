package io.dataroots.savingstreak.budgets;

/**
 * A monthly budget this application will not keep, or a change to one it will not make, carrying the
 * reason in words the person who typed it can act on.
 *
 * <p>Its own refusal, one per concept, as {@code accounts} has one for bills and another for income
 * and this module already has one for categories, one for spends and one for a bill's category. A
 * word a customer describes their money with and a figure they hold it to are different
 * declarations with different rules, and the sentence a person reads has to send them to the right
 * one: "there is no category 7" and "you are not budgeting Groceries" are answers to different
 * questions.
 *
 * <p><strong>There is deliberately no kind for a category that is not there, or one that has
 * ended.</strong> Those are {@link SpendingCategoryRefused}, raised by the face that owns the
 * categories, in the sentences it already uses everywhere a category is named — so that a budget put
 * on a category from somebody else's account is refused in the same words a spend filed under it
 * would be. There is no kind for a current account that is not there either: the controller vouches
 * for the identifier in the path first, in the words {@code AccountsService.noSuchCurrentAccount}
 * owns.
 *
 * <p>No status code anywhere near it. Which HTTP status reports a refusal is a question about the
 * API, and it is answered in the web layer, where a new value in {@link Kind} breaks the switch by
 * design.
 */
public class BudgetRefused extends RuntimeException {

    /**
     * The sorts of mistake a refusal here can be about. They are apart because the person reading
     * one has a different thing to do next: type a figure that is one, or accept that there is
     * nothing on that category to stop.
     */
    public enum Kind {

        /**
         * That category has no figure in force, and stopping one that is not there is not something
         * this application can do.
         *
         * <p>A refusal rather than a shrug, and deliberately not the same answer as stopping one
         * that is there. A customer pressing the button twice has a right to know that the second
         * press did nothing, because the alternative — answering yes to both — is an application
         * that cannot be used to find out what state anything is in. The months a stopped budget
         * governed stay exactly as they were, which is what the sentence says.
         */
        NO_SUCH_BUDGET,

        /**
         * The request does not describe a budget worth having: no figure at all, a figure that is
         * not an amount of money, or a figure of nothing.
         *
         * <p>One kind for all of them, because they are the same thing to whoever is reading: fix
         * what you typed and send it again. The sentence says which of them it was, in the words
         * {@code AmountOfMoney} already owns wherever it can.
         *
         * <p>A budget of nought belongs here rather than being quietly accepted. A category allowed
         * to cost nothing is a category its holder has stopped budgeting, which this module has a
         * way of saying already — and two ways of saying one thing would be two states every month
         * read would then have to mean the same by.
         */
        AGAINST_THE_RULES
    }

    private final Kind kind;

    BudgetRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
