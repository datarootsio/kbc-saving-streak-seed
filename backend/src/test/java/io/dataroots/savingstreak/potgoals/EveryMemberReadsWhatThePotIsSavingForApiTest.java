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
 * Story 43: every member of a shared pot reads its goals, their order, their progress and whether
 * they are on track, using the goals screens that already exist.
 *
 * <p><strong>The claim is that there is nothing new to understand.</strong> A shared goal is a goal
 * on the pot's savings account, so the list a viewer reads is the list the owner reads — same
 * fields, same order, same derived figures — and the assertion that says so is that all three
 * members' readings are equal to each other, field for field. A page rendering a pot's goals is the
 * page that renders a person's.
 *
 * <p>Whatever their role: an owner writes them, a contributor pays towards them, and a viewer
 * watches. All three see the same thing, because seeing is not deciding — a viewer who could not
 * see what the money was for would be watching a number, which is the one thing the word is not
 * supposed to mean.
 *
 * <p>The goal is given a deadline and the pot a weekly capacity, because without them there is no
 * projection and "on track" would be a field nobody had filled in. That is also story 45 from the
 * reading end: the capacity is what makes the status and the arrival date mean something.
 *
 * <p>Its own application, for the reason ticket 01's pot tests give.
 */
class EveryMemberReadsWhatThePotIsSavingForApiTest extends ApiIntegrationTest {

    /** Four weeks of the declared capacity, which is more than the goal below still needs. */
    private static final int A_MONTH_OF_WEEKS = 28;

    private static AnApplicationWithAClockToMove app;

    private static String contributor;
    private static String viewer;

    /** The pot every method here reads, set up once because no method here changes it. */
    private static SharedPotView pot;

    /** The goal with a deadline, which is the one that has a status worth reading. */
    private static GoalView kitchen;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-every-member-reads-the-goals"));
        contributor = app.aCustomerOfItsOwn("a contributor reading the goals");
        viewer = app.aCustomerOfItsOwn("a viewer reading the goals");
        pot = app.openAPot(ANKE, "Kitchen and Greece");
        joins(contributor, "CONTRIBUTOR");
        joins(viewer, "VIEWER");

        app.tryToDeclareTheSavingCapacity(pot.savingsAccountId(), ANKE, "100.00");
        kitchen = app.openAGoalAs(pot.savingsAccountId(), ANKE, "Kitchen", "200.00",
                app.theDateTheClockReads().plusDays(A_MONTH_OF_WEEKS));
        app.openAGoalAs(pot.savingsAccountId(), ANKE, "Greece", "500.00");

        // Money in the pot, paid in by the contributor from their own current account, so that the
        // progress every member reads below is progress somebody actually made.
        app.deposit(pot.savingsAccountId(), contributor, "60.00");
        app.tryToAllocate(pot.savingsAccountId(), kitchen.id(), ANKE, "60.00");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /** The owner's own reading, which is the one the other two are held against. */
    @Test
    void the_owner_reads_the_pots_goals_in_order_with_their_progress_and_their_status() {
        List<GoalView> asTheOwnerSeesThem = app.goalsAsReadBy(pot.savingsAccountId(), ANKE);

        assertThat(asTheOwnerSeesThem)
                .as("most important first, which is the order they were opened in")
                .extracting(GoalView::name)
                .containsExactly("Kitchen", "Greece");
        assertThat(asTheOwnerSeesThem)
                .extracting(GoalView::rank)
                .containsExactly(1, 2);
        assertThat(asTheOwnerSeesThem.get(0).allocation())
                .as("the progress: what the pot has put behind this goal")
                .isEqualByComparingTo("60.00");
        assertThat(asTheOwnerSeesThem.get(0).stillNeeded())
                .as("and what is left of its target")
                .isEqualByComparingTo("140.00");
        assertThat(asTheOwnerSeesThem.get(0).status())
                .as("and whether it is on track, which the deadline and the capacity decide")
                .isEqualTo("ON_TRACK");
        assertThat(asTheOwnerSeesThem.get(0).willBeReachedOn())
                .as("with the Monday it arrives on at the rate the plan is filling it")
                .isNotNull();
    }

    /** A contributor pays towards the goals and reads exactly what the owner reads. */
    @Test
    void a_contributor_reads_the_very_same_goals() {
        assertThat(app.goalsAsReadBy(pot.savingsAccountId(), contributor))
                .as("field for field, which is the whole of \"a shared goal needs nothing new\"")
                .isEqualTo(app.goalsAsReadBy(pot.savingsAccountId(), ANKE));
    }

    /** And so does a viewer, because watching is what a viewer is for. */
    @Test
    void a_viewer_reads_the_very_same_goals() {
        assertThat(app.goalsAsReadBy(pot.savingsAccountId(), viewer))
                .isEqualTo(app.goalsAsReadBy(pot.savingsAccountId(), ANKE));
    }

    /** One goal read on its own is the same answer to the same three people. */
    @Test
    void every_member_reads_one_of_the_pots_goals_on_its_own() {
        for (String member : List.of(ANKE, contributor, viewer)) {
            ResponseEntity<JsonNode> read =
                    app.tryToReadTheGoal(pot.savingsAccountId(), kitchen.id(), member);
            assertThat(read.getStatusCode())
                    .describedAs(member + " reading goal " + kitchen.id())
                    .isEqualTo(HttpStatus.OK);
            assertThat(read.getBody().get("name").asText()).isEqualTo("Kitchen");
        }
    }

    /** What the pot has claimed of its balance and what no goal has claimed, read by all three. */
    @Test
    void every_member_reads_what_the_pot_has_allocated() {
        for (String member : List.of(ANKE, contributor, viewer)) {
            ResponseEntity<JsonNode> read =
                    app.tryToReadTheAllocations(pot.savingsAccountId(), member);
            assertThat(read.getStatusCode())
                    .describedAs(member + " reading the allocations on the pot")
                    .isEqualTo(HttpStatus.OK);
            assertThat(read.getBody().get("allocated").decimalValue())
                    .isEqualByComparingTo("60.00");
        }
    }

    /** And the weekly capacity every projection above is quoted against. */
    @Test
    void every_member_reads_the_weekly_capacity_the_owner_declared() {
        for (String member : List.of(ANKE, contributor, viewer)) {
            ResponseEntity<JsonNode> read =
                    app.tryToReadTheSavingCapacity(pot.savingsAccountId(), member);
            assertThat(read.getStatusCode())
                    .describedAs(member + " reading the pot's weekly capacity")
                    .isEqualTo(HttpStatus.OK);
            assertThat(read.getBody().get("weeklyCapacity").decimalValue())
                    .isEqualByComparingTo("100.00");
        }
    }

    private static void joins(String customerName, String role) {
        PotInvitationView sent = app.invite(pot.id(), ANKE, customerName, role);
        app.accept(pot.id(), sent.id(), customerName);
    }
}
