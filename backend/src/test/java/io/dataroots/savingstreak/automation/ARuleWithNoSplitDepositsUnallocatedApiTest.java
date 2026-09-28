package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.support.AllocationsView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.RuleOccurrenceView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A rule with no split deposits unallocated, exactly as a manual deposit does — even on an account
 * that has goals.
 *
 * <p>User story 25, and the half of this feature that is easiest to break while adding the other
 * half: a rule is not forced to know about goals, and a split is something a customer asks for
 * rather than something that happens to them.
 *
 * <p><strong>The account has a goal, and that is the whole point of the test.</strong> A rule with no
 * split on an account with no goals would pass against an application that quietly spread every
 * deposit across whatever it found — there would be nothing to spread it across. The goal here is
 * live, is nowhere near its target, and is the obvious place a helpful defect would put the money.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: this test winds
 * the clock, and the file the rest of the run shares is not one to leave wound forward.
 */
class ARuleWithNoSplitDepositsUnallocatedApiTest extends ApiIntegrationTest {

    /** The name a trainer types into the development jobs endpoint. */
    private static final String THE_JOB = "fireSavingRulesDue";

    private static final String WHAT_MOVES = "50.00";

    /** Three days off, so the run before the rule's day is plainly not its day. */
    private static final int DAYS_UNTIL_IT_MOVES = 3;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWithAGoalNobodyAskedTheRuleToFill() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-rule-with-no-split"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_money_lands_in_the_account_and_no_goal_claims_any_of_it() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        GoalView house = app.openAGoal(savingsAccount, "House", "5000.00");
        LocalDate theDayItMovesOn = app.theDateTheClockReads().plusDays(DAYS_UNTIL_IT_MOVES);

        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(app.currentAccountOf(ANKE),
                        "Fifty a week", theDayItMovesOn.getDayOfWeek().name(), WHAT_MOVES));
        assertThat(rule.split())
                .as("nobody asked for a split, so the rule carries none")
                .isEmpty();

        AllocationsView before = app.allocationsOn(savingsAccount);
        app.daysPass(DAYS_UNTIL_IT_MOVES);
        app.runJob(THE_JOB);

        AllocationsView after = app.allocationsOn(savingsAccount);
        assertThat(after.balance())
                .as("the money moved, like any other deposit")
                .isEqualByComparingTo(before.balance().add(new BigDecimal(WHAT_MOVES)));
        assertThat(after.goal(house.id()).allocation())
                .as("and the goal that was sitting there claims none of it, because a rule is not "
                        + "forced to know about goals")
                .isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(after.unallocated())
                .as("all of it is unallocated, exactly as it would be after a manual deposit")
                .isEqualByComparingTo(before.unallocated().add(new BigDecimal(WHAT_MOVES)));

        List<RuleOccurrenceView> history = app.historyOf(savingsAccount, rule.id());
        assertThat(history).hasSize(1);
        RuleOccurrenceView occurrence = history.get(0);
        assertThat(occurrence.intoGoals())
                .as("no goal received anything, so the occurrence names none")
                .isEmpty();
        assertThat(occurrence.leftUnallocated())
                .as("and the whole of what it moved is readable back as having been left "
                        + "unallocated, which is where it actually is")
                .isEqualByComparingTo(new BigDecimal(WHAT_MOVES));
    }
}
