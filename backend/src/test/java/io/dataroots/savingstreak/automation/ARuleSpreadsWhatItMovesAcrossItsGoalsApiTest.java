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
import io.dataroots.savingstreak.support.SavingRuleView;
import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A rule with a split makes one deposit and then spreads it across the goals its holder named, in
 * the proportions they chose — to the cent, with nothing appearing and nothing vanishing.
 *
 * <p>User stories 21 and 22, and the "60/30/10 across three goals" of the brief.
 *
 * <p><strong>The amount is 100.01 rather than 100.00, and that is the whole point of it.</strong>
 * Sixty, thirty and ten percent of a hundred euros is exact arithmetic that any rounding rule gets
 * right. One cent more and the three exact figures are 60.006, 30.003 and 10.001, which floor to
 * 60.00, 30.00 and 10.00 — a hundred euros, with a cent left over that has to go somewhere. This
 * application says it goes to the share that was furthest past its cent, which is the sixty, and
 * that the allocations add up to exactly what was deposited. A cent that quietly vanished here would
 * be a balance and a goals page that stop agreeing, which is the one thing this feature must not do.
 *
 * <p><strong>Both halves of the sum are asserted from the account rather than from the rule.</strong>
 * What each goal is holding and what no goal has claimed are read off the allocations endpoint, which
 * derives what is unallocated as the balance less what the goals have claimed — so a split that
 * allocated too much or too little cannot satisfy this test by reporting itself accurately.
 *
 * <p>Its own application, for the reason {@link AnApplicationWithAClockToMove} gives: this test winds
 * the clock, and the file the rest of the run shares is not one to leave wound forward.
 */
class ARuleSpreadsWhatItMovesAcrossItsGoalsApiTest extends ApiIntegrationTest {

    /** The name a trainer types into the development jobs endpoint. */
    private static final String THE_JOB = "fireSavingRulesDue";

    /** A hundred euros and a cent: the smallest amount 60/30/10 cannot divide evenly. */
    private static final String WHAT_MOVES = "100.01";

    /** Far enough off that no goal here is anywhere near full, which is a different test's subject. */
    private static final String A_TARGET_NOTHING_HERE_REACHES = "5000.00";

    /** Three days off, so that the run before the rule's day is plainly not its day. */
    private static final int DAYS_UNTIL_IT_MOVES = 3;

    private static AnApplicationWithAClockToMove app;

    @BeforeAll
    static void startAnApplicationWhoseGoalsThisTestFills() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-rule-spreads-what-it-moves"));
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @Test
    void the_deposit_is_spread_sixty_thirty_ten_and_the_cents_add_up_to_exactly_what_moved() {
        long savingsAccount = app.savingsAccountOf(ANKE);
        GoalView house = app.openAGoal(savingsAccount, "House", A_TARGET_NOTHING_HERE_REACHES);
        GoalView car = app.openAGoal(savingsAccount, "Car", A_TARGET_NOTHING_HERE_REACHES);
        GoalView holiday = app.openAGoal(savingsAccount, "Holiday", A_TARGET_NOTHING_HERE_REACHES);
        LocalDate theDayItMovesOn = app.theDateTheClockReads().plusDays(DAYS_UNTIL_IT_MOVES);

        SavingRuleView rule = app.leaveARuleStanding(savingsAccount,
                RulesAsSomebodyWouldTypeThem.spreadAcross(
                        RulesAsSomebodyWouldTypeThem.aFixedAmountEveryWeek(
                                app.currentAccountOf(ANKE), "Three at once",
                                theDayItMovesOn.getDayOfWeek().name(), WHAT_MOVES),
                        RulesAsSomebodyWouldTypeThem.inTurn(
                                RulesAsSomebodyWouldTypeThem.aShareFor(house.id(), "60"),
                                RulesAsSomebodyWouldTypeThem.aShareFor(car.id(), "30"),
                                RulesAsSomebodyWouldTypeThem.aShareFor(holiday.id(), "10"))));

        assertThat(rule.split())
                .as("the rule carries the split its holder wrote, in their order, and reads it back")
                .extracting("goalId", "share")
                .containsExactly(
                        Tuple.tuple(house.id(), 60),
                        Tuple.tuple(car.id(), 30),
                        Tuple.tuple(holiday.id(), 10));

        AllocationsView before = app.allocationsOn(savingsAccount);
        assertThat(before.unallocated())
                .as("nothing has been saved into this account yet, so no goal is holding anything")
                .isEqualByComparingTo(new BigDecimal("0.00"));

        app.daysPass(DAYS_UNTIL_IT_MOVES);
        app.runJob(THE_JOB);

        AllocationsView after = app.allocationsOn(savingsAccount);
        assertThat(after.balance())
                .as("one deposit landed, and it is the whole of what the rule moves")
                .isEqualByComparingTo(before.balance().add(new BigDecimal(WHAT_MOVES)));
        assertThat(after.goal(house.id()).allocation())
                .as("sixty percent of 100.01 is 60.006, and the leftover cent goes to the share "
                        + "that was furthest past its cent — which is this one")
                .isEqualByComparingTo(new BigDecimal("60.01"));
        assertThat(after.goal(car.id()).allocation())
                .isEqualByComparingTo(new BigDecimal("30.00"));
        assertThat(after.goal(holiday.id()).allocation())
                .isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(after.allocated())
                .as("the three shares add up to exactly what was deposited — a cent that appeared "
                        + "or vanished here is a balance and a goals page that stop agreeing")
                .isEqualByComparingTo(new BigDecimal(WHAT_MOVES));
        assertThat(after.unallocated())
                .as("and nothing of it was left over, because every goal could take its share")
                .isEqualByComparingTo(new BigDecimal("0.00"));
        assertThat(after.allocated().add(after.unallocated()))
                .as("the account's own sum still holds, which is the invariant the whole feature "
                        + "rests on")
                .isEqualByComparingTo(after.balance());

        List<RuleOccurrenceView> history = app.historyOf(savingsAccount, rule.id());
        assertThat(history).hasSize(1);
        RuleOccurrenceView occurrence = history.get(0);
        assertThat(occurrence.outcome()).isEqualTo("MOVED");
        assertThat(occurrence.amount()).isEqualByComparingTo(new BigDecimal(WHAT_MOVES));
        assertThat(occurrence.intoGoals())
                .as("the occurrence says what each goal received, in the order the split offered "
                        + "it, so a customer can see where the money went")
                .extracting("goalId")
                .containsExactly(house.id(), car.id(), holiday.id());
        assertThat(addedUp(occurrence.intoGoals()).add(occurrence.leftUnallocated()))
                .as("what the goals got plus what was left over is exactly what was deposited, "
                        + "which is the promise the occurrence has to keep on its own")
                .isEqualByComparingTo(occurrence.amount());
        assertThat(occurrence.leftUnallocated())
                .as("nothing was left over: every goal took its whole share")
                .isEqualByComparingTo(new BigDecimal("0.00"));
    }

    private static BigDecimal addedUp(List<RuleAllocationView> intoGoals) {
        return intoGoals.stream()
                .map(RuleAllocationView::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
