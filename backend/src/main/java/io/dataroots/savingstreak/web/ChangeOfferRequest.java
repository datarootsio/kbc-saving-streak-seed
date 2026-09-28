package io.dataroots.savingstreak.web;

/**
 * What somebody running the scheme wants said differently about an offer that already exists.
 *
 * <p>Every field is optional and absent means "leave it alone", so that correcting one word is one
 * field rather than the whole form sent back. The reading is the module's and is argued for on
 * {@code AChangeToAnOffer}, including why a code may be sent here at all when it is the one thing
 * that can never change: a field that can be sent and is refused tells the truth, and a field
 * quietly ignored would show an administrator a rename that never happened.
 *
 * <p>The three eligibility thresholds read like every other field and unlike the two days:
 * absent leaves the rule exactly as it was, and there is no way here to take one back off. The
 * reading is the module's and the wart that comes with it is argued out on its own record.
 *
 * <p>The two caps have the two states every other field here has: absent leaves the cap exactly
 * as it was, and a number sets it. There is no way to take one off, which is the module's
 * reading and is argued out on its own record — the same wart the shelf life carries, and for
 * the same reason.
 *
 * <p><strong>The two days have three states between them, not two, and the contract spells them
 * out</strong> exactly as {@link ChangeGoalRequest} spells out a deadline's. No field at all, or a
 * null one, leaves the day exactly as it was. A day says the offer now opens or closes then. And
 * {@code ""} — which is precisely what a browser sends when somebody empties a date box — says
 * there is no longer such a day, which a person who announced a season and then called it off
 * otherwise has no way to say. Blank text rather than a second field, because matching what the
 * form already does is one fewer thing for the page to remember.
 *
 * <p>The stock is a restock and is the total rather than a difference — "there are forty of these
 * now" rather than "add twelve", because the second depends on what the box said when the page
 * was loaded. Absent leaves it alone, like everything else here. The backend refuses a figure
 * below what has already gone out, in a sentence quoting how many that is, and this layer judges
 * none of it.
 *
 * <p>The promotion's two days have the same three states as the window's, spelled out the same
 * way: absent leaves the day alone, a day moves it, and {@code ""} says there is no longer such a
 * day. Emptying both of them is how a sale is called off, and the discounted price comes off with
 * them — the module's reading, argued out on its own record, and the reason this contract needs
 * no fourth field for "take the promotion away".
 */
record ChangeOfferRequest(String code, String title, String description, Long costInPoints,
                          String voucherPrefix, String opensOn, String closesOn,
                          Integer voucherValidForDays,
                          Integer minimumStreakWeeks, String requiresBadge,
                          Long minimumLifetimePointsEarned,
                          Integer maxPerCustomer, Integer maxPerCustomerPerWeek,
                          Integer stock,
                          Long discountedCostInPoints,
                          String discountOpensOn, String discountClosesOn) {
}
