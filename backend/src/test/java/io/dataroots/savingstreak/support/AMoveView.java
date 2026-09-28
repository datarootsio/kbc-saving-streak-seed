package io.dataroots.savingstreak.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * A move between two of one customer's savings accounts as the API reports it, and so as a test
 * reads it: which two accounts, how much, the two rows it became, why it earned nothing, and the
 * anniversary the arriving money has just started counting towards.
 *
 * <p>{@code pointsEarned} is read rather than assumed, because it being nought is the claim: a move
 * earns nothing, and a test that did not look at the figure would not be asserting the rule the
 * whole operation rests on.
 *
 * <p>{@code earnedOnCarriedAcross} is what the deposits the money left had already been paid for,
 * carried onto the deposit it arrived in. It is the figure that explains why the most this customer
 * has ever saved did not move, and it is read here so that a test can check the explanation as well
 * as the outcome.
 *
 * <p>Shared by every test that makes a move, so that none of them can drift into disagreeing about
 * the shape of the answer.
 */
public record AMoveView(long fromSavingsAccountId, long toSavingsAccountId, long customerId,
                        BigDecimal amount, BigDecimal earnedOnCarriedAcross, long pointsEarned,
                        long withdrawalId, long depositId, Instant movedAt,
                        LocalDate newAnniversary, long pointsOnTheNewAnniversary) {
}
