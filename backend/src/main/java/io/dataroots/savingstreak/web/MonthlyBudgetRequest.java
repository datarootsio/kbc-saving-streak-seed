package io.dataroots.savingstreak.web;

/**
 * What a customer sends to say what one of their categories is allowed to cost each month: the
 * figure, for the category in the path.
 *
 * <p>One field, and the same request whether or not the category already carries one. There is no
 * separate "change it": what the customer is saying is "from now on, this much", and whether they
 * had said anything before is the backend's business rather than theirs. That is why the address is
 * a PUT — the whole of a fact being put where it belongs — and why declaring a second figure
 * supersedes the first rather than being refused as a duplicate.
 *
 * <p>The amount arrives as the text that was typed rather than as a number, the way a bill's and a
 * spend's do. Whether "250,00" is a figure at all is a fact about the request and is answered in the
 * controller, in a sentence written for the person who typed it; whether the number describes a
 * budget this application will keep is a rule, and it belongs to Budgets.
 *
 * <p>It is allowed to be absent, and that is the point: a budget with no figure in it is a thing a
 * customer can send, and there is a sentence waiting for it. A record that could only hold a legal
 * budget would move the refusal into the web layer, which does not own it.
 *
 * <p><strong>The rollover rule travels with the amount rather than at a path of its own.</strong>
 * It sits on the budget because it is the budget that has a surplus, and the two are superseded by
 * one declaration — so a customer changing only their rule sends the figure they already have
 * beside it, and what the application records is one new row rather than an amount and a rule that
 * could take effect in different months.
 *
 * <p>The rule arrives as text for the reason the amount does: a word this application does not know
 * is a fact about the request, and the person who typed it deserves a sentence naming the three it
 * does know rather than whatever a deserialiser would have said about an enum constant. Absent is
 * not a refusal — it is the default, because somebody who has never thought about rollover has a
 * rule all the same — which is a thing only a nullable field can express.
 */
record MonthlyBudgetRequest(String amount, String rollover) {
}
