package io.dataroots.savingstreak.sharedpots;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Who has to say yes before money leaves a shared pot: every other member who still has money in it.
 *
 * <p><strong>This is the rule the whole feature exists for.</strong> A withdrawal draws a savings
 * account's oldest deposits down first, whoever paid them in, so a withdrawal from a shared pot
 * spends other members' euros — and because what somebody has ever earned points on does not fall
 * when what they hold does, it also costs them the points on their next contributions until they
 * have climbed back to where they already were. One member taking five hundred euros out of a shared
 * pot is quietly spending somebody else's points. Whoever's euros are at stake gets a veto.
 *
 * <p>It follows from how withdrawals already work rather than from a policy anybody chose, which is
 * why the three exclusions below are exclusions and not exceptions:
 *
 * <ul>
 *   <li><strong>The proposer is never asked.</strong> Proposing is asking, and a proposal that
 *       counted its own author's assent would be a vote for yourself.</li>
 *   <li><strong>A member with nothing left in the pot is not asked.</strong> There is nothing of
 *       theirs to spend, so there is nothing to protect — and a withdrawal held up by somebody with
 *       no stake in it is a gate standing in an empty field. The sole contributor withdrawing
 *       without ceremony is the same sentence read from the other end.</li>
 *   <li><strong>A viewer is never asked.</strong> A viewer cannot pay in, so by construction they
 *       have nothing in the pot and the rule above would have excluded them anyway. It is said out
 *       loud rather than left to follow, because the one way a viewer could come to hold euros here
 *       is a contributor being demoted after paying some in, and the spec's sentence about a viewer
 *       is unconditional: "a viewer is never asked". What a pot owes a demoted contributor is a
 *       question about demotion and settlement, and it belongs to the slices that own those.</li>
 * </ul>
 *
 * <p>A function of its arguments and nothing else — no repository, no clock, no state — in the shape
 * {@code HowAnAmountIsSplit} and {@code WhenABillIsDue} already set, and unit-tested directly for the
 * reason those are: the table of cases it has to be right across is tedious to reach over HTTP and
 * trivial to state here. What the application <em>does</em> with the answer is asserted over HTTP,
 * where everything else in this feature is.
 *
 * <p>It takes what is still each member's rather than going and finding out, because this module
 * owns no money: the euros are deposits in the pot's savings account, and summing what remains of
 * them is the Deposits module's answer. Handing the figures in is also what keeps this a function
 * somebody can read the whole of in one screen.
 */
final class WhoseAssentAWithdrawalNeeds {

    private WhoseAssentAWithdrawalNeeds() {
    }

    /**
     * The members whose approval a proposal needs, in the order the members were given — which on
     * every real call is the order they joined the pot, so a page listing who is still to answer
     * reads down the pot's own membership.
     *
     * <p>An empty answer is an answer and not an absence: it means the proposer is the only person
     * with money in the pot, and their withdrawal needs nobody's approval because there is nobody
     * else's money to spend.
     *
     * @param proposingCustomerId    the member who proposed the withdrawal, who is not asked about
     *                               their own proposal
     * @param members                everybody in the pot, each with the role they hold
     * @param stillTheirsByCustomerId what is still each member's <em>in this pot</em> — the sum of
     *                               what remains of their own deposits into its savings account. A
     *                               member missing from it has nothing there, which is the same
     *                               answer as nought and is how a member who never paid in arrives
     */
    static List<APotMember> of(long proposingCustomerId, List<APotMember> members,
                               Map<Long, BigDecimal> stillTheirsByCustomerId) {
        List<APotMember> asked = new ArrayList<>();
        for (APotMember member : members) {
            if (member.customerId() == null || member.customerId() == proposingCustomerId) {
                continue;
            }
            if (!member.role().mayPayIn()) {
                continue;
            }
            if (whatIsStill(member, stillTheirsByCustomerId).signum() <= 0) {
                continue;
            }
            asked.add(member);
        }
        return List.copyOf(asked);
    }

    /**
     * What is still this member's in the pot, with a member nobody has a figure for reading as
     * nothing.
     *
     * <p>Nought rather than a refusal, because the two are the same fact arriving two ways: a member
     * who has never paid in has no deposits to sum, and a member whose deposits have all been drawn
     * down to nothing is outside the read that produced these figures. Both have nothing at stake.
     */
    private static BigDecimal whatIsStill(APotMember member, Map<Long, BigDecimal> stillTheirs) {
        return stillTheirs.getOrDefault(member.customerId(), BigDecimal.ZERO);
    }
}
