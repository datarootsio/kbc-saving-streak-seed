package io.dataroots.savingstreak.savingsgoals;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import io.dataroots.savingstreak.support.AllocationsView;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When a goal will be reached, and whether that is in time, driven over HTTP.
 *
 * <p>The projection is the only thing the engine really computes and every status is a comparison
 * against it, so almost every test here reads the date and the word together: a verdict with no date
 * behind it is one nobody can check, and this is the seam a customer sees both through.
 *
 * <p>Dates are worked out from the Monday the application's own week began on rather than from the
 * machine's calendar, because other classes in this run wind the clock forward — and because the
 * whole claim of this slice is that a goal's week and a streak's week are the same seven days. Every
 * expected date in here is that Monday plus a whole number of weeks, which is exactly how a customer
 * would count them.
 *
 * <p>Money is paid in only where a goal has to be filled to make a point. What a goal still needs is
 * its target until somebody allocates to it, so most of these tests never touch the balance.
 */
class AGoalSaysWhenItWillBeReachedApiTest extends ApiIntegrationTest {

    @Test
    void a_goal_needing_a_hundred_at_twenty_five_a_week_is_reached_four_weeks_out_on_a_monday() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "four weeks out");
        GoalView holiday = account.add("Holiday", "100.00", null);
        account.canSave("25.00");

        GoalView projected = account.goal(holiday.id());
        assertThat(projected.weeklyAmount())
                .describedAs("the set-up: the only goal there is, so it gets the whole capacity")
                .isEqualByComparingTo("25.00");
        assertThat(projected.willBeReachedOn())
                .describedAs("100.00 at 25.00 a week is four whole weeks of saving")
                .isEqualTo(thisWeekStartedOn(account).plusWeeks(4));
        assertThat(projected.willBeReachedOn().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void a_remainder_rounds_up_to_a_whole_week() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "a remainder rounds up");
        GoalView holiday = account.add("Holiday", "101.00", null);
        account.canSave("25.00");

        assertThat(account.goal(holiday.id()).willBeReachedOn())
                .describedAs("after four weeks it is a euro short, so it is reached in the fifth — "
                        + "five weeks, not four and a bit")
                .isEqualTo(thisWeekStartedOn(account).plusWeeks(5));
    }

    @Test
    void a_projection_landing_before_the_deadline_is_on_track() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "on track");
        LocalDate aFortnightOfWeeks = thisWeekStartedOn(account).plusWeeks(2);
        GoalView rent = account.add("Rent", "100.00", aFortnightOfWeeks);
        account.canSave("100.00");

        GoalView projected = account.goal(rent.id());
        assertThat(projected.willBeReachedOn())
                .describedAs("the whole 100.00 arrives this week, so it is there by next Monday")
                .isEqualTo(thisWeekStartedOn(account).plusWeeks(1));
        assertThat(projected.status()).isEqualTo("ON_TRACK");
    }

    @Test
    void a_projection_landing_exactly_on_the_deadline_is_on_track() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "exactly on the deadline");
        LocalDate nextMonday = thisWeekStartedOn(account).plusWeeks(1);
        GoalView rent = account.add("Rent", "100.00", nextMonday);
        account.canSave("100.00");

        GoalView projected = account.goal(rent.id());
        assertThat(projected.willBeReachedOn())
                .describedAs("the set-up: the projection lands on the day it is wanted by")
                .isEqualTo(nextMonday);
        assertThat(projected.status())
                .describedAs("on the day is in time: arriving on the day something is due is not "
                        + "being late for it")
                .isEqualTo("ON_TRACK");
    }

    @Test
    void a_projection_landing_after_the_deadline_is_off_track() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "off track");
        LocalDate nextWeek = account.today().plusDays(7);
        GoalView roof = account.add("Roof", "100.00", nextWeek);
        account.canSave("10.00");

        GoalView projected = account.goal(roof.id());
        assertThat(projected.willBeReachedOn())
                .describedAs("100.00 at 10.00 a week is ten whole weeks")
                .isEqualTo(thisWeekStartedOn(account).plusWeeks(10));
        assertThat(projected.status())
                .describedAs("ten weeks out and wanted in one: late is late, and there is no "
                        + "AT_RISK band to soften it into")
                .isEqualTo("OFF_TRACK");
    }

    /**
     * The two halves of one read, over HTTP: the plan works out what a goal has to be given every
     * week to arrive on time, the goal is given exactly that, and the same read has to call it on
     * track. It did not, for a while — the plan counted a deadline's weeks from today and the
     * projection counted from today's Monday — so the application funded a goal correctly and told
     * the customer in the same response that they were going to be late.
     *
     * <p>Six deadlines, none of them a whole number of weeks from today, because those are the
     * shapes where the two anchors used to part company; a deadline that is an exact multiple of
     * seven days away agreed even then, which is why every dated case here used to miss it. One
     * account each, so the goals are not competing across offsets. The weekly figure is read back
     * out of the application rather than worked out by this test: the claim is about the engine's
     * own minimum, so a figure of the test's own would prove nothing about it.
     *
     * <p>Every weekday a read can land on is swept deterministically in
     * {@code WhenAGoalWillBeReachedTest}, where the day is a constant rather than whatever the
     * clock this run has wound reads.
     */
    @Test
    void a_goal_funded_to_exactly_the_minimum_its_deadline_asks_for_is_on_track() {
        for (int daysOut = 15; daysOut <= 20; daysOut++) {
            AnAccountWithGoals account =
                    new AnAccountWithGoals(http, "funded to the minimum, " + daysOut + " days out");
            LocalDate deadline = account.today().plusDays(daysOut);
            // Ranked under a goal with no date that wants far more than the capacity: the third
            // pass hands the surplus to the highest-ranked goal, so what the dated goal below it is
            // given is its deadline minimum and nothing on top of it.
            account.add("Wants everything", "9000.00", null);
            GoalView rent = account.add("Rent", "100.00", deadline);
            account.canSave("500.00");

            BigDecimal itsMinimum = account.goal(rent.id()).weeklyAmount();
            assertThat(itsMinimum)
                    .describedAs("the set-up: the minimum the engine itself says arriving by "
                            + deadline + " costs")
                    .isPositive();

            account.canSave(itsMinimum.toPlainString());

            GoalView funded = account.goal(rent.id());
            assertThat(funded.weeklyAmount())
                    .describedAs("the whole capacity is that minimum now, and the goal still gets it")
                    .isEqualByComparingTo(itsMinimum);
            assertThat(funded.willBeReachedOn())
                    .describedAs("and the money really is all there by the day it is wanted: "
                            + itsMinimum + " a week against " + funded.stillNeeded()
                            + ", wanted by " + deadline)
                    .isBeforeOrEqualTo(deadline);
            assertThat(funded.status())
                    .describedAs("so the read that funded it to the minimum cannot also call it late")
                    .isEqualTo("ON_TRACK");
        }
    }

    @Test
    void a_goal_the_plan_gives_nothing_is_unreachable_and_reports_no_projection() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "given nothing at all");
        LocalDate nextWeek = account.today().plusDays(7);
        account.add("Roof", "100.00", nextWeek);
        GoalView lastInLine = account.add("Camera", "50.00", nextWeek);
        account.canSave("100.00");

        GoalView projected = account.goal(lastInLine.id());
        assertThat(projected.weeklyAmount())
                .describedAs("the set-up: the capacity ran out before this goal was reached")
                .isEqualByComparingTo("0.00");
        assertThat(projected.status()).isEqualTo("UNREACHABLE");
        assertThat(projected.willBeReachedOn())
                .describedAs("no date at all, rather than one infinitely far away")
                .isNull();
    }

    @Test
    void a_goal_with_no_deadline_reports_a_projection_and_is_never_called_off_track() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "nothing to be late for");
        GoalView emergencyFund = account.add("Emergency fund", "500.00", null);
        account.canSave("50.00");

        GoalView projected = account.goal(emergencyFund.id());
        assertThat(projected.willBeReachedOn())
                .describedAs("a goal with no date still gets an answer to 'when will I have it'")
                .isEqualTo(thisWeekStartedOn(account).plusWeeks(10));
        assertThat(projected.status())
                .describedAs("and cannot be on or off track, because there is nothing to be late for")
                .isEqualTo("NO_DEADLINE");
    }

    @Test
    void a_goal_that_has_reached_its_target_is_completed_and_reports_no_projection() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "arrived");
        account.savesUp("200.00");
        GoalView bike = account.add("Bike", "100.00", account.today().plusDays(7));
        account.putTowards(bike.id(), "100.00");
        account.canSave("100.00");

        GoalView arrived = account.goal(bike.id());
        assertThat(arrived.status())
                .describedAs("it has what it was for, which is neither on track nor off it")
                .isEqualTo("COMPLETED");
        assertThat(arrived.willBeReachedOn())
                .describedAs("there is no week left to count")
                .isNull();
        assertThat(arrived.stillNeeded()).isEqualByComparingTo("0.00");
    }

    @Test
    void freeing_money_from_a_goal_pushes_its_projection_later() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "freeing pushes it out");
        account.savesUp("200.00");
        GoalView holiday = account.add("Holiday", "100.00", null);
        account.putTowards(holiday.id(), "50.00");
        account.canSave("25.00");

        LocalDate before = account.goal(holiday.id()).willBeReachedOn();
        assertThat(before)
                .describedAs("50.00 still needed at 25.00 a week is two weeks")
                .isEqualTo(thisWeekStartedOn(account).plusWeeks(2));

        account.free(holiday.id(), "50.00");

        LocalDate after = account.goal(holiday.id()).willBeReachedOn();
        assertThat(after)
                .describedAs("the whole 100.00 is wanted again, which is four weeks")
                .isEqualTo(thisWeekStartedOn(account).plusWeeks(4));
        assertThat(ChronoUnit.WEEKS.between(before, after))
                .describedAs("and the account says by how much: two whole weeks later, which is "
                        + "what taking the money back out cost")
                .isEqualTo(2);
    }

    @Test
    void allocating_money_to_a_goal_pulls_its_projection_earlier() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "allocating pulls it in");
        account.savesUp("200.00");
        GoalView holiday = account.add("Holiday", "100.00", null);
        account.canSave("25.00");

        LocalDate before = account.goal(holiday.id()).willBeReachedOn();
        assertThat(before).isEqualTo(thisWeekStartedOn(account).plusWeeks(4));

        AllocationsView moved = account.putTowards(holiday.id(), "75.00");

        assertThat(moved.goal(holiday.id()).willBeReachedOn())
                .describedAs("25.00 still needed at 25.00 a week is one week")
                .isEqualTo(thisWeekStartedOn(account).plusWeeks(1));
        assertThat(ChronoUnit.WEEKS.between(moved.goal(holiday.id()).willBeReachedOn(), before))
                .describedAs("three weeks earlier, and no deposit was needed to do it")
                .isEqualTo(3);
        assertThat(moved.balance())
                .describedAs("the balance never moved: this is a claim on it, not a purse")
                .isEqualByComparingTo("200.00");
    }

    @Test
    void reordering_two_goals_turns_one_from_on_track_to_off_track_without_any_money_moving() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "reordering changes the verdict");
        account.savesUp("200.00");
        LocalDate nextWeek = account.today().plusDays(7);
        GoalView first = account.add("First", "60.00", nextWeek);
        GoalView second = account.add("Second", "60.00", nextWeek);
        account.putTowards(first.id(), "10.00");
        account.putTowards(second.id(), "10.00");
        account.canSave("80.00");

        AllocationsView before = account.allocations();
        assertThat(before.goal(first.id()).status())
                .describedAs("50.00 still needed and 50.00 a week: there by next Monday")
                .isEqualTo("ON_TRACK");
        assertThat(before.goal(second.id()).status())
                .describedAs("50.00 still needed and only the 30.00 that was left: two weeks, and "
                        + "it was wanted in one")
                .isEqualTo("OFF_TRACK");

        account.reorder(List.of(second.id(), first.id()));

        AllocationsView after = account.allocations();
        assertThat(after.goal(second.id()).status())
                .describedAs("at rank 1 it takes its minimum first and arrives in time")
                .isEqualTo("ON_TRACK");
        assertThat(after.goal(first.id()).status())
                .describedAs("and the goal that was comfortable is the one that is now late")
                .isEqualTo("OFF_TRACK");
        assertThat(after.goal(first.id()).willBeReachedOn())
                .isEqualTo(thisWeekStartedOn(account).plusWeeks(2));

        assertThat(after.balance()).isEqualByComparingTo(before.balance());
        assertThat(after.allocated()).isEqualByComparingTo(before.allocated());
        assertThat(after.goal(first.id()).allocation())
                .describedAs("not a cent moved: the order changed and nothing else did")
                .isEqualByComparingTo(before.goal(first.id()).allocation());
        assertThat(after.goal(second.id()).allocation())
                .isEqualByComparingTo(before.goal(second.id()).allocation());
    }

    @Test
    void with_no_capacity_declared_a_goal_reports_no_projection_and_no_track_verdict() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "nothing said about the rate");
        GoalView dated = account.add("Dated", "100.00", account.today().plusDays(7));
        GoalView undated = account.add("Undated", "100.00", null);

        assertThat(account.savingCapacity().declared())
                .describedAs("the set-up: nobody has said what they can save")
                .isFalse();
        assertThat(account.goals())
                .describedAs("no rate to project from, so no date and no verdict about being late")
                .allSatisfy(goal -> {
                    assertThat(goal.willBeReachedOn()).isNull();
                    assertThat(goal.status()).isEqualTo("STILL_SAVING");
                });
        assertThat(account.goal(dated.id()).status())
                .describedAs("and not UNREACHABLE: a customer who has said nothing has not said "
                        + "they can save nothing")
                .isEqualTo("STILL_SAVING");

        account.canSave("100.00");
        assertThat(account.goal(dated.id()).status())
                .describedAs("and the moment a figure exists, every goal is judged against it")
                .isEqualTo("ON_TRACK");
        assertThat(account.goal(undated.id()).willBeReachedOn())
                .describedAs("the goal below it gets nothing, so there is still no date to name")
                .isNull();
    }

    @Test
    void every_projection_is_a_monday_agreeing_with_the_week_a_streak_is_counted_in() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "every projection is a Monday");
        LocalDate today = account.today();
        // The undated goal is ranked first so that the surplus reaches it: the third pass hands
        // what is left to the highest-ranked goal alone, and one further down would be given
        // nothing and carry no date to assert about.
        account.add("No date", "2500.00", null);
        account.add("In a week", "100.00", today.plusDays(7));
        account.add("In a month", "400.00", today.plusDays(28));
        account.canSave("300.00");

        List<GoalView> goals = account.goals();
        assertThat(goals)
                .describedAs("a goal's week and a streak's week are the same seven days")
                .allSatisfy(goal -> {
                    assertThat(goal.willBeReachedOn()).isNotNull();
                    assertThat(goal.willBeReachedOn().getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
                    assertThat(new SavingsWeek(goal.willBeReachedOn()).startsOn())
                            .isEqualTo(goal.willBeReachedOn());
                    assertThat(goal.willBeReachedOn()).isAfter(today);
                });
    }

    @Test
    void a_goal_given_up_on_reports_no_projection_and_no_verdict() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "given up on");
        GoalView holiday = account.add("Holiday", "100.00", account.today().plusDays(7));
        account.canSave("100.00");
        assertThat(account.goal(holiday.id()).willBeReachedOn())
                .describedAs("the set-up: while it was live it had a date")
                .isNotNull();

        account.abandon(holiday.id());

        GoalView givenUpOn = account.goal(holiday.id());
        assertThat(givenUpOn.status()).isEqualTo("ABANDONED");
        assertThat(givenUpOn.willBeReachedOn())
                .describedAs("nobody is saving for it, so there is no day it arrives on")
                .isNull();
    }

    /**
     * The Monday the application's current savings week began on, which is where every projection's
     * weeks are counted from.
     *
     * <p>Off the application's clock rather than the machine's, and through {@code SavingsWeek}
     * itself rather than through an adjuster of the test's own: the claim being asserted is that the
     * two agree, and a test that worked the Monday out separately would pass even if they did not.
     */
    private static LocalDate thisWeekStartedOn(AnAccountWithGoals account) {
        return SavingsWeek.containing(account.today()).startsOn();
    }
}
