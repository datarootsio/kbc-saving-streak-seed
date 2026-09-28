package io.dataroots.savingstreak.sharedpots;

import java.util.Optional;

import io.dataroots.savingstreak.accounts.AccountPairing;
import io.dataroots.savingstreak.accounts.WhoMayPayIntoAnAccountNobodyHolds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The Shared Pots module's answer to the one thing Accounts cannot work out for itself: a savings
 * account with no holding customer belongs to a pot, and whether somebody may put money into it is
 * this module's rule.
 *
 * <p><strong>This is what makes a pot payable into at all.</strong> A deposit is refused unless the
 * two accounts pair, the pairing is Accounts' answer, and until now an account nobody held paired
 * with nothing — so the deposit endpoint answered "there is no savings account 7" for a pot that
 * plainly existed. The rule it was missing is one sentence long and it is here rather than there: a
 * current account pairs with a pot's savings account when its holder is a member of the pot, with a
 * role that may pay in.
 *
 * <p>A class of its own rather than a method on {@link SharedPotsService}, and the reason is the
 * application context rather than tidiness. That service asks Accounts who exists and what they are
 * called; if Accounts asked it back, the two would want each other at start-up and Spring would
 * refuse to start. This reads two repositories of its own and asks Accounts nothing, so the
 * dependency runs one way and there is no cycle to break. It is also the whole of the module's
 * outward face for this question — everything else about a pot goes through the service — and a
 * reader looking for "why was my contribution refused" finds one small file.
 *
 * <p>It answers in {@link AccountPairing}, which is Accounts' vocabulary and not this module's, for
 * the reason {@link WhoMayPayIntoAnAccountNobodyHolds} gives: the refusal has to reach a person as a
 * sentence they can act on, and a boolean would leave the module that cannot tell a stranger from a
 * viewer to word the difference.
 *
 * <p>It refuses nothing itself and raises no {@link SharedPotRefused}. Whoever asked was moving
 * money, the refusal is theirs to report in the words their own endpoint uses, and a second refusal
 * thrown from underneath would be the same event told twice.
 */
@Component
class MembersOfThePotMayPayIntoItsAccount implements WhoMayPayIntoAnAccountNobodyHolds {

    private static final Logger log =
            LoggerFactory.getLogger(MembersOfThePotMayPayIntoItsAccount.class);

    private final SharedPotRepository pots;
    private final PotMembershipRepository memberships;

    MembersOfThePotMayPayIntoItsAccount(SharedPotRepository pots,
                                        PotMembershipRepository memberships) {
        this.pots = pots;
        this.memberships = memberships;
    }

    /**
     * Which pot holds the account, what the paying customer is to that pot, and therefore whether
     * the two accounts pair.
     *
     * <p>Read-only and in one transaction, so that the pot and the membership that decides the
     * answer are read at one instant of the record. A member removed between the two reads would
     * otherwise be a contribution allowed by a pot and refused by a membership, or the other way
     * about.
     *
     * <p>The decision is said out loud at DEBUG with everything that made it — the pot, the
     * customer, the role they hold — because it is the one step of a contribution a reader cannot
     * redo from the deposit's own log line: the deposit records the account, and which pot that
     * account belongs to and what the payer is to it appear nowhere else. A refused contribution and
     * an accepted one both leave this line, so the two can be compared.
     */
    @Override
    @Transactional(readOnly = true)
    public AccountPairing pairingWith(long savingsAccountId, long payingCustomerId) {
        Optional<SharedPot> pot = pots.findBySavingsAccountId(savingsAccountId);
        if (pot.isEmpty()) {
            // An account held by no customer and by no pot either. Nothing in this application
            // writes one — a pot and its account are opened in one transaction — so this is the
            // record having gone wrong, and it is said at WARN rather than passed over quietly.
            log.warn("a savings account no customer holds belongs to no pot either "
                    + "savingsAccountId={} payingCustomerId={}", savingsAccountId, payingCustomerId);
            return AccountPairing.NO_SUCH_SAVINGS_ACCOUNT;
        }
        long potId = pot.get().getId();
        Optional<PotMembership> membership =
                memberships.findBySharedPotIdAndCustomerId(potId, payingCustomerId);
        // The pot before the person, and that order is the answer rather than an optimisation. A
        // closed pot has already returned every member's own euros to them; telling an owner they
        // may pay in, or telling a stranger to ask for an invitation, would be sending somebody off
        // to do a thing that cannot be done. What is wrong is the pot, so the role is not asked.
        AccountPairing pairing = pot.get().isClosed()
                ? AccountPairing.HELD_BY_A_POT_THAT_IS_CLOSED
                : membership
                        .map(theirs -> theirs.getRole().mayPayIn()
                                ? AccountPairing.HELD_BY_A_POT_THE_PAYER_MAY_PAY_INTO
                                : AccountPairing.HELD_BY_A_POT_THE_PAYER_ONLY_WATCHES)
                        .orElse(AccountPairing.HELD_BY_A_POT_THE_PAYER_DOES_NOT_BELONG_TO);
        log.debug("who may pay into a pot's account potId={} potName={} savingsAccountId={} "
                        + "payingCustomerId={} role={} closedAt={} pairing={}",
                potId, pot.get().getName(), savingsAccountId, payingCustomerId,
                membership.map(PotMembership::getRole).map(Enum::name).orElse("none"),
                pot.get().getClosedAt(), pairing);
        return pairing;
    }
}
