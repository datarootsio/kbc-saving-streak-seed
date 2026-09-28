package io.dataroots.savingstreak.seededhouseholds;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.BalancesView;
import io.dataroots.savingstreak.support.DepositView;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.SavingCapacityView;
import io.dataroots.savingstreak.support.SavingRuleView;
import io.dataroots.savingstreak.support.SimulationView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A reset demonstration database comes back with a household that already has a decision worth
 * simulating on it: weeks of saving behind her, a mark she has fallen below, two dated goals that
 * cannot both be paid for and a rule quietly filling the pot.
 *
 * <p>The seeded pair of households are what a trainer meets in the first minute, and the four
 * questions this feature exists to answer are only worth asking of somebody the answers can move.
 * Everything asserted below is the presence of one of those answers: a goal that will be missed for
 * a deadline to move, deposits of different ages for a withdrawal to draw down, a mark above the
 * balance for money to arrive and earn nothing against, and a run of secured weeks long enough that
 * a rate is lost when it ends rather than merely a habit.
 *
 * <p><strong>Two applications, deliberately</strong>, exactly as the households test beside this one
 * does and for the same reason: "the same figures on every reset" cannot be asserted from one
 * database, because every figure in it agrees with itself.
 *
 * <p><strong>And both on {@code dev,demo}</strong>, which is the pair of profiles
 * {@code spring-boot:run} uses and the only place the lived-in half of the seed exists — see
 * {@code AHouseholdWithADecisionToMake} for why a hundred other test classes must go on meeting an
 * Anke with no history at all. This is the class that keeps the demonstration honest in exchange.
 *
 * <p>Nothing here winds the clock and nothing here writes: a simulation is a question. So the order
 * the methods run in cannot matter, which is what lets one application answer all of them.
 */
class AHouseholdWithADecisionToMakeApiTest extends ApiIntegrationTest {

    /** The profiles a trainer's own application runs under, named once. */
    private static final String AS_A_TRAINER_RUNS_IT = "dev,demo";

    private static AnApplicationWithAClockToMove oneReset;
    private static AnApplicationWithAClockToMove anotherReset;

    @BeforeAll
    static void resetTheDemonstrationDatabaseTwice() {
        oneReset = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-decision-one"), AS_A_TRAINER_RUNS_IT);
        anotherReset = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-decision-another"),
                AS_A_TRAINER_RUNS_IT);
    }

    @AfterAll
    static void stopTheApplications() {
        if (oneReset != null) {
            oneReset.close();
        }
        if (anotherReset != null) {
            anotherReset.close();
        }
    }

    /**
     * Three deposits a week apart, a withdrawal that leaves her below her own best, and the run of
     * weeks that follows from them — none of which could have been written down, because all three
     * are derived from a ledger.
     */
    @Test
    void she_arrives_with_three_weeks_of_saving_and_a_mark_she_has_already_fallen_below() {
        long hers = oneReset.savingsAccountOf(ANKE);

        DepositView[] behindHer = oneReset.depositsInto(hers);
        BalancesView standing = oneReset.balancesOf(hers);

        assertThat(behindHer)
                .as("three deposits, because a withdrawal draws the oldest one down first and one "
                        + "deposit makes taking money out look like plain subtraction")
                .hasSize(3);
        assertThat(behindHer).extracting(DepositView::amount)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactlyInAnyOrder(new BigDecimal("400.00"), new BigDecimal("350.00"),
                        new BigDecimal("300.00"));
        assertThat(standing.moneyBalance()).isEqualByComparingTo("850.00");
        assertThat(standing.mostEverSaved())
                .as("above what the pot holds, which is the whole reason the seed takes money back "
                        + "out: below the mark, the next two hundred euros she saves earn nothing")
                .isEqualByComparingTo("1050.00");
        assertThat(standing.currentStreakWeeks())
                .as("a run long enough that a branch which stops for two months loses a rate and "
                        + "not only a habit")
                .isGreaterThanOrEqualTo(2);
        assertThat(standing.currentMultiplier()).isEqualByComparingTo("1.20");
    }

    /**
     * The seeded deposits really leave the everyday account, so the figure it is opened with is the
     * figure it must be left holding plus everything the history moves out of it. That arithmetic is
     * the one way this ticket could have moved a constant the whole test suite finds its accounts by.
     */
    @Test
    void her_everyday_account_is_still_left_holding_exactly_what_it_always_held() {
        assertThat(oneReset.theCurrentAccountOf(ANKE).balance()).isEqualByComparingTo("2480.00");
    }

    /**
     * The goals competing for a capacity that will not stretch to both: the one in front takes all
     * forty euros and still arrives late, and the one behind it is given nothing at all.
     */
    @Test
    void she_carries_a_goal_she_will_miss_and_a_second_that_never_arrives_at_all() {
        long hers = oneReset.savingsAccountOf(ANKE);

        SavingCapacityView saying = oneReset.savingCapacityOf(hers);
        List<GoalView> competing = oneReset.goalsOn(hers);

        assertThat(saying.weeklyCapacity()).isEqualByComparingTo("40.00");
        assertThat(saying.aWeekIsNotSecuredAtThisRate())
                .as("below the weekly minimum on purpose, so that adopting twenty-five more a week "
                        + "visibly crosses the line rather than merely raising a number")
                .isTrue();
        assertThat(competing).hasSize(2);
        GoalView inFront = competing.get(0);
        assertThat(inFront.rank()).isEqualTo(1);
        assertThat(inFront.status()).isEqualTo("OFF_TRACK");
        assertThat(inFront.weeklyAmount())
                .as("the deadline minimum eats the whole capacity, which is what starves the goal "
                        + "below it")
                .isEqualByComparingTo("40.00");
        assertThat(inFront.willBeReachedOn()).isAfter(inFront.deadline());
        GoalView behindIt = competing.get(1);
        assertThat(behindIt.rank()).isEqualTo(2);
        assertThat(behindIt.weeklyAmount()).isEqualByComparingTo("0.00");
        assertThat(behindIt.willBeReachedOn())
                .as("no arrival day at all, which is the day a moved deadline gives it — and the "
                        + "one thing no other kind of change in this feature can show")
                .isNull();
        assertThat(behindIt.deadline())
                .as("both deadlines well inside the twelve months a branch is folded over, so both "
                        + "are days a scenario may name")
                .isBefore(oneReset.theDateTheClockReads().plusYears(1));
    }

    /**
     * A rule standing on the account, drawing on an everyday account that has a salary arriving and
     * bills going out — without which a stop silences nothing and the rule moves nothing.
     */
    @Test
    void a_rule_stands_on_the_pot_drawing_on_a_salary_that_really_arrives() {
        long hers = oneReset.savingsAccountOf(ANKE);

        List<SavingRuleView> standing = oneReset.rulesOn(hers);

        assertThat(standing).hasSize(1);
        SavingRuleView sixtyAWeek = standing.get(0);
        assertThat(sixtyAWeek.trigger()).isEqualTo("WEEKLY");
        assertThat(sixtyAWeek.dayOfWeek()).isEqualTo(DayOfWeek.TUESDAY.name());
        assertThat(sixtyAWeek.howMuchMoves()).isEqualTo("A_FIXED_AMOUNT");
        assertThat(sixtyAWeek.amount()).isEqualByComparingTo("60.00");
        assertThat(oneReset.theCurrentAccountOf(ANKE).income().amount())
                .isEqualByComparingTo("2600.00");
        assertThat(oneReset.theCurrentAccountOf(ANKE).bills()).isNotEmpty();
    }

    /**
     * And the second pot left exactly as it was opened, so that a trainer who has adopted a branch on
     * one of them still holds an untouched one to ask the same question about again.
     */
    @Test
    void her_second_pot_is_left_pristine() {
        long theOtherOfHers = oneReset.otherSavingsAccountOf(ANKE);

        assertThat(oneReset.balancesOf(theOtherOfHers).moneyBalance()).isEqualByComparingTo("0.00");
        assertThat(oneReset.savingCapacityOf(theOtherOfHers).declared()).isFalse();
        assertThat(oneReset.goalsOn(theOtherOfHers)).isEmpty();
        assertThat(oneReset.balancesOf(theOtherOfHers).currentStreakWeeks())
                .as("the run is the customer's and not the pot's, so the untouched account reports "
                        + "the same three weeks the saving one does")
                .isEqualTo(oneReset.balancesOf(oneReset.savingsAccountOf(ANKE)).currentStreakWeeks());
    }

    /**
     * The branch nobody has to ask for already carries the event the whole feature is about: money
     * arriving and earning nothing, because those euros have been earned on once already.
     */
    @Test
    void the_year_already_under_way_shows_money_arriving_and_earning_nothing() {
        long hers = oneReset.savingsAccountOf(ANKE);

        SimulationView asked = oneReset.simulationOf(hers);

        BigDecimal earningNothing = asked.theYearAlreadyUnderWay().thingsThatHappen().stream()
                .filter(happens -> "MONEY_ARRIVES_AND_EARNS_NOTHING".equals(happens.kind()))
                .map(SimulationView.AThingThatHappensView::figure)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(earningNothing)
                .as("exactly the gap between what she holds and the most she has ever held: the "
                        + "rule climbs her back to her own mark before a euro earns again")
                .isEqualByComparingTo("200.00");
        assertThat(asked.theYearAlreadyUnderWay().thingsThatHappen())
                .extracting(SimulationView.AThingThatHappensView::kind)
                .as("both deadlines are missed at the rate she has declared, and the anniversaries "
                        + "of deposits made a fortnight before the window opened fall inside it")
                .contains("A_DEADLINE_IS_MISSED", "A_BONUS_IS_PAID");
    }

    /**
     * And the branch the problem statement is written about: five hundred out loses a week and
     * quietly reprices the anniversaries of the deposits it was drawn from.
     */
    @Test
    void taking_five_hundred_out_loses_a_week_and_pays_less_on_a_later_anniversary() {
        long hers = oneReset.savingsAccountOf(ANKE);
        LocalDate aMonthOn = oneReset.theDateTheClockReads().plusMonths(1);

        SimulationView asked = oneReset.simulationOf(hers, List.of(Map.of(
                "called", "Take five hundred out",
                "adjustments", List.of(Map.of("kind", "TAKE_MONEY_OUT", "amount", "500.00",
                        "on", aMonthOn.toString())))));

        SimulationView.HowAScenarioTurnsOutView takingItOut = asked.scenarios().get(1);
        assertThat(takingItOut.thingsThatHappen())
                .extracting(SimulationView.AThingThatHappensView::kind)
                .contains("A_WEEK_IS_LOST");
        assertThat(whatTheAnniversariesPayIn(takingItOut))
                .as("the deposits the five hundred was drawn from hold less, and a tenth of less is "
                        + "less — the part of a withdrawal nobody works out in their head")
                .isLessThan(whatTheAnniversariesPayIn(asked.theYearAlreadyUnderWay()));
    }

    /**
     * Declared and not generated: a second reset of a second database produces the identical
     * household, down to the points three deposits earned at three different rates.
     */
    @Test
    void a_second_reset_produces_the_identical_decision() {
        long hers = oneReset.savingsAccountOf(ANKE);
        long theSameAgain = anotherReset.savingsAccountOf(ANKE);

        BalancesView one = oneReset.balancesOf(hers);
        BalancesView another = anotherReset.balancesOf(theSameAgain);

        assertThat(another.moneyBalance()).isEqualByComparingTo(one.moneyBalance());
        assertThat(another.mostEverSaved()).isEqualByComparingTo(one.mostEverSaved());
        assertThat(another.pointsBalance()).isEqualTo(one.pointsBalance());
        assertThat(another.currentStreakWeeks()).isEqualTo(one.currentStreakWeeks());
        assertThat(asAReaderWouldSayThem(anotherReset.goalsOn(theSameAgain)))
                .isEqualTo(asAReaderWouldSayThem(oneReset.goalsOn(hers)));
    }

    /**
     * The goals as a person reads them, which is what two resets have to agree about. Not the
     * moments they were created at, which are two different real instants and are meant to be.
     */
    private static List<String> asAReaderWouldSayThem(List<GoalView> goals) {
        return goals.stream()
                .map(goal -> goal.name() + " wants " + goal.target() + " and is " + goal.status())
                .toList();
    }

    private static long whatTheAnniversariesPayIn(SimulationView.HowAScenarioTurnsOutView branch) {
        return branch.thingsThatHappen().stream()
                .filter(happens -> "A_BONUS_IS_PAID".equals(happens.kind()))
                .mapToLong(happens -> happens.figure().longValueExact())
                .sum();
    }
}
