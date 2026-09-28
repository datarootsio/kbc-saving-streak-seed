package io.dataroots.savingstreak.sharedpots;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** Package-private, for the same reason as {@link SharedPotRepository}. */
interface PotInvitationRepository extends JpaRepository<PotInvitation, Long> {

    /**
     * Every invitation one pot has issued, in any state, in the order they were sent.
     *
     * <p>Every state and not only the waiting ones, because this is the owner's record of who has
     * been asked: an invitation that vanished from the list the moment it was declined would leave
     * an owner asking the same person a second time, and would lose the only trace that they said
     * no.
     *
     * <p>By the identifier after the moment, the idiom every ordered read in this application uses:
     * two invitations sent in the same millisecond are otherwise in whatever order the database felt
     * like, and the later identifier is the later invitation.
     */
    List<PotInvitation> findBySharedPotIdOrderByInvitedAtAscIdAsc(long sharedPotId);

    /**
     * The invitations addressed to one customer that are still waiting for an answer, oldest first.
     *
     * <p>Waiting ones only, because this is the panel the invited customer reads and a panel is a
     * list of questions to answer. What became of the ones they have answered is the pot's record
     * rather than their inbox.
     */
    List<PotInvitation> findByInvitedCustomerIdAndStateOrderByInvitedAtAscIdAsc(
            long invitedCustomerId, InvitationState state);

    /**
     * Whether this customer is already being asked to join this pot.
     *
     * <p>What it protects is the rule one above it in the service: nobody is asked the same question
     * twice. Two waiting invitations to one pot would be two answers to one question and, if both
     * were accepted, two memberships — which the unique index would refuse, out of the middle of an
     * acceptance that had every reason to think it was fine.
     */
    Optional<PotInvitation> findBySharedPotIdAndInvitedCustomerIdAndState(
            long sharedPotId, long invitedCustomerId, InvitationState state);
}
