package io.dataroots.savingstreak.sharedpots;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/**
 * Makes one membership per customer per pot, and one answer per member per proposal, rules the
 * database keeps before the application serves anything.
 *
 * <p>Membership is what every rule in this feature is read off: whether somebody may pay in, whether
 * their assent is needed before money leaves, whether the pot still has an owner. A customer holding
 * two memberships of one pot would hold two roles at once, and every one of those questions would
 * have two answers with nothing to choose between them. {@link SharedPotsService} writes one row and
 * intends to go on writing one; that intention held only because SQLite serialises writers and this
 * application runs on a pool of one connection, which is a property of a configuration file rather
 * than a rule about pots.
 *
 * <p>Here rather than on the entity because the entity cannot say it. The schema is generated from
 * the entity model ({@code ddl-auto=update}) against SQLite, and that dialect writes a composite
 * unique clause nowhere: declared as a unique constraint, or as an index marked unique, the table is
 * created without it and the only statement that reaches the database is a drop that does nothing. A
 * {@code create unique index} is a statement SQLite does accept, so this is where the guarantee
 * comes from — and it is a step a reviewer can watch happen in a DEBUG start-up log rather than an
 * annotation they would have to take on trust. {@code GoalsOnStartUp}, {@code AccountsOnStartUp} and
 * {@code LoyaltyOnStartUp} are the same step for the same reason.
 *
 * <p>The same shape as those: it runs on every start, and all but the first do nothing.
 */
@Component
class SharedPotsOnStartUp implements SmartInitializingSingleton {

    private static final Logger log = LoggerFactory.getLogger(SharedPotsOnStartUp.class);

    private final PotMembershipRepository memberships;

    /**
     * The other thing in this module that must happen once and not twice.
     *
     * <p>Money leaves a pot when the members whose euros are at stake have all approved, which is
     * worked out by subtracting the members who have answered from the members who must. An
     * approval written twice would close that subtraction on its own, and the pot would pay out on
     * an assent one member gave and another never did.
     */
    private final WithdrawalProposalAnswerRepository answers;

    SharedPotsOnStartUp(PotMembershipRepository memberships,
                        WithdrawalProposalAnswerRepository answers) {
        this.memberships = memberships;
        this.answers = answers;
    }

    /**
     * Called once every bean exists and before the context finishes refreshing — which is before the
     * web server binds its port, so no pot can be joined against a membership that is not yet
     * unique.
     */
    @Override
    public void afterSingletonsInstantiated() {
        makeMembershipUniquePerCustomer();
        makeAnsweringOncePerMember();
    }

    private void makeMembershipUniquePerCustomer() {
        if (memberships.membershipIsAlreadyUniquePerCustomer() > 0) {
            // The ordinary case, and worth a line all the same: it says the question was asked, so
            // that the guarantee is something a reviewer can confirm on any start rather than only
            // on the first.
            log.debug("membership of a shared pot is already unique per customer "
                    + "index=one_membership_per_customer_per_pot");
            return;
        }
        memberships.makeMembershipUniquePerCustomer();
        log.info("membership of a shared pot was made unique per customer "
                + "index=one_membership_per_customer_per_pot columns=[shared_pot_id, customer_id]");
    }

    /**
     * And the same step for the answers a withdrawal proposal collects, for the reason the field
     * above gives: the count of them is what moves money.
     */
    private void makeAnsweringOncePerMember() {
        if (answers.answeringIsAlreadyOncePerMember() > 0) {
            log.debug("answering a pot withdrawal proposal is already once per member "
                    + "index=one_answer_per_member_per_proposal");
            return;
        }
        answers.makeAnsweringOncePerMember();
        log.info("answering a pot withdrawal proposal was made once per member "
                + "index=one_answer_per_member_per_proposal "
                + "columns=[withdrawal_proposal_id, customer_id]");
    }
}
