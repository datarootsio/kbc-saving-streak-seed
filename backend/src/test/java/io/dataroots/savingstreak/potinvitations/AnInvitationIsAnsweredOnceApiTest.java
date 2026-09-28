package io.dataroots.savingstreak.potinvitations;

import com.fasterxml.jackson.databind.JsonNode;
import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.PotInvitationView;
import io.dataroots.savingstreak.support.ScheduledJobView;
import io.dataroots.savingstreak.support.SharedPotView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * An invitation is answered once, by the person it was addressed to, and it waits for them for as
 * long as it takes.
 *
 * <p><strong>Once</strong> is the rule the whole class is about, and it is one rule rather than
 * four: an invitation that is not waiting can be neither accepted, nor declined, nor revoked, and it
 * does not matter which of the three closed it. An invitation answered twice would be a membership
 * written twice — the database would refuse the second as a duplicate and the customer would be
 * shown a stack trace — or a role changed by somebody clicking an old email, which is exactly what
 * story 13 says must not happen.
 *
 * <p><strong>By the person it was addressed to</strong>, which is a refusal this application has
 * never had to make before: the request is understood, the caller is known, and they are not the one
 * being asked. That is 403 and nothing else — not 404, because pretending the invitation is missing
 * would tell a member of the pot that the invitation they can see in the pot's own list is not
 * there.
 *
 * <p><strong>For as long as it takes.</strong> Nothing in this application expires an invitation,
 * and the test that says so winds the clock more than a year on and fires every job the application
 * has before answering it. That is worth a test rather than a comment: "we decided not to build
 * expiry" is invisible in the code, and a nightly sweep added later by somebody who did not read the
 * spec would be caught here.
 *
 * <p>Its own application and a second customer of its own, for the reasons
 * {@code AnInvitationMakesTheCustomerWhoAcceptsItAMemberApiTest} gives.
 */
class AnInvitationIsAnsweredOnceApiTest extends ApiIntegrationTest {

    /**
     * Comfortably more than a year, so that anything counted in months or in anniversaries has had
     * its chance to fire. Days, because that is what the clock endpoint takes.
     */
    private static final int MORE_THAN_A_YEAR_IN_DAYS = 400;

    private static AnApplicationWithAClockToMove app;

    /** The customer the invitations here are addressed to. */
    private static String invited;

    /** Somebody who is neither the owner nor the person being asked: the third party in the room. */
    private static String bystander;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-an-invitation-is-answered-once"));
        invited = app.aCustomerOfItsOwn("invited once");
        bystander = app.aCustomerOfItsOwn("a bystander");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /** Accepting twice: the second one is refused, and the membership is not written again. */
    @Test
    void an_invitation_already_accepted_cannot_be_accepted_again() {
        PotInvitationView sent = anInvitationTo("Accepted already");
        app.accept(sent.potId(), sent.id(), invited);

        ResponseEntity<JsonNode> refused =
                app.tryToAccept(sent.potId(), sent.id(), app.customerIdOf(invited));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonIn(refused))
                .as("and it says where the invitation stands, so the page can stop asking")
                .contains("ACCEPTED");
        assertThat(app.membersOfThePot(sent.potId()))
                .as("one answer, one membership")
                .hasSize(2);
    }

    /** And it cannot be declined either, because the answer already given is the answer. */
    @Test
    void an_invitation_already_accepted_cannot_be_declined() {
        PotInvitationView sent = anInvitationTo("Accepted, then declined");
        app.accept(sent.potId(), sent.id(), invited);

        ResponseEntity<JsonNode> refused =
                app.tryToDecline(sent.potId(), sent.id(), app.customerIdOf(invited));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(app.membersOfThePot(sent.potId()))
                .as("a declined acceptance would be a member quietly removed, and it is not")
                .hasSize(2);
    }

    /** A declined invitation is closed as firmly as an accepted one: a "no" is not a maybe. */
    @Test
    void an_invitation_already_declined_cannot_be_accepted() {
        PotInvitationView sent = anInvitationTo("Declined, then thought better of");
        app.decline(sent.potId(), sent.id(), invited);

        ResponseEntity<JsonNode> refused =
                app.tryToAccept(sent.potId(), sent.id(), app.customerIdOf(invited));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonIn(refused)).contains("DECLINED");
        assertThat(app.membersOfThePot(sent.potId()))
                .as("nobody joined a pot they had said no to")
                .hasSize(1);
    }

    /**
     * A revoked invitation can no longer be accepted, which is the whole point of revoking one: an
     * owner who mistyped an address takes it back, and the person who finds it in their inbox
     * afterwards cannot let themselves in.
     */
    @Test
    void a_revoked_invitation_can_no_longer_be_accepted() {
        PotInvitationView sent = anInvitationTo("Taken back");
        app.revoke(sent.potId(), sent.id(), ANKE);

        ResponseEntity<JsonNode> refused =
                app.tryToAccept(sent.potId(), sent.id(), app.customerIdOf(invited));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonIn(refused)).contains("REVOKED");
        assertThat(app.membersOfThePot(sent.potId())).hasSize(1);
    }

    /** And an invitation that has been answered cannot be revoked, for the same one reason. */
    @Test
    void an_answered_invitation_cannot_be_revoked() {
        PotInvitationView sent = anInvitationTo("Answered before it was taken back");
        app.decline(sent.potId(), sent.id(), invited);

        ResponseEntity<JsonNode> refused =
                app.tryToRevoke(sent.potId(), sent.id(), app.customerIdOf(ANKE));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonIn(refused)).contains("DECLINED");
        assertThat(app.invitationsIssuedBy(sent.potId()))
                .as("and the record still says what actually happened to it")
                .singleElement()
                .extracting(PotInvitationView::state)
                .isEqualTo("DECLINED");
    }

    /** Nor can the same invitation be revoked twice. */
    @Test
    void a_revoked_invitation_cannot_be_revoked_again() {
        PotInvitationView sent = anInvitationTo("Taken back twice");
        app.revoke(sent.potId(), sent.id(), ANKE);

        ResponseEntity<JsonNode> refused =
                app.tryToRevoke(sent.potId(), sent.id(), app.customerIdOf(ANKE));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonIn(refused)).contains("REVOKED");
    }

    /**
     * Somebody else's invitation is not theirs to answer, and the refusal says so rather than
     * pretending the invitation is not there.
     */
    @Test
    void accepting_an_invitation_addressed_to_somebody_else_is_refused() {
        PotInvitationView sent = anInvitationTo("Not yours to answer");

        ResponseEntity<JsonNode> refused =
                app.tryToAccept(sent.potId(), sent.id(), app.customerIdOf(bystander));

        assertThat(refused.getStatusCode())
                .as("understood, from somebody known, who is not allowed — which is 403")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(refused)).contains("invitation");
        assertThat(app.membersOfThePot(sent.potId())).hasSize(1);
        assertThat(app.invitationsWaitingFor(invited))
                .as("and it is still waiting for the person it was actually addressed to")
                .anyMatch(waiting -> waiting.id().equals(sent.id()));
    }

    /** Declining somebody else's is refused in the same words, because it is the same objection. */
    @Test
    void declining_an_invitation_addressed_to_somebody_else_is_refused() {
        PotInvitationView sent = anInvitationTo("Not yours to decline");

        ResponseEntity<JsonNode> refused =
                app.tryToDecline(sent.potId(), sent.id(), app.customerIdOf(bystander));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(app.invitationsWaitingFor(invited))
                .anyMatch(waiting -> waiting.id().equals(sent.id()));
    }

    /**
     * An owner cannot answer on somebody's behalf either, which is the same rule seen from the other
     * side: sending an invitation is not the same as being answered.
     */
    @Test
    void an_owner_cannot_accept_on_the_invited_customers_behalf() {
        PotInvitationView sent = anInvitationTo("Answering for somebody");

        ResponseEntity<JsonNode> refused =
                app.tryToAccept(sent.potId(), sent.id(), app.customerIdOf(ANKE));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(app.membersOfThePot(sent.potId())).hasSize(1);
    }

    /** An invitation nobody ever issued is an absence, which is what a 404 says. */
    @Test
    void an_invitation_that_does_not_exist_is_not_found() {
        PotInvitationView sent = anInvitationTo("Something to count past");
        long noSuchInvitation = sent.id() + 1_000;

        ResponseEntity<JsonNode> refused =
                app.tryToAccept(sent.potId(), noSuchInvitation, app.customerIdOf(invited));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonIn(refused)).contains(String.valueOf(noSuchInvitation));
    }

    /**
     * And an invitation that exists but belongs to another pot is not this pot's to answer: the pot
     * in the path is part of what identifies it, so answering it under the wrong pot is answering
     * something that is not there.
     */
    @Test
    void an_invitation_answered_under_the_wrong_pot_is_not_found() {
        PotInvitationView sent = anInvitationTo("The pot it belongs to");
        SharedPotView somewhereElse = app.openAPot(ANKE, "The pot it does not belong to");

        ResponseEntity<JsonNode> refused =
                app.tryToAccept(somewhereElse.id(), sent.id(), app.customerIdOf(invited));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(app.membersOfThePot(somewhereElse.id())).hasSize(1);
    }

    /**
     * An invitation waits, and goes on waiting. More than a year passes and every job this
     * application has is fired against it, and it is still there to be accepted.
     *
     * <p>Every job rather than the nightly three, asked of the application's own list rather than
     * named here: the point is that <em>nothing</em> in this application touches an invitation, and
     * a test naming the jobs it knows about would go on passing after somebody added a sweep that
     * did.
     */
    @Test
    void an_invitation_does_not_expire_however_long_it_waits() {
        PotInvitationView sent = anInvitationTo("Still waiting");

        app.daysPass(MORE_THAN_A_YEAR_IN_DAYS);
        for (ScheduledJobView job : app.whatCanBeRun()) {
            app.runJob(job.name());
        }

        assertThat(app.invitationsWaitingFor(invited))
                .as("a year of nights later, it is still waiting")
                .anyMatch(waiting -> waiting.id().equals(sent.id())
                        && waiting.state().equals("PENDING"));
        assertThat(app.accept(sent.potId(), sent.id(), invited).state()).isEqualTo("ACCEPTED");
    }

    /** A pot of its own with one invitation waiting in it, which is where most of these start. */
    private static PotInvitationView anInvitationTo(String potName) {
        SharedPotView pot = app.openAPot(ANKE, potName);
        return app.invite(pot.id(), ANKE, invited, "CONTRIBUTOR");
    }

    /**
     * The sentence the customer is shown, insisted on as present: a refusal carries its reason in
     * {@code detail}, and one that did not would be a page able to say only that something went
     * wrong.
     */
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
