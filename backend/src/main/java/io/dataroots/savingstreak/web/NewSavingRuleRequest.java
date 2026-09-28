package io.dataroots.savingstreak.web;

import java.util.List;

/**
 * What a customer fills in to leave a saving rule standing: what to call it, which of their current
 * accounts it takes the money from, what makes it move and on which day, and how much moves. Where
 * the money lands is the savings account in the path.
 *
 * <p>The figures and the days arrive as the text that was typed rather than as numbers already read
 * for us, for the reason a deposit's amount does: some of what this application has to say about a
 * figure is about the characters — "25,00" is a mistake somebody makes and deserves an answer about
 * the amount rather than about the request being unreadable — and text is the only form that still
 * has them.
 *
 * <p>The trigger and the kind of amount arrive as their words, and so does the day of the week.
 * Whether the characters name one of the three triggers, one of the two kinds or one of the seven
 * days is a question about the request and is answered in the controller; whether a rule saying
 * those things is one this application will keep is a rule, and it belongs to Automation.
 *
 * <p><strong>{@code split} is how what the rule moves is spread across the account's goals</strong>,
 * in the order the customer wrote it, with whole-percentage shares adding to a hundred. Leaving it
 * out — or sending an empty list — is the ordinary rule: what it moves lands unallocated, exactly as
 * a manual deposit does, and a rule is not forced to know about goals.
 *
 * <p>Both days and both figures travel in every request, and a rule keeps only the ones its kind
 * needs. A form that sent a floor along with a fixed amount is not refused for it — there is nothing
 * wrong with a field the rule has no use for — so a page can keep its boxes filled while somebody
 * makes their mind up.
 */
record NewSavingRuleRequest(String name, Long fromCurrentAccountId, String trigger, String dayOfWeek,
                            String dayOfMonth, String howMuchMoves, String amount, String floor,
                            List<SavingRuleSplitRequest> split) {
}
