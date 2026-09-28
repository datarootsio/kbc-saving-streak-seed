package io.dataroots.savingstreak.potgoals;

import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.GoalView;
import io.dataroots.savingstreak.support.PotInvitationView;
import io.dataroots.savingstreak.support.SharedPotView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a shared pot is saving for is its owner's decision, and everybody else is refused in a
 * sentence they could read out loud.
 *
 * <p><strong>A pot's goals are goals on the pot's savings account, and that is the whole of the
 * feature.</strong> Every request in this class goes to the goal endpoints that already existed,
 * with the bodies they already took, and the answers are the answers a personal account gives. The
 * only new thing on the wire is {@code ?customerId=}, saying who is asking — so the claim these
 * tests make is not that shared goals work, but that they are the same goals and that one rule now
 * stands in front of them.
 *
 * <p>The refusal is a 403, the same one inviting somebody and changing a role already answer, and
 * for the same reason: the request is understood, the caller is known, and they are not allowed. A
 * contributor, a viewer, somebody in no pot at all and a request naming nobody are all told what
 * would have been needed rather than told the pot is not there — telling a stranger that they are
 * not a member is telling them there is a pot here to be a member of.
 *
 * <p>Every refusal is followed by a read of the thing it was about, because a rule that refused
 * after writing the row would satisfy an assertion about the status and leave the goal changed
 * anyway.
 *
 * <p>A pot of its own per test, and its own application, for the reason ticket 01's pot tests give.
 * The order of a pot's goals is a permutation of exactly the goals on that account, so two tests
 * opening goals on one pot would be two tests reordering each other's.
 */
class APotsGoalsAreAnOwnersDecisionApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    /** Somebody who pays into the pot and decides nothing about what it is for. */
    private static String contributor;

    /** Somebody who joined to watch, so that "viewer" can be shown to mean something here too. */
    private static String viewer;

    /** Somebody in no pot at all, for the stranger who tries anyway. */
    private static String stranger;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-pots-goals-are-an-owners"));
        contributor = app.aCustomerOfItsOwn("a contributor of a pots goals");
        viewer = app.aCustomerOfItsOwn("a viewer of a pots goals");
        stranger = app.aCustomerOfItsOwn("a stranger to a pots goals");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /** Story 41: the group saves for something named rather than for a number. */
    @Test
    void an_owner_adds_a_goal_to_the_pot() {
        SharedPotView pot = aPotEverybodyIsIn("What we are saving for");

        GoalView kitchen = app.openAGoalAs(pot.savingsAccountId(), ANKE, "Kitchen", "1000.00");

        assertThat(kitchen.name()).isEqualTo("Kitchen");
        assertThat(kitchen.target()).isEqualByComparingTo("1000.00");
        assertThat(app.goalsAsReadBy(pot.savingsAccountId(), ANKE))
                .as("and the pot is saving for it from the moment it is opened")
                .extracting(GoalView::name)
                .containsExactly("Kitchen");
    }

    /** Story 42: a goal on a pot is renamed, retargeted and rescheduled like any other. */
    @Test
    void an_owner_changes_a_goal_of_the_pot() {
        SharedPotView pot = aPotEverybodyIsIn("A goal that changes its mind");
        GoalView goal = app.openAGoalAs(pot.savingsAccountId(), ANKE, "Kitchen", "1000.00");

        ResponseEntity<JsonNode> changed = app.tryToRenameTheGoal(pot.savingsAccountId(), goal.id(),
                ANKE, "Kitchen and floor");

        assertThat(changed.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(app.goalsAsReadBy(pot.savingsAccountId(), ANKE))
                .extracting(GoalView::name)
                .containsExactly("Kitchen and floor");
    }

    /** The order of importance is the group's, and the owner is who says what it is. */
    @Test
    void an_owner_reorders_the_pots_goals() {
        SharedPotView pot = aPotEverybodyIsIn("Two things at once");
        GoalView first = app.openAGoalAs(pot.savingsAccountId(), ANKE, "Kitchen", "1000.00");
        GoalView second = app.openAGoalAs(pot.savingsAccountId(), ANKE, "Greece", "500.00");

        ResponseEntity<JsonNode> reordered = app.tryToReorderTheGoals(pot.savingsAccountId(), ANKE,
                List.of(second.id(), first.id()));

        assertThat(reordered.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(app.goalsAsReadBy(pot.savingsAccountId(), ANKE))
                .as("most important first, which is the order the owner just set")
                .extracting(GoalView::name)
                .containsExactly("Greece", "Kitchen");
    }

    /** The one figure of the plan the group fixes by hand, which is still the owner's to fix. */
    @Test
    void an_owner_pins_a_weekly_amount_to_a_pots_goal() {
        SharedPotView pot = aPotEverybodyIsIn("A goal with a figure on it");
        GoalView goal = app.openAGoalAs(pot.savingsAccountId(), ANKE, "Kitchen", "1000.00");

        ResponseEntity<JsonNode> pinned = app.tryToPinAWeeklyAmount(pot.savingsAccountId(),
                goal.id(), ANKE, "25.00");

        assertThat(pinned.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(theOnlyGoalOf(pot).pinnedWeeklyAmount()).isEqualByComparingTo("25.00");

        ResponseEntity<JsonNode> unpinned = app.tryToUnpinTheWeeklyAmount(pot.savingsAccountId(),
                goal.id(), ANKE);

        assertThat(unpinned.getStatusCode())
                .as("and taking it off again is the same decision, so it is the same owner's")
                .isEqualTo(HttpStatus.OK);
        assertThat(theOnlyGoalOf(pot).pinnedWeeklyAmount()).isNull();
    }

    /** Giving up on what the group was saving for is as much the owner's decision as starting it. */
    @Test
    void an_owner_gives_up_a_pots_goal() {
        SharedPotView pot = aPotEverybodyIsIn("Something we gave up on");
        GoalView goal = app.openAGoalAs(pot.savingsAccountId(), ANKE, "Kitchen", "1000.00");

        ResponseEntity<JsonNode> abandoned = app.tryToAbandonTheGoal(pot.savingsAccountId(),
                goal.id(), ANKE);

        assertThat(abandoned.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(app.goalsAsReadBy(pot.savingsAccountId(), ANKE))
                .as("a goal given up on leaves the order it was in")
                .isEmpty();
    }

    /** Story 45: without it the projections on a shared goal would mean nothing. */
    @Test
    void an_owner_declares_the_pots_weekly_saving_capacity() {
        SharedPotView pot = aPotEverybodyIsIn("What we can put away each week");

        ResponseEntity<JsonNode> declared = app.tryToDeclareTheSavingCapacity(
                pot.savingsAccountId(), ANKE, "60.00");

        assertThat(declared.getStatusCode()).isEqualTo(HttpStatus.OK);
        ResponseEntity<JsonNode> read = app.tryToReadTheSavingCapacity(pot.savingsAccountId(), ANKE);
        assertThat(read.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(read.getBody().get("weeklyCapacity").decimalValue())
                .isEqualByComparingTo("60.00");
    }

    /** Story 44: a contributor pays in, and what the money is for is not theirs to say. */
    @Test
    void a_contributor_may_not_add_a_goal_to_the_pot() {
        SharedPotView pot = aPotEverybodyIsIn("Not the contributor's decision");

        ResponseEntity<JsonNode> refused = app.tryToOpenAGoal(pot.savingsAccountId(), contributor,
                "A kitchen of my own choosing", "1000.00");

        assertThat(refused.getStatusCode())
                .as("understood, from somebody known, who is not allowed — which is 403")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(refused))
                .as("and it says what would have been needed, so the page can say who to ask")
                .contains("owner");
        assertThat(app.goalsAsReadBy(pot.savingsAccountId(), ANKE))
                .as("and nothing was written by the attempt")
                .isEmpty();
    }

    /** Every other way of changing a goal is the same decision, so it gets the same answer. */
    @Test
    void a_contributor_may_not_change_reorder_pin_or_abandon_a_pots_goal() {
        SharedPotView pot = aPotEverybodyIsIn("Still not the contributor's decision");
        GoalView first = app.openAGoalAs(pot.savingsAccountId(), ANKE, "Kitchen", "1000.00");
        GoalView second = app.openAGoalAs(pot.savingsAccountId(), ANKE, "Greece", "500.00");

        assertThat(refusalOf(app.tryToRenameTheGoal(pot.savingsAccountId(), first.id(), contributor,
                "Something else")))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refusalOf(app.tryToReorderTheGoals(pot.savingsAccountId(), contributor,
                List.of(second.id(), first.id()))))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refusalOf(app.tryToPinAWeeklyAmount(pot.savingsAccountId(), first.id(),
                contributor, "25.00")))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refusalOf(app.tryToUnpinTheWeeklyAmount(pot.savingsAccountId(), first.id(),
                contributor)))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refusalOf(app.tryToAbandonTheGoal(pot.savingsAccountId(), first.id(),
                contributor)))
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(app.goalsAsReadBy(pot.savingsAccountId(), ANKE))
                .as("and the pot is saving for exactly what it was saving for, in the same order")
                .extracting(GoalView::name)
                .containsExactly("Kitchen", "Greece");
        assertThat(app.goalsAsReadBy(pot.savingsAccountId(), ANKE))
                .extracting(GoalView::pinnedWeeklyAmount)
                .containsOnlyNulls();
    }

    /** And the capacity the projections are quoted against is the owner's sentence, not theirs. */
    @Test
    void a_contributor_may_not_declare_the_pots_weekly_saving_capacity() {
        SharedPotView pot = aPotEverybodyIsIn("A capacity somebody else declared");
        app.tryToDeclareTheSavingCapacity(pot.savingsAccountId(), ANKE, "60.00");

        ResponseEntity<JsonNode> refused = app.tryToDeclareTheSavingCapacity(
                pot.savingsAccountId(), contributor, "5.00");

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(refused)).contains("owner");
        assertThat(app.tryToReadTheSavingCapacity(pot.savingsAccountId(), ANKE).getBody()
                .get("weeklyCapacity").decimalValue())
                .as("and the figure the owner declared is the figure that stands")
                .isEqualByComparingTo("60.00");
    }

    /** A viewer decides even less, which is the whole content of the word. */
    @Test
    void a_viewer_may_not_write_to_a_pots_goal_or_its_capacity() {
        SharedPotView pot = aPotEverybodyIsIn("Nothing the viewer may touch");
        GoalView goal = app.openAGoalAs(pot.savingsAccountId(), ANKE, "Kitchen", "1000.00");

        assertThat(refusalOf(app.tryToOpenAGoal(pot.savingsAccountId(), viewer, "Mine", "10.00")))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refusalOf(app.tryToRenameTheGoal(pot.savingsAccountId(), goal.id(), viewer,
                "Something else")))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refusalOf(app.tryToAbandonTheGoal(pot.savingsAccountId(), goal.id(), viewer)))
                .isEqualTo(HttpStatus.FORBIDDEN);
        ResponseEntity<JsonNode> capacity = app.tryToDeclareTheSavingCapacity(
                pot.savingsAccountId(), viewer, "5.00");
        assertThat(capacity.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(capacity)).contains("owner");

        assertThat(app.goalsAsReadBy(pot.savingsAccountId(), ANKE))
                .extracting(GoalView::name)
                .containsExactly("Kitchen");
    }

    /**
     * Somebody in no pot at all may neither read a pot's goals nor change them, and the read
     * refusal is the one worth having: a pot's goals say what two other people are saving for.
     */
    @Test
    void a_stranger_may_neither_read_nor_write_a_pots_goals() {
        SharedPotView pot = aPotEverybodyIsIn("None of a stranger's business");
        GoalView goal = app.openAGoalAs(pot.savingsAccountId(), ANKE, "Kitchen", "1000.00");

        ResponseEntity<JsonNode> reading = app.tryToReadTheGoals(pot.savingsAccountId(), stranger);

        assertThat(reading.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(reading))
                .as("told what would have been needed, and not that there is no pot here")
                .contains("member");
        assertThat(refusalOf(app.tryToReadTheGoal(pot.savingsAccountId(), goal.id(), stranger)))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refusalOf(app.tryToReadTheAllocations(pot.savingsAccountId(), stranger)))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refusalOf(app.tryToReadTheSavingCapacity(pot.savingsAccountId(), stranger)))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refusalOf(app.tryToOpenAGoal(pot.savingsAccountId(), stranger, "Mine", "10.00")))
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(refusalOf(app.tryToAbandonTheGoal(pot.savingsAccountId(), goal.id(), stranger)))
                .isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(app.goalsAsReadBy(pot.savingsAccountId(), ANKE))
                .extracting(GoalView::name)
                .containsExactly("Kitchen");
    }

    /**
     * A request that names nobody at all is nobody, and is refused in the same words a stranger is
     * — which is also what the goals screen of this application sends today, and is exactly why a
     * pot's account cannot be read by it without somebody signing the request.
     */
    @Test
    void a_request_that_names_nobody_is_refused_the_way_a_stranger_is() {
        SharedPotView pot = aPotEverybodyIsIn("Nobody said who was asking");
        GoalView goal = app.openAGoalAs(pot.savingsAccountId(), ANKE, "Kitchen", "1000.00");

        ResponseEntity<JsonNode> reading = app.tryToReadTheGoals(pot.savingsAccountId(), null);

        assertThat(reading.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(reading)).contains("member");
        ResponseEntity<JsonNode> writing = app.tryToAbandonTheGoal(pot.savingsAccountId(),
                goal.id(), null);
        assertThat(writing.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(writing)).contains("owner");
        assertThat(app.goalsAsReadBy(pot.savingsAccountId(), ANKE))
                .extracting(GoalView::name)
                .containsExactly("Kitchen");
    }

    /**
     * A pot with an owner, a contributor and a viewer in it, opened for one test and no other.
     *
     * <p>Over HTTP the whole way, invitation and acceptance included, because that is the only way
     * anybody but the opener gets into a pot.
     */
    private static GoalView theOnlyGoalOf(SharedPotView pot) {
        List<GoalView> goals = app.goalsAsReadBy(pot.savingsAccountId(), ANKE);
        assertThat(goals).hasSize(1);
        return goals.get(0);
    }

    private static SharedPotView aPotEverybodyIsIn(String name) {
        SharedPotView pot = app.openAPot(ANKE, name);
        joins(pot.id(), contributor, "CONTRIBUTOR");
        joins(pot.id(), viewer, "VIEWER");
        return pot;
    }

    private static void joins(long potId, String customerName, String role) {
        PotInvitationView sent = app.invite(potId, ANKE, customerName, role);
        app.accept(potId, sent.id(), customerName);
    }

    /** The status of a refusal, insisted on as one: a 200 here would mean the rule did not run. */
    private static HttpStatus refusalOf(ResponseEntity<JsonNode> answered) {
        assertThat(answered.getStatusCode().isError())
                .as("this was expected to be refused, and answered " + answered.getBody())
                .isTrue();
        return HttpStatus.valueOf(answered.getStatusCode().value());
    }

    /** The sentence the customer is shown, which is the whole of what they get to act on. */
    private static String reasonIn(ResponseEntity<JsonNode> refused) {
        assertThat(refused.getBody())
                .as("a refusal answers with a problem body")
                .isNotNull();
        assertThat(refused.getBody().hasNonNull("detail"))
                .as("a refusal carries its reason in detail, and this one answered " + refused.getBody())
                .isTrue();
        return refused.getBody().get("detail").asText();
    }
}
