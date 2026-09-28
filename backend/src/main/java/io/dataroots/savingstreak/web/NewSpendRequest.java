package io.dataroots.savingstreak.web;

import java.util.List;

/**
 * What a customer sends to record what they spent: a name, what it cost, and the split it was for,
 * for the account in the path.
 *
 * <p>The amount arrives as the text that was typed rather than as a number already read for us, for
 * the reason a bill's, a deposit's and a goal's target all do: "25,00" is a mistake a Belgian page
 * makes and deserves an answer about the figure rather than about the whole request being
 * unreadable. Text is the only form that still has the comma in it to name back.
 *
 * <p><strong>There is no date in it, and there will not be one.</strong> When the spend counts is
 * the application's clock, which is the whole of "a spend cannot be backdated": a field here would
 * let a customer file a spend into a month they have already read and rewrite a rollover chain by
 * typing a date.
 *
 * <p>There is nothing else in it either. Which account the money leaves is the path, and whether any
 * of this describes a spend the application will record is the Budgets module's answer rather than
 * this record's.
 */
record NewSpendRequest(String name, String amount, List<NewSpendPartRequest> parts) {
}
