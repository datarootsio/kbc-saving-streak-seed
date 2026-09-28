package io.dataroots.savingstreak.savingsgoals;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a goal says it is for can be changed, and the list then says the new thing.
 *
 * <p>Each of the three is changed on its own, and each test asserts that the other two are where they
 * were. That is the whole risk in a patch: a handler that read the request as a replacement would
 * pass a test that only checked the field it sent, and would silently blank the other two.
 *
 * <p>The list is read back every time rather than only the answer, for the reason opening a goal
 * asserts the list: an answer composed in the handler out of what arrived would agree with the
 * request whatever the database did.
 */
class AGoalIsRenamedRetargetedAndRescheduledApiTest extends ApiIntegrationTest {

    private AnAccountWithGoals account;
    private GoalView goal;
    private LocalDate originalDeadline;

    @BeforeEach
    void aGoalWithAllThreeThingsFilledIn() {
        account = new AnAccountWithGoals(http, "changed");
        originalDeadline = account.today().plusMonths(12);
        goal = account.add("Holiday", "1200.00", originalDeadline);
    }

    @Test
    void a_goals_name_can_be_changed_and_the_list_reports_the_new_one() {
        GoalView changed = account.change(goal.id(), Map.of("name", "Honeymoon"));

        assertThat(changed.name()).isEqualTo("Honeymoon");
        assertThat(changed.target()).isEqualByComparingTo("1200.00");
        assertThat(changed.deadline()).isEqualTo(originalDeadline);
        assertThat(theOnlyGoal().name()).isEqualTo("Honeymoon");
    }

    @Test
    void a_goals_target_can_be_changed_and_the_list_reports_the_new_one() {
        GoalView changed = account.change(goal.id(), Map.of("target", "1750.50"));

        assertThat(changed.target()).isEqualByComparingTo("1750.50");
        assertThat(changed.name()).isEqualTo("Holiday");
        assertThat(changed.deadline()).isEqualTo(originalDeadline);
        assertThat(theOnlyGoal().target()).isEqualByComparingTo("1750.50");
    }

    @Test
    void a_goals_deadline_can_be_changed_and_the_list_reports_the_new_one() {
        LocalDate later = originalDeadline.plusMonths(3);

        GoalView changed = account.change(goal.id(), Map.of("deadline", later.toString()));

        assertThat(changed.deadline()).isEqualTo(later);
        assertThat(changed.name()).isEqualTo("Holiday");
        assertThat(changed.target()).isEqualByComparingTo("1200.00");
        assertThat(theOnlyGoal().deadline()).isEqualTo(later);
    }

    /**
     * All three at once, because a page with one form and three boxes sends all three, and a handler
     * that could only take them one at a time would make that page three requests and two of them
     * failures waiting to happen.
     */
    @Test
    void all_three_can_be_changed_in_one_call() {
        LocalDate later = originalDeadline.plusYears(1);

        GoalView changed = account.change(goal.id(), Map.of(
                "name", "Sabbatical", "target", "9000.00", "deadline", later.toString()));

        assertThat(changed.name()).isEqualTo("Sabbatical");
        assertThat(changed.target()).isEqualByComparingTo("9000.00");
        assertThat(changed.deadline()).isEqualTo(later);
    }

    /**
     * The one asymmetry in the contract, and the reason it is worth a test of its own: a deadline
     * nobody mentioned leaves the goal's alone, and an empty one says there is no longer a day it is
     * wanted by. A customer who typed a date by mistake has no other way to take it back, and a
     * deadline is optional precisely so that having none is an answer.
     */
    @Test
    void a_deadline_can_be_taken_off_a_goal_by_clearing_it() {
        GoalView changed = account.change(goal.id(), Map.of("deadline", ""));

        assertThat(changed.deadline()).isNull();
        assertThat(theOnlyGoal().deadline()).isNull();
    }

    /**
     * The other half of the same asymmetry. A page renaming a goal sends a name and nothing else, and
     * a handler that read the missing deadline as "remove it" would quietly throw away the date every
     * time somebody fixed a typo.
     */
    @Test
    void a_change_that_says_nothing_about_the_deadline_leaves_it_alone() {
        Map<String, Object> onlyTheName = new HashMap<>();
        onlyTheName.put("name", "Holiday in Italy");

        GoalView changed = account.change(goal.id(), onlyTheName);

        assertThat(changed.deadline())
                .describedAs("absent is not the same instruction as empty")
                .isEqualTo(originalDeadline);
    }

    /**
     * A deadline is soft, and nothing in this feature closes a goal because a date passed — so a goal
     * whose day has gone by is still a goal somebody can rename. The rule about days already gone is
     * about a deadline being <em>set</em>, and re-applying it to one nobody touched would make an
     * ordinary rename impossible on exactly the goals most likely to need one.
     */
    @Test
    void a_goal_whose_deadline_has_passed_can_still_be_renamed() {
        // Set while the day was still to come, and read back from a clock this test then leaves
        // alone: the only way to hold a past deadline without asking the application to accept one.
        GoalView wantedByToday = account.add("Concert tickets", "120.00", account.today());

        GoalView changed = account.change(wantedByToday.id(), Map.of("name", "Festival tickets"));

        assertThat(changed.name()).isEqualTo("Festival tickets");
        assertThat(changed.deadline()).isEqualTo(wantedByToday.deadline());
    }

    private GoalView theOnlyGoal() {
        return account.goal(goal.id());
    }
}
