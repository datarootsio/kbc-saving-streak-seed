package io.dataroots.savingstreak.sharedpots;

import java.math.BigDecimal;

/**
 * What one member has put into a shared pot, as the rest of the application sees it: who they are,
 * what they are to the pot, how much they have paid into it altogether, how much of their money is
 * still in it, and how many points their contributions to this pot have earned them.
 *
 * <p><strong>Paid in and still theirs are two figures because they are two facts.</strong> They are
 * equal until money leaves the pot and different for ever afterwards, because an approved withdrawal
 * draws the pot's <em>oldest</em> deposits down first whoever paid them in — so one member's
 * withdrawal is spent out of another member's contribution. A screen carrying only one of them would
 * either forget what somebody carried or claim they still have money that has gone. Carrying both is
 * what makes the pot honest about who has paid for what.
 *
 * <p><strong>What is still each member's, added across the members, is what the pot holds.</strong>
 * Not approximately: both figures are summed from the very same deposits, so the arithmetic closes
 * to the cent and anybody reading the screen can check it. That is the claim the whole feature rests
 * on, and it is why nothing here is worked out from a share or a proportion.
 *
 * <p><strong>A member who has paid nothing in is here with nought beside them</strong> rather than
 * missing. A viewer watching two other people save is a member of the pot, and a list that quietly
 * dropped everybody with no money in it would read as a pot with fewer members than it has — and
 * would drop a contributor whose euros have all been spent, which is exactly the person the figures
 * are worth reading for.
 *
 * <p><strong>The points are the member's own and never the pot's.</strong> A euro paid into a shared
 * pot earns points for the person who paid it, at their own rate and against their own mark, so this
 * figure says what this pot has been worth to each of them without a single point having been
 * shared. It does not fall when a withdrawal spends the euros that earned it: points are earned
 * once, and the money leaving is a different event from the earning.
 *
 * <p><strong>The role is absent for somebody who has left the pot.</strong> This list is every
 * member of the pot and then everybody else whose contributions are still in its account, because
 * the record of what a departed member paid in has to survive their leaving — story 26, and without
 * it the pot's history stops adding up on the one screen that shows it adding up. A role is what
 * somebody <em>is</em> to the pot, and somebody who is not in it is not anything to it, so the
 * absence is the honest answer: {@link AnAnswerToAProposal} says the same thing the same way about
 * an answer whose author has gone. A fourth role would be a word no rule reads and no invitation can
 * grant.
 *
 * <p>The role travels as a member of {@link PotRole} for the reason {@link APotMember}'s does:
 * inside the application it is a decision rather than a label, and the word is what the web layer
 * sends.
 */
public record AContribution(Long customerId, String name, PotRole role,
                            BigDecimal paidInAltogether, BigDecimal stillTheirs,
                            long pointsEarned) {
}
