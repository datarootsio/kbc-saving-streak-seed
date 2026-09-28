package io.dataroots.savingstreak.challenges;

import java.util.List;

import io.dataroots.savingstreak.support.AchievementView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.ChallengeView;
import io.dataroots.savingstreak.support.GoalView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A challenge for finishing the savings goals you set yourself, in which every goal counts once and
 * the goals you had already finished before you took it on count for nothing.
 *
 * <p><strong>Once, by the goal's own identity.</strong> A goal is finished when what is allocated to
 * it has reached its target, which is a comparison rather than an event — so a customer can free the
 * money out of a finished goal and put it straight back in, and the goals ledger will say
 * "completed" both times. The rule this class is named after is that the challenge is not fooled by
 * that: the goal is the thing that counts, not the number of times it has stood at its target.
 *
 * <p><strong>And counted from the moment you enrol.</strong> A goal that had already reached its
 * target when the customer took the challenge on is not something they did since, and no challenge
 * in this feature looks backwards. The sharp case is the one where nobody reads the tab until after
 * that goal has been emptied and filled again: the verdict has to come from the goal's dated money
 * moves rather than from whatever the first pass happens to find, or the answer would depend on when
 * somebody looked.
 *
 * <p>Everything here is asserted through the challenges tab and the trophy case, which is all a
 * customer can see. Nothing reaches for a sighting row, and nothing in this class knows that the
 * challenge stores anything at all — the claims are about readings, badges and points.
 *
 * <p>Its own application on a database nothing has ever been written to, because every figure here
 * is a count of goals and the shared database arrives carrying whatever goals the tests that ran
 * first opened.
 */
class GoalsFinishedCountOnceApiTest extends ApiIntegrationTest {

    private static final String FINISH_YOUR_GOALS = "FINISH_YOUR_GOALS";

    /** What the seed asks for at each rung, and what it pays. */
    private static final long BRONZE_PAYS = 40;
    private static final long SILVER_PAYS = 120;
    private static final long GOLD_PAYS = 300;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-challenges-goals-finished"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * One goal, finished, emptied and finished again: one all the way through.
     *
     * <p>The middle step is the one that matters. Between the two sightings the goal is not complete
     * at all — it has been emptied — so a challenge that counted "goals standing at their target" or
     * "times a goal arrived" would answer two by the end of this. It answers one, because what it
     * counts is goals.
     */
    @Test
    void a_goal_finished_emptied_and_finished_again_counts_once() {
        String customer = app.aCustomerOfItsOwn("a-goal-finished-twice");
        long savings = app.savingsAccountOf(customer);
        app.deposit(savings, customer, "300.00");
        app.enrolIn(customer, FINISH_YOUR_GOALS);

        assertThat(app.challengeOf(customer, FINISH_YOUR_GOALS).reading())
                .as("nothing has been finished since they joined, so the reading is none")
                .isEqualByComparingTo("0");

        GoalView coat = app.openAGoal(savings, "A winter coat", "100.00");
        app.allocate(savings, coat.id(), "100.00");
        assertThat(app.goalsOn(savings)).first()
                .as("the goal really has arrived, so there is something to count")
                .extracting(GoalView::status).isEqualTo("COMPLETED");
        assertThat(app.challengeOf(customer, FINISH_YOUR_GOALS).reading())
                .as("one goal finished since they joined")
                .isEqualByComparingTo("1");

        app.freeFromAGoal(savings, coat.id(), "100.00");
        assertThat(app.challengeOf(customer, FINISH_YOUR_GOALS).reading())
                .as("taking the money back out does not un-finish what they already did")
                .isEqualByComparingTo("1");

        app.allocate(savings, coat.id(), "100.00");
        assertThat(app.challengeOf(customer, FINISH_YOUR_GOALS).reading())
                .as("and filling the same goal again is the same goal, not a second one")
                .isEqualByComparingTo("1");
        assertThat(app.achievementsOf(customer))
                .as("bronze, once, for the one goal they have finished")
                .extracting(AchievementView::rung)
                .containsExactly("BRONZE");
    }

    /** Two different goals are two different things finished, and they count twice. */
    @Test
    void two_different_goals_finished_count_twice() {
        String customer = app.aCustomerOfItsOwn("two-goals-finished");
        long savings = app.savingsAccountOf(customer);
        app.deposit(savings, customer, "300.00");
        app.enrolIn(customer, FINISH_YOUR_GOALS);

        GoalView coat = app.openAGoal(savings, "A winter coat", "100.00");
        app.allocate(savings, coat.id(), "100.00");
        assertThat(app.challengeOf(customer, FINISH_YOUR_GOALS).reading()).isEqualByComparingTo("1");

        GoalView bike = app.openAGoal(savings, "A new bike", "150.00");
        app.allocate(savings, bike.id(), "150.00");
        assertThat(app.challengeOf(customer, FINISH_YOUR_GOALS).reading())
                .as("a second goal is a second thing finished")
                .isEqualByComparingTo("2");
    }

    /**
     * A goal that had already arrived when they took the challenge on counts for nothing, and the
     * goals they finish afterwards still count.
     */
    @Test
    void a_goal_already_finished_when_they_enrolled_does_not_count() {
        String customer = app.aCustomerOfItsOwn("a-goal-finished-before-enrolling");
        long savings = app.savingsAccountOf(customer);
        app.deposit(savings, customer, "300.00");

        GoalView boughtAlready = app.openAGoal(savings, "A coat, already bought", "50.00");
        app.allocate(savings, boughtAlready.id(), "50.00");

        app.enrolIn(customer, FINISH_YOUR_GOALS);
        assertThat(app.challengeOf(customer, FINISH_YOUR_GOALS).reading())
                .as("enrolling counts from the moment you enrol; what was already done was already "
                        + "done")
                .isEqualByComparingTo("0");
        assertThat(app.achievementsOf(customer))
                .as("and nothing is paid for a past this challenge never looked at")
                .isEmpty();

        GoalView bike = app.openAGoal(savings, "A new bike", "150.00");
        app.allocate(savings, bike.id(), "150.00");
        assertThat(app.challengeOf(customer, FINISH_YOUR_GOALS).reading())
                .as("the one they finished since counts, and only that one")
                .isEqualByComparingTo("1");
    }

    /**
     * The same rule, with nobody looking in between — which is the case that says where the verdict
     * comes from.
     *
     * <p>Nothing reads the tab between the enrolment and the goal being emptied and filled again, so
     * the first time this challenge ever looks at that goal, the goal has just arrived. If the rule
     * were "whatever was complete the first time we looked does not count" it would answer nought
     * here for the wrong reason — and it would answer one for a customer who did the same thing in
     * the other order. The verdict is taken from the goal's own dated money moves, so the answer does
     * not depend on when anybody happened to open the tab.
     */
    @Test
    void a_goal_finished_before_enrolling_is_ruled_out_even_if_nobody_looks_until_after_it_is_refilled() {
        String customer = app.aCustomerOfItsOwn("a-goal-refilled-before-anybody-looked");
        long savings = app.savingsAccountOf(customer);
        app.deposit(savings, customer, "300.00");

        GoalView boughtAlready = app.openAGoal(savings, "A coat, already bought", "50.00");
        app.allocate(savings, boughtAlready.id(), "50.00");

        app.enrolIn(customer, FINISH_YOUR_GOALS);
        app.freeFromAGoal(savings, boughtAlready.id(), "50.00");
        app.allocate(savings, boughtAlready.id(), "50.00");

        assertThat(app.challengeOf(customer, FINISH_YOUR_GOALS).reading())
                .as("the first look of all, and it still knows the goal was finished before they "
                        + "joined")
                .isEqualByComparingTo("0");
        assertThat(app.achievementsOf(customer)).isEmpty();
    }

    /**
     * The rungs fill as the goals accumulate, and taking the challenge on again starts from nothing.
     *
     * <p>One narrative, because it is one: five goals climbed in three steps, and then the whole
     * thing again. The re-enrolment is the claim that a repeat is a real repeat — the five goals that
     * won gold were all finished before the second enrolment began, so they count for it exactly as
     * little as any other goal finished before an enrolment does, and the sixth goal is the second
     * round's first.
     */
    @Test
    void the_rungs_fill_as_goals_accumulate_and_a_second_time_round_starts_again_from_nothing() {
        String customer = app.aCustomerOfItsOwn("goals-filling-the-rungs");
        long savings = app.savingsAccountOf(customer);
        app.deposit(savings, customer, "400.00");
        app.enrolIn(customer, FINISH_YOUR_GOALS);

        finishAGoalOf(customer, savings, "A winter coat");
        long beforeBronze = app.pointsBalanceOf(customer);
        assertThat(app.achievementsOf(customer))
                .as("one goal is what bronze asks for")
                .extracting(AchievementView::rung).containsExactly("BRONZE");
        assertThat(app.pointsBalanceOf(customer) - beforeBronze)
                .as("and it pays what the card said it pays")
                .isEqualTo(BRONZE_PAYS);

        finishAGoalOf(customer, savings, "A new bike");
        finishAGoalOf(customer, savings, "A weekend away");
        long beforeSilver = app.pointsBalanceOf(customer);
        List<AchievementView> afterSilver = app.achievementsOf(customer);
        assertThat(afterSilver)
                .as("newest first, so the rung they have just reached is at the top")
                .extracting(AchievementView::rung).containsExactly("SILVER", "BRONZE");
        assertThat(afterSilver.get(0).reading())
                .as("silver was won at three goals, which is where they actually stood")
                .isEqualByComparingTo("3");
        assertThat(app.pointsBalanceOf(customer) - beforeSilver)
                .as("silver's points and nothing else — bronze is a mark on the same running figure")
                .isEqualTo(SILVER_PAYS);

        finishAGoalOf(customer, savings, "A winter coat for the dog");
        finishAGoalOf(customer, savings, "A rainy day");
        long beforeGold = app.pointsBalanceOf(customer);
        assertThat(app.achievementsOf(customer))
                .extracting(AchievementView::rung).containsExactly("GOLD", "SILVER", "BRONZE");
        assertThat(app.pointsBalanceOf(customer) - beforeGold).isEqualTo(GOLD_PAYS);

        ChallengeView finished = app.challengeOf(customer, FINISH_YOUR_GOALS);
        assertThat(finished.state())
                .as("the last rung is the end of it")
                .isEqualTo("COMPLETED");
        assertThat(finished.reading())
                .as("and the figure that finished it is the one it keeps")
                .isEqualByComparingTo("5");
        assertThat(finished.repeatable())
                .as("a flow kind, so it can be taken on again")
                .isTrue();

        app.enrolIn(customer, FINISH_YOUR_GOALS);
        assertThat(app.challengeOf(customer, FINISH_YOUR_GOALS).reading())
                .as("a second round asks for goals finished in the second round; the five that won "
                        + "gold were all finished before it started")
                .isEqualByComparingTo("0");

        finishAGoalOf(customer, savings, "A second winter coat");
        assertThat(app.challengeOf(customer, FINISH_YOUR_GOALS).reading())
                .as("and the first goal of the second round is its first")
                .isEqualByComparingTo("1");
        assertThat(app.achievementsOf(customer))
                .as("a second bronze, on a second enrolment, beside the three already won")
                .extracting(AchievementView::rung)
                .containsExactly("BRONZE", "GOLD", "SILVER", "BRONZE");
    }

    /**
     * A customer with two savings accounts finishes a goal on each, and the reading is two.
     *
     * <p>A goal belongs to an account and a challenge belongs to a person. If the reading were
     * account-scoped, which account somebody happened to open a goal on would be part of the rule,
     * and holding two accounts would be a way of splitting your saving into two challenges neither
     * of which you could finish.
     */
    @Test
    void the_reading_spans_every_savings_account_the_customer_holds() {
        long first = app.savingsAccountOf(ANKE);
        long second = app.otherSavingsAccountOf(ANKE);
        app.deposit(first, ANKE, "200.00");
        app.deposit(second, ANKE, "200.00");
        app.enrolIn(ANKE, FINISH_YOUR_GOALS);

        GoalView here = app.openAGoal(first, "A coat on the first account", "100.00");
        app.allocate(first, here.id(), "100.00");
        assertThat(app.challengeOf(ANKE, FINISH_YOUR_GOALS).reading()).isEqualByComparingTo("1");

        GoalView there = app.openAGoal(second, "A bike on the second account", "100.00");
        app.allocate(second, there.id(), "100.00");
        assertThat(app.challengeOf(ANKE, FINISH_YOUR_GOALS).reading())
                .as("both accounts are hers, so both goals are hers, so the challenge counts both")
                .isEqualByComparingTo("2");
    }

    /** Opens a goal of twenty euros on the account and fills it, which is one goal finished. */
    private void finishAGoalOf(String customer, long savingsAccountId, String name) {
        GoalView goal = app.openAGoal(savingsAccountId, name, "20.00");
        app.allocate(savingsAccountId, goal.id(), "20.00");
        assertThat(app.goalsOn(savingsAccountId))
                .as("the goal " + name + " of " + customer + " really did arrive")
                .anySatisfy(each -> {
                    assertThat(each.id()).isEqualTo(goal.id());
                    assertThat(each.status()).isEqualTo("COMPLETED");
                });
    }
}
