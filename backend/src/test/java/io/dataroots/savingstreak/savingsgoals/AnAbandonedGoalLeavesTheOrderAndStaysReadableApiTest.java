package io.dataroots.savingstreak.savingsgoals;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.savingsgoals.AnAccountWithGoals.idsOf;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Giving up on a goal closes it rather than deleting it: it leaves the list of things being saved
 * towards, gives up its place in the order, and keeps what it was for.
 *
 * <p>Closed rather than deleted for the reason a points batch records its expiry instead of being
 * emptied — what somebody was saving for is a fact about them, and a row that is gone cannot be
 * asked about. So the assertion that matters here is not that the goal disappeared; it is that it is
 * still readable afterwards, name and target intact, and that it is no longer standing in a
 * competition it left.
 *
 * <p>The renumbering is the other half. The order is strict, so a run of 1, 2, 4 is a hole nothing in
 * this application knows how to read, and the goals below the one that left have to close it.
 */
class AnAbandonedGoalLeavesTheOrderAndStaysReadableApiTest extends ApiIntegrationTest {

    private AnAccountWithGoals account;
    private GoalView emergencyFund;
    private GoalView holiday;
    private GoalView bike;

    @BeforeEach
    void threeGoalsOneOfWhichIsAboutToBeGivenUpOn() {
        account = new AnAccountWithGoals(http, "abandoned");
        emergencyFund = account.add("Emergency fund", "3000.00", null);
        holiday = account.add("Holiday", "1200.00", null);
        bike = account.add("New bike", "800.00", null);
    }

    @Test
    void an_abandoned_goal_leaves_the_live_list_keeps_its_name_and_target_and_holds_no_rank() {
        GoalView givenUpOn = account.abandon(holiday.id());

        assertThat(givenUpOn.state()).isEqualTo("ABANDONED");
        assertThat(givenUpOn.rank())
                .describedAs("a goal that left the order holds no place in it")
                .isNull();
        assertThat(givenUpOn.abandonedAt()).isNotNull();

        assertThat(idsOf(account.goals()))
                .describedAs("it is no longer one of the things this account is saving towards")
                .containsExactly(emergencyFund.id(), bike.id());

        GoalView readBack = account.goal(holiday.id());
        assertThat(readBack.name())
                .describedAs("what was being saved for is still readable afterwards")
                .isEqualTo("Holiday");
        assertThat(readBack.target()).isEqualByComparingTo("1200.00");
        assertThat(readBack.rank()).isNull();
        assertThat(readBack.state()).isEqualTo("ABANDONED");
    }

    /** And it is in the list of what was given up on, which is where somebody would go looking. */
    @Test
    void an_abandoned_goal_is_listed_among_what_was_given_up_on() {
        account.abandon(holiday.id());

        assertThat(idsOf(account.abandonedGoals())).containsExactly(holiday.id());
        assertThat(account.abandonedGoals())
                .singleElement()
                .satisfies(goal -> assertThat(goal.name()).isEqualTo("Holiday"));
    }

    /**
     * The order closes up behind it. A run of 1, 2, 4 would be a strict order with a hole in it, and
     * every later slice of this feature reads the ranks as the sequence money is spent in.
     */
    @Test
    void the_goals_below_the_one_that_left_close_the_gap() {
        account.abandon(holiday.id());

        assertThat(account.goals().stream().map(GoalView::rank)).containsExactly(1, 2);
        assertThat(account.goal(bike.id()).rank())
                .describedAs("the bike was third of three and is now second of two")
                .isEqualTo(2);
    }

    /**
     * Giving up on the most important goal promotes everything under it, which is what "it no longer
     * matters" means. Asserted separately from the middle case because the two are different holes:
     * one leaves the run starting at 2, and nothing renumbering only what is below a departure would
     * catch it.
     */
    @Test
    void giving_up_on_the_most_important_goal_promotes_the_rest() {
        account.abandon(emergencyFund.id());

        assertThat(idsOf(account.goals())).containsExactly(holiday.id(), bike.id());
        assertThat(account.goals().stream().map(GoalView::rank)).containsExactly(1, 2);
    }

    /**
     * A goal opened after one was given up on is ranked last among what is live — not last among
     * everything that ever existed. Abandoned goals hold no rank, so counting them would leave a gap
     * at the bottom of the order that nothing would ever fill.
     */
    @Test
    void a_goal_opened_after_an_abandonment_is_ranked_last_among_what_is_live() {
        account.abandon(holiday.id());

        GoalView opened = account.add("Winter coat", "150.00", null);

        assertThat(opened.rank()).isEqualTo(3);
        assertThat(account.goals().stream().map(GoalView::rank)).containsExactly(1, 2, 3);
    }

    /** With every goal given up on, the account is saving towards nothing and says so. */
    @Test
    void an_account_whose_goals_were_all_given_up_on_has_an_empty_live_list() {
        account.abandon(emergencyFund.id());
        account.abandon(holiday.id());
        account.abandon(bike.id());

        assertThat(account.goals()).isEmpty();
        assertThat(idsOf(account.abandonedGoals()))
                .containsExactlyInAnyOrder(emergencyFund.id(), holiday.id(), bike.id());
    }
}
