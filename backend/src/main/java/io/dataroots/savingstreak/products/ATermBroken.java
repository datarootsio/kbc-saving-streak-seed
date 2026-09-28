package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * What happened when a customer broke a fixed term: the day they broke it, the day it would have
 * matured, what it cost them, and what the account is living under now.
 *
 * <p><strong>Two agreements in one answer, which is what breaking a term is.</strong> The term that
 * ended is named by {@code wouldHaveMaturedOn} and priced by {@code charge}; the agreement that
 * replaced it is {@code nowOn}, in full, exactly as any other reading of an account's agreement
 * reads. A screen that had to fetch the account again to find out what it was on afterwards would be
 * asking a second time about something this operation has just decided — and, for a moment, could be
 * told something else.
 *
 * <p><strong>{@code nowOn.maturesOn()} is null, and that is the answer rather than a gap.</strong>
 * The ticket asks that the maturity date be gone from the account's reading once the term is broken,
 * and it is gone because the account is on a product with no term rather than because anything
 * cleared a field. That is the whole reason breaking moves the agreement instead of marking it: a
 * flag saying "broken" would leave a locked product on the account with a rule beside it saying to
 * ignore the lock, and the day somebody read the product without the flag the money would be locked
 * again.
 *
 * <p><strong>The charge is what was actually taken, not what was quoted.</strong> They are the same
 * number — both are worked out from the balance at the moment of breaking, in one place, floored the
 * same way — and saying so is the point: a customer can put the reading they were shown beside this
 * and see one figure twice. It is nought for an account with nothing in it, which is a true price
 * rather than a missing one.
 *
 * <p>{@code balanceItWasChargedOn} is here because a price nobody can redo is a price taken on
 * trust. The days are on it, the balance is on it, and the rate is on the version the account was
 * on — which the term it was on named, and which the reading below still names.
 */
public record ATermBroken(

        /** The savings account whose term ended. */
        long savingsAccountId,

        /** The day the customer broke it, read off this application's own clock. */
        LocalDate brokenOn,

        /** The day it would have matured on had they left it, which is what they gave up. */
        LocalDate wouldHaveMaturedOn,

        /** How many months the term was, as the terms it was written under named it. */
        int termMonths,

        /** Days of interest the terms charge for breaking early. */
        int earlyExitPenaltyDays,

        /** What the account held at the moment it was broken, which the charge is a fraction of. */
        BigDecimal balanceItWasChargedOn,

        /** What breaking actually cost, in euros, taken out of the account as its own movement. */
        BigDecimal charge,

        /** What the account is living under now: free savings, at the version on offer today. */
        TheAgreementAnAccountIsOn nowOn) {
}
