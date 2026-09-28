package io.dataroots.savingstreak.potinvitations;

import java.util.List;

import io.dataroots.savingstreak.support.AnApplicationWithAClockToMove;
import io.dataroots.savingstreak.support.ApiIntegrationTest;
import io.dataroots.savingstreak.support.PotInvitationView;
import io.dataroots.savingstreak.support.PotMemberView;
import io.dataroots.savingstreak.support.SharedPotView;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.dataroots.savingstreak.support.SeededAccounts.ANKE;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * An owner invites another customer by the address they bank under, the invitation waits for them,
 * and answering it is what makes them a member — or leaves them a stranger to the pot.
 *
 * <p>This is the slice that first makes a pot shared. Until now a pot had exactly one member, the
 * customer who opened it, and {@code CONTRIBUTOR} and {@code VIEWER} were words nobody could hold;
 * an accepted invitation is the only way either of them is reached, which is why the role the
 * invitation named is asserted on the membership rather than only on the invitation.
 *
 * <p><strong>Accepting is asserted from three sides</strong>, because an invitation that changed its
 * own state and nothing else would look accepted and mean nothing: the invitation says so, the pot's
 * membership has them in it with the role they were offered, and the pot they belong to turns up in
 * their own list of pots. Declining is asserted the same way from the other end — the invitation
 * says declined, and the membership is exactly as it was.
 *
 * <p>Its own application, for the reason {@code APotIsOpenedByACustomerWhoBecomesItsOwnerApiTest}
 * gives: a pot's savings account is held by nobody, and several tests on the run's shared database
 * ask for "an identifier no savings account has" by walking every account every customer holds. An
 * account held by nobody is in none of those lists, so a pot opened there would hand those tests an
 * identifier that does exist. Pots are opened where they are the only thing that has happened.
 *
 * <p>The second customer is opened by the test itself, through
 * {@code AnApplicationWithAClockToMove.aCustomerOfItsOwn}: the seeded pair are shared with every
 * other test of this application, and an invitation is between two people who must not be anybody
 * else's.
 */
class AnInvitationMakesTheCustomerWhoAcceptsItAMemberApiTest extends ApiIntegrationTest {

    private static AnApplicationWithAClockToMove app;

    /** The customer being asked to save with somebody, opened by this test and nobody else's. */
    private static String invited;

    @BeforeAll
    static void startAnApplicationOfItsOwn() {
        app = new AnApplicationWithAClockToMove(
                aDatabaseFileThatDoesNotExistYet("saving-streak-an-invitation-is-accepted"));
        invited = app.aCustomerOfItsOwn("invited");
    }

    @AfterAll
    static void stopTheApplication() {
        if (app != null) {
            app.close();
        }
    }

    /**
     * The invitation itself: addressed to the person by the email they bank under, carrying the pot
     * it is about, who is asking, the role it grants, and the fact that nobody has answered it.
     */
    @Test
    void an_invitation_names_the_pot_who_is_asking_and_the_role_it_grants() {
        SharedPotView pot = app.openAPot(ANKE, "Kitchen");

        PotInvitationView invitation = app.invite(pot.id(), ANKE, invited, "CONTRIBUTOR");

        assertThat(invitation.id()).isNotNull();
        assertThat(invitation.potId()).isEqualTo(pot.id());
        assertThat(invitation.potName())
                .as("the pot's name travels with it, because the page that shows it says what it is for")
                .isEqualTo("Kitchen");
        assertThat(invitation.invitedByCustomerId()).isEqualTo(app.customerIdOf(ANKE));
        assertThat(invitation.invitedByName()).isEqualTo(ANKE);
        assertThat(invitation.invitedCustomerId()).isEqualTo(app.customerIdOf(invited));
        assertThat(invitation.invitedName()).isEqualTo(invited);
        assertThat(invitation.role()).isEqualTo("CONTRIBUTOR");
        assertThat(invitation.state()).isEqualTo("PENDING");
        assertThat(invitation.answeredAt())
                .as("nobody has answered it, which is an absence rather than a moment")
                .isNull();
        assertThat(invitation.invitedAt())
                .as("the moment comes off the clock the application judges everything else by")
                .isNotNull()
                .isBeforeOrEqualTo(app.theClockReads());
    }

    /**
     * And it waits where the person it is addressed to would find it, which is the whole point of
     * sending one: somebody who was never told is somebody who never answers.
     */
    @Test
    void an_invitation_waits_in_the_list_of_the_customer_it_is_addressed_to() {
        SharedPotView pot = app.openAPot(ANKE, "Greece 2027");

        PotInvitationView sent = app.invite(pot.id(), ANKE, invited, "VIEWER");

        assertThat(app.invitationsWaitingFor(invited))
                .as("the invitation reads the same in their list as it did when it was sent")
                .contains(sent);
        assertThat(app.invitationsWaitingFor(ANKE))
                .as("and it is not waiting for the person who sent it")
                .noneMatch(waiting -> waiting.potId().equals(pot.id()));
    }

    /** The pot's own list is every invitation it ever issued, which is the owner's side of the same story. */
    @Test
    void a_pot_lists_the_invitations_it_has_issued() {
        SharedPotView pot = app.openAPot(ANKE, "New sofa");
        PotInvitationView sent = app.invite(pot.id(), ANKE, invited, "CONTRIBUTOR");

        assertThat(app.invitationsIssuedBy(pot.id())).containsExactly(sent);
    }

    /**
     * The point of the whole slice: accepting makes them a member, with the role the invitation
     * named and not some default — asserted on the pot's membership rather than on the invitation,
     * because it is the membership every later rule is read off.
     */
    @Test
    void accepting_makes_the_invited_customer_a_member_with_the_role_the_invitation_named() {
        SharedPotView pot = app.openAPot(ANKE, "Winter tyres");
        PotInvitationView sent = app.invite(pot.id(), ANKE, invited, "CONTRIBUTOR");

        PotInvitationView accepted = app.accept(pot.id(), sent.id(), invited);

        assertThat(accepted.state()).isEqualTo("ACCEPTED");
        assertThat(accepted.answeredAt())
                .as("an answered invitation says when it was answered")
                .isNotNull();
        List<PotMemberView> members = app.membersOfThePot(pot.id());
        assertThat(members).hasSize(2);
        assertThat(members.get(0).customerId())
                .as("the owner who opened it is still first, because the list is in joining order")
                .isEqualTo(app.customerIdOf(ANKE));
        PotMemberView joined = members.get(1);
        assertThat(joined.customerId()).isEqualTo(app.customerIdOf(invited));
        assertThat(joined.name()).isEqualTo(invited);
        assertThat(joined.role()).isEqualTo("CONTRIBUTOR");
        assertThat(joined.joinedAt())
                .as("they have belonged to it since the moment they accepted")
                .isEqualTo(accepted.answeredAt());
    }

    /**
     * A viewer is reached the same way and is a member as much as anybody else — the word means
     * something later, and the only thing it means here is that the role asked for is the role
     * granted.
     */
    @Test
    void a_customer_invited_to_watch_becomes_a_viewer_and_not_a_contributor() {
        SharedPotView pot = app.openAPot(ANKE, "Roof");
        PotInvitationView sent = app.invite(pot.id(), ANKE, invited, "VIEWER");

        app.accept(pot.id(), sent.id(), invited);

        assertThat(app.membersOfThePot(pot.id()))
                .filteredOn(member -> member.customerId().equals(app.customerIdOf(invited)))
                .singleElement()
                .extracting(PotMemberView::role)
                .isEqualTo("VIEWER");
    }

    /** And the pot is then one of theirs, in the list that answers "which of them am I in". */
    @Test
    void a_pot_somebody_accepted_an_invitation_to_is_one_of_their_pots() {
        SharedPotView pot = app.openAPot(ANKE, "A pot to be in");
        PotInvitationView sent = app.invite(pot.id(), ANKE, invited, "CONTRIBUTOR");

        app.accept(pot.id(), sent.id(), invited);

        assertThat(app.potsOf(invited))
                .as("the pot they joined is in their list, with them in its membership")
                .anyMatch(theirs -> theirs.id().equals(pot.id()));
    }

    /** An answered invitation stops waiting, because a panel that went on asking would be lying. */
    @Test
    void an_accepted_invitation_is_no_longer_waiting_for_anybody() {
        SharedPotView pot = app.openAPot(ANKE, "Nothing left to answer");
        PotInvitationView sent = app.invite(pot.id(), ANKE, invited, "CONTRIBUTOR");

        app.accept(pot.id(), sent.id(), invited);

        assertThat(app.invitationsWaitingFor(invited))
                .noneMatch(waiting -> waiting.id().equals(sent.id()));
        assertThat(app.invitationsIssuedBy(pot.id()))
                .as("the pot's own list keeps it, because it is the record of what was asked")
                .singleElement()
                .extracting(PotInvitationView::state)
                .isEqualTo("ACCEPTED");
    }

    /**
     * Declining is the other answer, and it is a real one: the invitation is closed, the pot's list
     * says so, and the membership is exactly what it was before anybody was asked.
     */
    @Test
    void declining_leaves_them_a_stranger_to_the_pot_and_says_so() {
        SharedPotView pot = app.openAPot(ANKE, "No thank you");
        PotInvitationView sent = app.invite(pot.id(), ANKE, invited, "CONTRIBUTOR");

        PotInvitationView declined = app.decline(pot.id(), sent.id(), invited);

        assertThat(declined.state()).isEqualTo("DECLINED");
        assertThat(declined.answeredAt()).isNotNull();
        assertThat(app.membersOfThePot(pot.id()))
                .as("declining makes nobody a member of anything")
                .singleElement()
                .extracting(PotMemberView::customerId)
                .isEqualTo(app.customerIdOf(ANKE));
        assertThat(app.potsOf(invited))
                .noneMatch(theirs -> theirs.id().equals(pot.id()));
        assertThat(app.invitationsWaitingFor(invited))
                .noneMatch(waiting -> waiting.id().equals(sent.id()));
    }

    /**
     * An owner takes back an invitation nobody has answered — a mistyped address, or a change of
     * mind — and it stops waiting for the person it named.
     */
    @Test
    void a_revoked_invitation_stops_waiting_and_makes_nobody_a_member() {
        SharedPotView pot = app.openAPot(ANKE, "A change of mind");
        PotInvitationView sent = app.invite(pot.id(), ANKE, invited, "CONTRIBUTOR");

        PotInvitationView revoked = app.revoke(pot.id(), sent.id(), ANKE);

        assertThat(revoked.state()).isEqualTo("REVOKED");
        assertThat(revoked.answeredAt())
                .as("an invitation that has stopped waiting says when it stopped")
                .isNotNull();
        assertThat(app.invitationsWaitingFor(invited))
                .noneMatch(waiting -> waiting.id().equals(sent.id()));
        assertThat(app.membersOfThePot(pot.id())).hasSize(1);
    }

    /**
     * Two invitations to two pots are two invitations, each waiting on its own. The panel somebody
     * reads is a list rather than a single question, and an invitation that quietly replaced another
     * would lose one of the two people asking.
     */
    @Test
    void invitations_to_different_pots_wait_side_by_side() {
        SharedPotView one = app.openAPot(ANKE, "One pot");
        SharedPotView other = app.openAPot(ANKE, "Another pot");

        PotInvitationView toOne = app.invite(one.id(), ANKE, invited, "CONTRIBUTOR");
        PotInvitationView toTheOther = app.invite(other.id(), ANKE, invited, "VIEWER");

        assertThat(app.invitationsWaitingFor(invited))
                .contains(toOne, toTheOther);
    }
}
