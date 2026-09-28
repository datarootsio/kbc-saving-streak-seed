package io.dataroots.savingstreak.potinvitations;

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

import static io.dataroots.savingstreak.potinvitations.InvitationsAsSomebodyWouldTypeThem.anInvitationAddressedToNobody;
import static io.dataroots.savingstreak.potinvitations.InvitationsAsSomebodyWouldTypeThem.anInvitationNobodyIsSending;
import static io.dataroots.savingstreak.potinvitations.InvitationsAsSomebodyWouldTypeThem.anInvitationTo;
import static io.dataroots.savingstreak.potinvitations.InvitationsAsSomebodyWouldTypeThem.anInvitationWithNoRole;
import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Who belongs to a pot is an owner's decision, and every other way of asking is refused in a
 * sentence somebody could read out loud.
 *
 * <p><strong>The 403 arrives here.</strong> A contributor or a viewer inviting somebody is a request
 * this application understood, from a caller it knows, that they are not allowed to make — and until
 * this slice there was no such thing anywhere in it. It is not a 404: telling a contributor that the
 * pot they are looking at does not exist would be a lie they could disprove by refreshing the page.
 * It is not a 400 either: there is nothing about what they typed to fix.
 *
 * <p><strong>Two of the sentences are deliberately not this module's own.</strong> An address nobody
 * banks under is refused in the words signing in already uses, and inviting yourself is refused in
 * the words gifting already uses — both asserted to be the same sentence rather than merely to be
 * similar, by asking those endpoints the same question and comparing the answers. Two copies of a
 * sentence are one rewording away from disagreeing about what absence sounds like, and a customer
 * who meets both in one afternoon should not meet two voices.
 *
 * <p><strong>Inviting an existing member is refused and changes nothing</strong>, which is the half
 * of story 13 that matters: a second invitation that quietly re-roled somebody would be a way of
 * promoting a viewer without ever saying so. The role they already hold is read back afterwards, so
 * the test would fail if the refusal ever arrived after the change rather than before it.
 *
 * <p>Its own application and its own customers, for the reasons
 * {@code AnInvitationMakesTheCustomerWhoAcceptsItAMemberApiTest} gives.
 */
class InvitingSomebodyIsAnOwnersDecisionApiTest extends ApiIntegrationTest {

    /** An address that is well formed and belongs to nobody, which is how a typo arrives. */
    private static final String NOBODY_BANKS_UNDER_THIS = "someone.else@example.be";

    private static AnApplicationWithAClockToMove app;

    /** Somebody who joins as a contributor, so that a contributor has a pot to try inviting into. */
    private static String contributor;

    /** Somebody who joins to watch, so that "viewer" can be shown to mean something. */
    private static String viewer;

    /** Somebody in no pot at all, for the stranger who tries anyway. */
    private static String stranger;

    /** The pot everybody in this class is arguing about, with a contributor and a viewer in it. */
    private static SharedPotView pot;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-inviting-is-an-owners-decision"));
        contributor = app.aCustomerOfItsOwn("a contributor");
        viewer = app.aCustomerOfItsOwn("a viewer");
        stranger = app.aCustomerOfItsOwn("a stranger");
        pot = app.openAPot(ANKE, "Whose decision this is");
        acceptAnInvitation(contributor, "CONTRIBUTOR");
        acceptAnInvitation(viewer, "VIEWER");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /** A contributor pays in and decides nothing about who else is in the pot. */
    @Test
    void a_contributor_may_not_invite_anybody() {
        ResponseEntity<JsonNode> refused = app.tryToInvite(pot.id(),
                anInvitationTo(app.contactDetailsOf(stranger), "CONTRIBUTOR",
                        app.customerIdOf(contributor)));

        assertThat(refused.getStatusCode())
                .as("understood, from somebody known, who is not allowed — which is 403")
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(refused))
                .as("and it says what would have been needed, so the page can say who to ask")
                .contains("owner");
        assertThat(app.invitationsIssuedBy(pot.id()))
                .as("and nothing was sent")
                .noneMatch(issued -> issued.invitedCustomerId().equals(app.customerIdOf(stranger)));
    }

    /** And a viewer decides even less, which is the whole content of the word. */
    @Test
    void a_viewer_may_not_invite_anybody() {
        ResponseEntity<JsonNode> refused = app.tryToInvite(pot.id(),
                anInvitationTo(app.contactDetailsOf(stranger), "VIEWER", app.customerIdOf(viewer)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(refused)).contains("owner");
    }

    /** Somebody who is in no pot at all is refused in the same words, and for the same reason. */
    @Test
    void somebody_who_is_not_in_the_pot_at_all_may_not_invite_anybody() {
        ResponseEntity<JsonNode> refused = app.tryToInvite(pot.id(),
                anInvitationTo(app.contactDetailsOf(stranger), "VIEWER", app.customerIdOf(stranger)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(refused)).contains("owner");
    }

    /**
     * Inviting somebody who is already in the pot is refused — and the role they hold is the role
     * they held, which is the part that would quietly go wrong if the refusal ever moved.
     */
    @Test
    void inviting_a_member_is_refused_and_leaves_the_role_they_hold_alone() {
        ResponseEntity<JsonNode> refused = app.tryToInvite(pot.id(),
                anInvitationTo(app.contactDetailsOf(viewer), "OWNER", app.customerIdOf(ANKE)));

        assertThat(refused.getStatusCode())
                .as("the request is perfectly well formed and the pot's state will not allow it")
                .isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonIn(refused))
                .contains(viewer)
                .contains("member");
        assertThat(roleHeldBy(viewer))
                .as("a second invitation is not a way of promoting somebody")
                .isEqualTo("VIEWER");
    }

    /**
     * And somebody already asked is not asked twice: two invitations waiting to the same pot would
     * be two answers to one question, and the second accepted would be a second membership — which
     * the database itself refuses.
     */
    @Test
    void inviting_somebody_who_is_already_waiting_for_an_answer_is_refused() {
        SharedPotView asking = app.openAPot(ANKE, "Asked once already");
        PotInvitationView first = app.invite(asking.id(), ANKE, stranger, "CONTRIBUTOR");

        ResponseEntity<JsonNode> refused = app.tryToInvite(asking.id(),
                anInvitationTo(app.contactDetailsOf(stranger), "VIEWER", app.customerIdOf(ANKE)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(reasonIn(refused)).contains(stranger);
        assertThat(app.invitationsIssuedBy(asking.id()))
                .as("the one that was already waiting is the only one there is")
                .containsExactly(first);
    }

    /**
     * An address nobody banks under is a typo, and the customer finds out now rather than wondering
     * why nobody ever accepted. The sentence is signing in's own, asserted by asking signing in the
     * same question and comparing the two answers.
     */
    @Test
    void inviting_an_address_nobody_banks_under_is_not_found() {
        ResponseEntity<JsonNode> refused = app.tryToInvite(pot.id(),
                anInvitationTo(NOBODY_BANKS_UNDER_THIS, "CONTRIBUTOR", app.customerIdOf(ANKE)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonIn(refused))
                .as("the same words signing in gives for an address it does not know")
                .isEqualTo(reasonIn(app.trySigningIn(NOBODY_BANKS_UNDER_THIS)));
    }

    /**
     * Inviting yourself would be a membership you already have, and it is refused in the sentence
     * gifting already uses for the same mistake — asserted by making the same mistake with a gift
     * and comparing how the two sentences end.
     *
     * <p>The ending rather than the whole sentence, because the two differ in exactly one respect
     * and should: one is about a gift and the other about an invitation. What must not drift is how
     * the application tells somebody who they are signed in as.
     */
    @Test
    void inviting_yourself_is_refused_in_the_sentence_gifting_already_uses() {
        ResponseEntity<JsonNode> refused = app.tryToInvite(pot.id(),
                anInvitationTo(app.contactDetailsOf(ANKE), "CONTRIBUTOR", app.customerIdOf(ANKE)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        String howGiftingEndsIt = ", and " + ANKE + " is who you are signed in as.";
        assertThat(reasonIn(app.tryToGive(ANKE, app.contactDetailsOf(ANKE), "1")))
                .as("which is how gifting ends it")
                .endsWith(howGiftingEndsIt);
        assertThat(reasonIn(refused))
                .as("and how an invitation to yourself ends too")
                .endsWith(howGiftingEndsIt)
                .contains("somebody else");
    }

    /**
     * A role this application has never heard of is a form to fix, and the sentence says what the
     * three words are — which is the only thing the person could do next.
     */
    @Test
    void a_role_nobody_has_heard_of_is_refused_with_the_three_that_exist() {
        ResponseEntity<JsonNode> refused = app.tryToInvite(pot.id(),
                anInvitationTo(app.contactDetailsOf(stranger), "TREASURER", app.customerIdOf(ANKE)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonIn(refused))
                .contains("TREASURER")
                .contains("OWNER")
                .contains("CONTRIBUTOR")
                .contains("VIEWER");
    }

    /** A form sent with the role box never filled in gets the same answer, because it is the same gap. */
    @Test
    void an_invitation_with_no_role_at_all_is_refused_in_the_same_way() {
        ResponseEntity<JsonNode> refused = app.tryToInvite(pot.id(),
                anInvitationWithNoRole(app.contactDetailsOf(stranger), app.customerIdOf(ANKE)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonIn(refused)).contains("OWNER");
    }

    /**
     * A body that says nothing about who is sending the invitation is a malformed request rather
     * than one of the module's refusals — the same line the gift endpoint and the pot endpoint both
     * draw between a field that was never filled in and a field filled in with somebody who does not
     * exist.
     */
    @Test
    void an_invitation_nobody_is_sending_is_refused_as_a_malformed_request() {
        ResponseEntity<JsonNode> refused = app.tryToInvite(pot.id(),
                anInvitationNobodyIsSending(app.contactDetailsOf(stranger), "CONTRIBUTOR"));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonIn(refused)).contains("customer");
    }

    /** And one addressed to nobody at all is refused before anybody is looked up. */
    @Test
    void an_invitation_addressed_to_nobody_is_refused_as_a_malformed_request() {
        ResponseEntity<JsonNode> refused = app.tryToInvite(pot.id(),
                anInvitationAddressedToNobody("CONTRIBUTOR", app.customerIdOf(ANKE)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonIn(refused)).contains("email address");
    }

    /** A customer this application has never heard of cannot invite anybody, and is told which. */
    @Test
    void a_customer_nobody_has_heard_of_cannot_invite_anybody() {
        long nobody = app.anIdNoCustomerHas();

        ResponseEntity<JsonNode> refused = app.tryToInvite(pot.id(),
                anInvitationTo(app.contactDetailsOf(stranger), "CONTRIBUTOR", nobody));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonIn(refused)).contains(String.valueOf(nobody));
    }

    /** And a pot nobody has heard of is answered as the absence it is, with the pot named back. */
    @Test
    void nobody_can_be_invited_into_a_pot_that_does_not_exist() {
        long noSuchPot = pot.id() + 1_000;

        ResponseEntity<JsonNode> refused = app.tryToInvite(noSuchPot,
                anInvitationTo(app.contactDetailsOf(stranger), "CONTRIBUTOR", app.customerIdOf(ANKE)));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonIn(refused)).contains(String.valueOf(noSuchPot));
    }

    /** Reading the invitations of a pot that is not there is the same absence, in the same words. */
    @Test
    void the_invitations_of_a_pot_that_does_not_exist_are_not_an_empty_list() {
        long noSuchPot = pot.id() + 1_000;

        ResponseEntity<JsonNode> refused = app.tryToReadTheInvitationsIssuedBy(noSuchPot);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonIn(refused)).contains(String.valueOf(noSuchPot));
    }

    /**
     * And so is reading the invitations waiting for a customer nobody has heard of, which is the
     * line every other per-customer read in this application draws.
     */
    @Test
    void the_invitations_waiting_for_a_customer_who_does_not_exist_are_not_an_empty_list() {
        long nobody = app.anIdNoCustomerHas();

        ResponseEntity<JsonNode> refused = app.tryToReadTheInvitationsWaitingFor(nobody);

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(reasonIn(refused)).contains(String.valueOf(nobody));
    }

    /** A revocation that never says who is revoking is a form that was never filled in. */
    @Test
    void a_revocation_that_does_not_say_who_is_revoking_is_a_malformed_request() {
        SharedPotView revoking = app.openAPot(ANKE, "Revoked by nobody");
        PotInvitationView sent = app.invite(revoking.id(), ANKE, stranger, "CONTRIBUTOR");

        ResponseEntity<JsonNode> refused = app.tryToRevokeAsNobody(revoking.id(), sent.id());

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(reasonIn(refused)).contains("customer");
        assertThat(app.invitationsIssuedBy(revoking.id()))
                .singleElement()
                .extracting(PotInvitationView::state)
                .isEqualTo("PENDING");
    }

    /** And revoking is an owner's decision as much as inviting is. */
    @Test
    void a_contributor_may_not_revoke_an_invitation() {
        SharedPotView revoking = app.openAPot(ANKE, "Not the contributor's to take back");
        app.invite(revoking.id(), ANKE, contributor, "CONTRIBUTOR");
        PotInvitationView theirs = app.invitationsWaitingFor(contributor).stream()
                .filter(waiting -> waiting.potId().equals(revoking.id()))
                .findFirst()
                .orElseThrow();
        app.accept(revoking.id(), theirs.id(), contributor);
        PotInvitationView somebodyElse = app.invite(revoking.id(), ANKE, stranger, "VIEWER");

        ResponseEntity<JsonNode> refused = app.tryToRevoke(revoking.id(), somebodyElse.id(),
                app.customerIdOf(contributor));

        assertThat(refused.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(reasonIn(refused)).contains("owner");
        assertThat(app.invitationsWaitingFor(stranger))
                .as("and the invitation is still waiting")
                .anyMatch(waiting -> waiting.id().equals(somebodyElse.id()));
    }

    /** What the pot says somebody is, read off the membership everybody else reads it off. */
    private static String roleHeldBy(String customerName) {
        return app.membersOfThePot(pot.id()).stream()
                .filter(member -> member.customerId().equals(app.customerIdOf(customerName)))
                .map(PotMemberView::role)
                .findFirst()
                .orElseThrow(() -> new AssertionError(customerName + " is not a member of the pot"));
    }

    /** Somebody invited and accepting, which is the only way anybody but an owner gets into a pot. */
    private static void acceptAnInvitation(String customerName, String role) {
        PotInvitationView sent = app.invite(pot.id(), ANKE, customerName, role);
        app.accept(pot.id(), sent.id(), customerName);
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
