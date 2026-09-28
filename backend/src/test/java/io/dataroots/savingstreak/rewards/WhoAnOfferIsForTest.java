package io.dataroots.savingstreak.rewards;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.rewards.WhoAnOfferIsFor.AnEligibilityRule.A_BADGE;
import static io.dataroots.savingstreak.rewards.WhoAnOfferIsFor.AnEligibilityRule.A_LIFETIME_OF_POINTS_EARNED;
import static io.dataroots.savingstreak.rewards.WhoAnOfferIsFor.AnEligibilityRule.A_STREAK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Who an offer is for, at the rule rather than over HTTP.
 *
 * <p><strong>The third test in this repo that is not an API test, and the ticket asks for it by
 * name.</strong> The first two are there because the only way to move time in this application is
 * a clock endpoint that advances in whole days, so no request a test can make lands on a
 * boundary. This one is there for a different reason and it is worth stating plainly: arranging a
 * customer who is on a run of exactly ten weeks, holds exactly one badge, and has earned exactly
 * five thousand points in all takes ten weeks of wound clock, an enrolment, a season, and a
 * deposit history — all at once, in one test, for every combination of three rules. The rule
 * itself is a function of two values, and a function of two values is covered by handing it two
 * values.
 *
 * <p><strong>What is asserted over HTTP instead is everything this cannot say.</strong> That the
 * verdict reaches a card, that the sentence is the same sentence the claim is refused with, that
 * nothing is spent, that crossing a threshold unlocks the offer with no other action, and that an
 * offer with no thresholds behaves exactly as it always has — every one of those is in
 * {@code anofferthatisnotforyou}, because every one of them is about the application and not
 * about the comparison. This class knows nothing about a claim, a card, the database or the web.
 *
 * <p>Which is the whole point of the arrangement it is testing: the rewards module names the
 * facts it needs in a record, is handed one, and decides. A module that went and fetched a streak
 * could not be asked this question at all without an application around it.
 */
class WhoAnOfferIsForTest {

    /** Somebody with nothing: no points, no history, no run of weeks and an empty trophy case. */
    private static final CustomerStanding NOBODY_IN_PARTICULAR =
            new CustomerStanding(0, 0, 0, Set.of());

    /**
     * An offer that asks for nothing is for everybody, including somebody with nothing.
     *
     * <p>The safety rail of the whole ticket, said at the rule. All four seeded offers are in
     * exactly this state, and if this answered anything but "nothing in the way" then every
     * reward the application has ever had would lock itself the day this shipped.
     */
    @Test
    void an_offer_that_asks_for_nothing_is_for_everybody() {
        WhoAnOfferIsFor anybody = new WhoAnOfferIsFor(null, null, null);

        assertThat(anybody.asksForNothing()).isTrue();
        assertThat(anybody.theFirstRuleNotMetBy(NOBODY_IN_PARTICULAR)).isEmpty();
        assertThat(anybody.theFirstRuleNotMetBy(new CustomerStanding(900, 9000, 30,
                Set.of("SAVE_EVERY_WEEK")))).isEmpty();
    }

    /**
     * A streak requirement is met at exactly the number asked for, and not one week below it.
     *
     * <p>Both sides of the fencepost, because an "at least" written as a "more than" is invisible
     * until the week somebody earns their way in and is still turned away — which is the one
     * failure a customer would never report, because they would assume they had miscounted.
     */
    @Test
    void a_streak_requirement_is_met_on_the_week_it_is_reached() {
        WhoAnOfferIsFor tenWeeks = new WhoAnOfferIsFor(10, null, null);

        assertThat(tenWeeks.theFirstRuleNotMetBy(standingOnAStreakOf(9))).contains(A_STREAK);
        assertThat(tenWeeks.theFirstRuleNotMetBy(standingOnAStreakOf(10))).isEmpty();
        assertThat(tenWeeks.theFirstRuleNotMetBy(standingOnAStreakOf(11))).isEmpty();
    }

    /** A badge is held or it is not; there is no nearly, which is why it has no boundary. */
    @Test
    void a_badge_requirement_is_met_by_holding_that_badge_and_by_nothing_else() {
        WhoAnOfferIsFor theBadge = new WhoAnOfferIsFor(null, "SAVE_EVERY_WEEK", null);

        assertThat(theBadge.theFirstRuleNotMetBy(NOBODY_IN_PARTICULAR)).contains(A_BADGE);
        assertThat(theBadge.theFirstRuleNotMetBy(holding("SOMETHING_ELSE"))).contains(A_BADGE);
        assertThat(theBadge.theFirstRuleNotMetBy(holding("SAVE_EVERY_WEEK"))).isEmpty();
        assertThat(theBadge.theFirstRuleNotMetBy(holding("SOMETHING_ELSE", "SAVE_EVERY_WEEK")))
                .as("a trophy case with other things in it is still a trophy case with this in it")
                .isEmpty();
    }

    /**
     * A lifetime requirement is met at exactly the figure asked for, and it is read off what was
     * earned rather than off what is left.
     *
     * <p>The second half is the one worth pinning. The standing carries both figures and they are
     * different numbers on purpose: somebody who has earned five thousand points and spent nearly
     * all of them still qualifies, because a lifetime is a record of behaviour. A rule reading
     * the balance would take the offer away from the customer the moment they used the scheme,
     * which is the opposite of what gating it on a lifetime is for.
     */
    @Test
    void a_lifetime_requirement_reads_what_was_earned_and_not_what_is_left() {
        WhoAnOfferIsFor fiveThousand = new WhoAnOfferIsFor(null, null, 5_000L);

        assertThat(fiveThousand.theFirstRuleNotMetBy(hasEarned(4_999, 4_999)))
                .contains(A_LIFETIME_OF_POINTS_EARNED);
        assertThat(fiveThousand.theFirstRuleNotMetBy(hasEarned(5_000, 5_000))).isEmpty();
        assertThat(fiveThousand.theFirstRuleNotMetBy(hasEarned(5_000, 3)))
                .as("earned five thousand and spent all but three: the lifetime is what counts")
                .isEmpty();
    }

    /**
     * Every threshold that is set has to be met, and meeting two of three is not meeting them.
     *
     * <p>ANDed, never ORed. An offer that unlocked on whichever rule was easiest would hand the
     * best reward in the catalogue to whoever satisfied the cheapest half of it, and the
     * administrator who wrote both wrote both.
     */
    @Test
    void every_threshold_that_is_set_has_to_be_met() {
        WhoAnOfferIsFor allThree = new WhoAnOfferIsFor(4, "SAVE_EVERY_WEEK", 1_000L);

        assertThat(allThree.theFirstRuleNotMetBy(
                new CustomerStanding(0, 1_000, 4, Set.of("SAVE_EVERY_WEEK"))))
                .as("all three met is nothing in the way")
                .isEmpty();
        assertThat(allThree.theFirstRuleNotMetBy(
                new CustomerStanding(0, 1_000, 4, Set.of())))
                .as("the badge alone is missing, and the badge alone is what is said")
                .contains(A_BADGE);
        assertThat(allThree.theFirstRuleNotMetBy(
                new CustomerStanding(0, 999, 4, Set.of("SAVE_EVERY_WEEK"))))
                .as("one point short of a lifetime is short of it")
                .contains(A_LIFETIME_OF_POINTS_EARNED);
        assertThat(allThree.asksForNothing()).isFalse();
    }

    /**
     * When several rules are unmet the customer is told about the first of them, in the declared
     * order: a streak, then a badge, then a lifetime.
     *
     * <p>The decision this record exists to make, asserted so that it cannot drift. The order is
     * fixed rather than "whichever they are closest to" because three weeks, one badge and eight
     * hundred points cannot be compared — there is no unit that holds all three — and it is this
     * order rather than another because it is the order the three sit in on the offer and on the
     * administration form, so that somebody reading the form top to bottom can say which sentence
     * their offer will show without running anything.
     */
    @Test
    void the_first_rule_in_the_declared_order_is_the_one_the_customer_is_told_about() {
        WhoAnOfferIsFor allThree = new WhoAnOfferIsFor(10, "SAVE_EVERY_WEEK", 5_000L);
        CustomerStanding meetsNone = new CustomerStanding(0, 0, 0, Set.of());

        assertThat(allThree.theFirstRuleNotMetBy(meetsNone)).contains(A_STREAK);
        assertThat(allThree.theFirstRuleNotMetBy(new CustomerStanding(0, 0, 10, Set.of())))
                .as("the streak satisfied, the badge is next")
                .contains(A_BADGE);
        assertThat(allThree.theFirstRuleNotMetBy(
                new CustomerStanding(0, 0, 10, Set.of("SAVE_EVERY_WEEK"))))
                .as("and then the lifetime, which is last")
                .contains(A_LIFETIME_OF_POINTS_EARNED);
    }

    /**
     * A standing is a reading of four figures that can only have come from a derivation, so a
     * figure that cannot be true is said out loud rather than compared against.
     *
     * <p>Nobody types any of these. A negative run of weeks or a balance larger than everything
     * ever earned is a mistake in whoever worked one out, and a rule that quietly compared
     * against it would lock or unlock an offer for a reason nobody could reconstruct afterwards.
     */
    @Test
    void a_standing_that_cannot_be_true_is_refused_where_it_is_made() {
        assertThatThrownBy(() -> new CustomerStanding(0, 0, -1, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("streakWeeks=-1");
        assertThatThrownBy(() -> new CustomerStanding(100, 10, 0, Set.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("more than what was ever");
    }

    /**
     * The standing handed in cannot be changed underneath the rule that read it.
     *
     * <p>Worth a line because the badges arrive as a collection somebody else built, and a rule
     * decided against a set that has since been added to is a decision nobody can reproduce from
     * the log line that recorded it.
     */
    @Test
    void the_badges_on_a_standing_are_the_ones_it_was_made_with() {
        Set<String> theirs = new java.util.HashSet<>(Set.of("SAVE_EVERY_WEEK"));
        CustomerStanding standing = new CustomerStanding(0, 0, 0, theirs);

        theirs.add("A_BADGE_WON_AFTERWARDS");

        assertThat(standing.badgesHeld()).containsExactly("SAVE_EVERY_WEEK");
        assertThat(new WhoAnOfferIsFor(null, "A_BADGE_WON_AFTERWARDS", null)
                .theFirstRuleNotMetBy(standing))
                .contains(A_BADGE);
    }

    /** Somebody on a run of this many weeks and nothing else to their name. */
    private static CustomerStanding standingOnAStreakOf(int weeks) {
        return new CustomerStanding(0, 0, weeks, Set.of());
    }

    /** Somebody holding these badges and nothing else. */
    private static CustomerStanding holding(String... badges) {
        return new CustomerStanding(0, 0, 0, Set.of(badges));
    }

    /** Somebody who has earned this much in all and has this much of it left. */
    private static CustomerStanding hasEarned(long lifetime, long left) {
        return new CustomerStanding(left, lifetime, 0, Set.of());
    }

    /** A reading with nothing in it, for the assertions that want to name the empty answer. */
    @Test
    void nothing_in_the_way_is_an_empty_answer_rather_than_a_null() {
        Optional<WhoAnOfferIsFor.AnEligibilityRule> nothing =
                new WhoAnOfferIsFor(null, null, null).theFirstRuleNotMetBy(NOBODY_IN_PARTICULAR);

        assertThat(nothing).isEmpty();
    }
}
