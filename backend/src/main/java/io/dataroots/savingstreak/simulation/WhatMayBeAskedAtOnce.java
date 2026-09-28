package io.dataroots.savingstreak.simulation;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * How much one question about the future is allowed to carry, and the three sentences that refuse a
 * question carrying more.
 *
 * <p><strong>Why there is a cap at all.</strong> Nothing in the fold gets slower gracefully: one
 * scenario of ten changes is three hundred and sixty-five mornings with ten questions asked of each
 * of them, and a request carrying a hundred scenarios of a thousand changes is a read endpoint that
 * folds for a minute and answers nobody. But the figures below are not chosen from a stopwatch, and
 * that is the point of them being here rather than in a configuration file: they are what the answer
 * is worth reading at.
 *
 * <p><strong>Four futures, because four is what can be compared.</strong> Four columns is what fits
 * side by side at 320px, which is the narrowest screen this application draws on, and it is about as
 * many arguments as a person can actually choose between at one go. A fifth column is not a
 * comparison that got better; it is a comparison somebody stopped reading. A customer with five
 * questions has two askings, and the do-nothing column is in both of them.
 *
 * <p><strong>Ten changes, because ten is past any real question.</strong> The decisions this feature
 * exists for are one and two changes deep — five hundred out and twenty-five a week back, two more
 * months on the car — and nobody weighing a pause needs eleven of them. Ten is far enough out that
 * no honest question meets it and near enough that a request cannot fold a thousand years of
 * adjustments.
 *
 * <p><strong>The shape of the question is judged before anything in it.</strong> All the counting
 * and the headings first, over every scenario, and only then each change put to its own rule. That
 * order is deliberate twice over: the counting needs no snapshot, so a question that was never going
 * to be answered is refused before a single module is read; and a column with no heading is refused
 * before a bad day inside another column, because a refusal that points at a scenario is no use to
 * somebody who has not named their scenarios.
 *
 * <p>Nothing here knows what a change is or what any kind of change objects to. It counts, it reads
 * a name, and it refuses in words naming the limit — because a customer told "that is too many" has
 * otherwise to find the number by trying.
 */
final class WhatMayBeAskedAtOnce {

    private static final Logger log = LoggerFactory.getLogger(WhatMayBeAskedAtOnce.class);

    /**
     * How many futures one asking may compare. Four: what fits side by side at 320px, and what a
     * person can actually choose between. The do-nothing future is not one of them — it is answered
     * always and is nobody's to ask for — so four asked is five columns drawn.
     */
    static final int AT_MOST_THIS_MANY_FUTURES_AT_ONCE = 4;

    /**
     * How many changes one future may be made of. Ten: past any question a customer actually has,
     * and short of a request that would fold a thousand years of adjustments.
     */
    static final int AT_MOST_THIS_MANY_CHANGES_IN_ONE_FUTURE = 10;

    private WhatMayBeAskedAtOnce() {
    }

    /**
     * Refuses the question outright when it carries more than can be compared, or a future nobody
     * gave a name to, and otherwise says nothing at all.
     *
     * <p>Asked before the present is read and long before anything is folded, for the reason the
     * class comment gives: none of these objections needs to know a thing about the account.
     *
     * @param savingsAccountId only so that the warning can be found again beside the rest of that
     *                         account's lines; no rule here is about the account
     * @param asked            the futures the customer typed, in their order
     */
    static void theQuestionIsOneThisApplicationWillTake(long savingsAccountId,
                                                        List<AScenarioToAskAbout> asked) {
        if (asked.size() > AT_MOST_THIS_MANY_FUTURES_AT_ONCE) {
            throw refused(savingsAccountId, null, "This question asks about " + asked.size()
                    + " futures at once, and at most " + AT_MOST_THIS_MANY_FUTURES_AT_ONCE
                    + " can be compared in one asking. Ask about fewer of them, or ask twice.");
        }
        for (int position = 1; position <= asked.size(); position++) {
            AScenarioToAskAbout scenario = asked.get(position - 1);
            // A column with no heading cannot be compared against the one beside it, and it is also
            // the one thing a refusal about any other column would have had to point at. Both
            // reasons say the same thing: this is judged first and named by where it was typed.
            if (scenario.called() == null || scenario.called().isBlank()) {
                throw refused(savingsAccountId, null, "The scenario at position " + position
                        + " in this question has no name, and a column with no heading cannot be "
                        + "compared against the one beside it. Give it the words you would use to "
                        + "choose between them.");
            }
            if (scenario.adjustments().size() > AT_MOST_THIS_MANY_CHANGES_IN_ONE_FUTURE) {
                throw refused(savingsAccountId, scenario.called(), "The scenario called \""
                        + scenario.called() + "\" is made of " + scenario.adjustments().size()
                        + " changes, and one scenario can be made of at most "
                        + AT_MOST_THIS_MANY_CHANGES_IN_ONE_FUTURE
                        + ". Ask about fewer changes, or split it into two scenarios.");
            }
        }
    }

    /**
     * Warns and hands back the refusal, so that every objection in this class is logged in one
     * wording and none of them can be thrown without a line saying why.
     */
    private static SimulationRefused refused(long savingsAccountId, String scenario, String reason) {
        log.warn("simulation rejected savingsAccountId={} scenario={} reason={}",
                savingsAccountId, scenario, reason);
        return scenario == null
                ? SimulationRefused.againstTheRules(reason)
                : SimulationRefused.aboutThatScenario(scenario, reason);
    }
}
