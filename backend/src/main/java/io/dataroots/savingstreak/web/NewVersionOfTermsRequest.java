package io.dataroots.savingstreak.web;

/**
 * What somebody running the bank fills in to publish the next version of a product's terms: the day
 * it takes effect, every figure the agreement carries, what happens at the end of it, and the line
 * saying what changed.
 *
 * <p><strong>No code and no version number.</strong> The product is named in the path, because a
 * version belongs to a product and cannot be moved to another one; the version number is the
 * catalogue's answer — one higher than the last that product published — and a number sent from a
 * form would be a number two administrators could send at once.
 *
 * <p><strong>Every figure arrives as the text that was typed.</strong> The same reading a deposit's
 * amount gets, word for word and for the same reason: some of what this application has to say
 * about a rate is about the characters — "0,50" is a mistake somebody makes and deserves an answer
 * about the rate rather than about the request being unreadable — and text is the only form that
 * still has them. It is also what lets an absent field stay absent: a number box left empty
 * arriving as a {@code null} number would be indistinguishable from one somebody typed a nought
 * into, and on this form that difference decides whether a product goes on paying interest.
 *
 * <p><strong>Nothing here is judged and nothing is defaulted.</strong> Whether a rate is a rate,
 * how finely it may be quoted, whether nought is a thing that figure may be, whether the ending is
 * one this bank offers and whether a version may take effect on that day are all rules about
 * agreements, and every one of them belongs to Products, which refuses in its own sentence. There
 * is no {@code jakarta.validation} in this codebase and this record does not introduce one; the
 * only reading done on the way through is characters into figures, which is a fact about the form.
 *
 * <p><strong>Including the ending, which travels as text.</strong> Read into
 * {@code MaturityAction} here, a body naming an ending that does not exist would fail to
 * deserialise somewhere with no words in it; read by the module, it comes back as a sentence
 * listing the three there are.
 *
 * <p>The day travels as text like every other date box in this application — a goal's deadline, an
 * offer's window — and for the reason those give: "31/12/2026" has to survive the journey with its
 * characters intact in order to be quoted back in the refusal.
 */
record NewVersionOfTermsRequest(String effectiveFrom, String annualRatePercent,
                                String bonusRatePercent, String noticeDays, String termMonths,
                                String minimumBalance, String earlyExitPenaltyDays,
                                String pointsMultiplier, String anniversaryRatePercent,
                                String maturityAction, String whatChanged) {
}
