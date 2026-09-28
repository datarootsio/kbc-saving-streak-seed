package io.dataroots.savingstreak.savingsgoals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A savings account carries a list of the things it is being saved towards, and opening one puts it
 * at the bottom of that list.
 *
 * <p>The order is the part worth asserting. A goal's name and target are what somebody typed handed
 * back, but its rank is this application's decision, and the rule it follows — last among the live
 * goals, no ties, no gaps — is what every later slice of this feature reads: what the weekly money is
 * spent on first, and which goal a reallocation suggests taking from. A list that quietly grew two
 * goals at rank 2 would still render, and would make every figure downstream of it a coin toss.
 *
 * <p>Its own account, opened by {@link AnAccountWithGoals}, because the run shares one database and
 * a rank order is only assertable on a list nothing else is adding to.
 */
class AGoalIsOpenedOnASavingsAccountApiTest extends ApiIntegrationTest {

    private AnAccountWithGoals account;

    @BeforeEach
    void anAccountWithNoGoalsOnItYet() {
        account = new AnAccountWithGoals(http, "opened");
        assertThat(account.goals())
                .describedAs("a freshly opened account has never been saved towards")
                .isEmpty();
    }

    /**
     * Everything the customer said, and the one thing the application decided. The list is asserted
     * as well as the answer, because a goal that came back looking right and was never written down
     * is exactly the failure a 201 on its own cannot rule out.
     */
    @Test
    void a_goal_comes_back_with_its_name_target_deadline_and_rank_and_appears_in_the_list() {
        LocalDate wantedBy = account.today().plusMonths(18);

        GoalView opened = account.add("House deposit", "25000.00", wantedBy);

        assertThat(opened.name()).isEqualTo("House deposit");
        assertThat(opened.target()).isEqualByComparingTo("25000.00");
        assertThat(opened.deadline()).isEqualTo(wantedBy);
        assertThat(opened.rank()).isEqualTo(1);
        assertThat(opened.state()).isEqualTo("LIVE");
        assertThat(opened.savingsAccountId()).isEqualTo(account.id());
        assertThat(opened.id()).isNotNull();

        assertThat(account.goals())
                .describedAs("a goal that was answered but not written down is the failure a 201 hides")
                .singleElement()
                .satisfies(goal -> {
                    assertThat(goal.id()).isEqualTo(opened.id());
                    assertThat(goal.name()).isEqualTo("House deposit");
                    assertThat(goal.target()).isEqualByComparingTo("25000.00");
                    assertThat(goal.deadline()).isEqualTo(wantedBy);
                    assertThat(goal.rank()).isEqualTo(1);
                });
    }

    /**
     * The ordinary case, and the one the feature is most emphatic about: an emergency fund genuinely
     * has a target and no date, and a deadline this application insisted on would be a date somebody
     * made up to get past a form.
     */
    @Test
    void a_goal_without_a_deadline_is_accepted_and_reports_no_deadline() {
        GoalView opened = account.add("Emergency fund", "3000.00", null);

        assertThat(opened.deadline()).isNull();
        assertThat(opened.target()).isEqualByComparingTo("3000.00");
        assertThat(account.goal(opened.id()).deadline())
                .describedAs("read back, and still with no day it is wanted by")
                .isNull();
    }

    /**
     * Last, because the customer said nothing about where it belongs. Guessing from the target or
     * the deadline would be this application deciding what matters most to somebody; reordering is
     * one call away and is how they say otherwise.
     */
    @Test
    void a_new_goal_is_ranked_last_among_the_accounts_live_goals() {
        GoalView first = account.add("Emergency fund", "3000.00", null);
        GoalView second = account.add("Holiday", "1200.00", null);
        GoalView third = account.add("New bike", "800.00", null);

        assertThat(first.rank()).isEqualTo(1);
        assertThat(second.rank()).isEqualTo(2);
        assertThat(third.rank()).isEqualTo(3);
        assertThat(AnAccountWithGoals.idsOf(account.goals()))
                .containsExactly(first.id(), second.id(), third.id());
    }

    /**
     * The order is strict and total: a run of 1..n with no two goals sharing a place. Asserted as the
     * whole list of ranks rather than by spot-checking a pair, because a tie or a gap anywhere in it
     * is the same broken order, and the only honest way to say "no ties, no gaps" is to read every
     * rank there is.
     */
    @Test
    void goals_are_listed_in_rank_order_and_no_two_live_goals_share_a_rank() {
        account.add("Emergency fund", "3000.00", null);
        account.add("Holiday", "1200.00", null);
        account.add("New bike", "800.00", null);
        account.add("Winter coat", "150.00", null);

        List<GoalView> listed = account.goals();

        assertThat(listed.stream().map(GoalView::rank))
                .describedAs("every live goal holds its own place, and the places run 1..n")
                .containsExactly(1, 2, 3, 4);
        assertThat(listed.stream().map(GoalView::target))
                .describedAs("every target comes back quoted to the cent, not as a float SQLite kept")
                .allSatisfy(target -> assertThat(target.scale()).isEqualTo(2));
        assertThat(listed.stream().map(GoalView::state)).allMatch("LIVE"::equals);
    }

    /**
     * A target is money and comes back written as money. SQLite has no decimal type and holds an
     * amount as a float, so an amount that has been through it arrives as 1500.0 unless somebody
     * quotes it back to the cent — and a page that printed it straight would show a number where a
     * customer expects an amount.
     */
    @Test
    void a_target_comes_back_quoted_to_the_cent() {
        GoalView opened = account.add("Camera", "1500", null);

        assertThat(opened.target()).isEqualByComparingTo(new BigDecimal("1500.00"));
        assertThat(account.goal(opened.id()).target().scale()).isEqualTo(2);
    }
}
