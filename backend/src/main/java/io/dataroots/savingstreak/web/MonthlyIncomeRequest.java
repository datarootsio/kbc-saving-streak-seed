package io.dataroots.savingstreak.web;

/**
 * What a customer sends to declare what lands in a current account every month: a day of the month
 * and an amount, for the account in the path.
 *
 * <p>Both arrive as the text that was typed rather than as numbers already read for us, for the
 * reason a goal's target, a deposit's amount and a weekly capacity all do: "2.500,00" is the mistake
 * a Belgian page makes most, and it deserves an answer about the figure rather than about the whole
 * request being unreadable. Text is the only form that still has the comma in it to name back — and
 * the same holds for a day typed as "the 25th", which a number-shaped field would reject with a
 * sentence about JSON.
 *
 * <p>There is nothing else in it. Which account it belongs to is the path, when it was declared is
 * the application's clock, and which day a short month lands it on is worked out rather than sent —
 * a request that carried that answer would be a request that could carry the wrong one.
 */
record MonthlyIncomeRequest(String dayOfMonth, String amount) {
}
