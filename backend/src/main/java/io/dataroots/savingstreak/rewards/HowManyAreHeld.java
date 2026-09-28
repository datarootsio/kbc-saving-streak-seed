package io.dataroots.savingstreak.rewards;

/**
 * How many of one offer are being held by anybody at all, right now.
 *
 * <p><strong>The second source the scarcity arithmetic reads, and it is deliberately its own
 * record rather than two more components on {@link HowManyHaveGone}.</strong> That one
 * is a grouped count over the claims table and answers what has <em>gone</em>; this one is a
 * grouped count over the holds table and answers what is <em>set aside</em>. They are two tables,
 * two queries and two facts that happen to be subtracted from the same stock figure, and folding
 * them into one projection would mean a query joining a customer's claim history to everybody
 * else's reservations in order to save a record.
 *
 * <p>Nothing in here is about the customer doing the reading. Whether <em>they</em> hold one is a
 * different question with a different answer — it is on {@link AHoldOfYourOwn}, which carries the
 * moment their own hold runs out because that is the part a card has to show — and keeping the
 * two apart is what stops "held" meaning "held by somebody else" in one line and "held by me" in
 * the next. The count here includes the reader's own hold, because stock does not care whose it
 * is; the places that must not let a customer's own hold lock them out say so themselves.
 *
 * <p>Live holds only, judged against the moment the reading was taken: a hold whose seventy-two
 * hours have gone by is holding nothing, whether or not the sweep has got round to writing that
 * down. The argument is on {@link HoldState}.
 *
 * <p>{@code noneOf} is the answer for almost every offer for almost every reading, and it exists
 * for the reason {@link HowManyHaveGone#noneOf} does: the query groups over the rows
 * there are, and an offer nobody is holding produces no row at all rather than a nought.
 */
record HowManyAreHeld(String code, long heldInAll) {

    static HowManyAreHeld noneOf(String code) {
        return new HowManyAreHeld(code, 0);
    }
}
