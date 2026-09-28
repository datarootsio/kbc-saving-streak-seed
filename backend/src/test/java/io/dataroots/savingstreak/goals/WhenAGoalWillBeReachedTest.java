package io.dataroots.savingstreak.goals;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import io.dataroots.savingstreak.goals.HowTheWeeklyMoneyIsSpent.AGoalCompetingForIt;
import io.dataroots.savingstreak.goals.WhenAGoalWillBeReached.AGoalOnItsWay;
import io.dataroots.savingstreak.goals.WhenAGoalWillBeReached.TheProjection;
import io.dataroots.savingstreak.streaks.SavingsWeek;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The projection asserted directly, for the cases the HTTP seam cannot reach.
 *
 * <p>The same bargain {@code HowTheWeeklyMoneyIsSpentTest} strikes, and for the same three reasons.
 * A deadline that went by while the goal stayed open cannot be set up over HTTP, because a deadline
 * already in the past is refused when a goal is opened and the only way to move time is a
 * development endpoint that winds whole days forward for the entire application. Which Monday the
 * weeks are counted from can only be pinned against a day that is known, and over HTTP the day is
 * whatever the clock other classes in this run have wound reads. And a goal filling so slowly that
 * it never arrives in any span worth naming needs a capacity of a cent, which is legal but says
 * nothing about anything else.
 *
 * <p>Everything about which goal comes up short, and what that does to its verdict, is asserted over
 * HTTP in {@code AGoalSaysWhenItWillBeReachedApiTest}, where it belongs.
 */
class WhenAGoalWillBeReachedTest {

    private static final long AN_ACCOUNT = 1;

    /** A Thursday, so that the Monday the weeks start on is visibly not the day being asked about. */
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 17);

    private static final LocalDate THIS_MONDAY = LocalDate.of(2026, 9, 14);

    @Test
    void a_hundred_at_twenty_five_a_week_is_reached_four_mondays_on() {
        TheProjection projection = projectionOf("100.00", null, "25.00");

        assertThat(projection.willBeReachedOn())
                .describedAs("four whole weeks, counted from the Monday this week began on and "
                        + "not from the Thursday it is being asked about")
                .isEqualTo(LocalDate.of(2026, 10, 12));
        assertThat(projection.willBeReachedOn().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void a_remainder_rounds_up_to_a_whole_week() {
        assertThat(projectionOf("101.00", null, "25.00").willBeReachedOn())
                .describedAs("after four weeks it is a euro short, so it is reached in the fifth")
                .isEqualTo(LocalDate.of(2026, 10, 19));
    }

    @Test
    void the_monday_named_is_the_one_the_money_is_all_there_by() {
        assertThat(projectionOf("100.00", null, "100.00").willBeReachedOn())
                .describedAs("a goal given the whole of what it needs this week is reached by next "
                        + "Monday, and naming this week's would be naming a day already gone")
                .isEqualTo(LocalDate.of(2026, 9, 21));
    }

    @Test
    void a_deadline_that_went_by_while_the_goal_stayed_open_is_off_track() {
        TheProjection projection = projectionOf("100.00", TODAY.minusDays(40), "100.00");

        assertThat(projection.status())
                .describedAs("a deadline is soft — nothing closes a goal because a date passed — "
                        + "and the goal is late rather than the arithmetic being wrong")
                .isEqualTo(GoalStatus.OFF_TRACK);
        assertThat(projection.willBeReachedOn()).isEqualTo(LocalDate.of(2026, 9, 21));
    }

    @Test
    void a_goal_filling_a_cent_at_a_time_never_arrives_in_any_span_worth_naming() {
        TheProjection projection = projectionOf("1000.00", null, "0.01");

        assertThat(projection.status())
                .describedAs("nineteen centuries is not a date to put in front of a customer, and "
                        + "past what a LocalDate holds it is not a date at all")
                .isEqualTo(GoalStatus.UNREACHABLE);
        assertThat(projection.willBeReachedOn()).isNull();
    }

    @Test
    void a_goal_that_has_arrived_is_completed_before_anything_is_asked_about_a_capacity() {
        TheProjection projection = projectionOf("0.00", TODAY.plusDays(7), null);

        assertThat(projection.status())
                .describedAs("it needs no more weeks, so nobody having declared a capacity changes "
                        + "nothing about it")
                .isEqualTo(GoalStatus.COMPLETED);
        assertThat(projection.willBeReachedOn()).isNull();
    }

    @Test
    void nothing_is_said_at_all_when_no_capacity_has_been_declared() {
        TheProjection projection = projectionOf("100.00", TODAY.plusDays(7), null);

        assertThat(projection.status())
                .describedAs("no rate to project from, so no date and no verdict — and not "
                        + "UNREACHABLE, which is an answer to a question nobody asked")
                .isEqualTo(GoalStatus.STILL_SAVING);
        assertThat(projection.willBeReachedOn()).isNull();
    }

    @Test
    void a_goal_the_plan_gives_nothing_is_unreachable_and_carries_no_date() {
        TheProjection projection = projectionOf("100.00", TODAY.plusDays(7), "0.00");

        assertThat(projection.status()).isEqualTo(GoalStatus.UNREACHABLE);
        assertThat(projection.willBeReachedOn())
                .describedAs("dividing by nothing is not an answer, and a date infinitely far away "
                        + "would be one a page had to special-case anyway")
                .isNull();
    }

    /**
     * Renamed after ticket 06's review, which caught it asserting the opposite of what it was
     * called. Only the map is this class's business: a goal the passes never saw is simply not in
     * it. Turning that absence into {@link TheProjection#NOTHING_SAID} is
     * {@code GoalsService.ThePlanOn.projectionFor}'s, and it is asserted where it can be reached:
     * over HTTP, by {@code AGoalSaysWhenItWillBeReachedApiTest}'s test that a goal given up on
     * reports no projection and no verdict, which reads an abandoned goal back and gets an answer
     * rather than a failure.
     */
    @Test
    void a_goal_the_projection_never_saw_is_absent_from_the_map_rather_than_projected() {
        Map<Long, TheProjection> projected = WhenAGoalWillBeReached.forAllOf(AN_ACCOUNT,
                List.of(aGoal(7L, "100.00", null, "25.00")), TODAY);

        assertThat(projected.get(99L))
                .describedAs("a goal given up on has left the order and never reaches the "
                        + "projection, so nothing was worked out about it")
                .isNull();
        assertThat(projected).containsOnlyKeys(7L);
        assertThat(TheProjection.NOTHING_SAID.status())
                .describedAs("and what a caller is handed for it says only that it is still being "
                        + "saved towards")
                .isEqualTo(GoalStatus.STILL_SAVING);
        assertThat(TheProjection.NOTHING_SAID.willBeReachedOn()).isNull();
    }

    /**
     * The two halves of one read, asserted against each other, which is the thing ticket 06's review
     * sent the work back for.
     *
     * <p>The engine decides what a dated goal has to be given every week to arrive on time; this
     * class decides whether what it was given gets it there. They agree only if they count weeks
     * from the same day, and for a while they did not — the engine from today, the projection from
     * today's Monday — so a goal funded to precisely the figure computed for arriving on time was
     * told in the same response that it was going to be late. It bit on most weekday-and-deadline
     * pairs, and on a Monday on every deadline that was not itself a Monday.
     *
     * <p>So: every weekday a read can happen on, every deadline from today out to three weeks, three
     * amounts including one that does not divide evenly. Fund the goal at exactly the minimum the
     * engine gives it and the verdict must be {@code ON_TRACK} — with the one honest exception, a
     * deadline no Monday at all falls on or before. Money arrives in whole savings weeks, and a goal
     * wanted before next Monday cannot be met by one.
     */
    @Test
    void a_goal_funded_to_exactly_its_deadline_minimum_is_never_called_late() {
        for (int weekday = 0; weekday < 7; weekday++) {
            LocalDate today = THIS_MONDAY.plusDays(weekday);
            LocalDate earliestMondayItCanBeMetOn = SavingsWeek.containing(today).startsOn().plusWeeks(1);
            for (int daysOut = 0; daysOut <= 21; daysOut++) {
                LocalDate deadline = today.plusDays(daysOut);
                for (String stillNeeded : List.of("10.00", "100.00", "333.33")) {
                    BigDecimal minimum = theMinimumTheEngineGivesFor(stillNeeded, deadline, today);
                    TheProjection projection = projectionOf(stillNeeded, deadline, minimum, today);
                    String sum = "today=" + today + " deadline=" + deadline + " needs=" + stillNeeded
                            + " funded at the engine's own minimum of " + minimum + " a week, "
                            + "reaching it on " + projection.willBeReachedOn();
                    if (deadline.isBefore(earliestMondayItCanBeMetOn)) {
                        assertThat(projection.status())
                                .describedAs("no Monday falls on or before it, so it is genuinely "
                                        + "late however it is funded — " + sum)
                                .isEqualTo(GoalStatus.OFF_TRACK);
                    } else {
                        assertThat(projection.status())
                                .describedAs("funded to the minimum for arriving on time — " + sum)
                                .isEqualTo(GoalStatus.ON_TRACK);
                        assertThat(projection.willBeReachedOn())
                                .describedAs("and the money really is all there by then — " + sum)
                                .isBeforeOrEqualTo(deadline);
                    }
                }
            }
        }
    }

    /**
     * What the engine gives one dated goal, which is exactly the minimum its deadline asks for.
     *
     * <p>Ranked second, under an undated goal wanting far more than the capacity, for the reason
     * {@code HowTheWeeklyMoneyIsSpentTest} ranks it there: the third pass hands whatever is left to
     * the highest-ranked unfinished goal, so a dated goal at rank 1 would be given its minimum and
     * then the surplus, and the figure read back would be the whole capacity rather than the
     * minimum. The capacity is far larger than any minimum below, so the second pass never runs out.
     */
    private static BigDecimal theMinimumTheEngineGivesFor(String stillNeeded, LocalDate deadline,
                                                          LocalDate today) {
        return HowTheWeeklyMoneyIsSpent
                .asAt(AN_ACCOUNT, new BigDecimal("100000.00"),
                        List.of(new AGoalCompetingForIt(9L, "wants everything", 1,
                                        new BigDecimal("900000.00"), null, null),
                                new AGoalCompetingForIt(7L, "funded to the minimum", 2,
                                        new BigDecimal(stillNeeded), deadline, null)),
                        today)
                .forGoal(7L);
    }

    /** One goal's projection, worked out the way a read works it out: over the whole account. */
    private static TheProjection projectionOf(String stillNeeded, LocalDate deadline,
                                              String weeklyAmount) {
        return WhenAGoalWillBeReached
                .forAllOf(AN_ACCOUNT, List.of(aGoal(7L, stillNeeded, deadline, weeklyAmount)), TODAY)
                .get(7L);
    }

    /** The same, for a caller holding a weekly amount the engine worked out and a day of its own. */
    private static TheProjection projectionOf(String stillNeeded, LocalDate deadline,
                                              BigDecimal weeklyAmount, LocalDate today) {
        return WhenAGoalWillBeReached
                .forAllOf(AN_ACCOUNT, List.of(new AGoalOnItsWay(7L, "goal 7", 1,
                        new BigDecimal(stillNeeded), deadline, weeklyAmount)), today)
                .get(7L);
    }

    private static AGoalOnItsWay aGoal(Long goalId, String stillNeeded, LocalDate deadline,
                                       String weeklyAmount) {
        return new AGoalOnItsWay(goalId, "goal " + goalId, 1, new BigDecimal(stillNeeded), deadline,
                weeklyAmount == null ? null : new BigDecimal(weeklyAmount));
    }

    /** The Monday the weeks are counted from, named once so the dates above can be read by hand. */
    @Test
    void the_weeks_are_counted_from_the_monday_the_week_containing_today_began_on() {
        assertThat(THIS_MONDAY.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(projectionOf("100.00", null, "100.00").willBeReachedOn())
                .isEqualTo(THIS_MONDAY.plusWeeks(1));
    }
}
