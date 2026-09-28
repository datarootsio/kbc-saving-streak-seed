package io.dataroots.savingstreak.products;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One notice as a customer reads it: what it was given on, when it was given, the day it comes
 * free, whether that day has come, and how much of it is still standing.
 *
 * <p><strong>Both the amount and what is still standing, because they are two different
 * answers.</strong> Notice on EUR 500 that paid for a withdrawal of EUR 200 still says EUR 500 —
 * that is what the customer did, and it is what makes the history read as a history — while EUR 300
 * is what it is good for now. A reading that carried only one of them would either lose the act or
 * lose the arithmetic.
 *
 * <p><strong>{@code ready} and {@code daysLeft} are derived and travel together.</strong> They are
 * the same subtraction read two ways, worked out against the application's clock at the moment this
 * record is built and stored nowhere. Sending both saves every screen from doing the arithmetic on
 * a date in a zone it would have to guess, and sending them together means a page can never show
 * "ready" beside "11 days left".
 *
 * <p>{@code readyOn} is the day rather than a countdown, because a countdown is only true for as
 * long as the page is open and a date is true for good. The two beside each other are what a
 * sentence like "ready on the 29th, in eleven days" is built from.
 *
 * <p>A cancelled notice never appears in one of these. Cancelling leaves nothing standing, and a
 * reading is a list of what a customer still has — the row survives in the database for the sake of
 * a later question about what happened, and that is a different question from this one.
 */
public record NoticeGiven(

        long id,

        long savingsAccountId,

        BigDecimal amount,

        BigDecimal stillStanding,

        LocalDate givenOn,

        LocalDate readyOn,

        boolean ready,

        int daysLeft) {
}
