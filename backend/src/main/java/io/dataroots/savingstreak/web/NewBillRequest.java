package io.dataroots.savingstreak.web;

/**
 * What a customer sends to declare something that leaves a current account every month: a name, a
 * day of the month and an amount, for the account in the path.
 *
 * <p>The figures arrive as the text that was typed rather than as numbers already read for us, for
 * the reason a monthly income's, a goal's target and a deposit's amount all do: "2.500,00" is the
 * mistake a Belgian page makes most, and it deserves an answer about the figure rather than about
 * the whole request being unreadable. Text is the only form that still has the comma in it to name
 * back — and the same holds for a day typed as "the 1st", which a number-shaped field would reject
 * with a sentence about JSON.
 *
 * <p>There is nothing else in it. Which account it leaves is the path, when it was declared is the
 * application's clock, and which day a short month takes it on is worked out rather than sent.
 */
record NewBillRequest(String name, String dayOfMonth, String amount) {
}
