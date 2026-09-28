package io.dataroots.savingstreak.whatifididthisinstead;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.SimulationView;
import io.dataroots.savingstreak.support.SimulationView.AThingThatHappensView;
import io.dataroots.savingstreak.support.SimulationView.HowAScenarioTurnsOutView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * The days things happen on, asked for over HTTP and read back the way the frontend reads them.
 *
 * <p>The twelve rows say what a month came to; this list says what happened and when, which is what
 * a customer is actually deciding about. A month in which a goal arrives and a month in which
 * nothing arrives can carry the same balance, and the difference between them is a line on this
 * list rather than a figure in that column.
 *
 * <p>What is asserted here is what the seam owes a client: that the list is there, what a thing on
 * it is made of, that it comes back in an order nobody has to sort, and that the day a goal arrives
 * on is the day the goals screen itself names. The arithmetic behind each kind — a run of weeks
 * ending on its Sunday, a batch going on its own anniversary, money earning nothing at the
 * high-water mark — is asserted as plain arithmetic in
 * {@code TheNightIsReplayedOneDayAtATimeForTwelveMonthsTest}, because over HTTP most of those days
 * can only be reached by winding a clock the whole run shares.
 *
 * <p>Its own customer every time and every date counted off the application's clock rather than the
 * machine's, for the reasons {@link AnAccountWithAFutureToAskAbout} gives.
 */
class TheDaysThingsHappenOnApiTest extends ApiIntegrationTest {

    /** What a page reads to know a goal has arrived, and what a page reads to know one will not. */
    private static final String A_GOAL_IS_REACHED = "A_GOAL_IS_REACHED";
    private static final String A_DEADLINE_IS_MISSED = "A_DEADLINE_IS_MISSED";
    private static final String A_BONUS_IS_PAID = "A_BONUS_IS_PAID";
    private static final String MONEY_ARRIVES_AND_EARNS_NOTHING = "MONEY_ARRIVES_AND_EARNS_NOTHING";

    @Test
    void a_dated_thing_is_a_day_a_kind_and_a_figure_and_nothing_else() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "the shape of a thing");
        account.depositsByHand("300.00");

        JsonNode answered = http.postForObject("/api/savings-accounts/{id}/simulations",
                Map.of("scenarios", List.of()), JsonNode.class, account.id());
        JsonNode thing = answered.get("scenarios").get(0).get("thingsThatHappen").get(0);

        assertThat(fieldsOf(thing))
                .as("a day, a kind and a figure. No sentence, no goal and no wording: a page turns "
                        + "a kind into the words its own language file holds, and an event carrying "
                        + "prose would be the backend deciding what language a customer reads")
                .containsExactlyInAnyOrder("on", "kind", "figure");
    }

    @Test
    void the_things_that_happen_come_back_in_day_order_and_within_a_day_in_the_order_the_night_runs_them() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "ordered by day");
        LocalDate thisWeekStartsOn =
                SavingsWeek.containing(account.theDateTheClockReads()).startsOn();
        LocalDate aFortnightOfSavingOn = thisWeekStartsOn.plusWeeks(2);
        // Fifty euros a week, a hundred to find and a fortnight to find it in: the first goal takes
        // the whole capacity as its deadline minimum and arrives on exactly the Monday it was wanted
        // by. The second is wanted on the same day, is left nothing, and cannot arrive at all — so
        // one day carries two things and they are of different kinds.
        account.declaresAWeeklyCapacity("50.00");
        account.opensAGoal("A weekend away", "100.00", aFortnightOfSavingOn.toString());
        account.opensAGoal("A car", "5000.00", aFortnightOfSavingOn.toString());

        HowAScenarioTurnsOutView carryingOn = account.simulation().theYearAlreadyUnderWay();

        assertThat(carryingOn.thingsThatHappen())
                .as("a goal arriving and a deadline going by are both dated on that Monday, and the "
                        + "list is in the order the day runs them rather than in the order the goals "
                        + "were opened — which is the order the account's own bar already puts two "
                        + "markers sharing a day in, for the same reason")
                .extracting(AThingThatHappensView::on, AThingThatHappensView::kind)
                .containsExactly(
                        tuple(aFortnightOfSavingOn, A_GOAL_IS_REACHED),
                        tuple(aFortnightOfSavingOn, A_DEADLINE_IS_MISSED));
        assertThat(carryingOn.thingsThatHappen())
                .as("and the days never go backwards, which is what lets a page draw the list "
                        + "straight down without sorting it — and stops two clients sorting it two "
                        + "different ways")
                .isSortedAccordingTo((one, other) -> one.on().compareTo(other.on()));
    }

    @Test
    void a_goal_is_reached_on_the_day_the_goals_screen_itself_names() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "a goal arriving");
        account.declaresAWeeklyCapacity("100.00");
        // A deadline a long way off, so that the goal is plainly on track and nothing about this
        // test is about being late: a hundred a week finds four hundred in four weeks.
        GoalView opened = account.opensAGoal("A boat", "400.00",
                account.theDateTheClockReads().plusMonths(10).toString());

        HowAScenarioTurnsOutView carryingOn = account.simulation().theYearAlreadyUnderWay();
        GoalView asTheGoalsScreenReadsIt = account.goals().stream()
                .filter(goal -> goal.id().equals(opened.id()))
                .findFirst()
                .orElseThrow();

        assertThat(carryingOn.thingsOfKind(A_GOAL_IS_REACHED))
                .as("one goal, one arrival, dated on the Monday the money is all there by — and it "
                        + "is the very day the goals screen already names, because the branch asks "
                        + "the two functions that screen asks rather than working it out again")
                .singleElement()
                .satisfies(reached -> {
                    assertThat(reached.on()).isEqualTo(asTheGoalsScreenReadsIt.willBeReachedOn());
                    assertThat(reached.figure())
                            .as("carrying what the goal is for, which is the one figure of a goal "
                                    + "that is a fact rather than another projection")
                            .isEqualByComparingTo(asTheGoalsScreenReadsIt.target());
                });
    }

    @Test
    void a_deadline_the_branch_goes_past_is_reported_once_on_the_day_it_was_wanted_by() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "a deadline missed");
        LocalDate wantedBy = account.theDateTheClockReads().plusMonths(2);
        // Ten euros a week against five thousand: the goal is decades off and the day it is wanted
        // by is two months away.
        account.declaresAWeeklyCapacity("10.00");
        account.opensAGoal("A car", "5000.00", wantedBy.toString());

        HowAScenarioTurnsOutView carryingOn = account.simulation().theYearAlreadyUnderWay();

        assertThat(carryingOn.thingsOfKind(A_DEADLINE_IS_MISSED))
                .as("once, on the day it was wanted by, and not on every one of the three hundred "
                        + "days after it — a goal that is late stays late, and three hundred markers "
                        + "would be saying one thing three hundred times")
                .singleElement()
                .satisfies(missed -> {
                    assertThat(missed.on()).isEqualTo(wantedBy);
                    assertThat(missed.figure()).isEqualByComparingTo("5000.00");
                });
        assertThat(carryingOn.thingsOfKind(A_GOAL_IS_REACHED))
                .as("and nothing says it arrives, because at ten euros a week it does not arrive "
                        + "inside the year this answer covers")
                .isEmpty();
    }

    @Test
    void an_anniversary_worth_nothing_puts_nothing_on_the_list() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "an anniversary worth nothing");
        // Both land today, so both reach their first anniversary on the last day the window covers.
        // One is worth a tenth of three hundred whole euros; the other is worth a tenth of nine, and
        // a tenth of nine rounds away.
        account.depositsByHand("300.00");
        account.depositsByHand("9.99");

        SimulationView answered = account.simulation();
        HowAScenarioTurnsOutView carryingOn = answered.theYearAlreadyUnderWay();

        assertThat(carryingOn.thingsOfKind(A_BONUS_IS_PAID))
                .as("two deposits reach an anniversary inside the window and only one of them is a "
                        + "thing that happens: an anniversary worth nothing writes no row and puts "
                        + "no marker on the account's own bar, so it puts no line here either")
                .singleElement()
                .satisfies(bonus -> {
                    assertThat(bonus.on()).isEqualTo(answered.until());
                    assertThat(bonus.figure()).isEqualByComparingTo("30");
                });
    }

    @Test
    void money_climbing_back_to_the_high_water_mark_is_reported_as_earning_nothing() {
        AnAccountWithAFutureToAskAbout account =
                new AnAccountWithAFutureToAskAbout(http, "earning nothing");
        // Two hundred in and a hundred and fifty back out: the mark sits at two hundred and the
        // account holds fifty, so the next hundred and fifty saved has been paid for once already.
        account.depositsByHand("200.00");
        account.withdrawsByHand("150.00");
        account.leavesARuleStanding(Map.of(
                "name", "Every Wednesday", "fromCurrentAccountId", account.currentAccountId(),
                "trigger", "WEEKLY", "dayOfWeek", DayOfWeek.WEDNESDAY.name(),
                "howMuchMoves", "A_FIXED_AMOUNT", "amount", "60.00"));

        HowAScenarioTurnsOutView carryingOn = account.simulation().theYearAlreadyUnderWay();

        assertThat(carryingOn.thingsOfKind(MONEY_ARRIVES_AND_EARNS_NOTHING))
                .as("the first two Wednesdays put sixty euros each into an account that has already "
                        + "been paid for them, and a branch that reported only points failing to "
                        + "rise would be read as broken rather than as the rule it is — the third "
                        + "Wednesday crosses the mark and only part of it earns nothing")
                .hasSize(3);
        assertThat(carryingOn.thingsOfKind(MONEY_ARRIVES_AND_EARNS_NOTHING).get(0).figure())
                .as("carrying the euros that earned nothing, which on the first Wednesday is the "
                        + "whole sixty")
                .isEqualByComparingTo("60.00");
        assertThat(carryingOn.thingsOfKind(MONEY_ARRIVES_AND_EARNS_NOTHING).get(2).figure())
                .as("and on the third it is the thirty that was still under the mark, the other "
                        + "thirty being genuinely new saving")
                .isEqualByComparingTo("30.00");
    }

    private static List<String> fieldsOf(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
