package io.dataroots.savingstreak.savingsgoals;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;

import io.dataroots.savingstreak.streaks.SavingsWeek;
import io.dataroots.savingstreak.support.AllocationsView;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.AppliedReallocationView;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.SuggestedMoveView;
import io.dataroots.savingstreak.support.SuggestedReallocationView;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The payload of the whole feature, driven over HTTP: when the money already allocated no longer
 * matches the order of importance, the account offers concrete moves and says why each one is worth
 * making.
 *
 * <p><strong>Every scenario is built so that the arithmetic can be checked by hand.</strong> The
 * capacity is 100.00 a week throughout and every deadline is a whole number of weeks from the Monday
 * this week began on, because that is the Monday the engine counts a deadline's weeks from and the
 * Monday the projection counts forward to. A deadline five weeks out against a capacity of 100.00
 * means "500.00 will have arrived by then", and what a goal needs to stop being late is the rest.
 *
 * <p>Each test opens its own customer and savings account, for the reason {@link AnAccountWithGoals}
 * gives: goals accumulate on an account and the whole run shares one database, so a test asserting
 * about rank 2 has to own every goal on the account.
 *
 * <p>Days are counted from what the application's clock says today is rather than from the machine's,
 * because other classes in this run wind that clock forward.
 */
class ChangingTheOrderProducesAReallocationWorthSuggestingApiTest extends ApiIntegrationTest {

    /** The one capacity every scenario here declares, so that every figure below divides by 100. */
    private static final String A_HUNDRED_A_WEEK = "100.00";

    @Test
    void with_every_goal_on_track_there_is_nothing_to_suggest() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "nothing to suggest");
        LocalDate inEightWeeks = account.today().plusWeeks(8);
        account.add("Holiday", "400.00", inEightWeeks);
        account.add("Car", "400.00", inEightWeeks);
        account.canSave(A_HUNDRED_A_WEEK);

        assertThat(account.goals()).extracting(GoalView::status)
                .describedAs("the set-up: 50.00 a week each is exactly what eight weeks asks for")
                .containsExactly("ON_TRACK", "ON_TRACK");

        SuggestedReallocationView suggested = account.suggestedReallocation();

        assertThat(suggested.worthSuggesting())
                .describedAs("nothing is late, so there is nothing to move money towards")
                .isFalse();
        assertThat(suggested.moves()).isEmpty();
        assertThat(suggested.inWords())
                .describedAs("a sentence a page can print, rather than an empty table")
                .isEqualTo("Every goal on this account is on its way in time, so there is nothing "
                        + "worth moving.");
    }

    @Test
    void promoting_a_goal_that_is_short_of_its_deadline_produces_moves_out_of_the_goals_now_below_it() {
        AnAccountWithGoals account = anAccountWhoseHouseIsAboutToBePromoted();
        List<GoalView> before = account.goals();
        GoalView holiday = theGoalCalled(before, "Holiday");
        GoalView house = theGoalCalled(before, "House deposit");

        assertThat(house.status())
                .describedAs("the set-up: the house is already going to be late, and the holiday is "
                        + "holding 500.00 — but the holiday is ranked above it")
                .isEqualTo("OFF_TRACK");
        assertThat(account.suggestedReallocation().worthSuggesting())
                .describedAs("a move never takes from a goal ranked above the one it is helping, so "
                        + "there is nothing to suggest while the holiday still comes first")
                .isFalse();

        account.reorder(List.of(house.id(), holiday.id()));

        SuggestedReallocationView suggested = account.suggestedReallocation();
        assertThat(suggested.worthSuggesting()).isTrue();
        assertThat(suggested.moves())
                .describedAs("one move, out of the goal that is now ranked below the promoted one")
                .hasSize(1);
        SuggestedMoveView move = suggested.moves().get(0);
        assertThat(move.outOfGoalId()).isEqualTo(holiday.id());
        assertThat(move.outOfGoalName()).isEqualTo("Holiday");
        assertThat(move.intoGoalId()).isEqualTo(house.id());
        assertThat(move.amount())
                .describedAs("900.00 is still wanted and the five weeks left bring 500.00 of it, so "
                        + "400.00 has to come from somewhere it has already been saved")
                .isEqualByComparingTo("400.00");
    }

    @Test
    void a_suggested_move_never_takes_from_a_goal_ranked_above_the_one_it_is_helping() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "never from above");
        account.savesUp("1500.00");
        GoalView holiday = account.add("Holiday", "600.00", account.today().plusWeeks(10));
        GoalView house = account.add("House deposit", "900.00", account.today().plusWeeks(5));
        account.canSave(A_HUNDRED_A_WEEK);
        account.putTowards(holiday.id(), "500.00");

        SuggestedReallocationView suggested = account.suggestedReallocation();

        assertThat(account.goal(house.id()).status())
                .describedAs("the goal that is late is the one ranked second")
                .isEqualTo("OFF_TRACK");
        assertThat(account.goal(holiday.id()).allocation())
                .describedAs("and the only money on the account is held by the goal above it")
                .isEqualByComparingTo("500.00");
        assertThat(suggested.worthSuggesting())
                .describedAs("robbing the more important goal to pay the less important one is the "
                        + "order of importance read backwards")
                .isFalse();
        assertThat(suggested.inWords())
                .describedAs("and the sentence names the goal that is late, so the customer knows "
                        + "which goal nothing could be found for")
                .contains("House deposit")
                .contains("no goal ranked below it is holding money");
    }

    @Test
    void a_suggested_move_never_takes_from_a_completed_goal() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "never from a completed goal");
        account.savesUp("1500.00");
        GoalView house = account.add("House deposit", "900.00", account.today().plusWeeks(5));
        GoalView holiday = account.add("Holiday", "600.00", account.today().plusWeeks(10));
        GoalView car = account.add("Car", "200.00", null);
        account.canSave(A_HUNDRED_A_WEEK);
        account.putTowards(holiday.id(), "500.00");
        account.putTowards(car.id(), "200.00");

        assertThat(account.goal(car.id()).status())
                .describedAs("the set-up: the car is the lowest-ranked goal and it is holding money, "
                        + "but it has arrived")
                .isEqualTo("COMPLETED");

        SuggestedReallocationView suggested = account.suggestedReallocation();

        assertThat(suggested.moves()).extracting(SuggestedMoveView::outOfGoalId)
                .describedAs("the lowest-ranked goal holding money is passed over because it has "
                        + "reached its target, and the holiday above it pays instead")
                .containsExactly(holiday.id());
        account.acceptTheSuggestedReallocation();
        assertThat(account.goal(car.id()).allocation())
                .describedAs("and the completed goal still holds every cent of it afterwards")
                .isEqualByComparingTo("200.00");
        assertThat(account.goal(house.id()).allocation()).isEqualByComparingTo("400.00");
    }

    @Test
    void the_moves_never_take_more_than_the_helped_goal_needs_or_leave_a_goal_holding_less_than_nothing() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "never more than it needs");
        account.savesUp("1500.00");
        GoalView emergency = account.add("Emergency fund", "300.00", null);
        account.add("Rent", "100.00", account.today().plusWeeks(1));
        GoalView holiday = account.add("Holiday", "1000.00", null);
        account.canSave(A_HUNDRED_A_WEEK);
        account.putTowards(holiday.id(), "800.00");

        assertThat(account.goal(emergency.id()).status())
                .describedAs("the set-up: the rent's deadline takes the whole capacity, so the goal "
                        + "above it is given nothing and never arrives")
                .isEqualTo("UNREACHABLE");

        SuggestedReallocationView suggested = account.suggestedReallocation();

        assertThat(suggested.movingInto(emergency.id()))
                .describedAs("300.00 and not a cent more, although 800.00 was there to take: the "
                        + "goal's whole remaining target is the cap, which is what stops a "
                        + "suggestion proposing a move the allocation endpoint would refuse")
                .isEqualByComparingTo("300.00");

        AppliedReallocationView accepted = account.acceptTheSuggestedReallocation();

        assertThat(accepted.allocations().goal(emergency.id()).stillNeeded())
                .describedAs("so it arrives exactly, and is never over-funded")
                .isEqualByComparingTo("0.00");
        assertThat(accepted.allocations().goals()).allSatisfy(goal ->
                assertThat(goal.allocation())
                        .describedAs("no goal is left holding less than nothing")
                        .isGreaterThanOrEqualTo(BigDecimal.ZERO));
        assertThat(accepted.allocations().goal(holiday.id()).allocation())
                .describedAs("the holiday gave up exactly what was asked of it and kept the rest")
                .isEqualByComparingTo("500.00");
    }

    @Test
    void money_below_that_cannot_bring_a_goal_back_is_still_worth_moving_and_the_goal_stays_off_track() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "a partial move");
        account.savesUp("1500.00");
        GoalView house = account.add("House deposit", "5000.00", account.today().plusWeeks(5));
        GoalView holiday = account.add("Holiday", "600.00", null);
        account.canSave(A_HUNDRED_A_WEEK);
        account.putTowards(holiday.id(), "200.00");

        SuggestedReallocationView suggested = account.suggestedReallocation();

        assertThat(suggested.moving())
                .describedAs("4500.00 would be needed and 200.00 is all there is, so all of it is "
                        + "worth moving: a partial answer is still an answer")
                .isEqualByComparingTo("200.00");

        AppliedReallocationView accepted = account.acceptTheSuggestedReallocation();

        assertThat(accepted.allocations().goal(house.id()).status())
                .describedAs("and the application does not pretend that fixed it")
                .isEqualTo("OFF_TRACK");
        assertThat(accepted.allocations().goal(house.id()).allocation()).isEqualByComparingTo("200.00");
        assertThat(accepted.allocations().goal(holiday.id()).allocation()).isEqualByComparingTo("0.00");
    }

    @Test
    void when_nothing_can_be_moved_the_account_says_so_rather_than_answering_an_empty_list() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "nothing can be moved");
        account.savesUp("1500.00");
        GoalView house = account.add("House deposit", "5000.00", account.today().plusWeeks(5));
        account.add("Holiday", "600.00", null);
        account.canSave(A_HUNDRED_A_WEEK);

        SuggestedReallocationView suggested = account.suggestedReallocation();

        assertThat(account.goal(house.id()).status())
                .describedAs("the set-up: a goal that is going to be late, and no goal below it "
                        + "holding a cent")
                .isEqualTo("OFF_TRACK");
        assertThat(suggested.worthSuggesting()).isFalse();
        assertThat(suggested.moves()).isEmpty();
        assertThat(suggested.inWords())
                .describedAs("which is a different sentence from every goal being on track, and "
                        + "sends the customer somewhere different")
                .isEqualTo("\"House deposit\" is not going to arrive in time, but no goal ranked "
                        + "below it is holding money that could be moved. Free money from somewhere "
                        + "else, or raise what you can put away each week.");
    }

    @Test
    void an_account_whose_holder_has_not_said_what_they_can_save_is_told_that_rather_than_a_verdict() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "nothing said about a week");
        account.savesUp("1500.00");
        GoalView holiday = account.add("Holiday", "600.00", account.today().plusWeeks(10));
        account.add("House deposit", "900.00", account.today().plusWeeks(1));
        account.putTowards(holiday.id(), "500.00");

        SuggestedReallocationView suggested = account.suggestedReallocation();

        assertThat(account.goals()).extracting(GoalView::status)
                .describedAs("nothing says how fast anything fills, so no goal has been told it is "
                        + "late — which is a different sentence from a plan in which it is")
                .containsOnly("STILL_SAVING");
        assertThat(suggested.worthSuggesting()).isFalse();
        assertThat(suggested.inWords())
                .describedAs("and the customer is sent to the figure that is missing rather than to "
                        + "their goals")
                .isEqualTo("Nothing has been said about how much can be put away each week, so "
                        + "nothing here says a goal is going to be late. Declare a weekly saving "
                        + "capacity first.");
    }

    @Test
    void each_suggested_move_carries_the_reason_it_is_suggested_naming_the_goal_it_helps() {
        AnAccountWithGoals account = anAccountWhoseHouseIsAboutToBePromoted();
        GoalView house = theGoalCalled(account.goals(), "House deposit");
        GoalView holiday = theGoalCalled(account.goals(), "Holiday");
        account.reorder(List.of(house.id(), holiday.id()));

        SuggestedMoveView move = account.suggestedReallocation().moves().get(0);

        assertThat(move.reason())
                .describedAs("the goal it helps, where that goal stands, and where the money is "
                        + "coming from — beside the row, because that is where it is asked about")
                .contains("\"House deposit\"")
                .contains("will not be there in time")
                .contains("5 whole weeks are left")
                .contains("100.00 a week")
                .contains("\"Holiday\" is ranked below it at 2");
        assertThat(account.suggestedReallocation().inWords())
                .describedAs("and the whole suggestion in one sentence above the list")
                .isEqualTo("1 move worth making, 400.00 altogether, towards \"House deposit\".");
    }

    @Test
    void accepting_applies_every_move_and_the_helped_goal_reads_better_afterwards() {
        AnAccountWithGoals account = anAccountWhoseHouseIsAboutToBePromoted();
        GoalView house = theGoalCalled(account.goals(), "House deposit");
        GoalView holiday = theGoalCalled(account.goals(), "Holiday");
        account.reorder(List.of(house.id(), holiday.id()));
        LocalDate thisMonday = SavingsWeek.containing(account.today()).startsOn();

        assertThat(account.goal(house.id()).willBeReachedOn())
                .describedAs("the set-up: 900.00 at 100.00 a week is nine weeks, and it was wanted "
                        + "in five")
                .isEqualTo(thisMonday.plusWeeks(9));

        AppliedReallocationView accepted = account.acceptTheSuggestedReallocation();

        assertThat(accepted.applied().moves()).hasSize(1);
        GoalView helped = accepted.allocations().goal(house.id());
        assertThat(helped.allocation()).isEqualByComparingTo("400.00");
        assertThat(helped.status())
                .describedAs("500.00 left to find at 100.00 a week is five weeks, which is exactly "
                        + "what it had")
                .isEqualTo("ON_TRACK");
        assertThat(helped.willBeReachedOn())
                .describedAs("and the projection came in by four whole weeks")
                .isEqualTo(thisMonday.plusWeeks(5));
        assertThat(account.goal(holiday.id()).allocation())
                .describedAs("out of the goal that paid for it, and the ledger says so")
                .isEqualByComparingTo("100.00");
        assertThat(account.historyOf(holiday.id()).get(0).intoGoalId())
                .describedAs("an accepted suggestion is an ordinary move and reads back as one")
                .isEqualTo(house.id());
    }

    @Test
    void accepting_a_suggestion_made_of_several_moves_applies_every_one_of_them() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "several moves");
        account.savesUp("1500.00");
        GoalView house = account.add("House deposit", "900.00", account.today().plusWeeks(5));
        GoalView holiday = account.add("Holiday", "600.00", null);
        GoalView car = account.add("Car", "600.00", null);
        account.canSave(A_HUNDRED_A_WEEK);
        account.putTowards(holiday.id(), "250.00");
        account.putTowards(car.id(), "150.00");

        SuggestedReallocationView suggested = account.suggestedReallocation();

        assertThat(suggested.moves())
                .describedAs("900.00 is wanted and the five weeks left bring 500.00 of it, so 400.00 "
                        + "has to be found — and no one goal below it is holding that much")
                .hasSize(2);
        assertThat(suggested.moves()).extracting(SuggestedMoveView::outOfGoalName)
                .describedAs("the lowest-ranked goal holding money pays first, then the one above it")
                .containsExactly("Car", "Holiday");

        AppliedReallocationView accepted = account.acceptTheSuggestedReallocation();

        assertThat(accepted.applied().moves())
                .describedAs("every move in the list is applied and not only the first: the second "
                        + "one reads the allocations back after the first one has written its ledger "
                        + "row, inside the same transaction, and this is the only place that is "
                        + "driven over HTTP")
                .hasSize(2);
        assertThat(accepted.applied().moving()).isEqualByComparingTo("400.00");
        assertThat(accepted.allocations().goal(house.id()).allocation())
                .isEqualByComparingTo("400.00");
        assertThat(accepted.allocations().goal(house.id()).status())
                .describedAs("500.00 left to find at 100.00 a week is exactly the five weeks it has")
                .isEqualTo("ON_TRACK");
        assertThat(accepted.allocations().goal(car.id()).allocation())
                .describedAs("both donors gave up every cent they were holding, and neither was asked "
                        + "for one more than that")
                .isEqualByComparingTo("0.00");
        assertThat(accepted.allocations().goal(holiday.id()).allocation()).isEqualByComparingTo("0.00");
        assertThat(accepted.allocations().allocated().add(accepted.allocations().unallocated()))
                .describedAs("and the one sum still holds after two moves rather than one")
                .isEqualByComparingTo(accepted.allocations().balance());
    }

    @Test
    void accepting_applies_what_is_true_at_the_moment_of_acceptance_and_not_what_was_read_before() {
        AnAccountWithGoals account = anAccountWhoseHouseIsAboutToBePromoted();
        GoalView house = theGoalCalled(account.goals(), "House deposit");
        GoalView holiday = theGoalCalled(account.goals(), "Holiday");
        account.reorder(List.of(house.id(), holiday.id()));

        SuggestedReallocationView readBefore = account.suggestedReallocation();
        assertThat(readBefore.moving()).isEqualByComparingTo("400.00");

        // The customer frees money from the holiday and takes it out of the account, which is the
        // only way a withdrawal can reach money a goal was holding: a withdrawal draws on what no
        // goal has claimed, and beyond that it is refused.
        account.free(holiday.id(), "300.00");
        account.withdraw("300.00");

        AppliedReallocationView accepted = account.acceptTheSuggestedReallocation();

        assertThat(accepted.applied().moving())
                .describedAs("200.00 is what the holiday is holding now, and what was read a moment "
                        + "ago is not applied to money that has since left the account")
                .isEqualByComparingTo("200.00");
        assertThat(accepted.allocations().goal(house.id()).allocation()).isEqualByComparingTo("200.00");
        assertThat(accepted.allocations().goal(holiday.id()).allocation()).isEqualByComparingTo("0.00");
    }

    @Test
    void reading_a_suggestion_twice_moves_no_money_and_says_the_same_thing_both_times() {
        AnAccountWithGoals account = anAccountWhoseHouseIsAboutToBePromoted();
        GoalView house = theGoalCalled(account.goals(), "House deposit");
        GoalView holiday = theGoalCalled(account.goals(), "Holiday");
        account.reorder(List.of(house.id(), holiday.id()));
        AllocationsView before = account.allocations();
        int movesInTheLedger = account.historyOf(holiday.id()).size();

        SuggestedReallocationView first = account.suggestedReallocation();
        SuggestedReallocationView second = account.suggestedReallocation();

        assertThat(second)
                .describedAs("a suggestion is derived on the read and stored nowhere, so two reads "
                        + "with nothing in between are the same read twice")
                .isEqualTo(first);
        assertThat(account.allocations())
                .describedAs("and reading it is a read: nothing moved")
                .isEqualTo(before);
        assertThat(account.historyOf(holiday.id()))
                .describedAs("no row was written by reading")
                .hasSize(movesInTheLedger);
        assertThat(account.goal(house.id()).allocation())
                .describedAs("ignoring a suggestion changes nothing at all — the engine never "
                        + "applies one on its own")
                .isEqualByComparingTo("0.00");
    }

    @Test
    void after_accepting_the_allocations_plus_what_no_goal_has_claimed_still_equal_the_balance() {
        AnAccountWithGoals account = anAccountWhoseHouseIsAboutToBePromoted();
        GoalView house = theGoalCalled(account.goals(), "House deposit");
        GoalView holiday = theGoalCalled(account.goals(), "Holiday");
        account.reorder(List.of(house.id(), holiday.id()));
        BigDecimal balanceBefore = account.savingsBalance();
        BigDecimal allocatedBefore = account.allocations().allocated();

        AllocationsView after = account.acceptTheSuggestedReallocation().allocations();

        assertThat(after.allocated().add(after.unallocated()))
                .describedAs("the one sum anybody reading this is checking")
                .isEqualByComparingTo(after.balance());
        assertThat(after.balance())
                .describedAs("and moving money between goals never touches the savings balance")
                .isEqualByComparingTo(balanceBefore);
        assertThat(after.allocated())
                .describedAs("nor what the goals have claimed altogether: the money moved sideways")
                .isEqualByComparingTo(allocatedBefore);
        assertThat(account.savingsBalance()).isEqualByComparingTo(balanceBefore);
    }

    @Test
    void accepting_a_suggestion_there_is_no_longer_anything_in_applies_nothing_and_says_why() {
        AnAccountWithGoals account = anAccountWhoseHouseIsAboutToBePromoted();
        GoalView house = theGoalCalled(account.goals(), "House deposit");
        GoalView holiday = theGoalCalled(account.goals(), "Holiday");
        account.reorder(List.of(house.id(), holiday.id()));
        assertThat(account.suggestedReallocation().worthSuggesting()).isTrue();

        // The order goes back to where it was, which is the customer changing their mind — and the
        // suggestion that was read a moment ago is about an account that no longer exists.
        account.reorder(List.of(holiday.id(), house.id()));
        AppliedReallocationView accepted = account.acceptTheSuggestedReallocation();

        assertThat(accepted.applied().worthSuggesting())
                .describedAs("a stale plan is not applied, and saying so is not a refusal")
                .isFalse();
        assertThat(accepted.applied().moves()).isEmpty();
        assertThat(accepted.allocations().goal(holiday.id()).allocation())
                .describedAs("not a cent moved")
                .isEqualByComparingTo("500.00");
        assertThat(accepted.allocations().goal(house.id()).allocation()).isEqualByComparingTo("0.00");
    }

    @Test
    void an_account_nobody_has_heard_of_is_refused_in_the_words_accounts_owns() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "no such account");
        long notAnAccount = account.id() + 1_000_000;

        assertThat(http.getForEntity("/api/savings-accounts/{id}/goals/suggested-reallocation",
                JsonNode.class, notAnAccount).getStatusCode().value())
                .describedAs("an account that is not one is answered before its goals are asked for")
                .isEqualTo(404);
        assertThat(http.postForEntity("/api/savings-accounts/{id}/goals/suggested-reallocation", null,
                JsonNode.class, notAnAccount).getStatusCode().value())
                .isEqualTo(404);
    }

    /**
     * The scenario the ticket is named after, set up and not yet reordered: a holiday that has been
     * saved into for a while, and a house deposit wanted sooner that nothing has been put towards.
     *
     * <p>Every figure divides by the capacity of 100.00 a week. The house wants 900.00 in five weeks,
     * which is 180.00 a week it cannot have; the five weeks left bring 500.00, so 400.00 has to come
     * out of money that is already saved. The holiday wants 600.00 in ten weeks and is holding 500.00
     * of it.
     */
    private AnAccountWithGoals anAccountWhoseHouseIsAboutToBePromoted() {
        AnAccountWithGoals account = new AnAccountWithGoals(http, "a promoted goal");
        account.savesUp("1500.00");
        GoalView holiday = account.add("Holiday", "600.00", account.today().plusWeeks(10));
        account.add("House deposit", "900.00", account.today().plusWeeks(5));
        account.canSave(A_HUNDRED_A_WEEK);
        account.putTowards(holiday.id(), "500.00");
        return account;
    }

    private static GoalView theGoalCalled(List<GoalView> goals, String name) {
        return goals.stream()
                .filter(goal -> name.equals(goal.name()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "no goal called \"" + name + "\" is on this account"));
    }
}
