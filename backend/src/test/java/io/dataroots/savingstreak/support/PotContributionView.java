package io.dataroots.savingstreak.support;

import java.math.BigDecimal;

/**
 * What one member of a shared pot has put into it, as the API reports it and so as a test reads it:
 * which customer, what they are called, what they are to the pot, how much they have paid in
 * altogether, how much of their money is still in it, and what their contributions to this pot have
 * earned them.
 *
 * <p>The role is read as the word the API sends rather than mapped onto an enum of the test's own,
 * for the reason {@link MoneyMovementView} gives: a rename in the backend should fail a test rather
 * than be quietly translated back.
 *
 * <p><strong>Three figures rather than two, and the third is the one that surprises people.</strong>
 * What somebody paid in and what is still theirs come apart the moment a withdrawal draws the pot's
 * oldest deposits down, because the euros that leave are the oldest ones whoever paid them in. A
 * test reading both back is what proves the pot is honest about that rather than quietly reporting
 * the contribution as though it were still there.
 *
 * <p>The points are a whole number and they are the member's own, not the pot's: a euro paid in
 * earns points for the person who paid it, so this figure says what this pot has been worth to each
 * of them without a single point ever having been shared.
 */
public record PotContributionView(Long customerId, String name, String role,
                                  BigDecimal paidInAltogether, BigDecimal stillTheirs,
                                  long pointsEarned) {
}
