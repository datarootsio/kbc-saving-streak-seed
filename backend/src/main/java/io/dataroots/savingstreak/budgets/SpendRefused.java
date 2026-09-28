package io.dataroots.savingstreak.budgets;

/**
 * A spend this application will not record, carrying the reason in words the person who typed it can
 * act on.
 *
 * <p>Its own refusal rather than {@link SpendingCategoryRefused}'s, one per concept, as
 * {@code accounts} has one for bills and another for income. That one is about a word — whether a
 * category may be named, renamed or ended — and this one is about the spend: which spend, what left
 * the account, what the account held, and whether a split adds up. They have no rule in common and
 * no reason to grow one, and a shared exception would tie the web layer's reading of a bad split to
 * its reading of a duplicate name.
 *
 * <p><strong>A part naming a category that is not there is deliberately not one of these.</strong>
 * That objection is {@link SpendingCategoryRefused}'s, raised in the sentence it already owns,
 * because "there is no category 7 on current account 3" is one sentence in this application rather
 * than one per path that can name a category. The same argument {@code AccountsService}'s static
 * sentences make about an account that is not there, applied to a category.
 *
 * <p>There is deliberately no kind for a current account that is not there either. The controller
 * vouches for the account in the path first and answers in the words
 * {@code AccountsService.noSuchCurrentAccount} owns, the same order every other module's controller
 * uses.
 *
 * <p>No status code anywhere near it. Which HTTP status reports a refusal is a question about the
 * API, and it is answered in the web layer, where a new value in {@link Kind} breaks the switch by
 * design.
 */
public class SpendRefused extends RuntimeException {

    /**
     * The sorts of mistake a refusal here can be about. They are apart because the person reading
     * one has a different thing to do next: fix what they typed, make the split add up, put fewer
     * categories on one purchase, or accept that the money is not there.
     */
    public enum Kind {

        /**
         * No spend of that identifier is on that current account.
         *
         * <p>Raised by the one thing that can be done to a spend after it was recorded, which is
         * correcting its split. A spend nobody ever recorded and a spend recorded on somebody else's
         * account are deliberately the same answer in the same sentence: this application has no
         * authentication to ask whose an account is, so the only honest reply to a guessed
         * identifier is one that tells the guesser nothing. A refusal that said "that spend is not
         * yours" would confirm it exists.
         *
         * <p>Its own kind rather than a sentence among the rules, for the reason a bill's and a
         * category's absences are: what the person does next is go and find the right spend rather
         * than fix a figure they typed, and it is the one refusal here that is about something that
         * is not there.
         */
        NO_SUCH_SPEND,

        /**
         * The request does not describe a spend this application will record: one with no name, an
         * amount that is not an amount of money, an amount of no value, or a split with no parts in
         * it at all.
         *
         * <p>One kind for all of them, because they are the same thing to whoever is reading: fix
         * what you typed and send it again. The sentence says which of them it was.
         */
        AGAINST_THE_RULES,

        /**
         * The parts of the split do not sum to what was spent.
         *
         * <p>Its own kind rather than one more thing that is against the rules, because what the
         * person does next is arithmetic on figures they have already typed rather than a
         * correction to one of them — and because this is the invariant the whole feature rests on.
         * A split a cent over or a cent under would leave this application quietly losing euros out
         * of a figure the customer is about to make a decision on, so it is refused and nothing
         * moves. The sentence names both totals, because the gap is the thing they have to find.
         */
        THE_PARTS_DO_NOT_ADD_UP,

        /**
         * One purchase split more ways than this application will keep.
         *
         * <p>Apart from the rules above for the reason the cap on categories is apart from a bad
         * name: nothing typed is wrong, there is simply a limit, and the answer is to combine parts
         * rather than to correct one. The sentence says what the limit is.
         */
        TOO_MANY_PARTS,

        /**
         * The account does not hold what the spend asked for.
         *
         * <p>Nothing moved and nothing was recorded: this is the all-or-nothing withdrawal every
         * other caller in this application meets, and a spend one cent larger than the balance takes
         * nothing rather than taking what is there. The sentence names what was asked for and what
         * was in the account, because the gap between them is what the customer needs in order to
         * decide what to do.
         *
         * <p>Not a conflict, for the reason a deposit's short balance is not one: the account is in
         * no unexpected state, there is simply less in it than was asked for.
         */
        NOT_ENOUGH_MONEY
    }

    private final Kind kind;

    SpendRefused(Kind kind, String reason) {
        super(reason);
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
