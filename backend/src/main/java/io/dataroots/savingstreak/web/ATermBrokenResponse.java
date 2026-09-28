package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.dataroots.savingstreak.products.ATermBroken;

/**
 * What happened when a customer broke a fixed term: the day, the maturity they gave up, what it
 * cost, and the agreement the account is on now.
 *
 * <p><strong>The new agreement is in the answer rather than left to be fetched.</strong> Breaking a
 * term changes what the account is living under, and a screen that had to ask again would be asking
 * a second time about something this press has just decided. {@code nowOn.maturesOn} is null in it,
 * which is the ticket's criterion — the maturity date is gone from the reading — shown as a fact
 * about the account rather than as a field somebody cleared.
 *
 * <p><strong>The charge is what was actually taken.</strong> It is the same number the reading
 * quoted before the button was pressed, because both are worked out from one balance by one
 * function; putting it in the answer is what lets a screen say "that cost you EUR 5.91" instead of
 * sending the customer to the money history to find out. It is {@code 0.00} for an account with
 * nothing in it, which is a true price rather than a missing one.
 *
 * <p>{@code balanceItWasChargedOn} and {@code earlyExitPenaltyDays} are on it for the reason the
 * interest postings carry their average balance and their rate: a figure on its own is a number the
 * customer has to take on trust, and the two beside it are what turn the charge into a sentence they
 * can redo.
 */
record ATermBrokenResponse(long savingsAccountId, LocalDate brokenOn, LocalDate wouldHaveMaturedOn,
                           int termMonths, int earlyExitPenaltyDays,
                           BigDecimal balanceItWasChargedOn, BigDecimal charge,
                           AnAgreementResponse nowOn) {

    static ATermBrokenResponse of(ATermBroken broken) {
        return new ATermBrokenResponse(
                broken.savingsAccountId(),
                broken.brokenOn(),
                broken.wouldHaveMaturedOn(),
                broken.termMonths(),
                broken.earlyExitPenaltyDays(),
                broken.balanceItWasChargedOn(),
                broken.charge(),
                AnAgreementResponse.of(broken.nowOn()));
    }
}
