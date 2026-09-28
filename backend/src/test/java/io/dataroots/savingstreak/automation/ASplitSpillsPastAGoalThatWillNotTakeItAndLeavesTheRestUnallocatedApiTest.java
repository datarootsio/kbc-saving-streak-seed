package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.support.AllocationsView;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.RuleAllocationView;
import io.dataroots.savingstreak.support.RuleOccurrenceView;
import io.dataroots.savingstreak.support.SavingRuleSplitView;
import io.dataroots.savingstreak.support.SavingRuleView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A share bigger than what its goal will take spills to the next goal in the split, what no goal in
 * the split will take is left unallocated — and the transfer itself is never refused for any of it.
 *
 * <p>User stories 23 and 24. This is the half of the split that a customer only ever meets once
 * their saving is going well: a goal nearly complete, a goal they gave up on, and a rule that still
 * has to move their money.
 *
 * <p><strong>Three ways of not taking a share, in one firing, because they are one thing.</strong>
 * The first goal still needs less than it is offered, the second is offered a share plus everything
 * the first would not take and still needs less than that, and the third was given up on after the
 * split was written and is not being saved towards at all. Each of them takes what it can and hands
 * the rest along; what comes out of the end of the split stays in the account with no goal's name on
 * it. A rule that refused instead would be rolling back a deposit because a goal was finished, which
 * is the thing this feature promises never to do.
 *
 * <p><strong>The transfer is asserted as having happened, not merely as not having failed.</strong>
 * The deposit is in the balance, the occurrence reads {@code MOVED} with the whole amount on it, and
 * the account's own sum — allocated plus unallocated equals the balance — still holds afterwards.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: this test winds
 * the clock, and the file the rest of the run shares is not one to leave wound forward.
 */
class ASplitSpillsPastAGoalThatWillNotTakeItAndLeavesTheRestUnallocatedApiTest extends ApiIntegrationTest {

    /** The name a trainer types into the development jobs endpoint. */
    private static final String THE_JOB = "fireSavingRulesDue";

    /** What the rule moves, chosen so that every share is far bigger than the goal it is offered to. */
    private static final String WHAT_MOVES = "100.00";

    /** Saved by hand first, so that the first goal in the split is genuinely nearly complete. */
    private static final String SAVED_BY_HAND = "15.00";

    /** Three days off, so the run before the rule's day is plainly not its day. */
    private static final int DAYS_UNTIL_IT_MOVES = 3;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseGoalsThisTestFills() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-split-spills-to-the-next-goal"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void every_share_no_goal_will_take_moves_along_the_split_and_what_is_left_stays_unallocated() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        // 20.00 wanted with 15.00 already in it, so this goal still needs 5.00 of the 50.00 it is
        // about to be offered.
        GoalView nearlyThere = app.openAGoal(savingsAccount, "Nearly there", "20.00");
        GoalView roomy = app.openAGoal(savingsAccount, "Roomy", "100.00");
        GoalView givenUpOn = app.openAGoal(savingsAccount, "Given up on", "5000.00");
        app.deposit(savingsAccount, ANKE, SAVED_BY_HAND);
        app.allocate(savingsAccount, nearlyThere.id(), SAVED_BY_HAND);

        LocalDate theDayItMovesOn = app.theDateTheClockReads().plusDays(DAYS_UNTIL_IT_MOVES);
        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.spreadAcross(
                        RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(
                                app.currentAccountOf(ANKE), "Fifty thirty twenty",
                                theDayItMovesOn.getDayOfWeek().name(), WHAT_MOVES),
                        RulesAsSomebodyWouldTypeThem.inTurn(
                                RulesAsSomebodyWouldTypeThem.aShareFor(nearlyThere.id(), "50"),
                                RulesAsSomebodyWouldTypeThem.aShareFor(roomy.id(), "30"),
                                RulesAsSomebodyWouldTypeThem.aShareFor(givenUpOn.id(), "20"))));

        // After the split was written, which is the point: a goal given up on afterwards does not
        // stop the rule firing, and nothing re-words the customer's instruction on their behalf.
        app.abandonGoal(savingsAccount, givenUpOn.id());
        assertThat(app.rulesOn(savingsAccount).get(0).split())
                .as("the abandoned goal is still named in the split, because taking it out would be "
                        + "quietly re-wording what the customer said")
                .extracting(SavingRuleSplitView::goalId)
                .containsExactly(nearlyThere.id(), roomy.id(), givenUpOn.id());

        AllocationsView before = app.allocationsOn(savingsAccount);
        app.daysPass(DAYS_UNTIL_IT_MOVES);
        app.runJob(THE_JOB);

        AllocationsView after = app.allocationsOn(savingsAccount);
        assertThat(after.balance())
                .as("the transfer happened: a goal that was full is not a reason to refuse a "
                        + "deposit that has already been asked for")
                .isEqualByComparingTo(before.balance().add(new BigDecimal(WHAT_MOVES)));
        assertThat(after.goal(nearlyThere.id()).allocation())
                .as("offered 50.00 and needing 5.00, it took the 5.00 and handed 45.00 along")
                .isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(after.goal(nearlyThere.id()).stillNeeded())
                .as("and is now complete rather than over-funded")
                .isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(after.goal(roomy.id()).allocation())
                .as("offered its own 30.00 plus the 45.00 the first goal would not take, and with "
                        + "room for all of it, it took 75.00 — which is the spill, and is the one "
                        + "figure that separates a split that spills from one that does not")
                .isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(after.allocated())
                .as("15.00 was already there, and the firing placed 5.00 and 75.00 of its 100.00")
                .isEqualByComparingTo(new BigDecimal("95.00"));
        assertThat(after.unallocated())
                .as("what no goal in the split would have is simply left unallocated — the money is "
                        + "in the account either way")
                .isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(after.allocated().add(after.unallocated()))
                .as("and the account's own sum still holds")
                .isEqualByComparingTo(after.balance());

        List<RuleOccurrenceView> history = app.historyOf(savingsAccount, rule.id());
        assertThat(history).hasSize(1);
        RuleOccurrenceView occurrence = history.get(0);
        assertThat(occurrence.outcome())
                .as("the split never refuses a transfer, so the occurrence is one that moved money")
                .isEqualTo("MOVED");
        assertThat(occurrence.amount()).isEqualByComparingTo(new BigDecimal(WHAT_MOVES));
        assertThat(occurrence.intoGoals())
                .as("only the two goals that actually took something are named, because nothing "
                        + "moved into the third and a 0.00 would claim it had")
                .extracting(RuleAllocationView::goalId)
                .containsExactly(nearlyThere.id(), roomy.id());
        assertThat(occurrence.intoGoals().get(0).amount())
                .as("the first goal took only what it still needed")
                .isEqualByComparingTo(new BigDecimal("5.00"));
        assertThat(occurrence.intoGoals().get(1).amount())
                .as("and the second took its own share plus everything that spilled onto it")
                .isEqualByComparingTo(new BigDecimal("75.00"));
        assertThat(occurrence.leftUnallocated())
                .as("and the abandoned goal's twenty percent, which came out of the end of the "
                        + "split with nowhere left to go, is readable back as left unallocated")
                .isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(addedUp(occurrence.intoGoals()).add(occurrence.leftUnallocated()))
                .as("what the goals got plus what was left over is exactly what was deposited")
                .isEqualByComparingTo(occurrence.amount());
    }

    private static BigDecimal addedUp(List<RuleAllocationView> intoGoals) {
        return intoGoals.stream()
                .map(RuleAllocationView::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
