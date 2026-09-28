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

import static io.dataroots.savingstreak.potmembers.RoleChangesAsSomebodyWouldTypeThem.aRoleOf;
import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stories 20 and 21: a pot always has at least one owner, and the last one is told to pass ownership
 * on rather than allowed to leave the pot with nobody able to administer it.
 *
 * <p><strong>This is the reason the slice exists.</strong> Everything else about changing a role is
 * an owner's ordinary administration; this is the one rule that cannot be undone if it is got wrong.
 * A pot whose last owner demoted themselves has nobody who may invite, nobody who may promote anybody
 * back, and therefore no way back at all — the pot would be stuck with whatever membership it had at
 * the moment of the click, for good.
 *
 * <p><strong>From both directions</strong>, because the rule is about the pot rather than about the
 * person: the last owner is refused, and an owner with somebody else owning it beside them is not.
 * A guard that refused every owner demoting themselves would pass the first of those and quietly
 * make story 21's "pass ownership on first" impossible to follow.
 *
 * <p>And it is a rule about the pot's state rather than about who opened it, which the last test
 * says out loud: once the founder has stepped down, the member they handed it to is the last owner
 * and is refused in exactly the same words.
 *
 * <p><strong>A conflict rather than a bad request.</strong> What the owner typed is perfectly good —
 * CONTRIBUTOR is a role, they are a member, they are allowed to change roles — and it is the state of
 * the pot that will not allow it. A 400 would send them looking for a mistake in the form that is not
 * there; the sentence tells them what to do about the pot instead.
 *
 * <p>Its own application and its own customers, for the reason ticket 01's pot tests give.
 */
class APotAlwaysHasAtLeastOneOwnerApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-a-pot-keeps-an-owner"));
    }

    /**
     * Story 21: the only owner is refused, and told what to do first — which is the whole difference
     * between a rule and an obstacle.
     */
    @Test
    void the_only_owner_is_refused_and_told_to_pass_ownership_on_first() {
        SharedPotView pot = app.openAPot(ANKE, "Kitchen");
        joins(pot, app.aCustomerOfItsOwn("only ever a contributor"), "CONTRIBUTOR");

        ResponseEntity<JsonNode> refused = app.tryToChangeTheRole(pot.id(), app.customerIdOf(ANKE),
                aRoleOf("CONTRIBUTOR", app.customerIdOf(ANKE)));

        assertThat(refused.getStatusCode())
                .as("the form is fine and the pot's state will not allow it: " + refused.getBody())
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonIn(refused))
                .as("and it says what to do first, because the alternative is a pot nobody can "
                        + "administer and no way back")
                .contains("only owner")
                .contains("owner first");
        assertThat(roleOf(pot, ANKE))
                .as("and they are the owner they were")
                .isEqualTo("OWNER");
    }

    /** Down to a viewer is the same refusal, because it is the same loss of the pot's last owner. */
    @Test
    void the_only_owner_cannot_make_themselves_a_viewer_either() {
        SharedPotView pot = app.openAPot(ANKE, "Greece 2027");

        ResponseEntity<JsonNode> refused = app.tryToChangeTheRole(pot.id(), app.customerIdOf(ANKE),
                aRoleOf("VIEWER", app.customerIdOf(ANKE)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonIn(refused)).contains("only owner");
        assertThat(roleOf(pot, ANKE)).isEqualTo("OWNER");
    }

    /**
     * And the owner who is still an owner is not refused anything: giving themselves the role they
     * already hold is not a demotion, however the guard is written.
     */
    @Test
    void the_only_owner_may_still_be_made_an_owner() {
        SharedPotView pot = app.openAPot(ANKE, "Nothing changes");

        assertThat(app.changeTheRole(pot.id(), ANKE, "OWNER", ANKE).role()).isEqualTo("OWNER");
        assertThat(roleOf(pot, ANKE)).isEqualTo("OWNER");
    }

    /**
     * The other direction, and the point of the refusal above: pass ownership on, and stepping down
     * is allowed. This is the sequence story 21 tells somebody to follow, followed.
     */
    @Test
    void an_owner_may_step_down_once_somebody_else_owns_the_pot() {
        SharedPotView pot = app.openAPot(ANKE, "Handed on");
        String successor = app.aCustomerOfItsOwn("handed the pot");
        joins(pot, successor, "CONTRIBUTOR");
        app.changeTheRole(pot.id(), successor, "OWNER", ANKE);

        PotMemberView steppedDown = app.changeTheRole(pot.id(), ANKE, "CONTRIBUTOR", ANKE);

        assertThat(steppedDown.role())
                .as("there is another owner, so this is administration rather than orphaning")
                .isEqualTo("CONTRIBUTOR");
        assertThat(app.membersOfThePot(pot.id()))
                .as("and the pot still has somebody who may administer it")
                .anyMatch(member -> "OWNER".equals(member.role()));
    }

    /**
     * And the guard is about the pot rather than about the person who opened it: the successor is
     * now the last owner, and is refused in the same words the founder was.
     */
    @Test
    void the_member_the_pot_was_handed_to_is_then_the_last_owner_themselves() {
        SharedPotView pot = app.openAPot(ANKE, "Handed on and stuck with it");
        String successor = app.aCustomerOfItsOwn("last owner in their turn");
        joins(pot, successor, "CONTRIBUTOR");
        app.changeTheRole(pot.id(), successor, "OWNER", ANKE);
        app.changeTheRole(pot.id(), ANKE, "CONTRIBUTOR", ANKE);

        ResponseEntity<JsonNode> refused = app.tryToChangeTheRole(pot.id(),
                app.customerIdOf(successor), aRoleOf("VIEWER", app.customerIdOf(successor)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonIn(refused)).contains("only owner");
        assertThat(roleOf(pot, successor)).isEqualTo("OWNER");
    }

    /**
     * Two owners, and one demotes the other. Allowed, because the pot keeps an owner — and worth its
     * own test because it is the one shape of demotion where the person refused and the person being
     * demoted are not the same person.
     */
    @Test
    void one_of_two_owners_may_demote_the_other() {
        SharedPotView pot = app.openAPot(ANKE, "Two owners");
        String theOther = app.aCustomerOfItsOwn("the other owner");
        joins(pot, theOther, "OWNER");

        assertThat(app.changeTheRole(pot.id(), theOther, "CONTRIBUTOR", ANKE).role())
                .isEqualTo("CONTRIBUTOR");
        assertThat(roleOf(pot, ANKE))
                .as("and the one who did it is still the owner they were")
                .isEqualTo("OWNER");
    }

    /** Somebody invited and accepting, which is the only way anybody but an owner gets into a pot. */
    private static void joins(SharedPotView pot, String customerName, String role) {
        PotInvitationView sent = app.invite(pot.id(), ANKE, customerName, role);
        app.accept(pot.id(), sent.id(), customerName);
    }

    /** What the pot says somebody is, read off the list everybody else reads it off. */
    private static String roleOf(SharedPotView pot, String customerName) {
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
