package io.dataroots.savingstreak.goals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import io.dataroots.savingstreak.goals.AReallocationWorthSuggesting.AGoalInTheOrder;
import io.dataroots.savingstreak.goals.AReallocationWorthSuggesting.AMoveWorthMaking;
import io.dataroots.savingstreak.goals.AReallocationWorthSuggesting.TheReallocation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reallocation rule asserted directly, for the things the HTTP seam cannot say plainly.
 *
 * <p>The same bargain {@code HowTheWeeklyMoneyIsSpentTest} and {@code WhenAGoalWillBeReachedTest}
 * strike, and for the same reason. A deadline that went by while the goal stayed open cannot be set
 * up over HTTP — a day already past is refused when a goal is opened, and the only way to move time
 * is a development endpoint that winds the whole application forward — and it is the one case where
 * this rule counts weeks differently from the engine, so it is worth pinning where the difference can
 * be seen. And which way money flows through a list of goals is a fact about the walk itself, which
 * reads as three lines here and as a paragraph of arithmetic over HTTP.
 *
 * <p>The reason sentences are pinned here too, whole and character for character, because they are
 * the criterion this ticket exists to satisfy and because {@link GoalStatus#UNREACHABLE} is two
 * different sentences wearing one name — the plan giving a goal nothing, and the plan giving it so
 * little that no arrival date is worth naming. Over HTTP the second of those needs a whole account
 * arranged so that the goals above eat the capacity to the last cent, and what it would assert is a
 * substring; here the two sit side by side and the difference is readable.
 *
 * <p>Everything about which goal is worth helping, what a move costs the goal it comes out of, and
 * what accepting one does is asserted over HTTP in
 * {@code ChangingTheOrderProducesAReallocationWorthSuggestingApiTest}, where it belongs.
 */
class AReallocationWorthSuggestingTest {

    private static final long AN_ACCOUNT = 1;

    /** A Thursday, so that the Monday the weeks are counted from is visibly not the day asked about. */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 17);

    private static final LocalDate THIS_MONDAY = LocalDate.of(2026, 9, 14);

    @Test
    void a_deadline_that_has_already_gone_asks_for_the_whole_of_what_the_goal_still_needs() {
        TheReallocation suggested = AReallocationWorthSuggesting.forTheGoalsOn(AN_ACCOUNT, List.of(
                late(1, "Wedding", "500.00", "0.00", THIS_MONDAY.minusWeeks(1), "100.00"),
                unreachable(2, "Holiday", "1000.00", "800.00", null)), TODAY);

        assertThat(suggested.moves()).hasSize(1);
        assertThat(suggested.moves().get(0).amount())
                .describedAs("no week is left to bring anything, so the whole 500.00 has to come out "
                        + "of money that is already saved. The engine floors the weeks left at one "
                        + "because it has to divide by them; this counts what they will actually "
                        + "bring, which is nothing, and a floor of one here would quietly forgive a "
                        + "week of lateness and suggest 400.00")
                .isEqualByComparingTo("500.00");
    }

    @Test
    void a_goal_that_gives_money_up_is_never_given_any_in_the_same_suggestion() {
        TheReallocation suggested = AReallocationWorthSuggesting.forTheGoalsOn(AN_ACCOUNT, List.of(
                late(1, "House deposit", "1000.00", "0.00", THIS_MONDAY.plusWeeks(5), "100.00"),
                unreachable(2, "Holiday", "1000.00", "400.00", THIS_MONDAY.plusWeeks(5)),
                unreachable(3, "Car", "1000.00", "200.00", null)), TODAY);

        assertThat(suggested.moves())
                .describedAs("1000.00 is wanted, the five weeks left bring 500.00, so 500.00 has to "
                        + "be found in money that is already saved")
                .hasSize(2);
        assertThat(suggested.moves().get(0).outOfGoalName())
                .describedAs("the lowest-ranked goal holding money pays first")
                .isEqualTo("Car");
        assertThat(suggested.moves().get(0).amount())
                .describedAs("all 200.00 of what it was holding, which is not enough on its own")
                .isEqualByComparingTo("200.00");
        assertThat(suggested.moves().get(1).outOfGoalName())
                .describedAs("and the one above it makes up the rest, working upward")
                .isEqualTo("Holiday");
        assertThat(suggested.moves().get(1).amount()).isEqualByComparingTo("300.00");
        assertThat(suggested.moves()).extracting(AMoveWorthMaking::intoGoalName)
                .describedAs("the holiday is now further from its own deadline than it was, and it is "
                        + "still given nothing back: a goal is only ever asked for money once every "
                        + "goal below it is empty or has arrived, which is exactly the state in which "
                        + "there is nothing left to top it up with. Money flows one way, upward")
                .containsOnly("House deposit");
    }

    @Test
    void a_goal_the_plan_funds_too_little_is_told_the_deadline_it_will_miss_not_that_it_gets_nothing() {
        LocalDate inFortyOneWeeks = THIS_MONDAY.plusWeeks(41);
        TheReallocation suggested = AReallocationWorthSuggesting.forTheGoalsOn(AN_ACCOUNT, List.of(
                fundedTooLittleToEverArrive(1, "Kitchen", "1000.00", "0.00", inFortyOneWeeks, "0.01"),
                unreachable(2, "Spare", "500.00", "300.00", null)), TODAY);

        assertThat(suggested.moves()).hasSize(1);
        assertThat(suggested.moves().get(0).amount())
                .describedAs("1000.00 is wanted and the 41 weeks left bring 0.41 of it, so 999.59 is "
                        + "the shortfall and the 300.00 below it is all there is to cover it with")
                .isEqualByComparingTo("300.00");
        assertThat(suggested.moves().get(0).reason())
                .describedAs("UNREACHABLE is two sentences wearing one name: the plan gives this goal "
                        + "0.01 a week, not nothing, so telling the customer it is getting nothing "
                        + "contradicts the same read's own weekly figure — and it hides the deadline, "
                        + "which is the one fact they need")
                .isEqualTo("\"Kitchen\" is wanted by " + inFortyOneWeeks + " and will not be there "
                        + "in time: 41 whole weeks are left and the plan gives it only 0.01 a week, "
                        + "which is so little that no arrival date is worth naming. \"Spare\" is "
                        + "ranked below it at 2 and is holding money, so 300.00 is worth moving out "
                        + "of it into \"Kitchen\".");
    }

    @Test
    void a_goal_the_plan_gives_nothing_is_told_so_and_still_named_the_day_it_is_wanted_by() {
        LocalDate inFourWeeks = THIS_MONDAY.plusWeeks(4);
        TheReallocation suggested = AReallocationWorthSuggesting.forTheGoalsOn(AN_ACCOUNT, List.of(
                unreachable(1, "Rent", "500.00", "0.00", inFourWeeks),
                unreachable(2, "Spare", "500.00", "300.00", null)), TODAY);

        assertThat(suggested.moves().get(0).reason())
                .describedAs("the other half of UNREACHABLE, which does say \"nothing each week\" — "
                        + "and a goal with a day of its own is told that day either way")
                .isEqualTo("\"Rent\" is wanted by " + inFourWeeks + " and is getting nothing towards "
                        + "it each week, so at this rate it never arrives. \"Spare\" is ranked below "
                        + "it at 2 and is holding money, so 300.00 is worth moving out of it into "
                        + "\"Rent\".");
    }

    /** A goal with a deadline it will not meet at the rate the plan is filling it. */
    private static AGoalInTheOrder late(int rank, String name, String target, String allocation,
                                        LocalDate deadline, String weeklyAmount) {
        return new AGoalInTheOrder((long) rank, name, rank, new BigDecimal(target),
                new BigDecimal(allocation), deadline, new BigDecimal(weeklyAmount),
                GoalStatus.OFF_TRACK);
    }

    /**
     * A goal the plan is filling, but so slowly that the projection runs past the thousand years
     * {@code WhenAGoalWillBeReached} is willing to name — which is {@code UNREACHABLE} too.
     */
    private static AGoalInTheOrder fundedTooLittleToEverArrive(int rank, String name, String target,
                                                               String allocation, LocalDate deadline,
                                                               String weeklyAmount) {
        return new AGoalInTheOrder((long) rank, name, rank, new BigDecimal(target),
                new BigDecimal(allocation), deadline, new BigDecimal(weeklyAmount),
                GoalStatus.UNREACHABLE);
    }

    /** A goal the plan gives nothing, so that at this rate it never arrives at all. */
    private static AGoalInTheOrder unreachable(int rank, String name, String target,
                                               String allocation, LocalDate deadline) {
        return new AGoalInTheOrder((long) rank, name, rank, new BigDecimal(target),
                new BigDecimal(allocation), deadline, BigDecimal.ZERO, GoalStatus.UNREACHABLE);
    }
}
