package io.dataroots.savingstreak.rewards;

/**
 * How many of one offer have gone: this customer's in all, this customer's inside the savings week
 * the question was asked in, and everybody's in all.
 *
 * <p><strong>The name says "gone" rather than "one customer has had", and it used to say the
 * second.</strong> It was written by the slice that only needed a customer's two counts, and the
 * merge that followed folded everybody's count into the same aggregate — at which point a record
 * called {@code HowManyOneCustomerHasHad} was carrying a figure that is not about one customer at
 * all, and every reader of {@link #goneInAll} had to notice the contradiction for themselves. What
 * all three figures have in common is the thing they count: claims standing against one code. Two
 * of them narrow that to this customer and say so in their own names, which is where the narrowing
 * belongs. It reads as the pair of {@link HowManyAreHeld} now, which is the same shape of grouped
 * count over the other table.
 *
 * <p><strong>Two figures out of one aggregate, for every offer at once.</strong> A lifetime cap
 * and a weekly cap are two questions about the same set of claims, and asking them separately
 * would be two passes over the same rows to answer one card. Asking them per offer inside the
 * loop that draws the catalogue would be worse still — a rewards page is a handful of offers
 * today and a scheme somebody runs will not stay that way, and a query per row is how a page that
 * was fast in a training session stops being fast in a demonstration. So the whole of a
 * customer's history is counted once, grouped by the code it was claimed under, and every offer
 * in the reading is answered out of that one map.
 *
 * <p><strong>An offer this customer has never claimed is simply missing from the answer</strong>,
 * which is the honest shape and the one {@code WhenABillWasLastTaken} already takes: there is
 * nothing to count, and a nought row claiming otherwise would have to be invented by the query
 * for every offer in the catalogue and filtered out by nobody. {@link #noneOf} is what the
 * reading uses in its place, and it is the state almost every offer is in for almost every
 * person.
 *
 * <p><strong>Cancelled claims are in none of the three figures and expired ones are in all of
 * them.</strong> That is the spec's rule rather than this record's, but it is worth saying where
 * the numbers are defined: a cancellation is the scheme undoing a claim, so it should not go on
 * costing somebody an allowance they never really spent nor go on holding a thing the scheme
 * still has, while an expiry is the customer's own miss and giving either back for one would
 * make a shelf life free. The two customer figures carried the exclusion from the slice that
 * wrote them, in anticipation of a state nothing could yet write; {@link #goneInAll} gained it
 * with the slice that revokes a voucher, which is the slice that can finally make any of it
 * change an answer.
 *
 * <p><strong>{@link #goneInAll} is the one figure here that is not about this customer, and it
 * is here because the two counts were folded into one query.</strong> Scarcity needs a count of
 * every claim standing against an offer, by anybody; limits need this customer's, and both leave
 * the cancelled ones out. Those were written as two queries by two
 * slices landing at the same time, and both ran on every rewards page load — the second of them
 * once per scarce offer. They are one aggregate now, grouped by the code, so that the number a
 * card is greyed against and the number a claim is refused against come from a single read of
 * the table rather than from two reads that could disagree about a claim made between them.
 * Both filters live inside the projection rather than in a {@code where}, because the two
 * counts want different rows out of the same group.
 *
 * <p>The week is not on this record. It is the savings week the reading was taken in, fixed once
 * for the whole list by {@link RewardsService}, and a copy of it here would be a second place it
 * could be decided from.
 *
 * <p>Package-private, like the repository that builds it and the entity it counts against: what
 * leaves this module is {@link AnOfferAsACustomerReadsIt}, with these figures already folded into
 * it.
 */
record HowManyHaveGone(String code, long everHad, long thisWeek, long goneInAll) {

    /**
     * The answer for an offer nobody has ever claimed, which is what most of the catalogue is
     * for most people.
     *
     * <p>Named rather than written out as three noughts at each call site, because "nothing has
     * ever gone out of it" is a fact worth reading as a sentence and because the figures have to
     * stay in step with each other: a weekly count above a lifetime count, or either of them
     * above what has gone out in all, would be a state no history can produce.
     *
     * <p>It is the right default for the missing row precisely because the query groups over
     * every claim rather than over this customer's: a code absent from the answer is a code
     * nothing has ever been claimed under, by them or by anybody. A code whose every claim has
     * since been cancelled is <em>present</em> in the answer with three noughts on it, which is
     * the same thing said the long way round and is why the two have never had to be told apart.
     */
    static HowManyHaveGone noneOf(String code) {
        return new HowManyHaveGone(code, 0, 0, 0);
    }
}
