package io.dataroots.savingstreak.sharedpots;

import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may read, and who may change, what the pot holding a savings account is saving for.
 *
 * <p><strong>A pot's goals are goals on the pot's savings account, and that is the whole design.</strong>
 * Nothing about the Goals module changes for this: goals are keyed by savings account, a pot has a
 * savings account, and every screen that draws a goal — its order, its progress, whether it is on
 * track, the weekly figure the plan gives it — works on a pot's goals without being told that pots
 * exist. What is new is one sentence in front of those endpoints: an owner decides what the group is
 * saving for, every member may read it, and nobody else may do either.
 *
 * <p><strong>This is where that sentence lives, and it is deliberately not in Goals.</strong> Goals
 * reads no other module — it cannot tell an existing account from a number somebody made up, let
 * alone a pot from a person — and a Goals that imported this module would be the newest module
 * reaching into the oldest question in the application from underneath. The precedent is
 * {@link MembersOfThePotMayPayIntoItsAccount}: when something outside needs to know what a customer
 * is to the pot holding an account, this module answers, from its own two repositories, and the
 * dependency runs one way. The difference is only who asks. A deposit is judged inside Accounts, so
 * that question had to arrive through a port Accounts declares; a goal is judged in front of Goals,
 * by the controllers, and reading across modules is exactly what a controller is for — so this is
 * called directly and needs no port of its own.
 *
 * <p><strong>An account no pot holds is not this class's business.</strong> Every method here
 * returns without a word when the savings account belongs to a customer, which is what keeps a
 * personal account's goals exactly as they were: same endpoints, same bodies, same answers, no role
 * anywhere in them. That is the risk this slice carries and it is answered by this one early return
 * rather than by care taken in fifteen handlers.
 *
 * <p><strong>Nobody at all is refused in the same words as a stranger.</strong> A request that names
 * no acting customer is not an owner and is not a member, so it is told what would have been needed
 * rather than told that it forgot a field — the same line {@link SharedPotsService#insistOnAnOwner}
 * draws between a contributor and somebody who is in no pot at all, and for the same reason: naming
 * the gap would tell whoever is guessing that there is a pot here to be a member of.
 */
@Component
public class WhatAPotIsSavingForIsItsOwnersDecision {

    private static final Logger log =
            LoggerFactory.getLogger(WhatAPotIsSavingForIsItsOwnersDecision.class);

    /** What the log calls a customer who is in the pot in no capacity at all. */
    private static final String NOT_IN_THE_POT = "none";

    private final SharedPotRepository pots;
    private final PotMembershipRepository memberships;

    /**
     * Asked for one thing: the sentence a member who is not an owner is refused with.
     *
     * <p>Reused rather than rewritten, because "Only an owner may &lt;this&gt; this pot." is already
     * what a contributor hears when they try to invite somebody or change a role, and a second
     * wording of it here would be two sentences about one rule, one rewording away from disagreeing
     * about what an owner is for.
     */
    private final SharedPotsService sharedPots;

    WhatAPotIsSavingForIsItsOwnersDecision(SharedPotRepository pots,
                                           PotMembershipRepository memberships,
                                           SharedPotsService sharedPots) {
        this.pots = pots;
        this.memberships = memberships;
        this.sharedPots = sharedPots;
    }

    /**
     * Insists that whoever is asking may read what the pot holding this account is saving for.
     *
     * <p>Every member may, whatever their role. A viewer watching two other people save is being
     * shown the thing they are saving for — that is the entire content of the word — and a
     * contributor who could pay into a pot without being able to see what it is for would be paying
     * into a number.
     *
     * <p>Does nothing at all for an account a customer holds, which is every account this
     * application had before pots existed.
     *
     * @param customerId    who is asking, as the request named them, which may be nobody at all
     * @param whatTheyAsked the act, as a verb phrase the sentence is built around, so that one rule
     *                      answers for the goals, the allocations and the capacity without three
     *                      sentences being written
     * @throws SharedPotRefused with {@code NOT_ALLOWED}, which answers 403, if a pot holds the
     *                          account and the customer asking is not in it
     */
    @Transactional(readOnly = true)
    public void insistTheyMayRead(long savingsAccountId, Long customerId, String whatTheyAsked) {
        Optional<SharedPot> pot = pots.findBySavingsAccountId(savingsAccountId);
        if (pot.isEmpty()) {
            return;
        }
        long potId = pot.get().getId();
        PotRole role = whatTheyAreToThePot(potId, savingsAccountId, customerId, whatTheyAsked);
        if (role != null) {
            return;
        }
        String reason = "Only a member may " + whatTheyAsked + " this pot.";
        log.warn("pot goals rejected savingsAccountId={} potId={} customerId={} role={} "
                        + "tryingTo={} reason={}",
                savingsAccountId, potId, customerId, NOT_IN_THE_POT, whatTheyAsked, reason);
        throw new SharedPotRefused(SharedPotRefused.Kind.NOT_ALLOWED, reason);
    }

    /**
     * Insists that whoever is asking may change what the pot holding this account is saving for.
     *
     * <p>An owner and nobody else. What a group is saving for is the thing the group agreed to, and
     * a contributor who could rename the goal, move the target or give up on it altogether would be
     * changing the agreement on everybody's behalf — story 44, and the same 403 inviting somebody
     * and changing a role already answer.
     *
     * <p>Does nothing at all for an account a customer holds. A person's own goals are their own
     * decision and there is no role in it to check.
     *
     * <p>Two log lines come out of one refusal here, and they are halves of one thought. This one
     * names the role that caused it, which is what somebody reading the log for "why was I refused"
     * needs and which the service's line does not carry. The service's names the sentence, and keeps
     * every refusal this module makes findable under one {@code shared pot rejected} grep. The
     * sentence is written there and only there, which is why this line does not repeat it.
     *
     * <p><strong>And a closed pot decides nothing more.</strong> Writing a goal is an act, and
     * story 67 asks for every act on a closed pot to be refused with a sentence saying so — a pot
     * whose goals were abandoned when it closed would otherwise be quietly reopened for saving by
     * an owner adding a new one to an empty account nobody can pay into. The sentence and the
     * warning are {@link SharedPotsService#insistThePotIsOpen}'s, for the reason the "only an owner
     * may" sentence below is that method's: one rule, said in one place, whichever endpoint ran
     * into it.
     *
     * <p>After the role and not before it, which is the order Shared Pots uses everywhere: somebody
     * who was never allowed to change what the group saves for should be told so whether or not the
     * pot is closed, or a contributor would learn from a closed pot what they could have done to an
     * open one.
     *
     * @param customerId    who is asking, as the request named them, which may be nobody at all
     * @param whatTheyTried the act, as a verb phrase the sentence is built around
     * @throws SharedPotRefused with {@code NOT_ALLOWED}, which answers 403, if a pot holds the
     *                          account and the customer asking is not its owner, or with
     *                          {@code POT_IS_CLOSED}, which answers 409, if the pot has been closed
     */
    @Transactional(readOnly = true)
    public void insistTheyMayDecide(long savingsAccountId, Long customerId, String whatTheyTried) {
        Optional<SharedPot> pot = pots.findBySavingsAccountId(savingsAccountId);
        if (pot.isEmpty()) {
            return;
        }
        long potId = pot.get().getId();
        PotRole role = whatTheyAreToThePot(potId, savingsAccountId, customerId, whatTheyTried);
        if (role != PotRole.OWNER) {
            log.warn("pot goals rejected savingsAccountId={} potId={} customerId={} role={} "
                            + "tryingTo={} ownerNeeded=true",
                    savingsAccountId, potId, customerId,
                    role == null ? NOT_IN_THE_POT : role.name(), whatTheyTried);
        }
        sharedPots.insistOnAnOwner(potId, customerId, whatTheyTried);
        sharedPots.insistThePotIsOpen(pot.get(), customerId, whatTheyTried);
    }

    /**
     * What this customer is to the pot, or nothing at all when they are in no way in it — including
     * when the request named nobody to be anything.
     *
     * <p>Said out loud at DEBUG whether it refuses or not, for the reason the deposit pairing's line
     * is: which pot holds an account, and what the person asking is to it, appear nowhere else in
     * the log, so an allowed change and a refused one can only be compared if both leave a line.
     */
    private PotRole whatTheyAreToThePot(long potId, long savingsAccountId, Long customerId,
                                        String whatTheyTried) {
        PotRole role = customerId == null ? null
                : memberships.findBySharedPotIdAndCustomerId(potId, customerId)
                        .map(PotMembership::getRole)
                        .orElse(null);
        log.debug("what a customer is to the pot holding an account savingsAccountId={} potId={} "
                        + "customerId={} role={} tryingTo={}",
                savingsAccountId, potId, customerId,
                role == null ? NOT_IN_THE_POT : role.name(), whatTheyTried);
        return role;
    }
}
