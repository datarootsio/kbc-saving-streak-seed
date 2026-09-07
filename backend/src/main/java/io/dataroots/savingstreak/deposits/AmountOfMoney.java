package io.dataroots.savingstreak.deposits;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * What this application will accept as an amount of money to move, and how it writes one down.
 *
 * <p>One place for both directions. A deposit and a withdrawal are the same movement turned around,
 * and "is that an amount of money" is the same question in each. Written out in both services the
 * two rules are free to drift apart — one of them starts rounding a third decimal place, or words
 * its refusal so differently that a customer who has met both cannot tell it is the same objection —
 * and a rule that has drifted is one nobody downstream can reword back into agreement.
 *
 * <p>It answers with the reason rather than refusing, because who refuses differs: Deposits raises a
 * {@link DepositRefused} and Withdrawals a {@link WithdrawalRefused}, and the web layer gives each
 * its status. This class owns the rule and the words for it, and decides nothing else.
 */
final class AmountOfMoney {

    /** Euros are quoted to the cent, so an amount carrying more places than this is not one. */
    static final int DECIMAL_PLACES = 2;

    private AmountOfMoney() {
    }

    /**
     * Why this is not an amount of money to move, in words the person who typed it can act on, or
     * nothing at all if it is one.
     *
     * @param movement what the person was trying to do, so the sentence names it back to them —
     *                 "deposit" or "withdrawal"
     */
    static Optional<String> whyItIsNotOne(String movement, BigDecimal amount) {
        if (amount.signum() <= 0) {
            return Optional.of("A " + movement + " has to be an amount of more than zero, and "
                    + amount.toPlainString() + " is not.");
        }
        // Refused rather than rounded. Rounding would move an amount nobody typed, and a bank that
        // quietly decides what a figure was meant to say is worse than one that asks.
        if (amount.scale() > DECIMAL_PLACES) {
            return Optional.of("An amount of money has at most two decimal places, and "
                    + amount.toPlainString() + " has " + amount.scale() + ".");
        }
        return Optional.empty();
    }

    /**
     * An amount of money written the way money is written, to the cent.
     *
     * <p>A figure that has been through the database comes back carrying whatever scale SQLite kept
     * — it has no decimal type and holds an amount as a float — so a balance of 2359.50 arrives as
     * 2359.5, and printed straight into a sentence it reads as a number rather than as money. This
     * matters where an amount is written for somebody to read: in a refusal, and in a log line a
     * reviewer checks a balance against. Everywhere else an amount is compared, which BigDecimal
     * does by value rather than by how many places it is carrying, or formatted by whoever displays
     * it.
     */
    static String asMoney(BigDecimal amount) {
        return quotedToTheCent(amount).toPlainString();
    }

    /**
     * The same amount as a figure rather than as words, for a caller handing one to another module
     * to print or to add up. The rounding lives here alone: two places that decided how many places
     * money has could decide differently.
     */
    static BigDecimal quotedToTheCent(BigDecimal amount) {
        return amount.setScale(DECIMAL_PLACES, RoundingMode.HALF_UP);
    }
}
