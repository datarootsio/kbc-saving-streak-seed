package io.dataroots.savingstreak.web;

import java.util.List;

/**
 * What somebody running the bank fills in to publish the next version of the scheme: the Monday it
 * takes effect, every figure the bank has decided about saving, and the line saying what changed.
 *
 * <p><strong>No version number.</strong> Which version this becomes is the module's answer — one
 * higher than the last the bank published — and a number sent from a form would be a number two
 * administrators could send at once. There is no code either, where a product's form has one in the
 * path: there is one scheme.
 *
 * <p><strong>Every figure arrives as the text that was typed.</strong> The same reading a deposit's
 * amount and a product's rate already get, word for word and for the same reason: some of what this
 * application has to say about a figure is about the characters — "0,50" is a mistake somebody
 * makes and deserves an answer about the rate rather than about the request being unreadable — and
 * text is the only form that still has them. It is also what lets an absent field stay absent: a
 * number box left empty arriving as a {@code null} number would be indistinguishable from one
 * somebody typed a nought into, and on this form that difference decides whether every week in the
 * bank secures itself.
 *
 * <p><strong>The rungs arrive as a list of text.</strong> A list because that is what they are — an
 * ordered collection of one kind of thing, of a length nobody has fixed — and text for the reason
 * every other figure here is text. An empty box inside the list stays an empty box rather than
 * being dropped, so that "the third rung is blank" is a sentence the module can say instead of a
 * ladder quietly one rung shorter than the one on the screen.
 *
 * <p><strong>Nothing here is judged and nothing is defaulted.</strong> Whether a rate may be quoted
 * to five places, whether nought is a thing the ordinary rate may be, whether the rungs climb and
 * whether a version may take effect on the day named are all rules about the scheme, and every one
 * of them belongs to the Scheme module, which refuses in its own sentence. There is no
 * {@code jakarta.validation} in this codebase and this record does not introduce one; the only
 * reading done on the way through is characters into figures, which is a fact about the form.
 *
 * <p>The day travels as text like every other date box in this application — a goal's deadline, an
 * offer's window, a product's effective date — and for the reason those give: "31/12/2026" has to
 * survive the journey with its characters intact in order to be quoted back in the refusal.
 */
record NewVersionOfTheSchemeRequest(String effectiveFrom, String weeklyThreshold,
                                    String theOrdinaryRate, String extraForEachFurtherWeek,
                                    String theMostAStreakPays, String howLongABatchOfPointsLasts,
                                    List<String> balanceRungs,
                                    String whatShareOfABudgetIsRunningLow,
                                    String howManyOutstandingIsASpiral,
                                    String daysBeforeAMaturityIsWorthSaying,
                                    String daysBeforeAnAnniversaryIsWorthSaying,
                                    String whatChanged) {
}
