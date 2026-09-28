package io.dataroots.savingstreak.rewards;

import java.util.Optional;

/**
 * The rules an offer carries about who may claim it: a minimum run of weeks, a badge, and a
 * lifetime of points earned — any of them, all of them, or none.
 *
 * <p><strong>A closed vocabulary of three thresholds, and never an expression.</strong> The spec
 * rejects a predicate language, a stored rule tree and a JSON condition in one sentence and the
 * sentence is the argument: <em>a rule nobody can read back in the administration form is a rule
 * nobody can debug</em>. Three boxes on a form can be read back, printed, and turned into one
 * sentence a customer understands; an expression cannot be any of those three, and the first
 * person who has to explain to a customer why they are locked out would be reading somebody
 * else's grammar out loud. It is also what makes every refusal sayable — there are exactly three
 * sentences here and they are written where every other refusal in this module is written.
 *
 * <p><strong>All the thresholds that are set must be met.</strong> ANDed, never ORed, because a
 * rule is a floor and two floors do not make a choice: an administrator who wrote "ten weeks" and
 * "the badge" wants both, and an offer that unlocked on either would be giving the best reward in
 * the catalogue to whoever satisfied the easiest half of it. A null threshold is no rule at all
 * and is not a floor of nought — the same reading every other absent column on an offer gets, and
 * the reason all four seeded offers behave exactly as they always have.
 *
 * <p><strong>When several are unmet the customer is told about one, and it is the first one in the
 * order below.</strong> Not the one they are closest to, and this is the decision worth arguing.
 * "Three weeks short" and "one badge short" and "eight hundred points short" cannot be compared:
 * there is no unit that holds all three, and inventing one would be the arbitrary band the goals
 * spec refused and the rolling window this spec refused, for the same reason both times. So the
 * order is fixed and declared — a streak, then a badge, then a lifetime of points earned — and it
 * is the order the three sit in on the offer, in the administration form and in this record, so
 * that somebody reading the form top to bottom can say without running anything which sentence
 * their offer will show. A customer who satisfies the first is then told the second, which is the
 * same bargain the module's whole order of checks already makes: one instruction at a time,
 * always the same one, and it changes only when they have done something about it.
 *
 * <p>Package-private, and a record rather than three parameters passed about, because it is the
 * one thing in this module that is a pure function of its inputs: the whole point of naming the
 * facts in {@link CustomerStanding} is that deciding who an offer is for can then be exercised
 * without arranging a streak, a badge and a points history at the same time. Nothing here reads a
 * clock, a repository or another module.
 */
record WhoAnOfferIsFor(Integer minimumStreakWeeks, String requiresBadge,
                       Long minimumLifetimePointsEarned) {

    /**
     * The three things an offer may ask for, in the order a customer is told about them.
     *
     * <p>Values rather than sentences, so that the one place a sentence is written is beside every
     * other sentence this module says — and so that the log line naming which rule locked a card
     * says a word somebody can grep for rather than a paragraph that will be reworded.
     */
    enum AnEligibilityRule {

        /** The offer asks for a run of consecutive secured weeks, and theirs is shorter. */
        A_STREAK,

        /** The offer asks for a badge, and it is not in their trophy case. */
        A_BADGE,

        /** The offer asks for a lifetime of points earned, and they have not earned that many. */
        A_LIFETIME_OF_POINTS_EARNED
    }

    /** Whether this offer asks anything at all — which is what all four seeded offers answer. */
    boolean asksForNothing() {
        return minimumStreakWeeks == null && requiresBadge == null
                && minimumLifetimePointsEarned == null;
    }

    /**
     * The first rule this customer does not meet, or nothing at all when they meet every one that
     * is set.
     *
     * <p>A pure function of the thresholds above and the standing handed in: the same two inputs
     * give the same answer whoever asks, on whatever day, with nothing read and nothing written.
     * That is what makes "who an offer is for" a thing a test can exercise one rule at a time,
     * and it is the reason the standing is a record of four plain figures rather than a customer
     * identifier this would have to go and look things up with.
     *
     * <p>Each comparison is "at least", inclusive, for the reason the offer's closing day is
     * inclusive: an administrator who wrote ten weeks means a customer on ten weeks is in, and a
     * fencepost here would lock somebody out on the very week they earned their way in — the one
     * failure a customer would never think to report, because they would assume they had
     * miscounted.
     */
    Optional<AnEligibilityRule> theFirstRuleNotMetBy(CustomerStanding standing) {
        if (minimumStreakWeeks != null && standing.streakWeeks() < minimumStreakWeeks) {
            return Optional.of(AnEligibilityRule.A_STREAK);
        }
        if (requiresBadge != null && !standing.holds(requiresBadge)) {
            return Optional.of(AnEligibilityRule.A_BADGE);
        }
        if (minimumLifetimePointsEarned != null
                && standing.lifetimePointsEarned() < minimumLifetimePointsEarned) {
            return Optional.of(AnEligibilityRule.A_LIFETIME_OF_POINTS_EARNED);
        }
        return Optional.empty();
    }
}
