package io.dataroots.savingstreak.potmembers;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.PotInvitationView;
import io.dataroots.savingstreak.support.PotMemberView;
import io.dataroots.savingstreak.support.SharedPotView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.potmembers.RoleChangesAsSomebodyWouldTypeThem.aChangeWithNoRoleAtAll;
import static io.dataroots.savingstreak.potmembers.RoleChangesAsSomebodyWouldTypeThem.aRoleChangeNobodyIsMaking;
import static io.dataroots.savingstreak.potmembers.RoleChangesAsSomebodyWouldTypeThem.aRoleOf;
import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * What somebody is to a pot is an owner's decision, and every other way of asking is refused in a
 * sentence somebody could read out loud.
 *
 * <p>The same 403 that inviting already answers, and for the same reason: a contributor promoting
 * themselves would be the whole role model undone in one request, and telling them the pot is not
 * there would be a lie they could disprove by refreshing the page. Somebody who is in the pot and
 * somebody who is in no pot at all are refused in the same words, for the reason the invitation
 * refusal gives — telling a stranger that they are not a member is telling them there is a pot here
 * to be a member of.
 *
 * <p><strong>A role change is about a member, and a member is somebody in this pot.</strong> A
 * customer who exists and is in no pot, and a customer number nobody answers to, are both a 404 about
 * the member — the pot cannot change what somebody who is not in it is to it, and the sentence names
 * whose membership was looked for.
 *
 * <p>Every refusal is followed by a read of the membership it was about, because a rule that refused
 * after writing the row would satisfy an assertion about the status and leave the role changed
 * anyway.
 *
 * <p>Its own application and its own customers, for the reason ticket 01's pot tests give.
 */
class ChangingARoleIsAnOwnersDecisionApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    /** Somebody who pays in and decides nothing about who else may. */
    private static String contributor;

    /** Somebody who joined to watch, so that "viewer" can be shown to mean something here too. */
    private static String viewer;

    /** Somebody in no pot at all, for the stranger who tries anyway. */
    private static String stranger;

    /** The pot everybody in this class is arguing about, with a contributor and a viewer in it. */
    private static SharedPotView pot;

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-role-change-is-an-owners"));
        contributor = app.aCustomerOfItsOwn("a contributor");
        viewer = app.aCustomerOfItsOwn("a viewer");
        stranger = app.aCustomerOfItsOwn("a stranger");
        pot = app.openAPot(ANKE, "Whose decision this is");
        joins(contributor, "CONTRIBUTOR");
        joins(viewer, "VIEWER");
    }

    /** A contributor pays in, and what everybody else may do is not theirs to say. */
    @Test
    void a_contributor_may_not_change_anybodys_role() {
        ResponseEntity<JsonNode> refused = app.tryToChangeTheRole(pot.id(), app.customerIdOf(viewer),
                aRoleOf("CONTRIBUTOR", app.customerIdOf(contributor)));

        assertThat(refused.getStatusCode())
                .as("understood, from somebody known, who is not allowed — which is 403")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(refused))
                .as("and it says what would have been needed, so the page can say who to ask")
                .contains("owner");
        assertThat(roleOf(viewer)).isEqualTo("VIEWER");
    }

    /** Least of all their own, which is the request this refusal really exists to stop. */
    @Test
    void a_contributor_may_not_promote_themselves() {
        ResponseEntity<JsonNode> refused = app.tryToChangeTheRole(pot.id(),
                app.customerIdOf(contributor), aRoleOf("OWNER", app.customerIdOf(contributor)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(roleOf(contributor)).isEqualTo("CONTRIBUTOR");
    }

    /** And a viewer decides even less, which is the whole content of the word. */
    @Test
    void a_viewer_may_not_change_anybodys_role() {
        ResponseEntity<JsonNode> refused = app.tryToChangeTheRole(pot.id(),
                app.customerIdOf(viewer), aRoleOf("CONTRIBUTOR", app.customerIdOf(viewer)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(refused)).contains("owner");
        assertThat(roleOf(viewer)).isEqualTo("VIEWER");
    }

    /** Somebody who is in no pot at all is refused in the same words, and for the same reason. */
    @Test
    void somebody_who_is_not_in_the_pot_at_all_may_not_change_anybodys_role() {
        ResponseEntity<JsonNode> refused = app.tryToChangeTheRole(pot.id(),
                app.customerIdOf(viewer), aRoleOf("OWNER", app.customerIdOf(stranger)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(refused)).contains("owner");
        assertThat(roleOf(viewer)).isEqualTo("VIEWER");
    }

    /**
     * Story 18 is about a member: somebody who is not in the pot has no role in it to change, and
     * the sentence says whose membership was looked for rather than leaving an owner wondering which
     * half of the request was wrong.
     */
    @Test
    void the_role_of_somebody_who_is_not_a_member_is_not_there_to_change() {
        ResponseEntity<JsonNode> refused = app.tryToChangeTheRole(pot.id(),
                app.customerIdOf(stranger), aRoleOf("CONTRIBUTOR", app.customerIdOf(ANKE)));

        assertThat(refused.getStatusCode())
                .as("a membership that is not there, which is an absence: " + refused.getBody())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonIn(refused))
                .contains(String.valueOf(app.customerIdOf(stranger)))
                .contains(String.valueOf(pot.id()));
        assertThat(app.membersOfThePot(pot.id()))
                .as("and nobody was let in by the attempt")
                .noneMatch(member -> member.customerId().equals(app.customerIdOf(stranger)));
    }

    /** A customer number nobody answers to is the same absence, for the same reason. */
    @Test
    void the_role_of_a_customer_nobody_has_heard_of_is_not_there_either() {
        long nobody = app.anIdNoCustomerHas();

        ResponseEntity<JsonNode> refused = app.tryToChangeTheRole(pot.id(), nobody,
                aRoleOf("CONTRIBUTOR", app.customerIdOf(ANKE)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonIn(refused)).contains(String.valueOf(nobody));
    }

    /** A customer this application has never heard of changes nobody's role, and is told which. */
    @Test
    void a_customer_nobody_has_heard_of_cannot_change_a_role() {
        long nobody = app.anIdNoCustomerHas();

        ResponseEntity<JsonNode> refused = app.tryToChangeTheRole(pot.id(),
                app.customerIdOf(viewer), aRoleOf("CONTRIBUTOR", nobody));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonIn(refused)).contains(String.valueOf(nobody));
        assertThat(roleOf(viewer)).isEqualTo("VIEWER");
    }

    /** And a pot nobody has heard of is answered as the absence it is, with the pot named back. */
    @Test
    void nobodys_role_can_be_changed_in_a_pot_that_does_not_exist() {
        long noSuchPot = pot.id() + 1_000;

        ResponseEntity<JsonNode> refused = app.tryToChangeTheRole(noSuchPot,
                app.customerIdOf(viewer), aRoleOf("CONTRIBUTOR", app.customerIdOf(ANKE)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonIn(refused)).contains(String.valueOf(noSuchPot));
    }

    /**
     * A role this application has never heard of is a form to fix, and the sentence says what the
     * three words are — which is the only thing the owner could do next.
     */
    @Test
    void a_role_nobody_has_heard_of_is_refused_with_the_three_that_exist() {
        ResponseEntity<JsonNode> refused = app.tryToChangeTheRole(pot.id(),
                app.customerIdOf(viewer), aRoleOf("TREASURER", app.customerIdOf(ANKE)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonIn(refused))
                .contains("TREASURER")
                .contains("OWNER")
                .contains("CONTRIBUTOR")
                .contains("VIEWER");
        assertThat(roleOf(viewer)).isEqualTo("VIEWER");
    }

    /** A form sent with the role box never filled in gets the same answer, because it is the same gap. */
    @Test
    void a_change_with_no_role_at_all_is_refused_in_the_same_way() {
        ResponseEntity<JsonNode> refused = app.tryToChangeTheRole(pot.id(),
                app.customerIdOf(viewer), aChangeWithNoRoleAtAll(app.customerIdOf(ANKE)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonIn(refused)).contains("OWNER");
        assertThat(roleOf(viewer)).isEqualTo("VIEWER");
    }

    /**
     * A body that says nothing about who is making the change is a malformed request rather than one
     * of the module's refusals — the same line every other endpoint in this feature draws between a
     * field that was never filled in and a field filled in with somebody who does not exist.
     */
    @Test
    void a_role_change_nobody_is_making_is_refused_as_a_malformed_request() {
        ResponseEntity<JsonNode> refused = app.tryToChangeTheRole(pot.id(),
                app.customerIdOf(viewer), aRoleChangeNobodyIsMaking("CONTRIBUTOR"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonIn(refused)).contains("customer");
        assertThat(roleOf(viewer)).isEqualTo("VIEWER");
    }

    /** Somebody invited and accepting, which is the only way anybody but an owner gets into a pot. */
    private static void joins(String customerName, String role) {
        PotInvitationView sent = app.invite(pot.id(), ANKE, customerName, role);
        app.accept(pot.id(), sent.id(), customerName);
    }

    /** What the pot says somebody is, read off the list everybody else reads it off. */
    private static String roleOf(String customerName) {
        return app.membersOfThePot(pot.id()).stream()
                .filter(member -> member.customerId().equals(app.customerIdOf(customerName)))
                .map(PotMemberView::role)
                .findFirst()
                .orElseThrow(() -> new AssertionError(customerName + " is not a member of the pot"));
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
