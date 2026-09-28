package io.dataroots.savingstreak.potmembers;

import java.math.BigDecimal;

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

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A role is not a label on a list: changing it changes what the person may do, from the next request
 * onwards.
 *
 * <p><strong>Proved through behaviour rather than through the membership.</strong> A test that
 * changed a role and read the role back would pass just as happily if the word were written to a
 * column nothing consults — which is exactly the bug worth catching here, because every rule this
 * feature has is a question asked of that word. So a viewer who is promoted is asked to pay in, and a
 * contributor who is demoted is asked the same thing, and the answers are the whole assertion. Paying
 * in is the one capability a role decides that this application can already do; every rule that comes
 * after it — proposing, approving, closing — will read the same word from the same row.
 *
 * <p><strong>And immediately.</strong> No sign-in, no cache to warm and no second call in between:
 * the deposit that was refused a moment ago succeeds, with nothing else having happened. A role read
 * once and remembered would fail here.
 *
 * <p>Its own application and its own customers, for the reason ticket 01's pot tests give: a savings
 * account nobody holds has no business on the run's shared database.
 */
class ChangingARoleChangesWhatSomebodyMayDoApiTest extends ApiIntegrationTest {

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
                aDatabaseFileThatDoesNotExistYet("saving-streak-changing-a-role"));
    }

    /**
     * Story 18, the whole of it: somebody who joined to watch is promoted, and pays in.
     *
     * <p>The refusal before it is part of the test rather than scene-setting. Without it the
     * assertion would be that a contributor can pay in, which was true before this slice existed.
     */
    @Test
    void a_viewer_who_is_promoted_can_pay_in_straight_away() {
        SharedPotView pot = app.openAPot(ANKE, "Kitchen");
        String watching = app.aCustomerOfItsOwn("promoted from watching");
        joins(pot, watching, "VIEWER");
        assertThat(app.tryToDeposit(pot.savingsAccountId(), watching, "20.00").getStatusCode())
                .as("watching is all they may do, which is what the promotion is going to change")
                .isEqualTo(HttpStatus.FORBIDDEN);

        PotMemberView promoted = app.changeTheRole(pot.id(), watching, "CONTRIBUTOR", ANKE);

        assertThat(promoted.role())
                .as("the answer is the membership as it now reads")
                .isEqualTo("CONTRIBUTOR");
        assertThat(roleOf(pot, watching))
                .as("and so is the pot's own list of who can do what")
                .isEqualTo("CONTRIBUTOR");
        assertThat(app.deposit(pot.savingsAccountId(), watching, "20.00").amount())
                .as("and the very deposit that was refused a moment ago goes through")
                .isEqualByComparingTo("20.00");
        assertThat(app.potWith(pot.id()).moneyBalance()).isEqualByComparingTo("20.00");
    }

    /**
     * And the other way, which is the half that protects the pot: somebody who was paying in is
     * demoted, and the next deposit is refused in the words a viewer gets.
     */
    @Test
    void a_contributor_who_is_demoted_cannot_pay_in_any_more() {
        SharedPotView pot = app.openAPot(ANKE, "Greece 2027");
        String paying = app.aCustomerOfItsOwn("demoted from paying in");
        joins(pot, paying, "CONTRIBUTOR");
        app.deposit(pot.savingsAccountId(), paying, "30.00");

        app.changeTheRole(pot.id(), paying, "VIEWER", ANKE);

        ResponseEntity<JsonNode> refused =
                app.tryToDeposit(pot.savingsAccountId(), paying, "30.00");
        assertThat(refused.getStatusCode())
                .as("understood, from somebody the pot knows, who is no longer allowed")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(refused))
                .as("in the sentence a viewer already gets, because that is what they now are")
                .contains("viewer");
        assertThat(app.potWith(pot.id()).moneyBalance())
                .as("and the euros they paid in while they could are still the pot's")
                .isEqualByComparingTo(new BigDecimal("30.00"));
    }

    /**
     * Ownership is handed on by the same call, which is what story 18 is for when the person being
     * promoted is to administer the pot rather than only pay into it.
     */
    @Test
    void a_contributor_can_be_made_an_owner_and_may_then_invite_somebody() {
        SharedPotView pot = app.openAPot(ANKE, "New roof");
        String handedTo = app.aCustomerOfItsOwn("handed the ownership");
        String stranger = app.aCustomerOfItsOwn("nobody has invited yet");
        joins(pot, handedTo, "CONTRIBUTOR");

        app.changeTheRole(pot.id(), handedTo, "OWNER", ANKE);

        assertThat(roleOf(pot, handedTo)).isEqualTo("OWNER");
        assertThat(app.invite(pot.id(), handedTo, stranger, "VIEWER").invitedCustomerId())
                .as("who else is in the pot is now this member's decision too")
                .isEqualTo(app.customerIdOf(stranger));
    }

    /**
     * The word is read the way every other role word in this feature is read — trimmed and without
     * regard to case — because an owner typing into a box is not typing an enum.
     */
    @Test
    void the_role_is_read_however_it_was_typed() {
        SharedPotView pot = app.openAPot(ANKE, "Winter tyres");
        String member = app.aCustomerOfItsOwn("typed in lower case");
        joins(pot, member, "VIEWER");

        assertThat(app.changeTheRole(pot.id(), member, " contributor ", ANKE).role())
                .isEqualTo("CONTRIBUTOR");
    }

    /**
     * Changing a role changes the role and nothing else about the membership. When somebody joined
     * is part of the pot's story — it is what orders the list of members — and a change that quietly
     * reset it would make the person who was promoted look like the person who joined last.
     */
    @Test
    void the_member_keeps_the_moment_they_joined() {
        SharedPotView pot = app.openAPot(ANKE, "Attic");
        String member = app.aCustomerOfItsOwn("keeps their joining moment");
        joins(pot, member, "VIEWER");
        PotMemberView asTheyJoined = memberOf(pot, member);

        PotMemberView promoted = app.changeTheRole(pot.id(), member, "CONTRIBUTOR", ANKE);

        assertThat(promoted.joinedAt()).isEqualTo(asTheyJoined.joinedAt());
        assertThat(promoted.customerId()).isEqualTo(asTheyJoined.customerId());
        assertThat(promoted.name()).isEqualTo(asTheyJoined.name());
    }

    /**
     * An owner giving somebody the role they already hold is allowed and changes nothing, which is
     * what a form submitted twice does. A refusal here would be an application telling somebody off
     * for a double click.
     */
    @Test
    void giving_somebody_the_role_they_already_hold_leaves_them_holding_it() {
        SharedPotView pot = app.openAPot(ANKE, "Nothing to change");
        String member = app.aCustomerOfItsOwn("already holds the role");
        joins(pot, member, "CONTRIBUTOR");

        assertThat(app.changeTheRole(pot.id(), member, "CONTRIBUTOR", ANKE).role())
                .isEqualTo("CONTRIBUTOR");
        assertThat(roleOf(pot, member)).isEqualTo("CONTRIBUTOR");
    }

    /** Somebody invited and accepting, which is the only way anybody but an owner gets into a pot. */
    private static void joins(SharedPotView pot, String customerName, String role) {
        PotInvitationView sent = app.invite(pot.id(), ANKE, customerName, role);
        app.accept(pot.id(), sent.id(), customerName);
    }

    /** What the pot says somebody is, read off the list everybody else reads it off. */
    private static String roleOf(SharedPotView pot, String customerName) {
        return memberOf(pot, customerName).role();
    }

    private static PotMemberView memberOf(SharedPotView pot, String customerName) {
        return app.membersOfThePot(pot.id()).stream()
                .filter(member -> member.customerId().equals(app.customerIdOf(customerName)))
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
