package io.dataroots.savingstreak.automation;

import java.math.BigDecimal;

import io.dataroots.savingstreak.deposits.AmountOfMoney;

/**
 * How much a saving rule would move out of a current account holding that balance.
 *
 * <p>A function of its arguments and nothing else — no database, no clock, no state — in the shape
 * {@code HowTheWeeklyMoneyIsSpent} and {@code LoyaltyAnniversary} already set.
 *
 * <p><strong>One function, and that is the point of it.</strong> The firing calls it, and so will
 * the twelve-month preview and the dry run of a rule nobody has saved yet. Three copies of "a fixed
 * amount is that amount, a sweep is the balance less the floor" are three chances to disagree about
 * what a rule would move — and the two that a customer reads before committing would be the ones
 * that were wrong.
 *
 * <p>It answers what the rule <em>asks</em> for rather than what the account can afford, and the
 * difference is deliberate. A fixed amount is all or nothing: whether the balance covers it is a
 * question about the occurrence, answered where the occurrence is recorded, because the honest
 * record of a standing order that could not be honoured is that it moved nothing rather than that it
 * moved less. A sweep cannot be short — its figure is derived from the balance — which is why the
 * same function answers both without a special case.
 *
 * <p><strong>Public for the simulator's fold</strong>, which is the fourth caller and the one that
 * makes the paragraph above load-bearing rather than tidy. A branch is shown to a customer so that
 * they can decide something, and a branch whose sweeps were worked out by a copy of this rule would
 * be an argument built on a figure the application disagrees with. The fold hands it the balance the
 * branch has arrived at on that morning, which is the best answer available and is still an
 * illustration — the word the response already carries, for exactly this reason.
 */
public final class WhatARuleWouldMove {

    private WhatARuleWouldMove() {
    }

    /**
     * The amount this rule would move out of an account holding {@code balance}, quoted to the cent.
     *
     * <p>Nothing is a legal answer and means the rule has nothing to do: a sweep whose account is
     * already at or under its floor asks for nothing, which is arithmetic rather than a failure.
     *
     * @param howMuchMoves whether the rule moves a figure or a surplus
     * @param amount       the figure a fixed-amount rule moves, and null on a sweep
     * @param floor        the line a sweep stops at, and null on a fixed-amount rule
     * @param balance      what the current account holds at the moment being asked about
     */
    public static BigDecimal outOfABalanceOf(HowMuchMoves howMuchMoves, BigDecimal amount, BigDecimal floor,
                                      BigDecimal balance) {
        return switch (howMuchMoves) {
            // The figure the customer named, whatever is in the account. A standing order says what
            // it moves; whether it can be honoured is the next question and not this one.
            case A_FIXED_AMOUNT -> AmountOfMoney.quotedToTheCent(amount);
            // Everything above the line, and nothing at all when the balance is already at or under
            // it. Never negative: a balance under the floor has no surplus to sweep, and a negative
            // amount would be money travelling the other way.
            case EVERYTHING_ABOVE -> AmountOfMoney.quotedToTheCent(
                    balance.subtract(floor).max(BigDecimal.ZERO));
        };
    }
}
