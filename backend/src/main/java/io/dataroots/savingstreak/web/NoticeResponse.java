package io.dataroots.savingstreak.web;

import java.math.BigDecimal;
import java.time.LocalDate;

import io.dataroots.savingstreak.products.NoticeGiven;

/**
 * One notice as the screen reads it: what it was given on, the day it was given, the day it comes
 * free, whether that day has come, how long is left, and how much of it is still standing.
 *
 * <p><strong>{@code ready} and {@code daysLeft} come down worked out.</strong> Both are the same
 * subtraction — the day it was given plus the days the agreement asks for, against the day this
 * application is standing on — and the backend has already read that day in the one zone it counts
 * its calendars in. A page working it out from {@code readyOn} would be doing calendar arithmetic
 * in the browser's zone, which is a different zone on a laptop in the wrong country and a different
 * answer on the morning a notice becomes ready.
 *
 * <p><strong>{@code amount} and {@code stillStanding} are both here and are not the same
 * figure.</strong> The first is what the customer did and never changes; the second is what the
 * notice is good for now, after a withdrawal has partly used it. A card shows the second and says
 * the first when the two differ, which is how "you gave notice on EUR 500 and EUR 300 of it is
 * left" becomes a sentence a page can draw.
 *
 * <p>The days travel as plain dates rather than as moments, for the reason every other date on
 * these responses gives: which calendar day a moment falls on depends on the zone it is read in,
 * and the backend has already decided it.
 */
record NoticeResponse(long id, long savingsAccountId, BigDecimal amount, BigDecimal stillStanding,
                      LocalDate givenOn, LocalDate readyOn, boolean ready, int daysLeft) {

    static NoticeResponse of(NoticeGiven given) {
        return new NoticeResponse(
                given.id(),
                given.savingsAccountId(),
                given.amount(),
                given.stillStanding(),
                given.givenOn(),
                given.readyOn(),
                given.ready(),
                given.daysLeft());
    }
}
